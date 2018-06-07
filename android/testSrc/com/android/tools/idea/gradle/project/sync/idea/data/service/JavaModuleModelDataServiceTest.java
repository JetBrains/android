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

import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.gradle.project.sync.setup.module.idea.JavaModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.java.JavaModuleCleanupStep;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.IdeaTestCase;
import org.mockito.Mock;

import java.util.Collection;
import java.util.Collections;

import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.JAVA_MODULE_MODEL;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link JavaModuleModelDataService}.
 */
public class JavaModuleModelDataServiceTest extends IdeaTestCase {
  @Mock private JavaModuleSetup myModuleSetup;
  @Mock private JavaModuleCleanupStep myCleanupStep;
  @Mock private GradleSyncState mySyncState;
  @Mock private ModuleSetupContext.Factory myModuleSetupContextFactory;
  @Mock private ModuleSetupContext myModuleSetupContext;

  private IdeModifiableModelsProvider myModelsProvider;
  private JavaModuleModelDataService myService;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    IdeComponents.replaceService(getProject(), GradleSyncState.class, mySyncState);
    myModelsProvider = new IdeModifiableModelsProviderImpl(getProject());
    myService = new JavaModuleModelDataService(myModuleSetupContextFactory, myModuleSetup, myCleanupStep);
  }

  public void testGetTargetDataKey() {
    assertSame(JAVA_MODULE_MODEL, myService.getTargetDataKey());
  }

  public void testImportDataWithoutModels() {
    Module appModule = createModule("app");
    IdeModifiableModelsProvider modelsProvider = new IdeModifiableModelsProviderImpl(getProject());

    myService.importData(Collections.emptyList(), getProject(), modelsProvider, Collections.emptyMap());
    verify(myCleanupStep).cleanUpModule(appModule, modelsProvider);
  }

  public void testImportData() {
    String appModuleName = "app";
    Module appModule = createModule(appModuleName);

    JavaModuleModel model = mock(JavaModuleModel.class);
    when(model.getModuleName()).thenReturn(appModuleName);

    DataNode<JavaModuleModel> dataNode = new DataNode<>(JAVA_MODULE_MODEL, model, null);
    Collection<DataNode<JavaModuleModel>> dataNodes = Collections.singleton(dataNode);

    when(myModuleSetupContextFactory.create(appModule, myModelsProvider)).thenReturn(myModuleSetupContext);
    myService.importData(dataNodes, null, getProject(), myModelsProvider);

    verify(mySyncState).isSyncSkipped();
    verify(myModuleSetup).setUpModule(myModuleSetupContext, model, false);
  }

  public void testOnModelsNotFound() {
    IdeModifiableModelsProvider modelsProvider = new IdeModifiableModelsProviderImpl(getProject());
    myService.onModelsNotFound(modelsProvider);
    verify(myCleanupStep).cleanUpModule(myModule, modelsProvider);
  }
}