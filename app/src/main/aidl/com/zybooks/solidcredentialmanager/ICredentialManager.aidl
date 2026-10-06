// ICredentialManager.aidl
// Contract between the Solid Credential Manager and client apps (version 1).
// Client apps keep byte-identical copies of this file and the two callbacks.
package com.zybooks.solidcredentialmanager;

import com.zybooks.solidcredentialmanager.ITokenCallback;
import com.zybooks.solidcredentialmanager.IWebIdListCallback;

interface ICredentialManager {
    // The WebIDs stored in the SCM (identifiers only).
    oneway void listWebIds(IWebIdListCallback callback);

    // Asks for an access token for one WebID. The caller is identified by the system, never by
    // anything it sends. The reply always arrives on the callback.
    oneway void requestToken(String webId, ITokenCallback callback);
}
