#!/bin/bash
# Runs our tool on Light's LightOS emulator, inside an Android Studio virtual phone.
# Follows Light's guide, docs/system_app/README.md, with the steps scripted.
#
#   scripts/emulator.sh setup [avd]   first time: prepares the virtual phone, installs the LightOS
#                                     emulator and our tool (takes 15-30 minutes)
#   scripts/emulator.sh start [avd]   starts the virtual phone later on (always use this, not the
#                                     play button in Android Studio, or LightOS won't be there)
#   scripts/emulator.sh install       rebuilds our tool and installs it on the running phone
#   scripts/emulator.sh screen flip   makes the running phone's screen match the Light Flip
#                                     (480 x 640); "screen lp3" switches back
#
# [avd] is the virtual phone's name from Android Studio's Device Manager. You can leave it out
# if you only have one.
#
# Written for macOS (and its bash 3.2); should also work on Linux.

set -euo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO"

EMULATOR_PACKAGE="com.thelightphone.sdk.emulator"
EMULATOR_APK="sdk/emulator/build/outputs/apk/debug/emulator-debug.apk"
TOOL_TOML="tool/lighttool.toml"
LOG_DIR="$REPO/build/emulator-logs"

say() { printf '\n==> %s\n' "$*"; }
fail() { printf '\nProblem: %s\n' "$*" >&2; exit 1; }

# --- Finding Android Studio's tools ----------------------------------------------------------

find_sdk() {
  local candidate
  for candidate in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
    if [ -n "$candidate" ] && [ -x "$candidate/platform-tools/adb" ]; then
      SDK="$candidate"
      return
    fi
  done
  fail "Couldn't find the Android SDK. Install Android Studio and finish its setup wizard first."
}

# Light's build tools are pinned to Java 17 (jvmToolchain(17)), so the build needs a Java 17
# installed, and Gradle 9.0 itself can't run on the Java 25 that newer Android Studios bundle.
# Simplest is to run everything on Java 17.
NEEDED_JAVA=17

# Prints the major version of the Java in the given home folder (e.g. 21), or nothing.
java_major() {
  local home="$1" line version
  [ -x "$home/bin/java" ] || return 0
  line="$("$home/bin/java" -version 2>&1 | grep -i 'version "' | head -1 || true)"
  version="$(printf '%s' "$line" | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p')"
  # Java 8 and older report "1.8".
  [ "$version" = "1" ] && version=8
  printf '%s' "$version"
}

find_java() {
  local candidate
  # Places a Java 17 might be: JAVA_HOME, macOS's registry (which includes JDKs Android Studio
  # downloaded), the usual folders, Homebrew, and Android Studio's own Java.
  {
    [ -n "${JAVA_HOME:-}" ] && printf '%s\n' "$JAVA_HOME"
    [ -x /usr/libexec/java_home ] && /usr/libexec/java_home -v "$NEEDED_JAVA" 2>/dev/null
    for candidate in "$HOME"/Library/Java/JavaVirtualMachines/*/Contents/Home \
                     /Library/Java/JavaVirtualMachines/*/Contents/Home \
                     /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
                     /usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
                     "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
                     "$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home"; do
      [ -d "$candidate" ] && printf '%s\n' "$candidate"
    done
    true
  } > "$LOG_DIR/java-candidates.txt"

  while IFS= read -r candidate; do
    if [ "$(java_major "$candidate")" = "$NEEDED_JAVA" ]; then
      export JAVA_HOME="$candidate"
      echo "Using Java $NEEDED_JAVA from $candidate"
      return
    fi
  done < "$LOG_DIR/java-candidates.txt"

  fail "Light's build needs Java $NEEDED_JAVA, and this Mac doesn't have it yet. To get it: in Android Studio, open Settings > Build, Execution, Deployment > Build Tools > Gradle, click the Gradle JDK menu > Download JDK, choose version $NEEDED_JAVA (any vendor) and click Download. Then run this again."
}

setup_tools() {
  mkdir -p "$LOG_DIR"
  find_sdk
  find_java
  ADB="$SDK/platform-tools/adb"
  EMULATOR="$SDK/emulator/emulator"
  [ -x "$EMULATOR" ] || fail "Android Studio's emulator isn't installed. In Android Studio, open Settings > Languages & Frameworks > Android SDK > SDK Tools and tick Android Emulator."
  export ANDROID_HOME="$SDK"
  # Gradle on the command line needs to know where the SDK is.
  if [ ! -f local.properties ]; then
    printf 'sdk.dir=%s\n' "$SDK" > local.properties
  fi
  mkdir -p "$LOG_DIR"
}

# --- The virtual phone -----------------------------------------------------------------------

pick_avd() {
  local wanted="${1:-}" avds count
  avds="$("$EMULATOR" -list-avds 2>/dev/null | grep -v '^INFO' || true)"
  [ -n "$avds" ] || fail "There's no virtual phone yet. Create one in Android Studio's Device Manager first (see DEVELOPING.md)."
  if [ -n "$wanted" ]; then
    printf '%s\n' "$avds" | grep -x "$wanted" >/dev/null || fail "No virtual phone called '$wanted'. You have: $(printf '%s' "$avds" | tr '\n' ' ')"
    AVD="$wanted"
    return
  fi
  count="$(printf '%s\n' "$avds" | wc -l | tr -d ' ')"
  [ "$count" = "1" ] || fail "You have several virtual phones; add the name: scripts/emulator.sh $COMMAND <name>. You have: $(printf '%s' "$avds" | tr '\n' ' ')"
  AVD="$avds"
}

avd_config() {
  local ini="$HOME/.android/avd/$AVD.ini" path=""
  if [ -f "$ini" ]; then path="$(grep '^path=' "$ini" | head -1 | cut -d= -f2-)"; fi
  [ -n "$path" ] || path="$HOME/.android/avd/$AVD.avd"
  printf '%s/config.ini' "$path"
}

# Sets key=value in an ini file, replacing the line if the key is already there.
set_ini() {
  local file="$1" key="$2" value="$3" tmp
  tmp="$(mktemp)"
  awk -v k="$key" -v v="$value" '
    BEGIN { done = 0 }
    index($0, k "=") == 1 { print k "=" v; done = 1; next }
    { print }
    END { if (!done) print k "=" v }
  ' "$file" > "$tmp"
  mv "$tmp" "$file"
}

# Light Phone III screen: 1080 x 1240 pixels, 3.92 inches (about 420 pixels per inch).
shape_like_light_phone() {
  local config
  config="$(avd_config)"
  [ -f "$config" ] || fail "Couldn't find the virtual phone's settings at $config"
  [ -f "$config.before-light" ] || cp "$config" "$config.before-light"
  set_ini "$config" hw.lcd.width 1080
  set_ini "$config" hw.lcd.height 1240
  set_ini "$config" hw.lcd.density 420
  set_ini "$config" skin.name 1080x1240
  set_ini "$config" skin.path _no_skin
  set_ini "$config" skin.dynamic yes
  set_ini "$config" showDeviceFrame no
  set_ini "$config" hw.initialOrientation portrait
  # Pass the computer's keyboard through as a hardware keyboard, to test typing on one.
  set_ini "$config" hw.keyboard yes
  echo "Set '$AVD' to the Light Phone III's screen size (old settings kept in config.ini.before-light)."
}

running_emulator() {
  "$ADB" devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }'
}

reboot_and_wait() {
  "$ADB" reboot
  # Wait for it to actually go down, so we don't mistake the old boot for the new one.
  "$ADB" wait-for-disconnect >/dev/null 2>&1 || sleep 15
  wait_for_boot
}

wait_for_boot() {
  local waited=0
  "$ADB" wait-for-device
  until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
    sleep 3
    waited=$((waited + 3))
    [ "$waited" -lt 600 ] || fail "The virtual phone took more than 10 minutes to start. See $LOG_DIR/emulator.log"
  done
  sleep 3
}

# True if this virtual phone is running and was started in the mode LightOS needs.
running_in_light_mode() {
  ps -axo command= 2>/dev/null | grep -e "-avd $AVD" | grep -e "-writable-system" >/dev/null
}

start_emulator() {
  if [ -n "$(running_emulator)" ]; then
    if running_in_light_mode; then
      echo "'$AVD' is already running in the right mode; using it."
      export ANDROID_SERIAL
      ANDROID_SERIAL="$(running_emulator)"
      wait_for_boot
      return
    fi
    fail "A virtual phone is already running, but not in the mode LightOS needs. Close its window first, then run this again."
  fi
  say "Starting '$AVD' (a window will open; leave it running)"
  # -writable-system keeps the LightOS emulator installed in the system partition. It's needed
  # every time, which is why the play button in Android Studio won't do.
  nohup "$EMULATOR" -avd "$AVD" -writable-system -no-snapshot >"$LOG_DIR/emulator.log" 2>&1 &
  local waited=0
  until [ -n "$(running_emulator)" ]; do
    sleep 3
    waited=$((waited + 3))
    if ! kill -0 $! 2>/dev/null; then
      fail "The virtual phone stopped while starting. Last lines of its log:
$(tail -15 "$LOG_DIR/emulator.log")"
    fi
    [ "$waited" -lt 300 ] || fail "The virtual phone didn't appear. See $LOG_DIR/emulator.log"
  done
  export ANDROID_SERIAL
  ANDROID_SERIAL="$(running_emulator)"
  wait_for_boot
  echo "Started ($ANDROID_SERIAL)."
}

use_running_emulator() {
  export ANDROID_SERIAL
  ANDROID_SERIAL="$(running_emulator)"
  [ -n "$ANDROID_SERIAL" ] || fail "No virtual phone is running. Start it with: scripts/emulator.sh start"
}

become_root() {
  local out
  out="$("$ADB" root 2>&1 || true)"
  if printf '%s' "$out" | grep -i "production builds" >/dev/null; then
    fail "This virtual phone uses a Google Play system image, which LightOS can't run on. Create one with the API 34 'Android Open Source' image (ARM 64 v8a) instead (see DEVELOPING.md)."
  fi
  # adbd restarts as root; give it a moment to come back.
  sleep 3
  "$ADB" wait-for-device
  sleep 2
}

system_is_writable() {
  "$ADB" shell 'touch /system/.light-check && rm /system/.light-check' >/dev/null 2>&1
}

make_system_writable() {
  say "Making the system partition writable"
  become_root
  "$ADB" remount >"$LOG_DIR/remount.log" 2>&1 || true
  if system_is_writable; then return; fi
  # First time: Android turns off verified boot, then needs a restart.
  "$ADB" disable-verity >>"$LOG_DIR/remount.log" 2>&1 || true
  echo "Restarting the virtual phone once to finish (about a minute)..."
  reboot_and_wait
  become_root
  "$ADB" remount >>"$LOG_DIR/remount.log" 2>&1 || true
  system_is_writable || fail "Couldn't make the system partition writable. Details in $LOG_DIR/remount.log"
}

check_test_keys() {
  local description
  description="$("$ADB" shell getprop ro.build.description | tr -d '\r')"
  case "$description" in
    *test-keys) ;;
    *) fail "This system image is signed with '${description##* }', but LightOS's emulator needs one built with test-keys. Create a virtual phone with the API 34 'Android Open Source' image (ARM 64 v8a) instead (see DEVELOPING.md)." ;;
  esac
}

# --- The LightOS emulator app ----------------------------------------------------------------

make_platform_key() {
  local keystore="sdk/emulator/keys/platform.jks" tmp
  if [ -f "$keystore" ]; then return; fi
  say "Creating the platform signing key (Android's public test key; not a secret)"
  tmp="$(mktemp -d)"
  curl -fsSL -o "$tmp/platform.x509.pem" https://raw.githubusercontent.com/wfairclough/android_aosp_keys/refs/heads/master/platform.x509.pem
  curl -fsSL -o "$tmp/platform.pk8" https://raw.githubusercontent.com/wfairclough/android_aosp_keys/refs/heads/master/platform.pk8
  openssl pkcs8 -inform DER -nocrypt -in "$tmp/platform.pk8" -out "$tmp/platform.pem"
  openssl pkcs12 -export -in "$tmp/platform.x509.pem" -inkey "$tmp/platform.pem" \
    -name platform -out "$tmp/platform.p12" -passout pass:android
  mkdir -p sdk/emulator/keys
  "$JAVA_HOME/bin/keytool" -importkeystore -noprompt \
    -srckeystore "$tmp/platform.p12" -srcstoretype PKCS12 -srcstorepass android \
    -destkeystore "$keystore" -deststoretype JKS -deststorepass android >/dev/null
  rm -rf "$tmp"
}

install_lightos_emulator() {
  say "Building the LightOS emulator app (the first build downloads a lot; can take 10+ minutes)"
  ./gradlew :sdk:emulator:assembleDebug
  say "Installing it as a system app"
  "$ADB" shell mkdir -p /system/priv-app/LightOSEmulator
  "$ADB" push "$EMULATOR_APK" /system/priv-app/LightOSEmulator/LightOSEmulator.apk
  echo "Restarting the virtual phone so Android picks it up (about a minute)..."
  reboot_and_wait

  local where details
  where="$("$ADB" shell pm path "$EMULATOR_PACKAGE" 2>/dev/null | tr -d '\r' || true)"
  case "$where" in
    */system/priv-app/LightOSEmulator/*) ;;
    *) fail "The LightOS emulator isn't installed as a system app (found: '$where'). See Troubleshooting in docs/system_app/README.md." ;;
  esac
  details="$("$ADB" shell dumpsys package "$EMULATOR_PACKAGE" 2>/dev/null | tr -d '\r' || true)"
  if ! printf '%s\n' "$details" | grep -E '(userId|appId|uid)=1000' >/dev/null; then
    fail "The LightOS emulator isn't running with system permissions. See Troubleshooting in docs/system_app/README.md."
  fi

  # Optional touches from Light's guide: LightOS as the home screen, no animations.
  "$ADB" shell cmd package set-home-activity "$EMULATOR_PACKAGE/.MainActivity" >/dev/null 2>&1 || true
  "$ADB" shell settings put global window_animation_scale 0
  "$ADB" shell settings put global transition_animation_scale 0
  "$ADB" shell settings put global animator_duration_scale 0
}

# --- Our tool --------------------------------------------------------------------------------

TOML_BACKUP=""
restore_toml() {
  if [ -n "$TOML_BACKUP" ] && [ -f "$TOML_BACKUP" ]; then
    cp "$TOML_BACKUP" "$TOOL_TOML"
    rm -f "$TOML_BACKUP"
    TOML_BACKUP=""
  fi
}

install_tool() {
  local id label tmp
  id="$(sed -n 's/^id *= *"\(.*\)"/\1/p' "$TOOL_TOML")"
  label="$(sed -n 's/^label *= *"\(.*\)"/\1/p' "$TOOL_TOML")"
  say "Building $label and installing it"

  # A real phone's LightOS is com.lightos; the emulator's is different. Point the build at the
  # emulator for this install only, then put the file back as it was.
  TOML_BACKUP="$(mktemp)"
  cp "$TOOL_TOML" "$TOML_BACKUP"
  trap restore_toml EXIT
  trap 'restore_toml; exit 130' INT TERM
  tmp="$(mktemp)"
  sed "s/^serverPackage *= *\".*\"/serverPackage = \"$EMULATOR_PACKAGE\"/" "$TOOL_TOML" > "$tmp"
  cat "$tmp" > "$TOOL_TOML"
  rm -f "$tmp"

  ./gradlew :tool:installDebug -DlightSdk.allowAltServerPackage=true
  restore_toml

  "$ADB" shell am start -n "$EMULATOR_PACKAGE/.MainActivity" >/dev/null 2>&1 || true
  cat <<EOF

$label ($id) is installed on the virtual phone.

If it isn't in the tool list yet: in the LightOS emulator, open Settings > Allowed Tools
and choose All Tools. That's needed because $label isn't signed by Light.
EOF
}

# --- Commands --------------------------------------------------------------------------------

COMMAND="${1:-}"
case "$COMMAND" in
  setup)
    setup_tools
    pick_avd "${2:-}"
    shape_like_light_phone
    start_emulator
    check_test_keys
    make_system_writable
    make_platform_key
    install_lightos_emulator
    install_tool
    say "All set. Next time, start the virtual phone with: scripts/emulator.sh start"
    ;;
  start)
    setup_tools
    pick_avd "${2:-}"
    start_emulator
    "$ADB" shell am start -n "$EMULATOR_PACKAGE/.MainActivity" >/dev/null 2>&1 || true
    say "Ready. After changing the tool's code, run: scripts/emulator.sh install"
    ;;
  install)
    setup_tools
    use_running_emulator
    install_tool
    ;;
  screen)
    setup_tools
    use_running_emulator
    case "${2:-}" in
      flip)
        # Light Flip: 480 x 640 pixels on a 2.8" screen (about 285 pixels per inch).
        "$ADB" shell wm size 480x640
        "$ADB" shell wm density 280
        say "The virtual phone now has the Light Flip's screen (480 x 640). Back with: scripts/emulator.sh screen lp3"
        ;;
      lp3)
        "$ADB" shell wm size reset
        "$ADB" shell wm density reset
        say "The virtual phone is back to the Light Phone III's screen (1080 x 1240)."
        ;;
      *) fail "Which screen? Use: scripts/emulator.sh screen flip   or   scripts/emulator.sh screen lp3" ;;
    esac
    ;;
  *)
    sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//'
    exit 1
    ;;
esac
