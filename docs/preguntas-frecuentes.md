# Preguntas frecuentes

**¿Ocurren eventos solos?**
No. Todo lo lanza un administrador: con un comando, desde la consola, un bloque de comandos, una función, una
[secuencia](secuencias-y-programacion.md) o un evento [programado](secuencias-y-programacion.md#programar-con-hora-real-le-schedule).

**¿Los jugadores necesitan el mod?**
Sí, en el cliente también (registra contenido propio). Un cliente con otra versión del protocolo recibe un mensaje
claro para actualizar.

**¿Puedo deshacer lo que ha destruido un evento?**
Sí: `/le revert last confirm` (o el número de la instancia). Funciona aunque reinicies el servidor y no toca lo que
los jugadores hayan construido encima. Ver [Guía para administradores](guia-administradores.md#deshacer-cambios-en-el-mundo-le-revert).

**¿Alguien puede morir?**
Depende del evento (`lethal`). La aurora y el eclipse son no letales; en una serie, crea una variante con
`"lethal": false` y el daño nunca bajará a nadie de medio corazón. En hardcore, los eventos letales piden `confirm`.

**Un jugador se marea con los efectos.**
Que pulse su tecla de pánico (*Controles → LiveEvents*) o ponga `max_profile` en `safe` en sus opciones. El admin
tiene `/le panic`.

**No veo nada al lanzar un evento.**
Comprueba que estás en la misma dimensión, que no tienes el HUD oculto (F1: los efectos se dibujan con el HUD) y que
tu cliente tiene LiveEvents. `/le debug timeline` muestra si el evento avanza.

**¿Por qué no se puede lanzar la supernova en el Nether?**
No tiene cielo. Los eventos celestes se rechazan ahí; los localizados (meteoritos, terremoto) sí se pueden si añades
el Nether a `allowed_dimensions`.

**"Ya hay un evento celeste en esta dimensión".**
Solo puede haber uno celeste por dimensión. Añade `queue` para que espere su turno.

**¿Cómo creo mi propio evento?**
Copia el [Example Pack](https://github.com/AloneX15/LiveEvents-mod/tree/main/examples/LiveEvents-Example-Pack) y sigue
[Crear eventos](crear-eventos.md).

**¿Funciona con Iris y shaders?**
Sí: los efectos se dibujan en el HUD y no dependen del cielo ni del post-procesado de vanilla.

**¿Envía datos a algún sitio?**
No hay telemetría. Solo sale información si configuras un webhook de Discord.

**¿Cómo reporto un error?**
Abre un [issue](https://github.com/AloneX15/LiveEvents-mod/issues) y adjunta el archivo de `/le debug report`. Los
problemas de fotosensibilidad tienen prioridad máxima. Las vulnerabilidades, por el
[reporte privado](https://github.com/AloneX15/LiveEvents-mod/security/advisories/new).

---

Creado por **TakumiStudios**.
