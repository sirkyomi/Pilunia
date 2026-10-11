import { readFile, readdir, stat } from 'node:fs/promises';
import { dirname, resolve, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../website/', import.meta.url));
const errors = [];
let checked = 0;

async function filesIn(directory) {
  const files = [];
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const path = resolve(directory, entry.name);
    files.push(...(entry.isDirectory() ? await filesIn(path) : [path]));
  }
  return files;
}

async function checkReference(source, reference) {
  if (/^(https?:|mailto:|tel:|data:)/i.test(reference)) return;
  if (reference.startsWith('/')) {
    errors.push(`${relative(root, source)}: use a relative URL for project Pages: ${reference}`);
    return;
  }
  const [pathname, fragment] = reference.split('#');
  const pathWithoutQuery = decodeURIComponent((pathname || '').split('?')[0]);
  let destination = pathWithoutQuery ? resolve(dirname(source), pathWithoutQuery) : source;
  if (relative(root, destination).startsWith('..')) {
    errors.push(`${relative(root, source)}: URL leaves website artifact: ${reference}`);
    return;
  }
  try {
    if ((await stat(destination)).isDirectory()) destination = resolve(destination, 'index.html');
    if (!(await stat(destination)).isFile()) throw new Error('not a file');
    if (fragment && /\.(html|svg)$/.test(destination)) {
      const text = await readFile(destination, 'utf8');
      if (!text.includes(`id="${fragment}"`) && !text.includes(`id='${fragment}'`)) {
        throw new Error(`missing fragment #${fragment}`);
      }
    }
    checked++;
  } catch (error) {
    errors.push(`${relative(root, source)}: ${reference}: ${error.message}`);
  }
}

for (const file of await filesIn(root)) {
  if (!/\.(html|css)$/.test(file)) continue;
  const source = await readFile(file, 'utf8');
  if (file.endsWith('.html')) {
    if (!/<html\s+lang="de"/.test(source)) errors.push(`${file}: missing German document language`);
    if ((source.match(/<h1\b/g) || []).length !== 1) errors.push(`${file}: expected one h1`);
    for (const image of source.matchAll(/<img\b[^>]*>/g)) {
      if (!/\balt="[^"]*"/.test(image[0])) errors.push(`${file}: image without alt`);
      if (!/\bwidth="\d+"/.test(image[0]) || !/\bheight="\d+"/.test(image[0])) errors.push(`${file}: image without intrinsic dimensions`);
    }
    for (const ref of source.matchAll(/\b(?:href|src)="([^"]+)"/g)) await checkReference(file, ref[1]);
  } else {
    for (const ref of source.matchAll(/url\(["']?([^"')]+)["']?\)/g)) await checkReference(file, ref[1]);
  }
}
if (errors.length) {
  console.error(errors.join('\n'));
  process.exitCode = 1;
} else {
  console.log(`Website checked: ${checked} local links, anchors and assets resolve.`);
}
