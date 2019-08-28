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

import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.ide.common.gradle.model.IdeVariant;
import com.android.ide.common.gradle.model.level2.IdeDependencies;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.model.NdkVariant;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.gradle.project.sync.ng.variantonly.VariantOnlySyncOptions;
import com.android.tools.idea.gradle.project.sync.setup.module.AndroidModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.setup.module.NdkModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.setup.module.android.AndroidVariantChangeModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.ndk.NdkVariantChangeModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.post.PostSyncProjectSetup;
import com.android.tools.idea.gradle.variant.view.BuildVariantUpdater.IdeModifiableModelsProviderFactory;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.JavaProjectTestCase;
import com.intellij.testFramework.ServiceContainerUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.util.Collections;

import static com.android.tools.idea.testing.Facets.*;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_VARIANT_SELECTION_CHANGED_BY_USER;
import static com.intellij.util.ThreeState.YES;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link BuildVariantUpdater}.
 */
public class BuildVariantUpdaterTest extends JavaProjectTestCase {
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

  private BuildVariantUpdater myVariantUpdater;

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
    when(myIdeDependencies.getModuleDependencies()).thenReturn(Collections.emptyList());

    ServiceContainerUtil.replaceService(project, PostSyncProjectSetup.class, myPostSyncProjectSetup, getTestRootDisposable());

    myVariantUpdater = new BuildVariantUpdater(myModuleSetupContextFactory, myModifiableModelsProviderFactory,
                                               new AndroidVariantChangeModuleSetup(mySetupStepToInvoke, mySetupStepToIgnore),
                                               new NdkVariantChangeModuleSetup(myNdkSetupStepToIgnore, myNdkSetupStepToInvoke));
    myVariantUpdater.addSelectionChangeListener(myVariantSelectionChangeListener);
  }

  public void testUpdateSelectedVariant() {
    String variantToSelect = "release";
    when(myAndroidModel.getSelectedVariant()).thenReturn(myDebugVariant);
    when(myAndroidModel.variantExists(variantToSelect)).thenReturn(true);
    when(myModuleSetupContextFactory.create(myModule, myModifiableModelsProvider)).thenReturn(myModuleSetupContext);

    myVariantUpdater.updateSelectedVariant(myProject, myModule.getName(), variantToSelect);

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

  public void testUpdateSelectedVariantWithNdkModule() {
    // variant display name for ndk module contains both of variant and abi.
    String variantToSelect = "release-x86";

    // setup ndk facet and NdkModuleModel.
    when(myNdkDebugVariant.getName()).thenReturn("debug");
    when(myNdkModel.getSelectedVariant()).thenReturn(myNdkDebugVariant);
    when(myNdkModel.variantExists(variantToSelect)).thenReturn(true);
    when(myNdkModel.getVariantName(variantToSelect)).thenReturn("release");
    NdkFacet ndkFacet = createAndAddNdkFacet(myModule);
    ndkFacet.setNdkModuleModel(myNdkModel);

    when(myAndroidModel.getSelectedVariant()).thenReturn(myDebugVariant);
    when(myAndroidModel.variantExists("release")).thenReturn(true);

    when(myModuleSetupContextFactory.create(myModule, myModifiableModelsProvider)).thenReturn(myModuleSetupContext);

    // invoke method to test.
    myVariantUpdater.updateSelectedVariant(myProject, myModule.getName(), variantToSelect);

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

    myVariantUpdater.updateSelectedVariant(myProject, myModule.getName(), variantToSelect);

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
    ServiceContainerUtil
      .replaceService(myProject, GradleSyncState.class, syncState, getTestRootDisposable());
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

    myVariantUpdater.updateSelectedVariant(myProject, myModule.getName(), variantToSelect);

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
      StudioFlags.NEW_SYNC_INFRA_ENABLED.override(true);
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

      myVariantUpdater.updateSelectedVariant(myProject, myModule.getName(), variantToSelect);

      // Check the BuildAction has its property set to generate sources and the sync request not
      GradleSyncInvoker.Request request = new GradleSyncInvoker.Request(TRIGGER_VARIANT_SELECTION_CHANGED_BY_USER);
      request.generateSourcesOnSuccess = true;
      request.variantOnlySyncOptions =
        new VariantOnlySyncOptions(gradleModel.getRootFolderPath(), gradleModel.getGradlePath(), variantToSelect, null, true);
      verify(syncInvoker).requestProjectSync(eq(myProject), eq(request), any());
    }
    finally {
      StudioFlags.NEW_SYNC_INFRA_ENABLED.clearOverride();
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

      myVariantUpdater.updateSelectedVariant(myProject, myModule.getName(), variantToSelect);

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
}
