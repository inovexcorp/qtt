/**
 * Fuzzy matching for the quick actions bar. Small on purpose: the lists are tens to hundreds of items.
 *
 * Each whitespace-separated word of the query must match the text, in one of these ways (best first):
 * the text starts with it, a word of the text starts with it, it appears anywhere, or its characters appear in order
 * (preferring the starts of words, so "crc" finds "Clear route cache").
 */

/** A matched span of the text: [start, end). */
export type Range = [number, number];

export interface Match {
  /** Higher is better. Only comparable between matches of the same query. */
  score: number;
  /** The matched characters, sorted and merged, for highlighting. */
  ranges: Range[];
}

const PREFIX = 1000;
const WORD_START = 800;
const CONTAINS = 600;
const SUBSEQUENCE = 300;

/**
 * Matches a query against a text; null when some word of the query doesn't match. An empty query matches.
 * With fuzzy false, each word must appear as written (no scattered characters): for secondary text such as a URL.
 */
export function match(query: string, text: string, fuzzy = true): Match | null {
  const words = query.trim().toLowerCase().split(/\s+/).filter(w => w.length > 0);
  const lower = text.toLowerCase();
  let score = 0;
  const ranges: Range[] = [];
  for (const word of words) {
    const m = matchWord(word, text, lower, fuzzy);
    if (!m) {
      return null;
    }
    score += m.score;
    ranges.push(...m.ranges);
  }
  // Shorter texts first among equal matches ("Routes" before "Routes table settings").
  return { score: words.length ? score - text.length / 100 : 0, ranges: merge(ranges) };
}

function matchWord(word: string, text: string, lower: string, fuzzy: boolean): Match | null {
  if (lower.startsWith(word)) {
    return { score: PREFIX, ranges: [[0, word.length]] };
  }
  let wordStartHit = -1;
  for (let i = lower.indexOf(word); i >= 0; i = lower.indexOf(word, i + 1)) {
    if (isWordStart(text, i)) {
      wordStartHit = i;
      break;
    }
  }
  if (wordStartHit >= 0) {
    return { score: WORD_START - wordStartHit, ranges: [[wordStartHit, wordStartHit + word.length]] };
  }
  const at = lower.indexOf(word);
  if (at >= 0) {
    return { score: CONTAINS - at, ranges: [[at, at + word.length]] };
  }
  return fuzzy ? subsequence(word, text, lower) : null;
}

/**
 * The characters of the word in order. Each character goes to the next start of a word when one matches before the
 * next plain occurrence would be needed; otherwise to its next occurrence.
 */
function subsequence(word: string, text: string, lower: string): Match | null {
  const ranges: Range[] = [];
  let pos = 0;
  let bonus = 0;
  let previous = -2;
  for (const ch of word) {
    let at = -1;
    for (let i = lower.indexOf(ch, pos); i >= 0; i = lower.indexOf(ch, i + 1)) {
      if (isWordStart(text, i)) {
        at = i;
        break;
      }
    }
    if (at < 0) {
      at = lower.indexOf(ch, pos);
    }
    if (at < 0) {
      return null;
    }
    bonus += isWordStart(text, at) ? 10 : 0;
    bonus += at === previous + 1 ? 5 : 0;
    ranges.push([at, at + 1]);
    previous = at;
    pos = at + 1;
  }
  const spread = previous - ranges[0][0];
  return { score: SUBSEQUENCE + bonus - spread, ranges };
}

/** True at the first character, after a separator, and at a lower-to-upper case change ("routeId"). */
function isWordStart(text: string, i: number): boolean {
  if (i === 0) {
    return true;
  }
  const prev = text[i - 1];
  const ch = text[i];
  if (!/[a-z0-9]/i.test(prev)) {
    return true;
  }
  return /[a-z]/.test(prev) && /[A-Z]/.test(ch);
}

function merge(ranges: Range[]): Range[] {
  const sorted = [...ranges].sort((a, b) => a[0] - b[0]);
  const out: Range[] = [];
  for (const r of sorted) {
    const last = out[out.length - 1];
    if (last && r[0] <= last[1]) {
      last[1] = Math.max(last[1], r[1]);
    } else {
      out.push([r[0], r[1]]);
    }
  }
  return out;
}

/** A text cut into plain and highlighted parts, for the template. */
export interface Segment {
  text: string;
  hit: boolean;
}

export function segments(text: string, ranges: Range[]): Segment[] {
  const out: Segment[] = [];
  let pos = 0;
  for (const [start, end] of ranges) {
    if (start > pos) {
      out.push({ text: text.slice(pos, start), hit: false });
    }
    out.push({ text: text.slice(start, end), hit: true });
    pos = end;
  }
  if (pos < text.length) {
    out.push({ text: text.slice(pos), hit: false });
  }
  return out;
}
