import { defineConfig } from 'vite'
import squint from 'squint-cljs/vite'

// Compiles cljs in process, hot-swaps changed modules without a page reload,
// and runs an nREPL server that evaluates in the live page.
export default defineConfig({
  server: { port: 5173, cors: true, origin: 'http://localhost:5173' },
  plugins: [squint()]
})
