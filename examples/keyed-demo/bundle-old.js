(() => {
  // ../../node_modules/.pnpm/squint-cljs@0.14.206/node_modules/squint-cljs/src/squint/core.js
  function __toFn(x) {
    if (x == null || typeof x === "function") return x;
    if (typeof x === "string") return (coll, d) => get(coll, x, d);
    switch (typeConst(x)) {
      case MAP_TYPE:
      case ARRAY_TYPE:
      case OBJECT_TYPE:
      case SET_TYPE:
        return (k, d) => get(x, k, d);
      case INSTANCE_TYPE:
        if (x[ILookup__lookup] !== void 0) return (k, d) => get(x, k, d);
    }
    return x;
  }
  function isMapLike(m) {
    return m != null && typeof m === "object" && (m.constructor === Object || m instanceof Map || m[TYPE_TAG] === MAP_TYPE);
  }
  function validateArrayKeys(o, k, kvs) {
    let len = o.length;
    for (let i = 0; i < kvs.length + 2; i += 2) {
      const key = i === 0 ? k : kvs[i - 2];
      if (!Number.isInteger(key)) {
        throw new Error("Vector's key for assoc must be a number.");
      }
      if (key < 0 || key > len) {
        throw new Error(`Index ${key} out of bounds [0,${len}]`);
      }
      if (key === len) len++;
    }
  }
  function assoc_BANG_(m, k, v, ...kvs) {
    if (arguments.length < 3 || kvs.length % 2 !== 0) {
      throw new Error("Illegal argument: assoc expects an odd number of arguments.");
    }
    switch (typeConst(m)) {
      case MAP_TYPE:
        m.set(k, v);
        for (let i = 0; i < kvs.length; i += 2) {
          m.set(kvs[i], kvs[i + 1]);
        }
        break;
      case ARRAY_TYPE:
        validateArrayKeys(m, k, kvs);
        m[k] = v;
        for (let i = 0; i < kvs.length; i += 2) {
          m[kvs[i]] = kvs[i + 1];
        }
        break;
      case INSTANCE_TYPE:
        if (m[ITransientAssociative__assoc_BANG_] !== void 0) {
          let ret = m[ITransientAssociative__assoc_BANG_](m, k, v);
          for (let i = 0; i < kvs.length; i += 2) {
            ret = ret[ITransientAssociative__assoc_BANG_](ret, kvs[i], kvs[i + 1]);
          }
          return ret;
        }
      // fall through: an instance without -assoc! keeps the object behavior
      case OBJECT_TYPE:
        m[k] = v;
        for (let i = 0; i < kvs.length; i += 2) {
          m[kvs[i]] = kvs[i + 1];
        }
        break;
      default:
        throw new Error(
          `Illegal argument: assoc! expects a Map, Array, or Object as the first argument, but got ${typeof m}.`
        );
    }
    return m;
  }
  function copyMeta(from, to) {
    const f = from?.[IMeta__meta];
    if (f !== void 0) {
      to[IMeta__meta] = f;
      to[IWithMeta__with_meta] = from[IWithMeta__with_meta];
    }
    return to;
  }
  function copy(o) {
    switch (typeConst(o)) {
      case MAP_TYPE:
        return copyMeta(o, new o.constructor(o));
      case SET_TYPE:
        return copyMeta(o, new o.constructor(o));
      case ARRAY_TYPE:
        return copyMeta(o, [...o]);
      case INSTANCE_TYPE:
      case OBJECT_TYPE:
        return copyMeta(o, { ...o });
      case LIST_TYPE:
        return copyMeta(o, new List(...o));
      default:
        throw new Error(`Don't know how to copy object of type ${typeof o}.`);
    }
  }
  function assoc(o, k, v, ...kvs) {
    if (arguments.length < 3 || kvs.length % 2 !== 0) {
      throw new Error("Illegal argument: assoc expects an odd number of arguments.");
    }
    if (o == null) {
      o = {};
    }
    if (!isObj(o) && !Array.isArray(o) && o[IAssociative__assoc] !== void 0) {
      let ret2 = o[IAssociative__assoc](o, k, v);
      for (let i = 0; i < kvs.length; i += 2) {
        ret2 = ret2[IAssociative__assoc](ret2, kvs[i], kvs[i + 1]);
      }
      return ret2;
    }
    const ret = copy(o);
    assoc_BANG_(ret, k, v, ...kvs);
    return ret;
  }
  var MAP_TYPE = 1;
  var ARRAY_TYPE = 2;
  var OBJECT_TYPE = 3;
  var LIST_TYPE = 4;
  var SET_TYPE = 5;
  var LAZY_ITERABLE_TYPE = 6;
  var INSTANCE_TYPE = 7;
  var TYPE_TAG = /* @__PURE__ */ Symbol("squint.lang.type");
  var SORTED_TAG = /* @__PURE__ */ Symbol("squint.lang.sorted");
  // @__NO_SIDE_EFFECTS__
  function defclass(c) {
    return c;
  }
  function isObj(coll) {
    return coll.constructor === Object;
  }
  function isVectorArray(x) {
    return Array.isArray(x) && x[TYPE_TAG] !== LIST_TYPE;
  }
  function object_QMARK_(coll) {
    return coll != null && isObj(coll);
  }
  function typeConst(obj) {
    if (obj == null) {
      return void 0;
    }
    if (isObj(obj)) {
      return OBJECT_TYPE;
    }
    if (obj instanceof Map) return MAP_TYPE;
    if (obj instanceof Set) return SET_TYPE;
    const tag = obj[TYPE_TAG];
    if (tag !== void 0) return tag;
    if (isVectorArray(obj)) return ARRAY_TYPE;
    if (typeof obj === "object") return INSTANCE_TYPE;
    return void 0;
  }
  function conj_BANG_set(o, rest2) {
    for (const x of rest2) {
      o.add(x);
    }
    return o;
  }
  function conj_BANG_(...xs) {
    const n = xs.length;
    if (n === 0) {
      return vector();
    }
    if (n === 1) {
      return xs[0];
    }
    let o = xs[0];
    if (o === null || o === void 0) {
      o = [];
    }
    if (n === 2) {
      switch (typeConst(o)) {
        case ARRAY_TYPE:
          o.push(xs[1]);
          return o;
        case SET_TYPE:
          o.add(xs[1]);
          return o;
      }
    }
    const rest2 = xs.slice(1);
    switch (typeConst(o)) {
      case SET_TYPE:
        conj_BANG_set(o, rest2);
        break;
      case LIST_TYPE:
        o.unshift(...rest2.reverse());
        break;
      case ARRAY_TYPE:
        o.push(...rest2);
        break;
      case MAP_TYPE:
        for (const x of rest2) {
          if (isVectorArray(x)) {
            asMapEntry(x);
            o.set(x[0], x[1]);
          } else for (const kv of mapEntriesOf(x)) o.set(kv[0], kv[1]);
        }
        break;
      case INSTANCE_TYPE:
        if (o[ITransientCollection__conj_BANG_] !== void 0) {
          let acc = o[ITransientCollection__conj_BANG_](o, rest2[0]);
          for (let i = 1; i < rest2.length; i++) acc = conj_BANG_(acc, rest2[i]);
          return acc;
        }
      // fall through: an instance without -conj! keeps the object behavior
      case OBJECT_TYPE:
        for (const x of rest2) {
          if (isVectorArray(x)) {
            asMapEntry(x);
            o[x[0]] = x[1];
          } else for (const kv of mapEntriesOf(x)) o[kv[0]] = kv[1];
        }
        break;
      default:
        throw new Error(
          "Illegal argument: conj! expects a Set, Array, List, Map, or Object as the first argument."
        );
    }
    return o;
  }
  function* mapEntriesOf(x) {
    if (isMapLike(x)) {
      yield* iterable(x);
      return;
    }
    for (const kv of iterable(x)) {
      if (!isVectorArray(kv)) {
        throw new Error("conj on a map takes map entries or seqables of map entries");
      }
      yield kv;
    }
  }
  function asMapEntry(x) {
    if (x.length < 2) {
      throw new Error("Vector arg to map conj must be a pair");
    }
    return x;
  }
  function conj(...xs) {
    if (xs.length === 0) {
      return vector();
    }
    const [_o, ...rest2] = xs;
    if (rest2.length === 0) return _o;
    let o = _o;
    if (o === null || o === void 0) {
      o = list();
    }
    let m, o2;
    switch (typeConst(o)) {
      case SET_TYPE:
        if (o[SORTED_TAG]) {
          return copyMeta(o, conj_BANG_set(new o.constructor(o), rest2));
        } else {
          return copyMeta(o, new o.constructor([...o, ...rest2]));
        }
      case LIST_TYPE:
        return copyMeta(o, new List(...rest2.reverse(), ...o));
      case ARRAY_TYPE:
        return copyMeta(o, [...o, ...rest2]);
      case MAP_TYPE:
        m = new Map(o);
        for (const x of rest2) {
          if (isVectorArray(x)) {
            asMapEntry(x);
            m.set(x[0], x[1]);
          } else for (const kv of mapEntriesOf(x)) m.set(kv[0], kv[1]);
        }
        return copyMeta(o, m);
      case LAZY_ITERABLE_TYPE:
        return lazy(function* () {
          yield* rest2;
          yield* o;
        });
      case INSTANCE_TYPE:
        if (o[ICollection__conj] !== void 0) {
          o2 = o[ICollection__conj](o, rest2[0]);
          for (let i = 1; i < rest2.length; i++) o2 = conj(o2, rest2[i]);
          return o2;
        }
      // fall through: an instance without -conj keeps the object behavior
      case OBJECT_TYPE:
        o2 = { ...o };
        for (const x of rest2) {
          if (isVectorArray(x)) {
            asMapEntry(x);
            o2[x[0]] = x[1];
          } else for (const kv of mapEntriesOf(x)) o2[kv[0]] = kv[1];
        }
        return copyMeta(o, o2);
      default:
        throw new Error(
          "Illegal argument: conj expects a Set, Array, List, Map, or Object as the first argument."
        );
    }
  }
  function disj_BANG_(s, ...xs) {
    if (s != null && s[ITransientSet__disjoin_BANG_] !== void 0) {
      let ret = s;
      for (const x of xs) {
        ret = ret != null && ret[ITransientSet__disjoin_BANG_] !== void 0 ? ret[ITransientSet__disjoin_BANG_](ret, x) : disj_BANG_(ret, x);
      }
      return ret;
    }
    for (const x of xs) {
      s.delete(x);
    }
    return s;
  }
  function disj(s, ...xs) {
    if (s == null) return s;
    if (xs.length === 0) return s;
    if (s[ISet__disjoin] !== void 0) {
      let ret = s[ISet__disjoin](s, xs[0]);
      for (let i = 1; i < xs.length; i++) ret = disj(ret, xs[i]);
      return ret;
    }
    const s1 = new s.constructor(s);
    return copyMeta(s, disj_BANG_(s1, ...xs));
  }
  function inc(n) {
    return n + 1;
  }
  function nth(coll, idx, orElse) {
    if (typeof idx !== "number") {
      throw new Error("Index argument to nth must be a number");
    }
    const hasDefault = arguments.length > 2;
    if (coll == null) return hasDefault ? orElse : null;
    if (Array.isArray(coll)) {
      if (idx >= 0 && idx < coll.length) {
        return coll[idx];
      }
    } else if (coll[IIndexed__nth] !== void 0) {
      return hasDefault ? coll[IIndexed__nth](coll, idx, orElse) : coll[IIndexed__nth](coll, idx);
    } else if (idx >= 0) {
      const next2 = chunkCursor(coll);
      let base = 0;
      let ch;
      while ((ch = next2()) !== null) {
        if (idx < base + ch.length) return ch[idx - base];
        base += ch.length;
      }
    }
    if (hasDefault) return orElse;
    throw new Error("Index out of bounds: " + idx);
  }
  function get(coll, key, otherwise = void 0) {
    if (coll == null) {
      return otherwise;
    }
    let v;
    if (isObj(coll)) {
      v = coll[key];
      if (v === void 0) {
        return otherwise;
      } else {
        return v;
      }
    }
    let g;
    switch (typeConst(coll)) {
      case SET_TYPE:
        if (coll.has(key)) v = key;
        break;
      case MAP_TYPE:
        v = coll.get(key);
        break;
      case ARRAY_TYPE:
        v = coll[key];
        break;
      default:
        if (coll[ILookup__lookup] !== void 0) {
          v = coll[ILookup__lookup](coll, key, otherwise);
          return v === void 0 ? otherwise : v;
        }
        g = coll["get"];
        if (typeof g === "function") {
          try {
            v = coll.get(key);
            break;
          } catch (e) {
          }
        }
        v = coll[key];
        break;
    }
    return v !== void 0 ? v : otherwise;
  }
  function seq_QMARK_(x) {
    return x != null && !!x[Symbol.iterator];
  }
  function seqable_QMARK_(x) {
    return x === null || x === void 0 || // plain objects (squint maps) are seqable via Object.entries in `iterable`,
    // even though they lack Symbol.iterator.
    object_QMARK_(x) || // we used to check instanceof Object but this returns false for TC39 Records
    // also we used to write `Symbol.iterator in` but this does not work for strings and some other types
    !!x[Symbol.iterator] || !!x[ISEQABLE_SYM];
  }
  var MAP_ENTRY = /* @__PURE__ */ Symbol("squint.lang.map-entry");
  function tagMapEntry(e) {
    e[MAP_ENTRY] = true;
    return e;
  }
  function iterable(x) {
    if (x === null || x === void 0) {
      return [];
    }
    if (x[Symbol.iterator]) {
      return x;
    }
    if (x[ISeqable__seq] !== void 0) return iterable(x[ISeqable__seq](x));
    if (isObj(x)) return Object.entries(x).map(tagMapEntry);
    throw new TypeError(`${x} is not iterable`);
  }
  var IIterable = /* @__PURE__ */ Symbol("Iterable");
  function _iterator(coll) {
    return coll[Symbol.iterator]();
  }
  var es6_iterator = _iterator;
  function seq(x) {
    if (x == null) return x;
    if (!seqable_QMARK_(x)) throw new TypeError(x + " is not ISeqable");
    if (typeof x === "string") return x.length ? [...x] : null;
    const iter = iterable(x);
    if (iter.length === 0 || iter.size === 0) {
      return null;
    }
    if (iter instanceof Set || iter[TYPE_TAG] === SET_TYPE) {
      return [...iter];
    }
    if (iter instanceof Map || iter[TYPE_TAG] === MAP_TYPE) {
      return [...iter].map(tagMapEntry);
    }
    if (iter[IMap__dissoc] !== void 0) {
      const entries = [...iter].map(tagMapEntry);
      return entries.length === 0 ? null : entries;
    }
    const _i = iter[Symbol.iterator]();
    if (_i.next().done) return null;
    return iter;
  }
  function first(coll) {
    if (coll == null) return void 0;
    if (Array.isArray(coll)) return coll[0];
    if (coll instanceof LazyIterable) {
      coll.force();
      return coll.chunk === null ? void 0 : coll.chunk[0];
    }
    const [first2] = iterable(coll);
    return first2;
  }
  function rest(coll) {
    const cell = chunkCells(coll);
    cell.force();
    const ch = cell.chunk;
    if (ch === null) return cell;
    if (ch.length > 1) {
      const c = new LazyIterable(null);
      c.realized = true;
      c.chunk = ch.slice(1);
      c._rest = cell._rest;
      return c;
    }
    return cell._rest;
  }
  var REDUCED_DEREF = (self) => self.value;
  var Reduced = class {
    value;
    constructor(x) {
      this.value = x;
      this[IDeref__deref] = REDUCED_DEREF;
    }
  };
  function reduce(f, arg1, arg2) {
    f = __toFn(f);
    const hasInit = arguments.length !== 2;
    const coll = hasInit ? arg2 : arg1;
    let val = hasInit ? arg1 : void 0;
    if (Array.isArray(coll)) {
      let i2 = 0;
      if (!hasInit) {
        if (coll.length === 0) return f();
        val = coll[0];
        i2 = 1;
      }
      if (val instanceof Reduced) return val.value;
      for (; i2 < coll.length; i2++) {
        val = f(val, coll[i2]);
        if (val instanceof Reduced) return val.value;
      }
      return val;
    }
    const next2 = chunkCursor(coll);
    let ch = next2();
    let i = 0;
    if (!hasInit) {
      if (ch === null) return f();
      val = ch[0];
      i = 1;
    }
    if (val instanceof Reduced) return val.value;
    while (ch !== null) {
      for (; i < ch.length; i++) {
        val = f(val, ch[i]);
        if (val instanceof Reduced) return val.value;
      }
      ch = next2();
      i = 0;
    }
    return val;
  }
  var CHUNK_SIZE = 32;
  var LazyIterable = /* @__PURE__ */ defclass(
    class LazyIterable2 {
      constructor(step) {
        this[TYPE_TAG] = LAZY_ITERABLE_TYPE;
        this[IIterable] = true;
        this.step = step;
        this.realized = false;
        this.chunk = null;
        this._rest = null;
      }
      force() {
        if (!this.realized) {
          this.realized = true;
          const r = this.step();
          this.step = null;
          if (r !== null && r !== void 0) {
            this.chunk = r[0];
            this._rest = new LazyIterable2(r[1]);
          }
        }
        return this;
      }
      [Symbol.iterator]() {
        let cell = this;
        let i = 0;
        return {
          next() {
            for (; ; ) {
              cell.force();
              const ch = cell.chunk;
              if (ch === null) return { value: void 0, done: true };
              if (i < ch.length) return { value: ch[i++], done: false };
              cell = cell._rest;
              i = 0;
            }
          },
          [Symbol.iterator]() {
            return this;
          }
        };
      }
      // Mirrors Array.prototype.indexOf so lazy seqs support (.indexOf coll x):
      // reference equality, returns -1 when absent. Unlike cljs.core, not by value.
      indexOf(x, fromIndex = 0) {
        let i = 0;
        for (const v of this) {
          if (i >= fromIndex && v === x) return i;
          i++;
        }
        return -1;
      }
    }
  );
  function unchunkedSteps(iter) {
    const step = () => {
      const r = iter.next();
      return r.done ? null : [[r.value], step];
    };
    return step;
  }
  function lazy(f) {
    return new LazyIterable(unchunkedSteps(f()));
  }
  function chunkCells(coll) {
    if (coll instanceof LazyIterable) return coll;
    if (Array.isArray(coll)) {
      const step = (pos) => () => {
        if (pos >= coll.length) return null;
        const end = Math.min(pos + CHUNK_SIZE, coll.length);
        return [coll.slice(pos, end), step(end)];
      };
      return new LazyIterable(step(0));
    }
    return new LazyIterable(unchunkedSteps(es6_iterator(iterable(coll))));
  }
  function chunkCursor(coll) {
    if (coll instanceof LazyIterable) {
      let cell = coll;
      return () => {
        if (cell === null) return null;
        cell.force();
        const ch = cell.chunk;
        cell = ch === null ? null : cell._rest;
        return ch;
      };
    }
    const it = es6_iterator(iterable(coll));
    return () => {
      const b = [];
      for (let i = 0; i < CHUNK_SIZE; i++) {
        const r = it.next();
        if (r.done) break;
        b.push(r.value);
      }
      return b.length === 0 ? null : b;
    };
  }
  function mapChunks(coll, xf) {
    const src = chunkCells(coll);
    const step = (cell, base) => () => {
      let c = cell;
      let b = base;
      for (; ; ) {
        c.force();
        const ch = c.chunk;
        if (ch === null) return null;
        const out = xf(ch, b);
        const rest2 = c._rest;
        b += ch.length;
        if (out.length !== 0) return [out, step(rest2, b)];
        c = rest2;
      }
    };
    return new LazyIterable(step(src, 0));
  }
  function map(f, ...colls) {
    f = __toFn(f);
    switch (colls.length) {
      case 0:
        return (rf) => {
          return (...args) => {
            switch (args.length) {
              case 0: {
                return rf();
              }
              case 1: {
                return rf(args[0]);
              }
              case 2: {
                return rf(args[0], f(args[1]));
              }
              default: {
                return rf(args[0], f(...args.slice(1)));
              }
            }
          };
        };
      case 1:
        return mapChunks(colls[0], (ch) => {
          const out = new Array(ch.length);
          for (let i = 0; i < ch.length; i++) out[i] = f(ch[i]);
          return out;
        });
      default: {
        const iters = colls.map((coll) => es6_iterator(iterable(coll)));
        return lazy(function* () {
          while (true) {
            const args = [];
            for (const i of iters) {
              const nextVal = i.next();
              if (nextVal.done) {
                return;
              }
              args.push(nextVal.value);
            }
            yield f(...args);
          }
        });
      }
    }
  }
  function not(expr) {
    return !truth_(expr);
  }
  var IATOM_SYM = /* @__PURE__ */ Symbol("squint.core.IAtom");
  var IDEREF_SYM = /* @__PURE__ */ Symbol("squint.core.IDeref");
  var ISEQABLE_SYM = /* @__PURE__ */ Symbol("squint.core.ISeqable");
  var IDeref__deref = /* @__PURE__ */ Symbol("IDeref_-deref");
  function _deref(o) {
    if (o != null && o[IDeref__deref] !== void 0) return o[IDeref__deref](o);
    return nilImpl(_deref, "IDeref.-deref", o)(o);
  }
  var ISeqable__seq = /* @__PURE__ */ Symbol("ISeqable_-seq");
  var ILookup__lookup = /* @__PURE__ */ Symbol("ILookup_-lookup");
  var IAssociative__assoc = /* @__PURE__ */ Symbol("IAssociative_-assoc");
  var IMap = { __sym: /* @__PURE__ */ Symbol("squint.core.IMap") };
  var IMap__dissoc = /* @__PURE__ */ Symbol("IMap_-dissoc");
  var ICounted__count = /* @__PURE__ */ Symbol("ICounted_-count");
  var ICollection__conj = /* @__PURE__ */ Symbol("ICollection_-conj");
  var IEmptyableCollection__empty = /* @__PURE__ */ Symbol("IEmptyableCollection_-empty");
  var ISet__disjoin = /* @__PURE__ */ Symbol("ISet_-disjoin");
  var ITransientCollection__conj_BANG_ = /* @__PURE__ */ Symbol("ITransientCollection_-conj!");
  var ITransientAssociative__assoc_BANG_ = /* @__PURE__ */ Symbol("ITransientAssociative_-assoc!");
  var ITransientSet__disjoin_BANG_ = /* @__PURE__ */ Symbol("ITransientSet_-disjoin!");
  var IMeta__meta = /* @__PURE__ */ Symbol("IMeta_-meta");
  var IWithMeta__with_meta = /* @__PURE__ */ Symbol("IWithMeta_-with-meta");
  var M3_C1 = 3432918353 | 0;
  var M3_C2 = 461845907 | 0;
  var IIndexed__nth = /* @__PURE__ */ Symbol("IIndexed_-nth");
  var IVector = { __sym: /* @__PURE__ */ Symbol("squint.core.IVector") };
  function _count(o) {
    if (o != null && o[ICounted__count] !== void 0) return o[ICounted__count](o);
    return nilImpl(_count, "ICounted.-count", o)(o);
  }
  function nilImpl(dispatchFn, protoMethod, o) {
    const f = dispatchFn[null];
    if (f === void 0) throw missing_protocol(protoMethod, o);
    return f;
  }
  var IReset = { __sym: /* @__PURE__ */ Symbol("squint.core.IReset") };
  var IReset__reset_BANG_ = /* @__PURE__ */ Symbol("IReset_-reset!");
  function _reset_BANG_(o, v) {
    if (o != null && o[IReset__reset_BANG_] !== void 0) return o[IReset__reset_BANG_](o, v);
    return nilImpl(_reset_BANG_, "IReset.-reset!", o)(o, v);
  }
  var ISwap = { __sym: /* @__PURE__ */ Symbol("squint.core.ISwap") };
  var ISwap__swap_BANG_ = /* @__PURE__ */ Symbol("ISwap_-swap!");
  var IWatchable = { __sym: /* @__PURE__ */ Symbol("squint.core.IWatchable") };
  var IWatchable__add_watch = /* @__PURE__ */ Symbol("IWatchable_-add-watch");
  var IWatchable__remove_watch = /* @__PURE__ */ Symbol("IWatchable_-remove-watch");
  var IWatchable__notify_watches = /* @__PURE__ */ Symbol("IWatchable_-notify-watches");
  var ATOM_DEREF = (self) => self.val;
  var ATOM_RESET = (self, x) => {
    if (self._validator && !truth_(self._validator(x))) {
      throw new Error("Validator rejected reference state");
    }
    const old_val = self.val;
    self.val = x;
    if (self._hasWatches) {
      for (const [k, f] of Object.entries(self._watches)) f(k, self, old_val, x);
    }
    return x;
  };
  var ATOM_SWAP = function(self, f, a, b, xs) {
    switch (arguments.length) {
      case 2:
        return ATOM_RESET(self, f(self.val));
      case 3:
        return ATOM_RESET(self, f(self.val, a));
      case 4:
        return ATOM_RESET(self, f(self.val, a, b));
      default:
        return ATOM_RESET(self, f(self.val, a, b, ...xs));
    }
  };
  var ATOM_ADD_WATCH = (self, k, f) => {
    self._watches[k] = f;
    self._hasWatches = true;
  };
  var ATOM_REMOVE_WATCH = (self, k) => {
    delete self._watches[k];
  };
  var ATOM_NOTIFY = (self, oldv, newv) => {
    for (const [k, f] of Object.entries(self._watches)) f(k, self, oldv, newv);
  };
  var Atom = class {
    constructor(init) {
      this.val = init;
      this._watches = {};
      this._hasWatches = false;
      this[IATOM_SYM] = true;
      this[IDEREF_SYM] = true;
      this[IDeref__deref] = ATOM_DEREF;
      this[IReset.__sym] = true;
      this[IReset__reset_BANG_] = ATOM_RESET;
      this[ISwap.__sym] = true;
      this[ISwap__swap_BANG_] = ATOM_SWAP;
      this[IWatchable.__sym] = true;
      this[IWatchable__add_watch] = ATOM_ADD_WATCH;
      this[IWatchable__remove_watch] = ATOM_REMOVE_WATCH;
      this[IWatchable__notify_watches] = ATOM_NOTIFY;
    }
  };
  function atom(init, ...opts) {
    const a = new Atom(init);
    for (let i = 0; i < opts.length; i += 2) {
      if (opts[i] === "meta") {
        const mv = opts[i + 1];
        a[IMeta__meta] = () => mv;
      } else if (opts[i] === "validator") a._validator = opts[i + 1];
    }
    return a;
  }
  function missing_protocol(proto, obj) {
    let ty;
    if (obj === null) ty = "null";
    else if (obj === void 0) ty = "undefined";
    else if (Array.isArray(obj)) ty = "array";
    else if (typeof obj === "object" && obj.constructor && obj.constructor !== Object) {
      ty = obj.constructor.name;
    } else ty = typeof obj;
    return new Error(
      `No protocol method ${proto} defined for type ${ty}: ${obj ?? ""}`
    );
  }
  function deref(ref) {
    if (ref?.[IDeref__deref] !== void 0) return ref[IDeref__deref](ref);
    return nilImpl(_deref, "IDeref.-deref", ref)(ref);
  }
  function reset_BANG_(atm, v) {
    if (atm?.[IReset__reset_BANG_] !== void 0) return atm[IReset__reset_BANG_](atm, v);
    return nilImpl(_reset_BANG_, "IReset.-reset!", atm)(atm, v);
  }
  function swap_BANG_(atm, f, ...args) {
    f = __toFn(f);
    if (atm?.[ISwap__swap_BANG_] !== void 0) {
      switch (args.length) {
        case 0:
          return atm[ISwap__swap_BANG_](atm, f);
        case 1:
          return atm[ISwap__swap_BANG_](atm, f, args[0]);
        case 2:
          return atm[ISwap__swap_BANG_](atm, f, args[0], args[1]);
        default:
          return atm[ISwap__swap_BANG_](atm, f, args[0], args[1], args.slice(2));
      }
    }
    const v = f(deref(atm), ...args);
    reset_BANG_(atm, v);
    return v;
  }
  function range(begin, end, step) {
    let b = begin, e = end, s = step;
    if (end === void 0) {
      b = 0;
      e = begin;
    }
    const start2 = b || 0;
    s = step ?? 1;
    const ascending = s >= 0;
    const more = (i) => e === void 0 || ascending && i < e || !ascending && e < i;
    const mkStep = (from) => () => {
      if (!more(from)) return null;
      const out = [];
      let i = from;
      while (out.length < CHUNK_SIZE && more(i)) {
        out.push(i);
        i += s;
      }
      return [out, mkStep(i)];
    };
    return new LazyIterable(mkStep(start2));
  }
  function subvec(arr, start2, end) {
    if (arr != null && arr[IVector.__sym] !== void 0) {
      if (end === void 0) end = _count(arr);
      if (start2 == null || end == null) {
        throw new Error("subvec: start and end must not be nil");
      }
      start2 = start2 | 0;
      end = end | 0;
      if (start2 < 0 || end < start2 || end > _count(arr)) {
        throw new Error("subvec: index out of bounds");
      }
      let ret = arr[IEmptyableCollection__empty](arr);
      for (let i = start2; i < end; i++) ret = ret[ICollection__conj](ret, arr[IIndexed__nth](arr, i));
      return ret;
    }
    if (!isVectorArray(arr)) {
      throw new Error("subvec: argument must be a vector");
    }
    if (end === void 0) end = arr.length;
    if (start2 == null || end == null) {
      throw new Error("subvec: start and end must not be nil");
    }
    start2 = start2 | 0;
    end = end | 0;
    if (start2 < 0 || end < start2 || end > arr.length) {
      throw new Error("subvec: index out of bounds");
    }
    return arr.slice(start2, end);
  }
  function vector(...args) {
    return args;
  }
  function vector_QMARK_(x) {
    if (x == null) return false;
    return isVectorArray(x) || x[IVector.__sym] !== void 0;
  }
  function mapv(...args) {
    if (args.length === 2) {
      const [_f, coll] = args;
      const f = __toFn(_f);
      const iter = iterable(coll);
      if (Array.isArray(iter)) {
        const ret = new Array(iter.length);
        for (var i = 0; i < iter.length; i++) {
          ret[i] = f(iter[i]);
        }
        return ret;
      } else {
        const ret = [];
        const next2 = chunkCursor(iter);
        let ch;
        while ((ch = next2()) !== null) {
          for (let i2 = 0; i2 < ch.length; i2++) ret.push(f(ch[i2]));
        }
        return ret;
      }
    }
    return [...map(...args)];
  }
  function pushAll(out, from) {
    if (from instanceof LazyIterable) {
      let cell = from;
      for (; ; ) {
        cell.force();
        const ch = cell.chunk;
        if (ch === null) return out;
        Array.prototype.push.apply(out, ch);
        cell = cell._rest;
      }
    }
    for (const x of iterable(from)) out.push(x);
    return out;
  }
  function toArray(coll) {
    if (coll instanceof LazyIterable) return pushAll([], coll);
    return [...iterable(coll)];
  }
  function vec(x) {
    if (isVectorArray(x)) return x;
    if (x != null && x[IVector.__sym] !== void 0) return x;
    return pushAll([], x);
  }
  function apply(f, ...args) {
    f = __toFn(f);
    const xs = args.slice(0, args.length - 1);
    const last = args[args.length - 1];
    const v = f.squint$lang$variadic;
    if (v) {
      const maxfa = v.length - 1;
      const fixed = [];
      let i = 0, rest2;
      for (; i < maxfa && i < xs.length; i++) fixed.push(xs[i]);
      if (i < maxfa) {
        let s = seq(last);
        for (; i < maxfa && s != null; i++) {
          fixed.push(first(s));
          s = next(s);
        }
        rest2 = s;
      } else {
        rest2 = i < xs.length ? concat1([xs.slice(i), last]) : last;
      }
      rest2 = rest2 == null ? null : seq(rest2);
      if (rest2 == null) return f(...fixed);
      return v(...fixed, rest2);
    }
    return f(...xs, ...iterable(last));
  }
  var List = class extends Array {
    constructor(...args) {
      super();
      this[TYPE_TAG] = LIST_TYPE;
      this.push(...args);
    }
  };
  function list(...args) {
    return new List(...args);
  }
  var CONCAT_DONE = /* @__PURE__ */ Symbol("concat-done");
  function concat1(colls) {
    const isArr = Array.isArray(colls);
    const arr = isArr ? colls.slice() : null;
    const collIter = isArr ? null : es6_iterator(iterable(colls));
    let idx = 0;
    const nextColl = () => {
      if (isArr) {
        if (idx >= arr.length) return CONCAT_DONE;
        const c = arr[idx];
        arr[idx] = null;
        idx++;
        return c;
      }
      const r = collIter.next();
      return r.done ? CONCAT_DONE : r.value;
    };
    const step = (src) => () => {
      for (; ; ) {
        if (src !== null) {
          if (src instanceof LazyIterable) {
            src.force();
            if (src.chunk !== null) return [src.chunk, step(src._rest)];
            src = null;
          } else {
            const a = src.a;
            const pos = src.pos;
            if (pos < a.length) {
              const end = Math.min(pos + CHUNK_SIZE, a.length);
              return [a.slice(pos, end), step({ a, pos: end })];
            }
            src = null;
          }
        }
        const nc = nextColl();
        if (nc === CONCAT_DONE) return null;
        src = nc instanceof LazyIterable ? nc : Array.isArray(nc) ? { a: nc, pos: 0 } : chunkCells(nc);
      }
    };
    return new LazyIterable(step(null));
  }
  function into(...args) {
    let to, xform, from, c, rf;
    switch (args.length) {
      case 0:
        return [];
      case 1:
        return args[0];
      case 2:
        to = args[0] ?? [];
        if (isVectorArray(to)) {
          return pushAll(copy(to), args[1]);
        }
        if (to[ICollection__conj] !== void 0) {
          return reduce(
            (acc, x) => acc != null && acc[ICollection__conj] !== void 0 ? acc[ICollection__conj](acc, x) : conj_BANG_(acc, x),
            to,
            args[1]
          );
        }
        return reduce(conj_BANG_, copy(to), args[1]);
      case 3:
        to = args[0];
        xform = args[1];
        from = args[2];
        c = to != null && to[ICollection__conj] !== void 0 ? to : copy(to);
        rf = (coll, v) => {
          if (v === void 0) {
            return coll;
          }
          return coll != null && coll[ICollection__conj] !== void 0 ? coll[ICollection__conj](coll, v) : conj_BANG_(coll, v);
        };
        return transduce(xform, rf, c, from);
      default:
        throw TypeError(`Invalid arity call of into: ${args.length}`);
    }
  }
  function update(coll, k, f, ...args) {
    f = __toFn(f);
    return assoc(coll, k, f(get(coll, k), ...args));
  }
  function fnil(f, ...defaults) {
    f = __toFn(f);
    const n = defaults.length;
    return function(...args) {
      for (let i = 0; i < n; i++) {
        if (args[i] == null) args[i] = defaults[i];
      }
      return f(...args);
    };
  }
  function sort(f, coll) {
    if (arguments.length === 1) {
      coll = f;
      f = void 0;
    }
    f = __toFn(f);
    const clone = toArray(coll);
    return clone.sort(f || compare);
  }
  function fnToComparator(f) {
    if (f === compare) {
      return f;
    }
    return (x, y) => {
      const r = f(x, y);
      if (number_QMARK_(r)) {
        return r;
      }
      if (r) {
        return -1;
      }
      if (f(y, x)) {
        return 1;
      }
      return 0;
    };
  }
  function sort_by(keyfn, comp, coll) {
    if (arguments.length === 2) {
      coll = comp;
      comp = compare;
    }
    keyfn = __toFn(keyfn);
    comp = __toFn(comp);
    return sort((x, y) => {
      const f = fnToComparator(comp);
      const kx = keyfn(x);
      const ky = keyfn(y);
      return f(kx, ky);
    }, coll);
  }
  function update_BANG_(m, k, f, ...args) {
    f = __toFn(f);
    const v = get(m, k);
    return assoc_BANG_(m, k, f(v, ...args));
  }
  function count(coll) {
    if (!coll) return 0;
    const len = coll.length || coll.size;
    if (typeof len === "number") {
      return len;
    }
    if (coll[ICounted__count] !== void 0) return coll[ICounted__count](coll);
    const next2 = chunkCursor(coll);
    let ret = 0;
    let ch;
    while ((ch = next2()) !== null) ret += ch.length;
    return ret;
  }
  function map_QMARK_(coll) {
    if (coll == null) return false;
    if (isObj(coll)) return true;
    if (coll instanceof Map) return true;
    if (coll[TYPE_TAG] === MAP_TYPE) return true;
    if (coll[IMap.__sym] !== void 0) return true;
    return false;
  }
  function next(x) {
    if (Array.isArray(x)) {
      const ret = x.slice(1);
      if (ret.length > 0) {
        return ret;
      } else {
        return null;
      }
    } else {
      return seq(rest(x));
    }
  }
  function compare(x, y) {
    if (x === y) {
      return 0;
    } else {
      if (x == null) {
        return -1;
      }
      if (y == null) {
        return 1;
      }
      const tx = typeof x;
      const ty = typeof y;
      if (tx === "number" && ty === "number" || tx === "string" && ty === "string" || tx === "boolean" && ty === "boolean") {
        if (x === y) {
          return 0;
        }
        if (x < y) {
          return -1;
        }
        return 1;
      } else if (Array.isArray(x) && Array.isArray(y)) {
        if (x.length < y.length) {
          return -1;
        } else if (x.length > y.length) {
          return 1;
        } else {
          for (let i = 0; i < x.length; i++) {
            const c = compare(x[i], y[i]);
            if (c != 0) {
              return c;
            }
          }
          return 0;
        }
      } else {
        throw new Error(`comparing ${tx} to ${ty}`);
      }
    }
  }
  function truth_(x) {
    return x != null && x !== false;
  }
  function subs(s, start2, end) {
    return s.substring(start2, end);
  }
  function fn_QMARK_(x) {
    return "function" === typeof x;
  }
  function number_QMARK_(x) {
    return typeof x == "number";
  }
  function string_QMARK_(s) {
    return typeof s === "string";
  }
  function boolean_QMARK_(x) {
    return x === true || x === false;
  }
  function fix(q) {
    if (q >= 0) {
      return Math.floor(q);
    }
    return Math.ceil(q);
  }
  function quot(n, d) {
    const rem = n % d;
    return fix((n - rem) / d);
  }
  function transduce(xform, ...args) {
    switch (args.length) {
      case 2: {
        const f = args[0];
        const coll = args[1];
        return transduce(xform, f, f(), coll);
      }
      default: {
        let f = args[0];
        const init = args[1];
        const coll = args[2];
        f = xform(f);
        const ret = reduce(f, init, coll);
        return f(ret);
      }
    }
  }

  // out-old/reagami/core.mjs
  var svg_ns = "http://www.w3.org/2000/svg";
  var parse_tag = function(tag) {
    const id_index1 = (() => {
      const index2 = tag.indexOf("#");
      if (index2 > 0) {
        return index2;
      }
      ;
    })();
    const class_index3 = (() => {
      const index4 = tag.indexOf(".");
      if (index4 > 0) {
        return index4;
      }
      ;
    })();
    return [truth_(id_index1) ? tag.substring(0, id_index1) : truth_(class_index3) ? tag.substring(0, class_index3) : "else" ? tag : null, truth_(id_index1) ? truth_(class_index3) ? tag.substring(id_index1 + 1, class_index3) : tag.substring(id_index1 + 1) : null, truth_(class_index3) ? tag.substring(class_index3 + 1) : null];
  };
  var properties = /* @__PURE__ */ new Set(["checked", "disabled", "selected", "value", "innerHTML"]);
  var property_QMARK_ = function(x) {
    return properties.has(x);
  };
  var hiccup_seq_QMARK_ = function(x) {
    return not(string_QMARK_(x)) && (() => {
      const and__23718__auto__1 = seq_QMARK_(x);
      if (truth_(and__23718__auto__1)) {
        return not(vector_QMARK_(x));
      } else {
        return and__23718__auto__1;
      }
      ;
    })();
  };
  var move_to_back = function(o, v) {
    if (v in o) {
      const value1 = o[v];
      delete o[v];
      return o[v] = value1;
    }
    ;
  };
  var on_render_key = "reagami.core/on-render";
  var attrs_key = "reagami.core/attrs";
  var props_key = "reagami.core/props";
  var vnode_key = "reagami.core/vnode";
  var root_key = "reagami.core/root";
  var is_run_key = "reagami.core/is-run";
  var data_key = "reagami.core/data";
  var create_vnode_STAR_ = function(hiccup, in_svg_QMARK_) {
    if (truth_((() => {
      const or__23674__auto__1 = hiccup == null;
      if (or__23674__auto__1) {
        return or__23674__auto__1;
      } else {
        const or__23674__auto__2 = string_QMARK_(hiccup);
        if (truth_(or__23674__auto__2)) {
          return or__23674__auto__2;
        } else {
          const or__23674__auto__3 = number_QMARK_(hiccup);
          if (truth_(or__23674__auto__3)) {
            return or__23674__auto__3;
          } else {
            return boolean_QMARK_(hiccup);
          }
          ;
        }
        ;
      }
      ;
    })())) {
      return { "tag": "#text", "text": `${hiccup ?? ""}` };
    } else {
      if (truth_(vector_QMARK_(hiccup))) {
        const tag7 = hiccup[0];
        const children_idx8 = 1;
        const vec__49 = truth_(string_QMARK_(tag7)) ? parse_tag(tag7) : [tag7];
        const tag10 = nth(vec__49, 0, null);
        const id11 = nth(vec__49, 1, null);
        const class$12 = nth(vec__49, 2, null);
        const classes13 = truth_(class$12) ? class$12.split(".") : null;
        const first_child14 = hiccup[children_idx8];
        const attr_idx15 = truth_(map_QMARK_(first_child14)) ? 1 : -1;
        const children_idx16 = -1 === attr_idx15 ? children_idx8 : children_idx8 + 1;
        const in_svg_QMARK_17 = (() => {
          const or__23674__auto__18 = in_svg_QMARK_;
          if (truth_(or__23674__auto__18)) {
            return or__23674__auto__18;
          } else {
            return "svg" === tag10;
          }
          ;
        })();
        const node19 = truth_(fn_QMARK_(tag10)) ? (() => {
          const res20 = apply(tag10, hiccup.slice(1));
          return create_vnode_STAR_(res20, in_svg_QMARK_17);
        })() : (() => {
          const new_children21 = [];
          const node22 = { "type": "element", "svg": in_svg_QMARK_17, "tag": truth_(in_svg_QMARK_17) ? tag10 : tag10.toUpperCase(), "children": new_children21 };
          const modified_props23 = {};
          const modified_attrs24 = {};
          node22[props_key] = modified_props23;
          node22[attrs_key] = modified_attrs24;
          const n1225 = hiccup.length - children_idx16;
          let i26 = 0;
          for (; i26 < n1225; i26++) {
            (() => {
              const child27 = hiccup[i26 + children_idx16];
              if (truth_(hiccup_seq_QMARK_(child27))) {
                for (let G__28 of iterable(child27)) {
                  const x29 = G__28;
                  new_children21.push(create_vnode_STAR_(x29, in_svg_QMARK_17));
                }
                return null;
              } else {
                return new_children21.push(create_vnode_STAR_(child27, in_svg_QMARK_17));
              }
              ;
            })();
          }
          ;
          if (-1 === attr_idx15) {
          } else {
            const attrs30 = hiccup[1];
            const entry_names31 = Object.getOwnPropertyNames(attrs30);
            const entry_count32 = entry_names31.length;
            if (truth_((() => {
              const or__23674__auto__33 = "max" in attrs30;
              if (or__23674__auto__33) {
                return or__23674__auto__33;
              } else {
                return "min" in attrs30;
              }
              ;
            })())) {
              move_to_back(attrs30, "default-value");
              move_to_back(attrs30, "value");
            }
            ;
            const n1334 = entry_count32;
            let i35 = 0;
            for (; i35 < n1334; i35++) {
              (() => {
                const k36 = entry_names31[i35];
                const v37 = attrs30[k36];
                if ("on-render" === k36) {
                  return node22[on_render_key] = v37;
                } else {
                  if (truth_(k36.startsWith("on"))) {
                    const event38 = k36.replaceAll("-", "");
                    return modified_props23[event38] = v37;
                  } else {
                    if (truth_(k36.startsWith("default"))) {
                      const default_attr39 = subs(k36, 7).replaceAll("-", "");
                      return modified_attrs24[default_attr39] = v37;
                    } else {
                      if ("else") {
                        if (truth_("style" === k36 && object_QMARK_(v37))) {
                          const style40 = reduce((function(s, e) {
                            return `${s ?? ""}${e[0] ?? ""}${": "}${e[1] ?? ""}${";"}`;
                          }), "", Object.entries(v37));
                          return modified_attrs24["style"] = style40;
                        } else {
                          if (truth_(property_QMARK_(k36))) {
                            return modified_props23[k36] = v37;
                          } else {
                            if ("else") {
                              if (truth_(v37)) {
                                return modified_attrs24[k36] = v37;
                              }
                            } else {
                              return null;
                            }
                          }
                        }
                        ;
                      } else {
                        return null;
                      }
                    }
                  }
                }
                ;
              })();
            }
            ;
          }
          ;
          if (truth_(!(classes13 == null) && classes13.length > 0)) {
            modified_attrs24["class"] = `${(() => {
              const temp__23263__auto__41 = modified_attrs24["class"];
              if (truth_(temp__23263__auto__41)) {
                const c42 = temp__23263__auto__41;
                return `${c42 ?? ""}${" "}`;
              }
              ;
            })() ?? ""}${classes13.join(" ") ?? ""}`;
          }
          ;
          if (truth_(id11)) {
            modified_attrs24["id"] = id11;
          }
          ;
          return node22;
        })();
        return node19;
      } else {
        if ("else") {
          throw (() => {
            console.error("Invalid hiccup:", hiccup);
            return new Error(`${"Invalid hiccup: "}${hiccup ?? ""}`);
          })();
        } else {
          return null;
        }
      }
    }
    ;
  };
  var create_vnode = function(hiccup) {
    return create_vnode_STAR_(hiccup, false);
  };
  var ref_registry = /* @__PURE__ */ new Map();
  var create_node = function(vnode, root) {
    const node1 = (() => {
      const temp__23182__auto__2 = vnode["text"];
      if (truth_(temp__23182__auto__2)) {
        const text3 = temp__23182__auto__2;
        return document.createTextNode(text3);
      } else {
        const tag4 = vnode["tag"];
        const node5 = truth_(vnode["svg"]) ? document.createElementNS(svg_ns, tag4) : document.createElement(tag4);
        const props6 = vnode[props_key];
        const attrs7 = vnode[attrs_key];
        const attr_names8 = Object.getOwnPropertyNames(attrs7);
        const prop_names9 = Object.getOwnPropertyNames(props6);
        const n1410 = attr_names8.length;
        let i11 = 0;
        for (; i11 < n1410; i11++) {
          (() => {
            const n12 = attr_names8[i11];
            const new_attr13 = attrs7[n12];
            return node5.setAttribute(n12, new_attr13);
          })();
        }
        ;
        const n1514 = prop_names9.length;
        let i15 = 0;
        for (; i15 < n1514; i15++) {
          (() => {
            const n16 = prop_names9[i15];
            const new_prop17 = props6[n16];
            const new_prop18 = void 0 === new_prop17 ? null : new_prop17;
            return node5[n16] = new_prop18;
          })();
        }
        ;
        const temp__23263__auto__19 = vnode["children"];
        if (truth_(temp__23263__auto__19)) {
          const children20 = temp__23263__auto__19;
          const len21 = children20.length;
          const n1622 = len21;
          let i23 = 0;
          for (; i23 < n1622; i23++) {
            (() => {
              const child24 = children20[i23];
              return node5.appendChild(create_node(child24, root));
            })();
          }
          ;
        }
        ;
        const temp__23263__auto__25 = vnode[on_render_key];
        if (truth_(temp__23263__auto__25)) {
          const ref26 = temp__23263__auto__25;
          node5[on_render_key] = ref26;
          update_BANG_(ref_registry, root, fnil(conj, /* @__PURE__ */ new Set([])), node5);
        }
        ;
        return node5;
      }
      ;
    })();
    node1[vnode_key] = vnode;
    return node1;
  };
  var patch = function(parent, new_children, root) {
    const parent_vnode1 = parent[vnode_key];
    const old_children_count2 = truth_((() => {
      const and__23718__auto__3 = parent_vnode1;
      if (truth_(and__23718__auto__3)) {
        return not(parent[root_key]);
      } else {
        return and__23718__auto__3;
      }
      ;
    })()) ? parent_vnode1["children"].length : root === parent ? parent.childNodes.length : "else" ? -1 : null;
    if (-1 === old_children_count2) {
      return null;
    } else {
      const old_children4 = parent.childNodes;
      const new_children_count5 = count(new_children);
      if (!(old_children_count2 === new_children_count5)) {
        return parent.replaceChildren.apply(parent, new_children.map((function(_PERCENT_1) {
          return create_node(_PERCENT_1, root);
        })));
      } else {
        const n176 = new_children_count5;
        let i7 = 0;
        for (; i7 < n176; i7++) {
          (() => {
            const old8 = old_children4[i7];
            const old_vnode9 = old8[vnode_key];
            const new_vnode10 = new_children[i7];
            const txt_old11 = old_vnode9["text"];
            const txt12 = new_vnode10["text"];
            const new_tag13 = new_vnode10["tag"];
            if (truth_((() => {
              const and__23718__auto__14 = txt_old11;
              if (truth_(and__23718__auto__14)) {
                return txt12;
              } else {
                return and__23718__auto__14;
              }
              ;
            })())) {
              if (txt12 === txt_old11) {
                return null;
              } else {
                old8.textContent = txt12;
                return old8[vnode_key] = new_vnode10;
              }
            } else {
              if (truth_((() => {
                const and__23718__auto__15 = old8;
                if (truth_(and__23718__auto__15)) {
                  const and__23718__auto__16 = old_vnode9;
                  if (truth_(and__23718__auto__16)) {
                    const and__23718__auto__17 = new_vnode10;
                    if (truth_(and__23718__auto__17)) {
                      return new_tag13 === old_vnode9["tag"];
                    } else {
                      return and__23718__auto__17;
                    }
                    ;
                  } else {
                    return and__23718__auto__16;
                  }
                  ;
                } else {
                  return and__23718__auto__15;
                }
                ;
              })())) {
                const old_props18 = old_vnode9[props_key];
                const old_attrs19 = old_vnode9[attrs_key];
                const new_props20 = new_vnode10[props_key];
                const new_attrs21 = new_vnode10[attrs_key];
                const old_prop_names22 = Object.getOwnPropertyNames(old_props18);
                const old_attr_names23 = Object.getOwnPropertyNames(old_attrs19);
                const new_attr_names24 = Object.getOwnPropertyNames(new_attrs21);
                const new_prop_names25 = Object.getOwnPropertyNames(new_props20);
                const n1826 = old_prop_names22.length;
                let i27 = 0;
                for (; i27 < n1826; i27++) {
                  (() => {
                    const o28 = old_prop_names22[i27];
                    if (o28 in new_props20) {
                      return null;
                    } else {
                      return old8[o28] = null;
                    }
                    ;
                  })();
                }
                ;
                const n1929 = old_attr_names23.length;
                let i30 = 0;
                for (; i30 < n1929; i30++) {
                  (() => {
                    const o31 = old_attr_names23[i30];
                    if (o31 in new_attrs21) {
                      return null;
                    } else {
                      return old8.removeAttribute(o31);
                    }
                    ;
                  })();
                }
                ;
                const n2032 = new_attr_names24.length;
                let i33 = 0;
                for (; i33 < n2032; i33++) {
                  (() => {
                    const n34 = new_attr_names24[i33];
                    const new_attr35 = new_attrs21[n34];
                    if (new_attr35 === old_attrs19[n34]) {
                      return null;
                    } else {
                      return old8.setAttribute(n34, new_attr35);
                    }
                    ;
                  })();
                }
                ;
                const n2136 = new_prop_names25.length;
                let i37 = 0;
                for (; i37 < n2136; i37++) {
                  (() => {
                    const n38 = new_prop_names25[i37];
                    const new_prop39 = (() => {
                      const v40 = new_props20[n38];
                      if (void 0 === v40) {
                        return null;
                      } else {
                        return v40;
                      }
                      ;
                    })();
                    if (old_props18[n38] === new_prop39) {
                      return null;
                    } else {
                      return old8[n38] = new_prop39;
                    }
                    ;
                  })();
                }
                ;
                const temp__23263__auto__41 = new_vnode10["children"];
                if (truth_(temp__23263__auto__41)) {
                  const new_children42 = temp__23263__auto__41;
                  patch(old8, new_children42, root);
                }
                ;
                return old8[vnode_key] = new_vnode10;
              } else {
                if ("else") {
                  const new_node43 = create_node(new_vnode10, root);
                  return parent.replaceChild(new_node43, old8);
                } else {
                  return null;
                }
              }
            }
            ;
          })();
        }
        ;
        return null;
      }
      ;
    }
    ;
  };
  var render = function(root, hiccup) {
    if (truth_(root[root_key])) {
    } else {
      root.textContent = "";
      root[root_key] = true;
    }
    ;
    const new_node1 = create_vnode(hiccup);
    patch(root, [new_node1], root);
    for (let G__2 of iterable(ref_registry.get(root))) {
      const node3 = G__2;
      const ref4 = node3[on_render_key];
      if (truth_(node3.isConnected)) {
        if (not(ref4[is_run_key])) {
          const data5 = ref4(node3, "mount", null);
          ref4[is_run_key] = true;
          ref4[data_key] = data5;
        } else {
          const data6 = ref4(node3, "update", ref4[data_key]);
          ref4[data_key] = data6;
        }
      } else {
        ref4(node3, "unmount", ref4[data_key]);
        delete ref4[data_key];
        update_BANG_(ref_registry, root, disj, node3);
      }
    }
    return null;
  };

  // out-old/app.mjs
  var N = 2e3;
  var now = function() {
    return performance.now();
  };
  var rows = function(n, start2) {
    return mapv((function(i) {
      return { "id": start2 + i };
    }), range(n));
  };
  var row = function(it) {
    return ["div.row", { "key": get(it, "id") }, ["span.id", `${"#"}${get(it, "id") ?? ""}`], ["input.field", { "placeholder": "type here" }], ["span.dot"]];
  };
  var view = function(state) {
    return into(["div.list"], mapv(row, get(state, "rows")));
  };
  var start = function(side) {
    const state1 = atom({ "rows": rows(N, 0) });
    const next_id2 = atom(N);
    const el3 = document.getElementById(`app-${side ?? ""}`);
    const time_el4 = document.getElementById(`time-${side ?? ""}`);
    const render_BANG_5 = (function() {
      const t06 = now();
      render(el3, view(deref(state1)));
      return time_el4.textContent = `${(now() - t06).toFixed(1) ?? ""}${" ms"}`;
    });
    render_BANG_5();
    return window[side] = { "prepend": (function() {
      swap_BANG_(state1, update, "rows", (function(_PERCENT_1) {
        return into([{ "id": swap_BANG_(next_id2, inc) }], _PERCENT_1);
      }));
      return render_BANG_5();
    }), "removeMiddle": (function() {
      swap_BANG_(state1, update, "rows", (function(rs) {
        const m7 = quot(count(rs), 2);
        return into(subvec(rs, 0, m7), subvec(rs, m7 + 1));
      }));
      return render_BANG_5();
    }), "shuffle": (function() {
      swap_BANG_(state1, update, "rows", (function(_PERCENT_1) {
        return vec(sort_by((function(_) {
          return Math.random();
        }), _PERCENT_1));
      }));
      return render_BANG_5();
    }), "reset": (function() {
      reset_BANG_(state1, { "rows": rows(N, 0) });
      reset_BANG_(next_id2, N);
      return render_BANG_5();
    }) };
  };

  // out-old/oldmain.mjs
  start("OLD");
})();
