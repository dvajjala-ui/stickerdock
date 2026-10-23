/* Shared admin dashboard. Always reads live data; no account or preview mode. */
(() => {
  "use strict";
  const $ = (id) => document.getElementById(id);
  const esc = (value) => String(value ?? "").replace(/[&<>"']/g, (c) => ({"&":"&amp;","<":"&lt;",">":"&gt;","\"":"&quot;","'":"&#39;"}[c]));
  const categories = ["GENERAL","REACTIONS","HINGLISH","MOVIES","NEWS","ANIMALS","LOVE","WORK","FESTIVALS","OTHER"];
  const contentCategories = ["TRENDING","POLITICS","CREATIVE","MOVIES","SERIES","INDIA","SPORTS","TECHNOLOGY","EVERYDAY","ADULT_HUMOR"];
  const titles = {overview:"Overview",packs:"Sticker packs",media:"Media library",generate:"AI workbench",content:"Content planner",factory:"Factory & queue",catalog:"Public catalog",activity:"Activity log",settings:"Settings",health:"Setup & health"};
  const pinterestReturn = new URLSearchParams(location.search).get("pinterest");
  let loaded = false, connectionError = "", page = ["connected","failed"].includes(pinterestReturn)?"pinterest":"overview", data, library = [], libraryNextOffset = null, detail = null, working = false, confirmation = null, search = "", filter = "all", toastTimer;
  if(["connected","failed"].includes(pinterestReturn))history.replaceState(null,"",location.pathname);
  const mediaDetails = new Map();
  let mediaObserver = null;
  let pendingCreatorJob = null;
  const creatorJobKey = "stickerdock:pending-creator-job";
  function readPendingCreatorJob() {try {return JSON.parse(sessionStorage.getItem(creatorJobKey));}catch (_) {return pendingCreatorJob;}}
  function savePendingCreatorJob(job) {pendingCreatorJob=job;try {sessionStorage.setItem(creatorJobKey,JSON.stringify(job));}catch (_) {}}
  function clearPendingCreatorJob() {pendingCreatorJob=null;try {sessionStorage.removeItem(creatorJobKey);}catch (_) {}}
  let pendingStickerEdits = new Map(), pendingPackName = null, pendingCreator = null;
  let readiness = null, packBaseline = null, pendingCategory = "", appendPack = null;
  let backgroundRefreshing = false, interactionVersion = 0, lastRefreshAt = 0;
  let pinterest = null, pinterestItems = [], pinterestBoard = null, pinterestBookmark = null;
  titles.pinterest = "Pinterest connection";
  titles.community = "User submissions";
  let community = {submissions:[],reports:[]};
  function renderCommunity() {
    return heading("User submissions", "Only opted-in snapshots appear here. Private synced libraries are not listed.", button("Refresh","community-refresh"))+
      `<div class="notice">Review every image, general-audience suitability, personal data, and redistribution rights. Creating a draft does not approve or publish it. The user can withdraw consent.</div>`+
      (community.submissions.length?`<div class="table-wrap"><table><thead><tr><th>Pack</th><th>Category / stickers</th><th>Status</th><th>Review</th></tr></thead><tbody>${community.submissions.map(s=>`<tr><td><strong>${esc(s.name)}</strong><small>${esc(s.id)}</small></td><td>${esc(s.manifest.category)} · ${s.manifest.stickers.length}</td><td>${badge(s.effective_status||s.status)}</td><td>${button("Preview","community-preview",s.id)}${s.admin_pack_id?button("Open review draft","open-pack",s.admin_pack_id):button("Create review draft","community-draft",s.id)}${!s.admin_pack_id?button("Reject","community-reject",s.id):""}</td></tr>`).join("")}</tbody></table></div>`:empty("No submissions", "Phone users can opt in after private sync and a rights confirmation."))+
      `<section class="panel"><h2>Reports & takedowns</h2>${community.reports.length?community.reports.map(r=>`<div><strong>${esc(r.catalog_id)}</strong><p>${esc(r.reason)}</p><p class="hint">Find the pack in Public catalog and unpublish if needed. Existing downloads cannot be recalled.</p>${button("Mark resolved","community-report-resolve",r.id)}</div>`).join(""):"No open reports. Support/privacy requests are also accepted at dvjjala@gmail.com."}</section>`;
  }
  function renderPinterest() {
    const status=pinterest||{status:"not_connected",message:"Connect an approved app to browse your own boards live.",oauth_ready:false};
    return heading("Pinterest connection","Live references from your own authorized account. Your meme sources stay separate.",button("Check connection","pinterest-check","","primary"))+
      (pinterestReturn==="failed"?'<div class="notice">Pinterest did not connect. Try Connect with Pinterest again, then use Check connection to verify access.</div>':"")+
      `<section class="panel"><h2>${esc(status.status.replaceAll("_"," "))}</h2><p>${esc(status.message)}</p>${status.app_id?`<p>Application ID: ${esc(status.app_id)}. Check the developer dashboard for review status.</p>`:""}<p>Required scopes: boards:read, pins:read. No advertising or write access is requested.</p><div class="toolbar">${button("Connect with Pinterest","pinterest-connect","","primary")}${button("Browse my boards","pinterest-boards")}${button("Disconnect","pinterest-disconnect")}<a class="secondary" href="https://developers.pinterest.com/apps/" target="_blank" rel="noopener">Developer dashboard ↗</a></div></section>`+
      `<section class="panel"><h3>Real application links</h3><p><a href="/about" target="_blank" rel="noopener">StickerDock website ↗</a> · <a href="/privacy" target="_blank" rel="noopener">Privacy policy ↗</a> · <a href="/support" target="_blank" rel="noopener">Support ↗</a></p><p class="hint">OAuth callback: https://stickerdock.vercel.app/api/pinterest_callback. Application approval and server app credentials are required before OAuth works.</p></section><div class="notice">This Admin has no account gate. Anyone with its URL can browse the connected account’s public boards and Pins or disconnect Pinterest. Pinterest responses are fetched live and never archived or converted into sticker packs. For real-image memes, upload your own image in Media library or use independently licensed Factory media. Phone imports stay local; signed-in users can enable private cloud backup separately.</div>`+
      `<section class="panel"><h3>${pinterestBoard?"Pins on this board":"Your boards"}</h3>${pinterestBoard?button("← Back to boards","pinterest-boards"):""}${pinterestItems.length?`<ul>${pinterestItems.map(i=>`<li>${pinterestBoard?`<a href="${esc(i.url)}" target="_blank" rel="noopener noreferrer">${esc(i.name||"Open Pin")} ↗</a>`:button(esc(i.name||"Untitled board"),"pinterest-board",i.id,"text-button")}</li>`).join("")}</ul>`:"<p>No live results loaded. Use Browse my boards after connection.</p>"}${pinterestBookmark?button("Load next page","pinterest-next"):""}</section>`;
  }
  function emptyLiveData() {return {packs:[],factory_packs:[],runs:[],requests:[],events:[],briefs:[],settings:null,catalog:{version:1,packs:[]},catalog_base:readiness?.catalog_base||"Server configuration required"};}
  async function refreshHealth() {readiness=await api("/api/admin",{action:"health"});}
  function renderHealth() {
    const checks=readiness?.checks||[{name:"Live connection",status:"pending",message:"Check the database and catalog connection."}];
    return heading("Setup & health","Read-only checks of database availability and actual content delivery. Phone and WhatsApp acceptance remain separate.",button("Check again","health-refresh","","primary"))+
      `<section class="panel"><h2>${readiness?.admin_ready?"Admin database is available":"Setup needs attention"}</h2><p>${Number(readiness?.published_packs||0)} published packs. ${readiness?.delivery_fresh?"Recent generation, review draft and catalog publication were observed.":"Content delivery needs attention; inspect the stage checks below."}</p><div class="table-wrap"><table><thead><tr><th>Check</th><th>Status</th><th>Next step</th></tr></thead><tbody>${checks.map(c=>`<tr><td>${esc(c.name)}</td><td>${badge(c.status)}</td><td>${esc(c.message)}</td></tr>`).join("")}</tbody></table></div><p>If required tables are missing, execute the reviewed <a href="https://github.com/dvajjala-ui/stickerdock/blob/main/db.sql" target="_blank" rel="noopener">database setup SQL</a> in your Supabase project, then check again.</p><p>Android release signing and actual WhatsApp acceptance are separate checks; this page does not certify them.</p></section>`;
  }
  function hasUnsavedPackEdits() {
    const form=$("pack-form");
    if(!form||!packBaseline||!$("pack-dialog").open)return false;
    if(form.elements.name.value!==packBaseline.pack.name||form.elements.creator.value!==packBaseline.pack.creator)return true;
    if($("pack-category")?.value)return true;
    return Array.from(document.querySelectorAll(".sticker-edit")).some(row=>{
      const original=packBaseline.stickers.find(s=>s.name===row.dataset.sticker);
      return original && ((row.querySelector('[data-field=label]')?.value??(original.label||""))!==(original.label||"")||
        (row.querySelector('[data-field=caption]')?.value||"")!==(original.caption_editable?(original.label||""):"")||
        row.querySelector('[data-field=tags]').value!==(original.tags||"")||row.querySelector('[data-field=remove]').checked);
    });
  }
  const colors = ["mint","lilac","peach",""];
  function toast(message, error = false) {
    $("toast").textContent = message;
    $("toast").classList.toggle("error",error);
    $("toast").hidden = false;
    clearTimeout(toastTimer); toastTimer = setTimeout(()=>$("toast").hidden=true,5500);
  }
  async function api(path, body) {
    const controller = new AbortController(), timeout = setTimeout(()=>controller.abort(),90000);
    try {
      const response = await fetch(path,{method:"POST",credentials:"same-origin",headers:{"Content-Type":"application/json"},body:JSON.stringify(body),signal:controller.signal});
      const result = await response.json().catch(()=>({}));
      if (!response.ok) throw new Error(result.error || `Request failed (${response.status})`);
      return result;
    } catch(error) {
      if(controller.signal.aborted)throw new Error("Request timed out. Refresh to check whether it finished before retrying.");
      throw error;
    } finally {clearTimeout(timeout);}
  }
  function findPack(id) {const row=data.packs.find(p=>p.id===id);if(!row)throw new Error("Pack not found");return row;}
  async function command(action, payload = {}) {
    return api("/api/admin",{action,...payload});
  }
  function connectionUi() {
    $("mode-pill").classList.toggle("live",loaded&&!connectionError);
    $("mode-pill").replaceChildren(); const dot=document.createElement("i");
    $("mode-pill").append(dot,document.createTextNode(connectionError?"Connection error":loaded?"Live backend":"Connecting…"));
    $("connection-label").textContent="No password required";
    $("pack-count").textContent=loaded?data.packs.filter(p=>p.status!=="archived").length:"—";
  }
  async function refresh(background=false) {
    const version=interactionVersion;
    let nextReadiness=readiness;
    if(!nextReadiness||page==="health"||nextReadiness.admin_ready===false)nextReadiness=await api("/api/admin",{action:"health"});
    const nextData=nextReadiness.admin_ready===false?emptyLiveData():await api("/api/admin",{action:"overview"});
    if(page==="pinterest")pinterest=await command("pinterest_status");
    if(background&&(version!==interactionVersion||!canBackgroundRefresh()))return;
    data=nextData;readiness=nextReadiness;loaded=true;connectionError="";lastRefreshAt=Date.now();
    if(readiness.admin_ready===false)page="health";
    connectionUi();render();
  }
  function canBackgroundRefresh() {
    return loaded&&!connectionError&&!document.hidden&&!working&&!Array.from(document.querySelectorAll("dialog")).some(d=>d.open)
      &&!["settings","generate","pinterest"].includes(page)&&!document.activeElement?.matches("input, textarea, select");
  }
  async function backgroundRefresh() {
    if(backgroundRefreshing||!canBackgroundRefresh()||Date.now()-lastRefreshAt<25000)return;
    backgroundRefreshing=true;
    try{await refresh(true);}catch(error){toast(error.message,true);}finally{backgroundRefreshing=false;}
  }
  async function task(fn) {
    if(working)return;
    working=true;interactionVersion++;
    document.querySelectorAll("button[data-action], .dialog-actions button[type=submit]").forEach(b=>b.disabled=true);
    try {await fn();}catch(error){toast(error.message,true);}finally{working=false;document.querySelectorAll("button[data-action], .dialog-actions button[type=submit]").forEach(b=>b.disabled=false);}
  }
  function badge(status) {return `<span class="badge ${esc(status)}">${esc(status.replaceAll("_"," "))}</span>`;}
  function heading(title,subtitle,actions="") {return `<div class="page-heading"><div><h1>${title}</h1><p>${subtitle}</p></div><div class="heading-actions">${actions}</div></div>`;}
  function button(label,action,id="",style="secondary") {return `<button class="${style}" data-action="${action}" data-id="${esc(id)}">${label}</button>`;}
  function packCard(p,index) {return `<article class="pack-card"><div class="pack-art ${colors[index%4]} ${p.art?"real-cover":""}">${p.art?`<img src="${esc(p.art)}" alt="${esc(p.name)} cover">`:`<span class="letter-cover">${esc(p.name.slice(0,1))}</span>`}</div><div class="pack-info"><h3>${esc(p.name)}</h3><p>${p.sticker_count} stickers · ${esc(p.rating === "MATURE"?"Private mature":"General audience")}</p><div class="pack-bottom">${badge(p.status)}<button class="card-action" aria-label="Open ${esc(p.name)}" data-action="open-pack" data-id="${p.id}">↗</button></div></div></article>`;}
  function empty(title,description) {return `<div class="empty"><h3>${title}</h3>${description}</div>`;}
  function renderOverview() {
    const visible=data.packs.filter(p=>p.status!=="archived"), pending=visible.filter(p=>p.status==="draft").length;
    const run=data.runs[0], queued=data.requests.filter(r=>["queued","running"].includes(r.status)).length;
    const total=visible.reduce((n,p)=>n+p.sticker_count,0),published=data.catalog.packs.length;
    const featured=visible.filter(p=>p.rating==="GENERAL"&&p.art).slice(0,2);
    return heading("Your sticker factory, at a glance.","A little creativity. A lot of reactions. Everything in one place.",button("＋ Create pack","create-pack","","primary"))+
    `<div class="stats-grid">${[["Total stickers",total,"✦","green","In your managed library"],["Sticker packs",visible.length,"▧","purple",`${pending} waiting for review`],["Published packs",published,"↗","blue","Available in the Android feed"],["Generation queue",queued,"◷","orange","Factory AI requests"]].map(([label,value,icon,color,meta])=>`<div class="stat"><div class="stat-label">${label}<span class="stat-icon ${color}">${icon}</span></div><div class="stat-value">${value}</div><div class="stat-meta">${meta}</div></div>`).join("")}</div>
    <section class="hero"><div><div class="eyebrow">THE NEXT GREAT REACTION STARTS HERE</div><h2>Turn an idea into a sticker.</h2><p>Bring your memes, add a little AI, and make packs people actually want to send.</p>${button("Open AI workbench  ↗","navigate","generate","primary")}</div><div class="hero-art ${featured.length?"":"hero-art-empty"}">${featured.length?featured.map(p=>`<img src="${esc(p.art)}" alt="${esc(p.name)} cover">`).join(""):`<span>Your real reactions appear here after you create a pack.</span>`}</div></section>
    <div class="content-grid"><section><div class="section-head"><h2>Your latest packs <small>${visible.length} packs</small></h2>${button("View all →","navigate","packs","text-button")}</div>${visible.length?`<div class="pack-grid">${visible.slice(0,6).map(packCard).join("")}</div>`:empty("Your library starts here","Migrate an existing Factory output or create a pack from your own images.")}</section><aside class="overview-side"><section class="panel"><div class="panel-title"><span class="live-dot"></span> Factory schedule ${badge(data.settings?.enabled===false?"paused":"enabled")}</div><div class="progress-caption"><span>Latest UTC run</span><strong>${run?.generated||0} / ${run?.target||data.settings?.daily_target||40}</strong></div><div class="progress-track"><progress class="progress-fill" max="${run?.target||40}" value="${run?.generated||0}"></progress></div><p class="run-summary">${esc(run?.day||"No run recorded")} · ${esc(run?.display_status||run?.status||"Awaiting first batch")}</p><div class="run-item"><span class="status-dot">✓</span><div><strong>Generation</strong><small>${esc(run?.last_result||"No generation result yet")}</small></div></div><div class="run-item"><span class="status-dot">↗</span><div><strong>${published} packs on the app</strong><small>New approved packs appear on refresh</small></div></div>${button("View Factory activity →","navigate","factory","text-button")}</section><section class="panel phone-flow"><h3>From the Factory to WhatsApp.</h3><p>Review your pack, publish it, then open StickerDock on your phone. Find the pack in Discover, preview and save it, then tap Add to WhatsApp.</p><div class="flow-steps"><span>Generate</span>→<span>Review</span>→<span>Publish</span>→<span>Send</span></div></section></aside></div>`;
  }
  function renderPacks() {
    const packs=data.packs.filter(p=>(filter==="all"||p.status===filter)&&p.name.toLowerCase().includes(search.toLowerCase()));
    const factory=data.factory_packs;
    return heading("Sticker packs","Organize drafts, review every sticker, and publish your best reactions.",button("＋ Create pack","create-pack","","primary"))+
      `<div class="toolbar"><input id="pack-search" aria-label="Search packs" placeholder="Search your packs…" value="${esc(search)}"><select id="pack-filter" aria-label="Filter pack status">${["all","draft","reviewed","published","rejected","archived"].map(s=>`<option value="${s}" ${s===filter?"selected":""}>${s==="all"?"All statuses":s[0].toUpperCase()+s.slice(1)}</option>`).join("")}</select></div>`+
      (packs.length?`<div class="pack-grid">${packs.map(packCard).join("")}</div>`:empty("No matching packs","Try another search or create your first draft."))+
      `<div class="section-head migration-head"><h2>Existing Factory output</h2><small>Migrate into editable drafts</small></div>`+
      (factory.length?`<div class="table-wrap"><table><thead><tr><th>Factory pack</th><th>Stickers</th><th>Status</th><th>Migration</th></tr></thead><tbody>${factory.map(p=>{const migrated=data.packs.find(a=>a.origin_id===p.id);return `<tr><td><strong>${esc(p.name)}</strong><small>${esc(p.day)}</small></td><td>${p.sticker_count}</td><td>${badge(p.status)}</td><td>${migrated?button("Open migrated pack","open-pack",migrated.id):button("Migrate to library ↗","migrate",p.id)}</td></tr>`;}).join("")}</tbody></table></div>`:empty("No complete Factory outputs","Finished daily archives will be available here. Migration preserves the original archive."));
  }
  function mediaName(item,index) {const name=mediaDetails.get(item.path)?.name||item.name||"";if(/^[0-9a-f]{40,64}\.(?:png|jpe?g|webp)$/i.test(name))return `Image ${index+1} · preview to identify`;return name.replace(/^File:/i,"").replace(/\.(?:png|jpe?g|webp)$/i,"").trim()||`Image ${index+1}`;}
  function renderMedia() {
    return heading("Media library","Workspace sources, original memes and completed AI results.",button("Upload new meme","upload-source","","primary")+`<a class="secondary" href="/studio/index.html" target="_blank" rel="noopener">Open canvas editor ↗</a>`)+
      `<input id="source-upload" type="file" accept="image/png,image/jpeg,image/webp" hidden><div class="notice">Uploads are visible in this shared workspace. Choose how each image may be used; public packs still require separate review.</div><label>How may this image be used?<select id="source-rights"><option value="">Choose before uploading</option><option value="USER_OWNED">I own it or have permission to make and share stickers</option><option value="REFERENCE_ONLY">Reference only · do not publish as a sticker</option></select></label>`+
      (library.length?`<div class="media-grid">${library.map((i,index)=>{
        const detail=mediaDetails.get(i.path),name=mediaName(i,index);
        return `<article class="media-card">${detail?.preview?`<img src="${esc(detail.preview)}" alt="${esc(name)}">`:`<div class="media-placeholder" data-media-preview="${esc(i.path)}">▨</div>`}<div><strong data-media-title="${esc(i.path)}">${esc(name.slice(0,40))}</strong><p>${Math.ceil(i.size/1024)} KB · ${esc(detail?`${detail.purpose} · ${detail.license}`:"Workspace source")}</p><div class="media-actions">${button("Create pack","create-pack-from-source",i.path)}${button("Preview","preview-source",i.path)}${button("Generate with AI","use-source",i.path)}</div></div></article>`;
      }).join("")}</div>${libraryNextOffset!==null?`<div class="toolbar">${button("Load more media","media-more")}</div>`:""}`:empty("Add your first source","Upload an original meme, then use it in a generation request or a new pack."));
  }
  function hydrateMedia() {
    mediaObserver?.disconnect();
    const nodes=document.querySelectorAll("[data-media-preview]");
    const load=async node=>{
      if(!node.isConnected||node.dataset.loading)return;
      node.dataset.loading="true";
      try {
        const detail=await api("/api/library",{action:"preview",path:node.dataset.mediaPreview});
        mediaDetails.set(detail.path,detail);
        const source=library.find(item=>item.path===detail.path);if(source)source.name=detail.name;
        if(!node.isConnected)return;
        const image=document.createElement("img");image.src=detail.preview;image.alt=detail.name;node.replaceWith(image);
        const card=image.closest(".media-card");
        card.querySelector("[data-media-title]").textContent=mediaName(source||detail,Math.max(0,library.indexOf(source))).slice(0,40);
        card.querySelector("p").textContent=`${Math.ceil((source?.size||0)/1024)} KB · ${detail.purpose} · ${detail.license}`;
      } catch (_) {if(node.isConnected)node.textContent="Preview unavailable";}
    };
    if(!("IntersectionObserver" in window)){nodes.forEach(load);return;}
    mediaObserver=new IntersectionObserver(entries=>entries.forEach(entry=>{
      if(entry.isIntersecting){mediaObserver.unobserve(entry.target);load(entry.target);}
    }),{rootMargin:"250px"});
    nodes.forEach(node=>mediaObserver.observe(node));
  }
  function renderGenerate() {return heading("AI workbench","Give your meme an idea. Let the Factory create a fresh reaction.")+`<div class="content-grid"><section class="panel"><h2>Create a private AI sticker</h2><form id="generate-form"><label>Source image<select name="source_path" required><option value="">Choose an image from the library</option>${library.map((i,index)=>`<option value="${esc(i.path)}">${esc(mediaName(i,index))}</option>`).join("")}</select></label><label>Creative direction<textarea name="prompt" maxlength="500" required placeholder="Turn this into a funny Hinglish reaction about finishing work on Friday. Keep the caption short."></textarea></label><div class="toolbar"><button class="secondary" type="button" data-action="prompt-template" data-id="reaction">Everyday reaction</button><button class="secondary" type="button" data-action="prompt-template" data-id="work">Office meme</button><button class="secondary" type="button" data-action="prompt-template" data-id="chai">Chai mood</button></div><p class="hint">Requests enter the private queue. The existing worker checks every 10 minutes; results return to the media library.</p><button type="submit" class="primary">✦ Queue AI sticker</button></form></section><aside><section class="panel"><h3>Make a better sticker</h3><p>Use a clear source image. Ask for one emotion and one short caption. Say who the joke is for.</p><p>Review spelling and the final crop before placing the output in a pack. Try a few creative directions and keep your favorite.</p></section><section class="panel phone-flow"><h3>Your visual pipeline</h3><p>Groq writes captions and analyzes the source. The Factory renders them into stickers. Original image synthesis requires an additional image provider.</p>${button("View request queue →","navigate","factory","text-button")}</section></aside></div>`;}
  function renderContent() {const brief=(data.briefs||[])[0];return heading("Content planner","Daily researched ideas, custom topics, and category routing for the Factory.",button("Edit discovery settings","navigate","settings","primary"))+
    `<div class="notice">The worker creates one cited brief per UTC day. News and politics become original reaction concepts; factual headlines are not copied into stickers. Adult humor is private MATURE-only.</div>`+
    (brief?`<section class="panel"><div class="section-head"><h2>${esc(brief.day)} content brief</h2>${badge(brief.source_mode||brief.status)}</div><p class="hint">${Number(brief.item_count??brief.items?.length??0)} validated items · ${(brief.categories||[]).map(c=>esc(c.replaceAll("_"," "))).join(" · ")}</p>${brief.warnings?.length?`<div class="notice">${brief.warnings.map(esc).join(" ")}</div>`:""}<div class="idea-grid">${(brief.items||[]).map(item=>`<article class="idea-card"><div>${badge(item.category)}</div><h3>${esc(item.title)}</h3><p>${esc(item.sticker_angle)}</p><a href="${esc(item.source_url)}" target="_blank" rel="noopener noreferrer">Open source ↗</a><small>${esc(item.published_at||"")}</small></article>`).join("")||empty("No researched items","Owner topics and safe evergreen reactions will still drive the next batch.")}</div></section>`:empty("No daily brief yet","The next enabled Factory invocation creates it and stores its source links privately."));}
  function dailyRunCard(r) {
    const issues={missing_batch:"The AI response omitted the sticker batch.",wrong_batch_size:"The AI returned a different number of stickers.",empty_caption:"The AI returned an empty caption.",caption_too_long:"A caption exceeded the sticker limit.",caption_unsupported_characters:"A caption used unsupported characters.",duplicate_caption:"The AI repeated a caption.",invalid_mood_or_color:"The AI used an unsupported style.",invalid_reference_index:"The AI cited an invalid reference.",incomplete_or_refused:"The AI response was incomplete or refused."};
    const issue=issues[r.groq_metrics?.validation_error];
    return `<article class="panel daily-run"><div class="section-head"><h3>${esc(r.day)}</h3>${badge(r.display_status||r.status)}</div><div class="progress-caption"><strong>${Number(r.generated)} / ${Number(r.target)} stickers</strong><span>${Number(r.attempts)} requests</span></div><progress value="${Number(r.generated)}" max="${Number(r.target)}" aria-label="Stickers generated"></progress><p>${esc(r.last_result||"Waiting for the next worker update.")}</p>${issue?`<p class="retry-hint">${esc(issue)} Saved stickers remain available for review.</p>`:""}${r.retry_at?`<p class="retry-hint">Provider cooldown ends ${esc(new Date(r.retry_at*1000).toLocaleString())}. The next scheduled worker will retry.</p>`:""}</article>`;
  }
  function renderFactory() {return heading("Factory & queue","Track scheduled generation, recover failed requests, and watch daily progress.",button("Factory settings","navigate","settings"))+
    `<div class="notice">Daily generation is ${data.settings?.enabled===false?"paused":"enabled"}. Settings are read at the next worker invocation. Publication always follows review.</div><h2>Daily runs</h2><div class="daily-runs">${data.runs.map(dailyRunCard).join("")||empty("No scheduled runs yet","The first enabled Factory run will appear here.")}</div><h2>Creator request queue</h2>`+
    (data.requests.length?`<div class="table-wrap"><table><thead><tr><th>Creative direction</th><th>Status</th><th>Attempts</th><th>Action</th></tr></thead><tbody>${data.requests.map(r=>`<tr><td><strong>${esc(r.prompt?.slice(0,80))}</strong><small>${esc(r.error_message||r.id)}</small></td><td>${badge(r.status)}</td><td>${r.attempts||0}</td><td>${r.status==="queued"?button("Cancel","queue-cancel",r.id):["failed","cancelled"].includes(r.status)?button("Retry","queue-retry",r.id):r.result_path?button("View result","preview-source",r.result_path):"Worker is processing"}</td></tr>`).join("")}</tbody></table></div>`:empty("The queue is clear","Start a request in the AI workbench. Completed results appear in your private media library."));}
  function renderCatalog() {return heading("Public catalog","These are the reviewed packs the Android app can discover.")+`<div class="notice">Factory drafts stay private. Published GENERAL packs appear in Discover. Users preview and save the packs they want. Users open a pack and confirm Add to WhatsApp.</div><section class="panel"><h3>Android catalog connection</h3><div class="catalog-url">${esc(data.catalog_base)}</div><p class="hint">The newly built APK uses the production Supabase catalog by default. Older APKs may still use the GitHub catalog.</p></section>`+(data.catalog.packs.length?`<div class="table-wrap"><table><thead><tr><th>Published pack</th><th>Creator</th><th>Audience</th><th>Management</th></tr></thead><tbody>${data.catalog.packs.map(p=>{const managed=data.packs.find(a=>"pack-"+a.id===p.id);return `<tr><td><strong>${esc(p.name)}</strong><small>${esc(p.id)}</small></td><td>${esc(p.creator)}</td><td>${badge("published")}</td><td>${managed?button("Manage pack","open-pack",managed.id):"Legacy Factory publication"}</td></tr>`;}).join("")}</tbody></table></div>`:empty("Nothing published yet","Migrate or create a pack, approve its content and rights, then publish it here."));}
  function renderSettings() {const s=data.settings||{enabled:true,daily_target:40,batch_size:5,topics:[],content_categories:["EVERYDAY"],rating:"GENERAL"};return heading("Factory settings","Tune daily discovery and generation without exposing a secret.")+`<section class="panel"><form id="settings-form"><div class="settings-row"><input id="factory-enabled" name="enabled" type="checkbox" ${s.enabled?"checked":""}><label for="factory-enabled">Enable scheduled Factory generation</label></div><div class="settings-row"><input id="discovery-enabled" name="discovery_enabled" type="checkbox" ${s.discovery_enabled!==false?"checked":""}><label for="discovery-enabled">Research a fresh content brief once per UTC day</label></div><div class="settings-row"><input id="auto-migrate" name="auto_migrate_completed" type="checkbox" ${s.auto_migrate_completed!==false?"checked":""}><label for="auto-migrate">Place each completed batch in Admin as a private draft</label></div><div class="form-grid"><label>Daily sticker target<input name="daily_target" type="number" min="3" max="100" value="${s.daily_target}" required></label><label>Stickers per request<input name="batch_size" type="number" min="3" max="10" value="${s.batch_size}" required></label></div><label>Audience for generated packs<select name="rating"><option value="GENERAL" ${s.rating!=="MATURE"?"selected":""}>General audience</option><option value="MATURE" ${s.rating==="MATURE"?"selected":""}>Private mature</option></select></label><fieldset class="category-picker"><legend>Daily content categories</legend>${contentCategories.map(c=>`<label><input type="checkbox" name="content_categories" value="${c}" ${(s.content_categories||[]).includes(c)?"checked":""}>${esc(c.replaceAll("_"," ").toLowerCase())}</label>`).join("")}</fieldset><label>Custom topic rotation · one per line<textarea name="topics" maxlength="5000">${esc((s.topics||[]).join("\n"))}</textarea></label><p class="hint">Optional format: CATEGORY | topic, for example INDIA | cricket group-chat reactions. ADULT_HUMOR requires Private mature. Use batches of 3–10; eight batches run per day. Audience changes apply next UTC day if today's generation has started. Cooldowns and request limits remain active.</p><button class="primary" type="submit">Save Factory settings</button></form></section><section class="panel"><h3>Deployment & access</h3><p>This is a shared dashboard with no password or account gate. Anyone with the website URL can view and manage this workspace. Phone-account backups remain authenticated and isolated.</p><p>Database and provider credentials stay on the server. Worker secrets: GROQ_API_KEY for captions and GROQ_TRENDS_API_KEY for daily browser search. Keys never appear here or in the APK.</p><a class="text-button" href="/studio/index.html" target="_blank" rel="noopener">Open existing Creator Studio ↗</a></section>`;}
  function renderActivity() {return heading("Activity log","A record of pack edits, review decisions, publication, and Factory changes.")+(data.events.length?`<div class="table-wrap"><table><thead><tr><th>Time</th><th>Action</th><th>Entity</th></tr></thead><tbody>${data.events.map(e=>`<tr><td>${esc(new Date(e.created_at).toLocaleString())}</td><td><strong>${esc(e.action)}</strong></td><td>${esc(e.entity_id||"Factory settings")}</td></tr>`).join("")}</tbody></table></div>`:empty("No activity yet","Changes in this workspace will appear here."));}
  const pages={community:renderCommunity,overview:renderOverview,packs:renderPacks,media:renderMedia,generate:renderGenerate,content:renderContent,factory:renderFactory,catalog:renderCatalog,settings:renderSettings,activity:renderActivity,health:renderHealth,pinterest:renderPinterest};
  function render() {
    $("content").innerHTML=loaded?pages[page]():heading(connectionError?"Workspace unavailable":"Loading your workspace",connectionError||"Reading your existing packs, Factory activity and settings…",connectionError?button("Try again","workspace-retry","","primary"):"");
    $("breadcrumb").textContent=titles[page];
    document.querySelectorAll("nav button[data-page]").forEach(b=>b.classList.toggle("active",b.dataset.page===page));connectionUi();
    if(loaded&&page==="media")hydrateMedia();else mediaObserver?.disconnect();
    if(loaded&&page==="generate"){
      const saved=readPendingCreatorJob(),form=$("generate-form");
      if(saved&&form&&saved.source&&saved.prompt){
        const picker=form.elements.source_path;
        if(!Array.from(picker.options).some(option=>option.value===saved.source))picker.add(new Option("Pending source",saved.source));
        picker.value=saved.source;form.elements.prompt.value=saved.prompt;
      }
    }
  }
  async function loadLibrary(offset=0) {
    const result=await api("/api/library",{action:"list",offset,limit:100});
    library=offset?library.concat(result.items.filter(item=>!library.some(existing=>existing.path===item.path))):result.items;
    libraryNextOffset=result.next_offset;
  }
  async function showCreatePack(selectedPath="") {
    const selected=library.find(s=>s.path===selectedPath);
    await loadLibrary();
    if(selected&&!library.some(s=>s.path===selectedPath))library.unshift(selected);
    $("create-form").reset();
    $("create-category").innerHTML=categories.map(c=>`<option>${c}</option>`).join("");
    $("create-sources").innerHTML=library.map((s,index)=>{const preview=mediaDetails.get(s.path)?.preview||s.preview;return `<label class="source-choice"><input type="checkbox" name="sources" value="${esc(s.path)}" ${s.path===selectedPath?"checked":""}>${preview?`<img src="${esc(preview)}" alt="">`:""}<span>${esc(mediaName(s,index))}</span></label>`;}).join("")||'<p class="hint">Upload an image in Media library first.</p>';
    $("create-dialog").showModal();
  }
  async function navigate(next) {if(!pages[next])return;if(readiness?.admin_ready===false)next="health";page=next;$("sidebar").classList.remove("open");if(["media","generate"].includes(next))await loadLibrary();if(next==="community")community=await command("community_list");if(next==="pinterest")pinterest=await command("pinterest_status");render();}
  function stickerSource(reference) {
    const media=reference?.media_credit||{};
    const title=media.title||media.file||reference?.source_path||"Source not recorded";
    const rights=media.license||"Rights not recorded";
    const url=typeof media.source_url==="string"&&/^https:\/\//i.test(media.source_url)?media.source_url:null;
    return `<div class="sticker-source"><small>Source: ${esc(title)} · ${esc(rights)}</small>${url?`<a href="${esc(url)}" target="_blank" rel="noopener noreferrer">Review rights ↗</a>`:""}</div>`;
  }
  async function openPack(id,offset=0) {
    if(detail?.pack.id === id && $("pack-dialog").open) {
      const form = $("pack-form");
      pendingPackName = form.elements.name.value; pendingCreator = form.elements.creator.value; pendingCategory = $("pack-category")?.value||"";
      document.querySelectorAll(".sticker-edit").forEach(row => pendingStickerEdits.set(row.dataset.sticker, {
        ...(row.querySelector('[data-field=label]')?{label:row.querySelector('[data-field=label]').value}:{}),
        ...(row.querySelector('[data-field=caption]')?{caption:row.querySelector('[data-field=caption]').value}:{}),
        tags:row.querySelector('[data-field=tags]').value,
        remove:row.querySelector('[data-field=remove]').checked
      }));
    } else { pendingStickerEdits = new Map(); pendingPackName = null; pendingCreator = null; pendingCategory = ""; packBaseline=null; }
    detail=await command("pack_detail",{id,offset});
    if(!packBaseline)packBaseline=structuredClone(detail);
    detail.stickers = detail.stickers.map(s => ({...s, ...pendingStickerEdits.get(s.name)}));
    const p=detail.pack;
    if(pendingPackName !== null) p.name = pendingPackName;
    if(pendingCreator !== null) p.creator = pendingCreator;
    const addButton=p.status!=="published"&&detail.target_packs?.some(p=>p.kind==="STATIC"&&p.count<30)?button("＋ Add stickers","add-sources",p.id):"";
    const actionButtons=p.status==="published"?button("Unpublish","unpublish",p.id):button("Archive","archive",p.id)+button("Reject","reject",p.id)+(p.status==="reviewed"?button("Publish to app ↗","publish",p.id,"primary"):p.status==="archived"?button("Restore draft","restore",p.id,"primary"):button("Approve review","review",p.id,"primary"));
    const mediaIds=(p.references||[]).map(r=>r.media_credit?.content_sha256||r.media_credit?.sha256||r.media_credit?.file).filter(Boolean);
    const diversityWarning=mediaIds.length>=3&&new Set(mediaIds).size<Math.min(3,mediaIds.length)?`<div class="notice">This pack repeats ${new Set(mediaIds).size} source image${new Set(mediaIds).size===1?"":"s"} across ${mediaIds.length} stickers. Review visual variety and caption fit before approval.</div>`:"";
    $("pack-editor").innerHTML=`<div class="editor-head"><h2>Pack details ${badge(p.status)}</h2><button class="icon-button" data-close aria-label="Close">×</button></div><form id="pack-form"><div class="form-grid"><label>Pack name<input name="name" value="${esc(p.name)}" maxlength="60" required ${p.status==="published"?"disabled":""}></label><label>Creator<input name="creator" value="${esc(p.creator)}" maxlength="60" required ${p.status==="published"?"disabled":""}></label></div><label>Pack category<select id="pack-category" ${p.status==="published"?"disabled":""}><option value="">Keep existing categories</option>${categories.map(c=>`<option value="${c}" ${pendingCategory===c?"selected":""}>${c}</option>`).join("")}</select></label><div class="section-head"><h3>${detail.stickers.length} stickers · ${esc(p.rating)}</h3><span class="count-label">Preview ${offset+1}–${Math.min(offset+5,detail.stickers.length)}</span></div>${diversityWarning}<div class="sticker-list">${detail.stickers.map((s,i)=>`<div class="sticker-edit" data-sticker="${esc(s.name)}">${s.preview?`<button type="button" class="preview-art" data-action="preview-sticker" data-id="${esc(s.name)}" aria-label="Preview full-size artwork for ${esc(s.label||"sticker")}"><img src="${esc(s.preview)}" alt=""></button>`:`<div class="count-label">${i+1}<br>${esc(s.kind)}</div>`}${s.caption_editable?`<label>Visible caption<input data-field="caption" value="${esc(s.caption??(s.label||""))}" maxlength="64" ${p.status==="published"?"disabled":""}></label>`:`<label>Label<input data-field="label" value="${esc(s.label||"")}" maxlength="80" ${p.status==="published"?"disabled":""}></label>`}<label class="tags">Tags<input data-field="tags" value="${esc(s.tags||"")}" maxlength="400" ${p.status==="published"?"disabled":""}></label>${stickerSource(p.references?.[i])}<label class="remove">Remove<input type="checkbox" data-field="remove" ${s.remove?"checked":""} ${p.status==="published"?"disabled":""}></label></div>`).join("")}</div><div class="toolbar">${offset>0?button("← Previous previews","pack-prev",p.id):""}${offset+5<detail.stickers.length?button("Next previews →","pack-next",p.id):""}${addButton}${button("Download ZIP","download-pack",p.id)}</div><p class="hint">Editing returns a pack to draft. Published packs must be unpublished first. Change up to three visible captions per save. Each redraws the static sticker from its independently reusable original photo and updates its label. Original files and Factory output remain preserved.</p><div class="editor-actions">${p.status!=="published"?'<button class="secondary" type="submit">Save changes</button>':""}${actionButtons}</div></form>`;
    if(!$("pack-dialog").open)$("pack-dialog").showModal();
  }
  function confirm(title,message,fn) {confirmation=fn;$("confirm-title").textContent=title;$("confirm-message").textContent=message;$("confirm-submit").textContent=title;$("confirm-dialog").showModal();}
  $("pack-dialog").addEventListener("cancel",e=>{if(hasUnsavedPackEdits()){e.preventDefault();confirm("Discard unsaved changes","Your changes have not been saved. Close this editor?",async()=>$("pack-dialog").close());}});
  document.addEventListener("click",async(e)=>{
    const close=e.target.closest("[data-close]");if(close){e.preventDefault();const dialog=close.closest("dialog");if(dialog.id==="pack-dialog"&&hasUnsavedPackEdits()){confirm("Discard unsaved changes","Your changes have not been saved. Close this editor?",async()=>dialog.close());return;}dialog.close();return;}
    const nav=e.target.closest("[data-page]");if(nav){await task(()=>navigate(nav.dataset.page));return;}
    const b=e.target.closest("[data-action]");if(!b)return;e.preventDefault();
    const id=b.dataset.id,action=b.dataset.action;
    if(action==="preview-sticker"){
      const sticker=detail?.stickers.find(item=>item.name===id);
      if(!sticker?.preview)return;
      $("sticker-preview-image").src=sticker.preview;
      $("sticker-preview-image").alt=sticker.label||"Sticker artwork";
      $("sticker-preview-label").textContent=sticker.label||"Inspect the rendered sticker at full size before review.";
      $("sticker-preview-dialog").showModal();
      return;
    }
    await task(async()=>{
      if(action==="navigate")return navigate(id);
      if(action==="workspace-retry"){await refresh();return;}
      if(action.startsWith("community-")) {
        if(action==="community-refresh"){community=await command("community_list");render();return;}
        if(action==="community-preview") {
          const offset=Number(b.dataset.offset||0),result=await command("community_detail",{id,offset});
          $("result-image").src=result.stickers[0]?.preview||"";
          $("result-actions").innerHTML=result.stickers.map((s,i)=>`<button class="secondary" data-action="community-image" data-id="${i}">Sticker ${offset+i+1}</button>`).join("")+
            (offset>0?`<button class="secondary" data-action="community-preview" data-id="${esc(id)}" data-offset="${Math.max(0,offset-5)}">Previous five</button>`:"")+
            (offset+5<result.submission.manifest.stickers.length?`<button class="secondary" data-action="community-preview" data-id="${esc(id)}" data-offset="${offset+5}">Next five</button>`:"");
          $("result-dialog").dataset.communityImages=JSON.stringify(result.stickers.map(s=>s.preview));
          if(!$("result-dialog").open)$("result-dialog").showModal();return;
        }
        if(action==="community-image"){$("result-image").src=JSON.parse($("result-dialog").dataset.communityImages)[Number(id)];return;}
        if(action==="community-draft"){const result=await command("community_draft",{id});await refresh();community=await command("community_list");return openPack(result.id);}
        if(action==="community-reject"||action==="community-report-resolve") {
          confirm("Review decision",action==="community-reject"?"Reject this submission? The user's private library is preserved.":"Confirm you investigated and handled this report?",async()=>{await command(action==="community-reject"?"community_reject":"report_resolve",{id});community=await command("community_list");render();});return;
        }
      }
      if(action.startsWith("pinterest-")) {
        if(action==="pinterest-check"){pinterest=await command("pinterest_status",{check:true});render();return;}
        if(action==="pinterest-connect"){const result=await command("pinterest_connect");if(!result.url.startsWith("https://www.pinterest.com/oauth/?"))throw new Error("Invalid authorization destination");window.location.assign(result.url);return;}
        if(action==="pinterest-disconnect"){confirm("Disconnect Pinterest","Remove the server's saved Pinterest credential? You can also revoke access in Pinterest account settings.",async()=>{await command("pinterest_disconnect");pinterestItems=[];pinterest=null;pinterestBoard=null;pinterestBookmark=null;render();toast("Pinterest disconnected.");});return;}
        if(action!=="pinterest-next"){pinterestBoard=action==="pinterest-board"?id:null;pinterestBookmark=null;}
        const result=await command("pinterest_browse",{board_id:pinterestBoard||undefined,bookmark:pinterestBookmark||undefined});pinterestItems=result.items;pinterestBookmark=result.bookmark;render();return;
      }
      if(action==="health-refresh"){await refreshHealth();await refresh();return;}
      if(action==="result-pack"){$("result-dialog").close();return showCreatePack(id);}
      if(action==="result-generate"){$("result-dialog").close();await navigate("generate");document.querySelector('#generate-form select').value=id;return;}
      if(action==="add-sources") {
        if(hasUnsavedPackEdits())throw new Error("Save your current changes before adding more stickers.");
        await loadLibrary();appendPack={id,version:detail.pack.version};
        const targets=detail.target_packs||[];
        $("add-target").innerHTML=targets.filter(p=>p.kind==="STATIC"&&p.count<30).map(p=>`<option value="${esc(p.id)}">${esc(p.name)} · ${30-p.count} spaces</option>`).join("");
        $("add-source-choices").innerHTML=library.map(source=>`<label class="source-choice"><input type="checkbox" name="sources" value="${esc(source.path)}"><span>${esc(source.name||source.path.slice(-20))}</span></label>`).join("")||"Upload images in Media library first.";
        $("add-sources-dialog").showModal();return;
      }
      if(action==="create-pack")return showCreatePack();
      if(action==="create-pack-from-source")return showCreatePack(id);
      if(action==="open-pack")return openPack(id);
      if(action==="migrate") {const result=await command("migrate_factory",{id});await refresh();toast("Factory output migrated. Original archive preserved.");return openPack(result.id);}
      if(action==="pack-next"||action==="pack-prev")return openPack(id,Math.max(0,detail.offset+(action==="pack-next"?5:-5)));
      if(action==="upload-source"){if(!$("source-rights").value)throw new Error("Choose reuse rights before uploading.");return $("source-upload").click();}
      if(action==="media-more"){if(libraryNextOffset!==null){await loadLibrary(libraryNextOffset);render();}return;}
      if(action==="preview-source") {
        const image=(await api("/api/library",{action:"fetch",path:id})).image;
        let item=library.find(s=>s.path===id);if(!item){item={path:id,name:"Generated AI result",size:0};library.unshift(item);}item.preview=image;
        $("result-image").src=image;$("result-actions").innerHTML=button("Create a pack","result-pack",id,"primary")+button("Generate another version","result-generate",id);$("result-dialog").showModal();return;
      }
      if(action==="use-source") {const selected=library.find(item=>item.path===id);await navigate("generate");if(selected&&!library.some(item=>item.path===id)){library.unshift(selected);render();}document.querySelector('#generate-form select').value=id;return;}
      if(action==="prompt-template") {document.querySelector('#generate-form textarea').value={reaction:"Create a short, funny Hinglish reaction to this image. Capture one clear emotion. Keep the caption under six words.",work:"Make this an office reaction meme about Monday meetings. Keep it friendly, original and readable on a phone.",chai:"Turn this into a playful Hinglish chai-time reaction. Use one short original caption and leave room around the subject."}[id];return;}
      if(action==="download-pack") {const result=await command("pack_download",{id});const a=document.createElement("a");a.href=result.url;a.rel="noopener";a.target="_blank";a.click();return;}
      if(action==="queue-cancel"||action==="queue-retry"){await command("queue_action",{id,operation:action==="queue-retry"?"retry":"cancel"});await refresh();toast("Queue updated.");return;}
      if(["review","archive","restore","reject","publish","unpublish"].includes(action)) {
        if(hasUnsavedPackEdits())throw new Error("Save changes before reviewing or changing pack status.");
        const p=findPack(id),status={review:"reviewed",archive:"archived",restore:"draft",reject:"rejected"}[action];
        const explanation={review:"Confirm you reviewed every sticker and its source rights. All stickers must pass public pack validation.",archive:"Archive this pack from the working library. Its files remain stored and the draft can be restored.",restore:"Return this archived pack to the draft library.",reject:"Mark this draft as rejected. The original files are preserved.",publish:"Publish this reviewed GENERAL pack to the production catalog. It will be visible in the Android Factory feed.",unpublish:"Remove this pack from the public catalog. Existing phone downloads remain on those devices; the private original is preserved."}[action];
        confirm(action[0].toUpperCase()+action.slice(1)+" pack",explanation,async()=>{await command(action==="publish"||action==="unpublish"?action:"pack_status",{id,version:p.version,status});$("pack-dialog").close();await refresh();toast("Pack updated.");});
      }
    });
  });
  document.addEventListener("submit",(e)=>{
    if(e.target.id==="pack-form") {e.preventDefault();task(async()=>{const form=new FormData(e.target),stickers=Array.from(document.querySelectorAll(".sticker-edit")).map(row=>({name:row.dataset.sticker,...(row.querySelector('[data-field=label]')?{label:row.querySelector('[data-field=label]').value}:{}),...(row.querySelector('[data-field=caption]')?{caption:row.querySelector('[data-field=caption]').value}:{}),tags:row.querySelector('[data-field=tags]').value,remove:row.querySelector('[data-field=remove]').checked}));await command("pack_save",{id:detail.pack.id,version:detail.pack.version,name:form.get("name"),creator:form.get("creator"),category:$("pack-category").value||undefined,stickers});$("pack-dialog").close();await refresh();toast("Pack saved as a private draft.");});}
    if(e.target.id==="generate-form") {e.preventDefault();task(async()=>{
      const form=new FormData(e.target),source=String(form.get("source_path")||""),prompt=String(form.get("prompt")||"").trim();
      let pending=readPendingCreatorJob();
      if(!pending||pending.source!==source||pending.prompt!==prompt)pending={id:crypto.randomUUID(),source,prompt};
      savePendingCreatorJob(pending);
      await command("queue_create",{source_path:source,prompt,request_id:pending.id});
      await refresh();await navigate("factory");clearPendingCreatorJob();toast("AI request queued for the Factory worker.");
    });}
    if(e.target.id==="settings-form") {e.preventDefault();task(async()=>{const form=new FormData(e.target),selected=form.getAll("content_categories"),rating=form.get("rating");if(!selected.length)throw new Error("Choose at least one content category.");if(selected.includes("ADULT_HUMOR")&&rating!=="MATURE")throw new Error("Adult humor requires Private mature.");await command("settings_save",{enabled:form.has("enabled"),discovery_enabled:form.has("discovery_enabled"),auto_migrate_completed:form.has("auto_migrate_completed"),daily_target:Number(form.get("daily_target")),batch_size:Number(form.get("batch_size")),rating,content_categories:selected,topics:String(form.get("topics")).split("\n").map(t=>t.trim()).filter(Boolean)});await refresh();toast("Settings saved for the next Factory run.");});}
  });
  $("create-form").addEventListener("submit",e=>{e.preventDefault();task(async()=>{const form=new FormData(e.target),sources=form.getAll("sources");if(!sources.length||sources.length>30)throw new Error("Choose 1–30 source images.");const result=await command("pack_create",{name:form.get("name"),creator:form.get("creator"),category:form.get("category"),sources});$("create-dialog").close();await refresh();await openPack(result.id);toast("Private draft created.");});});
  $("add-sources-form").addEventListener("submit",e=>{e.preventDefault();task(async()=>{
    const form=new FormData(e.target),sources=form.getAll("sources");if(!sources.length||sources.length>30)throw new Error("Choose 1–30 images.");
    await command("pack_add_sources",{...appendPack,target_pack_id:form.get("target_pack_id"),sources});
    const id=appendPack.id;$("add-sources-dialog").close();$("pack-dialog").close();await refresh();await openPack(id);toast("Stickers added. Review the updated draft before publishing.");
  });});
  $("confirm-form").addEventListener("submit",e=>{e.preventDefault();const fn=confirmation;confirmation=null;$("confirm-dialog").close();if(fn)task(fn);});
  document.addEventListener("change",e=>{if(e.target.id==="pack-filter"){filter=e.target.value;render();}if(e.target.id==="source-upload")task(async()=>{const file=e.target.files[0];if(!file)return;if(!["image/png","image/jpeg","image/webp"].includes(file.type)||file.size>3_000_000)throw new Error("Choose a static PNG, JPEG or WebP under 3 MB.");const rights=$("source-rights").value;if(!rights)throw new Error("Choose reuse rights before uploading.");const image=await new Promise((resolve,reject)=>{const r=new FileReader();r.onload=()=>resolve(r.result);r.onerror=()=>reject(new Error("Cannot read image"));r.readAsDataURL(file);});const saved=await command("source_upload",{image,title:file.name,rights});mediaDetails.delete(saved.path);await loadLibrary();render();toast("Image saved in the Factory inbox.");});});
  document.addEventListener("input",e=>{if(e.target.id==="pack-search"){const pos=e.target.selectionStart;search=e.target.value;render();$("pack-search").focus();$("pack-search").setSelectionRange(pos,pos);}});
  $("refresh-button").addEventListener("click",()=>task(async()=>{if(["media","generate"].includes(page))await loadLibrary();await refresh();toast("Factory data refreshed.");}));
  $("menu-button").addEventListener("click",()=>$("sidebar").classList.toggle("open"));
  document.addEventListener("visibilitychange",backgroundRefresh);
  setInterval(backgroundRefresh,30000);
  data=emptyLiveData();library=[];render();
  refresh().catch(error=>{connectionError=error.message;render();});
})();
