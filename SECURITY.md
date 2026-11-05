# Security Policy

## Supported versions

Only the latest release is supported. This is a small side project with a single maintainer; please
reproduce on the newest build before reporting.

## Reporting a vulnerability

Please **do not open a public issue** for a security problem.

Use GitHub's private reporting: **Security → Report a vulnerability** on this repository
(<https://github.com/HashirAKB/orbit-tv-iptv/security/advisories/new>). That keeps the report
private until a fix exists.

Expect an acknowledgement within a week. There is no bounty.

## Scope

In scope:

- Anything that lets a stream URL or catalogue response execute code, escape the player, or read
  app-private data.
- Mishandling of the on-device stores (`catalog-v1.json`, `stream-health.tsv`, DataStore prefs).
- Intent handling on `PlayerActivity`.

Known and accepted, so not worth reporting:

- **Cleartext HTTP is permitted.** A large share of the upstream catalogue is plain `http://`, so
  `usesCleartextTraffic` is on. These are public broadcast streams, not authenticated traffic.
- **Release builds are signed with the Android debug key.** Deliberate, so CI can publish an
  installable APK with no secrets. Documented in the README. Forks that publish must change it.
- **Arbitrary third-party URLs are contacted.** That is the entire function of the app. Stream
  hosts see your IP, as they would in VLC.
- **No certificate pinning.** Against ten thousand unrelated origins it would be meaningless.

## What Orbit TV does not do

No account, no telemetry, no analytics SDK, no crash reporting, no ads. Favourites, recents and the
stream-health cache never leave the device. The only outbound requests are to
`iptv-org.github.io` for the catalogue, to logo hosts for artwork, and to whichever stream you
choose to play.
