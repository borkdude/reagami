export default {
  server: {
    port: 5173,
    cors: true,
    origin: 'http://localhost:5173'
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    rollupOptions: {
      input: 'out/client.mjs',
      output: { entryFileNames: 'client.js' }
    }
  }
}
