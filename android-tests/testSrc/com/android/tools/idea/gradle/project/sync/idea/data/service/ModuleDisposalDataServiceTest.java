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
package com.android.tools.idea.gradle.project.sync.idea.data.service;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.ImportedModule;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.setup.post.project.DisposedModules;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.IdeaTestCase;
import org.gradle.tooling.model.idea.IdeaModule;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.IMPORTED_MODULE;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static org.mockito.Matchers.anyList;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link ModuleDisposalDataService}.
 */
public class ModuleDisposalDataServiceTest extends IdeaTestCase {
  @Mock private IdeInfo myIdeInfo;
  @Mock private DisposedModules myDisposedModules;

  private GradleSyncState myOriginalSyncState;
  private GradleSyncState mySyncState;
  private ModuleDisposalDataService myDataService;

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

    Project project = getProject();
    myOriginalSyncState = GradleSyncState.getInstance(project);
    mySyncState = IdeComponents.replaceServiceWithMock(project, GradleSyncState.class);
    myDataService = new ModuleDisposalDataService(myIdeInfo);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      DisposedModules.restoreFactory();
      if (myOriginalSyncState != null) {
        IdeComponents.replaceService(getProject(), GradleSyncState.class, myOriginalSyncState);
      }
    }
    finally {
      super.tearDown();
    }
  }

  public void testImportDataWithIdeNotAndroidStudio() {
    when(myIdeInfo.isAndroidStudio()).thenReturn(false);

    myDataService.importData(Collections.emptyList(), null, getProject(), mock(IdeModifiableModelsProvider.class));

    //noinspection unchecked
    verify(myDisposedModules, never()).markImlFilesForDeletion(anyList());
  }

  public void testImportDataWithAndroidStudioAndSyncFailed() {
    when(myIdeInfo.isAndroidStudio()).thenReturn(true);
    when(mySyncState.lastSyncFailedOrHasIssues()).thenReturn(true);

    myDataService.importData(Collections.emptyList(), null, getProject(), mock(IdeModifiableModelsProvider.class));

    //noinspection unchecked
    verify(myDisposedModules, never()).markImlFilesForDeletion(anyList());
  }

  public void testImportDataWithAndroidStudioAndSuccessfulSync() {
    when(myIdeInfo.isAndroidStudio()).thenReturn(true);
    when(mySyncState.lastSyncFailedOrHasIssues()).thenReturn(false);

    IdeaModule moduleModel = mock(IdeaModule.class);
    when(moduleModel.getName()).thenReturn(getModule().getName());
    ImportedModule importedModule = new ImportedModule(moduleModel);

    // This module should be disposed.
    Module libModule = createModule("lib");
    File libImlFilePath = new File(toSystemDependentName(libModule.getModuleFilePath()));

    IdeModifiableModelsProvider modelsProvider = new IdeModifiableModelsProviderImpl(getProject());

    Collection<DataNode<ImportedModule>> nodes = Collections.singleton(new DataNode<>(IMPORTED_MODULE, importedModule, null));
    myDataService.importData(nodes, null, getProject(), modelsProvider);

    // Apply changes
    ApplicationManager.getApplication().runWriteAction(modelsProvider::commit);

    // Make sure module "lib" was disposed and its .iml file was deleted.
    assertTrue(Disposer.isDisposed(libModule));
    verify(myDisposedModules, times(1)).markImlFilesForDeletion(Collections.singletonList(libImlFilePath));
  }
}