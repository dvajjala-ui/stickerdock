/* Owner-only admin UI. Preview records are deliberately separate from live data. */
(() => {
  "use strict";
  const $ = (id) => document.getElementById(id);
  const esc = (value) => String(value ?? "").replace(/[&<>"']/g, (c) => ({"&":"&amp;","<":"&lt;",">":"&gt;","\"":"&quot;","'":"&#39;"}[c]));
  const categories = ["GENERAL","REACTIONS","HINGLISH","MOVIES","NEWS","ANIMALS","LOVE","WORK","FESTIVALS","OTHER"];
  const contentCategories = ["TRENDING","POLITICS","CREATIVE","MOVIES","SERIES","INDIA","SPORTS","TECHNOLOGY","EVERYDAY","ADULT_HUMOR"];
  const titles = {overview:"Overview",packs:"Sticker packs",media:"Media library",generate:"AI workbench",content:"Content planner",factory:"Factory & queue",catalog:"Public catalog",activity:"Activity log",settings:"Settings",health:"Setup & health"};
  let token = "", page = "overview", data, library = [], detail = null, working = false, confirmation = null, search = "", filter = "all", toastTimer;
  let pendingStickerEdits = new Map(), pendingPackName = null, pendingCreator = null;
  let readiness = null, packBaseline = null, pendingCategory = "", appendPack = null;
  let backgroundRefreshing = false, interactionVersion = 0, lastRefreshAt = 0;
  function emptyLiveData() {return {packs:[],factory_packs:[],runs:[],requests:[],events:[],briefs:[],settings:null,catalog:{version:1,packs:[]},catalog_base:readiness?.catalog_base||"Server configuration required"};}
  async function refreshHealth() {readiness=await api("/api/admin",{action:"health"});}
  function renderHealth() {
    const checks=readiness?.checks||[{name:"Live connection",status:"pending",message:"Connect your owner token to check the real database and catalog."}];
    return heading("Setup & health","Read-only checks of the database and public catalog. Phone and worker acceptance remain separate.",button("Check again","health-refresh","","primary"))+
      `<section class="panel"><h2>${readiness?.admin_ready?"Admin database is available":"Setup needs attention"}</h2><p>${Number(readiness?.published_packs||0)} published packs. A successful generation run does not automatically publish content.</p><div class="table-wrap"><table><thead><tr><th>Check</th><th>Status</th><th>Next step</th></tr></thead><tbody>${checks.map(c=>`<tr><td>${esc(c.name)}</td><td>${badge(c.status)}</td><td>${esc(c.message)}</td></tr>`).join("")}</tbody></table></div><p>If required tables are missing, execute the reviewed <a href="https://github.com/dvajjala-ui/stickerdock/blob/main/db.sql" target="_blank" rel="noopener">database setup SQL</a> in your Supabase project, then check again.</p><p>Android release signing, hosted worker configuration and actual WhatsApp acceptance are separate checks; this page does not certify them.</p></section>`;
  }
  function hasUnsavedPackEdits() {
    const form=$("pack-form");
    if(!form||!packBaseline||!$("pack-dialog").open)return false;
    if(form.elements.name.value!==packBaseline.pack.name||form.elements.creator.value!==packBaseline.pack.creator)return true;
    if($("pack-category")?.value)return true;
    return Array.from(document.querySelectorAll(".sticker-edit")).some(row=>{
      const original=packBaseline.stickers.find(s=>s.name===row.dataset.sticker);
      return original && (row.querySelector('[data-field=label]').value!==(original.label||"")||row.querySelector('[data-field=tags]').value!==(original.tags||"")||row.querySelector('[data-field=remove]').checked);
    });
  }
  const colors = ["mint","lilac","peach",""];
  function sticker(caption, variant = 0) {
    const palette = ["#e3b2ee", "#f5bf71", "#b6d589", "#b8cbea"][variant % 4];
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200"><path d="M38 91 31 42 76 65Q100 50 126 65L168 42 161 94Q171 146 139 158H61Q26 147 38 91Z" fill="${palette}" stroke="white" stroke-width="9" stroke-linejoin="round"/><path d="M60 110q12-18 23 0m35 0q12-18 23 0" fill="none" stroke="#333749" stroke-width="6" stroke-linecap="round"/><path d="m95 118 8 7 8-7m-19 18q10 13 20 0" fill="none" stroke="#333749" stroke-width="5" stroke-linecap="round"/><rect x="10" y="147" width="180" height="32" rx="9" fill="white"/><text x="100" y="170" text-anchor="middle" font-size="16" font-family="Arial,sans-serif" font-weight="900" fill="#383447">${esc(caption.slice(0,20))}</text></svg>`;
    return "data:image/svg+xml;charset=utf-8," + encodeURIComponent(svg);
  }
  const illustrations = [sticker("BAS KAR YAAR",0),sticker("CHAI FIRST",1),sticker("MOOD HAI",2),sticker("OK BOSS",3)];
  const day = () => new Date().toISOString().slice(0,10);
  const uid = () => crypto.randomUUID();
  function previewData() {
    const names = ["Desi daily reactions","Office survival kit","Chai & chill","Weekend energy","Little cat moods","Everyday Hinglish"];
    const packs = names.map((name,i) => ({id:uid(),name,creator:"StickerDock AI Factory",rating:"GENERAL",status:["draft","published","reviewed","draft","published","rejected"][i],sticker_count:20,version:1,sha256:"preview",created_at:new Date().toISOString(),art:illustrations[i%4]}));
    return {packs,factory_packs:[{id:uid(),day:day(),name:"Daily " + day(),sticker_count:40,status:"pending",rating:"GENERAL",creator:"StickerDock AI Factory"}],runs:[{day:day(),generated:25,target:40,status:"running",last_result:"Five stickers generated",attempts:5}],requests:[],events:[],settings:{enabled:true,daily_target:40,batch_size:5,discovery_enabled:true,auto_migrate_completed:true,rating:"GENERAL",content_categories:["TRENDING","CREATIVE","MOVIES","SERIES","INDIA","TECHNOLOGY","EVERYDAY"],topics:["INDIA | Everyday desi reactions in Hinglish","CREATIVE | Office, chai and weekend memes"]},briefs:[{day:day(),status:"ready",source_mode:"groq_browser_search",item_count:3,categories:["TRENDING","MOVIES","INDIA"],items:[{category:"TRENDING",title:"A timely public story becomes a light reaction",sticker_angle:"When the group chat moves faster than the news",source_url:"https://example.com/story",published_at:day()},{category:"MOVIES",title:"A newly announced release excites fans",sticker_angle:"Waiting for opening night",source_url:"https://example.com/film",published_at:day()},{category:"INDIA",title:"A current India culture moment",sticker_angle:"Desi group chat energy",source_url:"https://example.com/india",published_at:day()}],warnings:[]}],catalog:{version:1,packs:packs.filter(p=>p.status==="published").map(p=>({id:"pack-"+p.id,name:p.name,creator:p.creator,rating:"GENERAL",file:p.id+".zip"}))},catalog_base:"Connect your backend to see the production catalog URL"};
  }
  data = previewData();
  library = illustrations.map((preview,i) => ({path:"preview/source-"+i+".png",name:["Original cat reaction","Chai time illustration","Weekend mood","Office reaction"][i],size:18000,preview}));
  const live = () => Boolean(token);
  function toast(message, error = false) {
    $("toast").textContent = message;
    $("toast").classList.toggle("error",error);
    $("toast").hidden = false;
    clearTimeout(toastTimer); toastTimer = setTimeout(()=>$("toast").hidden=true,5500);
  }
  async function api(path, body) {
    if (!token) throw new Error("Connect your owner token first.");
    const controller = new AbortController(), timeout = setTimeout(()=>controller.abort(),90000);
    try {
      const response = await fetch(path,{method:"POST",headers:{"Content-Type":"application/json","X-StickerDock-Token":token},body:JSON.stringify(body),signal:controller.signal});
      const result = await response.json().catch(()=>({}));
      if (!response.ok) throw new Error(result.error || `Request failed (${response.status})`);
      return result;
    } catch(error) {
      if(controller.signal.aborted)throw new Error("Request timed out. Refresh to check whether it finished before retrying.");
      throw error;
    } finally {clearTimeout(timeout);}
  }
  function event(action,entity) {
    data.events.unshift({id:uid(),action,entity_id:entity,created_at:new Date().toISOString()});
  }
  function findPack(id) {const row=data.packs.find(p=>p.id===id);if(!row)throw new Error("Pack not found");return row;}
  async function command(action, payload = {}) {
    if (live()) return api("/api/admin",{action,...payload});
    event(action,payload.id);
    const row = payload.id ? data.packs.find(p=>p.id===payload.id) : null;
    if(action==="migrate_factory") {
      let existing=data.packs.find(p=>p.origin_id===payload.id);
      if(!existing) {const source=data.factory_packs.find(p=>p.id===payload.id);existing={...source,id:uid(),origin_id:payload.id,status:"draft",version:1,art:illustrations[0]};data.packs.unshift(existing);}
      return {id:existing.id};
    }
    if(action==="pack_create") {const p={id:uid(),name:payload.name,creator:payload.creator,rating:"GENERAL",status:"draft",sticker_count:payload.sources.length,version:1,art:library.find(i=>i.path===payload.sources[0])?.preview};data.packs.unshift(p);return {id:p.id};}
    if(action==="pack_status") {row.status=payload.status;row.version++;}
    if(action==="publish") {if(row.status!=="reviewed")throw new Error("Review the pack first");row.status="published";row.version++;data.catalog.packs=data.catalog.packs.filter(p=>p.id!=="pack-"+row.id);data.catalog.packs.unshift({id:"pack-"+row.id,name:row.name,creator:row.creator,rating:"GENERAL"});}
    if(action==="unpublish") {row.status="draft";row.version++;data.catalog.packs=data.catalog.packs.filter(p=>p.id!=="pack-"+row.id);}
    if(action==="settings_save") data.settings={...payload};
    if(action==="queue_action") {const r=data.requests.find(r=>r.id===payload.id);r.status=payload.operation==="retry"?"queued":"cancelled";}
    if(action==="pack_save") {Object.assign(row,{name:payload.name,creator:payload.creator,status:"draft",version:row.version+1});if(row.demoStickers)row.demoStickers=row.demoStickers.filter(s=>!payload.stickers.find(e=>e.name===s.name)?.remove).map(s=>({...s,...payload.stickers.find(e=>e.name===s.name)}));row.sticker_count=row.demoStickers?.length||row.sticker_count;}
    if(action==="pack_detail") {const p=findPack(payload.id);const stickers=p.demoStickers||(p.demoStickers=Array.from({length:p.sticker_count},(_,i)=>({name:uid()+".webp",label:["Bas kar yaar","Chai first","Weekend mood","Okay boss"][i%4],tags:"reaction, hinglish",kind:"STATIC",preview:illustrations[i%4]})));return {pack:p,stickers,offset:payload.offset||0,preview_count:5};}
    return {saved:true};
  }
  function connectionUi() {
    $("preview-banner").hidden=live();
    $("mode-pill").classList.toggle("live",live());
    $("mode-pill").replaceChildren(); const dot=document.createElement("i");$("mode-pill").append(dot,document.createTextNode(live()?"Live backend":"Preview data"));
    $("connection-label").textContent=live()?"Connected · owner access":"Preview mode";
    $("connect-button").textContent=live()?"Connection":"Connect backend";
    $("pack-count").textContent=data.packs.filter(p=>p.status!=="archived").length;
  }
  async function refresh(background=false) {
    const version=interactionVersion, connection=token;
    let nextData=data, nextReadiness=readiness;
    if(live()) {
      if(page==="health"||nextReadiness?.admin_ready===false)nextReadiness=await api("/api/admin",{action:"health"});
      nextData=nextReadiness?.admin_ready===false?emptyLiveData():await api("/api/admin",{action:"overview"});
    }
    if(background&&(version!==interactionVersion||connection!==token||!canBackgroundRefresh()))return;
    data=nextData;readiness=nextReadiness;lastRefreshAt=Date.now();
    if(live()&&readiness?.admin_ready===false)page="health";
    connectionUi();render();
  }
  function canBackgroundRefresh() {
    return live()&&!document.hidden&&!working&&!Array.from(document.querySelectorAll("dialog")).some(d=>d.open)
      &&!["settings","generate"].includes(page)&&!document.activeElement?.matches("input, textarea, select");
  }
  async function backgroundRefresh() {
    if(backgroundRefreshing||!canBackgroundRefresh()||Date.now()-lastRefreshAt<25000)return;
    backgroundRefreshing=true;
    try{await refresh(true);}catch(error){if(live())toast(error.message,true);}finally{backgroundRefreshing=false;}
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
  function packCard(p,index) {return `<article class="pack-card"><div class="pack-art ${colors[index%4]}">${p.art?`<img src="${esc(p.art)}" alt=""><img src="${esc(illustrations[(index+1)%4])}" alt="">`:`<span class="letter-cover">${esc(p.name.slice(0,1))}</span>`}</div><div class="pack-info"><h3>${esc(p.name)}</h3><p>${p.sticker_count} stickers · ${esc(p.rating === "MATURE"?"Private mature":"General audience")}</p><div class="pack-bottom">${badge(p.status)}<button class="card-action" aria-label="Open ${esc(p.name)}" data-action="open-pack" data-id="${p.id}">↗</button></div></div></article>`;}
  function empty(title,description) {return `<div class="empty"><h3>${title}</h3>${description}</div>`;}
  function renderOverview() {
    const visible=data.packs.filter(p=>p.status!=="archived"), pending=visible.filter(p=>p.status==="draft").length;
    const run=data.runs[0], queued=data.requests.filter(r=>["queued","running"].includes(r.status)).length;
    const total=visible.reduce((n,p)=>n+p.sticker_count,0),published=data.catalog.packs.length;
    return heading("Your sticker factory, at a glance.","A little creativity. A lot of reactions. Everything in one place.",button("＋ Create pack","create-pack","","primary"))+
    `<div class="stats-grid">${[["Total stickers",total,"✦","green","In your managed library"],["Sticker packs",visible.length,"▧","purple",`${pending} waiting for review`],["Published packs",published,"↗","blue","Available in the Android feed"],["Generation queue",queued,"◷","orange","Private AI requests"]].map(([label,value,icon,color,meta])=>`<div class="stat"><div class="stat-label">${label}<span class="stat-icon ${color}">${icon}</span></div><div class="stat-value">${value}</div><div class="stat-meta">${meta}</div></div>`).join("")}</div>
    <section class="hero"><div><div class="eyebrow">THE NEXT GREAT REACTION STARTS HERE</div><h2>Turn an idea into a sticker.</h2><p>Bring your memes, add a little AI, and make packs people actually want to send.</p>${button("Open AI workbench  ↗","navigate","generate","primary")}</div><div class="hero-art"><img src="${illustrations[0]}" alt="Original cat reaction illustration"><img src="${illustrations[1]}" alt="Chai first sticker illustration"></div></section>
    <div class="content-grid"><section><div class="section-head"><h2>Your latest packs <small>${visible.length} packs</small></h2>${button("View all →","navigate","packs","text-button")}</div>${visible.length?`<div class="pack-grid">${visible.slice(0,6).map(packCard).join("")}</div>`:empty("Your library starts here","Migrate an existing Factory output or create a pack from your own images.")}</section><aside class="overview-side"><section class="panel"><div class="panel-title"><span class="live-dot"></span> Factory today ${badge(data.settings?.enabled===false?"paused":"running")}</div><div class="progress-caption"><span>Daily generation</span><strong>${run?.generated||0} / ${run?.target||data.settings?.daily_target||40}</strong></div><div class="progress-track"><progress class="progress-fill" max="${run?.target||40}" value="${run?.generated||0}"></progress></div><p class="run-summary">${esc(run?.day||"No run recorded")} · ${esc(run?.status||"Awaiting first batch")}</p><div class="run-item"><span class="status-dot">✓</span><div><strong>Generation</strong><small>${esc(run?.last_result||"No generation result yet")}</small></div></div><div class="run-item"><span class="status-dot">↗</span><div><strong>${published} packs on the app</strong><small>New approved packs appear on refresh</small></div></div>${button("View Factory activity →","navigate","factory","text-button")}</section><section class="panel phone-flow"><h3>From the Factory to WhatsApp.</h3><p>Review your pack, publish it, then open StickerDock on your phone. Approved packs enter the library automatically; open a pack and tap Add to WhatsApp.</p><div class="flow-steps"><span>Generate</span>→<span>Review</span>→<span>Publish</span>→<span>Send</span></div></section></aside></div>`;
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
  function renderMedia() {return heading("Media library","Your private sources, original memes, and completed AI results.",button("Upload new meme","upload-source","","primary")+`<a class="secondary" href="/studio/index.html" target="_blank" rel="noopener">Open canvas editor ↗</a>`)+`<input id="source-upload" type="file" accept="image/png,image/jpeg,image/webp" hidden><div class="notice">Upload images you own or have permission to use. New sources stay private; source rights are checked before publication.</div>`+(library.length?`<div class="media-grid">${library.map(i=>`<article class="media-card">${i.preview?`<img src="${esc(i.preview)}" alt="${esc(i.name)}">`:`<div class="media-placeholder">▨</div>`}<div><strong>${esc(i.name?.slice(0,30)||i.path.slice(-20))}</strong><p>${Math.ceil(i.size/1024)} KB · Private source</p>${button("Preview","preview-source",i.path)} ${button("Generate with AI","use-source",i.path,"text-button")}</div></article>`).join("")}</div>`:empty("Add your first source","Upload an original meme, then use it in a generation request or a new pack."));}
  function renderGenerate() {return heading("AI workbench","Give your meme an idea. Let the Factory create a fresh reaction.")+`<div class="content-grid"><section class="panel"><h2>Create a private AI sticker</h2><form id="generate-form"><label>Source image<select name="source_path" required><option value="">Choose an image from the library</option>${library.map(i=>`<option value="${esc(i.path)}">${esc(i.name||i.path.slice(-20))}</option>`).join("")}</select></label><label>Creative direction<textarea name="prompt" maxlength="500" required placeholder="Turn this into a funny Hinglish reaction about finishing work on Friday. Keep the caption short."></textarea></label><div class="toolbar"><button class="secondary" type="button" data-action="prompt-template" data-id="reaction">Everyday reaction</button><button class="secondary" type="button" data-action="prompt-template" data-id="work">Office meme</button><button class="secondary" type="button" data-action="prompt-template" data-id="chai">Chai mood</button></div><p class="hint">Requests enter the private queue. The existing worker checks every 10 minutes; results return to the media library.</p><button type="submit" class="primary">✦ Queue AI sticker</button></form></section><aside><section class="panel"><h3>Make a better sticker</h3><p>Use a clear source image. Ask for one emotion and one short caption. Say who the joke is for.</p><p>Review spelling and the final crop before placing the output in a pack. Try a few creative directions and keep your favorite.</p></section><section class="panel phone-flow"><h3>Your visual pipeline</h3><p>Groq writes captions and analyzes the source. The Factory renders them into stickers. Original image synthesis requires an additional image provider.</p>${button("View request queue →","navigate","factory","text-button")}</section></aside></div>`;}
  function renderContent() {const brief=(data.briefs||[])[0];return heading("Content planner","Daily researched ideas, custom topics, and category routing for the Factory.",button("Edit discovery settings","navigate","settings","primary"))+
    `<div class="notice">The worker creates one cited brief per UTC day. News and politics become original reaction concepts; factual headlines are not copied into stickers. Adult humor is private MATURE-only.</div>`+
    (brief?`<section class="panel"><div class="section-head"><h2>${esc(brief.day)} content brief</h2>${badge(brief.source_mode||brief.status)}</div><p class="hint">${Number(brief.item_count??brief.items?.length??0)} validated items · ${(brief.categories||[]).map(c=>esc(c.replaceAll("_"," "))).join(" · ")}</p>${brief.warnings?.length?`<div class="notice">${brief.warnings.map(esc).join(" ")}</div>`:""}<div class="idea-grid">${(brief.items||[]).map(item=>`<article class="idea-card"><div>${badge(item.category)}</div><h3>${esc(item.title)}</h3><p>${esc(item.sticker_angle)}</p><a href="${esc(item.source_url)}" target="_blank" rel="noopener noreferrer">Open source ↗</a><small>${esc(item.published_at||"")}</small></article>`).join("")||empty("No researched items","Owner topics and safe evergreen reactions will still drive the next batch.")}</div></section>`:empty("No daily brief yet","The next enabled Factory invocation creates it and stores its source links privately."));}
  function renderFactory() {return heading("Factory & queue","Track scheduled generation, recover failed requests, and watch daily progress.",button("Factory settings","navigate","settings"))+
    `<div class="notice">Daily generation is ${data.settings?.enabled===false?"paused":"enabled"}. Settings are read at the next worker invocation. Publication always follows review.</div><h2>Daily runs</h2><div class="table-wrap"><table><thead><tr><th>Day</th><th>Progress</th><th>Status</th><th>Requests</th><th>Last result</th></tr></thead><tbody>${data.runs.map(r=>`<tr><td>${esc(r.day)}</td><td>${r.generated} / ${r.target}</td><td>${badge(r.status)}</td><td>${r.attempts}</td><td>${esc(r.last_result||"—")}</td></tr>`).join("")}</tbody></table></div><h2>Creator request queue</h2>`+
    (data.requests.length?`<div class="table-wrap"><table><thead><tr><th>Creative direction</th><th>Status</th><th>Attempts</th><th>Action</th></tr></thead><tbody>${data.requests.map(r=>`<tr><td><strong>${esc(r.prompt?.slice(0,80))}</strong><small>${esc(r.error_message||r.id)}</small></td><td>${badge(r.status)}</td><td>${r.attempts||0}</td><td>${r.status==="queued"?button("Cancel","queue-cancel",r.id):["failed","cancelled"].includes(r.status)?button("Retry","queue-retry",r.id):r.result_path?button("View result","preview-source",r.result_path):"Worker is processing"}</td></tr>`).join("")}</tbody></table></div>`:empty("The queue is clear","Start a request in the AI workbench. Completed results appear in your private media library."));}
  function renderCatalog() {return heading("Public catalog","These are the reviewed packs the Android app can discover.")+`<div class="notice">Factory drafts stay private. Published GENERAL packs enter the Android library automatically. Users open a pack and confirm Add to WhatsApp.</div><section class="panel"><h3>Android catalog connection</h3><div class="catalog-url">${esc(data.catalog_base)}</div><p class="hint">The newly built APK uses the production Supabase catalog by default. Older APKs may still use the GitHub catalog.</p></section>`+(data.catalog.packs.length?`<div class="table-wrap"><table><thead><tr><th>Published pack</th><th>Creator</th><th>Audience</th><th>Management</th></tr></thead><tbody>${data.catalog.packs.map(p=>{const managed=data.packs.find(a=>"pack-"+a.id===p.id);return `<tr><td><strong>${esc(p.name)}</strong><small>${esc(p.id)}</small></td><td>${esc(p.creator)}</td><td>${badge("published")}</td><td>${managed?button("Manage pack","open-pack",managed.id):"Legacy Factory publication"}</td></tr>`;}).join("")}</tbody></table></div>`:empty("Nothing published yet","Migrate or create a pack, approve its content and rights, then publish it here."));}
  function renderSettings() {const s=data.settings||{enabled:true,daily_target:40,batch_size:5,topics:[],content_categories:["EVERYDAY"],rating:"GENERAL"};return heading("Factory settings","Tune daily discovery and generation without exposing a secret.")+`<section class="panel"><form id="settings-form"><div class="settings-row"><input id="factory-enabled" name="enabled" type="checkbox" ${s.enabled?"checked":""}><label for="factory-enabled">Enable scheduled Factory generation</label></div><div class="settings-row"><input id="discovery-enabled" name="discovery_enabled" type="checkbox" ${s.discovery_enabled!==false?"checked":""}><label for="discovery-enabled">Research a fresh content brief once per UTC day</label></div><div class="settings-row"><input id="auto-migrate" name="auto_migrate_completed" type="checkbox" ${s.auto_migrate_completed!==false?"checked":""}><label for="auto-migrate">Place completed daily packs in Admin as private drafts</label></div><div class="form-grid"><label>Daily sticker target<input name="daily_target" type="number" min="3" max="100" value="${s.daily_target}" required></label><label>Stickers per request<input name="batch_size" type="number" min="3" max="10" value="${s.batch_size}" required></label></div><label>Audience for generated packs<select name="rating"><option value="GENERAL" ${s.rating!=="MATURE"?"selected":""}>General audience</option><option value="MATURE" ${s.rating==="MATURE"?"selected":""}>Private mature</option></select></label><fieldset class="category-picker"><legend>Daily content categories</legend>${contentCategories.map(c=>`<label><input type="checkbox" name="content_categories" value="${c}" ${(s.content_categories||[]).includes(c)?"checked":""}>${esc(c.replaceAll("_"," ").toLowerCase())}</label>`).join("")}</fieldset><label>Custom topic rotation · one per line<textarea name="topics" maxlength="5000">${esc((s.topics||[]).join("\n"))}</textarea></label><p class="hint">Optional format: CATEGORY | topic, for example INDIA | cricket group-chat reactions. ADULT_HUMOR requires Private mature. Use batches of 3–10; eight batches run per day. Audience changes apply next UTC day if today's generation has started. Cooldowns and request limits remain active.</p><button class="primary" type="submit">Save Factory settings</button></form></section><section class="panel"><h3>Deployment & access</h3><p>This first version uses one owner token. Keep Vercel deployment protection enabled. Team roles and MFA are the next authentication phase.</p><p>Server secrets: STUDIO_ACCESS_TOKEN, SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY. Worker secrets: GROQ_API_KEY for captions and GROQ_TRENDS_API_KEY for daily browser search. Keys never appear here or in the APK.</p><a class="text-button" href="/studio/index.html" target="_blank" rel="noopener">Open existing Creator Studio ↗</a></section>`;}
  function renderActivity() {return heading("Activity log","A record of pack edits, review decisions, publication, and Factory changes.")+(data.events.length?`<div class="table-wrap"><table><thead><tr><th>Time</th><th>Action</th><th>Entity</th></tr></thead><tbody>${data.events.map(e=>`<tr><td>${esc(new Date(e.created_at).toLocaleString())}</td><td><strong>${esc(e.action)}</strong></td><td>${esc(e.entity_id||"Factory settings")}</td></tr>`).join("")}</tbody></table></div>`:empty("No activity yet","Changes in this workspace will appear here."));}
  const pages={overview:renderOverview,packs:renderPacks,media:renderMedia,generate:renderGenerate,content:renderContent,factory:renderFactory,catalog:renderCatalog,settings:renderSettings,activity:renderActivity,health:renderHealth};
  function render() {$("content").innerHTML=pages[page]();$("breadcrumb").textContent=titles[page];document.querySelectorAll("nav button[data-page]").forEach(b=>b.classList.toggle("active",b.dataset.page===page));connectionUi();}
  async function loadLibrary() {if(live())library=(await api("/api/library",{action:"list"})).items;}
  async function showCreatePack(selectedPath="") {
    const selected=library.find(s=>s.path===selectedPath);
    await loadLibrary();
    if(selected&&!library.some(s=>s.path===selectedPath))library.unshift(selected);
    $("create-form").reset();
    $("create-category").innerHTML=categories.map(c=>`<option>${c}</option>`).join("");
    $("create-sources").innerHTML=library.map(s=>`<label class="source-choice"><input type="checkbox" name="sources" value="${esc(s.path)}" ${s.path===selectedPath?"checked":""}>${s.preview?`<img src="${esc(s.preview)}" alt="">`:""}<span>${esc(s.name||s.path.slice(-20))}</span></label>`).join("")||'<p class="hint">Upload an image in Media library first.</p>';
    $("create-dialog").showModal();
  }
  async function navigate(next) {if(!pages[next])return;if(live()&&readiness?.admin_ready===false)next="health";page=next;$("sidebar").classList.remove("open");if(["media","generate"].includes(next))await loadLibrary();render();}
  async function openPack(id,offset=0) {
    if(detail?.pack.id === id && $("pack-dialog").open) {
      const form = $("pack-form");
      pendingPackName = form.elements.name.value; pendingCreator = form.elements.creator.value; pendingCategory = $("pack-category")?.value||"";
      document.querySelectorAll(".sticker-edit").forEach(row => pendingStickerEdits.set(row.dataset.sticker, {
        label:row.querySelector('[data-field=label]').value,
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
    const addButton=p.status!=="published"&&(!live()||detail.target_packs?.some(p=>p.kind==="STATIC"&&p.count<30))?button("＋ Add stickers","add-sources",p.id):"";
    const actionButtons=p.status==="published"?button("Unpublish","unpublish",p.id):button("Archive","archive",p.id)+button("Reject","reject",p.id)+(p.status==="reviewed"?button("Publish to app ↗","publish",p.id,"primary"):p.status==="archived"?button("Restore draft","restore",p.id,"primary"):button("Approve review","review",p.id,"primary"));
    $("pack-editor").innerHTML=`<div class="editor-head"><h2>Pack details ${badge(p.status)}</h2><button class="icon-button" data-close aria-label="Close">×</button></div><form id="pack-form"><div class="form-grid"><label>Pack name<input name="name" value="${esc(p.name)}" maxlength="60" required ${p.status==="published"?"disabled":""}></label><label>Creator<input name="creator" value="${esc(p.creator)}" maxlength="60" required ${p.status==="published"?"disabled":""}></label></div><label>Pack category<select id="pack-category" ${p.status==="published"?"disabled":""}><option value="">Keep existing categories</option>${categories.map(c=>`<option value="${c}" ${pendingCategory===c?"selected":""}>${c}</option>`).join("")}</select></label><div class="section-head"><h3>${detail.stickers.length} stickers · ${esc(p.rating)}</h3><span class="count-label">Preview ${offset+1}–${Math.min(offset+5,detail.stickers.length)}</span></div><div class="sticker-list">${detail.stickers.map((s,i)=>`<div class="sticker-edit" data-sticker="${esc(s.name)}">${s.preview?`<img src="${esc(s.preview)}" alt="${esc(s.label||"Sticker preview")}">`:`<div class="count-label">${i+1}<br>${esc(s.kind)}</div>`}<label>Label<input data-field="label" value="${esc(s.label||"")}" maxlength="80" ${p.status==="published"?"disabled":""}></label><label class="tags">Tags<input data-field="tags" value="${esc(s.tags||"")}" maxlength="400" ${p.status==="published"?"disabled":""}></label><label class="remove">Remove<input type="checkbox" data-field="remove" ${s.remove?"checked":""} ${p.status==="published"?"disabled":""}></label></div>`).join("")}</div><div class="toolbar">${offset>0?button("← Previous previews","pack-prev",p.id):""}${offset+5<detail.stickers.length?button("Next previews →","pack-next",p.id):""}${addButton}${button("Download ZIP","download-pack",p.id)}</div><p class="hint">Editing returns a pack to draft. Published packs must be unpublished first. Original files and Factory output remain preserved.</p><div class="editor-actions">${p.status!=="published"?'<button class="secondary" type="submit">Save changes</button>':""}${actionButtons}</div></form>`;
    if(!$("pack-dialog").open)$("pack-dialog").showModal();
  }
  function confirm(title,message,fn) {confirmation=fn;$("confirm-title").textContent=title;$("confirm-message").textContent=message;$("confirm-submit").textContent=title;$("confirm-dialog").showModal();}
  $("pack-dialog").addEventListener("cancel",e=>{if(hasUnsavedPackEdits()){e.preventDefault();confirm("Discard unsaved changes","Your changes have not been saved. Close this editor?",async()=>$("pack-dialog").close());}});
  document.addEventListener("click",async(e)=>{
    const close=e.target.closest("[data-close]");if(close){e.preventDefault();const dialog=close.closest("dialog");if(dialog.id==="pack-dialog"&&hasUnsavedPackEdits()){confirm("Discard unsaved changes","Your changes have not been saved. Close this editor?",async()=>dialog.close());return;}dialog.close();return;}
    const nav=e.target.closest("[data-page]");if(nav){await task(()=>navigate(nav.dataset.page));return;}
    const b=e.target.closest("[data-action]");if(!b)return;e.preventDefault();
    const id=b.dataset.id,action=b.dataset.action;
    await task(async()=>{
      if(action==="navigate")return navigate(id);
      if(action==="health-refresh"){if(!live())return toast("Connect an owner token to check the live backend.");await refreshHealth();await refresh();return;}
      if(action==="result-pack"){$("result-dialog").close();return showCreatePack(id);}
      if(action==="result-generate"){$("result-dialog").close();await navigate("generate");document.querySelector('#generate-form select').value=id;return;}
      if(action==="add-sources") {
        if(hasUnsavedPackEdits())throw new Error("Save your current changes before adding more stickers.");
        await loadLibrary();appendPack={id,version:detail.pack.version};
        const targets=detail.target_packs||[{id:"preview-target",name:detail.pack.name,kind:"STATIC",count:detail.stickers.length}];
        $("add-target").innerHTML=targets.filter(p=>p.kind==="STATIC"&&p.count<30).map(p=>`<option value="${esc(p.id)}">${esc(p.name)} · ${30-p.count} spaces</option>`).join("");
        $("add-source-choices").innerHTML=library.map(source=>`<label class="source-choice"><input type="checkbox" name="sources" value="${esc(source.path)}"><span>${esc(source.name||source.path.slice(-20))}</span></label>`).join("")||"Upload images in Media library first.";
        $("add-sources-dialog").showModal();return;
      }
      if(action==="create-pack")return showCreatePack();
      if(action==="open-pack")return openPack(id);
      if(action==="migrate") {const result=await command("migrate_factory",{id});await refresh();toast(live()?"Factory output migrated. Original archive preserved.":"Preview: Factory output migrated.");return openPack(result.id);}
      if(action==="pack-next"||action==="pack-prev")return openPack(id,Math.max(0,detail.offset+(action==="pack-next"?5:-5)));
      if(action==="upload-source")return $("source-upload").click();
      if(action==="preview-source") {
        const image=live()?(await api("/api/library",{action:"fetch",path:id})).image:library.find(s=>s.path===id)?.preview||illustrations[0];
        let item=library.find(s=>s.path===id);if(!item){item={path:id,name:"Generated AI result",size:0};library.unshift(item);}item.preview=image;
        $("result-image").src=image;$("result-actions").innerHTML=button("Create a pack","result-pack",id,"primary")+button("Generate another version","result-generate",id);$("result-dialog").showModal();return;
      }
      if(action==="use-source") {await navigate("generate");document.querySelector('#generate-form select').value=id;return;}
      if(action==="prompt-template") {document.querySelector('#generate-form textarea').value={reaction:"Create a short, funny Hinglish reaction to this image. Capture one clear emotion. Keep the caption under six words.",work:"Make this an office reaction meme about Monday meetings. Keep it friendly, original and readable on a phone.",chai:"Turn this into a playful Hinglish chai-time reaction. Use one short original caption and leave room around the subject."}[id];return;}
      if(action==="download-pack") {if(!live())return toast("Preview only. Connect the backend to download a real pack ZIP.");const result=await command("pack_download",{id});const a=document.createElement("a");a.href=result.url;a.rel="noopener";a.target="_blank";a.click();return;}
      if(action==="queue-cancel"||action==="queue-retry"){await command("queue_action",{id,operation:action==="queue-retry"?"retry":"cancel"});await refresh();toast("Queue updated.");return;}
      if(["review","archive","restore","reject","publish","unpublish"].includes(action)) {
        if(hasUnsavedPackEdits())throw new Error("Save changes before reviewing or changing pack status.");
        const p=findPack(id),status={review:"reviewed",archive:"archived",restore:"draft",reject:"rejected"}[action];
        const explanation={review:"Confirm you reviewed every sticker and its source rights. All stickers must pass public pack validation.",archive:"Archive this pack from the working library. Its files remain stored and the draft can be restored.",restore:"Return this archived pack to the draft library.",reject:"Mark this draft as rejected. The original files are preserved.",publish:"Publish this reviewed GENERAL pack to the production catalog. It will be visible in the Android Factory feed.",unpublish:"Remove this pack from the public catalog. Existing phone downloads remain on those devices; the private original is preserved."}[action];
        confirm(action[0].toUpperCase()+action.slice(1)+" pack",(live()?"":"Preview simulation: ")+explanation,async()=>{await command(action==="publish"||action==="unpublish"?action:"pack_status",{id,version:p.version,status});$("pack-dialog").close();await refresh();toast(live()?"Pack updated.":"Preview: pack updated.");});
      }
    });
  });
  document.addEventListener("submit",(e)=>{
    if(e.target.id==="pack-form") {e.preventDefault();task(async()=>{const form=new FormData(e.target),stickers=Array.from(document.querySelectorAll(".sticker-edit")).map(row=>({name:row.dataset.sticker,label:row.querySelector('[data-field=label]').value,tags:row.querySelector('[data-field=tags]').value,remove:row.querySelector('[data-field=remove]').checked}));await command("pack_save",{id:detail.pack.id,version:detail.pack.version,name:form.get("name"),creator:form.get("creator"),category:$("pack-category").value||undefined,stickers});$("pack-dialog").close();await refresh();toast("Pack saved as a private draft.");});}
    if(e.target.id==="generate-form") {e.preventDefault();task(async()=>{const form=new FormData(e.target);if(live())await command("queue_create",{source_path:form.get("source_path"),prompt:form.get("prompt")});else data.requests.unshift({id:uid(),prompt:form.get("prompt"),status:"queued",attempts:0,created_at:new Date().toISOString()});await refresh();await navigate("factory");toast(live()?"AI request queued for the private worker.":"Preview: request queued. No AI service was called.");});}
    if(e.target.id==="settings-form") {e.preventDefault();task(async()=>{const form=new FormData(e.target),selected=form.getAll("content_categories"),rating=form.get("rating");if(!selected.length)throw new Error("Choose at least one content category.");if(selected.includes("ADULT_HUMOR")&&rating!=="MATURE")throw new Error("Adult humor requires Private mature.");await command("settings_save",{enabled:form.has("enabled"),discovery_enabled:form.has("discovery_enabled"),auto_migrate_completed:form.has("auto_migrate_completed"),daily_target:Number(form.get("daily_target")),batch_size:Number(form.get("batch_size")),rating,content_categories:selected,topics:String(form.get("topics")).split("\n").map(t=>t.trim()).filter(Boolean)});await refresh();toast(live()?"Settings saved for the next Factory run.":"Preview: settings updated.");});}
  });
  $("create-form").addEventListener("submit",e=>{e.preventDefault();task(async()=>{const form=new FormData(e.target),sources=form.getAll("sources");if(!sources.length||sources.length>30)throw new Error("Choose 1–30 source images.");const result=await command("pack_create",{name:form.get("name"),creator:form.get("creator"),category:form.get("category"),sources});$("create-dialog").close();await refresh();await openPack(result.id);toast("Private draft created.");});});
  $("add-sources-form").addEventListener("submit",e=>{e.preventDefault();task(async()=>{
    const form=new FormData(e.target),sources=form.getAll("sources");if(!sources.length||sources.length>30)throw new Error("Choose 1–30 images.");
    if(live())await command("pack_add_sources",{...appendPack,target_pack_id:form.get("target_pack_id"),sources});
    else {const p=findPack(appendPack.id);if(p.sticker_count+sources.length>30)throw new Error("This pack has space for "+(30-p.sticker_count)+" stickers.");p.demoStickers.push(...sources.map((path,i)=>({name:uid()+".webp",label:"",tags:"",kind:"STATIC",preview:library.find(s=>s.path===path)?.preview||illustrations[i%4]})));p.sticker_count=p.demoStickers.length;p.version++;p.status="draft";}
    const id=appendPack.id;$("add-sources-dialog").close();$("pack-dialog").close();await refresh();await openPack(id);toast("Stickers added. Review the updated draft before publishing.");
  });});
  $("confirm-form").addEventListener("submit",e=>{e.preventDefault();const fn=confirmation;confirmation=null;$("confirm-dialog").close();if(fn)task(fn);});
  document.addEventListener("change",e=>{if(e.target.id==="pack-filter"){filter=e.target.value;render();}if(e.target.id==="source-upload")task(async()=>{const file=e.target.files[0];if(!file)return;if(!["image/png","image/jpeg","image/webp"].includes(file.type)||file.size>3_000_000)throw new Error("Choose a static PNG, JPEG or WebP under 3 MB.");const image=await new Promise((resolve,reject)=>{const r=new FileReader();r.onload=()=>resolve(r.result);r.onerror=()=>reject(new Error("Cannot read image"));r.readAsDataURL(file);});if(live())await command("source_upload",{image,title:file.name});else library.unshift({path:"preview/"+uid()+".png",name:file.name,size:file.size,preview:image});await loadLibrary();render();toast(live()?"Image saved in the private Factory inbox.":"Preview: image added locally.");});});
  document.addEventListener("input",e=>{if(e.target.id==="pack-search"){const pos=e.target.selectionStart;search=e.target.value;render();$("pack-search").focus();$("pack-search").setSelectionRange(pos,pos);}});
  function connectDialog(){$("connect-error").textContent="";$("owner-token").value="";$("connect-dialog").showModal();}
  ["connect-button","connection-button","banner-connect"].forEach(id=>$(id).addEventListener("click",connectDialog));
  $("connect-form").addEventListener("submit",async e=>{e.preventDefault();const candidate=$("owner-token").value.trim();if(candidate.length<32)return;const old=token;token=candidate;const submit=e.target.querySelector('[type=submit]');submit.disabled=true;try{await refreshHealth();data=readiness.admin_ready?await api("/api/admin",{action:"overview"}):emptyLiveData();if(!readiness.admin_ready)page="health";library=[];$("owner-token").value="";$("connect-dialog").close();connectionUi();await navigate(page);toast(readiness.admin_ready?"Connected to your live Factory.":"Owner token accepted. Finish the setup checks below.");}catch(error){token=old;$("connect-error").textContent=error.message;}finally{submit.disabled=false;}});
  $("disconnect-button").addEventListener("click",()=>{token="";readiness=null;data=previewData();library=illustrations.map((preview,i)=>({path:"preview/source-"+i+".png",name:"Original reaction "+(i+1),size:18000,preview}));document.querySelectorAll("dialog").forEach(d=>d.close());render();toast("Disconnected. Exploring preview data.");});
  $("refresh-button").addEventListener("click",()=>task(async()=>{if(["media","generate"].includes(page))await loadLibrary();await refresh();toast(live()?"Factory data refreshed.":"Preview refreshed.");}));
  $("menu-button").addEventListener("click",()=>$("sidebar").classList.toggle("open"));
  document.addEventListener("visibilitychange",backgroundRefresh);
  setInterval(backgroundRefresh,30000);
  render();
})();
