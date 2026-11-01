# PhotoVeil

**Invisible armour for your photos.** PhotoVeil adds a shield to an image's
pixels that you cannot see, and that an AI image editor cannot cleanly step
around. It runs entirely on your phone — no upload, no account, no network.

- **App:** Android 8.0+ (Kotlin, Jetpack Compose), signed APK from GitHub Releases
- **Site:** <https://merry8989-uk.github.io/photoveil/> — includes a live
  in-browser demo running the same algorithm
- **Version:** 1.0.0 · **Package ID:** `io.github.merry8989uk.photoveil` (permanent)

---

## Where the idea comes from

This project follows the idea in **PhotoGuard** — *Raising the Cost of Malicious
AI-Powered Image Editing* (Salman, Khaddaj, Leclerc, Ilyas, Madry —
[arXiv:2302.06588](https://arxiv.org/abs/2302.06588), [repo](https://github.com/MadryLab/photoguard)).
Their insight: push the image into a region of pixel space where a diffusion
model's encoder no longer lands on what you photographed, so an edit drifts away
from the original instead of refining it.

PhotoGuard does this with PGD gradients through Stable Diffusion's VAE, on a
CUDA GPU, in PyTorch. **No code from that repository is used here.** A phone has
no diffusion model and no GPU, so this is a from-scratch, gradient-free engine in
pure Kotlin (and a line-for-line JavaScript port for the browser demo).

## How the shield works

1. **A deterministic noise field.** `hash01(x, y, seed)` gives a stable
   pseudo-random value per pixel — no state, no allocation per row, reproducible
   from the seed alone. The field is then *band-shaped*: half low-passed with a
   separable five-tap kernel, half high-passed with a Laplacian. The low band is
   what a diffusion VAE's 8× downsampling folds back into the latent, and the
   part that best survives re-encoding; the high band carries the finest detail.
2. **A texture mask.** Amplitude follows the local luminance gradient
   (`MASK_FLOOR 0.32 + 2.2 × texture`, capped at 1.0). Busy areas absorb the
   perturbation invisibly; flat gradients like skin and sky get the least.
3. **A clamp instead of a fade.** The per-pixel delta is clamped to the room the
   pixel actually has (`[-min(r,g,b), 255-max(r,g,b)]`), so a near-white pixel
   gets a downward nudge rather than being skipped. No banding, no dead zones.
4. **Visible mode** additionally burns a tiled, 34/255-alpha stamp into the
   output — an honest visible deterrent on top of the invisible one.

The engine is streaming and band-based (96-row bands with a two-row margin), so a
12 MP photo never needs more than a few rows of working memory at a time.

## Measured behaviour

Not claims — numbers from the calibration harness in this repo, run over real
photographs (`trevor_5.jpg`, `hadi_trevor.png`, `hero_fig.png`), comparing the
shielded output against its source:

| Mode | Strength | Peak amplitude | Mean change | PSNR | Flat-area residual (before → after) |
|---|---|---|---|---|---|
| Standard | 0.40 | 6.8/255 | 0.37–0.50/255 | 51–52 dB | 0.11→0.40 … 0.58→0.85 |
| **Standard** | **0.60** | **9.2/255** | **0.49–0.68/255** | **49–50 dB** | **0.11→0.51 … 0.58→1.01** |
| Standard | 0.85 | 12.2/255 | 0.66–0.90/255 | 47–48 dB | 0.11→0.65 … 0.58→1.22 |
| Strong | 0.60 | 20.0/255 | 1.07–1.48/255 | 43 dB | 0.11→0.99 … 0.58→1.83 |

Two things that table says plainly:

- **Above ~42 dB PSNR is imperceptible in practice.** At the default it is
  49–50 dB. You cannot see it; the site's "difference ×20" view is the only way
  to.
- **JPEG erodes it.** A re-save at quality 90 pulls the flat-area residual back
  from ~1.0 to ~0.65 — most of the perturbation is gone. That is a real limit of
  any invisible perturbation, which is why PNG is the default output.

### The check

The **Check** tab compares the file you hold against the original and asks
whether the difference is *fine-grained* (noise-like → shield intact) or
*structural* (smooth → the image was edited). Measured on a real photo:

| Case | Mean difference | Fine-grained share | Verdict |
|---|---|---|---|
| Shielded (Standard 0.6) | 0.70/255 | 100% | Shield intact |
| Shielded, then JPEG q90 | 1.06/255 | 97% | Shield intact (JPEG noise looks similar — stated in the app) |
| Brightness +6 | 5.84/255 | 0% | Image was changed |
| Identical copy | 0.00/255 | 0% | No shield between these files |

The single-file "noise floor" reading is offered as a hint only, and the app says
so: a high-ISO photo can read as high as a shielded one.

## What it does not do

- It is a **deterrent, not a lock**. A determined person with a good model can
  still edit the photo; the shield raises the cost and the visible damage.
- **Resizing, cropping or screenshotting destroys it.** Any resample takes the
  perturbation with it.
- **It does not stop training scrapes.** It protects a specific file against a
  specific kind of editing, not your whole online presence.

## Repository layout

```
app/src/main/java/io/github/merry8989uk/photoveil/
├── core/ShieldEngine.kt     the engine: shield(), noiseFloor(), compare()
├── core/ImageIo.kt          decode, vault, gallery, share, EXIF
├── ui/ShieldScreen.kt       pick → shield → report → share/save
├── ui/VaultScreen.kt        every shielded image, share/delete
├── ui/InspectScreen.kt      original vs current file, with a verdict
├── settings/                DataStore-backed settings, About, contact
├── updater/                 in-app update check + install from GitHub Releases
└── diagnostics/             log collection (on by default), export .txt/.json/.md/.zip
docs/index.html              the public site, with the JS port of the engine
.github/workflows/           ci.yml (compile check), release.yml, pages.yml
```

## Build

```bash
./gradlew assembleDebug        # debug APK, no signing needed
./gradlew assembleRelease      # needs KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD
```

Releases are built by `.github/workflows/release.yml` on a `v*` tag: it decodes
the keystore from repository secrets, builds a signed APK + AAB, verifies the
signature with `apksigner`, and publishes them to GitHub Releases. The keystore
is never committed.

```bash
git tag v1.0.0 && git push origin v1.0.0
```

Bump `versionCode` and `versionName` in `app/build.gradle.kts` before tagging.
The package ID and signing key never change — that is what lets an update install
over the previous version without uninstalling.

## Contact

Built by **Amit** — <br19shc39@gmail.com>

Bugs, ideas, feature requests: email me, or follow
**[@osintgram_io](https://instagram.com/osintgram_io)** on Instagram and send a
DM. New builds and experiments go up there first.

MIT licensed.
