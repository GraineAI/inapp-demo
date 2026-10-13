/**
 * Small DOM helpers: an element builder, storage that never throws, a toast and
 * an in-page confirmation. Text always goes in as text (never as markup): some
 * of it — what the assistant remembers — comes from the network.
 */

/**
 * h("button", { class: "btn", onClick: fn, disabled: true }, "Label", child, …)
 * `on…` attributes become listeners; `true` sets an empty attribute; null,
 * undefined and false are left out.
 */
export function h(tag, attrs, ...children) {
  const el = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs || {})) {
    if (value == null || value === false) continue;
    if (key === "class") el.className = value;
    else if (key.startsWith("on") && typeof value === "function") el.addEventListener(key.slice(2).toLowerCase(), value);
    else if (value === true) el.setAttribute(key, "");
    else el.setAttribute(key, String(value));
  }
  for (const child of children.flat(Infinity)) {
    if (child == null || child === false) continue;
    el.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return el;
}

/**
 * localStorage that never throws: a private window, blocked site data or a
 * full quota leaves the page working, just forgetful.
 */
export const store = {
  get(key) {
    try { return window.localStorage.getItem(key); } catch (e) { return null; }
  },
  set(key, value) {
    try { window.localStorage.setItem(key, value); } catch (e) { /* forgetful, not broken */ }
  },
  remove(key) {
    try { window.localStorage.removeItem(key); } catch (e) { /* as above */ }
  },
  getJSON(key) {
    const raw = store.get(key);
    if (!raw) return null;
    try { return JSON.parse(raw); } catch (e) { return null; }
  },
  setJSON(key, value) {
    store.set(key, JSON.stringify(value));
  },
};

let toastTimer = 0;

/** A short message at the top of the page that goes away by itself. */
export function toast(message) {
  const el = document.getElementById("toast");
  if (!el || !message) return;
  el.textContent = message;
  el.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { el.hidden = true; }, 4500);
}

/**
 * Ask before something that cannot be undone, in the page (a <dialog>, not the
 * browser's confirm box). Resolves true when confirmed.
 */
export function confirmDialog({ title, text, confirmLabel, cancelLabel = "Keep" }) {
  const dialog = document.getElementById("confirm");
  if (!dialog) return Promise.resolve(false);
  dialog.querySelector("#confirm-title").textContent = title;
  dialog.querySelector("#confirm-text").textContent = text;
  const yes = dialog.querySelector("#confirm-yes");
  const no = dialog.querySelector("#confirm-no");
  yes.textContent = confirmLabel;
  no.textContent = cancelLabel;
  const opener = document.activeElement;

  return new Promise((resolve) => {
    let answered = false;
    const finish = (answer) => {
      if (answered) return;
      answered = true;
      yes.removeEventListener("click", onYes);
      no.removeEventListener("click", onNo);
      dialog.removeEventListener("cancel", onCancel);
      if (typeof dialog.close === "function" && dialog.open) dialog.close();
      else dialog.removeAttribute("open");
      if (opener && opener.isConnected && typeof opener.focus === "function") opener.focus();
      resolve(answer);
    };
    const onYes = () => finish(true);
    const onNo = () => finish(false);
    const onCancel = (e) => { e.preventDefault(); finish(false); };   // Escape
    yes.addEventListener("click", onYes);
    no.addEventListener("click", onNo);
    dialog.addEventListener("cancel", onCancel);
    if (typeof dialog.showModal === "function") dialog.showModal();
    else dialog.setAttribute("open", "");
    // The safe answer has the focus: Enter on an unread dialog keeps things.
    no.focus();
  });
}

/** Copy text; resolves true when it worked. */
export async function copyText(text) {
  try {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch (e) { /* fall through to the old way */ }
  try {
    const area = h("textarea", { class: "visually-hidden", "aria-hidden": "true" });
    area.value = text;
    document.body.append(area);
    area.select();
    const done = document.execCommand && document.execCommand("copy");
    area.remove();
    return !!done;
  } catch (e) {
    return false;
  }
}
