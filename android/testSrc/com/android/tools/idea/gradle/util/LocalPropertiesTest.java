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
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.IdeaTestCase;

import java.io.File;

import static org.easymock.EasyMock.*;

/**
 * Tests for {@link LocalProperties}.
 */
public class LocalPropertiesTest extends IdeaTestCase {
  private LocalProperties myLocalProperties;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myLocalProperties = new LocalProperties(myProject);
  }

  public void testCreateFileOnSave() throws Exception {
    myLocalProperties.save();
    File localPropertiesFile = new File(myProject.getBasePath(), SdkConstants.FN_LOCAL_PROPERTIES);
    assertTrue(localPropertiesFile.isFile());
  }

  public void testSetAndroidSdkPathWithString() throws Exception {
    String androidSdkPath = "/home/sdk2";
    myLocalProperties.setAndroidSdkPath(androidSdkPath);
    myLocalProperties.save();

    File actual = myLocalProperties.getAndroidSdkPath();
    assertNotNull(actual);
    assertEquals(FileUtil.toCanonicalPath(androidSdkPath), FileUtil.toCanonicalPath(actual.getPath()));
  }

  public void testSetAndroidSdkPathWithSdk() throws Exception {
    String androidSdkPath = "/home/sdk2";

    Sdk sdk = createMock(Sdk.class);
    expect(sdk.getHomePath()).andReturn(androidSdkPath);

    replay(sdk);

    myLocalProperties.setAndroidSdkPath(sdk);

    verify(sdk);

    myLocalProperties.save();

    File actual = myLocalProperties.getAndroidSdkPath();
    assertNotNull(actual);
    assertEquals(FileUtil.toCanonicalPath(androidSdkPath), FileUtil.toCanonicalPath(actual.getPath()));
  }
}
