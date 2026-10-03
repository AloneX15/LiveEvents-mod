# Créditos y procedencia de los recursos

Creado por **TakumiStudios**.

## Código

LiveEvents, bajo licencia [MIT](LICENSE).

## Texturas

Todas las texturas de `src/main/resources/assets/liveevents/textures/effect/` y el icono del mod
(`assets/liveevents/icon.png`) se generan de forma procedural con `tools/gen-textures.mjs` (ruido y degradados
propios). Son obra del proyecto y se publican como **CC0**.

| Archivo | Uso |
|---|---|
| `glow.png`, `core.png` | Halos y núcleos de cuerpos celestes, bloom falso |
| `ring.png` | Anillos de vapor y coronas |
| `flare.png` | Flares anamórficos |
| `cloud.png`, `filaments.png` | Nebulosa y plasma |
| `aurora.png` | Cortinas de aurora |
| `beam.png` | Haces del púlsar |
| `vignette.png`, `lens_dirt.png` | Viñeta y suciedad de lente |

## Sonidos

LiveEvents **no incluye archivos de sonido**. Sus 23 eventos de sonido (`assets/liveevents/sounds.json`) apuntan a
eventos de sonido de Minecraft (`"type": "event"`) con otro tono y volumen. Los sonidos pertenecen a Mojang y los
reproduce el propio juego del jugador; no se redistribuyen. Un resource pack puede sustituirlos por `.ogg` propios con
los mismos ids.

## Herramientas

- [Fabric](https://fabricmc.net/) (Loader, API, Loom) y [Stonecutter](https://stonecutter.kikugie.dev/).
- Integraciones opcionales: [Mod Menu](https://modrinth.com/mod/modmenu), [Text Placeholder API](https://modrinth.com/mod/placeholder-api)
  y [LuckPerms](https://luckperms.net/) (a través de la API de permisos de Fabric).
