// Minimal TrueType/gvar reader/writer for patching single glyphs.
const fs = require('fs');
function readFont(path) {
  const b = fs.readFileSync(path);
  const u16 = o => b.readUInt16BE(o), u32 = o => b.readUInt32BE(o);
  const n = u16(4), tables = {};
  for (let i = 0; i < n; i++) { const r = 12 + 16 * i; const tag = b.toString('latin1', r, r + 4); tables[tag] = b.subarray(u32(r + 8), u32(r + 8) + u32(r + 12)); }
  return { sfntVersion: u32(0), tables };
}
function glyphCount(f) { return f.tables.maxp.readUInt16BE(4); }
function locaFmt(f) { return f.tables.head.readInt16BE(50); }
function glyphData(f, g) {
  const l = f.tables.loca, fmt = locaFmt(f);
  const off = i => fmt ? l.readUInt32BE(4 * i) : 2 * l.readUInt16BE(2 * i);
  return f.tables.glyf.subarray(off(g), off(g + 1));
}
function parseSimple(d) {
  const nc = d.readInt16BE(0); if (nc < 0) throw new Error('composite');
  const ends = []; for (let c = 0; c < nc; c++) ends.push(d.readUInt16BE(10 + 2 * c));
  const np = nc ? ends[nc - 1] + 1 : 0;
  let q = 10 + 2 * nc; const il = d.readUInt16BE(q); q += 2 + il;
  const fl = []; while (fl.length < np) { const f = d[q++]; fl.push(f); if (f & 8) { let r = d[q++]; while (r--) fl.push(f); } }
  const pts = fl.map(f => ({ x: 0, y: 0, on: !!(f & 1) }));
  let v = 0; fl.forEach((f, i) => { if (f & 2) { const k = d[q++]; v += (f & 16) ? k : -k; } else if (!(f & 16)) { v += d.readInt16BE(q); q += 2; } pts[i].x = v; });
  v = 0; fl.forEach((f, i) => { if (f & 4) { const k = d[q++]; v += (f & 32) ? k : -k; } else if (!(f & 32)) { v += d.readInt16BE(q); q += 2; } pts[i].y = v; });
  const contours = []; let s = 0; for (const e of ends) { contours.push(pts.slice(s, e + 1)); s = e + 1; }
  return { contours, overlap: !!(fl[0] & 0x40) };
}
function encodeSimple(contours, overlap) {
  const pts = contours.flat();
  const head = Buffer.alloc(10 + 2 * contours.length + 2);
  head.writeInt16BE(contours.length, 0);
  const xs = pts.map(p => p.x), ys = pts.map(p => p.y);
  head.writeInt16BE(Math.min(...xs), 2); head.writeInt16BE(Math.min(...ys), 4); head.writeInt16BE(Math.max(...xs), 6); head.writeInt16BE(Math.max(...ys), 8);
  let e = -1; contours.forEach((c, i) => { e += c.length; head.writeUInt16BE(e, 10 + 2 * i); });
  head.writeUInt16BE(0, 10 + 2 * contours.length);
  const flags = [], xb = [], yb = [];
  let px = 0, py = 0;
  pts.forEach((p, i) => {
    let f = p.on ? 1 : 0; if (i === 0 && overlap) f |= 0x40;
    const dx = p.x - px, dy = p.y - py; px = p.x; py = p.y;
    if (dx === 0) f |= 16; else if (Math.abs(dx) < 256) { f |= 2; if (dx > 0) f |= 16; xb.push(Math.abs(dx)); } else { xb.push((dx >> 8) & 255, dx & 255); }
    if (dy === 0) f |= 32; else if (Math.abs(dy) < 256) { f |= 4; if (dy > 0) f |= 32; yb.push(Math.abs(dy)); } else { yb.push((dy >> 8) & 255, dy & 255); }
    flags.push(f);
  });
  return Buffer.concat([head, Buffer.from(flags), Buffer.from(xb), Buffer.from(yb)]);
}
// ---- gvar ----
function gvarHeader(f) {
  const g = f.tables.gvar;
  return { axisCount: g.readUInt16BE(4), sharedCount: g.readUInt16BE(6), sharedOff: g.readUInt32BE(8), glyphCount: g.readUInt16BE(12), flags: g.readUInt16BE(14), dataOff: g.readUInt32BE(16) };
}
function gvarSlice(f, gi) {
  const g = f.tables.gvar, h = gvarHeader(f);
  const off = i => (h.flags & 1) ? g.readUInt32BE(20 + 4 * i) : 2 * g.readUInt16BE(20 + 2 * i);
  return g.subarray(h.dataOff + off(gi), h.dataOff + off(gi + 1));
}
function readPoints(d, q) {
  let n = d[q++]; if (n & 0x80) n = ((n & 0x7f) << 8) | d[q++];
  if (n === 0) return { pts: null, q };
  const pts = []; let v = 0;
  while (pts.length < n) { const c = d[q++]; const cnt = (c & 0x7f) + 1; for (let i = 0; i < cnt; i++) { if (c & 0x80) { v += d.readUInt16BE(q); q += 2; } else v += d[q++]; pts.push(v); } }
  return { pts, q };
}
function readDeltas(d, q, n) {
  const out = [];
  while (out.length < n) { const c = d[q++]; const cnt = (c & 0x3f) + 1; for (let i = 0; i < cnt; i++) { if (c & 0x80) out.push(0); else if (c & 0x40) { out.push(d.readInt16BE(q)); q += 2; } else out.push(d.readInt8(q++)); } }
  return { out, q };
}
// Returns tuples with full deltas for numPoints (incl. 4 phantom); IUP applied using contours.
function parseGlyphVar(f, gi, contours, numPoints) {
  const d = gvarSlice(f, gi); if (!d.length) return [];
  const ax = gvarHeader(f).axisCount;
  const tc = d.readUInt16BE(0), count = tc & 0xfff; let dq = d.readUInt16BE(2);
  let hq = 4; const heads = [];
  for (let i = 0; i < count; i++) {
    const size = d.readUInt16BE(hq), idx = d.readUInt16BE(hq + 2); hq += 4;
    const t = { size, idx, peak: null, start: null, end: null };
    const rd = () => { const a = []; for (let k = 0; k < ax; k++) { a.push(d.readInt16BE(hq)); hq += 2; } return a; };
    if (idx & 0x8000) t.peak = rd();
    if (idx & 0x4000) { t.start = rd(); t.end = rd(); }
    heads.push(t);
  }
  let shared = null;
  if (tc & 0x8000) { const r = readPoints(d, dq); shared = r.pts; dq = r.q; }
  const res = [];
  for (const t of heads) {
    let q = dq; const endq = dq + t.size; let pts = shared;
    if (t.idx & 0x2000) { const r = readPoints(d, q); pts = r.pts; q = r.q; }
    const n = pts ? pts.length : numPoints;
    const xr = readDeltas(d, q, n); const yr = readDeltas(d, xr.q, n);
    dq = endq;
    const dx = new Array(numPoints).fill(null), dy = new Array(numPoints).fill(null);
    for (let i = 0; i < n; i++) { const p = pts ? pts[i] : i; dx[p] = xr.out[i]; dy[p] = yr.out[i]; }
    iup(dx, dy, contours);
    for (let i = 0; i < numPoints; i++) { if (dx[i] === null) dx[i] = 0; if (dy[i] === null) dy[i] = 0; }
    res.push({ idx: t.idx, peak: t.peak, start: t.start, end: t.end, dx, dy });
  }
  return res;
}
function iup(dx, dy, contours) {
  let s = 0;
  for (const c of contours) {
    const n = c.length, idx = []; for (let i = 0; i < n; i++) if (dx[s + i] !== null) idx.push(i);
    if (idx.length === 0) { s += n; continue; }
    if (idx.length === 1) { for (let i = 0; i < n; i++) if (dx[s + i] === null) { dx[s + i] = dx[s + idx[0]]; dy[s + i] = dy[s + idx[0]]; } s += n; continue; }
    for (let k = 0; k < idx.length; k++) {
      const a = idx[k], bI = idx[(k + 1) % idx.length];
      for (let i = (a + 1) % n; i !== bI; i = (i + 1) % n) {
        for (const [ax, dd] of [['x', dx], ['y', dy]]) {
          const ca = c[a][ax], cb = c[bI][ax], ci = c[i][ax], da = dd[s + a], db = dd[s + bI];
          let lo = ca, hi = cb, dlo = da, dhi = db; if (lo > hi) { [lo, hi] = [hi, lo]; [dlo, dhi] = [dhi, dlo]; }
          let v; if (ci <= lo) v = dlo; else if (ci >= hi) v = dhi; else if (lo === hi) v = da === db ? da : 0; else v = dlo + (dhi - dlo) * (ci - lo) / (hi - lo);
          dd[s + i] = v;
        }
      }
    }
    s += n;
  }
}
function packDeltas(arr) {
  const out = []; let i = 0;
  const fitsByte = v => v >= -128 && v <= 127;
  while (i < arr.length) {
    if (arr[i] === 0) { let j = i; while (j < arr.length && arr[j] === 0 && j - i < 64) j++; out.push(0x80 | (j - i - 1)); i = j; continue; }
    if (fitsByte(arr[i])) { let j = i; while (j < arr.length && j - i < 64 && arr[j] !== 0 && fitsByte(arr[j])) j++; out.push(j - i - 1); for (let k = i; k < j; k++) out.push(arr[k] & 255); i = j; }
    else { let j = i; while (j < arr.length && j - i < 64 && !fitsByte(arr[j])) j++; out.push(0x40 | (j - i - 1)); for (let k = i; k < j; k++) out.push((arr[k] >> 8) & 255, arr[k] & 255); i = j; }
  }
  return Buffer.from(out);
}
function encodeGlyphVar(tuples) {
  if (!tuples.length) return Buffer.alloc(0);
  const heads = [], datas = [];
  for (const t of tuples) {
    const data = Buffer.concat([packDeltas(t.dx.map(Math.round)), packDeltas(t.dy.map(Math.round))]);
    const idx = t.idx & ~0x2000; // all tuples use the shared "all points" set
    const words = [];
    if (idx & 0x8000) words.push(...t.peak);
    if (idx & 0x4000) words.push(...t.start, ...t.end);
    const hb = Buffer.alloc(4 + 2 * words.length); hb.writeUInt16BE(data.length, 0); hb.writeUInt16BE(idx, 2); words.forEach((w, k) => hb.writeInt16BE(w, 4 + 2 * k));
    heads.push(hb); datas.push(data);
  }
  const hdr = Buffer.concat(heads);
  const top = Buffer.alloc(4); top.writeUInt16BE(0x8000 | tuples.length, 0); top.writeUInt16BE(4 + hdr.length, 2);
  let out = Buffer.concat([top, hdr, Buffer.from([0]), ...datas]);
  if (out.length & 1) out = Buffer.concat([out, Buffer.alloc(1)]);
  return out;
}
function pad4(b) { return b.length % 4 ? Buffer.concat([b, Buffer.alloc(4 - b.length % 4)]) : b; }
// Replace glyph outlines + variations, rebuild glyf/loca/gvar.
function replaceGlyphs(f, repl /* {gi: {glyf: Buffer, gvar: Buffer}} */) {
  const n = glyphCount(f);
  const glyfs = [], gvars = [];
  for (let g = 0; g < n; g++) {
    glyfs.push(repl[g] ? repl[g].glyf : glyphData(f, g));
    gvars.push(repl[g] ? repl[g].gvar : gvarSlice(f, g));
  }
  const gparts = [], loca = Buffer.alloc(4 * (n + 1)); let off = 0;
  glyfs.forEach((d, g) => { loca.writeUInt32BE(off, 4 * g); const p = pad4(d); gparts.push(p); off += p.length; });
  loca.writeUInt32BE(off, 4 * n);
  const h = gvarHeader(f), og = f.tables.gvar;
  f.tables.glyf = Buffer.concat(gparts); f.tables.loca = loca;
  const head = Buffer.from(f.tables.head); head.writeInt16BE(1, 50); f.tables.head = head;
  const shared = og.subarray(h.sharedOff, h.sharedOff + 2 * h.axisCount * h.sharedCount);
  const hdrLen = 20 + 4 * (n + 1);
  const offs = Buffer.alloc(4 * (n + 1)); let vo = 0;
  gvars.forEach((d, g) => { offs.writeUInt32BE(vo, 4 * g); vo += d.length; }); offs.writeUInt32BE(vo, 4 * n);
  const hb = Buffer.alloc(20);
  og.copy(hb, 0, 0, 8);
  hb.writeUInt32BE(hdrLen, 8); hb.writeUInt16BE(n, 12); hb.writeUInt16BE(h.flags | 1, 14); hb.writeUInt32BE(hdrLen + shared.length, 16);
  f.tables.gvar = Buffer.concat([hb, offs, shared, ...gvars]);
}
function checksum(b) { const p = pad4(b); let s = 0; for (let i = 0; i < p.length; i += 4) s = (s + p.readUInt32BE(i)) >>> 0; return s; }
function writeFont(f, path) {
  const tags = Object.keys(f.tables).sort();
  const n = tags.length; let es = 0; while ((1 << (es + 1)) <= n) es++;
  const dir = Buffer.alloc(12 + 16 * n);
  dir.writeUInt32BE(f.sfntVersion, 0); dir.writeUInt16BE(n, 4); dir.writeUInt16BE(16 * (1 << es), 6); dir.writeUInt16BE(es, 8); dir.writeUInt16BE(16 * n - 16 * (1 << es), 10);
  const head = Buffer.from(f.tables.head); head.writeUInt32BE(0, 8); f.tables.head = head;
  let off = dir.length; const parts = [];
  tags.forEach((t, i) => { const d = f.tables[t]; const r = 12 + 16 * i; dir.write(t, r, 'latin1'); dir.writeUInt32BE(checksum(d), r + 4); dir.writeUInt32BE(off, r + 8); dir.writeUInt32BE(d.length, r + 12); const p = pad4(d); parts.push(p); off += p.length; });
  const all = Buffer.concat([dir, ...parts]);
  const adj = (0xB1B0AFBA - checksum(all)) >>> 0;
  const ho = all.readUInt32BE(12 + 16 * tags.indexOf('head') + 8); all.writeUInt32BE(adj, ho + 8);
  fs.writeFileSync(path, all);
}
module.exports = { readFont, glyphData, parseSimple, encodeSimple, parseGlyphVar, encodeGlyphVar, replaceGlyphs, writeFont, gvarHeader };
