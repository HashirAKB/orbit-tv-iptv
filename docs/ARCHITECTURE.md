# Architecture

Orbit TV is deliberately small. There is no DI framework, no annotation processing, no Room, no
code generation, and no module split. The whole app is one Gradle module and about 3,100 lines of
Kotlin. Everything below exists because something in the app needed it, not because it is the
standard shape of an Android project.

## Shape

```
                     ┌──────────────────────────────────────┐
                     │  OrbitApp (Application)              │
                     │  • Coil ImageLoader (+SVG decoder)   │
                     │  • Graph.init()                      │
                     └──────────────┬───────────────────────┘
                                    │
                     ┌──────────────▼───────────────────────┐
                     │  Graph  (service locator)            │
                     │  catalog · prefs · health            │
                     └───┬───────────┬──────────────┬───────┘
                         │           │              │
          ┌──────────────▼──┐  ┌─────▼────────┐  ┌──▼──────────────┐
          │CatalogRepository│  │  UserPrefs   │  │  StreamHealth   │
          │ fetch/join/cache│  │  DataStore   │  │ dead-link store │
          └──────────────┬──┘  └─────┬────────┘  └──┬──────────────┘
                         └───────────┼──────────────┘
                                     │
                         ┌───────────▼─────────────┐
                         │     OrbitViewModel      │
                         │ state · filter · search │
                         └───────────┬─────────────┘
                                     │
                 ┌───────────────────┼───────────────────┐
                 │                   │                   │
          ┌──────▼──────┐   ┌────────▼───────┐   ┌───────▼────────┐
          │ MainActivity│   │   screens/     │   │ PlayerActivity │
          │  NavHost    │   │ components/    │   │  ExoPlayer     │
          └─────────────┘   └────────────────┘   └────────────────┘
```

`PlayerActivity` reads `Graph` directly rather than going through `OrbitViewModel`. It is a separate
activity with a separate lifecycle and only needs three things — the channel, the health store and
prefs — so routing it through a shared ViewModel would have bought nothing.

## Why a service locator

`Graph` is an `object` with three `lateinit` properties, initialised once in `Application.onCreate`.

Hilt would have added KSP, a compiler plugin, generated code, and a build-time failure surface, to
inject exactly three singletons that live for the whole process. For an app this size the locator
is the smaller correct answer. If the app grows a second feature module, revisit it.

The cost is honest: `Graph` is global mutable state and makes unit-testing the ViewModel awkward.
That is a real trade and it is accepted here rather than hidden.

## Catalogue loading

`CatalogRepository.load()` is the only entry point and it is idempotent:

1. If a merged cache exists on disk and is under 24 hours old, parse and publish it. Done.
2. Otherwise fetch six endpoints, merge, write the cache, publish.
3. If the network fails and a stale cache exists, publish the stale cache rather than an error.
4. Only with no cache and no network does it publish `CatalogState.Failed`.

A `Mutex` guards against two callers racing on cold start. Progress is published as
`CatalogState.Loading(step, fraction)` so the splash shows what it is doing rather than an
indeterminate spinner for twenty seconds.

The merge step is documented separately in [DATA-PIPELINE.md](DATA-PIPELINE.md).

### The cache is the merged result, not the raw responses

Caching the six raw JSON files would mean re-running the join on every cold start. Caching the
merged `Catalog` means a cold start is one `decodeFromString` over a few MB. `Channel` is
`@Serializable` with its search haystack built in the class body, so it is recomputed on load and
never serialised.

## Indexes

`Catalog` builds `byId`, `byCountry` and `byCategory` in property initialisers — once, at
construction. Every screen then reads from a map instead of filtering 10,000 items per
recomposition. `Channel.search` is a pre-lowercased haystack built at merge time for the same
reason.

## State and filtering

`OrbitViewModel` exposes the catalogue, favourites, recents and toggles as `StateFlow`s, and owns
two shared helpers:

- `visible(channels)` — applies the adult filter and the offline filter.
- `search(catalog, query)` — term-wise match over the haystack, then ranks exact → prefix →
  substring, then by name length.

Screens call these inside `remember(...)` keyed on the inputs that can change the answer —
including `healthVersion`, which bumps whenever the dead-link store learns something, so the grid
re-filters without any manual invalidation.

## Playback

`PlayerActivity` rebuilds its media pipeline whenever the channel, the chosen source index or the
retry counter changes. It has to: `Referer` and `User-Agent` are per-source, which means a new
`DefaultHttpDataSource.Factory` per source rather than one for the player's lifetime.

Failure is treated as routine rather than exceptional:

```
onPlayerError
  └─▶ mark this URL dead in StreamHealth
      └─▶ is there another source?
            yes → sourceIndex++   (silent, user sees a reload)
            no  → show "Stream unavailable" with Retry / Back
```

`STATE_READY` reports the URL alive, guarded by an in-memory set so a rebuffer does not rewrite the
health file.

## Threading

- Network and disk run on `Dispatchers.IO`, inside `withContext` in the repository.
- `StreamHealth` guards its map with `synchronized` because it is touched from the player's
  listener thread, the probe coroutines and the UI.
- Probes are bounded by a `Semaphore(8)` and a per-call limit. Nothing ever sweeps the whole list.

## Deliberate omissions

| Not here | Why |
|---|---|
| EPG / programme guide | iptv-org publishes guide *sources*, not guide data. Fetching and parsing XMLTV per channel is a feature in its own right. |
| Chromecast | Adds the Cast SDK, a Play Services dependency and an expanded UI surface, for something a minority use. |
| Room | The data is read-mostly and fits in memory. A JSON file is enough. |
| Streams without a channel ID | No name, no logo, no country. They would be visible noise in a grid built around artwork. |
| Real signing config | Debug-key signing keeps CI producing an installable APK with zero secrets. Forks that publish must change this. |
