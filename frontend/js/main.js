/* ===== Start-up: use the Java API if it is there, otherwise browser-only mode ===== */
async function start(){
try{const r=await fetch(CFG.API_BASE+"/health",{cache:"no-store"});API=r.ok&&(await r.json()).ok===true}catch(e){API=false}
if(!API){let d=null;try{d=JSON.parse(localStorage.getItem("itsm2"))}catch(e){}
S=Object.assign(blank(),d&&Array.isArray(d.assets)&&Array.isArray(d.tickets)?d:seed());
dbs("💾 Local mode: data stays in this browser. Start the Java server to use the database.","var(--wn)");nav();go(0,0);return}
if(TOKEN){try{await enter(await api("GET","/me"));return}catch(e){}}
login()}
start();
