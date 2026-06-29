# Benchmarks

Reagami is compared against other CLJS UI libraries using
[js-framework-benchmark](https://github.com/krausest/js-framework-benchmark), the
keyed variant. The summary charts live in the [README](../README.md#benchmarks);
this page has the full numbers, the methodology and how to run it yourself.

## Methodology

All frameworks ran on the same machine (Macbook M4 Max) with headless Chrome and
CPU throttling, 10 iterations each, reported as the median in milliseconds.

Framework versions: Reagami 0.1.37, Replicant bb72d7b, Reagent 2.0.1, Helix 0.2.2,
UIX 1.4.9. Reagent, Helix and UIX run on React 19.2. "Squint" and "CLJS" denote the
compile target.

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
| create 1k | 27.3 | 30.6 | 58.5 | 53.8 | 39.3 | **26.1** | 27.1 |
| replace 1k | **29.8** | 33.5 | 68.2 | 64.5 | 46.1 | 31.5 | 31.6 |
| update every 10th | 49.8 | 54.0 | 49.8 | 47.0 | 30.7 | 24.6 | **20.7** |
| select | 33.9 | 43.8 | 31.6 | 26.3 | 7.3 | 13.2 | **7.0** |
| swap | 46.2 | 57.0 | 54.8 | **45.8** | 98.8 | 102.0 | 95.3 |
| remove | 27.6 | 32.5 | 27.1 | 23.1 | 18.5 | 16.4 | **14.6** |
| create 10k | **294.0** | 294.9 | 453.5 | 450.3 | 448.1 | 366.1 | 381.8 |
| append 1k | 38.8 | 42.0 | 73.3 | 63.9 | 44.8 | **31.3** | 32.5 |
| clear | **9.1** | **9.1** | 17.5 | 21.2 | 31.3 | 19.8 | 18.1 |

Geometric mean across the nine operations (the ninth root of the nine medians
multiplied together), one summary number per framework, lower is better:

| framework | geomean (ms) |
|---|---|
| UIX | 32.3 |
| Helix | 36.0 |
| Reagami Squint | 38.4 |
| Reagent | 42.6 |
| Reagami CLJS | 43.0 |
| Replicant Squint | 52.0 |
| Replicant CLJS | 56.0 |

## Size

The same data-table app, compiled with production settings, gzipped:

| framework | gzip (KB) |
|---|---|
| Reagami Squint | 7.9 |
| Replicant Squint | 16.9 |
| Reagami CLJS | 28.7 |
| Replicant CLJS | 75.9 |
| UIX | 91.7 |
| Helix | 98.4 |
| Reagent | 99.5 |

These are the full benchmark app. A minimal Reagami app under Squint is smaller,
around 5 KB gzip.

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
node dist/benchmarkRunner.js --headless true --count 10 \
  --framework keyed/reagami keyed/reagami-cljs keyed/reagent keyed/helix keyed/uix \
  --benchmark 01_ 02_ 03_ 04_ 05_ 06_ 07_ 08_ 09_
```

Results land as JSON in `webdriver-ts/results/`. Use `npm run results` to render
the official report.

The `replicant-squint` entry compiles Replicant from source under Squint, so it
expects a Replicant checkout at `~/dev/replicant`. Adjust its `squint.edn` path or
clone Replicant there to include it.
