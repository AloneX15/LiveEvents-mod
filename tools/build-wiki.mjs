// Genera la wiki de GitHub (carpeta wiki/) a partir de docs/: nombres de página, enlaces, barra lateral e imágenes.
// Uso: node tools/build-wiki.mjs   ·   Publicar: bash tools/publish-wiki.sh
import { readFileSync, writeFileSync, mkdirSync, readdirSync, copyFileSync, rmSync, existsSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const docs = join(root, "docs");
const wiki = join(root, "wiki");

// docs/<archivo> → página de la wiki (orden = barra lateral)
const PAGES = [
  ["inicio.md", "Home", "Inicio"],
  ["instalacion.md", "Instalacion", "Instalación"],
  ["guia-administradores.md", "Guia-para-administradores", "Guía para administradores"],
  ["comandos.md", "Comandos", "Comandos"],
  ["permisos.md", "Permisos", "Permisos"],
  ["configuracion.md", "Configuracion", "Configuración"],
  ["eventos-oficiales.md", "Eventos-oficiales", "Eventos oficiales"],
  ["perfiles-y-accesibilidad.md", "Perfiles-y-accesibilidad", "Perfiles y accesibilidad"],
  ["crear-eventos.md", "Crear-eventos", "Crear eventos"],
  ["modulos.md", "Referencia-de-modulos", "Referencia de módulos"],
  ["secuencias-y-programacion.md", "Secuencias-y-programacion", "Secuencias y programación"],
  ["api-addons.md", "API-para-addons", "API para addons"],
  ["rendimiento-y-compatibilidad.md", "Rendimiento-y-compatibilidad", "Rendimiento y compatibilidad"],
  ["preguntas-frecuentes.md", "Preguntas-frecuentes", "Preguntas frecuentes"],
  ["estado-del-plan.md", "Estado-del-plan", "Estado del plan"],
  ["registro-de-errores.md", "Registro-de-errores", "Registro de errores"],
];
const pageOf = Object.fromEntries(PAGES.map(([file, page]) => [file, page]));

if (existsSync(wiki)) {
  rmSync(wiki, { recursive: true });
}
mkdirSync(join(wiki, "images"), { recursive: true });

for (const [file, page] of PAGES) {
  let text = readFileSync(join(docs, file), "utf8");
  // Enlaces entre páginas: (comandos.md#ancla) → (Comandos#ancla)
  text = text.replace(/\]\(([a-z0-9-]+\.md)(#[^)]*)?\)/g, (m, target, anchor) =>
    pageOf[target] ? `](${pageOf[target]}${anchor ?? ""})` : m);
  writeFileSync(join(wiki, page + ".md"), text);
}

for (const img of readdirSync(join(docs, "images"))) {
  copyFileSync(join(docs, "images", img), join(wiki, "images", img));
}

const sections = [
  ["Empezar", ["Home", "Instalacion", "Guia-para-administradores", "Comandos", "Permisos", "Configuracion"]],
  ["Eventos", ["Eventos-oficiales", "Perfiles-y-accesibilidad"]],
  ["Crear", ["Crear-eventos", "Referencia-de-modulos", "Secuencias-y-programacion", "API-para-addons"]],
  ["Más", ["Rendimiento-y-compatibilidad", "Preguntas-frecuentes", "Estado-del-plan", "Registro-de-errores"]],
];
const titleOf = Object.fromEntries(PAGES.map(([, page, title]) => [page, title]));
let sidebar = "![LiveEvents](images/icon.png)\n\n**[LiveEvents](Home)**\n\n";
for (const [name, pages] of sections) {
  sidebar += `**${name}**\n\n` + pages.map((p) => `- [${titleOf[p]}](${p})`).join("\n") + "\n\n";
}
sidebar += "[Descargas](https://github.com/AloneX15/LiveEvents-mod/releases) · [Issues](https://github.com/AloneX15/LiveEvents-mod/issues)\n";
writeFileSync(join(wiki, "_Sidebar.md"), sidebar);
writeFileSync(join(wiki, "_Footer.md"), "LiveEvents · Minecraft 26.x · Fabric — Creado por **TakumiStudios**.\n");
console.log(`Wiki generada en ${wiki}: ${PAGES.length} páginas`);
