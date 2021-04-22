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
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.util.ThreeState.YES;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.model.VariantAbi;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult;
import com.android.tools.idea.testing.AndroidGradleTestUtilsKt;
import com.android.tools.idea.testing.AndroidModuleDependency;
import com.android.tools.idea.testing.AndroidModuleModelBuilder;
import com.android.tools.idea.testing.AndroidProjectBuilder;
import com.android.tools.idea.testing.IdeComponents;
import com.google.common.collect.ImmutableList;
import com.google.wireless.android.sdk.stats.GradleSyncStats;
import com.intellij.openapi.module.Module;
import java.io.File;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Tests for {@link BuildVariantUpdater}.
 */
public class BuildVariantUpdaterTest extends AndroidTestCase {
  private int mySelectionChangeCounter = 0;
  private final BuildVariantView.BuildVariantSelectionChangeListener myVariantSelectionChangeListener =
    new BuildVariantView.BuildVariantSelectionChangeListener() {
      @Override
      public void selectionChanged() {
        mySelectionChangeCounter++;
      }
    };

  private void setupProject(AndroidModuleModelBuilder... modelBuilders) {
    setupTestProjectFromAndroidModel(
      getProject(),
      new File(getProject().getBasePath()),
      true,
      modelBuilders
    );
  }

  public void testUpdateSelectedVariant() {
    setupProject(
      new AndroidModuleModelBuilder(
        ":",
        "debug",
        new AndroidProjectBuilder()
      )
    );
    String variantToSelect = "release";

    BuildVariantUpdater buildVariantUpdater = BuildVariantUpdater.getInstance(getProject());
    buildVariantUpdater.addSelectionChangeListener(myVariantSelectionChangeListener);
    buildVariantUpdater.updateSelectedBuildVariant(getProject(), module(":").getName(), variantToSelect);

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
    setupProject(
      new AndroidModuleModelBuilder(
        ":",
        "debug",
        new AndroidProjectBuilder().withNdkModel(AndroidGradleTestUtilsKt::buildNdkModelStub)
      )
    );

    String variantToSelect = "release";

    BuildVariantUpdater buildVariantUpdater = BuildVariantUpdater.getInstance(getProject());
    buildVariantUpdater.addSelectionChangeListener(myVariantSelectionChangeListener);
    buildVariantUpdater.updateSelectedBuildVariant(getProject(), module(":").getName(), variantToSelect);

    assertThat(androidModuleModel(":").getSelectedVariantName()).isEqualTo(variantToSelect);
    assertThat(safeGetAbi(ndkFacet(":").getSelectedVariantAbi())).isEqualTo("x86_64");
    assertThat(ndkModuleModel(":").getSelectedAbi()).isEqualTo("x86_64");
    assertChangeHappened();
  }

  private void assertChangeHappened() {
    // TODO(b/184824343): Re-enable when fixed.
    //      assertThat(mySelectionChangeCounter).isEqualTo(1);
    // TODO(b/184824343): Remove when fixed.
    assertThat(getProjectSystem(getProject()).getSyncManager().getLastSyncResult()).isEqualTo(SyncResult.SKIPPED);
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
    setupProject(
      new AndroidModuleModelBuilder(
        ":",
        "debug",
        new AndroidProjectBuilder().withNdkModel(AndroidGradleTestUtilsKt::buildNdkModelStub)
      )
    );

    VariantAbi variantAbiToSelect = new VariantAbi("debug", "arm64-v8a");

    BuildVariantUpdater buildVariantUpdater = BuildVariantUpdater.getInstance(getProject());
    buildVariantUpdater.addSelectionChangeListener(myVariantSelectionChangeListener);
    buildVariantUpdater.updateSelectedAbi(getProject(), module(":").getName(), variantAbiToSelect.getAbi());

    assertThat(androidModuleModel(":").getSelectedVariantName()).isEqualTo(variantAbiToSelect.getVariant());
    assertThat(safeGetAbi(ndkFacet(":").getSelectedVariantAbi())).isEqualTo(variantAbiToSelect.getAbi());
    //TODO(b/182456574): assertThat(ndkModuleModel(":").getSelectedAbi()).isEqualTo(variantAbiToSelect.getAbi());
    assertChangeHappened();
  }

  public void testUpdateSelectedVariantWithUnchangedVariantName() {
    setupProject(
      new AndroidModuleModelBuilder(
        ":",
        "debug",
        new AndroidProjectBuilder()
      )
    );
    String variantToSelect = "debug"; // The default selected variant after test setup is "debug".

    BuildVariantUpdater buildVariantUpdater = BuildVariantUpdater.getInstance(getProject());
    buildVariantUpdater.addSelectionChangeListener(myVariantSelectionChangeListener);
    buildVariantUpdater.updateSelectedBuildVariant(getProject(), module(":").getName(), variantToSelect);

    assertThat(androidModuleModel(":").getSelectedVariantName()).isEqualTo(variantToSelect);
    assertThat(mySelectionChangeCounter).isEqualTo(0);
  }

  public void testUpdateSelectedVariantWithChangedBuildFiles() {
    // Simulate build files have changed since last sync.
    GradleSyncState syncState = mock(GradleSyncState.class);
    IdeComponents ideComponents = new IdeComponents(getProject());
    ideComponents.replaceProjectService(GradleSyncState.class, syncState);
    when(syncState.isSyncNeeded()).thenReturn(YES);
    GradleSyncInvoker syncInvoker = ideComponents.mockApplicationService(GradleSyncInvoker.class);
    doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) {
        Object[] args = invocation.getArguments();
        GradleSyncListener syncListener = (GradleSyncListener)args[2];
        syncListener.syncSucceeded(getProject());
        return null;
      }
    }).when(syncInvoker).requestProjectSync(eq(getProject()), any(GradleSyncStats.Trigger.class), any());

    setupProject(
      new AndroidModuleModelBuilder(
        ":",
        "debug",
        new AndroidProjectBuilder()
      )
    );

    String variantToSelect = "release";

    BuildVariantUpdater buildVariantUpdater = BuildVariantUpdater.getInstance(getProject());
    buildVariantUpdater.addSelectionChangeListener(myVariantSelectionChangeListener);
    buildVariantUpdater.updateSelectedBuildVariant(getProject(), module(":").getName(), variantToSelect);

    assertThat(androidModuleModel(":").getSelectedVariantName()).isEqualTo(variantToSelect);
    // TODO(b/184824343): assertThat(mySelectionChangeCounter).isEqualTo(1);
    verify(syncInvoker).requestProjectSync(eq(getProject()), any(GradleSyncStats.Trigger.class), any());
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
    setupProject(
      new AndroidModuleModelBuilder(
        ":",
        "debug",
        new AndroidProjectBuilder()
          .withAndroidModuleDependencyList((it, variant) -> ImmutableList.of(new AndroidModuleDependency(":lib", variant)))
      ),
      new AndroidModuleModelBuilder(
        ":lib",
        "debug",
        new AndroidProjectBuilder()
      )
    );

    String variantToSelect = "release";

    BuildVariantUpdater buildVariantUpdater = BuildVariantUpdater.getInstance(getProject());
    buildVariantUpdater.addSelectionChangeListener(myVariantSelectionChangeListener);
    buildVariantUpdater.updateSelectedBuildVariant(getProject(), module(":").getName(), variantToSelect);

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
    setupProject(
      new AndroidModuleModelBuilder(
        ":",
        "debug",
        new AndroidProjectBuilder()
          .withNdkModel(AndroidGradleTestUtilsKt::buildNdkModelStub)
          .withAndroidModuleDependencyList((it, variant) -> ImmutableList.of(new AndroidModuleDependency(":lib", variant)))
      ),
      new AndroidModuleModelBuilder(
        ":lib",
        "debug",
        new AndroidProjectBuilder()
      )
    );

    String variantToSelect = "release";

    BuildVariantUpdater buildVariantUpdater = BuildVariantUpdater.getInstance(getProject());
    buildVariantUpdater.addSelectionChangeListener(myVariantSelectionChangeListener);
    buildVariantUpdater.updateSelectedBuildVariant(getProject(), module(":").getName(), variantToSelect);

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
    setupProject(
      new AndroidModuleModelBuilder(
        ":",
        "debug",
        new AndroidProjectBuilder()
          .withAndroidModuleDependencyList((it, variant) -> ImmutableList.of(new AndroidModuleDependency(":lib", variant)))
      ),
      new AndroidModuleModelBuilder(
        ":lib",
        "debug",
        new AndroidProjectBuilder()
          .withNdkModel(AndroidGradleTestUtilsKt::buildNdkModelStub)
      )
    );

    String variantToSelect = "release";

    BuildVariantUpdater buildVariantUpdater = BuildVariantUpdater.getInstance(getProject());
    buildVariantUpdater.addSelectionChangeListener(myVariantSelectionChangeListener);
    buildVariantUpdater.updateSelectedBuildVariant(getProject(), module(":").getName(), variantToSelect);

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
    setupProject(
      new AndroidModuleModelBuilder(
        ":",
        "debug",
        new AndroidProjectBuilder()
          .withNdkModel(AndroidGradleTestUtilsKt::buildNdkModelStub)
          .withAndroidModuleDependencyList((it, variant) -> ImmutableList.of(new AndroidModuleDependency(":lib", variant)))
      ),
      new AndroidModuleModelBuilder(
        ":lib",
        "debug",
        new AndroidProjectBuilder()
          .withNdkModel(AndroidGradleTestUtilsKt::buildNdkModelStub)
      )
    );

    String variantToSelect = "release";

    BuildVariantUpdater buildVariantUpdater = BuildVariantUpdater.getInstance(getProject());
    buildVariantUpdater.addSelectionChangeListener(myVariantSelectionChangeListener);
    buildVariantUpdater.updateSelectedBuildVariant(getProject(), module(":").getName(), variantToSelect);

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
    setupProject(
      new AndroidModuleModelBuilder(
        ":",
        "debug",
        new AndroidProjectBuilder()
          .withNdkModel(AndroidGradleTestUtilsKt::buildNdkModelStub)
          .withAndroidModuleDependencyList((it, variant) -> ImmutableList.of(new AndroidModuleDependency(":lib", variant)))
      ),
      new AndroidModuleModelBuilder(
        ":lib",
        "debug",
        new AndroidProjectBuilder()
          .withNdkModel(AndroidGradleTestUtilsKt::buildNdkModelStub)
      )
    );

    String variantToSelect = "debug";
    String abiToSelect = "arm64-v8a";

    BuildVariantUpdater buildVariantUpdater = BuildVariantUpdater.getInstance(getProject());
    buildVariantUpdater.addSelectionChangeListener(myVariantSelectionChangeListener);
    buildVariantUpdater.updateSelectedAbi(getProject(), module(":").getName(), abiToSelect);

    assertThat(androidModuleModel(":").getSelectedVariantName()).isEqualTo(variantToSelect);
    assertThat(safeGetAbi(ndkFacet(":").getSelectedVariantAbi())).isEqualTo(abiToSelect);
    assertThat(androidModuleModel(":lib").getSelectedVariantName()).isEqualTo(variantToSelect);
    assertThat(safeGetAbi(ndkFacet(":lib").getSelectedVariantAbi())).isEqualTo(abiToSelect);
    assertChangeHappened();
  }

  @NotNull
  private AndroidModuleModel androidModuleModel(@NotNull String gradlePath) {
    Module module = module(gradlePath);
    AndroidModuleModel androidModuleModel = AndroidModuleModel.get(module);
    if (androidModuleModel == null) {
      throw new AssertionError(String.format("Module '%s' (Gradle path: '%s') is ot an Android module", module.getName(), gradlePath));
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
}
