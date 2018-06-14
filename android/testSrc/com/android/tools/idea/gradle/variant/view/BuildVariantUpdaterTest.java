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

import com.android.ide.common.gradle.model.IdeVariant;
import com.android.ide.common.gradle.model.level2.IdeDependencies;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.gradle.project.sync.setup.module.AndroidModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.setup.module.VariantOnlySyncModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.post.PostSyncProjectSetup;
import com.android.tools.idea.gradle.variant.view.BuildVariantUpdater.IdeModifiableModelsProviderFactory;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Collections;

import static com.android.tools.idea.testing.Facets.createAndAddAndroidFacet;
import static com.intellij.util.ThreeState.YES;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link BuildVariantUpdater}.
 */
public class BuildVariantUpdaterTest extends IdeaTestCase {
  @Mock private IdeModifiableModelsProvider myModifiableModelsProvider;
  @Mock private IdeModifiableModelsProviderFactory myModifiableModelsProviderFactory;
  @Mock private AndroidModuleSetupStep mySetupStepToInvoke;
  @Mock private AndroidModuleSetupStep mySetupStepToIgnore;
  @Mock private AndroidModuleModel myAndroidModel;
  @Mock private IdeDependencies myIdeDependencies;
  @Mock private IdeVariant myDebugVariant;
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

    when(myDebugVariant.getName()).thenReturn("debug");

    when(myAndroidModel.getSelectedMainCompileLevel2Dependencies()).thenReturn(myIdeDependencies);
    when(myIdeDependencies.getModuleDependencies()).thenReturn(Collections.emptyList());

    new IdeComponents(project).replaceProjectService(PostSyncProjectSetup.class, myPostSyncProjectSetup);

    myVariantUpdater = new BuildVariantUpdater(myModuleSetupContextFactory, myModifiableModelsProviderFactory,
                                               new VariantOnlySyncModuleSetup(mySetupStepToInvoke, mySetupStepToIgnore));
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
}
