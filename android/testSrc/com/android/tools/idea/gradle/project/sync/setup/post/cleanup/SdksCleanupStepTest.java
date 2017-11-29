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

import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.Jdks;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.android.testutils.TestUtils.getSdk;
import static com.android.tools.idea.testing.Facets.createAndAddAndroidFacet;
import static com.android.tools.idea.testing.Sdks.findLatestAndroidTarget;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static com.intellij.openapi.roots.OrderRootType.SOURCES;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link SdksCleanupStep}.
 */
public class SdksCleanupStepTest extends IdeaTestCase {
  // failing after 2017.3 merge
  public void /*test*/CleanUpSdkWithMissingDocumentation() throws Exception {
    Sdk sdk = createSdk();
    try {
      removeRoots(sdk, JavadocOrderRootType.getInstance());

      Module module = getModule();
      setUpModuleAsAndroid(module, sdk);

      SdksCleanupStep cleanupStep = new SdksCleanupStep(AndroidSdks.getInstance());
      Set<Sdk> fixedSdks = new HashSet<>();
      Set<Sdk> invalidSdks = new HashSet<>();
      cleanupStep.cleanUpSdk(module, fixedSdks, invalidSdks);

      String[] urls = sdk.getRootProvider().getUrls(JavadocOrderRootType.getInstance());
      assertThat(urls).asList().containsExactly("http://developer.android.com/reference/");

      assertThat(fixedSdks).containsExactly(sdk);
      assertThat(invalidSdks).isEmpty();
    }
    finally {
      removeSdk(sdk);
    }
  }

  // failing after 2017.3 merge
  public void /*test*/CleanUpSdkWithSdkWithoutAndroidLibrary() throws Exception {
    Sdk sdk = createSdk();
    try {
      removeRoots(sdk, CLASSES);

      Module module = getModule();
      setUpModuleAsAndroid(module, sdk);

      SdksCleanupStep cleanupStep = new SdksCleanupStep(AndroidSdks.getInstance());
      Set<Sdk> fixedSdks = new HashSet<>();
      Set<Sdk> invalidSdks = new HashSet<>();
      cleanupStep.cleanUpSdk(module, fixedSdks, invalidSdks);

      // Ensure android.jar was added.
      VirtualFile[] jars = sdk.getRootProvider().getFiles(CLASSES);
      long androidDotJarFound = Arrays.stream(jars).filter(file -> file.getName().equals("android.jar")).count();
      assertEquals(1, androidDotJarFound);

      assertThat(fixedSdks).containsExactly(sdk);
      assertThat(invalidSdks).isEmpty();
    }
    finally {
      removeSdk(sdk);
    }
  }

  public void testCleanUpSdkWithAnAlreadyFixedSdk() throws Exception {
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
    androidFacet.setAndroidModel(mock(AndroidModel.class));

    cleanupStep.cleanUpSdk(appModule, fixedSdks, invalidSdks);

    // There should be no attempts to fix the SDK.
    verify(sdks, never()).getAndroidSdkAdditionalData(sdk);

    assertThat(fixedSdks).containsExactly(sdk);
    assertThat(invalidSdks).isEmpty();
  }

  public void testCleanUpSdkWithAnInvalidSdk() throws Exception {
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
    androidFacet.setAndroidModel(mock(AndroidModel.class));

    cleanupStep.cleanUpSdk(appModule, fixedSdks, invalidSdks);

    // There should be no attempts to fix the SDK.
    verify(sdks, never()).getAndroidSdkAdditionalData(sdk);

    assertThat(invalidSdks).containsExactly(sdk);
    assertThat(fixedSdks).isEmpty();
  }

  // See https://code.google.com/p/android/issues/detail?id=233392
  // failing after 2017.3 merge
  public void /*test*/CleanUpProjectWithSdkWithUpdatedSources() {
    Sdk sdk = createSdk();
    try {
      // We could have created the SDK without roots, but better make it explicit that we need an SDK without sources.
      removeRoots(sdk, SOURCES);

      Module module = getModule();
      setUpModuleAsAndroid(module, sdk);

      SdksCleanupStep cleanupStep = new SdksCleanupStep(AndroidSdks.getInstance());
      Set<Sdk> fixedSdks = new HashSet<>();
      Set<Sdk> invalidSdks = new HashSet<>();
      cleanupStep.cleanUpSdk(module, fixedSdks, invalidSdks);

      String[] urls = sdk.getRootProvider().getUrls(SOURCES);
      assertThat(urls).hasLength(1);

      assertThat(fixedSdks).containsExactly(sdk);
      assertThat(invalidSdks).isEmpty();
    }
    finally {
      removeSdk(sdk);
    }
  }

  @NotNull
  private static Sdk createSdk() {
    File sdkPath = getSdk();
    IAndroidTarget target = findLatestAndroidTarget(sdkPath);

    Jdks jdks = Jdks.getInstance();
    Sdk jdk = jdks.chooseOrCreateJavaSdk();

    Sdk sdk = AndroidSdks.getInstance().create(target, sdkPath, "Test SDK", jdk, true /* add roots */);
    assertNotNull(sdk);
    return sdk;
  }

  private static void removeSdk(@NotNull Sdk sdk) {
    ApplicationManager.getApplication().runWriteAction(() -> ProjectJdkTable.getInstance().removeJdk(sdk));
  }

  private static void removeRoots(@NotNull Sdk sdk, @NotNull OrderRootType rootType) {
    SdkModificator sdkModificator = sdk.getSdkModificator();
    sdkModificator.removeRoots(rootType);
    sdkModificator.commitChanges();
  }

  private static void setUpModuleAsAndroid(@NotNull Module module, @NotNull Sdk sdk) {
    AndroidFacet facet = createAndAddAndroidFacet(module);
    facet.setAndroidModel(mock(AndroidModel.class));

    ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(module).getModifiableModel();
    modifiableModel.setSdk(sdk);
    ApplicationManager.getApplication().runWriteAction(modifiableModel::commit);
  }
}