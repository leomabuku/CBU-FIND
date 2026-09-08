import { applicationDefault } from "firebase-admin/app";
import { argument } from "./firebase-admin.mjs";

const projectId = argument("--project") || process.env.GOOGLE_CLOUD_PROJECT;
if (!projectId) throw new Error("Pass --project <firebase-project-id> or set GOOGLE_CLOUD_PROJECT.");

const credential = applicationDefault();
const token = await credential.getAccessToken();
const url = `https://identitytoolkit.googleapis.com/admin/v2/projects/${encodeURIComponent(projectId)}/config`;
const headers = { authorization: `Bearer ${token.access_token}`, "content-type": "application/json" };
let config = await responseJson(await fetch(url, { headers }), "read");
report("auth-providers.audit", config);

if (process.argv.includes("--disable-phone") && config.signIn?.phoneNumber?.enabled === true) {
  config = await responseJson(await fetch(`${url}?updateMask=signIn.phoneNumber.enabled`, {
    method: "PATCH",
    headers,
    body: JSON.stringify({ signIn: { phoneNumber: { enabled: false } } }),
  }), "update");
  report("auth-providers.phone-disabled", config);
}

function report(event, value) {
  console.log(JSON.stringify({
    event,
    projectId,
    emailEnabled: value.signIn?.email?.enabled === true,
    passwordRequired: value.signIn?.email?.passwordRequired === true,
    phoneEnabled: value.signIn?.phoneNumber?.enabled === true,
  }));
}

async function responseJson(response, action) {
  if (!response.ok) throw new Error(`Identity Platform could not ${action} the provider configuration (HTTP ${response.status}).`);
  return response.json();
}
