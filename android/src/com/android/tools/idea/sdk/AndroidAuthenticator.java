/*
 * Copyright (C) 2015-2020 The Android Open Source Project
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
package com.android.tools.idea.sdk;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.credentialStore.OneTimeString;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.util.proxy.CommonProxy;
import com.intellij.util.proxy.NonStaticAuthenticator;
import java.net.PasswordAuthentication;
import java.net.URL;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidAuthenticator extends NonStaticAuthenticator {
  @Override
  @Nullable
  public PasswordAuthentication getPasswordAuthentication() {
    URL url = getRequestingURL();
    if (url != null) {
      String host = url.toString();
      PasswordAuthentication result = getAuthentication(host);
      if (result != null) {
        return result;
      }
    }
    return getAuthentication(CommonProxy.getHostNameReliably(getRequestingHost(), getRequestingSite(), getRequestingURL()));
  }

  public static String getCredentialServiceName(@NotNull String host) {
    return "AndroidSdk:" + host;
  }

  @Nullable
  public static PasswordAuthentication getAuthentication(@NotNull String host) {
    Credentials credentials = PasswordSafe.getInstance().get(new CredentialAttributes(getCredentialServiceName(host)));
    if (credentials != null) {
      OneTimeString password = credentials.getPassword();
      if (password != null) {
        return new PasswordAuthentication(credentials.getUserName(), password.toCharArray());
      }
    }
    return null;
  }
}
