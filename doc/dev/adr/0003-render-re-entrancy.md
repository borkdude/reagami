# 0003: Render re-entrancy is unguarded

Date: 2026-07-28

Status: Open. Deferred, not blocked.

## Context

`patch-keyed` starts by taking a snapshot of the parent's children:

```clojure
(let [old-nodes (js/Array.from (.-childNodes parent))
```

and works from that array for the rest of the pass. Anything that mutates the
DOM while the pass is running makes the snapshot stale.

A render cannot call itself directly, but the DOM can do it for you. Several
mutations fire handlers synchronously:

- removing a focused element fires `blur`
- `.focus()` on an element inside a scroll container fires `scroll`
- removing a custom element runs its `disconnectedCallback`

A handler that changes state then starts a second render inside the first.

The ssr-live example hit this by committing an edit on Enter: the commit
re-rendered, the re-render removed the focused input, and the resulting `blur`
committed again.

## Failure modes

Two, depending on where the stale snapshot lands.

A crash, in a browser:

```
Uncaught NotFoundError: Failed to execute 'removeChild' on 'Node':
The node to be removed is no longer a child of this node.
Perhaps it was moved in a 'blur' event handler?
    at patch_keyed (core.mjs:613:3)
```

Or, worse, no error at all. Rendering from a `disconnectedCallback` while patch
is mid-pass:

```
before: <div><re-entrant-el></re-entrant-el><span>b</span><span>c</span></div>
outer render asks for:  b2 / c2
nested render asks for: B! / C!
result: <div><span>B!</span><span>C!</span></div>     no throw
```

The outer render's work is discarded and nothing reports it.

## Reproduction

jsdom does not fire `blur` when a focused element is removed, and does no
layout, so `.focus()` never scrolls. Custom elements do work: define one whose
`disconnectedCallback` renders the same root, render it into a keyed list, then
render again without it. `HTMLElement` and `customElements` have to be copied
onto `globalThis` from the jsdom window first, as `install-jsdom` does for
`Node` and `Element`.

## Prior art

How other libraries handle a render arriving while one is running.

| | scheduling | on re-entrancy |
|---|---|---|
| Replicant | synchronous by default | save the latest hiccup, re-render on `requestAnimationFrame`, dev assert |
| Vue 3 | every job on a microtask | job runs in the same flush, dev recursion limit of 100 |
| Preact | every render on a microtask | queued, drained in depth order |
| React | own scheduler on `MessageChannel` | warns, does not queue |

Vue, Preact and React own state, so a render is never something you call and
scheduling everything is natural. Replicant is the only one with reagami's
shape, where `render` is a function the application calls. It patches
immediately when nothing is in flight, and only the re-entrant call is deferred.

Replicant, `src/replicant/dom.cljs`:

```clojure
(if rendering?
  (do (asserts/assert-no-nested-renders)
      (vswap! state assoc-in [el :queued] hiccup))
  (do ...reconcile...
      (when-let [pending (:queued (get @state el))]
        (js/requestAnimationFrame #(render el pending))
        (vswap! state update el dissoc :queued))))
```

Its assert says why rAF: "This call will be throttled... Nested renders can
cause performance issues, or, in the worst case - unresponsive UIs." Yielding to
the browser between passes means a render that keeps triggering renders runs at
frame rate instead of hanging the tab.

## Alternatives

There is one slot, not a queue. Only the latest hiccup is worth keeping.

**A. Save and re-render immediately.** Synchronous flush right after the outer
render finishes.

- no stale frame, because no paint can happen inside one task
- the saved hiccup cannot age, so a newer render cannot be overwritten by it
- works in a background tab
- needs a counter to bound a render that keeps triggering renders, since a
  synchronous loop never yields

**B. Save and re-render on `requestAnimationFrame`.** What Replicant does.

- loop safety for free, the frame boundary yields to the browser
- paints the outer render's stale output for about 16 ms every time
- the saved hiccup is a frame old when it runs, so an ordinary render landing in
  between is overwritten by it
- does not fire in a background tab, so the pending render waits for the tab to
  be looked at

**C. Throw.** What React does. A nested render is a bug, so report it.

- the only option where the return value stays counts in every case
- two lines
- an application relying on a nested render has to defer by hand
- applications that currently limp along start crashing

**D. Defer every render to a microtask and always return a promise.**

- re-entrancy becomes impossible, microtasks do not nest
- coalesces several renders in one task into one patch
- `render` no longer updates the DOM before returning, so every call site that
  renders and then reads the DOM has to await, including this repo's tests

Measured before considering D worthwhile: a one second fling produces 59 window
requests, essentially one per frame, and only 11 of 59 pushes land in a frame
that receives another. Coalescing helps calls in the same task, and stream
pushes arrive in separate tasks, so it does not help the case this repo has.

## Recommendation

A, with a bound. It is Replicant's shape with a different flush point, chosen
for freshness and background tabs, and it costs a counter to get back the loop
safety that the frame boundary gives Replicant for free.

Both reagami and Replicant render the whole app on every state change, so this
is a different trade on the same problem rather than a different problem.

## The return value

A deferred render cannot return its counts, because it has not run. Options are
nil, a sentinel such as `{:deferred true}`, or throwing, which does not defer at
all. A sentinel costs the same as nil and says what happened, so the caller can
tell "folded into the render in flight" from "nothing happened".

reagami has no dev and production split, so a Replicant style warning would ship
to users. The sentinel carries the same signal for no bytes of message.

## Status

Deferred. Recorded so the decision does not have to be rediscovered.

## References

- Snapshot: `patch-keyed` in `src/reagami/core.cljc`
- Application guard: `render!` in `examples/ssr-live/src/client.cljs`
- Replicant: `src/replicant/dom.cljs`, `src/replicant/asserts.cljc`
- Vue: `packages/runtime-core/src/scheduler.ts`
- Preact: `src/component.js`
