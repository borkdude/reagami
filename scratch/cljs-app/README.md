# Test with CLJS

Lite mode:

```
clj -M -m cljs.main --target browser --output-dir public/js --compile-opts '{:asset-path "js" :lite-mode true :optimizations :advanced}' --compile my-app.core
```
