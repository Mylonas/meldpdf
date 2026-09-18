#!/usr/bin/env bash
#
# Creates ONE upload key that signs your Play uploads, using openssl only — no
# JDK required. Run it once; reuse the same key across every app you publish
# under the same developer account (Play lets one upload key sign many apps).
#
# Run it in Git Bash:   bash store/make-upload-key.sh
#
# Bash does the password prompting, not openssl: openssl asks the *Windows
# console* for a hidden password, and Git Bash (MinTTY) is not a Windows console,
# so openssl's own prompt never appears and it blocks forever on a blank line.
# Going through the environment also keeps the password out of the process list.
set -euo pipefail

OUT_DIR="${1:-$HOME/play-upload-key}"
ALIAS="upload"
NAME="${CERT_NAME:-Developer}"
COUNTRY="${CERT_COUNTRY:-US}"

command -v openssl >/dev/null || { echo "openssl not found. Run this in Git Bash."; exit 1; }

if [ -e "$OUT_DIR/upload-keystore.p12" ]; then
  echo "You already have an upload key at $OUT_DIR/upload-keystore.p12"
  echo "Reusing it — the same key can sign all your apps."
  if [ ! -e "$OUT_DIR/upload-keystore.b64" ]; then
    base64 -w 0 "$OUT_DIR/upload-keystore.p12" > "$OUT_DIR/upload-keystore.b64"
  fi
  echo "  base64 : $OUT_DIR/upload-keystore.b64"
  echo "  alias  : $ALIAS"
  echo "Push it with:  bash store/push-secrets.sh"
  exit 0
fi

mkdir -p "$OUT_DIR"; cd "$OUT_DIR"

echo "Choose a keystore password. Put it in your password manager —"
echo "losing it means losing the ability to update your listings."
printf 'Keystore password: '; read -rs PW1; echo
printf 'Type it again    : '; read -rs PW2; echo; echo
[ "$PW1" = "$PW2" ] || { echo "Those did not match. Nothing was created."; exit 1; }
[ -n "$PW1" ] || { echo "An empty password is not usable. Nothing was created."; exit 1; }

# MSYS_NO_PATHCONV=1 is essential on Windows: without it Git Bash rewrites the
# "/CN=..." subject into a filesystem path and openssl rejects it.
MSYS_NO_PATHCONV=1 openssl req -x509 \
  -newkey rsa:2048 -sha256 -days 10000 -noenc \
  -keyout tmp.key -out tmp.crt \
  -subj "/CN=$NAME/O=$NAME/C=$COUNTRY" >/dev/null 2>&1

export KS_PW="$PW1"
openssl pkcs12 -export -inkey tmp.key -in tmp.crt \
  -name "$ALIAS" -out upload-keystore.p12 -passout env:KS_PW
unset KS_PW PW1 PW2
shred -u tmp.key tmp.crt 2>/dev/null || rm -f tmp.key tmp.crt
base64 -w 0 upload-keystore.p12 > upload-keystore.b64

echo "Done."
echo "  keystore : $OUT_DIR/upload-keystore.p12   <- BACK THIS UP, off this laptop"
echo "  base64   : $OUT_DIR/upload-keystore.b64   <- goes into the GitHub secret"
echo "  alias    : $ALIAS"
echo "Next:  bash store/push-secrets.sh"
