/* Local browser acceptance uses fixture APIs only. */
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright'),assert=require('node:assert/strict'),{spawn}=require('node:child_process'),fs=require('node:fs');
(async()=>{
  const server=spawn(process.env.PYTHON_EXECUTABLE||'python',['tools/admin_dev_server.py','--port','4193'],{windowsHide:true,stdio:'ignore'});let browser;
  try{
    for(let i=0;i<60;i++){try{await fetch('http://127.0.0.1:4193/admin');break;}catch{await new Promise(r=>setTimeout(r,100));}}
    browser=await chromium.launch({headless:true,...(process.platform==='win32'?{channel:'chrome'}:{})});
    const page=await browser.newPage({viewport:{width:1360,height:960}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
    const image='data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j5XkAAAAASUVORK5CYII=';
    let item={id:'12345678-1234-1234-1234-123456789abc',version:1,title:'Original reaction',topic:'reaction portrait',platform:'licensed',rights_status:'UNREVIEWED',generation_eligible:false,source_url:'https://commons.wikimedia.org/wiki/File:Original_reaction.jpg',archived:false};let approvals=0,uploads=0;
    await page.route('**/api/admin',async route=>{const p=route.request().postDataJSON();let result;
      if(p.action==='health')result={admin_ready:true,checks:[]};
      else if(p.action==='overview')result={packs:[],factory_packs:[],runs:[],requests:[],events:[],briefs:[],settings:{enabled:true,daily_target:40,batch_size:5},catalog:{packs:[]},catalog_base:'https://example.invalid/'};
      else if(p.action==='sources_list')result={items:[item],next_offset:null,source_options:[{id:'pinterest',name:'Pinterest'},{id:'flickr',name:'Flickr public tag feed'},{id:'custom-1234567890abcdef',name:'Owned topic search'}]};
      else if(['intake_preview','intake_thumbnail'].includes(p.action))result={image};
      else if(p.action==='source_admit'){assert.equal(p.quality_checked,true);assert.ok(p.permission_note.length>=12);item={...item,generation_eligible:true,rights_status:'APPROVED',version:2};approvals++;result={item};}
      else if(p.action==='source_upload_candidate'){assert.ok(p.image.startsWith('data:image/png;base64,'));uploads++;result={item};}
      else if(p.action==='collection_dashboard')result={items:[],plans:[],jobs:[],sources:['pinterest','reddit','imgflip','flickr','custom-1234567890abcdef'],source_options:[{id:'pinterest',name:'Pinterest'},{id:'reddit',name:'Reddit'},{id:'imgflip',name:'Imgflip'},{id:'flickr',name:'Flickr public tag feed'},{id:'custom-1234567890abcdef',name:'Owned topic search'}],daily_schedule:'10:00 Asia/Kolkata (Railway)',worker_online:false,capabilities:{candidate_thumbnail:true,custom_sources:true}};
      else throw Error('Unexpected fixture API '+p.action);
      return route.fulfill({json:result});
    });
    await page.goto('http://127.0.0.1:4193/admin');await page.getByRole('heading',{name:'Your creative workspace'}).waitFor();
    await page.locator('nav [data-page=sources]').click();await page.getByRole('heading',{name:'Sources',exact:true}).waitFor();assert.equal(await page.locator('#source-filters select[name=platform] option[value=flickr]').count(),1);assert.equal(await page.locator('#source-filters select[name=platform] option[value=custom-1234567890abcdef]').count(),1);
    await page.getByRole('button',{name:'Review for generation'}).click();await page.locator('#source-review-form select').selectOption('CC0');await page.locator('#source-review-form textarea').fill('Creator released this photograph under CC0 at the original source.');await page.locator('#source-review-form input[type=checkbox]').check();await page.getByRole('button',{name:'Approve for generation',exact:true}).click();await page.getByRole('button',{name:'Create sticker',exact:true}).waitFor();assert.equal(approvals,1);
    await page.locator('.source-upload-panel summary').click();await page.locator('#source-upload-form input[type=file]').setInputFiles({name:'my-cat.png',mimeType:'image/png',buffer:Buffer.from(image.split(',')[1],'base64')});await page.getByRole('button',{name:'Add image',exact:true}).click();await page.waitForFunction(()=>document.getElementById('toast').textContent.includes('Image added'));assert.equal(uploads,1);
    fs.mkdirSync('work/source-workspace-browser',{recursive:true});await page.screenshot({path:'work/source-workspace-browser/desktop.png',fullPage:false,animations:'disabled'});
    await page.getByRole('button',{name:'Collections & plans',exact:true}).click();await page.getByRole('heading',{name:'New collection'}).waitFor();assert.equal(await page.getByRole('heading',{name:'Downloaded image library'}).count(),0);assert.equal(await page.getByLabel('Flickr public tag feed').isChecked(),true);await page.getByLabel('Owned topic search').check();assert.equal(await page.getByLabel('Owned topic search').isChecked(),true);await page.getByText('No worker running now',{exact:true}).waitFor();await page.getByText(/10:00 Asia\/Kolkata \(Railway\)/).waitFor();
    await page.locator('nav [data-page=connections]').click();await page.getByRole('heading',{name:'Coming soon',exact:true}).waitFor();
    item={...item,platform:'pinterest',source_url:'https://www.pinterest.com/pin/123/'};
    await page.locator('nav [data-page=sources]').click();await page.getByText('Pinterest reference only. Upload your own original to create stickers.').waitFor();assert.equal(await page.getByRole('button',{name:'Create sticker',exact:true}).count(),0);
    await page.setViewportSize({width:390,height:844});await page.waitForFunction(()=>getComputedStyle(document.getElementById('sidebar')).transform==='matrix(1, 0, 0, 1, -230, 0)');await page.screenshot({path:'work/source-workspace-browser/mobile.png',fullPage:false,animations:'disabled'});assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));assert.deepEqual(errors,[]);
    console.log('Sources upload, admission, collection separation, roadmap and 390px layout passed with fixture APIs.');
  }finally{await browser?.close();server.kill();}
})().catch(e=>{console.error(e);process.exitCode=1;});
