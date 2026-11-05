/* Constants, helpers, entity (table) definitions and sample data */
const CAT=["Laptop","Desktop","Monitor","Peripheral","Printer","Scanner","Server","Router","Switch","Access Point","Firewall","CCTV","NVR","UPS"];
const AST=["In Use","In Stock","Under Repair","Retired","Lost"];
const TST=["Open","Assigned","In Progress","Pending","Resolved","Closed"];
const PRI=["Low","Medium","High","Critical"];
const TCAT=["Hardware","Software","Network","Account & Access","Other"];
const $=i=>document.getElementById(i),pad=(n,l)=>String(n).padStart(l,"0");
const iso=o=>{const d=new Date();d.setDate(d.getDate()+o);return d.getFullYear()+"-"+pad(d.getMonth()+1,2)+"-"+pad(d.getDate(),2)};  // local date, not UTC
const days=d=>d?Math.ceil((new Date(d)-new Date(iso(0)))/864e5):9999;
const esc=s=>String(s??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
const Y=new Date().getFullYear(),so=a=>a.join("/");
const PF=s=>s.split(";").map(p=>{const[a,b,c,d]=p.split(":");return[a,b,c,d?d.split("/"):0]});
const def=(t,p,n,f,c,fl="")=>({t,p,n,f:PF(f),c:c.split(","),fl:fl?fl.split(","):[]});
const E={
assets:def("Assets","IT-",4,`name:Asset name;category:Category:sel:${so(CAT)};brand:Brand and model;serial:Serial number;dept:Department:dept;owner:Assigned to:emp;location:Location:loc;purchase:Purchase date:date;price:Purchase price;warranty:Warranty ends:date;status:Status:sel:${so(AST)};ip:IP address;mac:MAC address;notes:Notes:area`,"id,name,category,dept,owner,status,warranty","status,category"),
tickets:def("Tickets","TKT-"+Y+"-",4,`requester:Requester:emp;category:Category:sel:${so(TCAT)};description:Description:area;priority:Priority:sel:${so(PRI)};tech:Technician;status:Status:sel:${so(TST)};created:Created date:date;hours:Hours spent;resolution:Resolution:area`,"id,requester,category,description,priority,tech,status,created","status,priority"),
comments:def("Ticket comments","CMT-",3,"ticket:Ticket:tkt;author:Author;comment:Comment:area;date:Date:date","id,ticket,author,comment,date"),
licenses:def("Licenses","LIC-",3,"software:Software name;version:Version;type:License type;seats:Number of licenses;purchase:Purchase date:date;expires:Expiration date:date;notes:Notes (no license keys):area","id,software,version,type,seats,expires"),
swrecords:def("Software records","SWR-",3,"software:Software;type:Record type:sel:Assignment/Installation/Renewal;target:Device or employee;date:Date:date;cost:Cost;notes:Notes:area","id,software,type,target,date,cost","type"),
employees:def("Employees","EMP-",3,"name:Full name;dept:Department:dept;location:Location:loc;email:Email;status:Status:sel:Active/Inactive","id,name,dept,location,email,status","dept,status"),
departments:def("Departments","DEP-",2,"name:Department name","id,name"),
users:def("Users","USR-",2,"name:Full name;email:Email;role:Role:sel:Administrator/IT Technician/Employee;status:Status:sel:Active/Disabled;password:Password:pwd","id,name,email,role,status"),
movements:def("Asset movements","MOV-",3,"asset:Asset:asset;type:Type:sel:Assignment/Transfer/Return;from_party:From;to_party:To;date:Date:date;notes:Notes:area","id,asset,type,from_party,to_party,date","type"),
maintenance:def("Maintenance","MNT-",3,"asset:Asset:asset;type:Type:sel:Preventive/Repair/Infrastructure;vendor:Vendor:ven;date:Date:date;status:Status:sel:Scheduled/In Progress/Done;tech:Technician;cost:Cost;parts:Parts replaced;notes:Notes:area","id,asset,type,vendor,date,status,cost","status,type"),
vendors:def("Vendors","VEN-",2,"name:Vendor name;contact:Contact person;phone:Phone;email:Email;service:Services","id,name,contact,phone,email,service"),
ips:def("IP addresses","IP-",3,"ip:IP address;device:Device;mac:MAC address;vlan:VLAN;location:Location:loc;status:Status:sel:Used/Free/Reserved","id,ip,device,mac,vlan,location,status","status"),
docs:def("Network documents","DOC-",2,"title:Title;type:Type:sel:Diagram/Procedure/Vendor/Other;url:Link;notes:Notes:area","id,title,type,url"),
locations:def("Locations","LOC-",2,"name:Location or office;address:Address or floor","id,name,address"),
categories:def("Categories","CAT-",2,"name:Name;type:Type:sel:Ticket category/Priority;sla:Response target (hours)","id,name,type,sla")};
const mk=(p,n,k,r)=>r.map((a,i)=>Object.fromEntries([["id",p+pad(i+1,n)],...k.split(",").map((x,j)=>[x,a[j]??""])]));
function seed(){const d=iso;
const A=[["Lenovo Laptop","Laptop","Accounting","Employee A","In Use",200],["Dell Desktop","Desktop","IT","","In Stock",120],["Dell Monitor","Monitor","HR","Employee B","In Use",400],["Logitech Mouse","Peripheral","Accounting","Employee A","In Use",300],["HP Printer","Printer","HR","Employee B","In Use",20],["Epson Scanner","Scanner","HR","","Under Repair",-30],["Dell Server","Server","IT","IT Administrator","In Use",600],["Ruijie Switch","Switch","IT","IT Administrator","In Use",400],["Ruijie Router","Router","IT","IT Administrator","In Use",25],["Ruijie Access Point","Access Point","IT","","In Use",380],["Sophos Firewall","Firewall","IT","IT Administrator","In Use",150],["Hikvision Camera","CCTV","Security","","In Use",500],["Hikvision NVR","NVR","Security","Security Team","In Use",15],["Old Laptop","Laptop","Operations","","Retired",-400]];
return{log:[],cfg:{co:"IT Desk",n1:1,n2:1,n3:1},
departments:mk("DEP-",2,"name",[["IT"],["Accounting"],["HR"],["Security"],["Operations"]]),
locations:mk("LOC-",2,"name,address",[["Head Office","Floor 1"],["Branch 1","Ground floor"],["Gate 1","Security booth"]]),
vendors:mk("VEN-",2,"name,contact,phone,email,service",[["TechParts Co","Contact A","000-000","sales@example.com","Hardware repair"],["NetWorks Ltd","Contact B","000-001","help@example.com","Network support"]]),
employees:mk("EMP-",3,"name,dept,location,email,status",[["Employee A","Accounting","Head Office","a@example.com","Active"],["Employee B","HR","Head Office","b@example.com","Active"],["IT Administrator","IT","Head Office","it@example.com","Active"],["Security Team","Security","Gate 1","sec@example.com","Active"],["Former Employee","Operations","Branch 1","x@example.com","Inactive"]]),
users:mk("USR-",2,"name,email,role,status",[["IT Administrator","it@example.com","Administrator","Active"],["IT Staff 01","staff01@example.com","IT Technician","Active"],["Employee A","a@example.com","Employee","Active"]]),
assets:mk("IT-",4,"name,category,dept,owner,status,warranty,location,purchase",A.map((r,i)=>[...r.slice(0,5),d(r[5]),"Head Office",d(-30*i-10)])),
tickets:mk("TKT-"+Y+"-",4,"requester,category,description,priority,status,tech,created",Array.from({length:12},(_,i)=>[["Employee A","Employee B","Security Team"][i%3],TCAT[i%5],["Laptop will not start","Office activation problem","Wi-Fi access point is down","Password reset needed","Printer needs a new toner"][i%5],PRI[(i+1)%4],["Open","Assigned","In Progress","Resolved","Closed","Pending"][i%6],i%4?"IT Staff 01":"",d(-i*17)])),
comments:mk("CMT-",3,"ticket,author,comment,date",[["TKT-"+Y+"-0001","IT Staff 01","Checking the device now.",d(0)],["TKT-"+Y+"-0002","IT Staff 01","Waiting for a spare part.",d(-17)]]),
licenses:mk("LIC-",3,"software,version,type,seats,expires",[["Microsoft Office","2021","Volume","50",d(45)],["Sophos Endpoint","Latest","Subscription","120",d(300)],["Hikvision iVMS-4200","3.x","Free","5",d(900)]]),
swrecords:mk("SWR-",3,"software,type,target,date,cost",[["Microsoft Office","Assignment","Employee A",d(-60),""],["Microsoft Office","Installation","Lenovo Laptop",d(-60),""],["Sophos Endpoint","Installation","Dell Desktop",d(-30),""],["Microsoft Office","Renewal","Company",d(-330),"1200"]]),
movements:mk("MOV-",3,"asset,type,from_party,to_party,date",[["IT-0001 Lenovo Laptop","Assignment","IT","Employee A",d(-90)],["IT-0002 Dell Desktop","Transfer","Accounting","IT",d(-20)],["IT-0014 Old Laptop","Return","Operations","IT",d(-40)]]),
maintenance:mk("MNT-",3,"asset,type,vendor,date,status,tech,cost,parts",[["IT-0006 Epson Scanner","Repair","TechParts Co",d(-5),"In Progress","IT Staff 01","80","Feed roller"],["IT-0007 Dell Server","Preventive","NetWorks Ltd",d(14),"Scheduled","IT Staff 01","0",""],["IT-0008 Ruijie Switch","Infrastructure","NetWorks Ltd",d(-90),"Done","IT Staff 01","150","Fan"]]),
ips:mk("IP-",3,"ip,device,mac,vlan,location,status",[["10.0.0.1","Ruijie Router","","1","Head Office","Used"],["10.0.0.10","Dell Server","","10","Head Office","Used"],["10.0.0.50","","","10","Head Office","Free"]]),
docs:mk("DOC-",2,"title,type,url,notes",[["Head office network diagram","Diagram","",""],["Switch replacement procedure","Procedure","",""]]),
categories:mk("CAT-",2,"name,type,sla",[...TCAT.map(c=>[c,"Ticket category",""]),...PRI.map((p,i)=>[p,"Priority",[72,24,8,2][i]])])}}
/* Which fields must be filled in, and which ticket fields an Employee never sees. */
const REQ={tickets:["requester","description"],comments:["ticket","comment"],users:["name","email"]},EH=["requester","tech","status","created","hours","resolution"];
