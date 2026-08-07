// Reagami itself, called from JavaScript. Hiccup is arrays and objects, so no
// ClojureScript is needed to use it. This page does what src/app.cljs does.
import * as reagami from "../out/reagami/core.mjs";
import "../out/todo_list.mjs";

// Hiccup is `["div", {...}, child]`. One Proxy turns any tag into a function,
// so `div(h2("hi"))` builds `["div", ["h2", "hi"]]`. args is fresh per call, so
// unshift reuses it instead of copying into a second array.
const tags = new Proxy({}, { get: (_, tag) => (...args) => (args.unshift(tag), args) });
const { div, h2, h3, label, input, pre, code } = tags;
const todoList = tags["todo-list"];

const state = { label: "Groceries", log: [] };

const root = document.getElementById("app");

const render = () => reagami.render(root, app());

const log = (line) => {
  state.log.push(line);
  render();
};

// A standard event is its DOM property name, so `oninput` and `onclick`.
// A custom event keeps its dashes, so `on-item-added` listens for "item-added".
const app = () =>
  div(
    h2("A web component, rendered by Reagami from JavaScript"),
    label("Heading: ",
          input({ value: state.label,
                  oninput: (e) => { state.label = e.target.value; render(); } })),
    todoList({ label: state.label,
               "on-item-added": (e) => log(`added ${e.detail.text}`),
               "on-item-changed": (e) =>
                 log(`changed ${e.detail.text}${e.detail.done ? " (done)" : ""}`),
               "on-item-removed": (e) => log(`removed ${e.detail.text}`) }),
    h3("Events"),
    pre(...state.log.map((line) => code(line + "\n"))));

render();
