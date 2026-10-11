# LUCIFER — "Liquid Crystal" UI Redesign Mockups

10 mockups covering **every tab / screen page** of the LUCIFER (CS Launcher V2) fork, rebuilt in a
frosted liquid-glass visual language. No feature from the current build was removed — the
"old → new home" mapping is listed in `gallery.html`.

## The screens

| # | Screen | Covers |
|---|--------|--------|
| 01 | `01-nexus-home.jpg` | Launcher hub — branding, social orbs, account chip, featured server, 3D character, launch orb, version pill, instance cards, new instance |
| 02 | `02-accounts-who-is-playing.jpg` | "Who is playing?" — add account, Microsoft / Ely.by / Local, active account, delete |
| 03 | `03-instances-profiles.jpg` | Profile list, instance hero (loader/Java/RAM/FPS), launch-clone-edit-delete, instance settings, new modpack, import/export |
| 04 | `04-version-manager.jpg` | Version list, Vanilla/Forge/NeoForge/Fabric/Quilt/OptiFine/BTA filters, install panel, download queue, loader tiles |
| 05 | `05-mod-store-modpacks.jpg` | Modrinth + CurseForge, search, mod grid, mod detail, dependencies, builder stepper |
| 06 | `06-skin-cape-studio.jpg` | Live 3D skin stage, classic/slim, skin slot, cape slot, camera controls, save/reset |
| 07 | `07-server-hub.jpg` | Featured CS Network card, licifermc (ping, players, maintenance), refresh/add/remove, servers.dat sync |
| 08 | `08-performance-lab.jpg` | RAM, performance mode, renderer, FPS presets, Java runtime, resolution scale, gyro, live telemetry, client features |
| 09 | `09-controls-cursor-studio.jpg` | Input Studio — cursor tuning + touch layout editor + gamepad mapping |
| 10 | `10-settings-about.jpg` | Settings sections + search + Plus Pro upsell + About / licences / changelog |

## What is deliberately new

- **Left icon rail is gone** → floating 5-orb glass dock at the bottom centre
  (Home · Instances · Mod Store · Server Hub · Settings). Account identity moved to a top-right medallion.
  Cursor + Custom Controls + Gamepad merged into one *Input Studio* screen.
- **White pill LAUNCH button is gone** → circular liquid-core Launch orb with an orbiting progress halo
  (same orb shows download progress).
- **Flat widget cards are gone** → thick frosted glass slabs, chromatic rims, specular highlights,
  liquid caustic bloom, floating shadows.

## Design tokens

| Token | Value |
|-------|-------|
| Void background | `#06070F` + aurora nebula, bokeh embers, blurred voxel horizon |
| Crystal Cyan | `#35E6FF` |
| Aura Violet | `#8B6CFF` |
| Prism Magenta | `#FF4FD8` |
| Reactor Mint | `#7CFFCB` |
| Glass panel | white 5–9% over backdrop, 24 dp squircle, 1 px rim at 14–20% |
| Blur | 18–28 dp backdrop blur, ~140% saturation, top-left specular highlight |

## Viewing

Open `gallery.html` (it embeds all 10 images with per-screen notes and the full feature-parity table).

## Building it for real (Android)

- Backdrop blur: `RenderEffect.createBlurEffect` (API 31+) or a blur-view fallback below that, always with a
  translucent fallback colour so older devices still read correctly.
- Rims / glows: layer-list + radial gradient drawables (not 9-patch) so they scale with density.
- Dock: one `FrameLayout` with 5 animated orbs replaces the left rail in
  `app_pojavlauncher/src/main/res/layout/fragment_home_fastclient.xml`.
- Launch orb: custom view with a rotating halo shader, hosting both launch and download-progress states.
- Aurora backdrop: one static blurred asset with slow parallax instead of a live shader (battery).

> These mockups are AI-rendered **visual direction**, not pixel-accurate specs — use them as the target look
> and build the real layouts in Android XML.
