#!/usr/bin/env bash
# Arena Android – schlanker APK-Build ganz ohne Android Studio / Gradle.
#
# Voraussetzungen (Details im README):
#   - bash, python3, node, openssl
#   - java (Java-Laufzeit, z. B. jdk4py aus PyPI)
#   - javac: entweder ein echtes javac im PATH oder JAVAC_JAR=tools.jar
#   - aapt2 (z. B. aus dem PyPI-Paket "aapt2")
#   - android.jar (z. B. github.com/Sable/android-platforms)
#   - Soot-Jars für Klassen→Dex (SOOT_DIR)
#
# Beispiel (Pfade per Umgebungsvariablen setzen):
#   JAVA=java JAVAC_JAR=/tools/tools.jar AAPT2=/tools/aapt2 \
#   ANDROID_JAR=/tools/android.jar SOOT_DIR=/tools/soot ./build.sh

set -euo pipefail
cd "$(dirname "$0")"

: "${JAVA:=java}"
: "${JAVAC:=}"
: "${JAVAC_JAR:=}"
: "${AAPT2:=aapt2}"
: "${ANDROID_JAR:=}"
: "${SOOT_DIR:=}"
: "${APKSIGN_LIB:=apk_sign_ts}"

if [ -z "$ANDROID_JAR" ] || [ -z "$SOOT_DIR" ]; then
  echo "ANDROID_JAR und SOOT_DIR müssen gesetzt sein (siehe README)." >&2
  exit 1
fi

VERSION=$(grep -oP "versionName '\K[^']+" app/build.gradle)
VC=$(grep -oP "versionCode \K[0-9]+" app/build.gradle)
MIN_SDK=$(grep -oP "minSdk \K[0-9]+" app/build.gradle)
TARGET_SDK=$(grep -oP "targetSdk \K[0-9]+" app/build.gradle)
OUT=build
rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex" "$OUT/keys"

echo "== 1/6 Manifest =="
# Package + Versionen für aapt2 einsetzen (bei Gradle-Builds kommt das aus build.gradle)
sed 's|<manifest |<manifest package="de.tuktuck.arena" android:versionCode="'"$VC"'" android:versionName="'"$VERSION"'" |' \
  app/src/main/AndroidManifest.xml > "$OUT/AndroidManifest.xml"

echo "== 2/6 Ressourcen (aapt2) =="
"$AAPT2" compile --dir app/src/main/res -o "$OUT/res.zip"
"$AAPT2" link -o "$OUT/app-unsigned.apk" -I "$ANDROID_JAR" \
  --manifest "$OUT/AndroidManifest.xml" -R "$OUT/res.zip" --auto-add-overlay \
  --min-sdk-version "$MIN_SDK" --target-sdk-version "$TARGET_SDK" \
  -A app/src/main/assets --java "$OUT/gen"

echo "== 3/6 Java kompilieren =="
SOURCES=$(find app/src/main/java "$OUT/gen" -name '*.java')
if [ -n "$JAVAC_JAR" ]; then
  "$JAVA" -cp "$JAVAC_JAR" com.sun.tools.javac.Main \
    -bootclasspath "$ANDROID_JAR" -source 1.7 -target 1.7 -encoding UTF-8 \
    -d "$OUT/classes" $SOURCES
else
  "${JAVAC:-javac}" -bootclasspath "$ANDROID_JAR" -source 1.7 -target 1.7 \
    -encoding UTF-8 -d "$OUT/classes" $SOURCES
fi

echo "== 4/6 Klassen → classes.dex (Soot) =="
CP=$(ls "$SOOT_DIR"/*.jar | tr '\n' ':')
"$JAVA" -cp "$CP" soot.Main -src-prec class -cp "$OUT/classes:$ANDROID_JAR" \
  -process-dir "$OUT/classes" -f dex -allow-phantom-refs -d "$OUT/dex"
if [ -f "$OUT/dex/classes.dex" ]; then
  DEX="$OUT/dex/classes.dex"
else
  DEX=$(find "$OUT/dex" -name '*.dex' | head -1)
fi

echo "== 5/6 Dex in den APK legen =="
python3 - "$OUT/app-unsigned.apk" "$DEX" <<'PYEOF'
import sys, zipfile, shutil, os
apk, dex = sys.argv[1], sys.argv[2]
tmp = apk + ".tmp"
with zipfile.ZipFile(apk) as zin, zipfile.ZipFile(tmp, "w", zipfile.ZIP_STORED) as zout:
    for it in zin.infolist():
        zout.writestr(it, zin.read(it.filename))
    zi = zipfile.ZipInfo("classes.dex")
    zi.compress_type = zipfile.ZIP_STORED
    zi.external_attr = 0o600 << 16
    zout.writestr(zi, open(dex, "rb").read())
shutil.move(tmp, apk)
print("classes.dex eingefügt")
PYEOF

echo "== 6/6 Signieren (v1+v2+v3) =="
KEYS=keystore
mkdir -p "$KEYS"
if [ ! -f "$KEYS/debug.key.pem" ]; then
  echo "Debug-Schlüssel erzeugen – BITTE AUFBEWAHREN (nötig für Updates): $KEYS/"
  openssl genrsa -out "$KEYS/debug.key.pem" 2048 2>/dev/null
  openssl req -x509 -new -key "$KEYS/debug.key.pem" \
    -out "$KEYS/debug.cert.pem" -days 10000 \
    -subj "/CN=Android Debug/O=Android/C=US" 2>/dev/null
fi
node scripts/sign-apk.mjs "$OUT/app-unsigned.apk" "$OUT/arena-$VERSION.apk" \
  "$KEYS/debug.key.pem" "$KEYS/debug.cert.pem"

echo
echo "FERTIG: $OUT/arena-$VERSION.apk"
ls -la "$OUT/arena-$VERSION.apk"
