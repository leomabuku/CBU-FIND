/* Firebase public configuration is supplied in the registration URL; no secret belongs here. */
importScripts("https://www.gstatic.com/firebasejs/12.2.1/firebase-app-compat.js");
importScripts("https://www.gstatic.com/firebasejs/12.2.1/firebase-messaging-compat.js");
const values = Object.fromEntries(new URL(self.location.href).searchParams.entries());
firebase.initializeApp({ apiKey: values.apiKey, authDomain: values.authDomain, projectId: values.projectId, messagingSenderId: values.messagingSenderId, appId: values.appId });
firebase.messaging();
self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const link = event.notification.data?.FCM_MSG?.fcmOptions?.link || "/messages";
  event.waitUntil(clients.matchAll({ type: "window", includeUncontrolled: true }).then((windows) => windows[0] ? windows[0].focus() : clients.openWindow(link)));
});
