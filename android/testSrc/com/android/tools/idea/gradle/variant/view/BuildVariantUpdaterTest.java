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
package com.android.tools.idea.gradle.variant.view;

import com.android.builder.model.level2.Library;
import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.ide.common.gradle.model.IdeVariant;
import com.android.ide.common.gradle.model.level2.IdeDependencies;
import com.android.tools.idea.gradle.project.ProjectStructure;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.model.NdkVariant;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleFiles;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.gradle.project.sync.VariantOnlySyncOptions;
import com.android.tools.idea.gradle.project.sync.setup.module.AndroidModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.setup.module.NdkModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.setup.module.android.AndroidVariantChangeModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.ndk.NdkVariantChangeModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.post.PostSyncProjectSetup;
import com.android.tools.idea.gradle.variant.view.BuildVariantUpdater.IdeModifiableModelsProviderFactory;
import com.android.tools.idea.testing.IdeComponents;
import com.google.common.collect.Lists;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestCase;
import java.util.HashSet;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.util.Collections;

import static com.android.tools.idea.testing.Facets.createAndAddAndroidFacet;
import static com.android.tools.idea.testing.Facets.createAndAddNdkFacet;
import static com.android.tools.idea.testing.Facets.createAndAddGradleFacet;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_VARIANT_SELECTION_CHANGED_BY_USER;
import static com.intellij.util.ThreeState.YES;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link BuildVariantUpdater}.
 */
public class BuildVariantUpdaterTest extends PlatformTestCase {
  @Mock private IdeModifiableModelsProvider myModifiableModelsProvider;
  @Mock private IdeModifiableModelsProviderFactory myModifiableModelsProviderFactory;
  @Mock private AndroidModuleSetupStep mySetupStepToInvoke;
  @Mock private AndroidModuleSetupStep mySetupStepToIgnore;
  @Mock private NdkModuleSetupStep myNdkSetupStepToInvoke;
  @Mock private NdkModuleSetupStep myNdkSetupStepToIgnore;
  @Mock private AndroidModuleModel myAndroidModel;
  @Mock private NdkModuleModel myNdkModel;
  @Mock private IdeAndroidProject myAndroidProject;
  @Mock private IdeDependencies myIdeDependencies;
  @Mock private IdeVariant myDebugVariant;
  @Mock private NdkVariant myNdkDebugVariant;
  @Mock private PostSyncProjectSetup myPostSyncProjectSetup;
  @Mock private ModuleSetupContext.Factory myModuleSetupContextFactory;
  @Mock private ModuleSetupContext myModuleSetupContext;
  @Mock private BuildVariantView.BuildVariantSelectionChangeListener myVariantSelectionChangeListener;
  @Mock private GradleFiles myGradleFiles;

  private BuildVariantUpdater myVariantUpdater;
  private List<Library> myModuleDependencies = Lists.newArrayList();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    AndroidFacet androidFacet = createAndAddAndroidFacet(getModule());
    androidFacet.getConfiguration().setModel(myAndroidModel);

    Project project = getProject();
    when(myModifiableModelsProviderFactory.create(project)).thenReturn(myModifiableModelsProvider);

    when(mySetupStepToInvoke.invokeOnBuildVariantChange()).thenReturn(true);
    when(mySetupStepToIgnore.invokeOnBuildVariantChange()).thenReturn(false);

    when(myNdkSetupStepToInvoke.invokeOnBuildVariantChange()).thenReturn(true);
    when(myNdkSetupStepToIgnore.invokeOnBuildVariantChange()).thenReturn(false);

    when(myDebugVariant.getName()).thenReturn("debug");

    when(myAndroidModel.getSelectedMainCompileLevel2Dependencies()).thenReturn(myIdeDependencies);
    when(myAndroidModel.getAndroidProject()).thenReturn(myAndroidProject);
    when(myAndroidProject.getDynamicFeatures()).thenReturn(Collections.emptyList());
    when(myIdeDependencies.getModuleDependencies()).thenReturn(myModuleDependencies);

    IdeComponents ideComponents = new IdeComponents(project);
    ideComponents.replaceProjectService(PostSyncProjectSetup.class, myPostSyncProjectSetup);
    // Replace the GradleFiles service so no hashes are updated as this can cause a NPE since the mocked models don't return anything
    ideComponents.replaceProjectService(GradleFiles.class, myGradleFiles);

    myVariantUpdater = new BuildVariantUpdater(myModuleSetupContextFactory, myModifiableModelsProviderFactory,
                                               new AndroidVariantChangeModuleSetup(mySetupStepToInvoke, mySetupStepToIgnore),
                                               new NdkVariantChangeModuleSetup(myNdkSetupStepToIgnore, myNdkSetupStepToInvoke));
    myVariantUpdater.addSelectionChangeListener(myVariantSelectionChangeListener);

    when(myNdkModel.getNdkVariantNames()).thenReturn(new HashSet() {{
      add("debug-armeabi-v7a");
      add("debug-x86");
      add("release-armeabi-v7a");
      add("release-x86");
    }});

  }

  public void testUpdateSelectedVariant() {
    String variantToSelect = "release";
    when(myAndroidModel.getSelectedVariant()).thenReturn(myDebugVariant);
    when(myAndroidModel.variantExists(variantToSelect)).thenReturn(true);
    when(myModuleSetupContextFactory.create(myModule, myModifiableModelsProvider)).thenReturn(myModuleSetupContext);

    myVariantUpdater.updateSelectedBuildVariant(myProject, myModule.getName(), variantToSelect);

    verify(myAndroidModel).setSelectedVariantName(variantToSelect);
    verify(mySetupStepToInvoke).setUpModule(myModuleSetupContext, myAndroidModel);
    verify(mySetupStepToIgnore, never()).setUpModule(myModuleSetupContext, myAndroidModel);
    verify(myVariantSelectionChangeListener).selectionChanged();

    // If PostSyncProjectSetup#setUpProject is invoked, the "Build Variants" view will show any selection variants issues.
    // See http://b/64069792
    PostSyncProjectSetup.Request setupRequest = new PostSyncProjectSetup.Request();
    setupRequest.generateSourcesAfterSync = false;
    setupRequest.cleanProjectAfterSync = false;
    verify(myPostSyncProjectSetup).setUpProject(eq(setupRequest), any(), any());
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
    String appVariant = "debug";
    String appNdkVariant = "debug-x86";
    String appAbi = "x86";

    // setup ndk facet and NdkModuleModel.
    when(myNdkDebugVariant.getName()).thenReturn(appNdkVariant);
    when(myNdkModel.getAbiName(appNdkVariant)).thenReturn(appAbi);
    when(myNdkModel.getVariantName(appNdkVariant)).thenReturn(appVariant);
    when(myNdkModel.getSelectedVariant()).thenReturn(myNdkDebugVariant);

    String ndkVariantToSelect = "release-x86";
    String variantToSelect = "release";
    String abiToSelect = "x86";

    when(myNdkModel.variantExists(ndkVariantToSelect)).thenReturn(true);
    when(myNdkModel.getVariantName(ndkVariantToSelect)).thenReturn(variantToSelect);
    when(myNdkModel.getAbiName(ndkVariantToSelect)).thenReturn(abiToSelect);
    NdkFacet ndkFacet = createAndAddNdkFacet(myModule);
    ndkFacet.setNdkModuleModel(myNdkModel);

    when(myAndroidModel.getSelectedVariant()).thenReturn(myDebugVariant);
    when(myAndroidModel.variantExists(variantToSelect)).thenReturn(true);

    when(myModuleSetupContextFactory.create(myModule, myModifiableModelsProvider)).thenReturn(myModuleSetupContext);

    // invoke method to test.
    myVariantUpdater.updateSelectedBuildVariant(myProject, myModule.getName(), variantToSelect);

    verify(myNdkSetupStepToInvoke).setUpModule(myModuleSetupContext, myNdkModel);
    verify(myNdkSetupStepToIgnore, never()).setUpModule(myModuleSetupContext, myNdkModel);
    verify(myVariantSelectionChangeListener).selectionChanged();

    // If PostSyncProjectSetup#setUpProject is invoked, the "Build Variants" view will show any selection variants issues.
    // See http://b/64069792
    PostSyncProjectSetup.Request setupRequest = new PostSyncProjectSetup.Request();
    setupRequest.generateSourcesAfterSync = false;
    setupRequest.cleanProjectAfterSync = false;
    verify(myPostSyncProjectSetup).setUpProject(eq(setupRequest), any(), any());
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
    String appVariant = "debug";
    String appNdkVariant = "debug-x86";
    String appAbi = "x86";

    // setup ndk facet and NdkModuleModel.
    when(myNdkDebugVariant.getName()).thenReturn(appNdkVariant);
    when(myNdkModel.getAbiName(appNdkVariant)).thenReturn(appAbi);
    when(myNdkModel.getVariantName(appNdkVariant)).thenReturn(appVariant);
    when(myNdkModel.getSelectedVariant()).thenReturn(myNdkDebugVariant);

    String ndkVariantToSelect = "debug-armeabi-v7a";
    String variantToSelect = "debug";
    String abiToSelect = "armeabi-v7a";

    when(myNdkModel.variantExists(ndkVariantToSelect)).thenReturn(true);
    when(myNdkModel.getVariantName(ndkVariantToSelect)).thenReturn(variantToSelect);
    when(myNdkModel.getAbiName(ndkVariantToSelect)).thenReturn(abiToSelect);
    NdkFacet ndkFacet = createAndAddNdkFacet(myModule);
    ndkFacet.setNdkModuleModel(myNdkModel);

    when(myAndroidModel.getSelectedVariant()).thenReturn(myDebugVariant);
    when(myAndroidModel.variantExists(variantToSelect)).thenReturn(true);

    when(myModuleSetupContextFactory.create(myModule, myModifiableModelsProvider)).thenReturn(myModuleSetupContext);

    // invoke method to test.
    myVariantUpdater.updateSelectedAbi(myProject, myModule.getName(), abiToSelect);

    verify(myNdkSetupStepToInvoke).setUpModule(myModuleSetupContext, myNdkModel);
    verify(myNdkSetupStepToIgnore, never()).setUpModule(myModuleSetupContext, myNdkModel);
    verify(myVariantSelectionChangeListener).selectionChanged();

    // If PostSyncProjectSetup#setUpProject is invoked, the "Build Variants" view will show any selection variants issues.
    // See http://b/64069792
    PostSyncProjectSetup.Request setupRequest = new PostSyncProjectSetup.Request();
    setupRequest.generateSourcesAfterSync = false;
    setupRequest.cleanProjectAfterSync = false;
    verify(myPostSyncProjectSetup).setUpProject(eq(setupRequest), any(), any());
  }

  public void testUpdateSelectedVariantWithUnchangedVariantName() {
    String variantToSelect = "debug"; // The default selected variant after test setup is "debug".
    when(myAndroidModel.getSelectedVariant()).thenReturn(myDebugVariant);
    when(myAndroidModel.variantExists(variantToSelect)).thenReturn(true);

    myVariantUpdater.updateSelectedBuildVariant(myProject, myModule.getName(), variantToSelect);

    verify(myAndroidModel, never()).setSelectedVariantName(variantToSelect);
    verify(mySetupStepToInvoke, never()).setUpModule(myModuleSetupContext, myAndroidModel);
    verify(mySetupStepToIgnore, never()).setUpModule(myModuleSetupContext, myAndroidModel);
    verify(myVariantSelectionChangeListener, never()).selectionChanged();

    // If PostSyncProjectSetup#setUpProject is invoked, the "Build Variants" view will show any selection variants issues.
    // See http://b/64069792
    PostSyncProjectSetup.Request setupRequest = new PostSyncProjectSetup.Request();
    setupRequest.generateSourcesAfterSync = false;
    setupRequest.cleanProjectAfterSync = false;
    verify(myPostSyncProjectSetup, never()).setUpProject(eq(setupRequest), any(), any());
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
    }).when(syncInvoker).requestProjectSyncAndSourceGeneration(eq(myProject), any(), any());

    String variantToSelect = "release";
    when(myAndroidModel.getSelectedVariant()).thenReturn(myDebugVariant);
    when(myAndroidModel.variantExists(variantToSelect)).thenReturn(true);

    myVariantUpdater.updateSelectedBuildVariant(myProject, myModule.getName(), variantToSelect);

    verify(myAndroidModel).setSelectedVariantName(variantToSelect);
    verify(syncInvoker).requestProjectSyncAndSourceGeneration(eq(myProject), any(), any());
    verify(myVariantSelectionChangeListener).selectionChanged();

    // Verify that module setup steps are not being called.
    verify(mySetupStepToInvoke, never()).setUpModule(myModuleSetupContext, myAndroidModel);
    verify(mySetupStepToIgnore, never()).setUpModule(myModuleSetupContext, myAndroidModel);
    PostSyncProjectSetup.Request setupRequest = new PostSyncProjectSetup.Request();
    setupRequest.generateSourcesAfterSync = false;
    setupRequest.cleanProjectAfterSync = false;
    verify(myPostSyncProjectSetup, never()).setUpProject(eq(setupRequest), any(), any());
  }

  public void testCompoundSyncEnabled() {
    try {
      StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.override(true);
      StudioFlags.COMPOUND_SYNC_ENABLED.override(true);

      GradleSyncInvoker syncInvoker = new IdeComponents(myProject).mockApplicationService(GradleSyncInvoker.class);

      GradleFacet gradleFacet = createAndAddGradleFacet(getModule());
      GradleModuleModel gradleModel = mock(GradleModuleModel.class);
      gradleFacet.setGradleModuleModel(gradleModel);
      when(gradleModel.getRootFolderPath()).thenReturn(new File(""));
      when(gradleModel.getGradlePath()).thenReturn(":");

      String variantToSelect = "release";
      when(myAndroidModel.getSelectedVariant()).thenReturn(myDebugVariant);
      when(myAndroidModel.variantExists(variantToSelect)).thenReturn(false);
      when(myModuleSetupContextFactory.create(myModule, myModifiableModelsProvider)).thenReturn(myModuleSetupContext);

      myVariantUpdater.updateSelectedBuildVariant(myProject, myModule.getName(), variantToSelect);

      // Check the BuildAction has its property set to generate sources and the sync request not
      GradleSyncInvoker.Request request = new GradleSyncInvoker.Request(TRIGGER_VARIANT_SELECTION_CHANGED_BY_USER);
      request.generateSourcesOnSuccess = true;
      request.variantOnlySyncOptions =
        new VariantOnlySyncOptions(gradleModel.getRootFolderPath(), gradleModel.getGradlePath(), variantToSelect, null, false);
      verify(syncInvoker).requestProjectSync(eq(myProject), eq(request), any());
    }
    finally {
      StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.clearOverride();
      StudioFlags.COMPOUND_SYNC_ENABLED.clearOverride();
    }
  }

  public void testCompoundSyncDisabled() {
    try {
      StudioFlags.COMPOUND_SYNC_ENABLED.override(false);

      GradleSyncInvoker syncInvoker = new IdeComponents(myProject).mockApplicationService(GradleSyncInvoker.class);

      GradleFacet gradleFacet = createAndAddGradleFacet(getModule());
      GradleModuleModel gradleModel = mock(GradleModuleModel.class);
      gradleFacet.setGradleModuleModel(gradleModel);
      when(gradleModel.getRootFolderPath()).thenReturn(new File(""));
      when(gradleModel.getGradlePath()).thenReturn(":");

      String variantToSelect = "release";
      when(myAndroidModel.getSelectedVariant()).thenReturn(myDebugVariant);
      when(myAndroidModel.variantExists(variantToSelect)).thenReturn(false);
      when(myModuleSetupContextFactory.create(myModule, myModifiableModelsProvider)).thenReturn(myModuleSetupContext);

      myVariantUpdater.updateSelectedBuildVariant(myProject, myModule.getName(), variantToSelect);

      // Check the BuildAction has its property set to not generate sources and the sync request does
      GradleSyncInvoker.Request request = new GradleSyncInvoker.Request(TRIGGER_VARIANT_SELECTION_CHANGED_BY_USER);
      request.generateSourcesOnSuccess = true;
      request.variantOnlySyncOptions =
        new VariantOnlySyncOptions(gradleModel.getRootFolderPath(), gradleModel.getGradlePath(), variantToSelect);
      verify(syncInvoker).requestProjectSync(eq(myProject), eq(request), any());
    }
    finally {
      StudioFlags.COMPOUND_SYNC_ENABLED.clearOverride();
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
    Library library = mock(Library.class);

    // Create library module with Android facet.
    Module libraryModule = createModule("library");
    AndroidFacet libraryAndroidFacet = createAndAddAndroidFacet(libraryModule);
    libraryAndroidFacet.getConfiguration().setModel(libraryAndroidModel);

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
    when(library.getType()).thenReturn(Library.LIBRARY_MODULE);
    when(library.getVariant()).thenReturn("release");
    when(library.getProjectPath()).thenReturn(":library");
    myModuleDependencies.add(library);
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

    // Verify the invoked setup steps.
    verify(mySetupStepToInvoke).setUpModule(myModuleSetupContext, myAndroidModel);
    verify(mySetupStepToIgnore, never()).setUpModule(myModuleSetupContext, myAndroidModel);
    verify(myVariantSelectionChangeListener).selectionChanged();

    // If PostSyncProjectSetup#setUpProject is invoked, the "Build Variants" view will show any selection variants issues.
    // See http://b/64069792
    PostSyncProjectSetup.Request setupRequest = new PostSyncProjectSetup.Request();
    setupRequest.generateSourcesAfterSync = false;
    setupRequest.cleanProjectAfterSync = false;
    verify(myPostSyncProjectSetup).setUpProject(eq(setupRequest), any(), any());
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
    String appNdkVariant = "debug-x86";
    String appAbi = "x86";
    String libraryVariant = "debug";

    // Setup app.

    // Setup expectations for app.
    when(myNdkDebugVariant.getName()).thenReturn(appNdkVariant);
    when(myNdkModel.getAbiName(appNdkVariant)).thenReturn(appAbi);
    when(myNdkModel.getSelectedVariant()).thenReturn(myNdkDebugVariant);
    when(myAndroidModel.getSelectedVariant()).thenReturn(myDebugVariant);
    when(myModuleSetupContextFactory.create(myModule, myModifiableModelsProvider)).thenReturn(myModuleSetupContext);

    // Register NDK facet for app.
    NdkFacet ndkFacet = createAndAddNdkFacet(myModule);
    ndkFacet.setNdkModuleModel(myNdkModel);

    // Mock objects required by library.
    AndroidModuleModel libraryAndroidModel = mock(AndroidModuleModel.class);
    IdeVariant libraryDebugVariant = mock(IdeVariant.class);
    IdeDependencies libraryIdeDependencies = mock(IdeDependencies.class);
    IdeAndroidProject libraryAndroidProject = mock(IdeAndroidProject.class);
    ModuleSetupContext libraryModuleSetupContext = mock(ModuleSetupContext.class);
    Library library = mock(Library.class);

    // Create library module with Android facet.
    Module libraryModule = createModule("library");
    AndroidFacet libraryAndroidFacet = createAndAddAndroidFacet(libraryModule);
    libraryAndroidFacet.getConfiguration().setModel(libraryAndroidModel);

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
    when(library.getType()).thenReturn(Library.LIBRARY_MODULE);
    when(library.getVariant()).thenReturn("release");
    when(library.getProjectPath()).thenReturn(":library");
    myModuleDependencies.add(library);
    when(myAndroidModel.getSelectedMainCompileLevel2Dependencies()).thenReturn(myIdeDependencies);
    ProjectStructure.getInstance(myProject).getModuleFinder().addModule(libraryModule, ":library");

    // Selected variants (and ABI) for NDK and non-NDK modules.
    String ndkVariantToSelect = "release-x86";
    String variantToSelect = "release";
    String abiToSelect = "x86";

    // Selected variant related expectations.
    when(myAndroidModel.variantExists(variantToSelect)).thenReturn(true);
    when(libraryAndroidModel.variantExists(variantToSelect)).thenReturn(true);
    when(myNdkModel.variantExists(ndkVariantToSelect)).thenReturn(true);
    when(myNdkModel.getVariantName(ndkVariantToSelect)).thenReturn(variantToSelect);
    when(myNdkModel.getAbiName(ndkVariantToSelect)).thenReturn(abiToSelect);

    // Invoke method to test.
    myVariantUpdater.updateSelectedBuildVariant(myProject, myModule.getName(), variantToSelect);

    // Verify that variants are selected as expected.
    verify(myAndroidModel).setSelectedVariantName(variantToSelect);
    verify(myNdkModel).setSelectedVariantName(ndkVariantToSelect);
    verify(libraryAndroidModel).setSelectedVariantName(variantToSelect);

    // Verify the invoked setup steps.
    verify(myNdkSetupStepToInvoke).setUpModule(myModuleSetupContext, myNdkModel);
    verify(myNdkSetupStepToIgnore, never()).setUpModule(myModuleSetupContext, myNdkModel);
    verify(myVariantSelectionChangeListener).selectionChanged();

    // If PostSyncProjectSetup#setUpProject is invoked, the "Build Variants" view will show any selection variants issues.
    // See http://b/64069792
    PostSyncProjectSetup.Request setupRequest = new PostSyncProjectSetup.Request();
    setupRequest.generateSourcesAfterSync = false;
    setupRequest.cleanProjectAfterSync = false;
    verify(myPostSyncProjectSetup).setUpProject(eq(setupRequest), any(), any());
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
    String libraryVariant = "debug";
    String libraryNdkVariant = "debug-x86";
    String libraryAbi = "x86";

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
    Library library = mock(Library.class);
    NdkModuleModel libraryNdkModel = mock(NdkModuleModel.class);
    NdkVariant libraryNdkDebugVariant = mock(NdkVariant.class);

    // Create library module with Android facet.
    Module libraryModule = createModule("library");
    AndroidFacet libraryAndroidFacet = createAndAddAndroidFacet(libraryModule);
    libraryAndroidFacet.getConfiguration().setModel(libraryAndroidModel);

    // Setup library.

    // Setup expectations for library.
    when(libraryDebugVariant.getName()).thenReturn(libraryVariant);
    when(libraryAndroidModel.getSelectedVariant()).thenReturn(libraryDebugVariant);
    when(libraryIdeDependencies.getModuleDependencies()).thenReturn(Collections.emptyList());
    when(libraryAndroidProject.getDynamicFeatures()).thenReturn(Collections.emptyList());
    when(libraryAndroidModel.getAndroidProject()).thenReturn(libraryAndroidProject);
    when(libraryAndroidModel.getSelectedMainCompileLevel2Dependencies()).thenReturn(libraryIdeDependencies);
    when(myModuleSetupContextFactory.create(libraryModule, myModifiableModelsProvider)).thenReturn(libraryModuleSetupContext);

    // Setup NDK expectations for library.
    when(libraryNdkDebugVariant.getName()).thenReturn(libraryNdkVariant);
    when(libraryNdkModel.getAbiName(libraryNdkVariant)).thenReturn(libraryAbi);
    when(libraryNdkModel.getSelectedVariant()).thenReturn(libraryNdkDebugVariant);

    // Register NDK facet for library.
    NdkFacet ndkFacet = createAndAddNdkFacet(libraryModule);
    ndkFacet.setNdkModuleModel(libraryNdkModel);

    // Register "library" into gradle so that "app:release" depends on "library:release".
    when(library.getType()).thenReturn(Library.LIBRARY_MODULE);
    when(library.getVariant()).thenReturn("release");
    when(library.getProjectPath()).thenReturn(":library");
    myModuleDependencies.add(library);
    when(myAndroidModel.getSelectedMainCompileLevel2Dependencies()).thenReturn(myIdeDependencies);
    ProjectStructure.getInstance(myProject).getModuleFinder().addModule(libraryModule, ":library");

    // Selected variants (and ABI) for NDK and non-NDK modules.
    String ndkVariantToSelect = "release-x86";
    String variantToSelect = "release";
    String abiToSelect = "x86";

    // Selected variant related expectations.
    when(myAndroidModel.variantExists(variantToSelect)).thenReturn(true);
    when(libraryAndroidModel.variantExists(variantToSelect)).thenReturn(true);
    when(libraryNdkModel.variantExists(ndkVariantToSelect)).thenReturn(true);
    when(libraryNdkModel.getVariantName(ndkVariantToSelect)).thenReturn(variantToSelect);
    when(libraryNdkModel.getAbiName(ndkVariantToSelect)).thenReturn(abiToSelect);

    when(libraryNdkModel.getNdkVariantNames()).thenReturn(new HashSet() {{
      add("debug-armeabi-v7a");
      add("debug-x86");
      add("release-armeabi-v7a");
      add("release-x86");
    }});

    // Invoke method to test.
    myVariantUpdater.updateSelectedBuildVariant(myProject, myModule.getName(), variantToSelect);

    // Verify that variants are selected as expected.
    verify(myAndroidModel).setSelectedVariantName(variantToSelect);
    verify(libraryAndroidModel).setSelectedVariantName(variantToSelect);
    verify(libraryNdkModel).setSelectedVariantName(ndkVariantToSelect);

    // Verify the invoked setup steps.
    verify(myNdkSetupStepToInvoke).setUpModule(libraryModuleSetupContext, libraryNdkModel);
    verify(myNdkSetupStepToIgnore, never()).setUpModule(libraryModuleSetupContext, libraryNdkModel);
    verify(myVariantSelectionChangeListener).selectionChanged();

    // If PostSyncProjectSetup#setUpProject is invoked, the "Build Variants" view will show any selection variants issues.
    // See http://b/64069792
    PostSyncProjectSetup.Request setupRequest = new PostSyncProjectSetup.Request();
    setupRequest.generateSourcesAfterSync = false;
    setupRequest.cleanProjectAfterSync = false;
    verify(myPostSyncProjectSetup).setUpProject(eq(setupRequest), any(), any());
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
    String appNdkVariant = "debug-x86";
    String appAbi = "x86";
    String libraryVariant = "debug";
    String libraryNdkVariant = "debug-x86";
    String libraryAbi = "x86";

    // Setup app.

    // Setup expectations for app.
    when(myNdkDebugVariant.getName()).thenReturn(appNdkVariant);
    when(myNdkModel.getAbiName(appNdkVariant)).thenReturn(appAbi);
    when(myNdkModel.getSelectedVariant()).thenReturn(myNdkDebugVariant);
    when(myAndroidModel.getSelectedVariant()).thenReturn(myDebugVariant);
    when(myModuleSetupContextFactory.create(myModule, myModifiableModelsProvider)).thenReturn(myModuleSetupContext);

    // Register NDK facet for app.
    NdkFacet ndkFacet = createAndAddNdkFacet(myModule);
    ndkFacet.setNdkModuleModel(myNdkModel);

    // Setup expectations for app.
    //when(myAndroidModel.getSelectedVariant()).thenReturn(myDebugVariant);
    //when(myModuleSetupContextFactory.create(myModule, myModifiableModelsProvider)).thenReturn(myModuleSetupContext);

    // Mock objects required by library.
    AndroidModuleModel libraryAndroidModel = mock(AndroidModuleModel.class);
    IdeVariant libraryDebugVariant = mock(IdeVariant.class);
    IdeDependencies libraryIdeDependencies = mock(IdeDependencies.class);
    IdeAndroidProject libraryAndroidProject = mock(IdeAndroidProject.class);
    ModuleSetupContext libraryModuleSetupContext = mock(ModuleSetupContext.class);
    Library library = mock(Library.class);
    NdkModuleModel libraryNdkModel = mock(NdkModuleModel.class);
    NdkVariant libraryNdkDebugVariant = mock(NdkVariant.class);

    // Create library module with Android facet.
    Module libraryModule = createModule("library");
    AndroidFacet libraryAndroidFacet = createAndAddAndroidFacet(libraryModule);
    libraryAndroidFacet.getConfiguration().setModel(libraryAndroidModel);

    // Setup library.

    // Setup expectations for library.
    when(libraryDebugVariant.getName()).thenReturn(libraryVariant);
    when(libraryAndroidModel.getSelectedVariant()).thenReturn(libraryDebugVariant);
    when(libraryIdeDependencies.getModuleDependencies()).thenReturn(Collections.emptyList());
    when(libraryAndroidProject.getDynamicFeatures()).thenReturn(Collections.emptyList());
    when(libraryAndroidModel.getAndroidProject()).thenReturn(libraryAndroidProject);
    when(libraryAndroidModel.getSelectedMainCompileLevel2Dependencies()).thenReturn(libraryIdeDependencies);
    when(myModuleSetupContextFactory.create(libraryModule, myModifiableModelsProvider)).thenReturn(libraryModuleSetupContext);

    // Setup NDK expectations for library.
    when(libraryNdkDebugVariant.getName()).thenReturn(libraryNdkVariant);
    when(libraryNdkModel.getAbiName(libraryNdkVariant)).thenReturn(libraryAbi);
    when(libraryNdkModel.getSelectedVariant()).thenReturn(libraryNdkDebugVariant);

    // Register NDK facet for library.
    NdkFacet libraryNdkFacet = createAndAddNdkFacet(libraryModule);
    libraryNdkFacet.setNdkModuleModel(libraryNdkModel);

    // Register "library" into gradle so that "app:release" depends on "library:release".
    when(library.getType()).thenReturn(Library.LIBRARY_MODULE);
    when(library.getVariant()).thenReturn("release");
    when(library.getProjectPath()).thenReturn(":library");
    myModuleDependencies.add(library);
    when(myAndroidModel.getSelectedMainCompileLevel2Dependencies()).thenReturn(myIdeDependencies);
    ProjectStructure.getInstance(myProject).getModuleFinder().addModule(libraryModule, ":library");

    // Selected variants (and ABI) for NDK and non-NDK modules.
    String ndkVariantToSelect = "release-x86";
    String variantToSelect = "release";
    String abiToSelect = "x86";

    // Selected variant related expectations.
    when(myAndroidModel.variantExists(variantToSelect)).thenReturn(true);
    when(myNdkModel.variantExists(ndkVariantToSelect)).thenReturn(true);
    when(myNdkModel.getVariantName(ndkVariantToSelect)).thenReturn(variantToSelect);
    when(libraryAndroidModel.variantExists(variantToSelect)).thenReturn(true);
    when(libraryNdkModel.variantExists(ndkVariantToSelect)).thenReturn(true);
    when(libraryNdkModel.getVariantName(ndkVariantToSelect)).thenReturn(variantToSelect);
    when(libraryNdkModel.getAbiName(ndkVariantToSelect)).thenReturn(abiToSelect);

    when(libraryNdkModel.getNdkVariantNames()).thenReturn(new HashSet() {{
      add("debug-armeabi-v7a");
      add("debug-x86");
      add("release-armeabi-v7a");
      add("release-x86");
    }});

    // Invoke method to test.
    myVariantUpdater.updateSelectedBuildVariant(myProject, myModule.getName(), variantToSelect);

    // Verify that variants are selected as expected.
    verify(myAndroidModel).setSelectedVariantName(variantToSelect);
    verify(myNdkModel).setSelectedVariantName(ndkVariantToSelect);
    verify(libraryAndroidModel).setSelectedVariantName(variantToSelect);
    verify(libraryNdkModel).setSelectedVariantName(ndkVariantToSelect);

    // Verify the invoked setup steps.
    verify(myNdkSetupStepToInvoke).setUpModule(libraryModuleSetupContext, libraryNdkModel);
    verify(myNdkSetupStepToIgnore, never()).setUpModule(libraryModuleSetupContext, libraryNdkModel);
    verify(myVariantSelectionChangeListener).selectionChanged();

    // If PostSyncProjectSetup#setUpProject is invoked, the "Build Variants" view will show any selection variants issues.
    // See http://b/64069792
    PostSyncProjectSetup.Request setupRequest = new PostSyncProjectSetup.Request();
    setupRequest.generateSourcesAfterSync = false;
    setupRequest.cleanProjectAfterSync = false;
    verify(myPostSyncProjectSetup).setUpProject(eq(setupRequest), any(), any());
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
    String appVariant = "debug";
    String appNdkVariant = "debug-x86";
    String appAbi = "x86";
    String libraryVariant = "debug";
    String libraryNdkVariant = "debug-x86";
    String libraryAbi = "x86";

    // Setup app.

    // Setup expectations for app.
    when(myNdkDebugVariant.getName()).thenReturn(appNdkVariant);
    when(myNdkModel.getVariantName(appNdkVariant)).thenReturn(appVariant);
    when(myNdkModel.getAbiName(appNdkVariant)).thenReturn(appAbi);
    when(myNdkModel.getSelectedVariant()).thenReturn(myNdkDebugVariant);
    when(myAndroidModel.getSelectedVariant()).thenReturn(myDebugVariant);
    when(myModuleSetupContextFactory.create(myModule, myModifiableModelsProvider)).thenReturn(myModuleSetupContext);

    // Register NDK facet for app.
    NdkFacet ndkFacet = createAndAddNdkFacet(myModule);
    ndkFacet.setNdkModuleModel(myNdkModel);

    // Setup expectations for app.
    //when(myAndroidModel.getSelectedVariant()).thenReturn(myDebugVariant);
    //when(myModuleSetupContextFactory.create(myModule, myModifiableModelsProvider)).thenReturn(myModuleSetupContext);

    // Mock objects required by library.
    AndroidModuleModel libraryAndroidModel = mock(AndroidModuleModel.class);
    IdeVariant libraryDebugVariant = mock(IdeVariant.class);
    IdeDependencies libraryIdeDependencies = mock(IdeDependencies.class);
    IdeAndroidProject libraryAndroidProject = mock(IdeAndroidProject.class);
    ModuleSetupContext libraryModuleSetupContext = mock(ModuleSetupContext.class);
    Library library = mock(Library.class);
    NdkModuleModel libraryNdkModel = mock(NdkModuleModel.class);
    NdkVariant libraryNdkDebugVariant = mock(NdkVariant.class);

    // Create library module with Android facet.
    Module libraryModule = createModule("library");
    AndroidFacet libraryAndroidFacet = createAndAddAndroidFacet(libraryModule);
    libraryAndroidFacet.getConfiguration().setModel(libraryAndroidModel);

    // Setup library.

    // Setup expectations for library.
    when(libraryDebugVariant.getName()).thenReturn(libraryVariant);
    when(libraryAndroidModel.getSelectedVariant()).thenReturn(libraryDebugVariant);
    when(libraryIdeDependencies.getModuleDependencies()).thenReturn(Collections.emptyList());
    when(libraryAndroidProject.getDynamicFeatures()).thenReturn(Collections.emptyList());
    when(libraryAndroidModel.getAndroidProject()).thenReturn(libraryAndroidProject);
    when(libraryAndroidModel.getSelectedMainCompileLevel2Dependencies()).thenReturn(libraryIdeDependencies);
    when(myModuleSetupContextFactory.create(libraryModule, myModifiableModelsProvider)).thenReturn(libraryModuleSetupContext);

    // Setup NDK expectations for library.
    when(libraryNdkDebugVariant.getName()).thenReturn(libraryNdkVariant);
    when(libraryNdkModel.getAbiName(libraryNdkVariant)).thenReturn(libraryAbi);
    when(libraryNdkModel.getSelectedVariant()).thenReturn(libraryNdkDebugVariant);

    // Register NDK facet for library.
    NdkFacet libraryNdkFacet = createAndAddNdkFacet(libraryModule);
    libraryNdkFacet.setNdkModuleModel(libraryNdkModel);

    // Register "library" into gradle so that "app:debug" depends on "library:debug".
    when(library.getType()).thenReturn(Library.LIBRARY_MODULE);
    when(library.getVariant()).thenReturn("debug");
    when(library.getProjectPath()).thenReturn(":library");
    myModuleDependencies.add(library);
    when(myAndroidModel.getSelectedMainCompileLevel2Dependencies()).thenReturn(myIdeDependencies);
    ProjectStructure.getInstance(myProject).getModuleFinder().addModule(libraryModule, ":library");

    // Selected variants (and ABI) for NDK and non-NDK modules.
    String ndkVariantToSelect = "debug-armeabi-v7a";
    String variantToSelect = "debug";
    String abiToSelect = "armeabi-v7a";

    // Selected variant related expectations.
    when(myAndroidModel.variantExists(variantToSelect)).thenReturn(true);
    when(myNdkModel.variantExists(ndkVariantToSelect)).thenReturn(true);
    when(myNdkModel.getVariantName(ndkVariantToSelect)).thenReturn(variantToSelect);
    when(myNdkModel.getAbiName(ndkVariantToSelect)).thenReturn(abiToSelect);
    when(libraryAndroidModel.variantExists(variantToSelect)).thenReturn(true);
    when(libraryNdkModel.variantExists(ndkVariantToSelect)).thenReturn(true);
    when(libraryNdkModel.getVariantName(ndkVariantToSelect)).thenReturn(variantToSelect);
    when(libraryNdkModel.getAbiName(ndkVariantToSelect)).thenReturn(abiToSelect);

    when(libraryNdkModel.getNdkVariantNames()).thenReturn(new HashSet() {{
      add("debug-armeabi-v7a");
      add("debug-x86");
      add("release-armeabi-v7a");
      add("release-x86");
    }});

    // Invoke method to test.
    myVariantUpdater.updateSelectedAbi(myProject, myModule.getName(), abiToSelect);

    // Verify that variants are selected as expected.
    verify(myAndroidModel).setSelectedVariantName(variantToSelect);
    verify(myNdkModel).setSelectedVariantName(ndkVariantToSelect);
    verify(libraryAndroidModel).setSelectedVariantName(variantToSelect);
    verify(libraryNdkModel).setSelectedVariantName(ndkVariantToSelect);

    // Verify the invoked setup steps.
    verify(myNdkSetupStepToInvoke).setUpModule(libraryModuleSetupContext, libraryNdkModel);
    verify(myNdkSetupStepToIgnore, never()).setUpModule(libraryModuleSetupContext, libraryNdkModel);
    verify(myVariantSelectionChangeListener).selectionChanged();

    // If PostSyncProjectSetup#setUpProject is invoked, the "Build Variants" view will show any selection variants issues.
    // See http://b/64069792
    PostSyncProjectSetup.Request setupRequest = new PostSyncProjectSetup.Request();
    setupRequest.generateSourcesAfterSync = false;
    setupRequest.cleanProjectAfterSync = false;
    verify(myPostSyncProjectSetup).setUpProject(eq(setupRequest), any(), any());
  }
}
