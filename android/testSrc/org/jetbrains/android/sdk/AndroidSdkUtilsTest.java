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
package org.jetbrains.android.sdk;

import static com.android.SdkConstants.FN_ADB;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.internal.androidTarget.MockPlatformTarget;
import com.android.testutils.TestUtils;
import com.android.tools.idea.sdk.AndroidSdkPathStore;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.EnvironmentUtil;
import java.io.File;
import org.jetbrains.android.facet.AndroidFacet;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * Tests for {@link AndroidSdkUtils}.
 */
public class AndroidSdkUtilsTest extends PlatformTestCase {
  private File mySdkPath;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    mySdkPath = TestUtils.getSdk().toFile();
    ApplicationManager.getApplication().runWriteAction(AndroidSdkUtilsTest::removeAllExistingSdks);
  }

  @Override
  protected void tearDown() throws Exception {
    ApplicationManager.getApplication().runWriteAction(AndroidSdkUtilsTest::removeAllExistingSdks);
    super.tearDown();
  }

  private static void removeAllExistingSdks() {
    ProjectJdkTable table = ProjectJdkTable.getInstance();
    for (Sdk sdk : table.getAllJdks()) {
      table.removeJdk(sdk);
    }
    AndroidSdkPathStore.getInstance().setAndroidSdkPath(null);
  }

  public void DISABLEDtestCreateNewAndroidPlatformWithPathOfModernSdkOnly() {
    Sdk sdk = AndroidSdkUtils.createNewAndroidPlatform(mySdkPath.getPath(), false);
    System.out.println("Creating new android platform from: " + mySdkPath + " -> " + sdk);
    assertNotNull(sdk);
    assertTrue(FileUtil.pathsEqual(mySdkPath.getPath(), sdk.getHomePath()));
  }

  public void testGetTargetLabel() throws Exception {
    IAndroidTarget platformTarget = new MockPlatformTarget(18, 2);
    assertEquals("API 18 (\"Jelly Bean\"; Android 4.3)", AndroidSdkUtils.getTargetLabel(platformTarget));

    IAndroidTarget unknownTarget = new MockPlatformTarget(-1, 1);
    assertEquals("API -1", AndroidSdkUtils.getTargetLabel(unknownTarget));

    IAndroidTarget anotherUnknownTarget = new MockPlatformTarget(100, 1);
    assertEquals("API 100", AndroidSdkUtils.getTargetLabel(anotherUnknownTarget));

    IAndroidTarget platformPreviewTarget = new MockPlatformTarget(100, 1) {
      @NonNull
      @Override
      public AndroidVersion getVersion() {
        return new AndroidVersion(100, "Z");
      }
    };
    assertEquals("API Z Preview", AndroidSdkUtils.getTargetLabel(platformPreviewTarget));
  }

  public void testGetDebugBridgeFromSystemPropertyOverride() throws Exception {
    File fakeAdb = createTempFile("fake-adb", "");
    System.setProperty(AndroidSdkUtils.ADB_PATH_PROPERTY, fakeAdb.getPath());

    try {
      AndroidSdkUtils.getDebugBridge(myProject);
    } catch(RuntimeException ex) {
      // If the error message contains "ADB not responding." then it's using the correct ADB defined above.
      // or else it will simply return null.
      assertThat(ex.getMessage()).contains("ADB not responding.");
    }
  }

  public void testGetAdbInPath() throws Exception {
    assertWithMessage("Precondition: project with no android facets")
        .that(ProjectFacetManager.getInstance(myProject).hasFacets(AndroidFacet.ID)).isFalse();
    try (MockedStatic<EnvironmentUtil> mockEnvironment = Mockito.mockStatic(EnvironmentUtil.class)) {
      String separator = System.getProperty("path.separator");
      File fakeAdb = createTempFile(FN_ADB, "");
      when(EnvironmentUtil.getValue("PATH")).thenReturn("foo" + separator + fakeAdb.getParent() + separator + "bar");
      assertThat(AndroidSdkUtils.findAdb(myProject).adbPath).isEqualTo(fakeAdb);
    }
  }
}
