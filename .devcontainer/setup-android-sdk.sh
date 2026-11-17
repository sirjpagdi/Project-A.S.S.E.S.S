#!/usr/bin/env bash
set -euo pipefail

USER_HOME="${HOME:-/home/codespace}"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$USER_HOME/android-sdk}}"
JAVA_HOME="${JAVA_HOME:-/usr/local/sdkman/candidates/java/current}"
WORKSPACE_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"

mkdir -p "$SDK_ROOT"

export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"
export JAVA_HOME
export PATH="$SDK_ROOT/cmdline-tools/latest/bin:$SDK_ROOT/platform-tools:$JAVA_HOME/bin:$PATH"

SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"

if [ ! -x "$SDKMANAGER" ]; then
    echo "Installing Android command-line tools..."

    TMP_DIR="$(mktemp -d)"
    cd "$TMP_DIR"

    wget -q \
        https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
        -O commandlinetools.zip

    unzip -q commandlinetools.zip

    mkdir -p "$SDK_ROOT/cmdline-tools"
    rm -rf "$SDK_ROOT/cmdline-tools/latest"
    mv cmdline-tools "$SDK_ROOT/cmdline-tools/latest"

    cd /
    rm -rf "$TMP_DIR"
fi

echo "Accepting Android SDK licenses..."
set +o pipefail
if yes | "$SDKMANAGER" --sdk_root="$SDK_ROOT" --licenses >/dev/null; then
    set -o pipefail
else
    license_status=$?
    set -o pipefail
    echo "Failed to accept Android SDK licenses." >&2
    exit "$license_status"
fi

echo "Installing/verifying Android SDK packages..."
"$SDKMANAGER" \
    --sdk_root="$SDK_ROOT" \
    "platform-tools" \
    "platforms;android-34" \
    "build-tools;34.0.0" >/dev/null

echo "Configuring Gradle SDK location..."

cat > "$WORKSPACE_ROOT/local.properties" <<EOF2
sdk.dir=$SDK_ROOT
EOF2

BASHRC="$USER_HOME/.bashrc"
touch "$BASHRC"

if ! grep -q "MCQ Quick Check Android build environment" "$BASHRC"; then
    cat >> "$BASHRC" <<EOF2

# MCQ Quick Check Android build environment
export ANDROID_HOME=$SDK_ROOT
export ANDROID_SDK_ROOT=$SDK_ROOT
export PATH="\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$PATH"
export JAVA_HOME=$JAVA_HOME
export PATH="\$JAVA_HOME/bin:\$PATH"
EOF2
fi

echo
echo "=============================================="
echo "MCQ Quick Check Android SDK"
echo "=============================================="
echo "SDK:        $SDK_ROOT"
echo "Java:       $JAVA_HOME"
echo "Platform:   android-34"
echo "BuildTools: 34.0.0"
echo "=============================================="
