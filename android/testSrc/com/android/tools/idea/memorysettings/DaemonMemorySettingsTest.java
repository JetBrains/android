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

import static com.android.utils.FileUtils.join;

import com.android.tools.idea.gradle.util.GradleProperties;
import com.android.utils.FileUtils;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.PlatformTestCase;
import java.io.File;
import java.io.IOException;
import org.junit.After;

public class DaemonMemorySettingsTest extends PlatformTestCase {

  private String myGradleUserHome;
  private String myUserHome;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myGradleUserHome = System.getProperty("gradle.user.home");
    myUserHome = System.getProperty("user.home");
  }

  @Override
  public void tearDown() throws Exception {
    if (myGradleUserHome != null) {
      System.setProperty("gradle.user.home", myGradleUserHome);
    }
    else {
      System.clearProperty("gradle.user.home");
    }

    if (myUserHome != null) {
      System.setProperty("user.home", myUserHome);
    }
    else {
      System.clearProperty("user.home");
    }
    super.tearDown();
  }

  private DaemonMemorySettings getDaemonMemorySettings(String... propertiesContent) throws Exception {
    assertTrue(propertiesContent.length >= 3);
    System.clearProperty("gradle.user.home");
    System.clearProperty("user.home");

    if (propertiesContent[0] != null) {
      File tempGradleUserHomeDir = createTempDir("gradle-user-home");
      File gradleUserHomePropertiesFile = createFile(tempGradleUserHomeDir, "gradle.properties", propertiesContent[0]);
      System.setProperty("gradle.user.home", gradleUserHomePropertiesFile.getParent());
    }

    if (propertiesContent[1] != null) {
      File tempHomeDir = createTempDir("home");
      System.setProperty("user.home", tempHomeDir.getPath());

      File gradleDir = new File(tempHomeDir.getPath() + File.separator + ".gradle");
      gradleDir.mkdir();
      createFile(gradleDir, "gradle.properties", propertiesContent[1]);
    }

    File tempProjectDir = createTempDir("project");
    File projectPropertiesFile = createFile(tempProjectDir, "gradle.properties", propertiesContent[2]);

    return new DaemonMemorySettings(new GradleProperties(projectPropertiesFile));
  }

  private File createFile(File parentDir, String name, String content) throws Exception {
    File file = join(parentDir, name);
    if (!file.exists() && !file.createNewFile()) {
      throw new IOException("Can't create " + file);
    }
    if (content != null) {
      FileUtil.writeToFile(file, content);
    }
    return file;
  }

  private void checkXmxWithUserProperties(int expectedGradleXmx, int expectedKotlinXmx,
                                          String... propertiesContent) throws Exception {
    DaemonMemorySettings daemonMemorySettings = getDaemonMemorySettings(propertiesContent);
    assertEquals(expectedGradleXmx, daemonMemorySettings.getProjectGradleDaemonXmx());
    assertEquals(expectedKotlinXmx, daemonMemorySettings.getProjectKotlinDaemonXmx());
  }

  public void testUserProperties() throws Exception {
    // No user properties
    checkXmxWithUserProperties(3072, 3072, null, null,
                               "org.gradle.jvmargs=-Xmx3G");
    checkXmxWithUserProperties(3072, 4096, null, null,
                               "org.gradle.jvmargs=-Xmx3G -Dkotlin.daemon.jvm.options=\"-Xmx4G\"");

    // User properties defined by gradle.user.home
    checkXmxWithUserProperties(1024, 1024,
                               "org.gradle.jvmargs=-Xmx1G",
                               null,
                               "org.gradle.jvmargs=-Xmx3G");
    checkXmxWithUserProperties(1024, 1024,
                               "org.gradle.jvmargs=-Xmx1G",
                               null,
                               "org.gradle.jvmargs=-Xmx3G -Dkotlin.daemon.jvm.options=\"-Xmx4G\"");
    checkXmxWithUserProperties(1024, 2048,
                               "org.gradle.jvmargs=-Xmx1G -Dkotlin.daemon.jvm.options=\"-Xmx2G\"",
                               null,
                               "org.gradle.jvmargs=-Xmx3G -Dkotlin.daemon.jvm.options=\"-Xmx4G\"");
    checkXmxWithUserProperties(-1, 2048,
                               "org.gradle.jvmargs=-Dkotlin.daemon.jvm.options=\"-Xmx2G\"",
                               null,
                               "org.gradle.jvmargs=-Xmx3G -Dkotlin.daemon.jvm.options=\"-Xmx4G\"");

    // User properties defined by GRADLE_USER_HOME
    checkXmxWithUserProperties(1024, 1024,
                               null,
                               "org.gradle.jvmargs=-Xmx1G",
                               "org.gradle.jvmargs=-Xmx3G");
    checkXmxWithUserProperties(1024, 1024,
                               null,
                               "org.gradle.jvmargs=-Xmx1G",
                               "org.gradle.jvmargs=-Xmx3G -Dkotlin.daemon.jvm.options=\"-Xmx4G\"");
    checkXmxWithUserProperties(1024, 2048,
                               null,
                               "org.gradle.jvmargs=-Xmx1G -Dkotlin.daemon.jvm.options=\"-Xmx2G\"",
                               "org.gradle.jvmargs=-Xmx3G -Dkotlin.daemon.jvm.options=\"-Xmx4G\"");
    checkXmxWithUserProperties(-1, 2048,
                               null,
                               "org.gradle.jvmargs=-Dkotlin.daemon.jvm.options=\"-Xmx2G\"",
                               "org.gradle.jvmargs=-Xmx3G -Dkotlin.daemon.jvm.options=\"-Xmx4G\"");

    // User properties defined by both gradle.user.home and GRADLE_USER_HOME
    checkXmxWithUserProperties(1024, 1024,
                               "org.gradle.jvmargs=-Xmx1G",
                               "org.gradle.jvmargs=-Xmx2G",
                               "org.gradle.jvmargs=-Xmx3G");
    checkXmxWithUserProperties(1024, 1024,
                               "org.gradle.jvmargs=-Xmx1G",
                               "org.gradle.jvmargs=-Xmx2G",
                               "org.gradle.jvmargs=-Xmx3G -Dkotlin.daemon.jvm.options=\"-Xmx4G\"");
    checkXmxWithUserProperties(1024, 2048,
                               "org.gradle.jvmargs=-Xmx1G -Dkotlin.daemon.jvm.options=\"-Xmx2G\"",
                               "org.gradle.jvmargs=-Xmx2G -Dkotlin.daemon.jvm.options=\"-Xmx3G\"",
                               "org.gradle.jvmargs=-Xmx3G -Dkotlin.daemon.jvm.options=\"-Xmx4G\"");
    checkXmxWithUserProperties(-1, 1024,
                               "org.gradle.jvmargs=-Dkotlin.daemon.jvm.options=\"-Xmx1G\"",
                               "org.gradle.jvmargs=-Xmx2G",
                               "org.gradle.jvmargs=-Xmx3G -Dkotlin.daemon.jvm.options=\"-Xmx4G\"");
  }

  private void checkUserPropertiesPath(boolean expectedHashUserPropertiesPath, String... gradlePropertiesContent) throws Exception {
    DaemonMemorySettings daemonMemorySettings = getDaemonMemorySettings(gradlePropertiesContent);
    assertEquals(expectedHashUserPropertiesPath, daemonMemorySettings.hasUserPropertiesPath());
    if (expectedHashUserPropertiesPath) {
      String expectedUserPropertiesPath =
        gradlePropertiesContent[0] != null ? getGradleUserHomePropertiesFilePath() : getUserHomePropertiesFilePath();
      assertEquals(expectedUserPropertiesPath, daemonMemorySettings.getUserPropertiesPath());
    }
  }

  private String getGradleUserHomePropertiesFilePath() {
    return join(System.getProperty("gradle.user.home"), "gradle.properties");
  }

  private String getUserHomePropertiesFilePath() {
    return join(System.getProperty("user.home"), ".gradle", "gradle.properties");
  }

  public void testUserPropertiesPath() throws Exception {
    checkUserPropertiesPath(false, "", "", "");

    checkUserPropertiesPath(false, "kotlin.code.style=official", "", "");

    checkUserPropertiesPath(false, "", "kotlin.code.style=official", "");

    checkUserPropertiesPath(true,
                            "org.gradle.jvmargs=-Xmx1G", null, "");

    checkUserPropertiesPath(true,
                            null, "org.gradle.jvmargs=-Xmx1G", "");

    checkUserPropertiesPath(true,
                            "org.gradle.jvmargs=-Xms1G", null, "");

    checkUserPropertiesPath(true,
                            null, "org.gradle.jvmargs=-Xms1G", "");

    checkUserPropertiesPath(true,
                            "org.gradle.jvmargs=-Xms1G", "org.gradle.jvmargs=-Xmx2G", "");
  }
}
