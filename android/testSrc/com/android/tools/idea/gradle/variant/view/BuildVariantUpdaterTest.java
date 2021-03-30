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

import static com.android.tools.idea.testing.Facets.createAndAddAndroidFacet;
import static com.android.tools.idea.testing.Facets.createAndAddNdkFacet;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.util.ThreeState.YES;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.ide.common.gradle.model.IdeDependencies;
import com.android.ide.common.gradle.model.IdeModuleLibrary;
import com.android.ide.common.gradle.model.IdeVariant;
import com.android.tools.idea.gradle.project.ProjectStructure;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.model.VariantAbi;
import com.android.tools.idea.gradle.project.sync.GradleFiles;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.testing.IdeComponents;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.wireless.android.sdk.stats.GradleSyncStats;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestCase;
import java.util.Collections;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Tests for {@link BuildVariantUpdater}.
 */
public class BuildVariantUpdaterTest extends PlatformTestCase {
  @Mock private IdeModifiableModelsProvider myModifiableModelsProvider;
  @Mock private AndroidModuleModel myAndroidModel;
  @Mock private NdkModuleModel myNdkModel;
  @Mock private IdeAndroidProject myAndroidProject;
  @Mock private IdeDependencies myIdeDependencies;
  @Mock private IdeVariant myDebugVariant;
  @Mock private ModuleSetupContext.Factory myModuleSetupContextFactory;
  @Mock private ModuleSetupContext myModuleSetupContext;
  @Mock private BuildVariantView.BuildVariantSelectionChangeListener myVariantSelectionChangeListener;
  @Mock private GradleFiles myGradleFiles;

  private BuildVariantUpdater myVariantUpdater;
  private List<IdeModuleLibrary> myModuleDependencies = Lists.newArrayList();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    AndroidFacet androidFacet = createAndAddAndroidFacet(getModule());
    AndroidModel.set(androidFacet, myAndroidModel);

    Project project = getProject();

    when(myDebugVariant.getName()).thenReturn("debug");

    when(myAndroidModel.getSelectedMainCompileLevel2Dependencies()).thenReturn(myIdeDependencies);
    when(myAndroidModel.getAndroidProject()).thenReturn(myAndroidProject);
    when(myAndroidProject.getDynamicFeatures()).thenReturn(Collections.emptyList());
    when(myIdeDependencies.getModuleDependencies()).thenReturn(myModuleDependencies);

    IdeComponents ideComponents = new IdeComponents(project);
    // Replace the GradleFiles service so no hashes are updated as this can cause a NPE since the mocked models don't return anything
    ideComponents.replaceProjectService(GradleFiles.class, myGradleFiles);

    myVariantUpdater = new BuildVariantUpdater();
    myVariantUpdater.addSelectionChangeListener(myVariantSelectionChangeListener);

    when(myNdkModel.getAllVariantAbis()).thenReturn(ImmutableList.of(
      new VariantAbi("debug", "armeabi-v7a"),
      new VariantAbi("debug", "x86"),
      new VariantAbi("release", "armeabi-v7a"),
      new VariantAbi("release", "x86")
    ));
  }

  public void testUpdateSelectedVariant() {
    String variantToSelect = "release";
    when(myAndroidModel.getSelectedVariant()).thenReturn(myDebugVariant);
    when(myAndroidModel.variantExists(variantToSelect)).thenReturn(true);
    when(myModuleSetupContextFactory.create(myModule, myModifiableModelsProvider)).thenReturn(myModuleSetupContext);

    myVariantUpdater.updateSelectedBuildVariant(myProject, myModule.getName(), variantToSelect);

    verify(myAndroidModel).setSelectedVariantName(variantToSelect);
    verify(myVariantSelectionChangeListener).selectionChanged();
  }

  // Initial variant/ABI selection:
  //    app: debug-x86      (native module)
  // The user is changing the build variant of app to "release".
  //
  // Target variants/ABIs have already been synced, and are served from cache.
  //
  // Expected final Variant/ABI selection:
  //    app: release-x86
  public void testUpdateSelectedVariantWithNdkModule() {
    VariantAbi variantAbi = new VariantAbi("debug", "x86");

    // setup ndk facet and NdkModuleModel.
    NdkFacet ndkFacet = createAndAddNdkFacet(myModule);
    ndkFacet.setNdkModuleModel(myNdkModel);
    ndkFacet.setSelectedVariantAbi(variantAbi);

    VariantAbi variantAbiToSelect = new VariantAbi("release", "x86");

    when(myNdkModel.getSyncedVariantAbis()).thenReturn(ImmutableList.of(variantAbi, variantAbiToSelect));

    when(myAndroidModel.getSelectedVariant()).thenReturn(myDebugVariant);
    when(myAndroidModel.variantExists(variantAbiToSelect.getVariant())).thenReturn(true);

    when(myModuleSetupContextFactory.create(myModule, myModifiableModelsProvider)).thenReturn(myModuleSetupContext);

    // invoke method to test.
    myVariantUpdater.updateSelectedBuildVariant(myProject, myModule.getName(), variantAbiToSelect.getVariant());

    verify(myVariantSelectionChangeListener).selectionChanged();
  }


  // Initial variant/ABI selection:
  //    app: debug-x86      (native module)
  // The user is changing the ABI of app to "armeabi-v7a".
  //
  // Target variants/ABIs have already been synced, and are served from cache.
  //
  // Expected final Variant/ABI selection:
  //    app: debug-armeabi-v7a
  public void testUpdateSelectedAbiWithNdkModule() {
    VariantAbi variantAbi = new VariantAbi("debug", "x86");

    // setup ndk facet and NdkModuleModel.
    NdkFacet ndkFacet = createAndAddNdkFacet(myModule);
    ndkFacet.setNdkModuleModel(myNdkModel);
    ndkFacet.setSelectedVariantAbi(variantAbi);

    VariantAbi variantAbiToSelect = new VariantAbi("debug", "armeabi-v7a");

    when(myNdkModel.getSyncedVariantAbis()).thenReturn(ImmutableList.of(variantAbi, variantAbiToSelect));

    when(myAndroidModel.getSelectedVariant()).thenReturn(myDebugVariant);
    when(myAndroidModel.variantExists(variantAbiToSelect.getVariant())).thenReturn(true);

    when(myModuleSetupContextFactory.create(myModule, myModifiableModelsProvider)).thenReturn(myModuleSetupContext);

    // invoke method to test.
    myVariantUpdater.updateSelectedAbi(myProject, myModule.getName(), variantAbiToSelect.getAbi());

    verify(myVariantSelectionChangeListener).selectionChanged();
  }

  public void testUpdateSelectedVariantWithUnchangedVariantName() {
    String variantToSelect = "debug"; // The default selected variant after test setup is "debug".
    when(myAndroidModel.getSelectedVariant()).thenReturn(myDebugVariant);
    when(myAndroidModel.variantExists(variantToSelect)).thenReturn(true);

    myVariantUpdater.updateSelectedBuildVariant(myProject, myModule.getName(), variantToSelect);

    verify(myAndroidModel, never()).setSelectedVariantName(variantToSelect);
    verify(myVariantSelectionChangeListener, never()).selectionChanged();
  }

  public void testUpdateSelectedVariantWithChangedBuildFiles() {
    // Simulate build files have changed since last sync.
    GradleSyncState syncState = mock(GradleSyncState.class);
    new IdeComponents(myProject).replaceProjectService(GradleSyncState.class, syncState);
    when(syncState.isSyncNeeded()).thenReturn(YES);
    IdeComponents ideComponents = new IdeComponents(myProject);
    GradleSyncInvoker syncInvoker = ideComponents.mockApplicationService(GradleSyncInvoker.class);
    doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) {
        Object[] args = invocation.getArguments();
        GradleSyncListener syncListener = (GradleSyncListener)args[2];
        syncListener.syncSucceeded(myProject);
        return null;
      }
    }).when(syncInvoker).requestProjectSync(eq(myProject), any(GradleSyncStats.Trigger.class), any());

    String variantToSelect = "release";
    when(myAndroidModel.getSelectedVariant()).thenReturn(myDebugVariant);
    when(myAndroidModel.variantExists(variantToSelect)).thenReturn(true);

    myVariantUpdater.updateSelectedBuildVariant(myProject, myModule.getName(), variantToSelect);

    verify(myAndroidModel).setSelectedVariantName(variantToSelect);
    verify(syncInvoker).requestProjectSync(eq(myProject), any(GradleSyncStats.Trigger.class), any());
    verify(myVariantSelectionChangeListener).selectionChanged();
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
    String libraryVariant = "debug";

    // Setup app.

    // Setup expectations for app.
    when(myAndroidModel.getSelectedVariant()).thenReturn(myDebugVariant);
    when(myModuleSetupContextFactory.create(myModule, myModifiableModelsProvider)).thenReturn(myModuleSetupContext);

    // Mock objects required by library.
    AndroidModuleModel libraryAndroidModel = mock(AndroidModuleModel.class);
    IdeVariant libraryDebugVariant = mock(IdeVariant.class);
    IdeDependencies libraryIdeDependencies = mock(IdeDependencies.class);
    IdeAndroidProject libraryAndroidProject = mock(IdeAndroidProject.class);
    ModuleSetupContext libraryModuleSetupContext = mock(ModuleSetupContext.class);
    IdeModuleLibrary moduleLibrary = mock(IdeModuleLibrary.class);

    // Create library module with Android facet.
    Module libraryModule = createModule("library");
    AndroidFacet libraryAndroidFacet = createAndAddAndroidFacet(libraryModule);
    AndroidModel.set(libraryAndroidFacet, libraryAndroidModel);

    // Setup library.

    // Setup expectations for library.
    when(libraryDebugVariant.getName()).thenReturn(libraryVariant);
    when(libraryAndroidModel.getSelectedVariant()).thenReturn(libraryDebugVariant);
    when(libraryIdeDependencies.getModuleDependencies()).thenReturn(Collections.emptyList());
    when(libraryAndroidProject.getDynamicFeatures()).thenReturn(Collections.emptyList());
    when(libraryAndroidModel.getAndroidProject()).thenReturn(libraryAndroidProject);
    when(libraryAndroidModel.getSelectedMainCompileLevel2Dependencies()).thenReturn(libraryIdeDependencies);
    when(myModuleSetupContextFactory.create(libraryModule, myModifiableModelsProvider)).thenReturn(libraryModuleSetupContext);

    // Register "library" into gradle so that "app:release" depends on "library:release".
    when(moduleLibrary.getVariant()).thenReturn("release");
    when(moduleLibrary.getProjectPath()).thenReturn(":library");
    myModuleDependencies.add(moduleLibrary);
    when(myAndroidModel.getSelectedMainCompileLevel2Dependencies()).thenReturn(myIdeDependencies);
    ProjectStructure.getInstance(myProject).getModuleFinder().addModule(libraryModule, ":library");

    // Selected variants (and ABI) for NDK and non-NDK modules.
    String variantToSelect = "release";

    // Selected variant related expectations.
    when(myAndroidModel.variantExists(variantToSelect)).thenReturn(true);
    when(libraryAndroidModel.variantExists(variantToSelect)).thenReturn(true);

    // Invoke method to test.
    myVariantUpdater.updateSelectedBuildVariant(myProject, myModule.getName(), variantToSelect);

    // Verify that variants are selected as expected.
    verify(myAndroidModel).setSelectedVariantName(variantToSelect);
    verify(libraryAndroidModel).setSelectedVariantName(variantToSelect);

    verify(myVariantSelectionChangeListener).selectionChanged();
  }

  // app module depends on library module.
  // Initial variant/ABI selection:
  //    app: debug-x86  (native module)
  //    library: debug  (non-native module)
  // The user is changing the variant of app to "release".
  //
  // Target variants/ABIs have already been synced, and are served from cache.
  //
  // Expected final Variant/ABI selection:
  //    app: release-x86
  //    library: release
  public void testNdkModuleDependsOnAndroidModule() {
    VariantAbi variantAbi = new VariantAbi("debug", "x86");
    String libraryVariant = "debug";

    // Register NDK facet for app.
    NdkFacet ndkFacet = createAndAddNdkFacet(myModule);
    ndkFacet.setNdkModuleModel(myNdkModel);
    ndkFacet.setSelectedVariantAbi(variantAbi);

    // Setup expectations for app.
    when(myAndroidModel.getSelectedVariant()).thenReturn(myDebugVariant);
    when(myModuleSetupContextFactory.create(myModule, myModifiableModelsProvider)).thenReturn(myModuleSetupContext);

    // Mock objects required by library.
    AndroidModuleModel libraryAndroidModel = mock(AndroidModuleModel.class);
    IdeVariant libraryDebugVariant = mock(IdeVariant.class);
    IdeDependencies libraryIdeDependencies = mock(IdeDependencies.class);
    IdeAndroidProject libraryAndroidProject = mock(IdeAndroidProject.class);
    ModuleSetupContext libraryModuleSetupContext = mock(ModuleSetupContext.class);
    IdeModuleLibrary moduleLibrary = mock(IdeModuleLibrary.class);

    // Create library module with Android facet.
    Module libraryModule = createModule("library");
    AndroidFacet libraryAndroidFacet = createAndAddAndroidFacet(libraryModule);
    AndroidModel.set(libraryAndroidFacet, libraryAndroidModel);

    // Setup library.

    // Setup expectations for library.
    when(libraryDebugVariant.getName()).thenReturn(libraryVariant);
    when(libraryAndroidModel.getSelectedVariant()).thenReturn(libraryDebugVariant);
    when(libraryIdeDependencies.getModuleDependencies()).thenReturn(Collections.emptyList());
    when(libraryAndroidProject.getDynamicFeatures()).thenReturn(Collections.emptyList());
    when(libraryAndroidModel.getAndroidProject()).thenReturn(libraryAndroidProject);
    when(libraryAndroidModel.getSelectedMainCompileLevel2Dependencies()).thenReturn(libraryIdeDependencies);
    when(myModuleSetupContextFactory.create(libraryModule, myModifiableModelsProvider)).thenReturn(libraryModuleSetupContext);

    // Register "library" into gradle so that "app:release" depends on "library:release".
    when(moduleLibrary.getVariant()).thenReturn("release");
    when(moduleLibrary.getProjectPath()).thenReturn(":library");
    myModuleDependencies.add(moduleLibrary);
    when(myAndroidModel.getSelectedMainCompileLevel2Dependencies()).thenReturn(myIdeDependencies);
    ProjectStructure.getInstance(myProject).getModuleFinder().addModule(libraryModule, ":library");

    // Selected variants (and ABI) for NDK and non-NDK modules.
    VariantAbi variantAbiToSelect = new VariantAbi("release", "x86");

    // Selected variant related expectations.
    when(myAndroidModel.variantExists(variantAbiToSelect.getVariant())).thenReturn(true);
    when(libraryAndroidModel.variantExists(variantAbiToSelect.getVariant())).thenReturn(true);
    when(myNdkModel.getSyncedVariantAbis()).thenReturn(ImmutableList.of(variantAbi, variantAbiToSelect));

    // Invoke method to test.
    myVariantUpdater.updateSelectedBuildVariant(myProject, myModule.getName(), variantAbiToSelect.getVariant());

    // Verify that variants are selected as expected.
    verify(myAndroidModel).setSelectedVariantName(variantAbiToSelect.getVariant());
    assertThat(ndkFacet.getSelectedVariantAbi()).isEqualTo(variantAbiToSelect);
    verify(libraryAndroidModel).setSelectedVariantName(variantAbiToSelect.getVariant());

    verify(myVariantSelectionChangeListener).selectionChanged();
  }

  // app module depends on library module.
  // Initial variant/ABI selection:
  //    app: debug          (non-native module)
  //    library: debug-x86  (native module)
  // The user is changing the variant of app to "release".
  //
  // Target variants/ABIs have already been synced, and are served from cache.
  //
  // Expected final Variant/ABI selection:
  //    app: release
  //    library: release-x86
  public void testAndroidModuleDependsOnNdkModule() {
    VariantAbi libraryVariantAbi = new VariantAbi("debug", "x86");

    // Setup expectations for app.
    when(myAndroidModel.getSelectedVariant()).thenReturn(myDebugVariant);
    when(myModuleSetupContextFactory.create(myModule, myModifiableModelsProvider)).thenReturn(myModuleSetupContext);

    // Mock objects required by library.
    AndroidModuleModel libraryAndroidModel = mock(AndroidModuleModel.class);
    IdeVariant libraryDebugVariant = mock(IdeVariant.class);
    IdeDependencies libraryIdeDependencies = mock(IdeDependencies.class);
    IdeAndroidProject libraryAndroidProject = mock(IdeAndroidProject.class);
    ModuleSetupContext libraryModuleSetupContext = mock(ModuleSetupContext.class);
    IdeModuleLibrary moduleLibrary = mock(IdeModuleLibrary.class);
    NdkModuleModel libraryNdkModel = mock(NdkModuleModel.class);

    // Create library module with Android facet.
    Module libraryModule = createModule("library");
    AndroidFacet libraryAndroidFacet = createAndAddAndroidFacet(libraryModule);
    AndroidModel.set(libraryAndroidFacet, libraryAndroidModel);

    // Register NDK facet for library.
    NdkFacet ndkFacet = createAndAddNdkFacet(libraryModule);
    ndkFacet.setNdkModuleModel(libraryNdkModel);
    ndkFacet.setSelectedVariantAbi(libraryVariantAbi);

    // Setup expectations for library.
    when(libraryDebugVariant.getName()).thenReturn(libraryVariantAbi.getVariant());
    when(libraryAndroidModel.getSelectedVariant()).thenReturn(libraryDebugVariant);
    when(libraryIdeDependencies.getModuleDependencies()).thenReturn(Collections.emptyList());
    when(libraryAndroidProject.getDynamicFeatures()).thenReturn(Collections.emptyList());
    when(libraryAndroidModel.getAndroidProject()).thenReturn(libraryAndroidProject);
    when(libraryAndroidModel.getSelectedMainCompileLevel2Dependencies()).thenReturn(libraryIdeDependencies);
    when(myModuleSetupContextFactory.create(libraryModule, myModifiableModelsProvider)).thenReturn(libraryModuleSetupContext);

    // Register "library" into gradle so that "app:release" depends on "library:release".
    when(moduleLibrary.getVariant()).thenReturn("release");
    when(moduleLibrary.getProjectPath()).thenReturn(":library");
    myModuleDependencies.add(moduleLibrary);
    when(myAndroidModel.getSelectedMainCompileLevel2Dependencies()).thenReturn(myIdeDependencies);
    ProjectStructure.getInstance(myProject).getModuleFinder().addModule(libraryModule, ":library");

    // Selected variants (and ABI) for NDK and non-NDK modules.
    VariantAbi variantAbiToSelect = new VariantAbi("release", "x86");

    // Selected variant related expectations.
    when(myAndroidModel.variantExists(variantAbiToSelect.getVariant())).thenReturn(true);
    when(libraryAndroidModel.variantExists(variantAbiToSelect.getVariant())).thenReturn(true);
    when(libraryNdkModel.getAllVariantAbis()).thenReturn(ImmutableList.of(libraryVariantAbi, variantAbiToSelect));
    when(libraryNdkModel.getSyncedVariantAbis()).thenReturn(ImmutableList.of(libraryVariantAbi, variantAbiToSelect));

    // Invoke method to test.
    myVariantUpdater.updateSelectedBuildVariant(myProject, myModule.getName(), variantAbiToSelect.getVariant());

    // Verify that variants are selected as expected.
    verify(myAndroidModel).setSelectedVariantName(variantAbiToSelect.getVariant());
    verify(libraryAndroidModel).setSelectedVariantName(variantAbiToSelect.getVariant());
    assertThat(ndkFacet.getSelectedVariantAbi()).isEqualTo(variantAbiToSelect);

    verify(myVariantSelectionChangeListener).selectionChanged();
  }

  // app module depends on library module.
  // Initial variant/ABI selection:
  //    app: debug-x86      (native module)
  //    library: debug-x86  (native module)
  // The user is changing the build variant of app to "release".
  //
  // Target variants/ABIs have already been synced, and are served from cache.
  //
  // Expected final Variant/ABI selection:
  //    app: release-x86
  //    library: release-x86
  public void testNdkModuleDependsOnNdkModule() {
    VariantAbi variantAbi = new VariantAbi("debug", "x86");

    // Register NDK facet for app.
    NdkFacet ndkFacet = createAndAddNdkFacet(myModule);
    ndkFacet.setNdkModuleModel(myNdkModel);
    ndkFacet.setSelectedVariantAbi(variantAbi);

    // Setup expectations for app.
    when(myAndroidModel.getSelectedVariant()).thenReturn(myDebugVariant);
    when(myModuleSetupContextFactory.create(myModule, myModifiableModelsProvider)).thenReturn(myModuleSetupContext);

    // Setup expectations for app.
    //when(myAndroidModel.getSelectedVariant()).thenReturn(myDebugVariant);
    //when(myModuleSetupContextFactory.create(myModule, myModifiableModelsProvider)).thenReturn(myModuleSetupContext);

    // Mock objects required by library.
    AndroidModuleModel libraryAndroidModel = mock(AndroidModuleModel.class);
    IdeVariant libraryDebugVariant = mock(IdeVariant.class);
    IdeDependencies libraryIdeDependencies = mock(IdeDependencies.class);
    IdeAndroidProject libraryAndroidProject = mock(IdeAndroidProject.class);
    ModuleSetupContext libraryModuleSetupContext = mock(ModuleSetupContext.class);
    IdeModuleLibrary moduleLibrary = mock(IdeModuleLibrary.class);
    NdkModuleModel libraryNdkModel = mock(NdkModuleModel.class);

    // Create library module with Android facet.
    Module libraryModule = createModule("library");
    AndroidFacet libraryAndroidFacet = createAndAddAndroidFacet(libraryModule);
    AndroidModel.set(libraryAndroidFacet, libraryAndroidModel);

    // Register NDK facet for library.
    NdkFacet libraryNdkFacet = createAndAddNdkFacet(libraryModule);
    libraryNdkFacet.setNdkModuleModel(libraryNdkModel);
    libraryNdkFacet.setSelectedVariantAbi(variantAbi);

    // Setup expectations for library.
    when(libraryDebugVariant.getName()).thenReturn(variantAbi.getVariant());
    when(libraryAndroidModel.getSelectedVariant()).thenReturn(libraryDebugVariant);
    when(libraryIdeDependencies.getModuleDependencies()).thenReturn(Collections.emptyList());
    when(libraryAndroidProject.getDynamicFeatures()).thenReturn(Collections.emptyList());
    when(libraryAndroidModel.getAndroidProject()).thenReturn(libraryAndroidProject);
    when(libraryAndroidModel.getSelectedMainCompileLevel2Dependencies()).thenReturn(libraryIdeDependencies);
    when(myModuleSetupContextFactory.create(libraryModule, myModifiableModelsProvider)).thenReturn(libraryModuleSetupContext);

    // Register "library" into gradle so that "app:release" depends on "library:release".
    when(moduleLibrary.getVariant()).thenReturn("release");
    when(moduleLibrary.getProjectPath()).thenReturn(":library");
    myModuleDependencies.add(moduleLibrary);
    when(myAndroidModel.getSelectedMainCompileLevel2Dependencies()).thenReturn(myIdeDependencies);
    ProjectStructure.getInstance(myProject).getModuleFinder().addModule(libraryModule, ":library");

    // Selected variants (and ABI) for NDK and non-NDK modules.
    VariantAbi variantAbiToSelect = new VariantAbi("release", "x86");

    // Selected variant related expectations.
    when(myAndroidModel.variantExists(variantAbiToSelect.getVariant())).thenReturn(true);
    when(myNdkModel.getSyncedVariantAbis()).thenReturn(ImmutableList.of(variantAbi, variantAbiToSelect));
    when(libraryAndroidModel.variantExists(variantAbiToSelect.getVariant())).thenReturn(true);
    when(libraryNdkModel.getAllVariantAbis()).thenReturn(ImmutableList.of(variantAbi, variantAbiToSelect));
    when(libraryNdkModel.getSyncedVariantAbis()).thenReturn(ImmutableList.of(variantAbi, variantAbiToSelect));

    // Invoke method to test.
    myVariantUpdater.updateSelectedBuildVariant(myProject, myModule.getName(), variantAbiToSelect.getVariant());

    // Verify that variants are selected as expected.
    verify(myAndroidModel).setSelectedVariantName(variantAbiToSelect.getVariant());
    assertThat(ndkFacet.getSelectedVariantAbi()).isEqualTo(variantAbiToSelect);
    verify(libraryAndroidModel).setSelectedVariantName(variantAbiToSelect.getVariant());
    assertThat(libraryNdkFacet.getSelectedVariantAbi()).isEqualTo(variantAbiToSelect);

    verify(myVariantSelectionChangeListener).selectionChanged();
  }

  // app module depends on library module.
  // Initial variant/ABI selection:
  //    app: debug-x86      (native module)
  //    library: debug-x86  (native module)
  // The user is changing the ABI of app to "armeabi-v7a".
  //
  // Target variants/ABIs have already been synced, and are served from cache.
  //
  // Expected final Variant/ABI selection:
  //    app: debug-armeabi-v7a
  //    library: debug-armeabi-v7a
  public void testNdkModuleDependsOnNdkModuleWithAbiChange() {
    VariantAbi variantAbi = new VariantAbi("debug", "x86");

    // Register NDK facet for app.
    NdkFacet ndkFacet = createAndAddNdkFacet(myModule);
    ndkFacet.setNdkModuleModel(myNdkModel);
    ndkFacet.setSelectedVariantAbi(variantAbi);

    // Setup expectations for app.
    when(myAndroidModel.getSelectedVariant()).thenReturn(myDebugVariant);
    when(myModuleSetupContextFactory.create(myModule, myModifiableModelsProvider)).thenReturn(myModuleSetupContext);

    // Setup expectations for app.
    //when(myAndroidModel.getSelectedVariant()).thenReturn(myDebugVariant);
    //when(myModuleSetupContextFactory.create(myModule, myModifiableModelsProvider)).thenReturn(myModuleSetupContext);

    // Mock objects required by library.
    AndroidModuleModel libraryAndroidModel = mock(AndroidModuleModel.class);
    IdeVariant libraryDebugVariant = mock(IdeVariant.class);
    IdeDependencies libraryIdeDependencies = mock(IdeDependencies.class);
    IdeAndroidProject libraryAndroidProject = mock(IdeAndroidProject.class);
    ModuleSetupContext libraryModuleSetupContext = mock(ModuleSetupContext.class);
    IdeModuleLibrary moduleLibrary = mock(IdeModuleLibrary.class);
    NdkModuleModel libraryNdkModel = mock(NdkModuleModel.class);

    // Create library module with Android facet.
    Module libraryModule = createModule("library");
    AndroidFacet libraryAndroidFacet = createAndAddAndroidFacet(libraryModule);
    AndroidModel.set(libraryAndroidFacet, libraryAndroidModel);

    // Register NDK facet for library.
    NdkFacet libraryNdkFacet = createAndAddNdkFacet(libraryModule);
    libraryNdkFacet.setNdkModuleModel(libraryNdkModel);
    libraryNdkFacet.setSelectedVariantAbi(variantAbi);

    // Setup expectations for library.
    when(libraryDebugVariant.getName()).thenReturn(variantAbi.getVariant());
    when(libraryAndroidModel.getSelectedVariant()).thenReturn(libraryDebugVariant);
    when(libraryIdeDependencies.getModuleDependencies()).thenReturn(Collections.emptyList());
    when(libraryAndroidProject.getDynamicFeatures()).thenReturn(Collections.emptyList());
    when(libraryAndroidModel.getAndroidProject()).thenReturn(libraryAndroidProject);
    when(libraryAndroidModel.getSelectedMainCompileLevel2Dependencies()).thenReturn(libraryIdeDependencies);
    when(myModuleSetupContextFactory.create(libraryModule, myModifiableModelsProvider)).thenReturn(libraryModuleSetupContext);

    // Register "library" into gradle so that "app:debug" depends on "library:debug".
    when(moduleLibrary.getVariant()).thenReturn("debug");
    when(moduleLibrary.getProjectPath()).thenReturn(":library");
    myModuleDependencies.add(moduleLibrary);
    when(myAndroidModel.getSelectedMainCompileLevel2Dependencies()).thenReturn(myIdeDependencies);
    ProjectStructure.getInstance(myProject).getModuleFinder().addModule(libraryModule, ":library");

    // Selected variants (and ABI) for NDK and non-NDK modules.
    VariantAbi variantAbiToSelect = new VariantAbi("debug", "armeabi-v7a");

    // Selected variant related expectations.
    when(myAndroidModel.variantExists(variantAbiToSelect.getVariant())).thenReturn(true);
    when(myNdkModel.getSyncedVariantAbis()).thenReturn(ImmutableList.of(variantAbi, variantAbiToSelect));
    when(libraryAndroidModel.variantExists(variantAbiToSelect.getVariant())).thenReturn(true);
    when(libraryNdkModel.getAllVariantAbis()).thenReturn(ImmutableList.of(variantAbi, variantAbiToSelect));
    when(libraryNdkModel.getSyncedVariantAbis()).thenReturn(ImmutableList.of(variantAbi, variantAbiToSelect));

    // Invoke method to test.
    myVariantUpdater.updateSelectedAbi(myProject, myModule.getName(), variantAbiToSelect.getAbi());

    // Verify that variants are selected as expected.
    verify(myAndroidModel).setSelectedVariantName(variantAbiToSelect.getVariant());
    assertThat(ndkFacet.getSelectedVariantAbi()).isEqualTo(variantAbiToSelect);
    verify(libraryAndroidModel).setSelectedVariantName(variantAbiToSelect.getVariant());
    assertThat(libraryNdkFacet.getSelectedVariantAbi()).isEqualTo(variantAbiToSelect);

    verify(myVariantSelectionChangeListener).selectionChanged();
  }
}
