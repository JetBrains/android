/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.module;

import com.android.tools.idea.gradle.project.sync.setup.post.project.DisposedModules;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import java.io.File;
import java.util.Collections;

import static com.intellij.openapi.util.io.FileUtilRt.toSystemDependentName;
import static org.mockito.Matchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link ModuleDisposer}.
 */
public class ModuleDisposerTest extends IdeaTestCase {
  @Mock private DisposedModules myDisposedModules;

  private ModuleDisposer myModuleDisposer;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    DisposedModules.setFactory(new DisposedModules.Factory() {
      @Override
      @NotNull
      public DisposedModules createNewInstance() {
        return myDisposedModules;
      }
    });

    myModuleDisposer = new ModuleDisposer();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      DisposedModules.restoreFactory();
    }
    finally {
      super.tearDown();
    }
  }

  public void testDisposeModulesAndMarkImlFilesForDeletion() {
    // This module should be disposed.
    Module libModule = createModule("lib");
    File libImlFilePath = new File(toSystemDependentName(libModule.getModuleFilePath()));

    Project project = getProject();
    IdeModifiableModelsProvider modelsProvider = new IdeModifiableModelsProviderImpl(project);

    myModuleDisposer.disposeModulesAndMarkImlFilesForDeletion(Collections.singletonList(libModule), project, modelsProvider);

    // Apply changes
    ApplicationManager.getApplication().runWriteAction(modelsProvider::commit);

    // Make sure module "lib" was disposed and its .iml file was deleted.
    assertTrue(Disposer.isDisposed(libModule));
    verify(myDisposedModules, times(1)).markImlFilesForDeletion(Collections.singletonList(libImlFilePath));
  }

  public void testDisposeModulesAndMarkImlFilesForDeletionWithNoModules() {
    Project project = getProject();
    IdeModifiableModelsProvider modelsProvider = new IdeModifiableModelsProviderImpl(project);

    myModuleDisposer.disposeModulesAndMarkImlFilesForDeletion(Collections.emptyList(), project, modelsProvider);

    // Apply changes
    ApplicationManager.getApplication().runWriteAction(modelsProvider::commit);

    //noinspection unchecked
    verify(myDisposedModules, never()).markImlFilesForDeletion(anyList());
  }
}