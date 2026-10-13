(() => {
  "use strict";
  const $ = (id) => document.getElementById(id);
  const canvas = $("canvas");
  const ctx = canvas.getContext("2d");
  const image = new Image();
  let ready = false;
  let captionX = 256;
  let captionY = 408;
  let dragging = false;
  let sourcePath = null;

  const status = (message, error = false) => {
    $("status").textContent = message;
    $("status").classList.toggle("error", error);
  };
  const token = () => $("token").value.trim();
  $("token").value = sessionStorage.getItem("stickerdock-studio-token") || "";
  $("token").addEventListener("change", () => sessionStorage.setItem("stickerdock-studio-token", token()));

  function coverGeometry(width, height, fit) {
    const scale = fit === "contain" ? Math.min(512 / width, 512 / height) : Math.max(512 / width, 512 / height);
    const w = width * scale, h = height * scale;
    return [(512 - w) / 2, (512 - h) / 2, w, h];
  }
  function wrappedLines(text, maxWidth) {
    const words = text.split(/\s+/).filter(Boolean);
    const lines = [];
    let line = "";
    for (const word of words) {
      const next = line ? `${line} ${word}` : word;
      if (line && ctx.measureText(next).width > maxWidth) {
        lines.push(line); line = word;
      } else line = next;
    }
    if (line) lines.push(line);
    return lines.slice(0, 4);
  }
  function drawSource(target = ctx, size = 512) {
    target.clearRect(0, 0, size, size);
    if (!ready) return;
    const fit = $("fit").value;
    const geometry = coverGeometry(image.naturalWidth, image.naturalHeight, fit).map((n) => n * size / 512);
    if (fit === "contain") {
      target.fillStyle = "#11120f";
      target.fillRect(0, 0, size, size);
    }
    target.drawImage(image, ...geometry);
  }
  function draw() {
    drawSource();
    if (!ready) return;
    let text = $("caption").value.trim();
    if ($("uppercase").checked) text = text.toUpperCase();
    if (!text) return;
    const fontSize = Number($("font-size").value);
    ctx.font = `900 ${fontSize}px Impact, Arial Black, sans-serif`;
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.lineJoin = "round";
    ctx.miterLimit = 2;
    const lines = wrappedLines(text, 512 * Number($("text-width").value) / 100);
    const lineHeight = fontSize * .98;
    const start = captionY - ((lines.length - 1) * lineHeight / 2);
    ctx.strokeStyle = $("stroke").value;
    ctx.lineWidth = Math.max(5, fontSize * .13);
    ctx.fillStyle = $("fill").value;
    lines.forEach((line, index) => {
      const y = start + index * lineHeight;
      ctx.strokeText(line, captionX, y);
      ctx.fillText(line, captionX, y);
    });
  }
  function loadDataUrl(url, originPath = null) {
    image.onload = () => {
      sourcePath = originPath;
      ready = true;
      $("empty").hidden = true;
      $("download").disabled = false;
      $("upload").disabled = false;
      draw();
      status("Image ready. Add a caption and style it. AI prompts are coming soon.");
    };
    image.onerror = () => status("That image could not be opened.", true);
    image.src = url;
  }
  function loadFile(file) {
    if (!file || !["image/png", "image/jpeg", "image/webp"].includes(file.type) || file.size > 15_000_000) {
      status("Choose a PNG, JPEG or WebP under 15 MB.", true); return;
    }
    const reader = new FileReader();
    reader.onload = () => loadDataUrl(reader.result);
    reader.onerror = () => status("The selected file could not be read.", true);
    reader.readAsDataURL(file);
  }
  async function api(path, body) {
    if (!token()) throw new Error("Enter your creator token first.");
    sessionStorage.setItem("stickerdock-studio-token", token());
    const response = await fetch(path, {
      method: "POST",
      headers: {"Content-Type": "application/json", "X-StickerDock-Token": token()},
      body: JSON.stringify(body)
    });
    const value = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(value.error || `Request failed (${response.status})`);
    return value;
  }
  $("file").addEventListener("change", (event) => loadFile(event.target.files[0]));
  const drop = $("drop");
  ["dragenter", "dragover"].forEach((name) => drop.addEventListener(name, (event) => { event.preventDefault(); drop.classList.add("dragging"); }));
  ["dragleave", "drop"].forEach((name) => drop.addEventListener(name, (event) => { event.preventDefault(); drop.classList.remove("dragging"); }));
  drop.addEventListener("drop", (event) => loadFile(event.dataTransfer.files[0]));
  ["caption", "font-size", "text-width", "fill", "stroke", "uppercase", "fit"].forEach((id) => $(id).addEventListener("input", () => {
    $("font-output").value = $("font-size").value;
    $("width-output").value = `${$("text-width").value}%`;
    draw();
  }));

  const position = (event) => {
    const rect = canvas.getBoundingClientRect();
    captionX = Math.max(25, Math.min(487, (event.clientX - rect.left) * 512 / rect.width));
    captionY = Math.max(35, Math.min(477, (event.clientY - rect.top) * 512 / rect.height));
    draw();
  };
  canvas.addEventListener("pointerdown", (event) => { if (ready) { dragging = true; canvas.setPointerCapture(event.pointerId); position(event); } });
  canvas.addEventListener("pointermove", (event) => { if (dragging) position(event); });
  canvas.addEventListener("pointerup", () => dragging = false);

  $("download").addEventListener("click", () => {
    draw();
    const link = document.createElement("a");
    link.download = `stickerdock-${new Date().toISOString().replace(/[:.]/g, "-")}.png`;
    link.href = canvas.toDataURL("image/png");
    link.click();
  });
  $("upload").addEventListener("click", async () => {
    const button = $("upload");
    button.disabled = true; status("Uploading edited sticker to the private inbox…");
    try {
      const result = await api("/api/upload", {image: canvas.toDataURL("image/png"),
        title: $("caption").value || "Creator Studio sticker", source_path: sourcePath});
      status(`Queued for the factory: ${result.sha256.slice(0, 10)}…`);
      await refreshLibrary();
    } catch (error) { status(error.message, true); }
    finally { button.disabled = false; }
  });
  async function refreshLibrary() {
    status("Loading the private factory inbox…");
    try {
      const result = await api("/api/library", {action: "list"});
      const select = $("library");
      select.replaceChildren(new Option(result.items.length ? "Choose an inbox image" : "Inbox has no editable images", ""),
        ...result.items.map((item) => new Option(`${item.name.slice(0, 14)}… (${Math.ceil(item.size / 1024)} KB)`, item.path)));
      status(`Factory inbox loaded: ${result.items.length} image${result.items.length === 1 ? "" : "s"}.`);
    } catch (error) { status(error.message, true); }
  }
  $("refresh").addEventListener("click", refreshLibrary);
  $("load-library").addEventListener("click", async () => {
    const path = $("library").value;
    if (!path) { status("Choose an inbox image first.", true); return; }
    status("Loading inbox image…");
    try { loadDataUrl((await api("/api/library", {action: "fetch", path})).image, path); }
    catch (error) { status(error.message, true); }
  });
  draw();
})();
