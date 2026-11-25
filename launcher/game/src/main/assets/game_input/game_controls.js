const port = browser.runtime.connectNative("gentv.game");
const keys = {
  ArrowUp: ["ArrowUp", "ArrowUp"],
  ArrowDown: ["ArrowDown", "ArrowDown"],
  ArrowLeft: ["ArrowLeft", "ArrowLeft"],
  ArrowRight: ["ArrowRight", "ArrowRight"],
  Shift: ["Shift", "ShiftLeft"],
  Space: [" ", "Space"],
  Enter: ["Enter", "Enter"],
  Escape: ["Escape", "Escape"],
  r: ["r", "KeyR"]
};
port.onMessage.addListener(message => {
  if (!message || !Object.prototype.hasOwnProperty.call(keys, message.key)
      || typeof message.down !== "boolean") return;
  const [key, code] = keys[message.key];
  const target = document.activeElement || document.body || document;
  target.dispatchEvent(new KeyboardEvent(message.down ? "keydown" : "keyup", {
    key, code, bubbles: true, cancelable: true
  }));
});
