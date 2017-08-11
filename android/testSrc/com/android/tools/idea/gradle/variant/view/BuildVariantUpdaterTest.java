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

import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.ide.android.IdeVariant;
import com.android.tools.idea.gradle.project.model.ide.android.level2.IdeDependencies;
import com.android.tools.idea.gradle.project.sync.setup.module.AndroidModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.setup.post.PostSyncProjectSetup;
import com.android.tools.idea.gradle.variant.view.BuildVariantUpdater.IdeModifiableModelsProviderFactory;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.Collections;

import static com.android.tools.idea.testing.Facets.createAndAddAndroidFacet;
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

  private BuildVariantUpdater myVariantUpdater;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    AndroidFacet androidFacet = createAndAddAndroidFacet(getModule());
    androidFacet.setAndroidModel(myAndroidModel);

    Project project = getProject();
    when(myModifiableModelsProviderFactory.create(project)).thenReturn(myModifiableModelsProvider);

    when(mySetupStepToInvoke.invokeOnBuildVariantChange()).thenReturn(true);
    when(mySetupStepToIgnore.invokeOnBuildVariantChange()).thenReturn(false);

    when(myDebugVariant.getName()).thenReturn("debug");

    when(myAndroidModel.getSelectedMainCompileLevel2Dependencies()).thenReturn(myIdeDependencies);

    IdeComponents.replaceService(project, PostSyncProjectSetup.class, myPostSyncProjectSetup);

    myVariantUpdater = new BuildVariantUpdater(myModifiableModelsProviderFactory, Arrays.asList(mySetupStepToInvoke, mySetupStepToIgnore));
  }

  public void testUpdateSelectedVariant() {
    when(myAndroidModel.getSelectedVariant()).thenReturn(myDebugVariant);
    when(myIdeDependencies.getModuleDependencies()).thenReturn(Collections.emptyList());

    Module module = getModule();
    boolean updated = myVariantUpdater.updateSelectedVariant(getProject(), module.getName(), "release");

    assertTrue(updated);

    verify(mySetupStepToInvoke).setUpModule(module, myModifiableModelsProvider, myAndroidModel, null, null);
    verify(mySetupStepToIgnore, never()).setUpModule(module, myModifiableModelsProvider, myAndroidModel, null, null);

    // If PostSyncProjectSetup#setUpProject is invoked, the "Build Variants" view will show any selection variants issues.
    // See http://b/64069792
    PostSyncProjectSetup.Request setupRequest = new PostSyncProjectSetup.Request();
    setupRequest.setGenerateSourcesAfterSync(false).setCleanProjectAfterSync(false);
    verify(myPostSyncProjectSetup).setUpProject(eq(setupRequest), any());
  }
}