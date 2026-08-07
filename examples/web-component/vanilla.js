// The same element from plain JavaScript. No Reagami and no ClojureScript here:
// two attributes in, one method, two events out.
import "./out/todo_list.mjs";

const app = document.getElementById("app");

const list = document.createElement("todo-list");
list.setAttribute("label", "Groceries");   // the attribute
list.label = "Groceries";                  // or the property, which mirrors it
app.append(list);

const log = document.createElement("ol");
const heard = (line) => {
  const li = document.createElement("li");
  li.textContent = line;
  log.append(li);
};

// one listener for both events: they bubble and are composed, so they leave the
// shadow root
list.addEventListener("item-added", (e) => heard(`added ${e.detail.text}`));
list.addEventListener("item-changed", (e) =>
  heard(`changed ${e.detail.text}${e.detail.done ? " (done)" : ""}`));
list.addEventListener("item-removed", (e) => heard(`removed ${e.detail.text}`));

const seed = document.createElement("button");
seed.textContent = "Add three items from JS";
seed.addEventListener("click", () => {
  for (const text of ["milk", "eggs", "bread"]) list.addItem(text);
});

const count = document.createElement("button");
count.textContent = "Count items";
count.addEventListener("click", () => heard(`${list.items.length} items now`));

const heading = document.createElement("h2");
heading.textContent = "The same web component, driven by plain JavaScript";
const controls = document.createElement("div");
controls.style.cssText = "display:flex;gap:0.5rem;margin:0.5rem 0";
controls.append(seed, count);
const logHeading = document.createElement("h3");
logHeading.textContent = "What the page heard";

app.prepend(heading);
app.append(controls, logHeading, log);
