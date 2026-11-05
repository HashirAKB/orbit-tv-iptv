# The data pipeline

Everything Orbit TV shows comes from the [iptv-org API](https://github.com/iptv-org/api), a set of
static JSON files published to GitHub Pages and regenerated upstream on a schedule. Orbit ships no
channel list of its own.

## Endpoints used

| File | What it gives us |
|---|---|
| `channels.json` | Identity: id, name, alt names, country, categories, NSFW flag, closed date |
| `streams.json` | Playable URLs, plus `quality`, `referrer`, `user_agent`, `label` |
| `logos.json` | Artwork per channel/feed, with format, dimensions and an `in_use` flag |
| `feeds.json` | Per-feed languages and formats |
| `countries.json` | Country names and flag emoji |
| `categories.json` | Human-readable category names |

`channels.json` and `streams.json` are required; the other four fail soft. Losing `logos.json`
costs you artwork, not the app.

## The join

```
ApiStream[]  ──group by .channel──▶  Map<channelId, StreamSource[]>
ApiLogo[]    ──best per channel───▶  Map<channelId, url>
ApiFeed[]    ──languages by chan──▶  Map<channelId, Set<lang>>
ApiCountry[] ──by code────────────▶  Map<code, ApiCountry>
ApiCategory[]──by id──────────────▶  Map<id, name>
                   │
ApiChannel[] ──────┴──────────────▶  Channel[]  (only those with ≥1 stream)
```

### Logo selection

A channel often has several logos. They are scored and the highest wins:

```
score = (in_use ? 1000 : 0)
      + formatRank            PNG 50 · WebP 45 · JPEG 40 · SVG 30 · AVIF 20 · GIF 10 · unknown 25
      + min(width / 100, 20)
```

`in_use` dominates deliberately — a current, smaller logo beats a large obsolete one. Raster
outranks SVG because Coil decodes it more cheaply, but SVG is kept usable via `coil-svg`.

### What gets dropped

| Dropped | Rule | Why |
|---|---|---|
| Closed channels | `closed != null` | Off the air permanently |
| Channels with no stream | no entry in the stream map | Nothing to play |
| Streams with no channel ID | `stream.channel == null` | No name, logo or country — visible noise in an artwork-led grid |
| Adult channels | `is_nsfw` | Filtered by default; `UserPrefs.showAdult` exists but is not surfaced in the UI |

Dropping null-channel streams is the most consequential choice here — it removes a meaningful slice
of the raw list. It is a product decision, not a technical limit: those entries have a title and a
URL and nothing else, and a grid built around logos and countries has nowhere to put them.

### Sorting

Channels sort by lowercased name. Countries and categories sort by channel count descending, so
Browse leads with the places that actually have something to watch.

## Caching

The merged catalogue — not the six raw responses — is written to `filesDir/catalog-v1.json`.

- Fresh (< 24 h): parsed and used, no network.
- Stale: a refresh is attempted; if it fails, the stale copy is used anyway.
- The refresh icon on Home forces a re-fetch.

Bump the `v1` in the filename if the shape of `Channel` ever changes incompatibly.

## Stream health

A large fraction of public IPTV URLs are dead at any given moment. Probing all ~10,000 would be
slow, pointless and impolite to the origin servers. Orbit learns in two bounded ways instead.

**1. Playback truth (free, and the strongest signal).**
`PlayerActivity` reports every failure and every successful start to `StreamHealth`. A stream that
actually refused to play is marked dead immediately.

**2. Targeted probing.**
`StreamHealth.probe(channels, limit)` issues a ranged `GET` (`Range: bytes=0-1024`) with the
stream's own `User-Agent` and `Referer`, at concurrency 8, and only for sources not checked
recently. It is called for:

- favourites and recents, at startup;
- the first ~80 channels of your country, at startup;
- the first ~60 channels of a collection you open;
- the first ~40 search results.

Results live in `filesDir/stream-health.tsv` as `url<TAB>alive<TAB>timestamp`, capped at 4,000
entries with stale rows evicted first.

### How the filter reads it

```
isKnownDead(url)      = recorded dead AND checked within the last 5 days
isChannelOffline(ch)  = ch has sources AND every one of them isKnownDead
```

A channel is only hidden when *all* of its mirrors are known-dead. One live mirror keeps it
visible. The 5-day TTL means a stream that comes back gets rechecked rather than being banished
forever, and re-probing only happens after 12 hours.

The eye icon on Home toggles the whole filter off, which is the escape hatch when the heuristic is
wrong.

## Playing a stream

Per-source `Referer` and `User-Agent` headers are applied to the HTTP data source. This is not
cosmetic: a number of streams in the catalogue return 403 without them, and those channels simply
would not play otherwise.

Cleartext HTTP is permitted via `network_security_config.xml` because a substantial share of
upstream URLs are plain `http://`. That is a knowing trade — these are public broadcast streams,
not authenticated traffic.
