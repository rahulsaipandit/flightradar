#!/usr/bin/env node
// Packages chromeExtension/ into a Chrome Web Store-ready zip.
//
// Written as a dependency-free ZIP writer (not PowerShell's Compress-Archive, not a `zip`
// shell-out) because Compress-Archive on Windows/.NET Framework stores entry paths with
// backslashes (e.g. "aircraft-icons\aircraft_airliner.png") instead of the ZIP spec's required
// forward slashes — that silently produces a package the Web Store (and unzip on non-Windows)
// can misread as flattened files instead of nested folders. Building the archive by hand keeps
// path separators correct regardless of what OS/shell this runs under.

const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const EXTENSION_DIR = path.resolve(__dirname, '..');
const DEFAULT_OUTPUT = path.resolve(EXTENSION_DIR, '..', 'flightpulse-extension.zip');

// Not part of the shipped extension — this packaging tooling itself, and editor/VCS cruft.
const EXCLUDED_TOP_LEVEL = new Set(['scripts', 'package.json', 'package-lock.json', 'node_modules', '.git']);

function collectFiles(dir, baseDir, out) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const fullPath = path.join(dir, entry.name);
    const relFromBase = path.relative(baseDir, fullPath);
    const topLevel = relFromBase.split(path.sep)[0];
    if (EXCLUDED_TOP_LEVEL.has(topLevel)) continue;

    if (entry.isDirectory()) {
      collectFiles(fullPath, baseDir, out);
    } else if (entry.isFile()) {
      out.push(fullPath);
    }
  }
  return out;
}

// --- ZIP writer (store + deflate, per the PKZIP APPNOTE.TXT format) ---

const CRC_TABLE = (() => {
  const table = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) {
      c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    }
    table[n] = c >>> 0;
  }
  return table;
})();

function crc32(buffer) {
  let crc = 0xffffffff;
  for (let i = 0; i < buffer.length; i++) {
    crc = CRC_TABLE[(crc ^ buffer[i]) & 0xff] ^ (crc >>> 8);
  }
  return (crc ^ 0xffffffff) >>> 0;
}

// DOS date/time encoding used by the ZIP format's mod-time fields.
function toDosDateTime(date) {
  const dosTime =
    ((date.getHours() & 0x1f) << 11) | ((date.getMinutes() & 0x3f) << 5) | ((date.getSeconds() >> 1) & 0x1f);
  const dosDate =
    (((date.getFullYear() - 1980) & 0x7f) << 9) | (((date.getMonth() + 1) & 0xf) << 5) | (date.getDate() & 0x1f);
  return { dosTime, dosDate };
}

function buildZip(files, baseDir) {
  const localChunks = [];
  const centralChunks = [];
  let offset = 0;
  const { dosTime, dosDate } = toDosDateTime(new Date());

  for (const filePath of files) {
    const entryName = path.relative(baseDir, filePath).split(path.sep).join('/');
    const data = fs.readFileSync(filePath);
    const crc = crc32(data);
    const deflated = zlib.deflateRawSync(data, { level: zlib.constants.Z_BEST_COMPRESSION });
    // Only use the deflated form if it's actually smaller — store raw otherwise (e.g. tiny files).
    const useDeflate = deflated.length < data.length;
    const method = useDeflate ? 8 : 0;
    const payload = useDeflate ? deflated : data;

    const nameBuf = Buffer.from(entryName, 'utf8');
    const localHeader = Buffer.alloc(30);
    localHeader.writeUInt32LE(0x04034b50, 0);
    localHeader.writeUInt16LE(20, 4); // version needed
    localHeader.writeUInt16LE(0, 6); // flags
    localHeader.writeUInt16LE(method, 8);
    localHeader.writeUInt16LE(dosTime, 10);
    localHeader.writeUInt16LE(dosDate, 12);
    localHeader.writeUInt32LE(crc, 14);
    localHeader.writeUInt32LE(payload.length, 18);
    localHeader.writeUInt32LE(data.length, 22);
    localHeader.writeUInt16LE(nameBuf.length, 26);
    localHeader.writeUInt16LE(0, 28); // extra field length

    localChunks.push(localHeader, nameBuf, payload);

    const centralHeader = Buffer.alloc(46);
    centralHeader.writeUInt32LE(0x02014b50, 0);
    centralHeader.writeUInt16LE(20, 4); // version made by
    centralHeader.writeUInt16LE(20, 6); // version needed
    centralHeader.writeUInt16LE(0, 8); // flags
    centralHeader.writeUInt16LE(method, 10);
    centralHeader.writeUInt16LE(dosTime, 12);
    centralHeader.writeUInt16LE(dosDate, 14);
    centralHeader.writeUInt32LE(crc, 16);
    centralHeader.writeUInt32LE(payload.length, 20);
    centralHeader.writeUInt32LE(data.length, 24);
    centralHeader.writeUInt16LE(nameBuf.length, 28);
    centralHeader.writeUInt16LE(0, 30); // extra field length
    centralHeader.writeUInt16LE(0, 32); // comment length
    centralHeader.writeUInt16LE(0, 34); // disk number start
    centralHeader.writeUInt16LE(0, 36); // internal attributes
    centralHeader.writeUInt32LE((0o100644 << 16) >>> 0, 38); // external attributes (unix -rw-r--r--)
    centralHeader.writeUInt32LE(offset, 42);

    centralChunks.push(centralHeader, nameBuf);

    offset += localHeader.length + nameBuf.length + payload.length;
  }

  const centralDirStart = offset;
  const centralDirBuf = Buffer.concat(centralChunks);

  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(0, 4); // disk number
  end.writeUInt16LE(0, 6); // disk with central dir
  end.writeUInt16LE(files.length, 8); // entries on this disk
  end.writeUInt16LE(files.length, 10); // total entries
  end.writeUInt32LE(centralDirBuf.length, 12);
  end.writeUInt32LE(centralDirStart, 16);
  end.writeUInt16LE(0, 20); // comment length

  return Buffer.concat([...localChunks, centralDirBuf, end]);
}

function main() {
  const outputPath = process.argv[2] ? path.resolve(process.argv[2]) : DEFAULT_OUTPUT;
  const files = collectFiles(EXTENSION_DIR, EXTENSION_DIR, []);
  if (!files.some((f) => path.relative(EXTENSION_DIR, f) === 'manifest.json')) {
    throw new Error('manifest.json not found at the extension root — refusing to package.');
  }

  const zipBuffer = buildZip(files, EXTENSION_DIR);
  fs.writeFileSync(outputPath, zipBuffer);

  console.log(`Packaged ${files.length} files into ${outputPath} (${zipBuffer.length} bytes)`);
}

main();
