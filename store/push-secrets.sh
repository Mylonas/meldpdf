#!/usr/bin/env bash
#
# Pushes this repo's release secrets to GitHub with `gh`. One upload key can
# sign every app, so the four signing secrets are the same everywhere; only the
# AdMob ids differ per app.
#
#   bash store/push-secrets.sh
#
# Nothing is written to disk and nothing is echoed. The password is read with
# echo off and passed to `gh` on stdin, so it never appears in shell history,
# a command line, or the process list.
set -euo pipefail

# The scaffolder fills these in for the app it generates.
REPO="${REPO:-Mylonas/meldpdf}"
AD_UNIT_SECRET="${AD_UNIT_SECRET:-ADMOB_INTERSTITIAL_ID}"   # ADMOB_INTERSTITIAL_ID or ADMOB_REWARDED_ID
KEY_DIR="${KEY_DIR:-$HOME/play-upload-key}"
B64="$KEY_DIR/upload-keystore.b64"
ALIAS="${ALIAS:-upload}"

command -v gh >/dev/null || { echo "gh (GitHub CLI) not found."; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "Not logged in. Run: gh auth login"; exit 1; }
[ -f "$B64" ] || { echo "No keystore base64 at $B64. Run make-upload-key.sh first."; exit 1; }

echo "Repo       : $REPO"
echo "Upload key : $B64"
printf 'Keystore password: '; read -rs KS_PASS; echo
[ -n "$KS_PASS" ] || { echo "Empty password, aborting."; exit 1; }

echo "--- AdMob ids for this app (each app is its own AdMob app) ---"
printf '  App ID        (ca-app-pub-XXXX~YYYY) : '; read -r APP_ID
printf "  Ad unit       (ca-app-pub-XXXX/ZZZZ) : "; read -r AD_UNIT
case "$APP_ID" in *'~'*) ;; *) echo "  !! App ID needs a ~"; exit 1;; esac
case "$AD_UNIT" in *'/'*) ;; *) echo "  !! ad unit id needs a /"; exit 1;; esac

echo "Setting secrets on $REPO"
gh secret set KEYSTORE_BASE64    --repo "$REPO" < "$B64"
printf '%s' "$KS_PASS" | gh secret set KEYSTORE_PASSWORD --repo "$REPO"
printf '%s' "$ALIAS"   | gh secret set KEY_ALIAS         --repo "$REPO"
printf '%s' "$KS_PASS" | gh secret set KEY_PASSWORD      --repo "$REPO"
printf '%s' "$APP_ID"  | gh secret set ADMOB_APP_ID      --repo "$REPO"
printf '%s' "$AD_UNIT" | gh secret set "$AD_UNIT_SECRET" --repo "$REPO"
unset KS_PASS

echo "Done. For Play publishing also add the service-account key:"
echo "  gh secret set PLAY_SERVICE_ACCOUNT_JSON --repo $REPO < service-account.json"
echo "Verify with:  gh secret list --repo $REPO"
