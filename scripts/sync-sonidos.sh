#!/usr/bin/env bash
# Trae el núcleo de SesoLibre Sonidos a Somnia (Android): compila libsonidos.so
# para las 4 arquitecturas (alineado a 16 KB, requisito de Play), copia el
# puente JNI, la receta y las muestras que la receta necesita.
#
# Uso: scripts/sync-sonidos.sh [ruta a sesolibre-sonidos]
# Requiere: cargo, cargo-ndk, targets de Rust para Android y el NDK.
set -euo pipefail

SOMNIA="$(cd "$(dirname "$0")/.." && pwd)"
SONIDOS="${1:-$SOMNIA/../sesolibre-sonidos}"
SONIDOS="$(cd "$SONIDOS" && pwd)"
RECETA="sonar.calma-ronquido"
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$(ls -d "$HOME"/Android/Sdk/ndk/* | sort -V | tail -1)}"

echo "Sonidos: $SONIDOS ($(git -C "$SONIDOS" rev-parse --short HEAD))"

# 1. Librería nativa
(cd "$SONIDOS" && RUSTFLAGS="-C link-arg=-Wl,-z,max-page-size=16384" \
  cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -t x86 \
  -o "$SOMNIA/app/src/main/jniLibs" build --release -p sonidos-android)
for so in "$SOMNIA"/app/src/main/jniLibs/*/libsonidos.so; do
  align=$(readelf -lW "$so" | awk '/LOAD/{print $NF}' | sort -u)
  [[ "$align" == "0x4000" ]] || { echo "ERROR: $so no está alineada a 16 KB ($align)"; exit 1; }
done

# 2. Puente JNI (el paquete debe ser com.sesolibre.sonidos.motor: la librería busca esos nombres)
DEST_JNI="$SOMNIA/app/src/main/java/com/sesolibre/sonidos/motor"
mkdir -p "$DEST_JNI"
cp "$SONIDOS/app/plugin/android/src/main/java/com/sesolibre/sonidos/motor/Nativo.kt" "$DEST_JNI/"

# 3. Receta y sus muestras
ASSETS="$SOMNIA/app/src/main/assets/sonidos"
rm -rf "$ASSETS"
mkdir -p "$ASSETS/audio"
cp "$SONIDOS/recetas/$RECETA.json" "$ASSETS/"
python3 - "$SONIDOS/assets/audio/banco.json" "$SONIDOS/recetas/$RECETA.json" <<'PY' > "$ASSETS/archivos.txt"
import json, sys
banco = json.load(open(sys.argv[1]))
receta = json.load(open(sys.argv[2]))
files = set()
for c in receta["capas"]:
    if c.get("timbre"):
        files.update(banco["instrumentos"][c["timbre"]]["notas"].values())
    if c.get("instrumento"):
        files.update(banco["instrumentos"][c["instrumento"]]["notas"].values())
    if c.get("ambiente"):
        files.add(banco["ambientes"][c["ambiente"]])
    if c.get("evento"):
        files.update(banco["eventos"][c["evento"]])
print("\n".join(sorted(files)))
PY
while read -r f; do
  [[ -z "$f" ]] && continue
  mkdir -p "$ASSETS/audio/$(dirname "$f")"
  cp "$SONIDOS/assets/audio/$f" "$ASSETS/audio/$f"
done < "$ASSETS/archivos.txt"
rm "$ASSETS/archivos.txt"
git -C "$SONIDOS" rev-parse HEAD > "$ASSETS/VERSION"
echo "Listo: $(find "$ASSETS" -type f | wc -l) archivos en assets/sonidos, libsonidos.so en 4 arquitecturas"
