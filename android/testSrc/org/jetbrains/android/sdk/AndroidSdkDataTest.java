/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.jetbrains.android.sdk;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.testutils.TestUtils;
import com.android.tools.idea.sdk.IdeSdks;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import java.io.File;
import org.jetbrains.android.AndroidTestCase;

/**
 * Test cases for procuring Sdk Data.
 */
public class AndroidSdkDataTest extends AndroidTestCase {

  private AndroidSdkData sdkData;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    File sdkDir = TestUtils.getSdk().toFile();
    sdkData = AndroidSdkData.getSdkData(sdkDir);

    ApplicationManager.getApplication().runWriteAction(() -> {
      IdeSdks ideSdks = IdeSdks.getInstance();
      ideSdks.setAndroidSdkPath(sdkDir);
      IdeSdks.removeJdksOn(myFixture.getProjectDisposable());
      ProjectRootManager.getInstance(getProject()).setProjectSdk(ideSdks.getEligibleAndroidSdks().get(0));
    });
  }

  public void testSdkDataExposesSdkComponents() throws Exception {
    assertNotNull(sdkData.getLatestBuildTool(false));
    assertThat(sdkData.getTargets().length).isAtLeast(1);
    assertEquals(sdkData.getLocation(), TestUtils.getSdk());
  }

  public void testGetSdkDataReturnsNullForInvalidSdkLocations() throws Exception {
    assertNull(AndroidSdkData.getSdkData("/blah"));
    assertNull(AndroidSdkData.getSdkData(getTestDataPath()));
  }

  public void testGetSdkDataByPath() throws Exception {
    String sdkPath = TestUtils.getSdk().toString();

    // This API should work with both a trailing slash and without
    String sdkPathWithTrailingSlash = sdkPath;
    String sdkPathWithoutTrailingSlash = sdkPath;
    if (sdkPath.endsWith(File.separator)) {
      sdkPathWithoutTrailingSlash = sdkPath.substring(0, sdkPath.length() - 1);
    } else {
      sdkPathWithTrailingSlash = sdkPath + File.separator;
    }

    assertFalse(sdkPathWithTrailingSlash.equals(sdkPathWithoutTrailingSlash));
    assertEquals(AndroidSdkData.getSdkData(sdkPathWithTrailingSlash), AndroidSdkData.getSdkData(sdkPathWithoutTrailingSlash));
    assertNotNull(AndroidSdkData.getSdkData(sdkPathWithTrailingSlash));
  }

  public void testGetSdkDataByProject() throws Exception {
    AndroidSdkData sdkFromProject = StudioAndroidSdkData.getSdkData(getProject());
    assertEquals(sdkData, sdkFromProject);
  }

  public void testGetSdkDataByModule() throws Exception {
    AndroidSdkData actual = StudioAndroidSdkData.getSdkData(myModule);
    assertEquals(sdkData, actual);
  }

  public void testGetSdkDataBySdkClass() throws Exception {
    Sdk sdk = mock(Sdk.class);
    when(sdk.getHomePath()).thenReturn(TestUtils.getSdk().toString());

    assertEquals(sdkData, AndroidSdkData.getSdkData(sdk));
  }
}
