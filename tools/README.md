# Herramientas

| Script | Qué hace |
|---|---|
| `gen-lang.mjs` | Genera `en_us.json` y `es_es.json` con las mismas claves. **Es la fuente de los textos**: edita aquí y ejecútalo |
| `gen-textures.mjs` | Genera las texturas de efectos y el icono (procedurales, CC0) |
| `build-wiki.mjs` | Convierte `docs/` en la wiki de GitHub (carpeta `wiki/`, ignorada por git) |
| `publish-wiki.sh` | Publica `wiki/` en `AloneX15/LiveEvents-mod.wiki.git` (la wiki debe tener ya una primera página) |

El JSON Schema y `docs/modulos.md` se generan con `./gradlew :26.3:test -PgenerateDocs` (la CI falla si no están al día).
Las capturas de `docs/images/` salen de `./gradlew :26.3:runClientGameTest`.
