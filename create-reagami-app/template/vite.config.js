import { defineConfig } from 'vite'
import squint from 'squint-cljs/vite'

// Compiles the cljs, hot-reloads changed modules without a page reload, and
// runs an nREPL server that evaluates in the live page.
export default defineConfig({
  plugins: [squint()]
})
