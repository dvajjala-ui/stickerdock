(() => {
  "use strict";
  const params=new URLSearchParams(location.hash.slice(1));
  let access=params.get("access_token")||"";
  const status=document.getElementById("account-status"),recover=document.getElementById("recover-form"),password=document.getElementById("password-form");
  history.replaceState(null,"",location.pathname);
  if(params.get("error")){status.textContent="This link expired or could not be verified. Request a new one.";access="";}
  else if(access&&params.get("type")==="recovery"){recover.hidden=true;password.hidden=false;status.textContent="Choose your new password.";}
  else if(access){access="";recover.hidden=true;status.textContent="Email confirmed. Return to StickerDock on your phone or open Admin and sign in.";}
  async function submit(form,payload) {
    const button=form.querySelector("button");button.disabled=true;
    try {
      const headers={"Content-Type":"application/json"};if(access)headers.Authorization="Bearer "+access;
      const response=await fetch("/api/account",{method:"POST",headers,body:JSON.stringify(payload)});
      const result=await response.json();if(!response.ok)throw new Error(result.error||"Please retry.");
      status.textContent=result.message;
      if(payload.action==="password"){access="";password.reset();password.hidden=true;}
    } catch(error){status.textContent=error.message;}finally{button.disabled=false;}
  }
  recover.addEventListener("submit",e=>{e.preventDefault();submit(recover,{action:"recover",email:document.getElementById("recover-email").value});});
  password.addEventListener("submit",e=>{e.preventDefault();const value=document.getElementById("new-password").value;if(value!==document.getElementById("confirm-password").value){status.textContent="Passwords do not match.";return;}submit(password,{action:"password",password:value});});
})();
