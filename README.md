# Kokki

App Android para planificar la semana (deporte, gimnasio, trabajo, trayectos, clases...) hablando con una IA,
con recordatorios en el móvil y check de cumplimiento.

## Qué hace

- **Semana**: vista de lunes a domingo con cada actividad, su hora y botones ✓ (hecho) / ✗ (no hecho).
  Muestra el % cumplido por semana y por tipo, avisa de solapamientos y permite añadir, editar,
  copiar la semana a la siguiente o vaciarla.
- **Asistente**: chat por voz o texto con una IA gratuita (Groq o Gemini). Le dices qué quieres ("reparte el deporte en 4 días
  y el inglés en 2"), te propone la semana completa, te dice si es viable y la aplicas con un botón.
- **Notificaciones**: un aviso X minutos antes de cada actividad para prepararte, y otro al terminar
  preguntando si la has cumplido, con botones *Hecho* / *No hecho* (los trayectos no piden check).
- **Progreso**: resumen de las últimas 8 semanas (gráfica, racha, mejor semana, cumplimiento por tipo)
  y lo que más te cuesta según tus datos. Pregunta "¿qué es lo más difícil que llevas?" (también por
  notificación los domingos a las 20:00, con respuesta directa) y se lo pasa a la IA al planificar.
- **Castigos**: reglas del tipo «si no cumplo Gimnasio, se bloquean WhatsApp e Instagram 1 h». Se aplican al marcar
  «no hecho» o si no se contesta al aviso en 2 horas. Para desbloquear antes hay un reto a elegir (o dos seguidos):
  - Sin internet ni IA: caminar X pasos, ir a un sitio y quedarse X min (GPS), recuperar la actividad otro rato
    (comprobada con pasos o GPS), sentadillas/flexiones/jumping jacks contados por la cámara (ML Kit), escanear un QR
    propio, modo zen (móvil boca abajo y quieto), reflexión escrita a mano (la lee ML Kit), leer en voz alta un texto
    en inglés, mini reto de JavaScript con tests (se ejecuta en un WebView) y leer un texto con 3 preguntas.
  - Con IA: pregunta de entrevista contestada en voz alta, foto de una tarea y vídeo de ejercicio (solo Gemini).
  - Opcional: cada intento fallido suma 15 o 30 min de castigo (como mucho, el doble del castigo inicial).
  El bloqueo usa un servicio de accesibilidad: al abrir una app castigada vuelve al inicio y muestra la pantalla del castigo.
- **Ajustes**: proveedor de IA y su clave (con botón que hace una petición de prueba), información fija para la IA (horario de trabajo, preferencias)
  y estado de los permisos.

## Coste: 0 €

- IA: Groq (plan gratuito, https://console.groq.com/keys) o Gemini (nivel gratuito de Google AI Studio,
  https://aistudio.google.com/apikey). Los errores temporales (límite por minuto, servidor ocupado) se reintentan solos.
- Cámara, QR, texto a mano y conteo de ejercicio: ML Kit, que funciona dentro del móvil y es gratis.
- Voz: reconocimiento de voz de Android.
- Todo lo demás es local en el móvil (Room + AlarmManager). Los vídeos de los retos se suben a Gemini solo para
  revisarlos (Google los borra a las 48 h) y se eliminan del móvil al terminar.

## Instalar en el móvil

1. En el móvil: Ajustes → Información del teléfono → pulsa 7 veces en «Número de compilación».
   Luego en Opciones de desarrollador activa **Depuración USB**.
2. Conecta el móvil por USB y abre esta carpeta en Android Studio → botón ▶ Run.

O desde terminal:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat installDebug
```

El APK también queda en `app/build/outputs/apk/debug/app-debug.apk` para pasarlo al móvil e instalarlo a mano.

## Estructura

```
app/src/main/java/com/rutina/app/
├── data/     Modelos, base de datos Room, preferencias y repositorio
├── ai/       Clientes de Groq y Gemini, retos con IA y prompt/parseo del plan semanal
├── notify/   Alarmas, notificaciones y receivers (avisos, checks, reinicio)
├── penalty/  Bloqueo de apps y pasos (accesibilidad), GPS del reto del sitio, tests de código y pantalla de bloqueo
└── ui/       Pantallas Compose: Semana, Asistente, Progreso, Castigos, Ajustes y editor
```
