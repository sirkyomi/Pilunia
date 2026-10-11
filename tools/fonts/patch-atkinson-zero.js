// Ersetzt die durchgestrichene 0 in Atkinson Hyperlegible Next durch eine 0 ohne Strich.
// Aufruf: node tools/fonts/patch-atkinson-zero.js <original.ttf> app/src/main/assets/fonts/atkinson_next.ttf
// (Original: https://github.com/google/fonts/tree/main/ofl/atkinsonhyperlegiblenext, Version 2.001)
//
// Die Original-0 besteht aus Außenkontur und zwei Innenhälften (der Strich teilt die Innenform).
// Das untere linke Viertel der Innenform ist vom Strich unberührt; es wird in jedem Master
// (Default + jede gvar-Region) an der Mitte der Innenform in alle vier Viertel gespiegelt.
const T = require('./ttf.js');
const [, , input, output] = process.argv;
const f = T.readFont(input);

const ZERO = 365; // Glyph „zero“; „zero.tf“ ist ein Verweis darauf
const g = T.parseSimple(T.glyphData(f, ZERO));
if (g.contours.length !== 3 || g.contours[1].length !== 11 || g.contours[2].length !== 12) {
  throw new Error('Unerwartete Form der 0 – Schrift ist schon angepasst oder eine andere Version.');
}
const pts = g.contours.flat();
const numPoints = pts.length + 4; // + Phantompunkte
const tuples = T.parseGlyphVar(f, ZERO, g.contours, numPoints);

// Punktindizes der Innenhälften: 16 unten, 23 links, 31 rechts, 36 oben; 24–26 unteres linkes Viertel.
const B = 16, L = 23, R = 31, TOP = 36, LL = [24, 25, 26];

function innerContour(at) {
  const cx = (at(L).x + at(R).x) / 2, cy = (at(B).y + at(TOP).y) / 2;
  const p = (i, on) => ({ x: at(i).x, y: at(i).y, on });
  const mx = (i, on) => ({ x: 2 * cx - at(i).x, y: at(i).y, on });
  const my = (i, on) => ({ x: at(i).x, y: 2 * cy - at(i).y, on });
  const rot = (i, on) => ({ x: 2 * cx - at(i).x, y: 2 * cy - at(i).y, on });
  const [a, b, c] = LL;
  // gegen den Uhrzeigersinn wie die Original-Innenkonturen, Start unten
  return [
    p(B, true), mx(c), mx(b), mx(a),
    mx(L, true), rot(a), rot(b), rot(c),
    rot(B, true), my(c), my(b), my(a),
    p(L, true), p(a), p(b), p(c),
  ].map(q => ({ x: q.x, y: q.y, on: !!q.on }));
}

const outer = g.contours[0];
const baseInner = innerContour(i => pts[i]).map(q => ({ ...q, x: Math.round(q.x), y: Math.round(q.y) }));
const newContours = [outer, baseInner];
const newCount = outer.length + baseInner.length;

const newTuples = tuples.map(t => {
  const at = i => ({ x: pts[i].x + t.dx[i], y: pts[i].y + t.dy[i] });
  const inner = innerContour(at);
  const dx = [], dy = [];
  outer.forEach((_, i) => { dx.push(t.dx[i]); dy.push(t.dy[i]); });
  inner.forEach((q, k) => { dx.push(Math.round(q.x) - baseInner[k].x); dy.push(Math.round(q.y) - baseInner[k].y); });
  for (let k = 4; k > 0; k--) { dx.push(t.dx[numPoints - k]); dy.push(t.dy[numPoints - k]); }
  return { ...t, dx, dy };
});

T.replaceGlyphs(f, { [ZERO]: { glyf: T.encodeSimple(newContours, g.overlap), gvar: T.encodeGlyphVar(newTuples) } });
T.writeFont(f, output);
console.log(`0 angepasst: ${pts.length} → ${newCount} Punkte, ${newTuples.length} Variationen → ${output}`);
