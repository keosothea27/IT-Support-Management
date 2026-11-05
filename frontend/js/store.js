/* ===== State, local persistence and the API client ===== */
const CFG=window.APP_CONFIG||{API_BASE:"/api"};
let API=false,TOKEN="",uname="";   // API becomes true when the Java server answers /api/health
try{TOKEN=sessionStorage.getItem("itsm_token")||""}catch(e){}
const ME=k=>uname||(k==="tech"?"IT Staff 01":"Employee A");
const dbs=(t,c)=>{const e=$("dbs");if(e){e.textContent=t;e.style.color=c||""}};
const blank=()=>{const o={log:[],cfg:{co:"IT Desk",n1:1,n2:1,n3:1}};Object.keys(E).forEach(n=>o[n]=[]);return o};
let S=blank();
const save=()=>{if(API)return;try{localStorage.setItem("itsm2",JSON.stringify(S))}catch(e){}};
const role=()=>$("role").value;
const log=m=>{S.log.unshift(new Date().toLocaleString()+" - "+role()+": "+m);S.log=S.log.slice(0,100);save()};
const saveCfg=()=>{if(!API)return save();api("PUT","/settings",S.cfg).catch(e=>alert(e.message))};

/* Every call to the Java server goes through here. */
async function api(method,path,body){
  const h={"Content-Type":"application/json"};if(TOKEN)h.Authorization="Bearer "+TOKEN;
  const r=await fetch(CFG.API_BASE+path,{method,headers:h,body:body===undefined?undefined:JSON.stringify(body)});
  let j=null;try{j=await r.json()}catch(e){}
  if(r.status===401&&path!=="/login"){TOKEN="";try{sessionStorage.removeItem("itsm_token")}catch(e){}login("Your session ended. Please sign in again.")}
  if(!r.ok)throw new Error((j&&j.error)||"Request failed ("+r.status+")");
  return j}
