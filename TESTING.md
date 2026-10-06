# Getting MeldPDF onto a Play testing track

The ordered, do-this-then-that runbook for a first **internal testing** release.
Everything in the repo is ready; the remaining steps need your Google Play
account and can't be automated from CI. Source material: `store/listing.md`
(paste-ready text), `store/README.md` (assets), `PRIVACY.md` (policy).

Package name — **permanent**, cannot change after first upload:
`com.mikmy.meldpdf`

---

## 0. One-time prerequisites

- A **Google Play Developer** account (one-time US$25 registration).
- These tools on your machine for the key + secrets step: `git bash`,
  `openssl`, and the **GitHub CLI** (`gh auth login`).

---

## 1. Privacy policy URL  ·  *required by Play (the app uses the ad ID)*

Hosted with your other apps' policies in the `Mylonas/privacy` repo (GitHub
Pages), already live:

**`https://mylonas.github.io/privacy/meldpdf.html`**

Use it in step 4 (App content → Privacy policy). Contact email on the listing:
`mikmylona@gmail.com` (same as the other apps).

---

## 2. Signing key + repo secrets  ·  *CI needs these to build a signed AAB*

Run in Git Bash, from the repo root:

```bash
bash store/make-upload-key.sh    # creates ONE upload key; back up the .p12 off your laptop
bash store/push-secrets.sh       # pushes signing + AdMob secrets to the repo with gh
```

`push-secrets.sh` sets six secrets. It will prompt you for the keystore password
and the two AdMob ids:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | base64 of the upload keystore (from `make-upload-key.sh`) |
| `KEYSTORE_PASSWORD` | the keystore password you chose |
| `KEY_ALIAS` | `upload` |
| `KEY_PASSWORD` | same as the keystore password |
| `ADMOB_APP_ID` | this app's AdMob **App ID** — `ca-app-pub-XXXX~YYYY` |
| `ADMOB_INTERSTITIAL_ID` | this app's interstitial **ad unit** — `ca-app-pub-XXXX/ZZZZ` |

> Create the AdMob app + interstitial unit at [apps.admob.com](https://apps.admob.com)
> first if you haven't — each app is its own AdMob app.

Then add the Play service-account key (see step 3):

```bash
gh secret set PLAY_SERVICE_ACCOUNT_JSON --repo Mylonas/meldpdf < service-account.json
gh secret list --repo Mylonas/meldpdf   # verify all 7 are present
```

---

## 3. Play service account  ·  *lets CI upload straight to the track*

1. In **Play Console → Users and permissions → Invite new users**, or via a
   linked Google Cloud project, create a **service account** and download its
   JSON key.
2. Invite it with **"Release apps to testing tracks"** on this app.
3. Add the JSON as the `PLAY_SERVICE_ACCOUNT_JSON` secret (command above).

> A fresh key returning **403** usually just means Play's permission change
> hasn't propagated yet — wait and retry, don't regenerate.

---

## 4. Create the app + store listing in Play Console

Create the app (MeldPDF · App · Free · en-US), then fill these from
`store/listing.md`:

- **Main store listing** — short + full description, upload:
  - App icon `store/icon-512.png`
  - Feature graphic `store/feature-graphic-1024x500.png`
  - **2–8 phone screenshots** — download the latest **Emulator smoke test**
    run's `meldpdf-emulator` artifact and upload its `shots/*.png`.
    (Regenerate the icon/feature PNGs anytime with `cd store && npm i && npm run gen`.)
- **App content** declarations:
  - Ads: **Yes** (Google AdMob)
  - Target audience: **13+**
  - Privacy policy: the URL from step 1
  - **Data safety**: Device or other IDs → **Advertising ID**, collected + shared,
    purpose Advertising/marketing, encrypted in transit, not deletable
    (exact answers in `store/listing.md`)
  - Content rating questionnaire (utility app → Everyone)

---

## 5. Publish to the internal track

Actions → **Publish to Play** → *Run workflow*:

| Input | First release |
| --- | --- |
| `versionCode` | `1` |
| `versionName` | `1.0.0` |
| `track` | `internal` |
| `status` | `completed` |

> **A brand-new ("draft") app** accepts an *internal*-track release with
> `status: completed` outright. A *closed* track (alpha/beta) on a draft app
> only accepts `status: draft` — then you press **Rollout** once in the Console.
> Production is deliberately not an option in this workflow.

Every later upload must use a **higher `versionCode`** than anything already
uploaded.

---

## 6. Invite testers

- **Internal testing** → add testers by email (up to 100), share the opt-in link.
- Moving to **closed testing** later for production eligibility: Play currently
  wants **≥12 testers opted in for 14 days**.

---

## Quick status

| Item | State |
| --- | --- |
| Signed-AAB build pipeline (`publish.yml`) | ✅ ready |
| App icon + feature graphic | ✅ generated + validated |
| Listing text + data-safety answers | ✅ `store/listing.md` |
| What's-new note | ✅ `distribution/whatsnew/` |
| Privacy policy page | ✅ live at mylonas.github.io/privacy/meldpdf.html |
| Repo secrets (signing, AdMob, Play SA) | ⬜ step 2–3 (you) |
| Play Console app + listing + content | ⬜ step 4 (you) |
| Screenshots uploaded | ⬜ from emulator artifact (step 4) |
| First internal release | ⬜ step 5 |
