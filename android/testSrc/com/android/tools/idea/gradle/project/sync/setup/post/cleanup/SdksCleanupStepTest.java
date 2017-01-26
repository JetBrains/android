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

import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.android.tools.idea.testing.Facets.createAndAddAndroidFacet;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link SdksCleanupStep}.
 */
public class SdksCleanupStepTest extends AndroidGradleTestCase {
  public void testCleanUpSdkWithMissingDocumentation() throws Exception {
    SdksCleanupStep cleanupStep = new SdksCleanupStep(AndroidSdks.getInstance());
    loadSimpleApplication();

    // Remove documentation from SDK to ensure it is added later.
    Module appModule = myModules.getAppModule();
    Sdk sdk = ModuleRootManager.getInstance(appModule).getSdk();
    assertNotNull(sdk);
    removeRoots(sdk, JavadocOrderRootType.getInstance());

    Set<Sdk> fixedSdks = new HashSet<>();
    Set<Sdk> invalidSdks = new HashSet<>();

    cleanupStep.cleanUpSdk(appModule, fixedSdks, invalidSdks);

    String[] urls = sdk.getRootProvider().getUrls(JavadocOrderRootType.getInstance());
    assertThat(urls).asList().containsExactly("http://developer.android.com/reference/");

    assertThat(fixedSdks).containsExactly(sdk);
    assertThat(invalidSdks).isEmpty();
  }

  public void testCleanUpSdkWithSdkWithoutAndroidLibrary() throws Exception {
    SdksCleanupStep cleanupStep = new SdksCleanupStep(AndroidSdks.getInstance());
    loadSimpleApplication();

    // Remove class jars from SDK to ensure they are added later.
    Module appModule = myModules.getAppModule();
    Sdk sdk = ModuleRootManager.getInstance(appModule).getSdk();
    assertNotNull(sdk);
    removeRoots(sdk, CLASSES);

    Set<Sdk> fixedSdks = new HashSet<>();
    Set<Sdk> invalidSdks = new HashSet<>();

    cleanupStep.cleanUpSdk(appModule, fixedSdks, invalidSdks);

    // Ensure android.jar was added.
    VirtualFile[] jars = sdk.getRootProvider().getFiles(CLASSES);
    long androidDotJarFound = Arrays.stream(jars).filter(file -> file.getName().equals("android.jar")).count();
    assertEquals(1, androidDotJarFound);

    assertThat(fixedSdks).containsExactly(sdk);
    assertThat(invalidSdks).isEmpty();
  }

  private static void removeRoots(@NotNull Sdk sdk, @NotNull OrderRootType rootType) {
    SdkModificator sdkModificator = sdk.getSdkModificator();
    sdkModificator.removeRoots(rootType);
    sdkModificator.commitChanges();
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
}