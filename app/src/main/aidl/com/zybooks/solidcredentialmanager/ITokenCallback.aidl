// ITokenCallback.aidl
package com.zybooks.solidcredentialmanager;

interface ITokenCallback {
    // result["reply"]: versioned JSON {"v":1,"status":...}.
    // result["action"]: a PendingIntent to launch for CONSENT_REQUIRED or LOGIN_REQUIRED.
    oneway void onResult(in Bundle result);
}
