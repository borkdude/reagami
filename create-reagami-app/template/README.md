# reagami-app

Resolving the reagami dependency needs the `clojure` CLI on the PATH.

```shell
npm install
npm run dev
```

Open http://localhost:5173.

Vite runs the `squint-cljs/vite` plugin. The plugin compiles the CLJS and
hot-swaps changed modules without a page reload. It also starts an nREPL
server on port 1339 that evaluates in the live page.
