# Changelog

All notable changes to this project are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

Nothing yet.

## [1.0.0] - 2026-11-05

First public release.

### Added

- Catalogue built from six iptv-org API endpoints, joined on device and cached for 24 hours.
- Home screen with a daily featured channel, "Continue watching", favourites, a rail for your own
  country, and twelve category rails.
- Browse by country and by category, both ranked by channel count.
- Search across channel names, alternative names, country and category, ranked exact to substring.
- Favourites and watch history, stored locally with DataStore.
- Dead-link learning: playback failures recorded instantly, plus bounded background probing of the
  channels you are most likely to open. Channels with no live mirror are hidden by default.
- Automatic failover across a channel's mirrors before any error is shown.
- Player with fit/zoom, rotation lock, picture-in-picture and a same-country zapper strip.
- Per-stream `Referer` and `User-Agent` headers, which are what make several otherwise-403 streams
  play.
- Generated gradient-and-initials artwork fallback for channels with no logo.

### Known limitations

- No EPG. iptv-org publishes guide *sources*, not guide data.
- No Chromecast.
- Streams with no channel ID in the upstream data are skipped — they carry no name, logo or country.
- Release builds are signed with the Android debug key.
- No automated tests.

[Unreleased]: https://github.com/HashirAKB/orbit-tv-iptv/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/HashirAKB/orbit-tv-iptv/releases/tag/v1.0.0
