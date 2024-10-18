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
package com.android.tools.idea.gradle.util;

import static com.intellij.util.net.ProxyConfiguration.ProxyProtocol.HTTP;

import com.google.common.collect.Comparators;
import com.google.common.collect.ContiguousSet;
import com.intellij.credentialStore.Credentials;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.util.net.ProxyConfiguration;
import com.intellij.util.net.ProxyConfiguration.StaticProxyConfiguration;
import com.intellij.util.net.ProxySettings;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Properties;

/**
 * Tests for {@link GradleProperties}.
 */
public class GradlePropertiesTest extends HeavyPlatformTestCase {
  private GradleProperties myProperties;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    File propertiesFilePath = createTempFile("gradle.properties", "");
    myProperties = new GradleProperties(propertiesFilePath);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      ProxySettings.getInstance().setProxyConfiguration(ProxySettings.getDefaultProxyConfiguration());
    } finally {
      super.tearDown();
    }
  }

  public void testCopyProxySettingsFromIde() {
    String host = "myproxy.test.com";
    int port = 443;

    IdeProxyInfo info = IdeProxyInfo.getInstance();
    ProxySettings ideSettings = info.getSettings();
    StaticProxyConfiguration configuration = ProxyConfiguration.proxy(HTTP, host, port, "");
    ideSettings.setProxyConfiguration(configuration);
    IdeGradleProxySettingsBridge proxySettings = new IdeGradleProxySettingsBridge(info, configuration);

    assertEquals(host, proxySettings.getHost());
    assertEquals(port, proxySettings.getPort());
    assertNull(proxySettings.getUser());
    assertNull(proxySettings.getPassword());
    assertEmpty(proxySettings.getExceptions());
  }

  public void testSetProxySettings() {
    String host = "myproxy.test.com";
    int port = 443;
    String user = "johndoe";
    String password = "123456";

    IdeProxyInfo info = IdeProxyInfo.getInstance();
    StaticProxyConfiguration configuration = ProxyConfiguration.proxy(HTTP, host, port, "");
    info.getSettings().setProxyConfiguration(configuration);
    info.getCredentialStore().setCredentials(host, port, new Credentials(user, password), false);
    IdeGradleProxySettingsBridge ideProxySettings = new IdeGradleProxySettingsBridge(info, configuration);

    // Verify that the proxy settings are stored properly in the actual properties file.
    ideProxySettings.applyProxySettings(myProperties.getProperties());

    assertEquals(host, myProperties.getProperty("systemProp.http.proxyHost"));
    assertEquals(String.valueOf(port), myProperties.getProperty("systemProp.http.proxyPort"));
    assertEquals(user, myProperties.getProperty("systemProp.http.proxyUser"));
    assertEquals(password, myProperties.getProperty("systemProp.http.proxyPassword"));

    IdeGradleProxySettingsBridge gradleProxySetting = myProperties.getHttpProxySettings();
    assertEquals(host, gradleProxySetting.getHost());
    assertEquals(port, gradleProxySetting.getPort());

    // Verify that username is removed but password not if authentication is disabled in IDE settings.
    info.getCredentialStore().setCredentials(host, port, null, false);

    ideProxySettings = new IdeGradleProxySettingsBridge(info, configuration);
    ideProxySettings.applyProxySettings(myProperties.getProperties());

    assertNull(myProperties.getProperty("systemProp.http.proxyUser"));
    assertEquals(password, myProperties.getProperty("systemProp.http.proxyPassword"));
  }

  /**
   * Verify that the content of getSortedProperties is enumerated lexicographically. This method is used while saving files.
   */
  public void testGetSortedProperties() {
    // Add a sequence of keys permuted randomly
    Properties properties = myProperties.getProperties();
    ArrayList<Integer> permutation = new ArrayList<>(ContiguousSet.closedOpen(0, 20));
    Collections.shuffle(permutation);
    permutation.forEach(index -> properties.setProperty(String.format("key%02d", index), String.format("value%02d", index)));
    // Add some common properties
    IdeProxyInfo info = IdeProxyInfo.getInstance();
    StaticProxyConfiguration configuration = ProxyConfiguration.proxy(HTTP, "myproxy.test.com", 443, "");
    info.getSettings().setProxyConfiguration(configuration);
    info.getCredentialStore().setCredentials("myproxy.test.com", 443, new Credentials("johndoe", "123456"), false);
    IdeGradleProxySettingsBridge ideProxySettings = new IdeGradleProxySettingsBridge(info, configuration);
    ideProxySettings.applyProxySettings(properties);
    properties.setProperty("org.gradle.parallel", "true");
    properties.setProperty("org.gradle.jvmargs", "-Xmx2g -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8");

    // Generated sorted properties
    Properties sortedProperties = myProperties.getSortedProperties();
    ArrayList<Object> list = Collections.list(sortedProperties.keys());

    assertSameElements(list, properties.keySet());
    assertTrue("getSortedProperties should be sorted: " + list, Comparators.isInOrder(list, Comparator.comparing(obj -> (String)obj)));
  }
}
