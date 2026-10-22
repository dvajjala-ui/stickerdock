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
  let aiBusy = false;
  let libraryOffset = 0;
  let libraryBusy = false;
  let libraryEpoch = 0;
  const mediaDetails = new Map();
  const pendingAiKey = "stickerdock:pending-studio-ai";
  let pendingAiMemory = null;
  function readPendingAi() {try {return JSON.parse(sessionStorage.getItem(pendingAiKey));}catch (_) {return pendingAiMemory;}}
  function savePendingAi(job) {pendingAiMemory=job;try {sessionStorage.setItem(pendingAiKey,JSON.stringify(job));}catch (_) {}}
  function clearPendingAi() {pendingAiMemory=null;try {sessionStorage.removeItem(pendingAiKey);}catch (_) {}}

  const sleep = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds));

  const status = (message, error = false) => {
    $("status").textContent = message;
    $("status").classList.toggle("error", error);
  };
  const updateAiButton = () => {
    $("generate").disabled = aiBusy || !ready || !$("prompt").value.trim();
  };
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
  function loadDataUrl(url, originPath = null, readyMessage = "Image ready. Add a caption and style it, or use the Factory AI worker.") {
    image.onload = () => {
      sourcePath = originPath;
      ready = true;
      $("empty").hidden = true;
      $("download").disabled = false;
      $("upload").disabled = false;
      draw();
      updateAiButton();
      status(readyMessage);
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
    const options = {
      method: "POST",
      credentials: "omit",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify(body)
    };
    const response = await fetch(path, options);
    const value = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(value.error || `Request failed (${response.status})`);
    return value;
  }
  async function ensureAiSource() {
    if (sourcePath) return sourcePath;
    if (!ready || !image.src.startsWith("data:image/")) throw new Error("Choose an image first.");
    const rights = $("source-rights").value;
    if (!rights) throw new Error("Choose reuse rights before sending a local image online.");
    status("Saving the source to the Factory inbox…");
    const upload = document.createElement("canvas");
    const scale = Math.min(1, 1024 / Math.max(image.naturalWidth, image.naturalHeight));
    upload.width = Math.max(1, Math.round(image.naturalWidth * scale));
    upload.height = Math.max(1, Math.round(image.naturalHeight * scale));
    const context = upload.getContext("2d");
    context.drawImage(image, 0, 0, upload.width, upload.height);
    let prepared = upload.toDataURL("image/png");
    if (prepared.length > 4_000_000) {
      const smaller = document.createElement("canvas");
      smaller.width = Math.max(1, Math.round(upload.width * 0.7));
      smaller.height = Math.max(1, Math.round(upload.height * 0.7));
      smaller.getContext("2d").drawImage(upload, 0, 0, smaller.width, smaller.height);
      prepared = smaller.toDataURL("image/png");
    }
    const result = await api("/api/upload", {image: prepared, title: "Creator AI source", rights});
    sourcePath = result.path;
    return sourcePath;
  }

  async function waitForAiRequest(id) {
    for (let attempt = 0; attempt < 240; attempt += 1) {
      const result = await api("/api/captions", {action: "status", id});
      if (result.status === "complete") return result;
      if (result.status === "failed" || result.status === "cancelled") {
        const error = new Error(result.error_message || `AI request ${result.status}.`);
        error.terminal = true;
        throw error;
      }
      status(result.status === "running" ? "AI worker is creating your sticker…" : "AI request queued. The Factory worker checks every 10 minutes…");
      await sleep(5000);
    }
    throw new Error("AI request is still queued. You can leave this page and try again later.");
  }

  $("prompt").addEventListener("input", updateAiButton);
  $("generate").addEventListener("click", async () => {
    if (aiBusy) return;
    const prompt = $("prompt").value.trim();
    if (!prompt) { status("Describe the AI sticker you want first.", true); return; }
    aiBusy = true; updateAiButton();
    try {
      const path = await ensureAiSource();
      status("Queueing a Factory AI sticker request…");
      let pending = readPendingAi();
      if (!pending || pending.path !== path || pending.prompt !== prompt)
        pending = {id: crypto.randomUUID(), path, prompt};
      savePendingAi(pending);
      const queued = await api("/api/captions", {action: "queue", prompt, source_path: path,
        request_id: pending.id});
      const completed = await waitForAiRequest(queued.id);
      const result = await api("/api/library", {action: "fetch", path: completed.result_path});
      clearPendingAi();
      $("caption").value = "";
      loadDataUrl(result.image, completed.result_path, "AI sticker ready. Download it or continue editing.");
    } catch (error) { if (error.terminal) clearPendingAi(); status(error.message, true); }
    finally { aiBusy = false; updateAiButton(); }
  });

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
      const rights = sourcePath ? "REFERENCE_ONLY" : $("source-rights").value;
      if (!rights) throw new Error("Choose reuse rights before uploading a local image.");
      const result = await api("/api/upload", {image: canvas.toDataURL("image/png"),
        title: $("caption").value || "Creator Studio sticker", source_path: sourcePath, rights});
      status(`Queued for the factory: ${result.sha256.slice(0, 10)}…`);
      await refreshLibrary();
    } catch (error) { status(error.message, true); }
    finally { button.disabled = false; }
  });
  const sourceTitle = (name, index) => {
    if (/^[0-9a-f]{40,64}\.(?:png|jpe?g|webp)$/i.test(name)) return `Image ${index + 1}`;
    return name.replace(/^File:/i, "").replace(/\.(?:png|jpe?g|webp)$/i, "").trim() || `Image ${index + 1}`;
  };
  const observer = "IntersectionObserver" in window ? new IntersectionObserver((entries) => {
    for (const entry of entries) if (entry.isIntersecting) {
      observer.unobserve(entry.target);
      const card = entry.target;
      const path = card.dataset.path;
      const epoch = Number(card.dataset.epoch);
      (async () => {
        try {
          let detail = mediaDetails.get(path);
          if (!detail) {
            detail = await api("/api/library", {action: "preview", path});
            mediaDetails.set(path, detail);
          }
          if (!card.isConnected || epoch !== libraryEpoch) return;
          const img = document.createElement("img");
          img.src = detail.preview;
          img.alt = detail.name;
          card.querySelector(".library-placeholder").replaceWith(img);
          card.querySelector("strong").textContent = sourceTitle(detail.name, Number(card.dataset.index));
          card.querySelector("small").textContent = `${detail.purpose} · ${detail.license}`;
          card.setAttribute("aria-label", `Load ${sourceTitle(detail.name, Number(card.dataset.index))}, ${detail.purpose}, ${detail.license}`);
        } catch (_) { if (card.isConnected) card.querySelector(".library-placeholder").textContent = "Preview unavailable"; }
      })();
    }
  }, {root: $("library-gallery"), rootMargin: "80px"}) : null;
  async function loadLibraryPage(reset = false) {
    if (libraryBusy) return;
    libraryBusy = true;
    const more = $("library-more");
    more.disabled = true;
    const gallery = $("library-gallery");
    if (reset) {
      libraryEpoch += 1;
      libraryOffset = 0;
      gallery.replaceChildren();
    }
    status("Loading workspace images…");
    try {
      const result = await api("/api/library", {action: "list", offset: libraryOffset, limit: 12});
      for (const item of result.items) {
        const index = gallery.children.length;
        const card = document.createElement("button");
        card.type = "button";
        card.className = "library-item";
        card.dataset.path = item.path;
        card.dataset.index = index;
        card.dataset.epoch = libraryEpoch;
        const placeholder = document.createElement("span");
        placeholder.className = "library-placeholder";
        placeholder.textContent = "Loading preview…";
        const title = document.createElement("strong");
        title.textContent = sourceTitle(item.name, index);
        const detail = document.createElement("small");
        detail.textContent = `${Math.ceil(item.size / 1024)} KB`;
        card.setAttribute("aria-label", `Load ${title.textContent}`);
        card.append(placeholder, title, detail);
        card.addEventListener("click", async () => {
          status(`Loading ${title.textContent}…`);
          try { loadDataUrl((await api("/api/library", {action: "fetch", path: item.path})).image, item.path); }
          catch (error) { status(error.message, true); }
        });
        gallery.append(card);
        if (observer) observer.observe(card);
        else { placeholder.textContent = "Tap to load"; }
      }
      libraryOffset = result.next_offset;
      more.hidden = libraryOffset === null;
      if (!gallery.children.length) {
        const empty = document.createElement("p");
        empty.className = "library-empty";
        empty.textContent = "No workspace images yet. Upload one above.";
        gallery.append(empty);
      }
      status(`${gallery.querySelectorAll(".library-item").length} workspace images ready.`);
    } catch (error) { status(error.message, true); }
    finally { libraryBusy = false; more.disabled = false; }
  }
  function refreshLibrary() { return loadLibraryPage(true); }
  $("refresh").addEventListener("click", refreshLibrary);
  $("library-more").addEventListener("click", () => loadLibraryPage());
  async function restorePendingAi() {
    const pending = readPendingAi();
    if (!pending?.path || !pending?.prompt) return;
    $("prompt").value = pending.prompt;
    try {
      const result = await api("/api/library", {action: "fetch", path: pending.path});
      loadDataUrl(result.image, pending.path, "Saved AI request found. Press Generate to resume its status.");
    } catch (error) {status(`Saved AI request could not be reopened: ${error.message}`, true);}
  }
  draw();
  updateAiButton();
  refreshLibrary().then(restorePendingAi);
})();
