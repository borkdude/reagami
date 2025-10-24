import * as squint_core from 'squint-cljs/core.js';
var svg_ns = "http://www.w3.org/2000/svg";
var parse_tag = function (tag) {
const id_index1 = (() => {
const index2 = tag.indexOf("#");
if ((index2 > 0)) {
return index2;
};

})();
const class_index3 = (() => {
const index4 = tag.indexOf(".");
if ((index4 > 0)) {
return index4;
};

})();
return [((squint_core.truth_(id_index1)) ? (tag.substring(0, id_index1)) : (((squint_core.truth_(class_index3)) ? (tag.substring(0, class_index3)) : ((("else") ? (tag) : (null)))))), ((squint_core.truth_(id_index1)) ? (((squint_core.truth_(class_index3)) ? (tag.substring((id_index1 + 1), class_index3)) : (tag.substring((id_index1 + 1))))) : (null)), ((squint_core.truth_(class_index3)) ? (tag.substring((class_index3 + 1))) : (null))];

};
var create_node = (() => {
const f1 = (function (...args2) {
const G__31 = args2.length;
switch (G__31) {case 1:
return f1.cljs$core$IFn$_invoke$arity$1(args2[0]);

break;
case 2:
return f1.cljs$core$IFn$_invoke$arity$2(args2[0], args2[1]);

break;
default:
throw new Error(`${"Invalid arity: "}${args2.length??''}`)};

});
f1.cljs$core$IFn$_invoke$arity$1 = (function (hiccup) {
return create_node(hiccup, false);

});
f1.cljs$core$IFn$_invoke$arity$2 = (function (hiccup, in_svg_QMARK_) {
if (squint_core.truth_((() => {
const or__23175__auto__3 = (hiccup == null);
if (or__23175__auto__3) {
return or__23175__auto__3} else {
const or__23175__auto__4 = squint_core.string_QMARK_(hiccup);
if (squint_core.truth_(or__23175__auto__4)) {
return or__23175__auto__4} else {
const or__23175__auto__5 = squint_core.number_QMARK_(hiccup);
if (squint_core.truth_(or__23175__auto__5)) {
return or__23175__auto__5} else {
return squint_core.boolean_QMARK_(hiccup)};
};
};

})())) {
return document.createTextNode(`${hiccup??''}`)} else {
if (squint_core.truth_(squint_core.vector_QMARK_(hiccup))) {
const vec__615 = hiccup;
const seq__716 = squint_core.seq(vec__615);
const first__817 = squint_core.first(seq__716);
const seq__718 = squint_core.next(seq__716);
const tag19 = first__817;
const children20 = seq__718;
const vec__921 = ((squint_core.truth_(squint_core.string_QMARK_(tag19))) ? (parse_tag(tag19)) : ([tag19]));
const tag22 = squint_core.nth(vec__921, 0, null);
const id23 = squint_core.nth(vec__921, 1, null);
const class$24 = squint_core.nth(vec__921, 2, null);
const classes25 = ((squint_core.truth_(class$24)) ? (class$24.split(".")) : (null));
const vec__1226 = ((squint_core.truth_(squint_core.map_QMARK_(squint_core.first(children20)))) ? ([squint_core.first(children20), squint_core.rest(children20)]) : ([null, children20]));
const attrs27 = squint_core.nth(vec__1226, 0, null);
const children28 = squint_core.nth(vec__1226, 1, null);
const in_svg_QMARK_29 = (() => {
const or__23175__auto__30 = in_svg_QMARK_;
if (squint_core.truth_(or__23175__auto__30)) {
return or__23175__auto__30} else {
return ("svg" === tag22)};

})();
const node31 = ((squint_core.truth_(squint_core.fn_QMARK_(tag22))) ? ((() => {
const res32 = squint_core.apply(tag22, ((squint_core.truth_(attrs27)) ? (squint_core.cons(attrs27, children28)) : (children28)));
return create_node(res32, in_svg_QMARK_29);

})()) : ((() => {
const node33 = ((squint_core.truth_(in_svg_QMARK_29)) ? (document.createElementNS(svg_ns, tag22)) : (document.createElement(tag22)));
for (let G__34 of squint_core.iterable(children28)) {
const child35 = G__34;
const child_nodes36 = ((squint_core.truth_((() => {
const and__23196__auto__37 = squint_core.seq_QMARK_(child35);
if (squint_core.truth_(and__23196__auto__37)) {
return squint_core.not(squint_core.vector_QMARK_(child35))} else {
return and__23196__auto__37};

})())) ? (squint_core.mapv((function (_PERCENT_1) {
return create_node(_PERCENT_1, in_svg_QMARK_29);

}), child35)) : ([create_node(child35, in_svg_QMARK_29)]));
for (let G__38 of squint_core.iterable(child_nodes36)) {
const child_node39 = G__38;
node33.appendChild(child_node39)
}
};
for (let G__40 of squint_core.iterable(attrs27)) {
const vec__4144 = G__40;
const k45 = squint_core.nth(vec__4144, 0, null);
const v46 = squint_core.nth(vec__4144, 1, null);
const key_name47 = k45;
if (squint_core.truth_((("style" === key_name47) && squint_core.map_QMARK_(v46)))) {
for (let G__48 of squint_core.iterable(v46)) {
const vec__4952 = G__48;
const k53 = squint_core.nth(vec__4952, 0, null);
const v54 = squint_core.nth(vec__4952, 1, null);
(node33.style[k53] = v54)
}} else {
if (squint_core.truth_(key_name47.startsWith("on"))) {
const event55 = key_name47.replace(/on-?/, "").toLowerCase();
node33.addEventListener(event55, v46)} else {
if ("else") {
if (squint_core.truth_(v46)) {
node33.setAttribute(key_name47, `${v46??''}`)}} else {
}}}
};
const class_list56 = node33.classList;
for (let G__57 of squint_core.iterable(classes25)) {
const clazz58 = G__57;
class_list56.add(clazz58)
};
if (squint_core.truth_(id23)) {
node33.id = id23};
return node33;

})()));
return node31;
} else {
if ("else") {
throw (() => {
console.error("Invalid hiccup:", hiccup);
return new Error(`${"Invalid hiccup: "}${hiccup??''}`)
})()} else {
return null}}};

});
f1.cljs$lang$maxFixedArity = 2;
return f1;

})();
var patch = function (parent, new_children) {
const old_children1 = parent.childNodes;
if (!(squint_core._EQ_(squint_core.count(old_children1), squint_core.count(new_children)))) {
return parent.replaceChildren.apply(parent, new_children)} else {
for (let G__2 of squint_core.iterable(squint_core.mapv(squint_core.vector, old_children1, new_children))) {
const vec__36 = G__2;
const old7 = squint_core.nth(vec__36, 0, null);
const new$8 = squint_core.nth(vec__36, 1, null);
if (squint_core.truth_((() => {
const and__23196__auto__9 = old7;
if (squint_core.truth_(and__23196__auto__9)) {
const and__23196__auto__10 = new$8;
if (squint_core.truth_(and__23196__auto__10)) {
return squint_core._EQ_(old7.nodeName, new$8.nodeName)} else {
return and__23196__auto__10};
} else {
return and__23196__auto__9};

})())) {
if ((3 === old7.nodeType)) {
const txt11 = new$8.textContent;
old7.textContent = txt11} else {
const new_attributes12 = new$8.attributes;
const old_attributes13 = old7.attributes;
for (let G__14 of squint_core.iterable(new_attributes12)) {
const attr15 = G__14;
old7.setAttribute(attr15.name, attr15.value)
};
for (let G__16 of squint_core.iterable(old_attributes13)) {
const attr17 = G__16;
if (squint_core.truth_(new$8.hasAttribute(attr17.name))) {
} else {
old7.removeAttribute(attr17.name)}
};
const temp__22835__auto__18 = new$8.childNodes;
if (squint_core.truth_(temp__22835__auto__18)) {
const new_children19 = temp__22835__auto__18;
patch(old7, new_children19)}}} else {
if ("else") {
parent.replaceChild(new$8, old7)} else {
}}
}return null};

};
var render = function (root, hiccup) {
const new_node1 = create_node(hiccup);
return patch(root, [new_node1]);

};

export { svg_ns, render }
