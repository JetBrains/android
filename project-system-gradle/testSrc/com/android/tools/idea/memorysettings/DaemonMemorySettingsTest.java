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

import com.android.tools.idea.gradle.util.GradleProjectSystemUtil;
import com.android.tools.idea.gradle.util.GradleProperties;
import com.android.tools.idea.gradle.util.PropertiesFiles;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.testFramework.PlatformTestCase;
import java.io.File;
import java.io.IOException;
import java.util.Properties;
import org.jetbrains.annotations.NotNull;

public class DaemonMemorySettingsTest extends PlatformTestCase {

  private String myGradleUserHome;
  private GradleProperties myGradleProperties;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myGradleUserHome = System.getProperty("gradle.user.home");
    File gradlePropertiesFile = GradleProjectSystemUtil.getUserGradlePropertiesFile(myProject);
    if (gradlePropertiesFile.exists()) {
      myGradleProperties = new GradleProperties(gradlePropertiesFile);
    }
  }

  @Override
  public void tearDown() throws Exception {
    if (myGradleUserHome != null) {
      System.setProperty("gradle.user.home", myGradleUserHome);
    }
    else {
      System.clearProperty("gradle.user.home");
    }

    if (myGradleProperties != null) {
      // restore gradle.properties in user.home
      myGradleProperties.save();
    }
    else {
      File gradlePropertiesFile = GradleProjectSystemUtil.getUserGradlePropertiesFile(myProject);
      if (gradlePropertiesFile.exists()) {
        gradlePropertiesFile.delete();
      }
    }
    super.tearDown();
  }

  private DaemonMemorySettings getDaemonMemorySettings(String... propertiesContent) throws Exception {
    assertTrue(propertiesContent.length >= 3);
    System.clearProperty("gradle.user.home");


    File tempGradleUserHomeDir = createTempDir("gradle-user-home");
    if (propertiesContent[0] != null) {
      File gradleUserHomePropertiesFile = createFile(tempGradleUserHomeDir, "gradle.properties", propertiesContent[0]);
      System.setProperty("gradle.user.home", gradleUserHomePropertiesFile.getParent());
    } else {
      File file = new File(tempGradleUserHomeDir, "gradle.properties");
      if (file.exists()) {
        file.delete();
      }
    }

    File userHomeGradleDir = new File(System.getProperty("user.home"), ".gradle");
    if (propertiesContent[1] != null) {
      if (!userHomeGradleDir.exists()) {
        userHomeGradleDir.mkdir();
      }
      createFile(userHomeGradleDir, "gradle.properties", propertiesContent[1]);
    } else {
      File file = new File(userHomeGradleDir, "gradle.properties");
      if (file.exists()) {
        file.delete();
      }
    }

    File tempProjectDir = createTempDir("project");
    File projectPropertiesFile = createFile(tempProjectDir, "gradle.properties", propertiesContent[2]);

    return new DaemonMemorySettings(myProject, new GradleProperties(projectPropertiesFile));
  }

  private File createFile(File parentDir, String name, String content) throws Exception {
    File file = join(parentDir, name);
    if (!file.exists() && !file.createNewFile()) {
      throw new IOException("Can't create " + file);
    }
    if (content != null) {
      Properties properties = new Properties();
      properties.setProperty("org.gradle.jvmargs", content);
      PropertiesFiles.savePropertiesToFile(properties, file, null);
      LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
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
                               "-Xmx3G");
    checkXmxWithUserProperties(3072, 4096, null, null,
                               "-Xmx3G -Dkotlin.daemon.jvm.options=\"-Xmx4G\"");

    // User properties defined by gradle.user.home
    checkXmxWithUserProperties(1024, 1024,
                               "-Xmx1G",
                               null,
                               "-Xmx3G");
    checkXmxWithUserProperties(1024, 1024,
                               "-Xmx1G",
                               null,
                               "-Xmx3G -Dkotlin.daemon.jvm.options=\"-Xmx4G\"");
    checkXmxWithUserProperties(1024, 2048,
                               "-Xmx1G -Dkotlin.daemon.jvm.options=\"-Xmx2G\"",
                               null,
                               "-Xmx3G -Dkotlin.daemon.jvm.options=\"-Xmx4G\"");
    checkXmxWithUserProperties(-1, 2048,
                               "-Dkotlin.daemon.jvm.options=\"-Xmx2G\"",
                               null,
                               "-Xmx3G -Dkotlin.daemon.jvm.options=\"-Xmx4G\"");

    // User properties defined by GRADLE_USER_HOME
    checkXmxWithUserProperties(1024, 1024,
                               null,
                               "-Xmx1G",
                               "-Xmx3G");
    checkXmxWithUserProperties(1024, 1024,
                               null,
                               "-Xmx1G",
                               "-Xmx3G -Dkotlin.daemon.jvm.options=\"-Xmx4G\"");
    checkXmxWithUserProperties(1024, 2048,
                               null,
                               "-Xmx1G -Dkotlin.daemon.jvm.options=\"-Xmx2G\"",
                               "-Xmx3G -Dkotlin.daemon.jvm.options=\"-Xmx4G\"");
    checkXmxWithUserProperties(-1, 2048,
                               null,
                               "-Dkotlin.daemon.jvm.options=\"-Xmx2G\"",
                               "-Xmx3G -Dkotlin.daemon.jvm.options=\"-Xmx4G\"");

    // User properties defined by both gradle.user.home and GRADLE_USER_HOME
    checkXmxWithUserProperties(1024, 1024,
                               "-Xmx1G",
                               "-Xmx2G",
                               "-Xmx3G");
    checkXmxWithUserProperties(1024, 1024,
                               "-Xmx1G",
                               "-Xmx2G",
                               "-Xmx3G -Dkotlin.daemon.jvm.options=\"-Xmx4G\"");
    checkXmxWithUserProperties(1024, 2048,
                               "-Xmx1G -Dkotlin.daemon.jvm.options=\"-Xmx2G\"",
                               "-Xmx2G -Dkotlin.daemon.jvm.options=\"-Xmx3G\"",
                               "-Xmx3G -Dkotlin.daemon.jvm.options=\"-Xmx4G\"");
    checkXmxWithUserProperties(-1, 1024,
                               "-Dkotlin.daemon.jvm.options=\"-Xmx1G\"",
                               "-Xmx2G",
                               "-Xmx3G -Dkotlin.daemon.jvm.options=\"-Xmx4G\"");
  }

  private void checkUserPropertiesPath(boolean expectedHashUserPropertiesPath, String... gradlePropertiesContent) throws Exception {
    DaemonMemorySettings daemonMemorySettings = getDaemonMemorySettings(gradlePropertiesContent);
    assertEquals(expectedHashUserPropertiesPath, daemonMemorySettings.hasUserPropertiesPath());
    if (expectedHashUserPropertiesPath) {
      String expectedUserPropertiesPath =
        gradlePropertiesContent[0] != null ? getGradleUserHomePropertiesFilePath() : getUserHomePropertiesFilePath();
      assertEquals(expectedUserPropertiesPath.replace('/', File.separatorChar), daemonMemorySettings.getUserPropertiesPath());
    }
  }

  @NotNull
  private String getGradleUserHomePropertiesFilePath() {
    return join(System.getProperty("gradle.user.home"), "gradle.properties");
  }

  @NotNull
  private String getUserHomePropertiesFilePath() {
    return join(System.getProperty("user.home"), ".gradle", "gradle.properties");
  }

  public void testUserPropertiesPath() throws Exception {
    checkUserPropertiesPath(false, null, null, "");

    checkUserPropertiesPath(true,
                            "-Xmx1G", null, null);

    checkUserPropertiesPath(true,
                            null, "-Xmx1G", null);

    checkUserPropertiesPath(true,
                            "-Xms1G", null, null);

    checkUserPropertiesPath(true,
                            null, "-Xms1G", null);

    checkUserPropertiesPath(true,
                            "-Xms1G", "-Xmx2G", null);
  }
}
