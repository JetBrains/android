/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.updater;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import org.jetbrains.android.AndroidTestCase;

import java.net.Authenticator;
import java.net.InetAddress;
import java.net.PasswordAuthentication;
import java.net.URL;

/**
 * Tests for {@link AndroidSdkUpdaterPlugin}
 */
public class AndroidSdkUpdaterPluginTest extends AndroidTestCase {
  public void testAuthenticator() throws Exception {
    String url = "http://example.com/foo/bar.xml" + System.currentTimeMillis();
    String serviceName = AndroidSdkUpdaterPlugin.getCredentialServiceName(url);
    String user = "testUser";
    String password = "testPassword" + System.currentTimeMillis();
    PasswordSafe.getInstance().set(new CredentialAttributes(serviceName), new Credentials(user, password), true);
    PasswordAuthentication auth = Authenticator.requestPasswordAuthentication("example.com", InetAddress.getByName("example.com"), 80,
                                                                              "http", "Server authentication: foo", "basic", new URL(url),
                                                                              Authenticator.RequestorType.SERVER);
    assertEquals(user, auth.getUserName());
    assertEquals(password, new String(auth.getPassword()));
  }
}
