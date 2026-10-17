# Créditos y procedencia de los recursos

Creado por **TakumiStudios**.

## Código

LiveEvents, bajo licencia [MIT](LICENSE).

## Texturas

Las texturas originales enumeradas abajo, dentro de `src/main/resources/assets/liveevents/textures/effect/` y el icono del mod
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

## Recursos de meteoritos v2

Las escenas, imágenes, atlas, vídeos y mallas de `art/meteor_shower/`, la malla de
`assets/liveevents/meteor/meteor_mesh.json` y las texturas `meteor_*.png` son recursos propios de TakumiStudios,
con **todos los derechos reservados**. Se distribuyen con LiveEvents Mod para su uso en el mod y sus modpacks;
no se concede su redistribución independiente. Esta condición no modifica la licencia CC0 de los recursos previos.

- Escenas, atlas y malla: `art/meteor_shower/create_preview_v2.py`, Blender 5.2.2 LTS.
- Roca y humo neutro: `tools/generate-meteor-textures.py`, semilla 2107.
- Código de los generadores: MIT, como el resto del código del repositorio.
- No se distribuyen recursos de Veil, Photon, packs de shaders ni otros juegos.

## Meteor audio

Original procedural meteor sounds created for LiveEvents by TakumiStudios, licensed under the project MIT license. No external samples. Reproduce with `tools/generate_meteor_audio.py` (Python, numpy, soundfile).
