#!/usr/bin/env bash
# A dependency-free SDK build; Gradle/Android Studio is also supported.
set -euo pipefail
task_root="$(cd "$(dirname "$0")/.." && pwd)"
: "${ANDROID_HOME:?Set ANDROID_HOME to an Android SDK with platform 35 and build-tools 35.0.0}"
task_tools="$ANDROID_HOME/build-tools/35.0.0"
task_android_jar="$ANDROID_HOME/platforms/android-35/android.jar"
task_build="$task_root/app/build/manual"
mkdir -p "$task_build/generated" "$task_build/classes" "$task_build/dex" "$task_root/dist"
rm -f "$task_build/resources.zip" "$task_build/unsigned.apk" "$task_build/aligned.apk"
"$task_tools/aapt2" compile --dir "$task_root/app/src/main/res" -o "$task_build/resources.zip"
# AGP supplies the namespace; aapt2 called directly needs it in its temporary manifest.
sed 's/<manifest /<manifest package="local.ridewatch" /' "$task_root/app/src/main/AndroidManifest.xml" > "$task_build/AndroidManifest.xml"
"$task_tools/aapt2" link -o "$task_build/unsigned.apk" -I "$task_android_jar" \
    --manifest "$task_build/AndroidManifest.xml" --java "$task_build/generated" \
    --min-sdk-version 26 --target-sdk-version 35 --version-code 1 --version-name 0.1.0 "$task_build/resources.zip"
find "$task_root/app/src/main/java" "$task_build/generated" -name '*.java' -print > "$task_build/sources.txt"
javac -encoding UTF-8 --release 8 -classpath "$task_android_jar" -d "$task_build/classes" @"$task_build/sources.txt"
jar cf "$task_build/classes.jar" -C "$task_build/classes" .
"$task_tools/d8" --min-api 26 --lib "$task_android_jar" --output "$task_build/dex" "$task_build/classes.jar"
(cd "$task_build/dex" && zip -q "$task_build/unsigned.apk" classes*.dex)
"$task_tools/zipalign" -f -p 4 "$task_build/unsigned.apk" "$task_build/aligned.apk"
task_key_dir="${XDG_DATA_HOME:-$HOME/.local/share}/ridewatch"
mkdir -p "$task_key_dir"
chmod 700 "$task_key_dir"
task_key="$task_key_dir/debug.keystore"
if [ ! -f "$task_key" ]; then
    keytool -genkeypair -keystore "$task_key" -storepass android -keypass android -alias androiddebugkey \
        -dname 'CN=RideWatch Debug,O=Local Development,C=CN' -keyalg RSA -keysize 2048 -validity 10000 >/dev/null
fi
chmod 600 "$task_key"
"$task_tools/apksigner" sign --ks "$task_key" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android \
    --out "$task_root/dist/ridewatch-debug.apk" "$task_build/aligned.apk"
"$task_tools/apksigner" verify "$task_root/dist/ridewatch-debug.apk"
sha256sum "$task_root/dist/ridewatch-debug.apk"
