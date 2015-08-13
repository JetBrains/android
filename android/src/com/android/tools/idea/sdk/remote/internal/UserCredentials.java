/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.idea.sdk.remote.internal;

public class UserCredentials {
    private final String mUserName;
    private final String mPassword;
    private final String mWorkstation;
    private final String mDomain;

    public UserCredentials(String userName, String password, String workstation, String domain) {
        mUserName = userName;
        mPassword = password;
        mWorkstation = workstation;
        mDomain = domain;
    }

    public String getUserName() {
        return mUserName;
    }

    public String getPassword() {
        return mPassword;
    }

    public String getWorkstation() {
        return mWorkstation;
    }

    public String getDomain() {
        return mDomain;
    }
}
