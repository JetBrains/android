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

import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.project.sync.setup.module.AndroidModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.setup.project.PostSyncProjectSetupStep;
import com.android.tools.idea.gradle.project.sync.validation.AndroidProjectValidator;
import com.android.tools.idea.testing.legacy.AndroidGradleTestCase;
import com.google.common.collect.Lists;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.mockito.Mock;

import java.util.Collections;

import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.ANDROID_MODEL;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link AndroidGradleModelDataService}.
 */
public class AndroidGradleModelDataServiceTest extends AndroidGradleTestCase {
  @Mock private AndroidModuleSetupStep myModuleSetupStep1;
  @Mock private AndroidModuleSetupStep myModuleSetupStep2;
  @Mock private AndroidProjectValidator myValidator;
  @Mock private PostSyncProjectSetupStep myProjectSetupStep1;
  @Mock private PostSyncProjectSetupStep myProjectSetupStep2;

  private AndroidGradleModelDataService myService;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    initMocks(this);

    AndroidModuleSetupStep[] moduleSetupSteps = {myModuleSetupStep1, myModuleSetupStep2};

    AndroidProjectValidator.Factory validatorFactory = mock(AndroidProjectValidator.Factory.class);
    when(validatorFactory.create(getProject())).thenReturn(myValidator);

    PostSyncProjectSetupStep[] projectSetupSteps = {myProjectSetupStep1, myProjectSetupStep2};

    myService = new AndroidGradleModelDataService(moduleSetupSteps, validatorFactory, projectSetupSteps);
  }

  public void testImportData() throws Exception {
    loadSimpleApplication();
    Module appModule = myModules.getAppModule();

    AndroidGradleModel androidModel = AndroidGradleModel.get(appModule);
    assertNotNull(androidModel);

    DataNode<AndroidGradleModel> dataNode = new DataNode<>(ANDROID_MODEL, androidModel, null);
    Project project = getProject();
    IdeModifiableModelsProvider modelsProvider = new IdeModifiableModelsProviderImpl(project);

    myService.importData(Lists.newArrayList(dataNode), mock(ProjectData.class), project, modelsProvider);

    verify(myModuleSetupStep1).setUpModule(appModule, modelsProvider, androidModel, null, null);
    verify(myModuleSetupStep2).setUpModule(appModule, modelsProvider, androidModel, null, null);
    verify(myValidator).validate(appModule, androidModel);
    verify(myValidator).fixAndReportFoundIssues();
    verify(myProjectSetupStep1).setUpProject(project, modelsProvider, null);
    verify(myProjectSetupStep2).setUpProject(project, modelsProvider, null);
  }

  public void testImportDataWithEmptyDataNodes() throws Exception {
    Project project = getProject();
    IdeModifiableModelsProvider modelsProvider = new IdeModifiableModelsProviderImpl(project);

    myService.importData(Collections.emptyList(), mock(ProjectData.class), project, modelsProvider);

    Module module = mock(Module.class);
    AndroidGradleModel androidModel = mock(AndroidGradleModel.class);
    verify(myModuleSetupStep1, never()).setUpModule(module, modelsProvider, androidModel, null, null);
    verify(myModuleSetupStep2, never()).setUpModule(module, modelsProvider, androidModel, null, null);
    verify(myValidator, never()).validate(module, androidModel);
    verify(myValidator, never()).fixAndReportFoundIssues();
    verify(myProjectSetupStep1, never()).setUpProject(project, modelsProvider, null);
    verify(myProjectSetupStep2, never()).setUpProject(project, modelsProvider, null);
  }
}