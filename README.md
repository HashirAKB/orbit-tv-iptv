<div align="center">

# Orbit TV

**A live-TV app for Android built on the public [iptv-org](https://github.com/iptv-org/iptv) catalogue.**

Roughly 10,000 free-to-air channels from 100+ countries — browsable, searchable and watchable,
instead of a 10 MB playlist file you paste into VLC.

[![CI](https://github.com/HashirAKB/orbit-tv-iptv/actions/workflows/ci.yml/badge.svg)](https://github.com/HashirAKB/orbit-tv-iptv/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/HashirAKB/orbit-tv-iptv?label=download%20apk)](https://github.com/HashirAKB/orbit-tv-iptv/releases/latest)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue)](LICENSE)
[![Min SDK](https://img.shields.io/badge/minSdk-24-green)](app/build.gradle.kts)
[![Kotlin](https://img.shields.io/badge/kotlin-2.0.21-7F52FF)](https://kotlinlang.org)

</div>

---

## Why

`https://iptv-org.github.io/iptv/index.m3u` works perfectly well in VLC. It is also a flat list of
ten thousand URLs with no search, no artwork, no memory of what you watched, and no idea which
half of its links are dead today.

Orbit TV is the front end that list deserves: category rails, real search, channel logos,
favourites, and a player that quietly fails over to a different mirror when one dies.

No account. No tracking. No ads. No analytics SDK.

## Install

**Easiest — grab the APK:** open [**Releases**](https://github.com/HashirAKB/orbit-tv-iptv/releases/latest)
on your phone, download `OrbitTV.apk`, tap it, and allow your browser to install apps when Android asks.

Android 7.0 (API 24) or newer.

Prefer to build it yourself, including from a phone with no computer? See
[`docs/INSTALL.md`](docs/INSTALL.md).

## Features

- **Organised, not dumped.** Home rails by category and by your own country, full browse-by-country
  and browse-by-category grids, and a daily featured channel.
- **Search that ranks.** Matches channel names, alternative names, country and category; exact
  matches first, then prefix, then substring.
- **Artwork everywhere.** Logos are joined in from the iptv-org logo index. Where one is missing,
  the tile falls back to a gradient derived from the channel ID plus its initials — deterministic,
  so a channel looks the same on every launch and a grid never looks broken.
- **Favourites and recents**, stored on device via DataStore. Nothing leaves the phone.
- **Dead-link handling.** A large share of public IPTV links are offline at any moment. Orbit learns
  which in two ways: instantly, when playback fails, and by probing — in bounded batches — the
  channels you are most likely to open next. Channels whose every source is known-dead get hidden;
  the eye icon on Home turns that off. See [`docs/DATA-PIPELINE.md`](docs/DATA-PIPELINE.md).
- **Automatic source failover.** Most channels carry several mirrors. When one errors the player
  walks to the next and only reports failure once all are exhausted.
- **Player.** Full screen, fit/zoom toggle, rotation lock, picture-in-picture, and a
  "more from this country" zapper strip.
- **Per-stream headers.** `Referer` and `User-Agent` from the catalogue are applied per source,
  which is what makes a number of otherwise-403 streams play.

## How it works

On first launch the app pulls six JSON endpoints from the iptv-org API, joins them into a single
catalogue, and caches the merged result on device for 24 hours.

```
channels.json ─┐
streams.json ──┤
logos.json ────┼──▶ CatalogRepository.merge() ──▶ Catalog ──▶ catalog-v1.json (cache)
feeds.json ────┤                                     │
countries.json ┤                                     ├─ byId
categories.json┘                                     ├─ byCountry
                                                     └─ byCategory
```

Streams with no channel ID are skipped — they carry no name, logo or country, and would look like
junk in a poster grid. Channels marked closed upstream are dropped. Adult channels are filtered out.

Full detail: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) and
[`docs/DATA-PIPELINE.md`](docs/DATA-PIPELINE.md).

## Built with

| | |
|---|---|
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose (BOM 2024.12.01), Material 3 — dark-only |
| Playback | AndroidX Media3 / ExoPlayer 1.5.1 (HLS) |
| Images | Coil 2.7 — `coil-compose` + `coil-svg` |
| Networking | OkHttp 4.12 |
| Serialization | kotlinx.serialization JSON 1.7.3 |
| Storage | DataStore Preferences + a merged JSON cache |
| DI | A hand-rolled service locator. No Hilt, no KSP, no annotation processing. |

No Room, no code generation, no third-party analytics. The whole app is ~3,100 lines of Kotlin.

## Building

Nothing but a JDK 17 is required — Gradle fetches the Android SDK components it needs.

```bash
git clone https://github.com/HashirAKB/orbit-tv-iptv.git
cd orbit-tv-iptv
gradle assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

Or open the folder in Android Studio (Ladybug or newer) and press Run.

> **Signing:** release builds are signed with the standard Android debug key so that CI can produce
> an immediately installable APK. That is fine for sideloading and wrong for Play Store
> distribution. If you fork this to publish, wire in a real keystore first.

## Project layout

```
app/src/main/java/dev/hashir/orbittv/
├── OrbitApp.kt            Application + service locator + Coil image loader
├── data/
│   ├── ApiModels.kt       Wire models for the iptv-org API
│   ├── Models.kt          Domain model and indexes
│   ├── CatalogRepository.kt  Fetch, join, cache
│   ├── UserPrefs.kt       Favourites, recents, toggles
│   └── StreamHealth.kt    Dead-link learning and probing
├── ui/
│   ├── MainActivity.kt    Nav host, bottom bar, channel sheet host
│   ├── OrbitViewModel.kt  Shared state, filtering, search
│   ├── components/        Tiles, rails, grids, empty states
│   ├── screens/           Home, Browse, Search, Saved, Collection, Sheet, Loading
│   └── theme/             Colours and type
└── player/
    └── PlayerActivity.kt  ExoPlayer, failover, PiP, zapper
```

## Contributing

Issues and pull requests are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md).

A dead channel is almost always a problem with the upstream catalogue rather than with this app.
Report those to [iptv-org/iptv](https://github.com/iptv-org/iptv/issues); there is a
[dead-channel issue template](.github/ISSUE_TEMPLATE/dead_channel.yml) here for cases where Orbit
is at fault — for example, a stream that plays in VLC but not in Orbit.

## Legal

Orbit TV is a client. It does not host, curate, mirror, transcode or rebroadcast any media, and it
ships with no channel list of its own — it reads an index that the iptv-org project publishes
publicly. Availability, licensing, geo-restriction and legality of any given stream are entirely
down to whoever operates it.

Check what is lawful where you are before using it. If you own a stream that appears in the
catalogue and want it gone, the request belongs
[upstream at iptv-org](https://github.com/iptv-org/iptv/issues), which is where the data lives.

## Credits

The entire catalogue — channels, feeds, logos, countries, categories and stream URLs — comes from
the volunteers at [**iptv-org**](https://github.com/iptv-org). This app is a window onto their work.

## License

[Apache License 2.0](LICENSE) — see also [NOTICE](NOTICE).
