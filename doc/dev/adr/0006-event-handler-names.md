# 0006: A handler is on- and the event name

Date: 2026-08-07

Status: Accepted

## Context

`set-prop!` sends a key that starts with `on` down one of two paths. When the
element has a matching `on*` property it writes the property. When it does not,
`set-handler!` calls `addEventListener` with `(subs k 3)`, the key without its
first three characters.

That makes the third character a separator rather than part of the name.
`:on-item-added` listens for `item-added`, and `:on-rated` listens for `rated`
even though that name holds no hyphen.

0.2.39 lower cased the key before the property lookup, so `:onClick` reached the
`onclick` property again. The change cost 8 bytes gzip and no measurable time,
because `prop-name` keeps its answer in a cache.

## Experiment

camelCase then worked for most events and failed for two groups.

A custom event has no property, so the key goes to `addEventListener` as written.

```
:on-item-added   listens for "item-added"   fires
:onItemAdded     listens for "temAdded"      does not fire
```

`focusin` and `focusout` have no property either. Of 17 common `on*` names
checked on a plain div in Chrome, 15 are present and those two are not, so
`:onFocusIn` listens for `FocusIn`. Preact carries a special case for the same
two names.

Both failures are silent. Nothing throws and the DOM shows nothing.

## Decision

Take the lower casing back out. A handler is `on-` and the event name, and
camelCase is not accepted at all.

Partial support is worse than none here. `:onClick` working invites `:onItemAdded`
and `:onFocusIn`, which read the same and do nothing. One form that always works
is easier to hold than a form that works apart from two groups a reader cannot
guess.

## Cost

- `:onClick` does nothing, as before 0.2.39. A reader coming from React has to
  learn one rule.
- `:onclick` still reaches the property directly, because the element has it.
- 8 bytes gzip back.

## References

- `prop-name` and `set-handler!` in `src/reagami/core.cljc`
- `handler-name-test` in `test/basic_test.cljc`
