# Guía de Release — Google Play

## Datos de la cuenta
- **Cuenta:** SesoLibre Apps (personal)
- **ID de cuenta:** 8581382143712347644
- **applicationId:** `com.sesolibre.somnia`
- **Nombre en tienda:** SesoLibre Somnia

## Firma
- Keystore de subida: `keystore/upload-keystore.jks` (alias `somnia-upload`), credenciales en `keystore.properties`.
- **Ambos están en `.gitignore` y NUNCA deben subirse al repo.** El proyecto vive en una carpeta MEGA sincronizada, lo que sirve de respaldo; aun así conviene guardar una copia del keystore y la contraseña en un gestor de contraseñas.
- Recomendado al crear la app en Play Console: inscribirse en **Play App Signing** (Google guarda la clave de firma final; la nuestra queda solo como clave de subida y es reemplazable si se pierde).

## Generar el AAB

```bash
./gradlew bundleRelease
# resultado: app/build/outputs/bundle/release/app-release.aab
```

Antes de cada release: incrementar `versionCode` (+1) y ajustar `versionName` en `app/build.gradle.kts`.

## Checklist para la ficha de Play (primera publicación)
1. Crear la app en Play Console (idioma predeterminado: español).
2. **Declaración de micrófono:** la revisión pedirá justificar `RECORD_AUDIO` en segundo plano/foreground service. Texto sugerido: *"La app es un monitor de sueño: el usuario inicia explícitamente una sesión nocturna que analiza el sonido ambiente para detectar ronquidos y ruidos. El audio se procesa y almacena únicamente en el dispositivo."* Preparar un video corto mostrando el flujo (iniciar sesión → notificación → detener).
3. **Video/declaración de Foreground Service** (requisito para target SDK 34+): tipo `microphone`, caso de uso "monitoreo iniciado por el usuario".
4. **Sección de seguridad de datos:** audio se recolecta, NO se comparte, se procesa localmente, cifrado en reposo del sistema, el usuario puede borrarlo.
5. Política de privacidad publicada en una URL (requisito obligatorio por usar micrófono) — pendiente de redactar y hospedar (puede ser GitHub Pages del propio repo).
6. Clasificación de contenido, categoría: Salud y bienestar.
7. Subir el AAB a prueba interna primero; promover a producción tras validar en dispositivos reales.

## Distribución alterna
CI genera APK debug en cada push (artefacto de GitHub Actions). Para sideload de release: `./gradlew assembleRelease`.
