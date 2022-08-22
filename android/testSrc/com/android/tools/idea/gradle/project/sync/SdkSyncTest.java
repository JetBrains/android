/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync;

import com.android.testutils.TestUtils;
import com.android.tools.idea.AndroidTestCaseHelper;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.sdk.AndroidSdkPathStore;
import com.android.tools.idea.sdk.IdeSdks;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.util.Ref;
import com.intellij.testFramework.PlatformTestCase;
import java.io.File;
import java.io.IOException;
import org.jetbrains.annotations.Nullable;

/**
 * Tests for {@link SdkSync}.
 */
public class SdkSyncTest extends PlatformTestCase {
  private LocalProperties myLocalProperties;
  private File myAndroidSdkPath;
  private IdeSdks myIdeSdks;

  private SdkSync mySdkSync;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    AndroidTestCaseHelper.removeExistingAndroidSdks();
    myLocalProperties = new LocalProperties(myProject);
    myAndroidSdkPath = TestUtils.getSdk().toFile();
    myIdeSdks = IdeSdks.getInstance();
    ApplicationManager.getApplication().runWriteAction(() -> AndroidSdkPathStore.getInstance().setAndroidSdkPath(null));
    mySdkSync = new SdkSync();
    assertNull(myIdeSdks.getAndroidSdkPath());
    IdeSdks.removeJdksOn(getTestRootDisposable());
  }

  public void testSyncIdeAndProjectAndroidHomesWithIdeSdkAndLocalPropertiesExistsAndNoProjectSdk() throws Exception {
    ApplicationManager.getApplication().runWriteAction(() -> {
      myIdeSdks.setAndroidSdkPath(myAndroidSdkPath, null);
    });

    createEmptyLocalPropertiesFile();
    mySdkSync.syncIdeAndProjectAndroidSdks(myLocalProperties);

    assertProjectSdkSet();
  }

  public void testSyncIdeAndProjectAndroidHomesWithIdeSdkAndNoLocalPropertiesExistsAndNoProjectSdk() throws Exception {
    ApplicationManager.getApplication().runWriteAction(() -> {
      myIdeSdks.setAndroidSdkPath(myAndroidSdkPath, null);
    });

    assertNoLocalPropertiesExists();
    mySdkSync.syncIdeAndProjectAndroidSdks(myLocalProperties);

    if (IdeInfo.getInstance().isAndroidStudio()) {
      assertProjectSdkSet();
    }
    else {
      assertFalse("IDEA should not implicitly create local.properties (e.g. in pure java-gradle projects",
                  myLocalProperties.getPropertiesFilePath().exists());
    }
  }

  public void testSyncIdeAndProjectAndroidHomesWithIdeSdkAndInvalidProjectSdk() throws Exception {
    ApplicationManager.getApplication().runWriteAction(() -> {
      myIdeSdks.setAndroidSdkPath(myAndroidSdkPath, null);
    });

    myLocalProperties.setAndroidSdkPath(new File("randomPath"));
    myLocalProperties.save();

    mySdkSync.syncIdeAndProjectAndroidSdks(myLocalProperties);

    assertProjectSdkSet();
  }

  public void testSyncIdeAndProjectAndroidHomesWithNoIdeSdkAndValidProjectSdk() throws Exception {
    myLocalProperties.setAndroidSdkPath(myAndroidSdkPath);
    myLocalProperties.save();

    mySdkSync.syncIdeAndProjectAndroidSdks(myLocalProperties);

    assertDefaultSdkSet();
  }

  public void testSyncIdeAndProjectAndroidHomesWhenNoLocalPropertiesExistsAndUserSelectsValidSdkPath() throws Exception {
    Ref<Boolean> selectSdkDialogShown = new Ref<>(false);
    SdkSync.FindValidSdkPathTask task = new SdkSync.FindValidSdkPathTask() {
      @Nullable
      @Override
      File selectValidSdkPath() {
        selectSdkDialogShown.set(true);
        return myAndroidSdkPath;
      }
    };

    assertNoLocalPropertiesExists();
    mySdkSync.syncIdeAndProjectAndroidSdk(myLocalProperties, task, myProject);

    assertEquals("IDEA should not ask users to configure Android SDK in pure java-gradle projects. " +
                 "Android Studio asks users to do so.",
                 IdeInfo.getInstance().isAndroidStudio(), selectSdkDialogShown.get().booleanValue());

    if (IdeInfo.getInstance().isAndroidStudio()) {
      assertProjectSdkSet();
      assertDefaultSdkSet();
    }
  }

  public void testSyncIdeAndProjectAndroidHomesWhenLocalPropertiesExistsAndUserSelectsValidSdkPath() throws Exception {
    SdkSync.FindValidSdkPathTask task = new SdkSync.FindValidSdkPathTask() {
      @Nullable
      @Override
      File selectValidSdkPath() {
        return myAndroidSdkPath;
      }
    };

    createEmptyLocalPropertiesFile();
    mySdkSync.syncIdeAndProjectAndroidSdk(myLocalProperties, task, myProject);

    assertProjectSdkSet();
    assertDefaultSdkSet();
  }

  public void testSyncIdeAndProjectAndroidHomesWhenUserDoesNotSelectValidSdkPath() throws Exception {
    SdkSync.FindValidSdkPathTask task = new SdkSync.FindValidSdkPathTask() {
      @Nullable
      @Override
      File selectValidSdkPath() {
        return null;
      }
    };
    try {
      mySdkSync.syncIdeAndProjectAndroidSdk(myLocalProperties, task, myProject);
      assertFalse("Expecting ExternalSystemException in Android Studio", IdeInfo.getInstance().isAndroidStudio());
    }
    catch (ExternalSystemException e) {
      assertTrue("Not expecting ExternalSystemException in IDEA", IdeInfo.getInstance().isAndroidStudio());
    }

    assertNull(myIdeSdks.getAndroidSdkPath());
    myLocalProperties = new LocalProperties(myProject);
    assertNull(myLocalProperties.getAndroidSdkPath());
  }

  private void assertDefaultSdkSet() {
    File actual = myIdeSdks.getAndroidSdkPath();
    assertNotNull(actual);
    assertEquals(myAndroidSdkPath.getPath(), actual.getPath());
  }

  private void assertProjectSdkSet() throws Exception {
    myLocalProperties = new LocalProperties(myProject);
    File actual = myLocalProperties.getAndroidSdkPath();
    assertNotNull(actual);
    assertEquals(myAndroidSdkPath.getPath(), actual.getPath());
  }

  private void createEmptyLocalPropertiesFile() throws IOException {
    assertTrue("Could not create directory: " + myLocalProperties.getPropertiesFilePath().getParentFile(),
               myLocalProperties.getPropertiesFilePath().getParentFile().mkdirs());
    assertTrue("Precondition failed: local.properties should exist", myLocalProperties.getPropertiesFilePath().createNewFile());
  }

  private void assertNoLocalPropertiesExists() {
    assertFalse("Precondition failed: file local.properties should not exist", myLocalProperties.getPropertiesFilePath().exists());
  }
}
