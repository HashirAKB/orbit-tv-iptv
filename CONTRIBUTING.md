# Contributing to Orbit TV

Thanks for taking a look. This is a small project and the bar for contributing is low — a clear bug
report is as welcome as a pull request.

## Before you file a dead channel

**Most "channel doesn't work" reports belong upstream.** Orbit TV ships no channel list; it reads
the index published by [iptv-org](https://github.com/iptv-org/iptv). If a stream is dead, the fix
is a change to their data, and reporting it here cannot help.

File it here only when Orbit is plausibly at fault — the clearest signal being **a stream that plays
in VLC but not in Orbit**. There is a
[dedicated template](https://github.com/HashirAKB/orbit-tv-iptv/issues/new?template=dead_channel.yml)
for exactly that case.

## Getting set up

```bash
git clone https://github.com/HashirAKB/orbit-tv-iptv.git
cd orbit-tv-iptv
gradle assembleDebug
```

JDK 17, and that's it. Android Studio Ladybug or newer if you want the IDE.

> The Gradle wrapper is not committed — CI pins Gradle 8.11.1 explicitly instead. If you want
> `./gradlew`, run `gradle wrapper --gradle-version 8.11.1` once; Android Studio also offers to
> generate it on first sync.

No development machine? Fork it and let CI build — see [docs/INSTALL.md](docs/INSTALL.md).

## House style

The project has a point of view. Please work with it rather than around it:

- **Smallest change that does the job.** No speculative abstraction, no "while I was in here"
  refactors, no reformatting unrelated lines. A diff should be readable in one pass.
- **No new build-time machinery** without a strong reason. There is deliberately no DI framework,
  no annotation processing, no code generation and no Room. Each of those is a build failure
  surface, and this app does not need them. If you think one has become necessary, open an issue
  first and make the case.
- **Dependencies are a cost.** New ones need a reason the standard library and the existing stack
  can't meet.
- **Match surrounding style.** Four-space indent, trailing commas, explicit types on public
  signatures, Kotlin official style.
- **Comment the *why*.** The code already says what it does. Comments earn their place by
  explaining a trade-off, a workaround or a non-obvious constraint.
- **Dead code goes.** If your change orphans something, delete it in the same PR.

## Before opening a pull request

Please actually do these — the project has no test suite yet, so manual verification is the only
gate there is:

1. `gradle assembleDebug` passes.
2. You installed the APK and exercised the change on a real device or emulator.
3. You can state what you verified, concretely. "Searched for 'BBC', opened BBC One, confirmed
   failover when I killed Wi-Fi mid-stream" beats "works fine".
4. The PR description matches what the diff does.

Write that verification into the PR body. The template asks for it.

## Testing

There are no automated tests. That is a gap, not a principle — the pieces most worth covering are
`CatalogRepository.merge()` (pure, deterministic, easy to fixture) and `StreamHealth`'s TTL and
eviction logic. A PR adding JVM unit tests for either would be very welcome, and is probably the
single most useful contribution available right now.

## Commits

Short imperative subject lines. Conventional Commits are welcome but not enforced.

```
Fix source failover skipping the last mirror
Add Malayalam to the default search suggestions
```

## Licensing

By contributing you agree your work is licensed under the
[Apache License 2.0](LICENSE), the same as the rest of the project. Per-file licence headers are
not used here; the top-level `LICENSE` and `NOTICE` cover the repository.

## Code of Conduct

Participation is governed by the [Contributor Covenant](CODE_OF_CONDUCT.md).
