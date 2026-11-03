# Instalar Spin con ayuda de una IA

Un prompt para pegar en un asistente de IA con acceso al shell (por ejemplo Claude Code), en un ordenador de la misma red que tu tele. Instala Spin Launcher TV y, si quieres, limpia la tele con [cleanyourtv](https://github.com/Gathin23/cleanyourtv).

Antes, en la tele: Ajustes › Preferencias del dispositivo › Información, pulsa OK siete veces en «Compilación» y activa la **depuración USB** (y la de red si aparece) en Opciones de desarrollador. Spin solo está probado en la **Xiaomi TV Box S de segunda generación** con Android 11.

---

Quiero instalar **Spin Launcher TV** (`https://github.com/enguicode/spin-launcher-tv`) en mi tele con Android TV. Modelo: `<MODELO>`. IP: `<IP_DE_LA_TELE>`. Sigue el `README.md` del repo para compilar, instalar y configurar, trabaja sobre la tele real y comprueba cada paso con una captura (`adb exec-out screencap -p`).

**Reglas**
- Antes de cambiar nada, guarda en un archivo el inicio actual y los paquetes ya desactivados, para poder deshacerlo.
- No desinstales nada del sistema: solo `pm disable-user --user 0`, que se deshace con `pm enable`.
- Avísame antes de lo que congele el mando, cambie la red o me pida otra vez la clave del Wi-Fi.
- Mi token de Home Assistant es un secreto. Lo escribo yo en un `home.json` local: tú solo lo envías a la tele, sin abrirlo ni mostrarlo. Al acabar, recuérdame borrarlo con `shred -u`.
- Si algo falla, deshaz lo último antes de seguir.

**Pregúntame**
- Qué apps no quiero que gane la ruleta, para añadirlas a `NEVER_WINS` (ver «Excluir apps de la ruleta» en el README).
- Mi localidad y, si uso Home Assistant, la URL y las entidades de temperatura de cada habitación.

**Cuidado con**
- Al añadir el servicio del botón Apps, conserva los servicios de accesibilidad que ya hubiera.
- Tras cada `adb install -r`, vuelve a poner Spin como inicio y comprueba que el servicio sigue activo.

**Limpiar la tele (solo si te lo confirmo)**
- Usa el ejemplo de cleanyourtv de **mi modelo y firmware exactos**; si no existe, para. Explícame su lista antes de aplicarla y pregúntame qué conservar.
- Spin tiene que ser ya el inicio antes de desactivar el de Google TV (`launcherx`) y el asistente de configuración (`setupwraith`).
- No desactives el Asistente, Chromecast, el receptor de duplicar pantalla, el salvapantallas ni `partnercustomizer` (botones Netflix, YouTube y Prime del mando) salvo que te lo pida.
- Desactiva en lotes pequeños, con una captura entre lote y lote, y al final pídeme que pruebe los botones del mando.

**Al terminar**, resúmeme qué instalaste, qué desactivaste y para qué servía, qué dejaste sin tocar y cómo deshacerlo todo.
