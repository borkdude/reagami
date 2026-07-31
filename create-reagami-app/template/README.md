# reagami-app

Resolving the reagami dependency needs the `clojure` CLI on the PATH.

```shell
npm install
npm run dev
```

Open http://localhost:5173.

Vite runs the `squint-cljs/vite` plugin. The plugin compiles the CLJS and
hot-reloads changed modules without a page reload. It also starts an nREPL
server on port 1339 that evaluates in the live page.

## Without the clojure CLI

The `:deps` entry in `squint.edn` needs the `clojure` CLI. To use npm instead:

1. Remove `:deps` from `squint.edn`.
2. Add reagami with `npm install reagami`.
3. In `src/app.cljs`, change the require to `["reagami" :as r]`.
