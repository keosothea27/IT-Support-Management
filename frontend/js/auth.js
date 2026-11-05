/* ===== Sign-in screen (used only when the Java server is running) ===== */
function login(m){const d=$("dlg");
d.innerHTML=`<form onsubmit="return false"><h2>Sign in to ${esc(S.cfg.co||"IT Desk")}</h2><label>Email<input id="le" type="email" autocomplete="username" onkeydown="if(event.key==='Enter')doLogin()"></label><label>Password<input id="lp" type="password" autocomplete="current-password" onkeydown="if(event.key==='Enter')doLogin()"></label><p id="lm" class="note" style="color:var(--er)">${esc(m||"")}</p><div class="tools"><button class="p" onclick="doLogin()">Sign in</button></div></form>`;
d.oncancel=e=>e.preventDefault();if(!d.open)d.showModal()}
async function doLogin(){let r;
try{r=await api("POST","/login",{email:$("le").value.trim(),password:$("lp").value})}catch(e){$("lm").textContent=e.message;return}
TOKEN=r.token;try{sessionStorage.setItem("itsm_token",TOKEN)}catch(e){}
$("dlg").oncancel=null;$("dlg").close();enter(r.user)}
async function enter(u){const r=$("role");r.value=u.role;r.disabled=true;uname=u.name;
try{const d=await api("GET","/data"),o=blank();Object.keys(E).forEach(n=>{o[n]=d.tables[n]||[]});Object.assign(o.cfg,d.cfg);o.log=d.log||[];S=o;
dbs("☁ PostgreSQL database · "+u.email+" · "+u.role)}
catch(e){S=blank();dbs("⚠ Cannot read database: "+e.message,"var(--er)")}
document.querySelector(".av").textContent=(u.name||u.email).slice(0,2).toUpperCase();$("lo").style.display="";nav();go(0,0)}
function logout(){api("POST","/logout").catch(()=>{}).finally(()=>{TOKEN="";try{sessionStorage.removeItem("itsm_token")}catch(e){}location.reload()})}
