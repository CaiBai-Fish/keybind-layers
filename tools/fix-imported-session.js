#!/usr/bin/env node
/**
 * 修复 DSH 导入会话中的「悬空 tool call」。
 *
 * 症状：打开导入的会话后继续对话，报
 *   DeepSeek Messages tool calls need immediate results (INVALID_REQUEST)
 *
 * 原因：导入一个**仍在进行中**的会话时，源会话日志里最后一步的 assistant 消息已经写入，
 * 但该步的工具调用结果还没写进日志（「导入自己」这种自引用场景最容易命中）。于是新会话的
 * 消息序列里出现「assistant 带 tool_calls，后面却没有 role=tool 的结果」，被 API 拒绝。
 *
 * 本脚本做的事：
 *   1. 逐帧解压 session.v4.jsonl.zstd（DSH 日志是「多帧、每帧多行事件」）；
 *   2. 找出所有「有 tool-call 但缺 tool/result」的 assistant 消息；
 *   3. 为缺失者补写 tool/call 与 tool/result 事件（补录文本会明确标注不是原始输出）；
 *   4. 重新连续编号所有事件的 seq；
 *   5. 按原来的帧划分重新压缩写回（默认先备份）。
 *
 * 用法：
 *   node fix-imported-session.js "<...>\session.v4.jsonl.zstd"            # 只报告，不写入
 *   node fix-imported-session.js "<...>\session.v4.jsonl.zstd" --apply    # 备份后写入
 *
 * 注意：修复前请关闭该会话（或重启 DSH）；内存里的会话状态可能在之后覆盖写回。
 */
'use strict';

const fs = require('fs');
const zlib = require('zlib');

const argv = process.argv.slice(2);
const apply = argv.includes('--apply');
const file = argv.find((a) => !a.startsWith('--'));

if (!file) {
  console.error('usage: node fix-imported-session.js <session.v4.jsonl.zstd> [--apply]');
  process.exit(2);
}

const ZSTD_MAGIC = Buffer.from([0x28, 0xb5, 0x2f, 0xfd]);

// ------------------------------------------------------------------ 解帧

const buf = fs.readFileSync(file);
const frameOffsets = [];
let cursor = 0;
while ((cursor = buf.indexOf(ZSTD_MAGIC, cursor)) !== -1) {
  frameOffsets.push(cursor);
  cursor += 4;
}
if (frameOffsets.length === 0) {
  console.error('本文件里找不到 zstd 帧，脚本不做任何事。');
  process.exit(1);
}

const frameTexts = [];
for (let k = 0; k < frameOffsets.length; k++) {
  const start = frameOffsets[k];
  const end = k + 1 < frameOffsets.length ? frameOffsets[k + 1] : buf.length;
  frameTexts.push(zlib.zstdDecompressSync(buf.subarray(start, end)).toString('utf8'));
}

const frameShapes = frameTexts.map((text) => {
  const trailingNewline = text.endsWith('\n');
  const lines = text.split('\n');
  if (trailingNewline) lines.pop();
  return { trailingNewline, lines: lines.filter((l) => l.trim().length > 0) };
});

// 事件 + 它所属的帧序号
const entries = [];
frameShapes.forEach((shape, frameIndex) => {
  shape.lines.forEach((line, lineIndex) => {
    entries.push({ event: JSON.parse(line), frameIndex, lineIndex });
  });
});
console.log(`帧数 ${frameShapes.length}，事件 ${entries.length} 条`);

const events = entries.map((it) => it.event);

// ------------------------------------------------------ 找出悬空 tool call

const resultIds = new Set();
for (const e of events) {
  if (e.type !== 'tool/result') continue;
  const id = e.data.toolCallId
    || (e.data.message && e.data.message.source && e.data.message.source.callId);
  if (id) resultIds.add(id);
}

const missingGroups = [];
events.forEach((e, index) => {
  if (e.type !== 'assistant/message') return;
  const calls = (e.data.message.content || []).filter((c) => c.type === 'tool-call');
  const unanswered = calls.filter((c) => !resultIds.has(c.id));
  if (unanswered.length > 0) {
    missingGroups.push({ index, turn: e.data.turn, step: e.data.step, calls, unanswered });
  }
});

if (missingGroups.length === 0) {
  console.log('没有发现悬空的 tool call，文件无需修改。');
  process.exit(0);
}

console.log(`\n发现 ${missingGroups.length} 个 assistant 消息存在未配对的 tool call：`);
for (const g of missingGroups) {
  console.log(`  事件 #${g.index}（turn ${g.turn} step ${g.step}）：`);
  for (const c of g.unanswered) console.log(`     - ${c.id}  ${c.name}`);
}

// ------------------------------------------------------------- 组织补录事件

function describeCall(call) {
  let args = {};
  try {
    args = JSON.parse(call.arguments);
  } catch (e) {
    /* 参数不是 JSON 时保持空对象 */
  }
  switch (call.name) {
    case 'pwsh':
      return `命令：${args.command || '(未记录)'}`;
    case 'import_chat':
      return `导入来源：${args.path || '(未记录)'}`;
    case 'todo_write':
      return '更新任务清单';
    case 'subagent':
    case 'subagent_fork':
      return `委派任务：${args.description || '(未记录)'}`;
    default:
      return `工具：${call.name}`;
  }
}

function placeholderText(call) {
  return [
    '[补录] 该工具调用的结果在源会话被导入时尚未写入日志（导入发生时这一步仍在执行），',
    '这里由 tools/fix-imported-session.js 补写一条占位结果，以满足「tool call 必须紧跟',
    'tool result」的接口约束。',
    '',
    describeCall(call),
    '',
    '注意：这不是该调用的原始输出；需要真实结果请查看源会话。',
  ].join('\n');
}

function makeCallEvent(call, g, time) {
  return {
    type: 'tool/call',
    seq: null,
    time,
    data: { turn: g.turn, step: g.step, callId: call.id, name: call.name, arguments: call.arguments },
  };
}

function makeResultEvent(call, g, time) {
  return {
    type: 'tool/result',
    seq: null,
    time,
    data: {
      turn: g.turn,
      step: g.step,
      message: {
        role: 'tool',
        source: { kind: 'tool', callId: call.id },
        toolCallId: call.id,
        content: [{ type: 'text', text: placeholderText(call) }],
      },
    },
  };
}

// anchorIndex -> { events: [...], frameIndex }
const insertions = new Map();
let insertedCount = 0;

for (const g of missingGroups) {
  // 该步最后一个 tool/call | tool/result 事件的位置；没有就退回 assistant 消息本身
  let anchor = g.index;
  for (let j = g.index + 1; j < events.length && events[j].type !== 'assistant/message'; j++) {
    if (events[j].type === 'tool/call' || events[j].type === 'tool/result') anchor = j;
  }

  const presentCallIds = new Set();
  for (let j = g.index + 1; j < events.length && events[j].type !== 'assistant/message'; j++) {
    if (events[j].type === 'tool/call') presentCallIds.add(events[j].data.callId);
  }

  const anchorFrame = entries[anchor].frameIndex;
  const baseTime = events[g.index].time || Date.now();
  let tick = 1;
  const pending = [];

  for (const call of g.calls) {
    if (resultIds.has(call.id)) continue;
    if (!presentCallIds.has(call.id)) pending.push(makeCallEvent(call, g, baseTime + tick++));
    pending.push(makeResultEvent(call, g, baseTime + tick++));
  }

  if (pending.length > 0) {
    const slot = insertions.get(anchor) || { events: [], frameIndex: anchorFrame };
    slot.events.push(...pending);
    insertions.set(anchor, slot);
    insertedCount += pending.length;
  }
}

// ---------------------------------------------------------------- 重建序列

const rebuilt = [];
entries.forEach((it, index) => {
  rebuilt.push(it);
  const slot = insertions.get(index);
  if (slot) {
    for (const e of slot.events) rebuilt.push({ event: e, frameIndex: slot.frameIndex, lineIndex: -1 });
  }
});

let seq = 0;
for (const it of rebuilt) {
  if (it.event.seq === undefined && it.event.type === 'session') continue;
  it.event.seq = seq++;
}

// 按帧分组写回（插入的事件跟随其锚点所在的帧，保持原有「批次」划分）
const perFrameLines = frameShapes.map(() => []);
for (const it of rebuilt) {
  perFrameLines[it.frameIndex].push(JSON.stringify(it.event));
}

const outputFrames = perFrameLines.map((lines, k) => {
  const text = lines.join('\n') + (frameShapes[k].trailingNewline ? '\n' : '');
  return zlib.zstdCompressSync(Buffer.from(text, 'utf8'));
});

const output = Buffer.concat(outputFrames);
console.log(`\n补录事件 ${insertedCount} 条；输出 ${output.length} 字节（原 ${buf.length} 字节）`);

if (!apply) {
  console.log('（dry-run：未写入。确认无误后加 --apply 执行）');
  process.exit(0);
}

const backup = `${file}.bak-${Date.now()}`;
fs.copyFileSync(file, backup);
fs.writeFileSync(file, output);
console.log(`已备份到 ${backup}`);
console.log(`已写回 ${file}`);
