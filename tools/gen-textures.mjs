// Genera las texturas de LiveEvents de forma procedural (sin dependencias). Son obra propia, CC0.
// Uso: node tools/gen-textures.mjs
import { writeFileSync, mkdirSync } from "node:fs";
import { deflateSync } from "node:zlib";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const out = join(root, "src/main/resources/assets/liveevents/textures/effect");
mkdirSync(out, { recursive: true });

const CRC = new Uint32Array(256).map((_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});
function crc32(buf) {
  let c = 0xffffffff;
  for (const b of buf) c = CRC[(c ^ b) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type, "ascii"), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}
function png(w, h, pixel) {
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) {
    raw[y * (w * 4 + 1)] = 0;
    for (let x = 0; x < w; x++) {
      const [r, g, b, a] = pixel(x, y);
      const o = y * (w * 4 + 1) + 1 + x * 4;
      raw[o] = clamp255(r); raw[o + 1] = clamp255(g); raw[o + 2] = clamp255(b); raw[o + 3] = clamp255(a);
    }
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ihdr),
    chunk("IDAT", deflateSync(raw, { level: 9 })), chunk("IEND", Buffer.alloc(0))]);
}
const clamp255 = (v) => Math.max(0, Math.min(255, Math.round(v)));
const smooth = (t) => t * t * (3 - 2 * t);

// Ruido de valor determinista
function hash(x, y, s) {
  let h = (x * 374761393 + y * 668265263 + s * 2147483647) | 0;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return ((h ^ (h >>> 16)) >>> 0) / 4294967295;
}
function noise(x, y, s, period) {
  const xi = Math.floor(x), yi = Math.floor(y), fx = smooth(x - xi), fy = smooth(y - yi);
  const w = (v) => ((v % period) + period) % period;
  const a = hash(w(xi), w(yi), s), b = hash(w(xi + 1), w(yi), s), c = hash(w(xi), w(yi + 1), s), d = hash(w(xi + 1), w(yi + 1), s);
  return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy;
}
function fbm(x, y, s, oct, period) {
  let sum = 0, amp = 0.5, norm = 0, f = 1;
  for (let o = 0; o < oct; o++) { sum += amp * noise(x * f, y * f, s + o * 31, period * f); norm += amp; amp *= 0.5; f *= 2; }
  return sum / norm;
}
const save = (name, w, h, fn) => { writeFileSync(join(out, name), png(w, h, fn)); console.log("  " + name); };

console.log("Texturas en " + out);
// Halo radial suave (bloom falso, cuerpos celestes)
save("glow.png", 128, 128, (x, y) => {
  const d = Math.hypot(x - 63.5, y - 63.5) / 64;
  return [255, 255, 255, 255 * Math.exp(-d * d * 4.5) * (d < 1 ? 1 : 0)];
});
// Núcleo estelar nítido
save("core.png", 64, 64, (x, y) => {
  const d = Math.hypot(x - 31.5, y - 31.5) / 32;
  return [255, 255, 255, 255 * Math.max(0, 1 - Math.pow(d, 0.6)) ** 1.5];
});
// Anillo suave (ondas de choque, anillos de vapor)
save("ring.png", 128, 128, (x, y) => {
  const d = Math.hypot(x - 63.5, y - 63.5) / 64;
  return [255, 255, 255, 255 * Math.exp(-Math.pow((d - 0.85) / 0.07, 2))];
});
// Flare anamórfico horizontal
save("flare.png", 256, 32, (x, y) => {
  const dx = (x - 127.5) / 128, dy = (y - 15.5) / 16;
  return [255, 255, 255, 255 * Math.exp(-dx * dx * 3) * Math.exp(-dy * dy * 12)];
});
// Nube de ruido (nebulosa, plasma), con bordes suaves y repetible
save("cloud.png", 256, 256, (x, y) => {
  const n = fbm(x / 32, y / 32, 7, 5, 8);
  const d = Math.hypot(x - 127.5, y - 127.5) / 128;
  const edge = Math.max(0, 1 - d * d);
  return [255, 255, 255, 255 * Math.pow(Math.max(0, n * 1.6 - 0.45), 1.3) * edge];
});
// Filamentos turbulentos (plasma con "tentáculos")
save("filaments.png", 256, 256, (x, y) => {
  const n = fbm(x / 24, y / 24, 99, 5, 10.6667);
  const ridge = 1 - Math.abs(n * 2 - 1);
  const d = Math.hypot(x - 127.5, y - 127.5) / 128;
  return [255, 255, 255, 255 * Math.pow(ridge, 6) * Math.max(0, 1 - d)];
});
// Cortina de aurora: borde inferior brillante que se desvanece hacia arriba, con rayas verticales
save("aurora.png", 128, 256, (x, y) => {
  const v = 1 - y / 255;
  const streak = 0.55 + 0.45 * fbm(x / 6, 0.5, 3, 3, 21.333);
  const base = Math.exp(-Math.pow((y - 210) / 26, 2)) * 0.9 + Math.pow(Math.max(0, (y - 40) / 215), 2) * 0.6;
  const sideFade = Math.sin((x / 127) * Math.PI);
  return [255, 255, 255, 255 * base * streak * sideFade * (v > 0 ? 1 : 0)];
});
// Viñeta (negra en los bordes)
save("vignette.png", 256, 256, (x, y) => {
  const dx = (x - 127.5) / 128, dy = (y - 127.5) / 128;
  const d = Math.min(1, Math.hypot(dx, dy) / Math.SQRT2 * 1.25);
  return [0, 0, 0, 255 * Math.pow(smooth(d), 2.2)];
});
// Suciedad y rayas de lente quemada
save("lens_dirt.png", 256, 256, (x, y) => {
  const blobs = Math.max(0, fbm(x / 20, y / 20, 51, 4, 12.8) - 0.55) * 3;
  const scratch = Math.max(0, 1 - Math.abs(((x * 0.7 + y * 0.3) % 37) - 18) / 0.8) * (hash(Math.floor((x * 0.7 + y * 0.3) / 37), 0, 5) > 0.7 ? 0.5 : 0);
  return [255, 250, 240, 255 * Math.min(1, blobs * 0.6 + scratch * 0.4)];
});
// Rayo de faro del púlsar (haz que se estrecha)
save("beam.png", 64, 256, (x, y) => {
  const t = y / 255;
  const width = 0.15 + 0.85 * t;
  const dx = (x - 31.5) / 32 / width;
  return [255, 255, 255, 255 * Math.exp(-dx * dx * 4) * (1 - t * 0.8)];
});

// Icono del mod: supernova estilizada sobre cielo nocturno
const icon = join(root, "src/main/resources/assets/liveevents/icon.png");
writeFileSync(icon, png(256, 256, (x, y) => {
  const cx = x - 127.5, cy = y - 127.5, d = Math.hypot(cx, cy) / 128, a = Math.atan2(cy, cx);
  // fondo: degradado nocturno con estrellas
  let r = 10 + 20 * (1 - d), g = 14 + 10 * (1 - d), b = 26 + 40 * (1 - d);
  if (hash(x, y, 3) > 0.996) { r = g = b = 230; }
  // nebulosa roja/turquesa
  const neb = fbm(x / 40, y / 40, 11, 4, 6.4);
  r += 160 * Math.max(0, neb - 0.5) * (1 - d); b += 140 * Math.max(0, 0.55 - neb) * (1 - d) * 0.8; g += 90 * Math.max(0, 0.55 - neb) * (1 - d) * 0.8;
  // rayos
  const rays = Math.pow(Math.abs(Math.cos(a * 6)), 40) * Math.exp(-d * 2.2) + Math.pow(Math.abs(Math.cos(a * 6 + 0.26)), 60) * Math.exp(-d * 3);
  // halo y núcleo
  const halo = Math.exp(-d * d * 18), core = Math.exp(-d * d * 120);
  r += 255 * (halo * 0.9 + rays * 0.8 + core); g += 255 * (halo * 0.55 + rays * 0.9 + core); b += 255 * (halo * 0.95 + rays + core);
  // anillo de choque cian
  const ring = Math.exp(-Math.pow((d - 0.62) / 0.03, 2)) * 0.8;
  g += 220 * ring; b += 255 * ring; r += 60 * ring;
  // esquinas redondeadas
  const corner = Math.max(Math.abs(cx), Math.abs(cy)) > 124 && Math.hypot(Math.max(0, Math.abs(cx) - 100), Math.max(0, Math.abs(cy) - 100)) > 27.5 ? 0 : 255;
  return [r, g, b, corner];
}));
console.log("  icon.png");
