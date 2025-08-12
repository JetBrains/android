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

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.intellij.openapi.roots.ModuleRootModificationUtil.addContentRoot;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.PlatformUtils.IDEA_CE_PREFIX;
import static com.intellij.util.PlatformUtils.PLATFORM_PREFIX_KEY;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

import com.android.test.testutils.TestUtils;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.sdk.AndroidSdkPathStore;
import com.android.tools.idea.sdk.IdeSdks;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Tests for {@link SdkSyncImpl}.
 */
@RunWith(Parameterized.class)
public class SdkSyncImplTest extends HeavyPlatformTestCase {

  public static final String ANDROID_STUDIO_PLATFORM_PREFIX = "AndroidStudio";

  @Parameters(name = "{index}: androidManifestPath:{0} isAndroidStudio:{1}")
  public static Iterable<Object[]> produceParameters() {
    List<String> androidManifestPaths = asList(
      null,
      "app/src/main/AndroidManifest.xml",
      "composeApp/src/androidMain/AndroidManifest.xml"
    );
    List<Boolean> isAndroidStudio = asList(
      true,
      false
    );

    return androidManifestPaths.stream()
      .flatMap(flag -> isAndroidStudio.stream().map(version -> new Object[]{flag, version}))
      .collect(toList());
  }

  @Parameter(0)
  public @Nullable String androidManifestPath;

  @Parameter(1)
  public boolean isAndroidStudio;

  private LocalProperties myLocalProperties;
  private File myAndroidSdkPath;
  private IdeSdks myIdeSdks;
  private SdkSyncImpl mySdkSync;
  private boolean myCheckedProjectIsAndroid;

  private String originalPlatformPrefix;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myCheckedProjectIsAndroid = false;
    originalPlatformPrefix = System.getProperty(PLATFORM_PREFIX_KEY);
    System.setProperty(PLATFORM_PREFIX_KEY, isAndroidStudio ? ANDROID_STUDIO_PLATFORM_PREFIX : IDEA_CE_PREFIX);
    VirtualFile baseDir = PlatformTestUtil.getOrCreateProjectBaseDir(getProject());

    createEmptyAndroidManifestFile(baseDir);
    addContentRoot(getModule(), baseDir);

    myLocalProperties = new LocalProperties(virtualToIoFile(baseDir));
    myAndroidSdkPath = TestUtils.getSdk().toFile();
    myIdeSdks = IdeSdks.getInstance();
    ApplicationManager.getApplication().runWriteAction(() -> AndroidSdkPathStore.getInstance().setAndroidSdkPath(null));
    mySdkSync = new SdkSyncImpl();
    assertNull(myIdeSdks.getAndroidSdkPath());
    IdeSdks.removeJdksOn(getTestRootDisposable());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      System.setProperty(PLATFORM_PREFIX_KEY, originalPlatformPrefix);
      assertTrue("Each test should call projectIsAndroid at least once, " +
                 "to assert being in the context of Android project, assume such " +
                 "project required for test execution or configure the test " +
                 "behaviour based on test context project", myCheckedProjectIsAndroid);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @Test
  public void testSyncIdeAndProjectAndroidHomesWithIdeSdkAndLocalPropertiesExistsAndNoProjectSdk() throws Exception {
    ApplicationManager.getApplication().runWriteAction(() -> {
      myIdeSdks.setAndroidSdkPath(myAndroidSdkPath);
    });

    createEmptyLocalPropertiesFile();

    mySdkSync.syncIdeAndProjectAndroidSdks(myLocalProperties, getProject());

    if (projectIsAndroid()) {
      assertProjectSdkSet();
    }
    else {
      assertNull("IDEA should not implicitly create sdk.dir entry in local.properties (e.g. in pure java-gradle projects)",
                  myLocalProperties.getAndroidSdkPath());
    }
  }

  @Test
  public void testSyncIdeAndProjectAndroidHomesWithIdeSdkAndNoLocalPropertiesExistsAndNoProjectSdk() throws Exception {
    ApplicationManager.getApplication().runWriteAction(() -> {
      myIdeSdks.setAndroidSdkPath(myAndroidSdkPath);
    });

    assertNoLocalPropertiesExists();
    mySdkSync.syncIdeAndProjectAndroidSdks(myLocalProperties, getProject());

    if (projectIsAndroid()) {
      assertProjectSdkSet();
    }
    else {
      assertFalse("IDEA should not implicitly create local.properties (e.g. in pure java-gradle projects)",
                  myLocalProperties.getPropertiesFilePath().exists());
    }
  }

  @Test
  public void testSyncIdeAndProjectAndroidHomesWithIdeSdkAndInvalidProjectSdk() throws Exception {
    ApplicationManager.getApplication().runWriteAction(() -> {
      myIdeSdks.setAndroidSdkPath(myAndroidSdkPath);
    });

    myLocalProperties.setAndroidSdkPath(new File("randomPath"));
    myLocalProperties.save();
    assertTrue(projectIsAndroid());

    mySdkSync.syncIdeAndProjectAndroidSdks(myLocalProperties, getProject());

    assertProjectSdkSet();
  }

  @Test
  public void testSyncIdeAndProjectAndroidHomesWithNoIdeSdkAndValidProjectSdk() throws Exception {
    myLocalProperties.setAndroidSdkPath(myAndroidSdkPath);
    myLocalProperties.save();
    assertTrue(projectIsAndroid());

    mySdkSync.syncIdeAndProjectAndroidSdks(myLocalProperties, getProject());

    assertDefaultSdkSet();
  }

  @Test
  public void testSyncIdeAndProjectAndroidHomesWhenNoLocalPropertiesExistsAndUserSelectsValidSdkPath() throws Exception {
    Ref<Boolean> selectSdkDialogShown = new Ref<>(false);
    SdkSyncImpl.FindValidSdkPathTask task = new SdkSyncImpl.FindValidSdkPathTask() {
      @Nullable
      @Override
      File selectValidSdkPath() {
        selectSdkDialogShown.set(true);
        return myAndroidSdkPath;
      }
    };

    assertNoLocalPropertiesExists();
    mySdkSync.syncIdeAndProjectAndroidSdk(myLocalProperties, task, getProject());

    assertEquals("IDEA should not ask users to configure Android SDK in pure java-gradle projects. " +
                 "Android Studio asks users to do so.",
                 projectIsAndroid(), selectSdkDialogShown.get().booleanValue());

    if (projectIsAndroid()) {
      assertProjectSdkSet();
      assertDefaultSdkSet();
    }
    else {
      assertNoLocalPropertiesExists();
    }
  }

  @Test
  public void testSyncIdeAndProjectAndroidHomesWhenLocalPropertiesExistsAndUserSelectsValidSdkPath() throws Exception {
    Ref<Boolean> selectSdkDialogShown = new Ref<>(false);
    SdkSyncImpl.FindValidSdkPathTask task = new SdkSyncImpl.FindValidSdkPathTask() {
      @Nullable
      @Override
      File selectValidSdkPath() {
        selectSdkDialogShown.set(true);
        return myAndroidSdkPath;
      }
    };

    createEmptyLocalPropertiesFile();
    mySdkSync.syncIdeAndProjectAndroidSdk(myLocalProperties, task, getProject());

    assertEquals("IDEA should not ask users to configure Android SDK in KMPP projects with no android modules (https://youtrack.jetbrains.com/issue/IDEA-265504). " +
                 "Android Studio asks users to do so.",
      projectIsAndroid(), selectSdkDialogShown.get().booleanValue());

    if (projectIsAndroid()) {
      assertProjectSdkSet();
      assertDefaultSdkSet();
    }
    else {
      assertNull("IDEA should not implicitly create sdk.dir entry in local.properties (e.g. in pure java-gradle projects)",
                 myLocalProperties.getAndroidSdkPath());
    }
  }

  @Test
  public void testSyncIdeAndProjectAndroidHomesWhenUserDoesNotSelectValidSdkPath() throws Exception {
    SdkSyncImpl.FindValidSdkPathTask task = new SdkSyncImpl.FindValidSdkPathTask() {
      @Nullable
      @Override
      File selectValidSdkPath() {
        return null;
      }
    };
    try {
      mySdkSync.syncIdeAndProjectAndroidSdk(myLocalProperties, task, getProject());
      assertFalse("Expecting ExternalSystemException in Android Studio", projectIsAndroid());
    }
    catch (ExternalSystemException e) {
      assertTrue("Not expecting ExternalSystemException in IDEA", projectIsAndroid());
    }

    assertNull(myIdeSdks.getAndroidSdkPath());
    myLocalProperties = new LocalProperties(getBaseDirPath(getProject()));
    assertNull(myLocalProperties.getAndroidSdkPath());
  }

  private void assertDefaultSdkSet() {
    File actual = myIdeSdks.getAndroidSdkPath();
    assertNotNull(actual);
    assertEquals(myAndroidSdkPath.getPath(), actual.getPath());
  }

  private void assertProjectSdkSet() throws Exception {
    myLocalProperties = new LocalProperties(getBaseDirPath(getProject()));
    File actual = myLocalProperties.getAndroidSdkPath();
    assertNotNull(actual);
    assertEquals(myAndroidSdkPath.getPath(), actual.getPath());
  }

  private void createEmptyLocalPropertiesFile() throws IOException {
    File parent = myLocalProperties.getPropertiesFilePath().getParentFile();
    assertTrue("Could not create directory: " + parent, parent.exists() || parent.mkdirs());
    assertTrue("Precondition failed: local.properties should exist", myLocalProperties.getPropertiesFilePath().createNewFile());
  }

  private void createEmptyAndroidManifestFile(VirtualFile baseDir) throws IOException {
    if (androidManifestPath == null) return;

    File androidManifest = new File(virtualToIoFile(baseDir), androidManifestPath);
    File parent = androidManifest.getParentFile();
    assertTrue("Could not create directory: " + parent, parent.exists() || parent.mkdirs());
    assertTrue("Precondition failed: AndroidManifest.xml in  should exist", androidManifest.createNewFile());
  }

  private void assertNoLocalPropertiesExists() {
    assertFalse("Precondition failed: file local.properties should not exist", myLocalProperties.getPropertiesFilePath().exists());
  }

  private boolean projectIsAndroid() {
    boolean projectIsAndroid = SdkSyncImpl.projectIsAndroid(myLocalProperties, getProject());
    myCheckedProjectIsAndroid = true;
    return projectIsAndroid;
  }
}
