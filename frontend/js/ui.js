/* Navigation, lists, forms, CSV export, theme, backup */
let cg=0,ci=0,og=0,ce="assets",cf=null,flt={q:""};
function nav(){$("nav").innerHTML=`<div class="lg">${esc(S.cfg.co||"IT Desk")}</div>`+G.map((g,gi)=>{const ls=g[2].map((l,li)=>okG(g,l)?`<a tabindex="0" role="button" class="${gi===cg&&li===ci?"on":""}" onclick="go(${gi},${li})" onkeydown="if(event.key==='Enter')go(${gi},${li})">${esc(l[0])}</a>`:"").join("");return ls?`<button class="gh ${gi===cg?"on":""}" aria-expanded="${gi===og}" onclick="og=og===${gi}?-1:${gi};nav()"><span class="gl">${IC(g[1])}${g[0]}</span><span class="cv${gi===og?" up":""}">${IC("chev")}</span></button><div class="gb ${gi===og?"open":""}">${ls}</div>`:""}).join("")}
function go(g,i){cg=g;ci=i;og=g;show(G[g][2][i])}
function relog(){okG(G[cg],G[cg][2][ci])?go(cg,ci):go(0,0)}
function show(L,noEdit){flt={q:""};cf=null;if(L[1]!=="p"){ce=L[2];cf=L[3]||null}
nav();$("bc").textContent="Home / "+G[cg][0]+" / "+L[0];$("ttl").textContent=L[0];
$("v").innerHTML=L[1]==="p"?PG[L[2]](L[3]):list(ce);
if(L[1]==="f"&&canEdit(ce)&&!noEdit)edit(ce)}
function gsearch(v){if(role()==="Employee")return;show(["Search results","l","assets"]);flt.q=v;$("q").value=v;$("tb").innerHTML=rows("assets")}
const opts=f=>({dept:()=>S.departments.map(x=>x.name),emp:()=>["",...S.employees.map(x=>x.name)],loc:()=>["",...S.locations.map(x=>x.name)],asset:()=>S.assets.map(x=>x.id+" "+x.name),tkt:()=>S.tickets.map(x=>x.id),ven:()=>["",...S.vendors.map(x=>x.name)]}[f[2]]||(()=>f[3]||[]))();
function badge(v){const g=["In Use","Resolved","Closed","In Stock","Low","Active","Done","Used","Free"],w=["Under Repair","Pending","Medium","In Progress","Assigned","Scheduled","Reserved"],b=["Lost","Retired","Critical","High","Open","Inactive","Disabled"];return`<span class="tag ${g.includes(v)?"g":w.includes(v)?"w":b.includes(v)?"b":""}">${esc(v)}</span>`}
const tbl=(h,r)=>`<div class="wrap"><table><thead><tr>${h.map(x=>`<th>${x}</th>`).join("")}</tr></thead><tbody>${r.map(x=>`<tr>${x.map(c=>`<td>${esc(c)}</td>`).join("")}</tr>`).join("")}</tbody></table></div>`;
function list(n){const e=E[n];
const fs=e.fl.map(k=>{const f=e.f.find(x=>x[0]===k);return`<select data-f="${k}" aria-label="Filter by ${f[1]}"><option value="">All ${f[1].toLowerCase()}</option>${opts(f).filter(Boolean).map(o=>`<option>${esc(o)}</option>`).join("")}</select>`}).join("");
return`<div class="tools"><input id="q" type="search" placeholder="Search ${e.t.toLowerCase()}" aria-label="Search"> ${fs}${canEdit(n)?`<button class="p" onclick="edit('${n}')">Add</button>`:""}<button onclick="csv('${n}')">Export CSV</button><button onclick="print()">Print</button></div><div class="wrap"><table><thead><tr>${e.c.map(c=>`<th>${c==="id"?"ID":esc(e.f.find(x=>x[0]===c)[1])}</th>`).join("")}<th></th></tr></thead><tbody id="tb">${rows(n)}</tbody></table></div>`}
function rows(n){const e=E[n],q=(flt.q||"").toLowerCase();
const r=S[n].filter(x=>(!cf||cf(x))&&(role()!=="Employee"||n!=="tickets"||x.requester===ME("r"))&&(!q||Object.values(x).join(" ").toLowerCase().includes(q))&&e.fl.every(k=>!flt[k]||x[k]===flt[k]));
if(!r.length)return`<tr><td colspan="${e.c.length+1}" class="empty">No records found.</td></tr>`;
return r.map(x=>`<tr>${e.c.map(c=>`<td>${c==="status"&&n==="tickets"&&role()!=="Employee"?`<select data-s="${esc(x.id)}" aria-label="Ticket status">${TST.map(s=>`<option${s===x.status?" selected":""}>${s}</option>`).join("")}</select>`:["status","priority"].includes(c)?badge(x[c]):esc(x[c])}</td>`).join("")}<td>${canEdit(n)&&role()!=="Employee"?`<button data-act="edit" data-n="${n}" data-id="${esc(x.id)}">Edit</button>`:""}${role()==="Administrator"?` <button class="d" data-act="del" data-n="${n}" data-id="${esc(x.id)}">Delete</button>`:""}</td></tr>`).join("")}
document.addEventListener("input",e=>{if(e.target.id==="q"){flt.q=e.target.value;$("tb").innerHTML=rows(ce)}});
document.addEventListener("change",async e=>{const t=e.target;
if(t.dataset.f){flt[t.dataset.f]=t.value;$("tb").innerHTML=rows(ce)}
if(t.dataset.s){const k=S.tickets.find(x=>x.id===t.dataset.s);
if(API){try{await api("PUT","/tickets/"+encodeURIComponent(k.id),{status:t.value})}catch(err){alert(err.message);$("tb").innerHTML=rows(ce);return}}
k.status=t.value;log(k.id+" status set to "+t.value);save();$("tb").innerHTML=rows(ce)}});
document.addEventListener("click",e=>{const b=e.target.closest("[data-act]");if(!b)return;const a=b.dataset.act,n=b.dataset.n,id=b.dataset.id;
if(a==="edit")edit(n,id);else if(a==="del")del(n,id);else if(a==="save")saveRec(n,id)});
const DF=()=>({tickets:{status:"Open",priority:"Medium",created:iso(0)},assets:{status:"In Stock"},comments:{date:iso(0)},movements:{date:iso(0)},maintenance:{date:iso(0)},swrecords:{date:iso(0)}});
/* Fields shown in the form: the password box only exists with the server; employees only describe their problem. */
const fieldsOf=n=>E[n].f.filter(f=>!(f[2]==="pwd"&&!API)&&!(role()==="Employee"&&n==="tickets"&&EH.includes(f[0])));
function edit(n,id){const e=E[n],r=id?S[n].find(x=>x.id===id):{...(DF()[n]||{})};if(!r)return;
$("dlg").innerHTML=`<form method="dialog" onsubmit="return false"><h2>${id?"Edit":"Add"} ${e.t.toLowerCase()}</h2>${fieldsOf(n).map(f=>{const t=f[2],v=esc(r[f[0]]);return`<label>${f[1]}`+(t==="area"?`<textarea name="${f[0]}" rows="2">${v}</textarea>`:t==="pwd"?`<input name="${f[0]}" type="password" autocomplete="new-password" placeholder="${id?"Leave blank to keep the current password":"At least 10 characters"}">`:t==="date"||!t?`<input name="${f[0]}" type="${t==="date"?"date":"text"}" value="${v}">`:`<select name="${f[0]}">${opts(f).concat(r[f[0]]&&!opts(f).includes(r[f[0]])?[r[f[0]]]:[]).map(o=>`<option${o===r[f[0]]?" selected":""}>${esc(o)}</option>`).join("")}</select>`)+"</label>"}).join("")}<div class="tools"><button class="p" data-act="save" data-n="${n}" data-id="${esc(id||"")}">Save changes</button><button onclick="$('dlg').close()">Cancel</button></div></form>`;$("dlg").showModal()}
async function saveRec(n,id){const e=E[n],fd=new FormData($("dlg").querySelector("form")),o={};
fieldsOf(n).forEach(f=>{o[f[0]]=(fd.get(f[0])||"").trim()});
if(role()==="Employee"&&n==="tickets")o.requester=ME("r");
const miss=(REQ[n]||[e.f[0][0]]).find(k=>!o[k]);
if(miss){alert("Enter "+e.f.find(f=>f[0]===miss)[1].toLowerCase()+".");return}
if("password" in o&&!o.password){if(id)delete o.password;else{alert("Enter a password of at least 10 characters.");return}}
let rec;
if(API){try{rec=await api(id?"PUT":"POST","/"+n+(id?"/"+encodeURIComponent(id):""),o)}catch(err){alert(err.message);return}
const i=S[n].findIndex(x=>x.id===rec.id);if(i<0)S[n].push(rec);else S[n][i]=rec}
else if(id){rec=Object.assign(S[n].find(x=>x.id===id),o)}
else{const m=Math.max(0,...S[n].map(x=>parseInt(x.id.split("-").pop())||0))+1;rec={id:e.p+pad(m,e.n),...(DF()[n]||{}),...o};S[n].push(rec)}
log((id?"edited ":"added ")+n+" "+rec.id);save();$("dlg").close();show(G[cg][2][ci],true)}
async function del(n,id){if(!confirm("Delete "+id+"? This cannot be undone."))return;
if(API){try{await api("DELETE","/"+n+"/"+encodeURIComponent(id))}catch(err){alert(err.message);return}}
S[n]=S[n].filter(x=>x.id!==id);if(n==="tickets")S.comments=S.comments.filter(x=>x.ticket!==id);
log("deleted "+id);save();$("tb").innerHTML=rows(ce)}
function csv(n,data,name){const rs=data||S[n].filter(x=>!cf||cf(x));if(!rs.length){alert("Nothing to export.");return}
/* Union of all keys (older rows may lack fields) and a leading ' on cells that a spreadsheet could run as a formula. */
const ks=[...new Set(rs.flatMap(r=>Object.keys(r)))],q=v=>{v=String(v??"");if(/^[=@\t\r]|^[+-][^\d\s.(]/.test(v))v="'"+v;return'"'+v.replace(/"/g,'""')+'"'};
const a=document.createElement("a");a.href=URL.createObjectURL(new Blob(["\ufeff"+[ks.map(q).join(","),...rs.map(r=>ks.map(k=>q(r[k])).join(","))].join("\r\n")],{type:"text/csv"}));a.download=(name||n)+".csv";a.click()}
function theme(){const r=document.documentElement,dark=r.dataset.theme?r.dataset.theme==="dark":matchMedia("(prefers-color-scheme: dark)").matches;r.dataset.theme=dark?"light":"dark"}
function bk(){const a=document.createElement("a");a.href=URL.createObjectURL(new Blob([JSON.stringify(S)],{type:"application/json"}));a.download="it-backup-"+iso(0)+".json";a.click()}
function rs(f){if(!f)return;const r=new FileReader();r.onload=()=>{try{const o=JSON.parse(r.result);if(!Array.isArray(o.assets)||!Array.isArray(o.tickets))throw 0;const n=Object.assign(blank(),o);Object.keys(E).forEach(k=>{if(!Array.isArray(n[k]))n[k]=[]});if(confirm("Replace all current data with this backup?")){S=n;save();go(0,0)}}catch(e){alert("This file is not a valid backup.")}};r.readAsText(f)}
function resetData(){if(confirm("Replace all data with the sample data?")){S=seed();save();go(0,0)}}
