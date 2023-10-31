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
package com.android.tools.idea.gradle.project;

import static com.android.tools.idea.gradle.util.ProxySettings.HTTP_PROXY_TYPE;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.Disposer.dispose;
import static com.intellij.openapi.util.Disposer.isDisposed;

import com.android.tools.idea.gradle.util.ProxySettings;
import com.intellij.openapi.Disposable;
import com.intellij.testFramework.LightPlatformTestCase;
import java.util.Properties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tests for {@link ProxySettingsDialog}.
 */
public class ProxySettingsDialogTest extends LightPlatformTestCase {
  private ProxySettingsDialog myDialog;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myDialog = new ProxySettingsDialog(getProject(), new ProxySettings(HTTP_PROXY_TYPE), true);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myDialog != null) {
        Disposable disposable = myDialog.getDisposable();
        if (!isDisposed(disposable)) {
          dispose(disposable);
        }
      }
    }
    finally {
      super.tearDown();
    }
  }

  public void testApplyHttpProxySettingsWithAuthentication() {
    myDialog.setHttpsProxyEnabled(false);
    String host = "tatooine.com";
    myDialog.setHttpProxyHost(host);

    int port = 88;
    myDialog.setHttpPortNumber(port);

    String exception = "exception1";
    myDialog.setHttpProxyException(exception);

    myDialog.setHttpProxyAuthenticationEnabled(true);

    String login = "luke";
    myDialog.setHttpProxyLogin(login);

    Properties properties = new Properties();
    assertTrue(myDialog.applyProxySettings(properties));

    assertEquals(host, getHttpProxyHost(properties));
    assertEquals(port, getHttpProxyPort(properties));
    assertEquals(exception, getHttpNonProxyHosts(properties));
    assertEquals(login, getHttpProxyUser(properties));

    // Verify http://b/63914231
    assertThat(getHttpProxyPassword(properties)).isEmpty();
  }

  public void testApplyHttpProxySettingsWithAuthenticationWithPassword() {
    myDialog.setHttpsProxyEnabled(false);
    String host = "tatooine.com";
    myDialog.setHttpProxyHost(host);

    int port = 88;
    myDialog.setHttpPortNumber(port);

    String exception = "exception1";
    myDialog.setHttpProxyException(exception);

    myDialog.setHttpProxyAuthenticationEnabled(true);

    String login = "luke";
    myDialog.setHttpProxyLogin(login);

    Properties properties = new Properties();
    String password = "myPass";
    properties.setProperty("systemProp.http.proxyPassword", password);
    assertFalse(myDialog.applyProxySettings(properties));

    assertEquals(host, getHttpProxyHost(properties));
    assertEquals(port, getHttpProxyPort(properties));
    assertEquals(exception, getHttpNonProxyHosts(properties));
    assertEquals(login, getHttpProxyUser(properties));

    // Verify password is not overwritten
    assertThat(getHttpProxyPassword(properties)).isEqualTo(password);
  }

  public void testApplyHttpProxySettingsWithoutAuthentication() {
    myDialog.setHttpsProxyEnabled(false);
    String host = "alderaan.com";
    myDialog.setHttpProxyHost(host);

    int port = 66;
    myDialog.setHttpPortNumber(port);

    String exception = "exception2";
    myDialog.setHttpProxyException(exception);

    myDialog.setHttpProxyAuthenticationEnabled(false);
    myDialog.setHttpProxyLogin("leia");

    Properties properties = new Properties();
    assertFalse(myDialog.applyProxySettings(properties));

    assertEquals(host, getHttpProxyHost(properties));
    assertEquals(port, getHttpProxyPort(properties));
    assertEquals(exception, getHttpNonProxyHosts(properties));
    assertNull(getHttpProxyUser(properties));
    assertNull(getHttpProxyPassword(properties));
  }

  @Nullable
  private static String getHttpProxyHost(@NotNull Properties properties) {
    return properties.getProperty("systemProp.http.proxyHost");
  }

  private static int getHttpProxyPort(@NotNull Properties properties) {
    return Integer.parseInt(properties.getProperty("systemProp.http.proxyPort"));
  }

  @Nullable
  private static String getHttpNonProxyHosts(@NotNull Properties properties) {
    return properties.getProperty("systemProp.http.nonProxyHosts");
  }

  @Nullable
  private static String getHttpProxyUser(@NotNull Properties properties) {
    return properties.getProperty("systemProp.http.proxyUser");
  }

  @Nullable
  private static String getHttpProxyPassword(@NotNull Properties properties) {
    return properties.getProperty("systemProp.http.proxyPassword");
  }

  public void testApplyHttpsProxySettingsWithAuthentication() {
    myDialog.setHttpsProxyEnabled(true);
    String host = "tatooine.com";
    myDialog.setHttpsProxyHost(host);

    int port = 88;
    myDialog.setHttpsPortNumber(port);

    String exception = "exception1";
    myDialog.setHttpsProxyException(exception);

    myDialog.setHttpsProxyAuthenticationEnabled(true);

    String login = "luke";
    myDialog.setHttpsProxyLogin(login);

    Properties properties = new Properties();
    myDialog.applyProxySettings(properties);

    assertEquals(host, getHttpsProxyHost(properties));
    assertEquals(port, getHttpsProxyPort(properties));
    assertEquals(exception, getHttpsNonProxyHosts(properties));
    assertEquals(login, getHttpsProxyUser(properties));

    // Verify http://b/63914231
    assertThat(getHttpsProxyPassword(properties)).isEmpty();
  }

  public void testApplyHttpsProxySettingsWithoutAuthentication() {
    myDialog.setHttpsProxyEnabled(true);
    String host = "alderaan.com";
    myDialog.setHttpsProxyHost(host);

    int port = 66;
    myDialog.setHttpsPortNumber(port);

    String exception = "exception2";
    myDialog.setHttpsProxyException(exception);

    myDialog.setHttpsProxyAuthenticationEnabled(false);
    myDialog.setHttpsProxyLogin("leia");

    Properties properties = new Properties();
    myDialog.applyProxySettings(properties);

    assertEquals(host, getHttpsProxyHost(properties));
    assertEquals(port, getHttpsProxyPort(properties));
    assertEquals(exception, getHttpsNonProxyHosts(properties));
    assertNull(getHttpsProxyUser(properties));
    assertNull(getHttpsProxyPassword(properties));
  }

  @Nullable
  private static String getHttpsProxyHost(@NotNull Properties properties) {
    return properties.getProperty("systemProp.https.proxyHost");
  }

  private static int getHttpsProxyPort(@NotNull Properties properties) {
    return Integer.parseInt(properties.getProperty("systemProp.https.proxyPort"));
  }

  @Nullable
  private static String getHttpsNonProxyHosts(@NotNull Properties properties) {
    return properties.getProperty("systemProp.https.nonProxyHosts");
  }

  @Nullable
  private static String getHttpsProxyUser(@NotNull Properties properties) {
    return properties.getProperty("systemProp.https.proxyUser");
  }

  @Nullable
  private static String getHttpsProxyPassword(@NotNull Properties properties) {
    return properties.getProperty("systemProp.https.proxyPassword");
  }

  public void testHttpsProxySettingsAreEnabledByDefault() {
    String host = "myhost.com";
    myDialog.setHttpsProxyHost(host);
    int port = 80;
    myDialog.setHttpsPortNumber(port);

    Properties properties = new Properties();
    myDialog.applyProxySettings(properties);

    assertEquals(host, getHttpsProxyHost(properties));
    assertEquals(port, getHttpsProxyPort(properties));
  }

  /**
   * Verify that the dialog text correctly indicates if the IDE is using a proxy
   */
  public void testDialogText() {
    assertThat(ProxySettingsDialog.generateDialogText(/* with proxy */ true)).contains("is configured to use an HTTP proxy");
    assertThat(ProxySettingsDialog.generateDialogText(/* without proxy */ false)).contains("is configured to not use an HTTP proxy");
  }
}