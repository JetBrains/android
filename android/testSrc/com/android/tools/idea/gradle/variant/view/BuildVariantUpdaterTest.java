/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.tools.idea.projectsystem.ProjectSystemUtil.getProjectSystem;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.gradleModule;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.setupTestProjectFromAndroidModel;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.switchTestProjectVariantsFromAndroidModel;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.model.VariantAbi;
import com.android.tools.idea.project.SyncTimestampUtil;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult;
import com.android.tools.idea.testing.AndroidGradleTestUtilsKt;
import com.android.tools.idea.testing.AndroidModuleDependency;
import com.android.tools.idea.testing.AndroidModuleModelBuilder;
import com.android.tools.idea.testing.AndroidProjectBuilder;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiFile;
import java.io.File;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tests for {@link BuildVariantUpdater}.
 */
public class BuildVariantUpdaterTest extends AndroidTestCase {
  private int mySelectionChangeCounter = 0;
  private BuildVariantUpdater myBuildVariantUpdater;
  private final BuildVariantView.BuildVariantSelectionChangeListener myVariantSelectionChangeListener =
    () -> mySelectionChangeCounter++;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myBuildVariantUpdater = BuildVariantUpdater.getInstance(getProject());
    myBuildVariantUpdater.addSelectionChangeListener(myVariantSelectionChangeListener);
  }

  private void setupProject(AndroidModuleModelBuilder... modelBuilders) {
    setupTestProjectFromAndroidModel(
      getProject(),
      new File(getProject().getBasePath()),
      true,
      modelBuilders
    );
  }

  private void expectAndSimulateSync(Runnable block, AndroidModuleModelBuilder... modelBuilders) {
    ProjectSystemSyncManager syncState = getProjectSystem(getProject()).getSyncManager();
    long stamp = SyncTimestampUtil.getLastSyncTimestamp(getProject());
    block.run();
    assertThat(SyncTimestampUtil.getLastSyncTimestamp(getProject())).isGreaterThan(stamp);
    assertThat(syncState.getLastSyncResult()).isEqualTo(SyncResult.SKIPPED);
    switchTestProjectVariantsFromAndroidModel(
      getProject(),
      new File(getProject().getBasePath()),
      modelBuilders
    );
  }

  public void testUpdateSelectedVariant() {
    AndroidModuleModelBuilder root = new AndroidModuleModelBuilder(
      ":",
      "debug",
      new AndroidProjectBuilder()
    );

    setupProject(root);

    String variantToSelect = "release";

    expectAndSimulateSync(
      () -> {
        myBuildVariantUpdater.updateSelectedBuildVariant(getProject(), module(":").getName(), variantToSelect);
        assertThat(selectedVariant(":")).isEqualTo(variantToSelect);
        assertThat(selectedAbi(":")).isNull();
      },
      root.withSelectedBuildVariant(variantToSelect)
    );

    assertThat(androidModuleModel(":").getSelectedVariantName()).isEqualTo(variantToSelect);
    assertChangeHappened();
  }

  // Initial variant/ABI selection:
  //    app: debug-x86_64      (native module)
  // The user is changing the build variant of app to "release".
  //
  // Target variants/ABIs have already been synced, and are served from cache.
  //
  // Expected final Variant/ABI selection:
  //    app: release-x86_64
  public void testUpdateSelectedVariantWithNdkModule() {
    AndroidModuleModelBuilder root = new AndroidModuleModelBuilder(
      ":",
      "debug",
      new AndroidProjectBuilder().withNdkModel(AndroidGradleTestUtilsKt::buildNdkModelStub)
    );

    setupProject(root);

    String variantToSelect = "release";

    expectAndSimulateSync(
      () -> {
        myBuildVariantUpdater.updateSelectedBuildVariant(getProject(), module(":").getName(), variantToSelect);
        assertThat(selectedVariant(":")).isEqualTo(variantToSelect);
        assertThat(selectedAbi(":")).isEqualTo("x86_64");
      },
      root.withSelectedBuildVariant(variantToSelect)
    );

    assertThat(androidModuleModel(":").getSelectedVariantName()).isEqualTo(variantToSelect);
    assertThat(safeGetAbi(ndkFacet(":").getSelectedVariantAbi())).isEqualTo("x86_64");
    assertThat(ndkModuleModel(":").getSelectedAbi()).isEqualTo("x86_64");
    assertChangeHappened();
  }

  // Initial variant/ABI selection:
  //    app: debug-x86_64      (native module)
  // The user is changing the ABI of app to "armeabi-v7a".
  //
  // Target variants/ABIs have already been synced, and are served from cache.
  //
  // Expected final Variant/ABI selection:
  //    app: debug-armeabi-v7a
  public void testUpdateSelectedAbiWithNdkModule() {
    AndroidModuleModelBuilder root = new AndroidModuleModelBuilder(
      ":",
      "debug",
      new AndroidProjectBuilder().withNdkModel(AndroidGradleTestUtilsKt::buildNdkModelStub)
    );

    setupProject(root);

    VariantAbi variantAbiToSelect = new VariantAbi("debug", "arm64-v8a");

    expectAndSimulateSync(
      () -> {
        myBuildVariantUpdater.updateSelectedAbi(getProject(), module(":").getName(), variantAbiToSelect.getAbi());
        assertThat(selectedVariant(":")).isEqualTo(variantAbiToSelect.getVariant());
        assertThat(selectedAbi(":")).isEqualTo(variantAbiToSelect.getAbi());
      },
      root.withSelectedBuildVariant(variantAbiToSelect.getVariant()).withSelectedAbi(variantAbiToSelect.getAbi())
    );

    assertThat(androidModuleModel(":").getSelectedVariantName()).isEqualTo(variantAbiToSelect.getVariant());
    assertThat(safeGetAbi(ndkFacet(":").getSelectedVariantAbi())).isEqualTo(variantAbiToSelect.getAbi());
    assertThat(ndkModuleModel(":").getSelectedAbi()).isEqualTo(variantAbiToSelect.getAbi());
    assertChangeHappened();
  }

  public void testUpdateSelectedVariantWithUnchangedVariantName() {
    AndroidModuleModelBuilder root = new AndroidModuleModelBuilder(
      ":",
      "debug",
      new AndroidProjectBuilder()
    );

    setupProject(root);
    String variantToSelect = "debug"; // The default selected variant after test setup is "debug".

    myBuildVariantUpdater.updateSelectedBuildVariant(getProject(), module(":").getName(), variantToSelect);

    assertThat(androidModuleModel(":").getSelectedVariantName()).isEqualTo(variantToSelect);
    assertThat(mySelectionChangeCounter).isEqualTo(0);
  }

  public void testUpdateSelectedVariantWithChangedBuildFiles() {
    // Simulate build files have changed since last sync.
    PsiFile buildFile = myFixture.addFileToProject("build.gradle", "// gradle");
    AndroidModuleModelBuilder root = new AndroidModuleModelBuilder(
      ":",
      "debug",
      new AndroidProjectBuilder()
    );

    setupProject(root);

    // First switch to release.
    {
      reset();
      String variantToSelect = "release";
      expectAndSimulateSync(
        () -> {
          myBuildVariantUpdater.updateSelectedBuildVariant(getProject(), module(":").getName(), variantToSelect);
          assertThat(selectedVariant(":")).isEqualTo(variantToSelect);
          assertThat(selectedAbi(":")).isNull();
        },
        root.withSelectedBuildVariant(variantToSelect)
      );

      assertThat(androidModuleModel(":").getSelectedVariantName()).isEqualTo(variantToSelect);
      assertChangeHappened();
    }

    // Then change the build file and switch back.
    myFixture.saveText(buildFile.getVirtualFile(), "android {}");

    {
      reset();
      String variantToSelect = "debug";
      expectAndSimulateSync(
        () -> {
          myBuildVariantUpdater.updateSelectedBuildVariant(getProject(), module(":").getName(), variantToSelect);
          assertThat(selectedVariant(":")).isEqualTo(variantToSelect);
          assertThat(selectedAbi(":")).isNull();
        },
        root
      );

      assertThat(androidModuleModel(":").getSelectedVariantName()).isEqualTo(variantToSelect);
      assertThat(mySelectionChangeCounter).isEqualTo(1);
    }
  }

  // app module depends on library module.
  // Initial Variant/ABI selection:
  //    app: debug      (non-native module)
  //    library: debug  (non-native module)
  // The user is changing the variant of app to "release".
  //
  // Target variants/ABIs have already been synced, and are served from cache.
  //
  // Expected final Variant/ABI selection:
  //    app: release
  //    library: release
  public void testAndroidModuleDependsOnAndroidModule() {
    AndroidModuleModelBuilder root = new AndroidModuleModelBuilder(
      ":",
      "debug",
      new AndroidProjectBuilder()
        .withAndroidModuleDependencyList((it, variant) -> ImmutableList.of(new AndroidModuleDependency(":lib", variant)))
    );
    AndroidModuleModelBuilder lib = new AndroidModuleModelBuilder(
      ":lib",
      "debug",
      new AndroidProjectBuilder()
    );

    setupProject(root, lib);

    String variantToSelect = "release";

    expectAndSimulateSync(
      () -> {
        myBuildVariantUpdater.updateSelectedBuildVariant(getProject(), module(":").getName(), variantToSelect);
        assertThat(selectedVariant(":")).isEqualTo(variantToSelect);
        assertThat(selectedAbi(":")).isNull();
      },
      root.withSelectedBuildVariant(variantToSelect),
      lib.withSelectedBuildVariant(variantToSelect)
    );

    assertThat(androidModuleModel(":").getSelectedVariantName()).isEqualTo(variantToSelect);
    assertThat(androidModuleModel(":lib").getSelectedVariantName()).isEqualTo(variantToSelect);
    assertChangeHappened();
  }

  // app module depends on library module.
  // Initial variant/ABI selection:
  //    app: debug-x86_64  (native module)
  //    library: debug  (non-native module)
  // The user is changing the variant of app to "release".
  //
  // Target variants/ABIs have already been synced, and are served from cache.
  //
  // Expected final Variant/ABI selection:
  //    app: release-x86_64
  //    library: release
  public void testNdkModuleDependsOnAndroidModule() {
    AndroidModuleModelBuilder root = new AndroidModuleModelBuilder(
      ":",
      "debug",
      new AndroidProjectBuilder()
        .withNdkModel(AndroidGradleTestUtilsKt::buildNdkModelStub)
        .withAndroidModuleDependencyList((it, variant) -> ImmutableList.of(new AndroidModuleDependency(":lib", variant)))
    );
    AndroidModuleModelBuilder lib = new AndroidModuleModelBuilder(
      ":lib",
      "debug",
      new AndroidProjectBuilder()
    );

    setupProject(root, lib);

    String variantToSelect = "release";

    expectAndSimulateSync(
      () -> {
        myBuildVariantUpdater.updateSelectedBuildVariant(getProject(), module(":").getName(), variantToSelect);
        assertThat(selectedVariant(":")).isEqualTo(variantToSelect);
        assertThat(selectedAbi(":")).isEqualTo("x86_64");
      },
      root.withSelectedBuildVariant(variantToSelect),
      lib.withSelectedBuildVariant(variantToSelect)
    );

    assertThat(androidModuleModel(":").getSelectedVariantName()).isEqualTo(variantToSelect);
    assertThat(safeGetAbi(ndkFacet(":").getSelectedVariantAbi())).isEqualTo("x86_64");
    assertThat(androidModuleModel(":lib").getSelectedVariantName()).isEqualTo(variantToSelect);
    assertChangeHappened();
  }

  // app module depends on library module.
  // Initial variant/ABI selection:
  //    app: debug          (non-native module)
  //    library: debug-x86_64  (native module)
  // The user is changing the variant of app to "release".
  //
  // Target variants/ABIs have already been synced, and are served from cache.
  //
  // Expected final Variant/ABI selection:
  //    app: release
  //    library: release-x86_64
  public void testAndroidModuleDependsOnNdkModule() {
    AndroidModuleModelBuilder root = new AndroidModuleModelBuilder(
      ":",
      "debug",
      new AndroidProjectBuilder()
        .withAndroidModuleDependencyList((it, variant) -> ImmutableList.of(new AndroidModuleDependency(":lib", variant)))
    );
    AndroidModuleModelBuilder lib = new AndroidModuleModelBuilder(
      ":lib",
      "debug",
      new AndroidProjectBuilder()
        .withNdkModel(AndroidGradleTestUtilsKt::buildNdkModelStub)
    );

    setupProject(root, lib);

    String variantToSelect = "release";

    expectAndSimulateSync(
      () -> {
        myBuildVariantUpdater.updateSelectedBuildVariant(getProject(), module(":").getName(), variantToSelect);
        assertThat(selectedVariant(":")).isEqualTo(variantToSelect);
        assertThat(selectedAbi(":lib")).isEqualTo("x86_64");
      },
      root.withSelectedBuildVariant(variantToSelect),
      lib.withSelectedBuildVariant(variantToSelect)
    );

    assertThat(androidModuleModel(":").getSelectedVariantName()).isEqualTo(variantToSelect);
    assertThat(androidModuleModel(":lib").getSelectedVariantName()).isEqualTo(variantToSelect);
    assertThat(safeGetAbi(ndkFacet(":lib").getSelectedVariantAbi())).isEqualTo("x86_64");
    assertChangeHappened();
  }

  // app module depends on library module.
  // Initial variant/ABI selection:
  //    app: debug-x86_64      (native module)
  //    library: debug-x86_64  (native module)
  // The user is changing the build variant of app to "release".
  //
  // Target variants/ABIs have already been synced, and are served from cache.
  //
  // Expected final Variant/ABI selection:
  //    app: release-x86_64
  //    library: release-x86_64
  public void testNdkModuleDependsOnNdkModule() {
    AndroidModuleModelBuilder root = new AndroidModuleModelBuilder(
      ":",
      "debug",
      new AndroidProjectBuilder()
        .withNdkModel(AndroidGradleTestUtilsKt::buildNdkModelStub)
        .withAndroidModuleDependencyList((it, variant) -> ImmutableList.of(new AndroidModuleDependency(":lib", variant)))
    );
    AndroidModuleModelBuilder lib = new AndroidModuleModelBuilder(
      ":lib",
      "debug",
      new AndroidProjectBuilder()
        .withNdkModel(AndroidGradleTestUtilsKt::buildNdkModelStub)
    );

    setupProject(root, lib);

    String variantToSelect = "release";

    expectAndSimulateSync(
      () -> {
        myBuildVariantUpdater.updateSelectedBuildVariant(getProject(), module(":").getName(), variantToSelect);
        assertThat(selectedVariant(":")).isEqualTo(variantToSelect);
        assertThat(selectedAbi(":")).isEqualTo("x86_64");
      },
      root.withSelectedBuildVariant(variantToSelect),
      lib.withSelectedBuildVariant(variantToSelect)
    );

    assertThat(androidModuleModel(":").getSelectedVariantName()).isEqualTo(variantToSelect);
    assertThat(safeGetAbi(ndkFacet(":").getSelectedVariantAbi())).isEqualTo("x86_64");
    assertThat(androidModuleModel(":lib").getSelectedVariantName()).isEqualTo(variantToSelect);
    assertThat(safeGetAbi(ndkFacet(":lib").getSelectedVariantAbi())).isEqualTo("x86_64");
    assertChangeHappened();
  }

  // app module depends on library module.
  // Initial variant/ABI selection:
  //    app: debug-x86_64      (native module)
  //    library: debug-x86_64  (native module)
  // The user is changing the ABI of app to "armeabi-v7a".
  //
  // Target variants/ABIs have already been synced, and are served from cache.
  //
  // Expected final Variant/ABI selection:
  //    app: debug-armeabi-v7a
  //    library: debug-armeabi-v7a
  public void testNdkModuleDependsOnNdkModuleWithAbiChange() {
    AndroidModuleModelBuilder root = new AndroidModuleModelBuilder(
      ":",
      "debug",
      new AndroidProjectBuilder()
        .withNdkModel(AndroidGradleTestUtilsKt::buildNdkModelStub)
        .withAndroidModuleDependencyList((it, variant) -> ImmutableList.of(new AndroidModuleDependency(":lib", variant)))
    );
    AndroidModuleModelBuilder lib = new AndroidModuleModelBuilder(
      ":lib",
      "debug",
      new AndroidProjectBuilder()
        .withNdkModel(AndroidGradleTestUtilsKt::buildNdkModelStub)
    );

    setupProject(root, lib);

    String variantToSelect = "debug";
    String abiToSelect = "arm64-v8a";

    expectAndSimulateSync(
      () -> {
        myBuildVariantUpdater.updateSelectedAbi(getProject(), module(":").getName(), abiToSelect);
        assertThat(selectedVariant(":")).isEqualTo(variantToSelect);
        assertThat(selectedAbi(":")).isEqualTo(abiToSelect);
      },
      root.withSelectedBuildVariant(variantToSelect).withSelectedAbi(abiToSelect),
      lib.withSelectedBuildVariant(variantToSelect).withSelectedAbi(abiToSelect)
    );

    assertThat(androidModuleModel(":").getSelectedVariantName()).isEqualTo(variantToSelect);
    assertThat(safeGetAbi(ndkFacet(":").getSelectedVariantAbi())).isEqualTo(abiToSelect);
    assertThat(androidModuleModel(":lib").getSelectedVariantName()).isEqualTo(variantToSelect);
    assertThat(safeGetAbi(ndkFacet(":lib").getSelectedVariantAbi())).isEqualTo(abiToSelect);
    assertChangeHappened();
  }

  @NotNull
  private AndroidFacet androidFacet(@NotNull String gradlePath) {
    Module module = module(gradlePath);
    AndroidFacet androidFacet = AndroidFacet.getInstance(module);
    if (androidFacet == null) {
      throw new AssertionError(String.format("Module '%s' (Gradle path: '%s') is not an Android module", module.getName(), gradlePath));
    }
    return androidFacet;
  }

  @NotNull
  private String selectedVariant(@NotNull String gradlePath) {
    return androidFacet(gradlePath).getProperties().SELECTED_BUILD_VARIANT;
  }

  @Nullable
  private String selectedAbi(@NotNull String gradlePath) {
    Module module = module(gradlePath);
    NdkFacet ndkFacet = NdkFacet.getInstance(module);
    if (ndkFacet == null) return null;
    return safeGetAbi(ndkFacet.getSelectedVariantAbi());
  }

  @NotNull
  private AndroidModuleModel androidModuleModel(@NotNull String gradlePath) {
    Module module = module(gradlePath);
    AndroidModuleModel androidModuleModel = AndroidModuleModel.get(module);
    if (androidModuleModel == null) {
      throw new AssertionError(String.format("Module '%s' (Gradle path: '%s') is not an Android module", module.getName(), gradlePath));
    }
    return androidModuleModel;
  }

  @NotNull
  private NdkModuleModel ndkModuleModel(@NotNull String gradlePath) {
    Module module = module(gradlePath);
    NdkModuleModel ndkModuleModel = NdkModuleModel.get(module);
    if (ndkModuleModel == null) {
      throw new AssertionError(String.format("Module '%s' (Gradle path: '%s') is not a native module", module.getName(), gradlePath));
    }
    return ndkModuleModel;
  }

  @NotNull
  private NdkFacet ndkFacet(@NotNull String gradlePath) {
    Module module = module(gradlePath);
    NdkFacet ndkFacet = NdkFacet.getInstance(module);
    if (ndkFacet == null) {
      throw new AssertionError(String.format("Module '%s' (Gradle path: '%s') is not a native module", module.getName(), gradlePath));
    }
    return ndkFacet;
  }

  @NotNull
  private String safeGetAbi(@Nullable VariantAbi abi) {
    if (abi == null) return "";
    return abi.getAbi();
  }

  @NotNull
  private Module module(@NotNull String gradlePath) {
    Module module = gradleModule(getProject(), gradlePath);
    if (module == null) throw new AssertionError(String.format("No module corresponds to Gradle path '%s'", gradlePath));
    return module;
  }

  private void reset() {
    mySelectionChangeCounter = 0;
  }

  private void assertChangeHappened() {
    assertThat(mySelectionChangeCounter).isEqualTo(1);
    assertThat(getProjectSystem(getProject()).getSyncManager().getLastSyncResult()).isEqualTo(ProjectSystemSyncManager.SyncResult.SKIPPED);
  }
}
