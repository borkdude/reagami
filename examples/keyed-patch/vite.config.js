import { defineConfig } from 'vite'
import squint from 'squint-cljs/vite'

export default defineConfig({
  server: { port: 5175 },
  plugins: [squint()]
})
