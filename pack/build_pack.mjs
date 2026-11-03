// Uncharted Pack builder v2 (Phase 2).
// Streams EDSM's systemsWithCoordinates.json.gz, extracts (x, y, z) as 3x
// float32 (12 B/record), sorts by x, and writes:
//   - the full pack:  data/pack/stellar-pack.bin
//   - 8 x-slices:     data/pack/stellar-pack-s0.bin ... s7.bin (~75 MB each)
//   - a manifest:     data/pack/stellar-pack.manifest.json
// Header v2: magic "SLPK1" (5B) | version u8=2 | count u32 | bounds 6xf32 |
//            cellSizeLy u32 | reserved u32 | builtAtMs u64 | reserved u64
// Records: x f32 | y f32 | z f32 (little-endian), sorted by x ascending.
//
// Run: node --max-old-space-size=4096 tools/build_pack.mjs
import fs from "node:fs";
import https from "node:https";
import zlib from "node:zlib";

const URL = "https://www.edsm.net/dump/systemsWithCoordinates.json.gz";
const OUT_DIR = process.env.PACK_OUT_DIR || "data/pack";
const CELL = 32;
const SLICES = 16;
const CAP_STEP = 1 << 20;
const VERSION = 2;
const SKIP_FULL = process.env.SKIP_FULL === "1";

fs.mkdirSync(OUT_DIR, { recursive: true });

const t0 = Date.now();
let count = 0;
let skipped = 0;
let cap = CAP_STEP;
let fx = new Float32Array(cap);
let fy = new Float32Array(cap);
let fz = new Float32Array(cap);

function grow() {
  cap += CAP_STEP;
  const nx = new Float32Array(cap); nx.set(fx); fx = nx;
  const ny = new Float32Array(cap); ny.set(fy); fy = ny;
  const nz = new Float32Array(cap); nz.set(fz); fz = nz;
}

function flushRecord(recText) {
  let obj;
  try {
    obj = JSON.parse(recText, (k, v) => (k === "id64" ? BigInt(v) : v));
  } catch {
    skipped++;
    return;
  }
  const c = obj.coords;
  if (
    !c ||
    typeof c.x !== "number" || typeof c.y !== "number" || typeof c.z !== "number" ||
    !Number.isFinite(c.x) || !Number.isFinite(c.y) || !Number.isFinite(c.z)
  ) {
    skipped++;
    return;
  }
  if (count === cap) grow();
  fx[count] = Math.fround(c.x);
  fy[count] = Math.fround(c.y);
  fz[count] = Math.fround(c.z);
  count++;
  if (count % 1000000 === 0) {
    console.log(`parsed ${count} records, skipped ${skipped}, ${Math.round((Date.now() - t0) / 1000)}s`);
  }
}

let pending = "";
function onData(s) {
  pending += s;
  let depth = 0;
  let start = -1;
  let lastEnd = -1;
  for (let i = 0; i < pending.length; i++) {
    const ch = pending[i];
    if (ch === "{") {
      if (depth === 0) start = i;
      depth++;
    } else if (ch === "}") {
      if (depth > 0) depth--;
      if (depth === 0 && start >= 0) {
        flushRecord(pending.slice(start, i + 1));
        lastEnd = i + 1;
        start = -1;
      }
    }
  }
  pending = lastEnd >= 0 ? pending.slice(lastEnd) : pending.slice(0, 2000);
}

console.log("downloading " + URL + " ...");

function resetState() {
  count = 0;
  skipped = 0;
  pending = "";
}

async function downloadAndParse(maxAttempts = 4) {
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    resetState();
    try {
      await new Promise((resolve, reject) => {
        const req = https.get(URL, { headers: { "User-Agent": "StellarLogbook-pack-builder/0.2" } }, (res) => {
          if (res.statusCode !== 200) {
            reject(new Error(`HTTP ${res.statusCode} downloading ${URL}`));
            res.resume();
            return;
          }
          const gunzip = zlib.createGunzip();
          res.pipe(gunzip);
          gunzip.on("data", (chunk) => onData(chunk.toString("utf8")));
          gunzip.on("end", resolve);
          gunzip.on("error", reject);
          res.on("error", reject);
        });
        req.on("error", reject);
      });
      console.log(`parse complete: ${count} records, ${skipped} skipped`);
      return;
    } catch (e) {
      console.log(`download attempt ${attempt}/${maxAttempts} failed: ${e.message}`);
      if (attempt === maxAttempts) throw e;
      const waitMs = attempt * 30000;
      console.log(`retrying in ${waitMs / 1000}s...`);
      await new Promise((r) => setTimeout(r, waitMs));
    }
  }
}

await downloadAndParse();

console.log(`sorting ${count} records by x...`);
const order = new Uint32Array(count);
for (let i = 0; i < count; i++) order[i] = i;
order.sort((a, b) => fx[a] - fx[b]);
console.log(`sort done in ${Math.round((Date.now() - t0) / 1000)}s`);

function writePackFile(path, indices) {
  const n = indices.length;
  let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity, minZ = Infinity, maxZ = -Infinity;
  for (const i of indices) {
    const x = fx[i], y = fy[i], z = fz[i];
    if (x < minX) minX = x; if (x > maxX) maxX = x;
    if (y < minY) minY = y; if (y > maxY) maxY = y;
    if (z < minZ) minZ = z; if (z > maxZ) maxZ = z;
  }
  const hdr = Buffer.alloc(58);
  hdr.write("SLPK1", 0, "ascii");
  hdr.writeUInt8(VERSION, 5);
  hdr.writeUInt32LE(n, 6);
  hdr.writeFloatLE(minX, 10); hdr.writeFloatLE(maxX, 14);
  hdr.writeFloatLE(minY, 18); hdr.writeFloatLE(maxY, 22);
  hdr.writeFloatLE(minZ, 26); hdr.writeFloatLE(maxZ, 30);
  hdr.writeUInt32LE(CELL, 34);
  hdr.writeUInt32LE(0, 38);
  hdr.writeBigUInt64LE(BigInt(Date.now()), 42);
  hdr.writeBigUInt64LE(0n, 50);

  // Synchronous writes: never mutate a buffer that an async stream write may
  // still hold (that corrupted the first 1.2 GB full-pack attempt).
  const fd = fs.openSync(path, "w");
  fs.writeSync(fd, hdr);
  const chunk = Buffer.alloc(1 << 22);
  let off = 0;
  for (const i of indices) {
    if (off + 12 > chunk.length) {
      fs.writeSync(fd, chunk, 0, off);
      off = 0;
    }
    chunk.writeFloatLE(fx[i], off);
    chunk.writeFloatLE(fy[i], off + 4);
    chunk.writeFloatLE(fz[i], off + 8);
    off += 12;
  }
  if (off > 0) fs.writeSync(fd, chunk, 0, off);
  fs.closeSync(fd);
}

// full pack (skippable in CI: phones only download slices)
if (!SKIP_FULL) {
  writePackFile(`${OUT_DIR}/stellar-pack.bin`, order);
  console.log(`full pack written (${(fs.statSync(`${OUT_DIR}/stellar-pack.bin`).size / 1e6).toFixed(1)} MB)`);
}

// slices: contiguous ranges of the sorted order
const sliceSize = Math.ceil(count / SLICES);
const slices = [];
for (let s = 0; s < SLICES; s++) {
  const lo = s * sliceSize;
  const hi = Math.min(count, (s + 1) * sliceSize);
  if (lo >= hi) break;
  const indices = order.subarray(lo, hi);
  const file = `stellar-pack-s${s}.bin`;
  writePackFile(`${OUT_DIR}/${file}`, indices);
  slices.push({
    file,
    bytes: fs.statSync(`${OUT_DIR}/${file}`).size,
    count: hi - lo,
    minX: fx[indices[0]],
    maxX: fx[indices[hi - lo - 1]],
  });
  console.log(`slice s${s}: ${file} (${(slices[s].bytes / 1e6).toFixed(1)} MB, x ${slices[s].minX.toFixed(0)}..${slices[s].maxX.toFixed(0)})`);
}

let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity, minZ = Infinity, maxZ = -Infinity;
for (let i = 0; i < count; i++) {
  if (fx[i] < minX) minX = fx[i]; if (fx[i] > maxX) maxX = fx[i];
  if (fy[i] < minY) minY = fy[i]; if (fy[i] > maxY) maxY = fy[i];
  if (fz[i] < minZ) minZ = fz[i]; if (fz[i] > maxZ) maxZ = fz[i];
}

const manifest = {
  builtAt: new Date().toISOString(),
  source: URL,
  version: VERSION,
  recordCount: count,
  cellSizeLy: CELL,
  slices: SLICES,
  bounds: { minX, maxX, minY, maxY, minZ, maxZ },
  full: {
    file: "stellar-pack.bin",
    bytes: fs.statSync(`${OUT_DIR}/stellar-pack.bin`).size,
  },
  slices,
};
fs.writeFileSync(`${OUT_DIR}/stellar-pack.manifest.json`, JSON.stringify(manifest, null, 2));
console.log(`\n=== PACK v${VERSION} BUILT ===`);
console.log(`records: ${count}, skipped: ${skipped}`);
console.log(`bounds: x [${minX.toFixed(0)}, ${maxX.toFixed(0)}] y [${minY.toFixed(0)}, ${maxY.toFixed(0)}] z [${minZ.toFixed(0)}, ${maxZ.toFixed(0)}]`);
console.log(`full: ${(manifest.full.bytes / 1e6).toFixed(1)} MB`);
console.log(`total time: ${Math.round((Date.now() - t0) / 1000)}s`);
