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
package com.android.tools.idea.gradle.project.sync.setup.module.idea;

import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.gradle.project.sync.ng.SyncAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.mockito.Mock;

import static com.android.tools.idea.testing.Facets.createAndAddAndroidFacet;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link JavaModuleSetup}.
 */
public class JavaModuleSetupTest extends IdeaTestCase {
  @Mock private JavaModuleModel myJavaModel;
  @Mock private SyncAction.ModuleModels myModuleModels;
  @Mock private ProgressIndicator myProgressIndicator;
  @Mock private JavaModuleSetupStep mySetupStep1;
  @Mock private JavaModuleSetupStep mySetupStep2;

  private JavaModuleSetup myModuleSetup;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    myModuleSetup = new JavaModuleSetup(mySetupStep1, mySetupStep2);
  }

  public void testSetUpModuleWithProgressIndicator() {
    when(myJavaModel.isAndroidModuleWithoutVariants()).thenReturn(false);

    IdeModifiableModelsProvider modelsProvider = mock(IdeModifiableModelsProvider.class);

    Module module = getModule();
    myModuleSetup.setUpModule(module, modelsProvider, myJavaModel, myModuleModels, myProgressIndicator, false);

    verify(mySetupStep1, times(1)).setUpModule(module, modelsProvider, myJavaModel, myModuleModels, myProgressIndicator);
    verify(mySetupStep2, times(1)).setUpModule(module, modelsProvider, myJavaModel, myModuleModels, myProgressIndicator);
  }

  public void testSetUpModuleWithoutProgressIndicator() {
    when(myJavaModel.isAndroidModuleWithoutVariants()).thenReturn(false);

    IdeModifiableModelsProvider modelsProvider = mock(IdeModifiableModelsProvider.class);

    Module module = getModule();
    myModuleSetup.setUpModule(module, modelsProvider, myJavaModel, myModuleModels, null, false);

    verify(mySetupStep1, times(1)).setUpModule(module, modelsProvider, myJavaModel, myModuleModels, null);
    verify(mySetupStep2, times(1)).setUpModule(module, modelsProvider, myJavaModel, myModuleModels, null);
  }

  public void testSetUpAndroidModuleWithSyncSkipped() {
    when(mySetupStep1.invokeOnSkippedSync()).thenReturn(true);
    IdeModifiableModelsProvider modelsProvider = mock(IdeModifiableModelsProvider.class);

    myModuleSetup.setUpModule(myModule, modelsProvider, myJavaModel, myModuleModels, null, true /* sync skipped */);

    // Only 'mySetupStep1' should be invoked when sync is skipped.
    verify(mySetupStep1, times(1)).setUpModule(myModule, modelsProvider, myJavaModel, myModuleModels, null);
    verify(mySetupStep2, never()).setUpModule(myModule, modelsProvider, myJavaModel, myModuleModels, null);
  }

  public void testSetUpAndroidModuleWithSyncNotSkipped() {
    when(mySetupStep1.invokeOnSkippedSync()).thenReturn(true);
    IdeModifiableModelsProvider modelsProvider = mock(IdeModifiableModelsProvider.class);

    myModuleSetup.setUpModule(myModule, modelsProvider, myJavaModel, myModuleModels, null, false /* sync not skipped */);

    // Only 'mySetupStep1' should be invoked when sync is skipped.
    verify(mySetupStep1, times(1)).setUpModule(myModule, modelsProvider, myJavaModel, myModuleModels, null);
    verify(mySetupStep2, times(1)).setUpModule(myModule, modelsProvider, myJavaModel, myModuleModels, null);
  }

  public void testSetUpAndroidModuleWithoutVariants() {
    IdeModifiableModelsProvider modelsProvider = new IdeModifiableModelsProviderImpl(getProject());

    when(myJavaModel.isAndroidModuleWithoutVariants()).thenReturn(true);

    Module module = getModule();
    // Add AndroidFacet to verify that is removed.
    createAndAddAndroidFacet(module);

    ApplicationManager.getApplication().runWriteAction(() -> {
      // Add source folders and excluded folders to verify that they are removed.
      ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(module).getModifiableModel();
      ContentEntry contentEntry = modifiableModel.addContentEntry("file://fakePath");
      contentEntry.addSourceFolder("file://fakePath/sourceFolder", false);
      contentEntry.addExcludeFolder("file://fakePath/excludedFolder");
      modifiableModel.commit();
    });

    myModuleSetup.setUpModule(module, modelsProvider, myJavaModel, myModuleModels, null, false);
    ApplicationManager.getApplication().runWriteAction(modelsProvider::commit);

    // Verify AndroidFacet was removed.
    assertNull(AndroidFacet.getInstance(module));

    // Verify source folders and excluded folders were removed.
    ContentEntry[] contentEntries = ModuleRootManager.getInstance(module).getContentEntries();
    assertThat(contentEntries).hasLength(1);

    ContentEntry contentEntry = contentEntries[0];
    assertThat(contentEntry.getSourceFolders()).isEmpty();
    assertThat(contentEntry.getExcludeFolderUrls()).isEmpty();
  }
}