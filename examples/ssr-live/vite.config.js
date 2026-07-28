import { defineConfig } from 'vite'
import squint from 'squint-cljs/vite'

// The squint plugin compiles cljs in-process, hot-swaps modules without a page
// reload, and runs an nREPL server whose eval goes to the live page. No
// separate `squint watch`.
export default defineConfig({
  server: { port: 5173, cors: true, origin: 'http://localhost:5173' },
  plugins: [squint()],
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    rollupOptions: { input: 'out/client.mjs', output: { entryFileNames: 'client.js' } }
  }
})
