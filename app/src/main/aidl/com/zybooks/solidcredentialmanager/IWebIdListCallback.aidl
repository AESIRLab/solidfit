// IWebIdListCallback.aidl
package com.zybooks.solidcredentialmanager;

interface IWebIdListCallback {
    oneway void onWebIds(in List<String> webIds);
}
