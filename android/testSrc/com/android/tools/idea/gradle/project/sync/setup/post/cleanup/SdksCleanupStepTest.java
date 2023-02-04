/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.post.cleanup;

import static com.android.SdkConstants.FD_PKG_SOURCES;
import static com.android.testutils.TestUtils.getSdk;
import static com.android.tools.idea.gradle.project.sync.setup.post.cleanup.SdksCleanupUtil.updateSdkIfNeeded;
import static com.android.tools.idea.testing.Facets.createAndAddAndroidFacet;
import static com.android.tools.idea.testing.Sdks.findLatestAndroidTarget;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static com.intellij.openapi.roots.OrderRootType.SOURCES;
import static com.intellij.openapi.util.io.FileUtil.createDirectory;
import static com.intellij.openapi.util.io.FileUtil.join;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.testing.Sdks;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tests for {@link SdksCleanupStep}.
 */
public class SdksCleanupStepTest extends PlatformTestCase {
  @Nullable private Sdk mySdk;

  public void testUpdateSdkWithMissingDocumentation() {
    createSdk();
    try {
      IAndroidTarget target = findLatestAndroidTarget(getSdk().toFile());
      Sdk spy = spy(mySdk);
      File mockJdkHome = new File(getProject().getBasePath(), "jdkHome");
      when(spy.getHomePath()).thenReturn(mockJdkHome.getPath());
      updateSdkIfNeeded(spy, AndroidSdks.getInstance(), target);
      String[] urls = spy.getRootProvider().getUrls(JavadocOrderRootType.getInstance());
      assertThat(urls).asList().containsExactly("http://developer.android.com/reference/");
    }
    finally {
      removeSdk();
    }
  }

  public void testUpdateSdkWithSourcesInstalled() {
    createSdk();
    try {
      IAndroidTarget target = findLatestAndroidTarget(getSdk().toFile());
      Sdk spy = spy(mySdk);
      File mockJdkHome = new File(getProject().getBasePath(), "jdkHome");
      when(spy.getHomePath()).thenReturn(mockJdkHome.getPath());
      updateSdkIfNeeded(spy, AndroidSdks.getInstance(), target);

      String[] urls = spy.getRootProvider().getUrls(JavadocOrderRootType.getInstance());
      assertThat(urls).asList().containsExactly("http://developer.android.com/reference/");

      // Simulate the case that sources are installed after the initial sync.
      createDirectory(new File(mockJdkHome, join(FD_PKG_SOURCES, target.getPath(IAndroidTarget.SOURCES).getFileName().toString())));
      updateSdkIfNeeded(spy, AndroidSdks.getInstance(), target);

      // Verify that Javadoc is set to empty since sources are now available.
      assertThat(spy.getRootProvider().getUrls(JavadocOrderRootType.getInstance())).asList().isEmpty();
      assertThat(spy.getRootProvider().getUrls(SOURCES)).hasLength(1);
    }
    finally {
      removeSdk();
    }
  }

  public void testCleanUpSdkWithSdkWithoutAndroidLibrary() {
    createSdk();
    try {
      Module module = getModule();
      setUpModuleAsAndroid(module, mySdk);

      SdksCleanupStep cleanupStep = new SdksCleanupStep();
      Set<Sdk> fixedSdks = new HashSet<>();
      Set<Sdk> invalidSdks = new HashSet<>();
      cleanupStep.cleanUpSdk(module, fixedSdks, invalidSdks);

      // Ensure android.jar was added.
      VirtualFile[] jars = mySdk.getRootProvider().getFiles(CLASSES);
      long androidDotJarFound = Arrays.stream(jars).filter(file -> file.getName().equals("android.jar")).count();
      assertEquals(1, androidDotJarFound);

      assertThat(fixedSdks).containsExactly(mySdk);
      assertThat(invalidSdks).isEmpty();
    }
    finally {
      removeSdk();
    }
  }

  public void testCleanUpSdkWithAnAlreadyFixedSdk() {
    AndroidSdks sdks = mock(AndroidSdks.class);
    SdksCleanupStep cleanupStep = new SdksCleanupStep(sdks);

    Sdk sdk = mock(Sdk.class);
    // Simulate this SDK was already fixed.
    Set<Sdk> fixedSdks = new HashSet<>();
    fixedSdks.add(sdk);

    Set<Sdk> invalidSdks = new HashSet<>();

    Module appModule = createModule("app");
    // Simulate this is an Android module.
    AndroidFacet androidFacet = createAndAddAndroidFacet(appModule);

    cleanupStep.cleanUpSdk(appModule, fixedSdks, invalidSdks);

    // There should be no attempts to fix the SDK.
    verify(sdks, never()).getAndroidSdkAdditionalData(sdk);

    assertThat(fixedSdks).containsExactly(sdk);
    assertThat(invalidSdks).isEmpty();
  }

  public void testCleanUpSdkWithAnInvalidSdk() {
    AndroidSdks sdks = mock(AndroidSdks.class);
    SdksCleanupStep cleanupStep = new SdksCleanupStep(sdks);

    Set<Sdk> fixedSdks = new HashSet<>();

    Sdk sdk = mock(Sdk.class);
    // Simulate this SDK is invalid.
    Set<Sdk> invalidSdks = new HashSet<>();
    invalidSdks.add(sdk);

    Module appModule = createModule("app");
    // Simulate this is an Android module.
    AndroidFacet androidFacet = createAndAddAndroidFacet(appModule);

    cleanupStep.cleanUpSdk(appModule, fixedSdks, invalidSdks);

    // There should be no attempts to fix the SDK.
    verify(sdks, never()).getAndroidSdkAdditionalData(sdk);

    assertThat(invalidSdks).containsExactly(sdk);
    assertThat(fixedSdks).isEmpty();
  }

  // See https://code.google.com/p/android/issues/detail?id=233392
  public void testCleanUpProjectWithSdkWithUpdatedSources() {
    createSdk();
    try {
      Module module = getModule();
      setUpModuleAsAndroid(module, mySdk);

      SdksCleanupStep cleanupStep = new SdksCleanupStep(AndroidSdks.getInstance());
      Set<Sdk> fixedSdks = new HashSet<>();
      Set<Sdk> invalidSdks = new HashSet<>();
      cleanupStep.cleanUpSdk(module, fixedSdks, invalidSdks);

      String[] urls = mySdk.getRootProvider().getUrls(SOURCES);
      assertThat(urls).hasLength(1);

      assertThat(fixedSdks).containsExactly(mySdk);
      assertThat(invalidSdks).isEmpty();
    }
    finally {
      removeSdk();
    }
  }

  private void createSdk() {
    File sdkPath = getSdk().toFile();
    Sdks.allowAccessToSdk(getTestRootDisposable());
    IAndroidTarget target = findLatestAndroidTarget(sdkPath);

    mySdk = AndroidSdks.getInstance().create(target, sdkPath, "Test SDK", true /* add roots */);
    assertNotNull(mySdk);
    IdeSdks.removeJdksOn(getTestRootDisposable());
  }

  private void removeSdk() {
    ApplicationManager.getApplication().runWriteAction(() -> ProjectJdkTable.getInstance().removeJdk(mySdk));
  }

  private static void setUpModuleAsAndroid(@NotNull Module module, @NotNull Sdk sdk) {
    AndroidFacet facet = createAndAddAndroidFacet(module);

    ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(module).getModifiableModel();
    modifiableModel.setSdk(sdk);
    ApplicationManager.getApplication().runWriteAction(modifiableModel::commit);
  }
}
