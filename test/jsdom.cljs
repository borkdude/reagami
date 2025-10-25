(ns test.jsdom
  (:require ["jsdom" :as jsdom]))

(defonce jsdom-instance (jsdom/JSDOM. "<!doctype html><html><body></body></html>"))
(defonce window (.-window jsdom-instance))

(set! js/globalThis.window window)
(set! js/globalThis.document (.-document window))
(set! js/globalThis.Node (.-Node window))
(set! js/globalThis.Element (.-Element window))
(set! js/globalThis.MouseEvent (.-MouseEvent window))
