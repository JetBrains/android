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

import com.android.tools.idea.sdk.IdeSdks;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import org.jetbrains.android.AndroidTestCase;

import java.io.File;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test cases for procuring Sdk Data.
 */
public class AndroidSdkDataTest extends AndroidTestCase {

  private AndroidSdkData sdkData;
  private AndroidSdkData defaultSdkData;
  private File sdkDir;
  private File defaultSdkDir;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    sdkDir = new File(getTestSdkPath());
    assertTrue(sdkDir.exists());
    sdkData = AndroidSdkData.getSdkData(sdkDir);
    defaultSdkDir = new File(getDefaultTestSdkPath());
    assertTrue(defaultSdkDir.exists());
    defaultSdkData = AndroidSdkData.getSdkData(defaultSdkDir);

    assertNotNull(sdkData);
    assertNotNull(defaultSdkData);
    assertFalse(defaultSdkData.equals(sdkData));

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        IdeSdks.setAndroidSdkPath(sdkDir, null);
        ProjectRootManager.getInstance(getProject()).setProjectSdk(IdeSdks.getEligibleAndroidSdks().get(0));
      }
    });
  }

  @Override
  protected boolean requireRecentSdk() {
    return true;
  }

  public void testGetSdkDataBadFile() throws Exception {
    assertNull(AndroidSdkData.getSdkData("/blah"));
    assertNull(AndroidSdkData.getSdkData(getTestDataPath()));
  }

  public void testGetSdkDataFile() throws Exception {
    assertEquals(sdkData, AndroidSdkData.getSdkData(sdkDir));
    assertEquals(defaultSdkData, AndroidSdkData.getSdkData(defaultSdkDir));

    assertThat(AndroidSdkData.getSdkData(sdkDir)).isNotEqualTo(AndroidSdkData.getSdkData(defaultSdkDir));
  }

  public void testGetSdkDataPath() throws Exception {
    String testSdkPath = getTestSdkPath();
    assertEquals(sdkData, AndroidSdkData.getSdkData(testSdkPath));
    assertEquals(defaultSdkData, AndroidSdkData.getSdkData(getDefaultTestSdkPath()));

    String otherEnding;
    if (testSdkPath.endsWith(File.separator)) {
      otherEnding = testSdkPath.substring(0, testSdkPath.length() - 1);
    } else {
      otherEnding = testSdkPath + File.separator;
    }

    assertFalse(otherEnding.equals(testSdkPath));
    assertEquals(new File(testSdkPath), new File(otherEnding));
    assertEquals(AndroidSdkData.getSdkData(testSdkPath), AndroidSdkData.getSdkData(otherEnding));

    assertThat(AndroidSdkData.getSdkData(getTestSdkPath())).isNotEqualTo(AndroidSdkData.getSdkData(getDefaultTestSdkPath()));
  }

  public void testGetSdkDataProject() throws Exception {
    AndroidSdkData sdkFromProject = AndroidSdkData.getSdkData(getProject());
    assertEquals(sdkData, sdkFromProject);
  }

  public void testGetSdkDataModule() throws Exception {
    AndroidSdkData actual = AndroidSdkData.getSdkData(myModule);
    assertEquals(sdkData, actual);
  }

  public void testGetSdkDataSdk() throws Exception {
    Sdk sdk = mock(Sdk.class);
    when(sdk.getHomePath()).thenReturn(getTestSdkPath());
    Sdk defaultSdk = mock(Sdk.class);
    when(defaultSdk.getHomePath()).thenReturn(getDefaultTestSdkPath());

    assertEquals(sdkData, AndroidSdkData.getSdkData(sdk));
    assertEquals(defaultSdkData, AndroidSdkData.getSdkData(defaultSdk));

    assertThat(AndroidSdkData.getSdkData(sdk)).isNotEqualTo(AndroidSdkData.getSdkData(defaultSdk));
  }
}
