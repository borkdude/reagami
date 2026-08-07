# 0005: A removed property is set to nil

Date: 2026-08-06

Status: Accepted

## Context

`patch` removes a property that the new render no longer carries by writing nil.

```clojure
(when-not (js-in o new-props) (set-prop! old o nil))
```

Seven of the nine members of `properties` read nil as the value a fresh element
of that tag reports, so the write resets them by accident.

```
input.value          ""      input.disabled   false
input.checked        false   option.selected  false
input.indeterminate  false   video.muted      false
progress.value       0       div.innerHTML    ""
li.value             0
```

`volume` and `playbackRate` do not. A media element starts both at 1 and nil
reads as 0, so removing `:volume` mutes the element.

## Experiment

Two fixes, both passing a test that removes every member of `properties` and
compares against a fresh element of that tag.

Read the default off a probe element of the same tag, cached per tag and per
property. Costs 113 bytes gzip. It also covers a default that varies per tag,
which no current member needs, because nil already reads as `""` on an input and
0 on a progress.

Carry a table of the two exceptions. Costs 51 bytes gzip.

Neither sits on the hot path. The lookup runs only when a property present on the
last render is absent on this one. A workload that drops a property from 500
elements on every render measured 1.004 against main.

Other libraries:

| | volume set as | on removal |
|---|---|---|
| Vue 3.5 | property, from `key in el` | a number reads as 0 |
| React 18 | attribute, the prop is unknown | `removeAttribute` |
| Replicant | attribute, not one of its four properties | `removeAttribute` |

Vue lands on the same 0. React and Replicant never set the property, so
`:volume` does nothing there at all.

## Decision

Keep nil. Do not carry reset values.

The correct reset costs code for a case that React and Replicant do not serve at
all and Vue serves the same way. An application that needs a media property back
at 1 can render that value instead of dropping the key.

## Cost

- Removing `:volume` or `:playbackRate` leaves 0, where the element starts at 1.
- The other seven members are unaffected. They read nil as their own default.

## References

- Issue #55
- Branch `fix/property-removal-defaults` holds the table version and the test
