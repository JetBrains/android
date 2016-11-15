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

import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.setup.module.AndroidModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.project.PostSyncProjectSetupStep;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.gradle.project.sync.validation.android.AndroidModuleValidator;
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
 * Tests for {@link AndroidModuleModelDataService}.
 */
public class AndroidModuleModelDataServiceTest extends AndroidGradleTestCase {
  @Mock private AndroidModuleSetup myModuleSetup;
  @Mock private AndroidModuleValidator myValidator;
  @Mock private PostSyncProjectSetupStep myProjectSetupStep1;
  @Mock private PostSyncProjectSetupStep myProjectSetupStep2;

  private AndroidModuleModelDataService myService;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    initMocks(this);

    AndroidModuleValidator.Factory validatorFactory = mock(AndroidModuleValidator.Factory.class);
    when(validatorFactory.create(getProject())).thenReturn(myValidator);

    PostSyncProjectSetupStep[] projectSetupSteps = {myProjectSetupStep1, myProjectSetupStep2};

    myService = new AndroidModuleModelDataService(myModuleSetup, validatorFactory, projectSetupSteps);
  }

  public void testGetTargetDataKey() {
    assertSame(ANDROID_MODEL, myService.getTargetDataKey());
  }

  public void testImportData() throws Exception {
    loadSimpleApplication();
    Module appModule = myModules.getAppModule();

    AndroidModuleModel androidModel = AndroidModuleModel.get(appModule);
    assertNotNull(androidModel);

    DataNode<AndroidModuleModel> dataNode = new DataNode<>(ANDROID_MODEL, androidModel, null);
    Project project = getProject();
    IdeModifiableModelsProvider modelsProvider = new IdeModifiableModelsProviderImpl(project);

    myService.importData(Lists.newArrayList(dataNode), mock(ProjectData.class), project, modelsProvider);

    verify(myModuleSetup).setUpModule(appModule, modelsProvider, androidModel, null, null);
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
    AndroidModuleModel androidModel = mock(AndroidModuleModel.class);
    verify(myModuleSetup, never()).setUpModule(module, modelsProvider, androidModel, null, null);
    verify(myValidator, never()).validate(module, androidModel);
    verify(myValidator, never()).fixAndReportFoundIssues();
    verify(myProjectSetupStep1, never()).setUpProject(project, modelsProvider, null);
    verify(myProjectSetupStep2, never()).setUpProject(project, modelsProvider, null);
  }
}