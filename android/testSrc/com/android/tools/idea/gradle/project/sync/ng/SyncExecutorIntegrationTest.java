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
package com.android.tools.idea.gradle.project.sync.ng;

import com.android.builder.model.*;
import com.android.java.model.GradlePluginModel;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.sync.common.CommandLineArgs;
import com.android.tools.idea.gradle.project.sync.errors.SyncErrorHandlerManager;
import com.android.tools.idea.gradle.project.sync.ng.variantonly.VariantOnlyProjectModels;
import com.android.tools.idea.gradle.project.sync.ng.variantonly.VariantOnlyProjectModels.VariantOnlyModuleModel;
import com.android.tools.idea.gradle.project.sync.ng.variantonly.VariantOnlySyncOptions;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.BuildEnvironment;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.intellij.mock.MockProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.io.File;
import java.util.*;
import java.util.concurrent.CountDownLatch;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE;
import static com.android.tools.idea.gradle.project.sync.Modules.createUniqueModuleId;
import static com.android.tools.idea.testing.AndroidGradleTests.replaceRegexGroup;
import static com.android.tools.idea.testing.TestProjectPaths.*;
import static com.android.utils.FileUtils.join;
import static com.android.utils.FileUtils.writeToFile;
import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * Tests for {@link SyncExecutor}.
 */
public class SyncExecutorIntegrationTest extends AndroidGradleTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    StudioFlags.NEW_SYNC_INFRA_ENABLED.override(true);
    setUpIdeGradleSettings();
  }

  private void setUpIdeGradleSettings() {
    GradleProjectSettings settings = new GradleProjectSettings();
    settings.setDistributionType(DistributionType.DEFAULT_WRAPPED);
    settings.setExternalProjectPath(getProjectFolderPath().getPath());

    GradleSettings.getInstance(getProject()).setLinkedProjectsSettings(Collections.singleton(settings));
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.clearOverride();
      StudioFlags.NEW_SYNC_INFRA_ENABLED.clearOverride();
      StudioFlags.COMPOUND_SYNC_ENABLED.clearOverride();
    }
    finally {
      super.tearDown();
    }
  }

  public void testSyncProjectWithSingleVariantSync() throws Throwable {
    StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.override(true);

    prepareProjectForImport(SIMPLE_APPLICATION);

    Project project = getProject();

    // Simulate that "release" variant is selected in "app" module.
    SelectedVariantCollectorMock variantCollector = new SelectedVariantCollectorMock(project);
    variantCollector.setSelectedVariants("app", "release");

    SyncExecutor syncExecutor = new SyncExecutor(project, ExtraGradleSyncModelsManager.getInstance(),
                                                 new CommandLineArgs(true /* apply Java library plugin */),
                                                 new SyncErrorHandlerManager(project), variantCollector);

    SyncListener syncListener = new SyncListener();
    syncExecutor.syncProject(new MockProgressIndicator(), syncListener);
    syncListener.await();

    syncListener.propagateFailureIfAny();

    SyncProjectModels models = syncListener.getSyncModels();
    Map<String, SyncModuleModels> modelsByModule = indexByModuleName(models.getModuleModels());
    assertThat(modelsByModule).hasSize(2);

    verifyRequestedVariants(modelsByModule.get("app"), singletonList("release"));
  }

  public void testSyncProjectWithSingleVariantSyncOnFirstTime() throws Throwable {
    StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.override(true);

    prepareProjectForImport(TRANSITIVE_DEPENDENCIES);

    Project project = getProject();

    SyncExecutor syncExecutor = new SyncExecutor(project);

    SyncListener syncListener = new SyncListener();
    syncExecutor.syncProject(new MockProgressIndicator(), syncListener);
    syncListener.await();

    syncListener.propagateFailureIfAny();

    SyncProjectModels models = syncListener.getSyncModels();
    Map<String, SyncModuleModels> modelsByModule = indexByModuleName(models.getModuleModels());

    verifyRequestedVariants(modelsByModule.get("app"), singletonList("debug"));
    verifyRequestedVariants(modelsByModule.get("library1"), singletonList("debug"));
    verifyRequestedVariants(modelsByModule.get("library2"), singletonList("debug"));
  }

  public void testSyncProjectWithSingleVariantSyncWithRecursiveSelection() throws Throwable {
    StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.override(true);

    prepareProjectForImport(TRANSITIVE_DEPENDENCIES);

    Project project = getProject();
    // Simulate that "debug" variant is selected in "app" module.
    // "release" is selected in library1 and library2.
    SelectedVariantCollectorMock variantCollector = new SelectedVariantCollectorMock(project);
    variantCollector.setSelectedVariants("app", "debug");
    variantCollector.setSelectedVariants("library1", "release");
    variantCollector.setSelectedVariants("library2", "release");

    SyncExecutor syncExecutor = new SyncExecutor(project, ExtraGradleSyncModelsManager.getInstance(),
                                                 new CommandLineArgs(true /* apply Java library plugin */),
                                                 new SyncErrorHandlerManager(project), variantCollector);
    SyncListener syncListener = new SyncListener();
    syncExecutor.syncProject(new MockProgressIndicator(), syncListener);
    syncListener.await();

    syncListener.propagateFailureIfAny();

    SyncProjectModels models = syncListener.getSyncModels();
    Map<String, SyncModuleModels> modelsByModule = indexByModuleName(models.getModuleModels());

    // app -> library2 -> library1
    // Verify that the variant of library1 and library2 are selected based on app.
    verifyRequestedVariants(modelsByModule.get("app"), singletonList("debug"));
    verifyRequestedVariants(modelsByModule.get("library1"), singletonList("debug"));
    verifyRequestedVariants(modelsByModule.get("library2"), singletonList("debug"));
  }

  public void testSyncProjectWithSingleVariantSyncWithSelectionConflict() throws Throwable {
    StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.override(true);
    prepareProjectForImport(TRANSITIVE_DEPENDENCIES);
    Project project = getProject();

    // Create a new module library3, so that library3 -> library2 -> library1.
    File projectFolderPath = getProjectFolderPath();
    File buildFile = new File(projectFolderPath, join("library3", FN_BUILD_GRADLE));
    String text = "apply plugin: 'com.android.library'\n" +
                  "android {\n" +
                  "    compileSdkVersion " + BuildEnvironment.getInstance().getCompileSdkVersion() + "\n" +
                  "}\n" +
                  "dependencies {\n" +
                  "    api project(':library2')\n" +
                  "}";
    writeToFile(buildFile, text);
    File settingsFile = new File(projectFolderPath, FN_SETTINGS_GRADLE);
    String contents = Files.asCharSource(settingsFile, Charsets.UTF_8).read().trim();
    contents += ", ':library3'";
    writeToFile(settingsFile, contents);


    // Simulate that "debug" variant is selected in "app" module.
    // "release" is selected in "library3" module.
    // This will cause variant conflict because app -> library2, and library3 -> library2.
    SelectedVariantCollectorMock variantCollector = new SelectedVariantCollectorMock(project);
    variantCollector.setSelectedVariants("app", "debug");
    variantCollector.setSelectedVariants("library3", "release");

    SyncExecutor syncExecutor = new SyncExecutor(project, ExtraGradleSyncModelsManager.getInstance(),
                                                 new CommandLineArgs(true /* apply Java library plugin */),
                                                 new SyncErrorHandlerManager(project), variantCollector);
    SyncListener syncListener = new SyncListener();
    syncExecutor.syncProject(new MockProgressIndicator(), syncListener);
    syncListener.await();

    syncListener.propagateFailureIfAny();

    SyncProjectModels models = syncListener.getSyncModels();
    Map<String, SyncModuleModels> modelsByModule = indexByModuleName(models.getModuleModels());

    // app -> library2 -> library1
    // library3 -> library2 -> library1
    // Verify that library1 and library2 have both of debug and release variants requested.
    verifyRequestedVariants(modelsByModule.get("app"), singletonList("debug"));
    verifyRequestedVariants(modelsByModule.get("library3"), singletonList("release"));
    verifyRequestedVariants(modelsByModule.get("library1"), asList("debug", "release"));
    verifyRequestedVariants(modelsByModule.get("library2"), asList("debug", "release"));
  }

  public void testVariantOnlySyncWithDynamicApp() throws Throwable {
    StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.override(true);

    prepareProjectForImport(DYNAMIC_APP);

    Project project = getProject();
    // Simulate that "release" variant is selected in both modules.
    SelectedVariantCollectorMock variantCollector = new SelectedVariantCollectorMock(project);
    variantCollector.setSelectedVariants("app", "release");
    variantCollector.setSelectedVariants("feature1", "release");

    // Request variant only sync for "app" -> "debug".
    File buildId = getProjectFolderPath();
    VariantOnlySyncOptions options = new VariantOnlySyncOptions(buildId, ":app", "debug");

    SyncExecutor syncExecutor = new SyncExecutor(project, ExtraGradleSyncModelsManager.getInstance(),
                                                 new CommandLineArgs(false /* don't apply Java library plugin */),
                                                 new SyncErrorHandlerManager(project), variantCollector);
    SyncListener syncListener = new SyncListener();
    syncExecutor.syncProject(new MockProgressIndicator(), syncListener, options);
    syncListener.await();

    syncListener.propagateFailureIfAny();

    VariantOnlyProjectModels models = syncListener.getVariantOnlyModels();
    Map<String, VariantOnlyModuleModel> modelsByModuleId =
      models.getModuleModels().stream().collect(toMap(VariantOnlyModuleModel::getModuleId, m -> m));

    // Verify that feature module was also requested.
    verifyRequestedVariants(modelsByModuleId.get(createUniqueModuleId(buildId, ":app")), singletonList("debug"));
    verifyRequestedVariants(modelsByModuleId.get(createUniqueModuleId(buildId, ":feature1")), singletonList("debug"));
  }

  private static void verifyRequestedVariants(@NotNull SyncModuleModels moduleModels, @NotNull List<String> requestedVariants) {
    AndroidProject androidProject = moduleModels.findModel(AndroidProject.class);
    assertNotNull(androidProject);
    assertThat(androidProject.getVariants()).isEmpty();

    List<Variant> variants = moduleModels.findModels(Variant.class);
    assertNotNull(variants);
    assertThat(variants.stream().map(Variant::getName).collect(toList())).containsExactlyElementsIn(requestedVariants);
  }

  // Disabled while fixing b/131791484
  public void /*test*/SingleVariantSyncWithOldGradleVersion() throws Throwable {
    StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.override(true);

    // Use plugin 1.5.0 and Gradle 2.4.0
    prepareProjectForImport(PROJECT_WITH1_DOT5);
    File projectFolderPath = getProjectFolderPath();
    createGradleWrapper(projectFolderPath, "2.6");

    File topBuildFilePath = new File(projectFolderPath, "build.gradle");
    String contents = Files.asCharSource(topBuildFilePath, Charsets.UTF_8).read();

    contents = replaceRegexGroup(contents, "classpath ['\"]com.android.tools.build:gradle:(.+)['\"]", "1.5.0");
    // Remove constraint-layout, which was not supported by old plugins.
    contents = replaceRegexGroup(contents, "(compile 'com.android.support.constraint:constraint-layout:\\+')", "");
    Files.asCharSink(topBuildFilePath, Charsets.UTF_8).write(contents);

    Project project = getProject();

    // Simulate that "release" variant is selected in "app" module.
    SelectedVariantCollectorMock variantCollector = new SelectedVariantCollectorMock(project);
    variantCollector.setSelectedVariants("app", "release");

    SyncExecutor syncExecutor = new SyncExecutor(project, ExtraGradleSyncModelsManager.getInstance(),
                                                 new CommandLineArgs(true /* apply Java library plugin */),
                                                 new SyncErrorHandlerManager(project), variantCollector);

    SyncListener syncListener = new SyncListener();
    syncExecutor.syncProject(new MockProgressIndicator(), syncListener);
    syncListener.await();

    syncListener.propagateFailureIfAny();
    SyncProjectModels models = syncListener.getSyncModels();
    Map<String, SyncModuleModels> modelsByModule = indexByModuleName(models.getModuleModels());
    assertThat(modelsByModule).hasSize(2);

    SyncModuleModels appModels = modelsByModule.get("app");
    AndroidProject androidProject = appModels.findModel(AndroidProject.class);
    assertNotNull(androidProject);
    Collection<Variant> variants = androidProject.getVariants();
    assertThat(variants).isNotEmpty();
    assertNull(appModels.findModel(Variant.class));
  }

  @NotNull
  private static Map<String, SyncModuleModels> indexByModuleName(@NotNull List<SyncModuleModels> allModuleModels) {
    Map<String, SyncModuleModels> modelsByModuleName = new HashMap<>();
    for (SyncModuleModels moduleModels : allModuleModels) {
      modelsByModuleName.put(moduleModels.getModuleName(), moduleModels);
    }
    return modelsByModuleName;
  }

  public void testVariantOnlySyncWithRecursiveSelection() throws Throwable {
    prepareProjectForImport(TRANSITIVE_DEPENDENCIES);

    Project project = getProject();
    SyncExecutor syncExecutor = new SyncExecutor(project, ExtraGradleSyncModelsManager.getInstance(),
                                                 new CommandLineArgs(true /* apply Java library plugin */),
                                                 new SyncErrorHandlerManager(project), new SelectedVariantCollectorMock(project));
    SyncListener syncListener = new SyncListener();

    // Request for single-variant sync for library2 with variant "release".
    File buildId = getProjectFolderPath();
    VariantOnlySyncOptions options = new VariantOnlySyncOptions(buildId, ":library2", "release");
    syncExecutor.syncProject(new MockProgressIndicator(), syncListener, options);
    syncListener.await();

    syncListener.propagateFailureIfAny();

    VariantOnlyProjectModels models = syncListener.getVariantOnlyModels();
    Map<String, VariantOnlyModuleModel> modelsByModuleId =
      models.getModuleModels().stream().collect(toMap(VariantOnlyModuleModel::getModuleId, m -> m));

    // app -> library2 -> library1
    // Verify that models for app was not requested.
    assertNull(modelsByModuleId.get(createUniqueModuleId(buildId, ":app")));
    // Verify that models for library1 and library2 are requested based on user selection.
    verifyRequestedVariants(modelsByModuleId.get(createUniqueModuleId(buildId, ":library1")), singletonList("release"));
    verifyRequestedVariants(modelsByModuleId.get(createUniqueModuleId(buildId, ":library2")), singletonList("release"));
  }

  public void testGradlePluginModel() throws Throwable {
    prepareProjectForImport(SIMPLE_APPLICATION);
    Project project = getProject();
    SelectedVariantCollectorMock variantCollector = new SelectedVariantCollectorMock(project);
    SyncExecutor syncExecutor = new SyncExecutor(project, ExtraGradleSyncModelsManager.getInstance(),
                                                 new CommandLineArgs(true /* apply Java library plugin */),
                                                 new SyncErrorHandlerManager(project), variantCollector);
    SyncListener syncListener = new SyncListener();
    syncExecutor.syncProject(new MockProgressIndicator(), syncListener);
    syncListener.await();

    syncListener.propagateFailureIfAny();
    SyncProjectModels models = syncListener.getSyncModels();
    for (SyncModuleModels moduleModels : models.getModuleModels()) {
      // Verify that GradlePluginModel is available for all Gradle modules.
      GradlePluginModel pluginModel = moduleModels.findModel(GradlePluginModel.class);
      assertNotNull(pluginModel);
      Collection<String> plugins = pluginModel.getGradlePluginList();
      // Verify that Java Library Plugin is in the plugin list of each module.
      assertThat(plugins).contains("com.android.java.model.builder.JavaLibraryPlugin");
      if ("app".equals(moduleModels.getModuleName())) {
        // Verify that Android plugin is in the plugin list of app module.
        assertThat(plugins).contains("com.android.build.gradle.AppPlugin");
      }
    }
  }

  public void testSyncProjectWithCompoundSync() throws Throwable {
    StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.override(true);
    StudioFlags.COMPOUND_SYNC_ENABLED.override(true);

    prepareProjectForImport(SIMPLE_APPLICATION);

    Project project = getProject();

    // Simulate that "release" variant is selected in "app" module.
    SelectedVariantCollectorMock variantCollector = new SelectedVariantCollectorMock(project);
    variantCollector.setSelectedVariants("app", "release");

    SyncExecutor syncExecutor = new SyncExecutor(project, ExtraGradleSyncModelsManager.getInstance(),
                                                 new CommandLineArgs(true /* apply Java library plugin */),
                                                 new SyncErrorHandlerManager(project), variantCollector);

    SyncListener syncListener = new SyncListener();
    syncExecutor.syncProject(new MockProgressIndicator(), syncListener, null, null, null, null, true);
    syncListener.await();

    syncListener.propagateFailureIfAny();

    SyncProjectModels models = syncListener.getSyncModels();
    Map<String, SyncModuleModels> modelsByModule = indexByModuleName(models.getModuleModels());
    assertThat(modelsByModule).hasSize(2);

    verifyRequestedVariants(modelsByModule.get("app"), singletonList("release"));
  }

  private static void verifyRequestedVariants(@NotNull VariantOnlyModuleModel moduleModels, @NotNull List<String> requestedVariants) {
    AndroidProject androidProject = moduleModels.getAndroidProject();
    assertNotNull(androidProject);
    assertThat(androidProject.getVariants()).isEmpty();

    for (String variant : requestedVariants) {
      assertTrue(moduleModels.containsVariant(variant));
    }
  }

  public void testSyncProjectWithSingleVariantSyncWithNdkProject() throws Throwable {
    StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.override(true);

    prepareProjectForImport(HELLO_JNI);

    Project project = getProject();

    // Simulate that "release" variant is selected in "app" module.
    String variant = "x86Release";
    String abi = "x86";
    SelectedVariantCollectorMock variantCollector = new SelectedVariantCollectorMock(project);
    variantCollector.setSelectedVariants("app", variant, abi);

    SyncExecutor syncExecutor = new SyncExecutor(project, ExtraGradleSyncModelsManager.getInstance(),
                                                 new CommandLineArgs(true /* apply Java library plugin */),
                                                 new SyncErrorHandlerManager(project), variantCollector);

    SyncListener syncListener = new SyncListener();
    syncExecutor.syncProject(new MockProgressIndicator(), syncListener);
    syncListener.await();

    syncListener.propagateFailureIfAny();

    SyncProjectModels models = syncListener.getSyncModels();
    Map<String, SyncModuleModels> modelsByModule = indexByModuleName(models.getModuleModels());
    assertThat(modelsByModule).hasSize(2);

    SyncModuleModels moduleModels = modelsByModule.get("app");
    verifyRequestedVariants(moduleModels, singletonList(variant));

    NativeAndroidProject nativeProject = moduleModels.findModel(NativeAndroidProject.class);
    assertNotNull(nativeProject);
    assertThat(nativeProject.getArtifacts()).isEmpty();

    List<NativeVariantAbi> variants = moduleModels.findModels(NativeVariantAbi.class);
    assertNotNull(variants);
    List<NativeArtifact> artifacts = variants.stream()
                                             .flatMap(it -> it.getArtifacts().stream())
                                             .filter(it -> variant.equals(it.getGroupName()) && abi.equals(it.getAbi()))
                                             .collect(toList());
    assertNotEmpty(artifacts);
  }

  public void testVariantOnlySyncWithNdkProject() throws Throwable {
    prepareProjectForImport(HELLO_JNI);

    Project project = getProject();
    SyncExecutor syncExecutor = new SyncExecutor(project, ExtraGradleSyncModelsManager.getInstance(),
                                                 new CommandLineArgs(true /* apply Java library plugin */),
                                                 new SyncErrorHandlerManager(project), new SelectedVariantCollectorMock(project));
    SyncListener syncListener = new SyncListener();

    // Request for single-variant sync for app module with variant "x86Release", abi "x86".
    String variant = "x86Release";
    String abi = "x86";
    File buildId = getProjectFolderPath();
    VariantOnlySyncOptions options = new VariantOnlySyncOptions(buildId, ":app", variant, abi, false);
    syncExecutor.syncProject(new MockProgressIndicator(), syncListener, options);
    syncListener.await();

    syncListener.propagateFailureIfAny();

    VariantOnlyProjectModels models = syncListener.getVariantOnlyModels();
    Map<String, VariantOnlyModuleModel> modelsByModuleId =
      models.getModuleModels().stream().collect(toMap(VariantOnlyModuleModel::getModuleId, m -> m));

    VariantOnlyModuleModel moduleModels = modelsByModuleId.get(createUniqueModuleId(buildId, ":app"));
    // Verify that variant x86Release is requested for Android variant.
    verifyRequestedVariants(moduleModels, singletonList(variant));
    // Verify that variant x86Release, and abi x86 is requested for NativeVariantAbi.
    NativeVariantAbi nativeVariantAbi = moduleModels.getNativeVariantAbi().model;
    assertNotNull(nativeVariantAbi);
    List<NativeArtifact> artifacts = nativeVariantAbi.getArtifacts().stream()
                                                     .filter(it -> variant.equals(it.getGroupName()) && abi.equals(it.getAbi()))
                                                     .collect(toList());
    assertNotEmpty(artifacts);
  }

  public void testFetchGradleModelsWithSingleVariantSync() throws Throwable {
    StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.override(true);
    prepareProjectForImport(SIMPLE_APPLICATION);
    Project project = getProject();

    // Simulate that "release" variant is selected in "app" module.
    SelectedVariantCollectorMock variantCollector = new SelectedVariantCollectorMock(project);
    variantCollector.setSelectedVariants("app", "release");

    SyncExecutor syncExecutor = new SyncExecutor(project, ExtraGradleSyncModelsManager.getInstance(),
                                                 new CommandLineArgs(true /* apply Java library plugin */),
                                                 new SyncErrorHandlerManager(project), variantCollector);

    Map<String, SyncModuleModels> modelsByName = indexByModuleName(syncExecutor.fetchGradleModels(new MockProgressIndicator()));
    assertThat(modelsByName).hasSize(2);
    SyncModuleModels appModuleModels = modelsByName.get("app");

    // Verify that full variants are requested even single-variant sync is enabled.
    AndroidProject androidProject = appModuleModels.findModel(AndroidProject.class);
    assertNotNull(androidProject);
    assertThat(androidProject.getVariants()).hasSize(2);
    assertNull(appModuleModels.findModels(Variant.class));
  }

  private static class SelectedVariantCollectorMock extends SelectedVariantCollector {
    @NotNull private final SelectedVariants mySelectedVariants = new SelectedVariants();
    @NotNull private final File myProjectFolderPath;

    SelectedVariantCollectorMock(@NotNull Project project) {
      super(project);
      myProjectFolderPath = new File(project.getBasePath());
    }

    @Override
    @NotNull
    public SelectedVariants collectSelectedVariants() {
      return mySelectedVariants;
    }

    void setSelectedVariants(@NotNull String moduleName, @NotNull String selectedVariant) {
      setSelectedVariants(moduleName, selectedVariant, null);
    }

    void setSelectedVariants(@NotNull String moduleName, @NotNull String selectedVariant, @Nullable String selectedAbi) {
      String moduleId = createUniqueModuleId(myProjectFolderPath, ":" + moduleName);
      mySelectedVariants.addSelectedVariant(moduleId, selectedVariant, selectedAbi);
    }
  }

  private static class SyncListener extends SyncExecutionCallback {
    @NotNull private final CountDownLatch myCountDownLatch = new CountDownLatch(1);
    private boolean myFailed;

    SyncListener() {
      doWhenDone(() -> myCountDownLatch.countDown());

      doWhenRejected(s -> {
        myFailed = true;
        myCountDownLatch.countDown();
      });
    }

    void await() throws InterruptedException {
      myCountDownLatch.await(5, MINUTES);
    }

    void propagateFailureIfAny() throws Throwable {
      if (myFailed) {
        Throwable error = getSyncError();
        if (error != null) {
          throw error;
        }
        throw new AssertionError("Sync failed - unknown cause");
      }
    }
  }
}
