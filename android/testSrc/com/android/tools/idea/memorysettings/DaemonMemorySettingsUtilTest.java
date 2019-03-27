/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.memorysettings;

import static com.android.tools.idea.memorysettings.DaemonMemorySettingsUtil.getGradleDaemonXmx;
import static com.android.tools.idea.memorysettings.DaemonMemorySettingsUtil.setGradleDaemonXmx;
import static com.android.tools.idea.memorysettings.DaemonMemorySettingsUtil.setXmxInVmArgs;

import com.android.tools.idea.gradle.util.GradleProperties;
import com.intellij.testFramework.IdeaTestCase;
import java.io.File;

public class DaemonMemorySettingsUtilTest extends IdeaTestCase {

  public void testNoGradleDaemonXmx() throws Exception {
    File propertiesFilePath = createTempFile("gradle.properties", "");
    GradleProperties properties = new GradleProperties(propertiesFilePath);
    assertEquals(MemorySettingsUtil.NO_XMX_IN_VM_ARGS,
                 getGradleDaemonXmx(properties));

    propertiesFilePath = createTempFile("gradle.properties", "org.gradle.jvmargs=");
    properties = new GradleProperties(propertiesFilePath);
    assertEquals(MemorySettingsUtil.NO_XMX_IN_VM_ARGS,
                 getGradleDaemonXmx(properties));

    propertiesFilePath = createTempFile("gradle.properties", "# org.gradle.jvmargs=-Xmx1024M");
    properties = new GradleProperties(propertiesFilePath);
    assertEquals(MemorySettingsUtil.NO_XMX_IN_VM_ARGS,
                 getGradleDaemonXmx(properties));
  }

  public void testGetGradleDaemonXmx() throws Exception {
    File propertiesFilePath = createTempFile("gradle.properties", "org.gradle.jvmargs=-Xmx");
    GradleProperties properties = new GradleProperties(propertiesFilePath);
    assertEquals(-1, getGradleDaemonXmx(properties));

    propertiesFilePath = createTempFile("gradle.properties", "org.gradle.jvmargs=-XmxT");
    properties = new GradleProperties(propertiesFilePath);
    assertEquals(-1, getGradleDaemonXmx(properties));

    propertiesFilePath = createTempFile("gradle.properties", "org.gradle.jvmargs=-Xmx1a");
    properties = new GradleProperties(propertiesFilePath);
    assertEquals(-1, getGradleDaemonXmx(properties));

    propertiesFilePath = createTempFile("gradle.properties", "org.gradle.jvmargs=-Xmx1T");
    properties = new GradleProperties(propertiesFilePath);
    assertEquals(1024 * 1024, getGradleDaemonXmx(properties));

    propertiesFilePath = createTempFile("gradle.properties", "org.gradle.jvmargs=-Xmx2t");
    properties = new GradleProperties(propertiesFilePath);
    assertEquals(2 * 1024 * 1024, getGradleDaemonXmx(properties));

    propertiesFilePath = createTempFile("gradle.properties", "org.gradle.jvmargs=-Xmx1G");
    properties = new GradleProperties(propertiesFilePath);
    assertEquals(1024, getGradleDaemonXmx(properties));

    propertiesFilePath = createTempFile("gradle.properties", "org.gradle.jvmargs=-Xmx4g");
    properties = new GradleProperties(propertiesFilePath);
    assertEquals(4 * 1024, getGradleDaemonXmx(properties));

    propertiesFilePath = createTempFile("gradle.properties", "org.gradle.jvmargs=-Xmx1M");
    properties = new GradleProperties(propertiesFilePath);
    assertEquals(1, getGradleDaemonXmx(properties));

    propertiesFilePath = createTempFile("gradle.properties", "org.gradle.jvmargs=-Xmx10m");
    properties = new GradleProperties(propertiesFilePath);
    assertEquals(10, getGradleDaemonXmx(properties));

    propertiesFilePath = createTempFile("gradle.properties", "org.gradle.jvmargs=-Xmx1024K");
    properties = new GradleProperties(propertiesFilePath);
    assertEquals(1, getGradleDaemonXmx(properties));

    propertiesFilePath = createTempFile("gradle.properties", "org.gradle.jvmargs=-Xmx2048k");
    properties = new GradleProperties(propertiesFilePath);
    assertEquals(2, getGradleDaemonXmx(properties));
  }

  public void testSetGradleDaemonXmx() throws Exception {
    File propertiesFilePath = createTempFile("gradle.properties", "");
    GradleProperties properties = new GradleProperties(propertiesFilePath);
    setGradleDaemonXmx(properties, 10);
    assertEquals(10, getGradleDaemonXmx(properties));

    propertiesFilePath = createTempFile("gradle.properties", "#org.gradle.jvmargs=-Xms1280m");
    properties = new GradleProperties(propertiesFilePath);
    setGradleDaemonXmx(properties, 10);
    assertEquals(10, getGradleDaemonXmx(properties));

    propertiesFilePath = createTempFile("gradle.properties", "org.gradle.jvmargs=-Xms1280m");
    properties = new GradleProperties(propertiesFilePath);
    setGradleDaemonXmx(properties, 1024);
    assertEquals(1024, getGradleDaemonXmx(properties));

    propertiesFilePath = createTempFile("gradle.properties", "org.gradle.jvmargs=-Xmx1280m");
    properties = new GradleProperties(propertiesFilePath);
    setGradleDaemonXmx(properties, 2048);
    assertEquals(2048, getGradleDaemonXmx(properties));
  }

  public void testSetDaemonXmx() throws Exception {
    assertEquals("-Xmx1024M", setXmxInVmArgs("", 1024));
    assertEquals("-Xms512m -Xmx1024M", setXmxInVmArgs("-Xms512m", 1024));
    assertEquals("-Xmx1024M", setXmxInVmArgs("-Xmx512m", 1024));
    assertEquals("-Xms512m -Xmx2048M",
                 setXmxInVmArgs("-Xms512m -Xmx1G", 2048));
    assertEquals("-Xms512m -Xmx768m -Xmx2048M",
                 setXmxInVmArgs("-Xms512m -Xmx768m -Xmx1G", 2048));
  }
}
