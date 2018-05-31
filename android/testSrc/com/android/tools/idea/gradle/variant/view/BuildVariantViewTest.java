/*
 * Copyright (C) 2013 The Android Open Source Project
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
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.gradle.project.sync.setup.module.VariantOnlySyncModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.post.PostSyncProjectSetup;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.mockito.Mock;

import static com.android.tools.idea.testing.Facets.createAndAddAndroidFacet;
import static java.util.Collections.emptyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link BuildVariantView}.
 */
public class BuildVariantViewTest extends IdeaTestCase {
  private Listener myListener;
  private BuildVariantView myView;
  private String myBuildVariantName;

  @Mock private IdeModifiableModelsProvider myModifiableModelsProvider;
  @Mock private BuildVariantUpdater.IdeModifiableModelsProviderFactory myModifiableModelsProviderFactory;
  @Mock private AndroidModuleModel myAndroidModel;
  @Mock private IdeDependencies myIdeDependencies;
  @Mock private IdeVariant myDebugVariant;
  @Mock private PostSyncProjectSetup myPostSyncProjectSetup;
  @Mock private ModuleSetupContext.Factory myModuleSetupContextFactory;
  @Mock private ModuleSetupContext myModuleSetupContext;
  @Mock private VariantOnlySyncModuleSetup myModuleSetup;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    myView = BuildVariantView.getInstance(getProject());
    myListener = new Listener();
    myView.addListener(myListener);
    myBuildVariantName = "debug";

    AndroidFacet androidFacet = createAndAddAndroidFacet(myModule);
    androidFacet.getConfiguration().setModel(myAndroidModel);

    Project project = getProject();
    when(myModifiableModelsProviderFactory.create(project)).thenReturn(myModifiableModelsProvider);

    when(myAndroidModel.getSelectedVariant()).thenReturn(myDebugVariant);
    when(myDebugVariant.getName()).thenReturn(myBuildVariantName);

    when(myAndroidModel.getSelectedMainCompileLevel2Dependencies()).thenReturn(myIdeDependencies);
    when(myIdeDependencies.getModuleDependencies()).thenReturn(emptyList());

    new IdeComponents(project).replaceProjectService(PostSyncProjectSetup.class, myPostSyncProjectSetup);
    when(myModuleSetupContextFactory.create(myModule, myModifiableModelsProvider)).thenReturn(myModuleSetupContext);

    BuildVariantUpdater variantUpdater =
      new BuildVariantUpdater(myModuleSetupContextFactory, myModifiableModelsProviderFactory, myModuleSetup);
    myView.setUpdater(variantUpdater);
  }

  public void testSelectVariantWithSuccessfulUpdate() {
    String variantToSelect = "release";
    when(myAndroidModel.variantExists(variantToSelect)).thenReturn(true);
    // Changing selected variant from "debug" to "release".
    myView.buildVariantSelected(myModule.getName(), variantToSelect);
    // Verify module setup was called.
    verify(myModuleSetup).setUpModule(any(), any());
    // Verify listener is invoked.
    assertTrue(myListener.myWasCalled);
  }

  public void testSelectVariantWithUnchangedVariantName() {
    when(myAndroidModel.variantExists(myBuildVariantName)).thenReturn(true);
    // Choose "debug" when "debug" is already selected.
    myView.buildVariantSelected(myModule.getName(), myBuildVariantName);
    // Verify listener not invoked when the selected value didn't change.
    assertFalse(myListener.myWasCalled);
  }

  private static class Listener implements BuildVariantView.BuildVariantSelectionChangeListener {
    boolean myWasCalled;

    @Override
    public void selectionChanged() {
      myWasCalled = true;
    }
  }
}
