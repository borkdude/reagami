import { defineConfig } from 'vite'
import squint from 'squint-cljs/vite'
import analyzer from 'rollup-plugin-analyzer'

const { plugin: analyze } = analyzer

// Compiles the cljs, hot-reloads changed modules without a page reload, and
// runs an nREPL server that evaluates in the live page. `npm run build` prints
// a bundle size summary.
export default defineConfig({
  plugins: [squint()],
  build: {
    rollupOptions: {
      plugins: [analyze({ summaryOnly: true, limit: 15 })]
    }
  }
})
