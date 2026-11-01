# Kokki · 克己

**Kokki** (克己) significa en japonés *disciplina* o *autocontrol*.

Es una app Android para planificar la semana con un asistente de IA y comprometerse a cumplirla.
Si no cumples lo que te has propuesto, se bloquean las apps que tú elijas hasta que pase el tiempo
o superes un reto.

Funciona solo en Android (8.0 o superior) y no cuesta nada: todo usa servicios gratuitos o va dentro del móvil.

## Cómo funciona, en cuatro pasos

1. **Planifica la semana** hablando con el asistente («trabajo de 9 a 18, quiero entrenar 4 días y el inglés 2 tardes»)
   o añadiendo las actividades a mano.
2. **Fija la semana**: eliges si el compromiso empieza ahora o en una fecha. Desde ese día solo puedes añadir actividades,
   no quitarlas ni moverlas.
3. **Marca lo que haces**: la app te avisa antes de cada actividad y al terminar te pregunta si la has cumplido.
4. **Si fallas, hay castigo**: se bloquean las apps de la regla que hayas creado. Puedes desbloquearlas antes con un reto
   o con un comodín.

## Funciones

### Mi semana
- Un día por pantalla: se cambia deslizando o tocando la tira de días (L M X J V S D).
- Arriba salen las actividades **pendientes**. Las ya **hechas** (en verde) y **no cumplidas** (en rojo) van aparte,
  detrás de un botón.
- **Trabajo y Trayecto son horas ocupadas, no tareas**: no se marcan ni llevan castigo. Sirven para que el asistente
  no ponga nada encima.
- Avisa si dos actividades se solapan.
- **Antes de fijar la semana** puedes borrar actividades una a una (con «Deshacer») o borrarlo todo y empezar de nuevo.
- **Cambiar por otras tareas**: si sabes que no vas a poder hacer algo, lo cambias por tareas útiles de 30 min
  (poner una lavadora, fregar, barrer, ir a la farmacia…). Si las haces todas, te libras del castigo.
- Copiar la semana a la siguiente.

### Asistente
- Chat por texto o voz con una IA gratuita (Groq o Gemini).
- Propone la semana completa, explica si es viable (sueño, comidas, descanso entre entrenamientos, solapes)
  y la aplicas con un botón.
- Respeta las horas ocupadas por trabajo y trayectos.
- Tiene en cuenta lo que te está costando según tus datos y la reflexión semanal.

### Progreso
- Resumen de esta semana: % cumplido, cuántas llevas y tus comodines.
- Últimas 8 semanas: gráfica, racha, mejor semana y cumplimiento por tipo de actividad.
- Diario: cada domingo te pregunta qué te ha costado más, también por notificación.

### Castigos
- Reglas del tipo «si no cumplo Gimnasio, se bloquean Instagram y TikTok durante 1 h». Se crean con un asistente
  paso a paso, con resumen y confirmación final.
- Se aplican al marcar «no hecho» o si no contestas al aviso, y solo desde la fecha en que empieza tu compromiso.
- Una vez que ha saltado el aviso de una actividad, sus reglas quedan **bloqueadas**: no se pueden desactivar,
  editar ni borrar salvo superando un reto.
- **Castigo que crece** (opcional): cada intento fallido suma minutos, como mucho el doble del castigo inicial.
- **Retos combinados**: uno o dos retos seguidos para desbloquear.
- El bloqueo usa un servicio de accesibilidad: al abrir una app castigada vuelve al inicio y enseña el castigo.

### Retos para desbloquear antes
| Sin internet ni IA | Con IA |
|---|---|
| Esperar el tiempo del castigo | Pregunta de entrevista contestada en voz alta |
| Caminar X pasos | Foto de una tarea hecha (Gemini) |
| Ir a un sitio y quedarte X min (GPS) | Vídeo haciendo ejercicio (Gemini) |
| Recuperar la actividad otro rato | |
| Sentadillas, flexiones o jumping jacks contados por la cámara | |
| Escanear un QR propio | |
| Modo zen: móvil boca abajo y quieto | |
| Reflexión escrita a mano (la lee la cámara) | |
| Leer en voz alta un texto en inglés | |
| Mini reto de JavaScript con tests | |
| Leer un texto y responder 3 preguntas | |

### Comodines
Cada 5 actividades cumplidas (configurable: 3, 5, 7 o 10) ganas un **comodín** que te salta un castigo.

### Ajustes
- Proveedor de IA y su clave, con botón de prueba.
- Información fija para el asistente (horarios, preferencias).
- Recordatorios y permisos.
- Apariencia: tema claro (por defecto), oscuro o el del sistema.

## Coste: 0 €

- **IA**: [Groq](https://console.groq.com/keys) (recomendada, rápida) o [Gemini](https://aistudio.google.com/apikey),
  ambas en su plan gratuito. Los errores temporales (límite por minuto, servidor ocupado) se reintentan solos.
- **Cámara, QR, texto a mano y conteo de ejercicio**: ML Kit, dentro del móvil.
- **Voz**: reconocimiento de voz de Android.
- **Todo lo demás es local** (Room y AlarmManager): no hay servidor ni cuenta. Las claves de IA se guardan solo en el móvil.
  Los vídeos de los retos se suben a Gemini solo para revisarlos y se borran del móvil al terminar.

## Compilar e instalar

Requisitos: Android Studio (con su JDK) y un móvil Android con arquitectura arm64.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleDebug
```

La APK queda en `app/build/outputs/apk/debug/app-debug.apk`; pásala al móvil e instálala.
Con el móvil conectado por USB y la depuración USB activada, `.\gradlew.bat installDebug` la instala directamente.

Al abrir la app por primera vez hay que dar los permisos de notificaciones, alarmas exactas y el servicio de accesibilidad
(para los castigos). Los de cámara, ubicación y micrófono se piden solo al usar los retos que los necesitan.

## Tecnología

Kotlin · Jetpack Compose (Material 3) · Room · AlarmManager · CameraX · ML Kit (poses, códigos de barras, texto) · ZXing

## Estructura

```
app/src/main/java/com/rutina/app/
├── data/     Modelos, base de datos Room, compromisos, comodines, castigos, estadísticas y preferencias
├── ai/       Clientes de Groq y Gemini, retos con IA y prompt/parseo del plan semanal
├── notify/   Alarmas, notificaciones y receivers (avisos, checks, reinicio del móvil)
├── penalty/  Bloqueo de apps y pasos (accesibilidad), GPS, tests de código y pantalla de bloqueo
└── ui/       Pantallas Compose: Semana, Asistente, Progreso, Castigos, Ajustes, editor, retos e iconos
```

El paquete sigue siendo `com.rutina.app` (el nombre original del proyecto) para que las actualizaciones se instalen
encima sin perder datos.
