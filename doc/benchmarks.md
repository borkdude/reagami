# Benchmarks

Reagami is compared against other CLJS UI libraries using
[js-framework-benchmark](https://github.com/krausest/js-framework-benchmark), the
keyed variant. The summary charts live in the [README](../README.md#benchmarks);
this page has the full numbers, the methodology and how to run it yourself.

## Methodology

All frameworks ran on the same machine (Macbook M4 Max) with headless Chrome and
CPU throttling, 15 iterations each, reported as the median in milliseconds.

All frameworks ran in one invocation of the benchmark runner. This matters. The
same build measured in two sessions can differ by 25% on a single operation, so
numbers from different sessions are not comparable.

Framework versions: Reagami 784aea4, Reagent 2.0.1, Helix 0.2.2, UIX 1.4.9, and
Replicant a22f871 for the CLJS entry / 4bfd556 for the Squint entry, which builds
Replicant from a local checkout. Reagent, Helix and UIX run on React 19.2.
"Squint" and "CLJS" denote the compile target.

Both Reagami entries build from a local checkout, not from npm, because the
published package trails this source.

Every entry is built the same way for its target, which matters: an entry built
with different settings is not comparable. The CLJS entries all build with
`shadow-cljs release`; the Squint entries all build with `squint compile` plus
`esbuild --bundle --minify`. An earlier version of this page reported Replicant
CLJS at 75.9 KB, which was a figwheel build with `:pretty-print true` left on in
its advanced prod config - a toolchain artifact, not Replicant's cost. That entry
now builds with shadow like the rest, which is where 41.2 comes from.

The benchmark renders one large data table. Each row is an item with a numeric id
and a label of random words. Each operation is triggered by a button click and
timed:

- create 1k / create 10k: build a table of 1,000 (or 10,000) rows from scratch.
- replace 1k: replace all 1,000 rows with newly generated ones.
- update every 10th: change the label of every 10th row in a 1,000-row table.
- select: highlight a single row.
- swap: exchange two rows far apart in a 1,000-row table (row 2 and row 999).
- remove: delete a single row.
- append 1k: add 1,000 rows to an existing 1,000.
- clear: remove all rows.

Each operation runs several times. The median is the middle value of those timings,
so a single slow run does not skew it. The best result per row is in bold.

## Performance

| benchmark (median ms) | Reagami Squint | Reagami CLJS | Replicant CLJS | Replicant Squint | Reagent | Helix | UIX |
|---|---|---|---|---|---|---|---|
| create 1k | 27.5 | 27.9 | 52.2 | 52.4 | 38.5 | **25.6** | 26.5 |
| replace 1k | **28.7** | 30.0 | 64.6 | 66.2 | 47.0 | 30.8 | 32.7 |
| update every 10th | 38.1 | 44.0 | 36.6 | 48.0 | 26.0 | 26.4 | **22.8** |
| select | 25.3 | 34.1 | 17.5 | 26.4 | **5.9** | 14.1 | 7.1 |
| swap | **33.2** | 42.1 | 37.5 | 43.3 | 96.3 | 99.7 | 91.5 |
| remove | 20.2 | 26.0 | 18.5 | 24.0 | 18.2 | 16.0 | **13.7** |
| create 10k | 295.2 | **294.3** | 439.0 | 455.2 | 459.2 | 381.9 | 398.2 |
| append 1k | 38.9 | 40.5 | 68.9 | 65.8 | 46.4 | 35.2 | **33.9** |
| clear | 10.3 | **10.0** | 20.0 | 20.6 | 30.8 | 19.3 | 18.3 |

Geometric mean across the nine operations (the ninth root of the nine medians
multiplied together), one summary number per framework, lower is better:

| framework | geomean (ms) |
|---|---|
| UIX | 32.8 |
| Reagami Squint | 34.0 |
| Helix | 36.7 |
| Reagami CLJS | 38.0 |
| Reagent | 40.9 |
| Replicant CLJS | 45.9 |
| Replicant Squint | 52.1 |

```mermaid
---
config:
  xyChart:
    width: 850
    height: 480
  themeVariables:
    xyChart:
      plotColorPalette: "#ff7f0e, #4c78a8"
---
xychart-beta
    title "Perf: geomean of 9 keyed ops (ms, lower is better)"
    x-axis ["UIX", "Reagami Squint", "Helix", "Reagami CLJS", "Reagent", "Replicant CLJS", "Replicant Squint"]
    y-axis "ms" 0 --> 60
    bar [-5, 34.0, -5, 38.0, -5, -5, -5]
    bar [32.8, -5, 36.7, -5, 40.9, 45.9, 52.1]
```

### Patching without reading the DOM

Reagami keeps the DOM node on the vnode and diffs against the previous child
vnodes instead of walking `childNodes`. Position and reuse come from array
indexes rather than from a map and a set keyed by DOM node.

The previous patcher ran in the same invocation as an extra entry, so these two
columns are directly comparable.

| benchmark (median ms) | before | after |
|---|---|---|
| create 1k | 27.6 | 27.5 |
| replace 1k | 29.7 | 28.7 |
| update every 10th | 50.5 | 38.1 |
| select | 27.0 | 25.3 |
| swap | 37.4 | 33.2 |
| remove | 21.4 | 20.2 |
| create 10k | 288.2 | 295.2 |
| append 1k | 39.0 | 38.9 |
| clear | 9.8 | 10.3 |
| geomean | 35.9 | 34.0 |

The gain is on the patching operations. Creating nodes is unchanged.

A copy of the unchanged build ran as a control in an earlier run. It measured
within 4% of the original on every operation. That is the noise floor of this
setup, so treat a difference of less than 5% on one operation as noise.

## Size

The same data-table app, compiled with production settings, gzipped. The Squint
figure is on squint-cljs 0.14.206. Around 1.5 KB of it is protocol dispatch
machinery that a plain-data app never uses.

| framework | gzip (KB) |
|---|---|
| Reagami Squint | 9.4 |
| Replicant Squint | 16.5 |
| Reagami CLJS | 28.5 |
| Replicant CLJS | 40.2 |
| UIX | 89.5 |
| Helix | 96.1 |
| Reagent | 97.1 |

```mermaid
---
config:
  xyChart:
    width: 850
    height: 480
  themeVariables:
    xyChart:
      plotColorPalette: "#ff7f0e, #4c78a8"
---
xychart-beta
    title "Bundle size (gzip KB, lower is better)"
    x-axis ["Reagami Squint", "Replicant Squint", "Reagami CLJS", "Replicant CLJS", "UIX", "Helix", "Reagent"]
    y-axis "KB" 0 --> 100
    bar [9.4, 0, 28.5, 0, 0, 0, 0]
    bar [0, 16.5, 0, 40.2, 89.5, 96.1, 97.1]
```

These are the full benchmark app. A minimal Reagami app under Squint is smaller,
around 5.5 KB gzip (a static hello world), rising to around 6 KB once it uses an
atom.

## Running it yourself

The framework entries live in a fork of js-framework-benchmark:
[borkdude/js-framework-benchmark](https://github.com/borkdude/js-framework-benchmark),
on the `cljs` branch.

```sh
git clone -b cljs https://github.com/borkdude/js-framework-benchmark
cd js-framework-benchmark
npm ci
npm run install-local            # installs the server + webdriver-ts driver
```

Build the frameworks you want to measure. Each entry has a `build-prod` script:

```sh
cd frameworks/keyed/reagami       && npm install && npm run build-prod
cd ../reagami-cljs                && npm install && npm run build-prod
cd ../reagent                     && npm install && npm run build-prod
cd ../helix                       && npm install && npm run build-prod
cd ../uix                         && npm install && npm run build-prod
```

The Squint and CLJS entries need a JVM with the Clojure CLI and `clojure`/`squint`
on the path. The React entries (Reagent, Helix, UIX) need only npm.

Then start the server and run the driver:

```sh
cd server && npm start            # serves on http://localhost:8080, leave running
# in another shell:
cd webdriver-ts
node dist/benchmarkRunner.js --headless true --count 15 \
  --framework keyed/reagami keyed/reagami-cljs keyed/replicant \
  keyed/replicant-squint keyed/reagent keyed/helix keyed/uix \
  --benchmark 01_ 02_ 03_ 04_ 05_ 06_ 07_ 08_ 09_
```

Give all frameworks to one invocation, as shown above. Two invocations give
numbers that you cannot compare.

Results land as JSON in `webdriver-ts/results/`. Use `npm run results` to render
the official report.

The `replicant-squint` entry compiles Replicant from source under Squint, so it
expects a Replicant checkout at `~/dev/replicant`. Adjust its `squint.edn` path or
clone Replicant there to include it.
