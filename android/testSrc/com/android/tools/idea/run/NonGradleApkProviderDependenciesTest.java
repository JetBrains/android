/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.android.ddmlib.IDevice;
import com.google.common.collect.Iterables;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.android.facet.AndroidFacetProperties;
import org.mockito.Mockito;

import java.util.*;

import static com.android.AndroidProjectTypes.PROJECT_TYPE_APP;
import static com.android.AndroidProjectTypes.PROJECT_TYPE_LIBRARY;

/**
 * Additional tests for {@link NonGradleApkProvider} that require a project setup with
 * module dependencies. These are separated because the test framework is oriented toward setting up all the test project
 * modules in setUp, and most test methods did not want dependency APKs.
 */
public class NonGradleApkProviderDependenciesTest extends AndroidTestCase {
  @Override
  protected void configureAdditionalModules(@NotNull TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder,
                                            @NotNull List<MyAdditionalModuleData> modules) {
    addModuleWithAndroidFacet(projectBuilder, modules, "dependencyApp1", PROJECT_TYPE_APP);
    addModuleWithAndroidFacet(projectBuilder, modules, "dependencyApp2", PROJECT_TYPE_APP);
    addModuleWithAndroidFacet(projectBuilder, modules, "dependencyLibrary", PROJECT_TYPE_LIBRARY);
  }

  /** For testing convenience, quickly specify a facet's package and APK. */
  private static void setIdAndApk(AndroidFacet facet, String appId, String apk) {
    AndroidFacetProperties properties = facet.getProperties();
    properties.APK_PATH = "/" + apk;
    properties.USE_CUSTOM_MANIFEST_PACKAGE = true;
    properties.CUSTOM_MANIFEST_PACKAGE = appId;
  }

  public void testGetApksWithDependencies() throws Exception {
    IDevice device = Mockito.mock(IDevice.class);

    setIdAndApk(myFacet, "com.test.app", "app.apk");
    for (Module module : myAdditionalModules) {
      for (VirtualFile contentRoot : ModuleRootManager.getInstance(module).getContentRoots()) {
        if (contentRoot.getPath().endsWith("dependencyApp1")) {
          setIdAndApk(AndroidFacet.getInstance(module), "com.test.dep1", "dep1.apk");
          break;
        } else if (contentRoot.getPath().endsWith("dependencyApp2")) {
          setIdAndApk(AndroidFacet.getInstance(module), "com.test.dep2", "dep2.apk");
          break;
        } else if (contentRoot.getPath().endsWith("dependencyLibrary")) {
          assertTrue(module.getName() + " at " + contentRoot.getPath() + " should be an Android library.",
                     AndroidFacet.getInstance(module).getConfiguration().isLibraryProject());
          break;
        }
      }
    }

    NonGradleApkProvider provider = new NonGradleApkProvider(myFacet, new NonGradleApplicationIdProvider(myFacet), null);

    Collection<ApkInfo> apks = provider.getApks(device);
    assertNotNull(apks);
    assertEquals(3, apks.size());
    // Sort the apks to keep test consistent.
    List<ApkInfo> apkList = new ArrayList<>(apks);
    Collections.sort(apkList, new Comparator<ApkInfo>() {
      @Override
      public int compare(ApkInfo a, ApkInfo b) {
        return a.getApplicationId().compareTo(b.getApplicationId());
      }
    });
    ApkInfo mainApk = apkList.get(0);
    ApkInfo dep1Apk = apkList.get(1);
    ApkInfo dep2Apk = apkList.get(2);
    assertEquals("com.test.app", mainApk.getApplicationId());
    assertTrue(Iterables.getOnlyElement(mainApk.getFiles()).getApkFile().getPath().endsWith("app.apk"));
    assertEquals("com.test.dep1", dep1Apk.getApplicationId());
    assertTrue(Iterables.getOnlyElement(dep1Apk.getFiles()).getApkFile().getPath().endsWith("dep1.apk"));
    assertEquals("com.test.dep2", dep2Apk.getApplicationId());
    assertTrue(Iterables.getOnlyElement(dep2Apk.getFiles()).getApkFile().getPath().endsWith("dep2.apk"));
  }
}
