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

import com.google.common.collect.Comparators;
import com.google.common.collect.ContiguousSet;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.util.net.HttpConfigurable;
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
  private HttpConfigurable myOriginalIdeSettings;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    File propertiesFilePath = createTempFile("gradle.properties", "");
    myProperties = new GradleProperties(propertiesFilePath);
    myOriginalIdeSettings = HttpConfigurable.getInstance().getState();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      HttpConfigurable.getInstance().loadState(myOriginalIdeSettings);
    } finally {
      super.tearDown();
    }
  }

  public void testCopyProxySettingsFromIde() {
    String host = "myproxy.test.com";
    int port = 443;

    HttpConfigurable ideSettings = HttpConfigurable.getInstance();
    ideSettings.USE_HTTP_PROXY = true;
    ideSettings.PROXY_HOST = host;
    ideSettings.PROXY_PORT = port;

    IdeGradleProxySettingsBridge proxySettings = new IdeGradleProxySettingsBridge(ideSettings);

    assertEquals(host, proxySettings.getHost());
    assertEquals(port, proxySettings.getPort());
  }

  public void testSetProxySettings() {
    String host = "myproxy.test.com";
    int port = 443;
    String user = "johndoe";
    String password = "123456";

    HttpConfigurable ideSettings = HttpConfigurable.getInstance();
    ideSettings.USE_HTTP_PROXY = true;
    ideSettings.PROXY_HOST = host;
    ideSettings.PROXY_PORT = port;
    ideSettings.PROXY_AUTHENTICATION = true;
    ideSettings.setProxyLogin(user);
    ideSettings.setPlainProxyPassword(password);

    IdeGradleProxySettingsBridge ideProxySettings = new IdeGradleProxySettingsBridge(ideSettings);

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
    ideSettings.PROXY_AUTHENTICATION = false;

    ideProxySettings = new IdeGradleProxySettingsBridge(ideSettings);
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
    HttpConfigurable ideSettings = HttpConfigurable.getInstance();
    ideSettings.USE_HTTP_PROXY = true;
    ideSettings.PROXY_HOST = "myproxy.test.com";
    ideSettings.PROXY_PORT = 443;
    ideSettings.PROXY_AUTHENTICATION = true;
    ideSettings.setProxyLogin("johndoe");
    ideSettings.setPlainProxyPassword("123456");
    IdeGradleProxySettingsBridge ideProxySettings = new IdeGradleProxySettingsBridge(ideSettings);
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
