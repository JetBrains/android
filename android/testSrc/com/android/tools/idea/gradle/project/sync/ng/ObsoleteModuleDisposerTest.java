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
package com.android.tools.idea.gradle.project.sync.ng;

import com.android.tools.idea.gradle.project.sync.setup.module.ModuleDisposer;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.IdeaTestCase;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.gradle.project.sync.ng.AndroidModuleProcessor.MODULE_GRADLE_MODELS_KEY;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link ObsoleteModuleDisposer}.
 */
public class ObsoleteModuleDisposerTest extends IdeaTestCase {
  @Mock private IdeModifiableModelsProvider myModelsProvider;
  @Mock private ModuleDisposer myModuleDisposer;

  private Module myModule1;
  private Module myModule2;
  private Module myModule3;
  private ProgressIndicator myIndicator;

  private ObsoleteModuleDisposer myObsoleteModuleDisposer;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    myModule1 = createModule("module1");
    myModule2 = createModule("module2");
    myModule3 = createModule("module3");

    when(myModelsProvider.getModules()).thenReturn(new Module[]{myModule1, myModule2, myModule3});

    myIndicator = new EmptyProgressIndicator();
    myObsoleteModuleDisposer = new ObsoleteModuleDisposer(getProject(), myModelsProvider, myModuleDisposer);
  }

  public void testDisposedObsoleteModules() {
    // Only myModule2 will not have models, making it obsolete and ready for disposal.
    myModule1.putUserData(MODULE_GRADLE_MODELS_KEY, mock(SyncAction.ModuleModels.class));
    myModule3.putUserData(MODULE_GRADLE_MODELS_KEY, mock(SyncAction.ModuleModels.class));

    Project project = getProject();
    when(myModuleDisposer.canDisposeModules(project)).thenReturn(true);

    myObsoleteModuleDisposer.disposeObsoleteModules(myIndicator);

    List<Module> expectedDisposedModel = new ArrayList<>();
    expectedDisposedModel.add(myModule2);
    verify(myModuleDisposer).disposeModules(eq(expectedDisposedModel), same(project), same(myModelsProvider));
  }

  public void testDisposedObsoleteModulesWhenModuleDisposalIsNotPossible() {
    Project project = getProject();
    when(myModuleDisposer.canDisposeModules(project)).thenReturn(false);

    myObsoleteModuleDisposer.disposeObsoleteModules(myIndicator);

    // If disposal is not possible, 'myModuleDisposer' should not be invoked.
    verify(myModuleDisposer, never()).disposeModules(anyList(), same(project), same(myModelsProvider));
  }
}