/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.variant.view;

import static com.android.SdkConstants.ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE;
import static com.android.tools.idea.testing.TestProjectPaths.DEPENDENT_MODULES;
import static com.android.tools.idea.testing.TestProjectPaths.DEPENDENT_NATIVE_MODULES;
import static com.android.tools.idea.testing.TestProjectPaths.DYNAMIC_APP;
import static com.android.tools.idea.testing.TestProjectPaths.TRANSITIVE_DEPENDENCIES;
import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.appendToFile;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.util.io.FileUtil.writeToFile;
import static com.intellij.util.containers.ContainerUtil.map;

import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.BuildEnvironment;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager;
import com.intellij.openapi.util.Ref;
import com.intellij.testFramework.PlatformTestUtil;
import java.io.File;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

public class BuildVariantUpdaterIntegTest extends AndroidGradleTestCase {

  public void testWithModules() throws Exception {
    loadProject(DYNAMIC_APP);

    {
      AndroidModuleModel appAndroidModel = AndroidModuleModel.get(getModule("app"));
      AndroidModuleModel featureAndroidModel = AndroidModuleModel.get(getModule("feature1"));
      assertNotNull(appAndroidModel);
      assertNotNull(featureAndroidModel);
      assertEquals("debug", appAndroidModel.getSelectedVariant().getName());
      assertEquals("debug", featureAndroidModel.getSelectedVariant().getName());
    }

    {
      BuildVariantUpdater.getInstance(getProject()).updateSelectedBuildVariant(getProject(), getModule("app").getName(), "release");
      AndroidModuleModel appAndroidModel = AndroidModuleModel.get(getModule("app"));
      AndroidModuleModel featureAndroidModel = AndroidModuleModel.get(getModule("feature1"));

      assertEquals("release", appAndroidModel.getSelectedVariant().getName());
      // TODO(b/159377709): Uncomment when switching feature module variants is fixed.
      // assertEquals("release", featureAndroidModel.getSelectedVariant().getName());
    }

    // Gets served from cache.
    {
      BuildVariantUpdater.getInstance(getProject()).updateSelectedBuildVariant(getProject(), getModule("app").getName(), "debug");
      AndroidModuleModel appAndroidModel = AndroidModuleModel.get(getModule("app"));
      AndroidModuleModel featureAndroidModel = AndroidModuleModel.get(getModule("feature1"));
      assertEquals("debug", appAndroidModel.getSelectedVariant().getName());
      assertEquals("debug", featureAndroidModel.getSelectedVariant().getName());
    }
  }

  public void testWithDepModules() throws Exception {
    loadProject(TRANSITIVE_DEPENDENCIES);

    assertEquals("debug", getVariant("app"));
    assertEquals("debug", getVariant("library1"));
    assertEquals("debug", getVariant("library2"));

    // Switch selected variant from debug to release.
    BuildVariantUpdater.getInstance(getProject()).updateSelectedBuildVariant(getProject(), getModule("app").getName(), "release");
    assertEquals("release", getVariant("app"));
    assertEquals("release", getVariant("library1"));
    assertEquals("release", getVariant("library2"));

    // Switch selected variant from release to debug.
    BuildVariantUpdater.getInstance(getProject()).updateSelectedBuildVariant(getProject(), getModule("app").getName(), "debug");
    assertEquals("debug", getVariant("app"));
    assertEquals("debug", getVariant("library1"));
    assertEquals("debug", getVariant("library2"));
  }

  @NotNull
  private String getVariant(@NotNull String moduleName) {
    AndroidModuleModel moduleModel = AndroidModuleModel.get(getModule(moduleName));
    assertNotNull(moduleModel);
    return moduleModel.getSelectedVariant().getName();
  }

  public void testWithNonExistingFeatureVariant() throws Exception {
    loadProject(DYNAMIC_APP);

    // Define new buildType qa in app module.
    File appBuildFile = getBuildFilePath("app");
    appendToFile(appBuildFile, "\nandroid.buildTypes { qa { } }\n");

    requestSyncAndWait();

    {
      // Verify debug is selected by default.
      AndroidModuleModel appAndroidModel = AndroidModuleModel.get(getModule("app"));
      AndroidModuleModel featureAndroidModel = AndroidModuleModel.get(getModule("feature1"));
      assertNotNull(appAndroidModel);
      assertNotNull(featureAndroidModel);
      assertEquals("debug", appAndroidModel.getSelectedVariant().getName());
      assertEquals("debug", featureAndroidModel.getSelectedVariant().getName());
    }

    {
      // Switch selected variant for app module to qa.
      BuildVariantUpdater.getInstance(getProject()).updateSelectedBuildVariant(getProject(), getModule("app").getName(), "qa");
      AndroidModuleModel appAndroidModel = AndroidModuleModel.get(getModule("app"));
      AndroidModuleModel featureAndroidModel = AndroidModuleModel.get(getModule("feature1"));

      // Verify that variant for app module is updated to qa, and is unchanged for feature module since feature module doesn't contain variant qa.
      assertEquals("qa", appAndroidModel.getSelectedVariant().getName());
      assertEquals("debug", featureAndroidModel.getSelectedVariant().getName());
      AndroidFacet featureFacet = AndroidFacet.getInstance(getModule("feature1"));
      assertEquals("debug", featureFacet.getProperties().SELECTED_BUILD_VARIANT);
    }
  }

  public void testWithProductFlavors() throws Exception {
    loadProject(DEPENDENT_MODULES);
    {
      AndroidModuleModel appAndroidModel = AndroidModuleModel.get(getModule("app"));
      AndroidModuleModel libAndroidModel = AndroidModuleModel.get(getModule("lib"));
      assertNotNull(appAndroidModel);
      assertNotNull(libAndroidModel);
      assertEquals("basicDebug", appAndroidModel.getSelectedVariant().getName());
      assertEquals("debug", libAndroidModel.getSelectedVariant().getName());
    }

    // Triggers a sync.
    {
      BuildVariantUpdater.getInstance(getProject()).updateSelectedBuildVariant(getProject(), getModule("app").getName(), "basicRelease");
      AndroidModuleModel appAndroidModel = AndroidModuleModel.get(getModule("app"));
      AndroidModuleModel libAndroidModel = AndroidModuleModel.get(getModule("lib"));
      assertEquals("basicRelease", appAndroidModel.getSelectedVariant().getName());
      assertEquals("release", libAndroidModel.getSelectedVariant().getName());
    }

    // Gets served from cache.
    {
      BuildVariantUpdater.getInstance(getProject()).updateSelectedBuildVariant(getProject(), getModule("app").getName(), "basicDebug");
      AndroidModuleModel appAndroidModel = AndroidModuleModel.get(getModule("app"));
      AndroidModuleModel libAndroidModel = AndroidModuleModel.get(getModule("lib"));
      assertEquals("basicDebug", appAndroidModel.getSelectedVariant().getName());
      assertEquals("debug", libAndroidModel.getSelectedVariant().getName());
    }
  }

  public void testWithNativeModulesChangeBuildVariant() throws Exception {
    loadProject(DEPENDENT_NATIVE_MODULES);
    {
      AndroidModuleModel appAndroidModel = AndroidModuleModel.get(getModule("app"));
      AndroidModuleModel lib1AndroidModel = AndroidModuleModel.get(getModule("lib1"));
      AndroidModuleModel lib2AndroidModel = AndroidModuleModel.get(getModule("lib2"));
      AndroidModuleModel lib3AndroidModel = AndroidModuleModel.get(getModule("lib3"));
      assertNotNull(appAndroidModel);
      assertNotNull(lib1AndroidModel);
      assertNotNull(lib2AndroidModel);
      assertNotNull(lib3AndroidModel);
      assertEquals("debug", appAndroidModel.getSelectedVariant().getName());
      assertEquals("debug", lib1AndroidModel.getSelectedVariant().getName());
      assertEquals("debug", lib2AndroidModel.getSelectedVariant().getName());
      assertEquals("debug", lib3AndroidModel.getSelectedVariant().getName());

      NdkModuleModel appNdkModel = NdkModuleModel.get(getModule("app"));
      NdkFacet appNdkFacet = NdkFacet.getInstance(getModule("app"));
      NdkModuleModel lib1NdkModel = NdkModuleModel.get(getModule("lib1"));
      NdkModuleModel lib2NdkModel = NdkModuleModel.get(getModule("lib2"));
      NdkFacet lib2NdkFacet = NdkFacet.getInstance(getModule("lib2"));
      NdkModuleModel lib3NdkModel = NdkModuleModel.get(getModule("lib3"));
      NdkFacet lib3NdkFacet = NdkFacet.getInstance(getModule("lib3"));
      assertNotNull(appNdkModel);
      assertNull(lib1NdkModel);
      assertNotNull(lib2NdkModel);
      assertNotNull(lib3NdkModel);
      assertEquals("debug-x86", appNdkFacet.getSelectedVariantAbi().getDisplayName());
      assertEquals("debug-x86", lib2NdkFacet.getSelectedVariantAbi().getDisplayName());
      assertEquals("debug-x86", lib3NdkFacet.getSelectedVariantAbi().getDisplayName());
    }
    // Triggers a sync.
    {
      BuildVariantUpdater.getInstance(getProject()).updateSelectedBuildVariant(getProject(), getModule("app").getName(), "release");
      AndroidModuleModel appAndroidModel = AndroidModuleModel.get(getModule("app"));
      AndroidModuleModel lib1AndroidModel = AndroidModuleModel.get(getModule("lib1"));
      AndroidModuleModel lib2AndroidModel = AndroidModuleModel.get(getModule("lib2"));
      AndroidModuleModel lib3AndroidModel = AndroidModuleModel.get(getModule("lib3"));
      assertEquals("release", appAndroidModel.getSelectedVariant().getName());
      assertEquals("release", lib1AndroidModel.getSelectedVariant().getName());
      assertEquals("release", lib2AndroidModel.getSelectedVariant().getName());
      assertEquals("release", lib3AndroidModel.getSelectedVariant().getName());

      NdkFacet appNdkFacet = NdkFacet.getInstance(getModule("app"));
      NdkFacet lib2NdkFacet = NdkFacet.getInstance(getModule("lib2"));
      NdkFacet lib3NdkFacet = NdkFacet.getInstance(getModule("lib3"));
      assertEquals("release-x86", appNdkFacet.getSelectedVariantAbi().getDisplayName());
      // TODO(b/168472164): Uncomment when variant switching with native variants is fixed.
//      assertEquals("release-x86", lib2NdkFacet.getSelectedVariantAbi().getDisplayName());
//      assertEquals("release-x86", lib3NdkFacet.getSelectedVariantAbi().getDisplayName());
    }
    // Gets served from cache.
    BuildVariantUpdater.getInstance(getProject()).updateSelectedBuildVariant(getProject(), getModule("app").getName(), "debug");
    {
      AndroidModuleModel appAndroidModel = AndroidModuleModel.get(getModule("app"));
      AndroidModuleModel lib1AndroidModel = AndroidModuleModel.get(getModule("lib1"));
      AndroidModuleModel lib2AndroidModel = AndroidModuleModel.get(getModule("lib2"));
      AndroidModuleModel lib3AndroidModel = AndroidModuleModel.get(getModule("lib3"));
      assertEquals("debug", appAndroidModel.getSelectedVariant().getName());
      assertEquals("debug", lib1AndroidModel.getSelectedVariant().getName());
      assertEquals("debug", lib2AndroidModel.getSelectedVariant().getName());
      assertEquals("debug", lib3AndroidModel.getSelectedVariant().getName());

      NdkFacet appNdkFacet = NdkFacet.getInstance(getModule("app"));
      NdkFacet lib2NdkFacet = NdkFacet.getInstance(getModule("lib2"));
      NdkFacet lib3NdkFacet = NdkFacet.getInstance(getModule("lib3"));
      assertEquals("debug-x86", appNdkFacet.getSelectedVariantAbi().getDisplayName());
      assertEquals("debug-x86", lib2NdkFacet.getSelectedVariantAbi().getDisplayName());
      assertEquals("debug-x86", lib3NdkFacet.getSelectedVariantAbi().getDisplayName());
    }
  }

  public void testWithNativeModulesChangeAbi() throws Exception {
    loadProject(DEPENDENT_NATIVE_MODULES);

    {
      AndroidModuleModel appAndroidModel = AndroidModuleModel.get(getModule("app"));
      AndroidModuleModel lib1AndroidModel = AndroidModuleModel.get(getModule("lib1"));
      AndroidModuleModel lib2AndroidModel = AndroidModuleModel.get(getModule("lib2"));
      AndroidModuleModel lib3AndroidModel = AndroidModuleModel.get(getModule("lib3"));
      assertNotNull(appAndroidModel);
      assertNotNull(lib1AndroidModel);
      assertNotNull(lib2AndroidModel);
      assertNotNull(lib3AndroidModel);
      assertEquals("debug", appAndroidModel.getSelectedVariant().getName());
      assertEquals("debug", lib1AndroidModel.getSelectedVariant().getName());
      assertEquals("debug", lib2AndroidModel.getSelectedVariant().getName());
      assertEquals("debug", lib3AndroidModel.getSelectedVariant().getName());

      NdkModuleModel appNdkModel = NdkModuleModel.get(getModule("app"));
      NdkFacet appNdkFacet = NdkFacet.getInstance(getModule("app"));
      NdkModuleModel lib1NdkModel = NdkModuleModel.get(getModule("lib1"));
      NdkModuleModel lib2NdkModel = NdkModuleModel.get(getModule("lib2"));
      NdkFacet lib2NdkFacet = NdkFacet.getInstance(getModule("lib2"));
      NdkModuleModel lib3NdkModel = NdkModuleModel.get(getModule("lib3"));
      NdkFacet lib3NdkFacet = NdkFacet.getInstance(getModule("lib3"));
      assertNotNull(appNdkModel);
      assertNull(lib1NdkModel);
      assertNotNull(lib2NdkModel);
      assertNotNull(lib3NdkModel);
      assertEquals("debug-x86", appNdkFacet.getSelectedVariantAbi().getDisplayName());
      assertThat(appNdkModel.getSyncedVariantAbis()).contains(appNdkFacet.getSelectedVariantAbi());
      assertEquals("debug-x86", lib2NdkFacet.getSelectedVariantAbi().getDisplayName());
      assertThat(lib2NdkModel.getSyncedVariantAbis()).contains(lib2NdkFacet.getSelectedVariantAbi());
      assertEquals("debug-x86", lib3NdkFacet.getSelectedVariantAbi().getDisplayName());
      assertThat(lib3NdkModel.getSyncedVariantAbis()).contains(lib3NdkFacet.getSelectedVariantAbi());
    }

    // Triggers a sync.
    {
      BuildVariantUpdater.getInstance(getProject()).updateSelectedAbi(getProject(), getModule("app").getName(), "armeabi-v7a");
      AndroidModuleModel appAndroidModel = AndroidModuleModel.get(getModule("app"));
      AndroidModuleModel lib1AndroidModel = AndroidModuleModel.get(getModule("lib1"));
      AndroidModuleModel lib2AndroidModel = AndroidModuleModel.get(getModule("lib2"));
      AndroidModuleModel lib3AndroidModel = AndroidModuleModel.get(getModule("lib3"));
      assertEquals("debug", appAndroidModel.getSelectedVariant().getName());
      assertEquals("debug", lib1AndroidModel.getSelectedVariant().getName());
      assertEquals("debug", lib2AndroidModel.getSelectedVariant().getName());
      assertEquals("debug", lib3AndroidModel.getSelectedVariant().getName());

      NdkModuleModel appNdkModel = NdkModuleModel.get(getModule("app"));
      NdkFacet appNdkFacet = NdkFacet.getInstance(getModule("app"));
      NdkModuleModel lib2NdkModel = NdkModuleModel.get(getModule("lib2"));
      NdkFacet lib2NdkFacet = NdkFacet.getInstance(getModule("lib2"));
      NdkModuleModel lib3NdkModel = NdkModuleModel.get(getModule("lib3"));
      NdkFacet lib3NdkFacet = NdkFacet.getInstance(getModule("lib3"));
      assertEquals("debug-armeabi-v7a", appNdkFacet.getSelectedVariantAbi().getDisplayName());
      assertThat(appNdkModel.getSyncedVariantAbis()).contains(appNdkFacet.getSelectedVariantAbi());
      assertEquals("debug-armeabi-v7a", lib2NdkFacet.getSelectedVariantAbi().getDisplayName());
      assertThat(lib2NdkModel.getSyncedVariantAbis()).contains(lib2NdkFacet.getSelectedVariantAbi());
      assertEquals("debug-armeabi-v7a", lib3NdkFacet.getSelectedVariantAbi().getDisplayName());
      // TODO(b/168472164): Uncomment when ABI switching across non-native modules is fixed.
      // assertThat(lib3NdkModel.getSyncedVariantAbis()).contains(lib3NdkFacet.getSelectedVariantAbi());
    }

    // Gets served from cache.
    BuildVariantUpdater.getInstance(getProject()).updateSelectedAbi(getProject(), getModule("app").getName(), "x86");
    {
      AndroidModuleModel appAndroidModel = AndroidModuleModel.get(getModule("app"));
      AndroidModuleModel lib1AndroidModel = AndroidModuleModel.get(getModule("lib1"));
      AndroidModuleModel lib2AndroidModel = AndroidModuleModel.get(getModule("lib2"));
      AndroidModuleModel lib3AndroidModel = AndroidModuleModel.get(getModule("lib3"));
      assertEquals("debug", appAndroidModel.getSelectedVariant().getName());
      assertEquals("debug", lib1AndroidModel.getSelectedVariant().getName());
      assertEquals("debug", lib2AndroidModel.getSelectedVariant().getName());
      assertEquals("debug", lib3AndroidModel.getSelectedVariant().getName());

      NdkModuleModel appNdkModel = NdkModuleModel.get(getModule("app"));
      NdkFacet appNdkFacet = NdkFacet.getInstance(getModule("app"));
      NdkModuleModel lib2NdkModel = NdkModuleModel.get(getModule("lib2"));
      NdkFacet lib2NdkFacet = NdkFacet.getInstance(getModule("lib2"));
      NdkModuleModel lib3NdkModel = NdkModuleModel.get(getModule("lib3"));
      NdkFacet lib3NdkFacet = NdkFacet.getInstance(getModule("lib3"));
      assertEquals("debug-x86", appNdkFacet.getSelectedVariantAbi().getDisplayName());
      assertThat(appNdkModel.getSyncedVariantAbis()).contains(appNdkFacet.getSelectedVariantAbi());
      assertEquals("debug-x86", lib2NdkFacet.getSelectedVariantAbi().getDisplayName());
      assertThat(lib2NdkModel.getSyncedVariantAbis()).contains(lib2NdkFacet.getSelectedVariantAbi());
      assertEquals("debug-x86", lib3NdkFacet.getSelectedVariantAbi().getDisplayName());
      assertThat(lib3NdkModel.getSyncedVariantAbis()).contains(lib3NdkFacet.getSelectedVariantAbi());
    }
  }

  // Test the scenario when there are two app modules in one project, and they share the same set of dependency modules.
  // When variant selection is changed from UI window for one of the app modules, the selection of dependency modules should
  // be consistent with the module whose selection was changed from UI.
  public void testWithSharedDepModules() throws Exception {
    prepareProjectForImport(TRANSITIVE_DEPENDENCIES);

    // Create build file for module app2, so that
    //         app  -> library2 -> library1
    //         app2 -> library2 -> library1
    File buildFilePath = new File(getProjectFolderPath(), join("app2", FN_BUILD_GRADLE));
    writeToFile(buildFilePath, "apply plugin: 'com.android.application'\n" +
                               "android {\n" +
                               "    compileSdkVersion " + BuildEnvironment.getInstance().getCompileSdkVersion() + "\n" +
                               "}\n" +
                               "dependencies {\n" +
                               "    api project(':library2')\n" +
                               "}");

    // Add app2 to settings file.
    File settingsFile = new File(getProjectFolderPath(), FN_SETTINGS_GRADLE);
    String settingsText = Files.asCharSource(settingsFile, UTF_8).read();
    writeToFile(settingsFile, settingsText.trim() + ", \":app2\"");

    // Create manifest file for app2.
    File manifest = new File(getProjectFolderPath(), join("app2", "src", "main", ANDROID_MANIFEST_XML));
    writeToFile(manifest, "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                          "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"com.example\">\n" +
                          "</manifest>");

    requestSyncAndWait();
    assertEquals("debug", getVariant("app"));
    assertEquals("debug", getVariant("app2"));
    assertEquals("debug", getVariant("library1"));
    assertEquals("debug", getVariant("library2"));

    // Switch selected variant for app from debug to release.
    BuildVariantUpdater.getInstance(getProject()).updateSelectedBuildVariant(getProject(), getModule("app").getName(), "release");
    // Verify that app, library1, library2 are all switched to release. app2 remains as debug.
    assertEquals("release", getVariant("app"));
    assertEquals("debug", getVariant("app2"));
    assertEquals("release", getVariant("library1"));
    assertEquals("release", getVariant("library2"));

    // Switch selected variant for app from release to debug.
    BuildVariantUpdater.getInstance(getProject()).updateSelectedBuildVariant(getProject(), getModule("app").getName(), "debug");
    // Verify that app, library1, library2 are all switched back to debug.
    assertEquals("debug", getVariant("app"));
    assertEquals("debug", getVariant("app2"));
    assertEquals("debug", getVariant("library1"));
    assertEquals("debug", getVariant("library2"));

    // Switch selected variant for app2 from debug to release.
    BuildVariantUpdater.getInstance(getProject()).updateSelectedBuildVariant(getProject(), getModule("app2").getName(), "release");
    // Verify that app2, library1, library2 are all switched to release. app remains as debug.
    assertEquals("debug", getVariant("app"));
    assertEquals("release", getVariant("app2"));
    assertEquals("release", getVariant("library1"));
    assertEquals("release", getVariant("library2"));
  }

  public void testVariantsAreCached() throws Exception {
    final Ref<Boolean> syncPerformed = new Ref<>(false);
    ExternalSystemProgressNotificationManager notificationManager =
      ApplicationManager.getApplication().getService(ExternalSystemProgressNotificationManager.class);
    ExternalSystemTaskNotificationListenerAdapter listener = new ExternalSystemTaskNotificationListenerAdapter() {
      @Override
      public void onEnd(@NotNull ExternalSystemTaskId id) {
        syncPerformed.set(true);
      }
    };
    notificationManager.addNotificationListener(listener);

    try {
      loadProject(TRANSITIVE_DEPENDENCIES);

      // Verify that only debug variant is available.
      List<String> expectedVariants = ImmutableList.of("debug");
      verifyContainsVariant("app", expectedVariants);
      verifyContainsVariant("library1", expectedVariants);
      verifyContainsVariant("library2", expectedVariants);
      assertTrue(syncPerformed.get());

      // Switch selected variant from debug to release.
      syncPerformed.set(false);
      BuildVariantUpdater.getInstance(getProject()).updateSelectedBuildVariant(getProject(), getModule("app").getName(), "release");

      // Verify that debug and release variants are both available.
      expectedVariants = ImmutableList.of("debug", "release");
      verifyContainsVariant("app", expectedVariants);
      verifyContainsVariant("library1", expectedVariants);
      verifyContainsVariant("library2", expectedVariants);
      assertTrue(syncPerformed.get());

      // Switch back to debug.
      syncPerformed.set(false);
      BuildVariantUpdater.getInstance(getProject()).updateSelectedBuildVariant(getProject(), getModule("app").getName(), "debug");
      // Verify that no Gradle Sync was performed.
      assertFalse(syncPerformed.get());
    }
    finally {
      notificationManager.removeNotificationListener(listener);
    }
  }

  private void verifyContainsVariant(@NotNull String moduleName, @NotNull List<String> expectedVariantNames) {
    AndroidModuleModel androidModel = AndroidModuleModel.get(getModule(moduleName));
    List<String> variantsInModel = map(androidModel.getVariants(), variant -> variant.getName());
    assertThat(variantsInModel).containsExactlyElementsIn(expectedVariantNames);
  }

  public void testVariantsAreCachedWithNativeModules() throws Exception {
    final Ref<Boolean> syncPerformed = new Ref<>(false);
    ExternalSystemProgressNotificationManager notificationManager =
      ApplicationManager.getApplication().getService(ExternalSystemProgressNotificationManager.class);
    ExternalSystemTaskNotificationListenerAdapter listener = new ExternalSystemTaskNotificationListenerAdapter() {
      @Override
      public void onEnd(@NotNull ExternalSystemTaskId id) {
        syncPerformed.set(true);
      }
    };
    notificationManager.addNotificationListener(listener);

    // app module depends on lib2
    loadProject(DEPENDENT_NATIVE_MODULES);

    // Verify that only debug-x86 abi is available.
    List<String> expectedVariants = ImmutableList.of("debug-x86");
    verifyContainsVariantAbi("app", expectedVariants);
    verifyContainsVariantAbi("lib2", expectedVariants);
    assertTrue(syncPerformed.get());

    // Switch selected variant abi from x86 to armeabi-v7a.
    syncPerformed.set(false);
    BuildVariantUpdater.getInstance(getProject()).updateSelectedAbi(getProject(), getModule("app").getName(), "armeabi-v7a");

    // Verify that both of debug-x86 and debug-armeabi-v7a are available.
    expectedVariants = ImmutableList.of("debug-x86", "debug-armeabi-v7a");
    verifyContainsVariantAbi("app", expectedVariants);
    verifyContainsVariantAbi("lib2", expectedVariants);
    assertTrue(syncPerformed.get());

    // Switch back to x86.
    syncPerformed.set(false);
    BuildVariantUpdater.getInstance(getProject()).updateSelectedAbi(getProject(), getModule("app").getName(), "x86");
    // Verify that no Gradle Sync was performed.
    assertFalse(syncPerformed.get());
  }

  private void verifyContainsVariantAbi(@NotNull String moduleName, @NotNull List<String> expectedVariantNames) {
    NdkModuleModel ndkModel = NdkModuleModel.get(getModule(moduleName));
    List<String> variantAbisInModel = map(ndkModel.getSyncedVariantAbis(), variantAbi -> variantAbi.getDisplayName());
    assertThat(variantAbisInModel).containsExactlyElementsIn(expectedVariantNames);
  }
}
