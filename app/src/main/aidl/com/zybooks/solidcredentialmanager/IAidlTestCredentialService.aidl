// IAidlTestCredentialService.aidl
package com.zybooks.solidcredentialmanager;

import com.zybooks.solidcredentialmanager.IAccessRequestCallback;
import com.zybooks.solidcredentialmanager.IWebIdRequestCallback;

// Declare any non-default types here with import statements

interface IAidlTestCredentialService {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */
    void basicTypes(int anInt, long aLong, boolean aBoolean, float aFloat,
            double aDouble, String aString);

    void registerAccessRequestCallback(IAccessRequestCallback cb);
    void requestWebId(String webId, String packageName, long requestTime);
    void getWebIds(IWebIdRequestCallback cb);
}