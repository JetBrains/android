/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.SdkConstants;
import com.android.tools.idea.io.FilePaths;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.io.Files;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.LightPlatformTestCase;

import java.io.*;
import java.util.Properties;

import static com.android.SdkConstants.GRADLE_JDK_DIR_PROPERTY;
import static com.android.SdkConstants.NDK_DIR_PROPERTY;
import static com.android.SdkConstants.SDK_DIR_PROPERTY;
import static com.intellij.openapi.util.io.FileUtil.toCanonicalPath;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static org.easymock.EasyMock.*;

/**
 * Tests for {@link LocalProperties}.
 */
public class LocalPropertiesTest extends LightPlatformTestCase {
  private LocalProperties myLocalProperties;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myLocalProperties = new LocalProperties(getProject());
  }

  // See https://code.google.com/p/android/issues/detail?id=82184
  public void testGetAndroidSdkPathWithSeparatorDifferentThanPlatformOne() throws IOException {
    if (!SystemInfo.isWindows) {
      String path = Joiner.on('\\').join("C:", "dir", "file");
      myLocalProperties.properties().setProperty(SDK_DIR_PROPERTY, path);

      File actual = myLocalProperties.getAndroidSdkPath();
      assertNotNull(actual);
      assertEquals(path, actual.getPath());
    }
  }

  public void testGetAndroidNdkPathWithSeparatorDifferentThanPlatformOne() throws IOException {
    if (!SystemInfo.isWindows) {
      String path = Joiner.on('\\').join("C:", "dir", "file");
      myLocalProperties.properties().setProperty(NDK_DIR_PROPERTY, path);
      myLocalProperties.save();

      File actual = myLocalProperties.getAndroidNdkPath();
      assertNotNull(actual);
      assertEquals(path, actual.getPath());
    }
  }

  public void testGetGradleJdkPathWithSeparatorDifferentThanPlatformOne() throws IOException {
    if (!SystemInfo.isWindows) {
      String path = Joiner.on('\\').join("C:", "dir", "file");
      myLocalProperties.properties().setProperty(GRADLE_JDK_DIR_PROPERTY, path);
      myLocalProperties.save();

      File actual = myLocalProperties.getGradleJdkPath();
      assertNotNull(actual);
      assertEquals(path, actual.getPath());
    }
  }

  public void testCreateFileOnSave() throws Exception {
    myLocalProperties.setAndroidSdkPath("~/sdk");
    myLocalProperties.save();
    File localPropertiesFile = new File(getProject().getBasePath(), SdkConstants.FN_LOCAL_PROPERTIES);
    assertTrue(localPropertiesFile.isFile());
  }

  public void testSetAndroidSdkPathWithFile() throws Exception {
    File androidSdkPath = FilePaths.stringToFile("/home/sdk2");
    myLocalProperties.setAndroidSdkPath(androidSdkPath);
    myLocalProperties.save();

    File actual = myLocalProperties.getAndroidSdkPath();
    assertNotNull(actual);
    assertEquals(toCanonicalPath(androidSdkPath.getPath()), toCanonicalPath(actual.getPath()));
  }

  public void testSetAndroidSdkPathWithString() throws Exception {
    String androidSdkPath = toSystemDependentName("/home/sdk2");
    myLocalProperties.setAndroidSdkPath(androidSdkPath);
    myLocalProperties.save();

    File actual = myLocalProperties.getAndroidSdkPath();
    assertNotNull(actual);
    assertEquals(toCanonicalPath(androidSdkPath), toCanonicalPath(actual.getPath()));
  }

  public void testSetAndroidSdkPathWithSdk() throws Exception {
    String androidSdkPath = toSystemDependentName("/home/sdk2");

    Sdk sdk = createMock(Sdk.class);
    expect(sdk.getHomePath()).andStubReturn(androidSdkPath);
    replay(sdk);

    myLocalProperties.setAndroidSdkPath(sdk);
    verify(sdk);

    myLocalProperties.save();

    File actual = myLocalProperties.getAndroidSdkPath();
    assertNotNull(actual);
    assertEquals(toCanonicalPath(androidSdkPath), toCanonicalPath(actual.getPath()));
  }

  public void testSetGradleJdkPathWithString() throws Exception {
    String gradleJdkPath = toSystemDependentName("/home/jdk");
    myLocalProperties.setGradleJdkPath(gradleJdkPath);
    myLocalProperties.save();

    File actual = myLocalProperties.getGradleJdkPath();
    assertNotNull(actual);
    assertEquals(toCanonicalPath(gradleJdkPath), toCanonicalPath(actual.getPath()));
  }

  public void testSetGradleJdkPathWithFile() throws Exception {
    String gradleJdkPath = toSystemDependentName("/home/jdk");
    myLocalProperties.setGradleJdkPath(new File(gradleJdkPath));
    myLocalProperties.save();

    File actual = myLocalProperties.getGradleJdkPath();
    assertNotNull(actual);
    assertEquals(toCanonicalPath(gradleJdkPath), toCanonicalPath(actual.getPath()));
  }

  public void testSetAndroidNdkPathWithString() throws Exception {
    String androidNdkPath = toSystemDependentName("/home/ndk2");
    myLocalProperties.setAndroidNdkPath(androidNdkPath);
    myLocalProperties.save();

    File actual = myLocalProperties.getAndroidNdkPath();
    assertNotNull(actual);
    assertEquals(toCanonicalPath(androidNdkPath), toCanonicalPath(actual.getPath()));
  }

  public void testSetAndroidNdkPathWithFile() throws Exception {
    String androidNdkPath = toSystemDependentName("/home/ndk2");
    myLocalProperties.setAndroidNdkPath(new File(androidNdkPath));
    myLocalProperties.save();

    File actual = myLocalProperties.getAndroidNdkPath();
    assertNotNull(actual);
    assertEquals(toCanonicalPath(androidNdkPath), toCanonicalPath(actual.getPath()));
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void testUnicodeLoad() throws Exception {
    File localPropertiesFile = new File(getProject().getBasePath(), SdkConstants.FN_LOCAL_PROPERTIES);
    File tempDir = Files.createTempDir();
    File sdk = new File(tempDir, "\u00C6\u0424");
    sdk.mkdirs();

    Properties outProperties = new Properties();
    outProperties.setProperty(SDK_DIR_PROPERTY, sdk.getPath());

    // First write properties using the default encoding (which will \\u escape all non-iso-8859 chars)
    PropertiesFiles.savePropertiesToFile(outProperties, localPropertiesFile, null);

    // Read back platform default version of string; confirm that it gets converted properly
    LocalProperties properties1 = new LocalProperties(getProject());
    File sdkPath1 = properties1.getAndroidSdkPath();
    assertNotNull(sdkPath1);
    assertTrue(sdkPath1.exists());
    assertTrue(FileUtil.filesEqual(sdk, sdkPath1));

    // Next write properties using the UTF-8 encoding. Chars will no longer be escaped.
    // Confirm that we read these in properly too.
    try (Writer writer = new OutputStreamWriter(new FileOutputStream(localPropertiesFile), Charsets.UTF_8)) {
      outProperties.store(writer, null);
    }

    // Read back platform default version of string; confirm that it gets converted properly
    LocalProperties properties2 = new LocalProperties(getProject());
    File sdkPath2 = properties2.getAndroidSdkPath();
    assertNotNull(sdkPath2);
    assertTrue(sdkPath2.exists());
    assertTrue(FileUtil.filesEqual(sdk, sdkPath2));

    sdk.delete();
    tempDir.delete();
  }
}
