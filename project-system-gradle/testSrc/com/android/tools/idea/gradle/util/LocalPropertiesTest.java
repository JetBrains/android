/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.SdkConstants.NDK_DIR_PROPERTY;
import static com.android.SdkConstants.SDK_DIR_PROPERTY;
import static com.intellij.openapi.util.io.FileUtil.toCanonicalPath;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.SdkConstants;
import com.android.tools.idea.io.FilePaths;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.io.Files;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.ApplicationRule;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Properties;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests for {@link LocalProperties}.
 */
public class LocalPropertiesTest {
  @ClassRule
  public static ApplicationRule myApplicationRule = new ApplicationRule();
  @Rule
  public final TemporaryFolder myTmpFolder = new TemporaryFolder();
  private File myLocalPropertiesFile;
  private LocalProperties myLocalProperties;

  @Before
  public void setUp() throws IOException {
    myTmpFolder.create();
    myLocalPropertiesFile = myTmpFolder.getRoot();
    myLocalProperties = new LocalProperties(myLocalPropertiesFile);
  }

  // See https://code.google.com/p/android/issues/detail?id=82184
  @Test
  public void testGetAndroidSdkPathWithSeparatorDifferentThanPlatformOne() {
    if (!SystemInfo.isWindows) {
      String path = Joiner.on('\\').join("C:", "dir", "file");
      myLocalProperties.properties().setProperty(SDK_DIR_PROPERTY, path);

      File actual = myLocalProperties.getAndroidSdkPath();
      assertNotNull(actual);
      assertEquals(path, actual.getPath());
    }
  }

  @Test
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

  @Test
  public void testCreateFileOnSave() throws Exception {
    myLocalProperties.setAndroidSdkPath("~/sdk");
    myLocalProperties.save();
    File localPropertiesFile = new File(myLocalPropertiesFile, SdkConstants.FN_LOCAL_PROPERTIES);
    assertTrue(localPropertiesFile.isFile());
  }

  @Test
  public void testSetAndroidSdkPathWithFile() throws Exception {
    File androidSdkPath = FilePaths.stringToFile("/home/sdk2");
    myLocalProperties.setAndroidSdkPath(androidSdkPath);
    myLocalProperties.save();

    File actual = myLocalProperties.getAndroidSdkPath();
    assertNotNull(actual);
    assertEquals(toCanonicalPath(androidSdkPath.getPath()), toCanonicalPath(actual.getPath()));
  }

  @Test
  public void testSetAndroidSdkPathWithString() throws Exception {
    String androidSdkPath = toSystemDependentName("/home/sdk2");
    myLocalProperties.setAndroidSdkPath(androidSdkPath);
    myLocalProperties.save();

    File actual = myLocalProperties.getAndroidSdkPath();
    assertNotNull(actual);
    assertEquals(toCanonicalPath(androidSdkPath), toCanonicalPath(actual.getPath()));
  }

  @Test
  public void testSetAndroidSdkPathWithSdk() throws Exception {
    String androidSdkPath = toSystemDependentName("/home/sdk2");
    Sdk sdk = mock(Sdk.class);
    when(sdk.getHomePath()).thenReturn(androidSdkPath);
    myLocalProperties.setAndroidSdkPath(sdk);
    myLocalProperties.save();

    File actual = myLocalProperties.getAndroidSdkPath();
    assertNotNull(actual);
    assertEquals(toCanonicalPath(androidSdkPath), toCanonicalPath(actual.getPath()));
  }

  @Test
  public void testSetAndroidNdkPathWithString() throws Exception {
    String androidNdkPath = toSystemDependentName("/home/ndk2");
    myLocalProperties.setAndroidNdkPath(androidNdkPath);
    myLocalProperties.save();

    File actual = myLocalProperties.getAndroidNdkPath();
    assertNotNull(actual);
    assertEquals(toCanonicalPath(androidNdkPath), toCanonicalPath(actual.getPath()));
  }

  @Test
  public void testSetAndroidNdkPathWithFile() throws Exception {
    String androidNdkPath = toSystemDependentName("/home/ndk2");
    myLocalProperties.setAndroidNdkPath(new File(androidNdkPath));
    myLocalProperties.save();

    File actual = myLocalProperties.getAndroidNdkPath();
    assertNotNull(actual);
    assertEquals(toCanonicalPath(androidNdkPath), toCanonicalPath(actual.getPath()));
  }

  @Test
  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void testUnicodeLoad() throws Exception {
    File localPropertiesFile = new File(myLocalPropertiesFile, SdkConstants.FN_LOCAL_PROPERTIES);
    File tempDir = Files.createTempDir();
    File sdk = new File(tempDir, "\u00C6\u0424");
    sdk.mkdirs();

    Properties outProperties = new Properties();
    outProperties.setProperty(SDK_DIR_PROPERTY, sdk.getPath());

    // First write properties using the default encoding (which will \\u escape all non-iso-8859 chars)
    PropertiesFiles.savePropertiesToFile(outProperties, localPropertiesFile, null);

    // Read back platform default version of string; confirm that it gets converted properly
    LocalProperties properties1 = new LocalProperties(myLocalPropertiesFile);
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
    LocalProperties properties2 = new LocalProperties(myLocalPropertiesFile);
    File sdkPath2 = properties2.getAndroidSdkPath();
    assertNotNull(sdkPath2);
    assertTrue(sdkPath2.exists());
    assertTrue(FileUtil.filesEqual(sdk, sdkPath2));

    sdk.delete();
    tempDir.delete();
  }
}
