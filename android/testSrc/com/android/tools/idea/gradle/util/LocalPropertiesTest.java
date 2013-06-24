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

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.IdeaTestCase;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import static org.easymock.EasyMock.*;

/**
 * Tests for {@link LocalProperties}.
 */
public class LocalPropertiesTest extends IdeaTestCase {
  private Sdk androidSdk;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    androidSdk = createMock(Sdk.class);
  }

  public void testCreateAndReadFile() throws Exception {
    String androidSdkPath = createLocalPropertiesFile();

    File localPropertiesFile = new File(myProject.getBasePath(), "local.properties");
    assertTrue(localPropertiesFile.isFile());

    Properties properties = LocalProperties.readFile(myProject);
    assertNotNull(properties);

    assertEquals(androidSdkPath, properties.getProperty("sdk.dir"));
    assertEquals(androidSdkPath, new LocalProperties(myProject).getAndroidSdkPath());
  }

  public void testSetAndroidSdkPath() throws Exception {
    createLocalPropertiesFile();
    LocalProperties properties = new LocalProperties(myProject);

    String androidSdkPath = "/home/sdk2";
    properties.setAndroidSdkPath(androidSdkPath);
    assertEquals(androidSdkPath, properties.getAndroidSdkPath());
  }

  private String createLocalPropertiesFile() throws IOException {
    String androidSdkPath = "/home/sdk";

    expect(androidSdk.getHomePath()).andReturn(androidSdkPath);
    replay(androidSdk);

    LocalProperties.createFile(getProject(), androidSdk);

    verify(androidSdk);
    return androidSdkPath;
  }
}
