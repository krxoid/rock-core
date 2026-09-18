#!/usr/bin/env bash
set -euo pipefail

REPO="krxoid/rock-core"

INSTALL_DIR="/usr/share/rock-core"
BIN="/usr/bin/rock"

GITHUB_API="https://api.github.com/repos/$REPO"
RELEASE_BASE="https://github.com/$REPO/releases/download"
ROCK_URL="https://raw.githubusercontent.com/$REPO/master/rock"

die() {
    echo "rock-core: error: $*" >&2
    exit 1
}

info() {
    echo "rock-core: $*"
}

# ------------------------------------------------------------
# Root check
# ------------------------------------------------------------

if [ "$(id -u)" -ne 0 ]; then
    die "please run this installer as root: sudo ./install.sh"
fi

# ------------------------------------------------------------
# Dependencies
# ------------------------------------------------------------

command -v curl >/dev/null 2>&1 ||
    die "curl is required"

command -v java >/dev/null 2>&1 ||
    die "Java is required. Install Java 25 or newer first."

# ------------------------------------------------------------
# Check Java version
# ------------------------------------------------------------

JAVA_VERSION="$(
    java -version 2>&1 |
    sed -n 's/.*version "\([0-9]*\).*/\1/p' |
    head -n1
)"

if [ -z "$JAVA_VERSION" ]; then
    die "could not determine Java version"
fi

if [ "$JAVA_VERSION" -lt 25 ]; then
    die "Java 25 or newer is required (found Java $JAVA_VERSION)"
fi

info "using Java $JAVA_VERSION"

# ------------------------------------------------------------
# Find latest GitHub release
# ------------------------------------------------------------

info "checking latest Rock Core release..."

VERSION="$(
    curl -fsSL \
        -H "Accept: application/vnd.github+json" \
        "$GITHUB_API/releases/latest" |
    sed -n 's/.*"tag_name": *"\([^"]*\)".*/\1/p' |
    head -n1
)"

[ -n "$VERSION" ] ||
    die "could not determine latest release"

# Allow releases tagged v1.3.0 while using 1.3.0 for the asset.
VERSION="${VERSION#v}"

case "$VERSION" in
    ''|*[!0-9.]*)
        die "invalid release version: $VERSION"
        ;;
esac

JAR_URL="$RELEASE_BASE/$VERSION/rock-core-$VERSION.jar"

info "latest version: $VERSION"

# ------------------------------------------------------------
# Create installation directory
# ------------------------------------------------------------

mkdir -p "$INSTALL_DIR"

# ------------------------------------------------------------
# Download JAR
# ------------------------------------------------------------

TMP_JAR="$(mktemp)"

cleanup() {
    rm -f "$TMP_JAR"
}

trap cleanup EXIT

info "downloading Rock Core $VERSION..."

curl \
    --fail \
    --location \
    --show-error \
    --retry 3 \
    --connect-timeout 10 \
    --output "$TMP_JAR" \
    "$JAR_URL"

[ -s "$TMP_JAR" ] ||
    die "downloaded JAR is empty"

# ------------------------------------------------------------
# Basic JAR validation
# ------------------------------------------------------------

if command -v unzip >/dev/null 2>&1; then
    unzip -t "$TMP_JAR" >/dev/null 2>&1 ||
        die "downloaded file is not a valid JAR"
fi

# ------------------------------------------------------------
# Install JAR
# ------------------------------------------------------------

install -m 644 "$TMP_JAR" "$INSTALL_DIR/rock-core.jar"

info "installed JAR to $INSTALL_DIR/rock-core.jar"

# ------------------------------------------------------------
# Download launcher
# ------------------------------------------------------------

info "downloading Rock Core launcher..."

TMP_ROCK="$(mktemp)"

curl \
    --fail \
    --location \
    --show-error \
    --retry 3 \
    --connect-timeout 10 \
    --output "$TMP_ROCK" \
    "$ROCK_URL"

[ -s "$TMP_ROCK" ] ||
    die "downloaded launcher is empty"

# Make sure it looks like a shell script rather than an error page.
head -n1 "$TMP_ROCK" | grep -q '^#!' ||
    die "downloaded launcher does not appear to be a script"

install -m 755 "$TMP_ROCK" "$BIN"

rm -f "$TMP_ROCK"

info "installed launcher to $BIN"

# ------------------------------------------------------------
# Done
# ------------------------------------------------------------

echo
echo "Rock Core $VERSION installed successfully."
echo
echo "Run:"
echo "  rock"
echo
echo "Launcher:"
echo "  $BIN"
echo
echo "JAR:"
echo "  $INSTALL_DIR/rock-core.jar"