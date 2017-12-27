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
import com.android.tools.idea.gradle.project.sync.setup.module.AndroidModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.setup.module.android.AndroidModuleCleanupStep;
import com.android.tools.idea.gradle.project.sync.setup.module.android.ContentRootsModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.setup.module.android.DependenciesAndroidModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.validation.android.AndroidModuleValidator;
import com.android.tools.idea.testing.AndroidGradleTestCase;
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
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link AndroidModuleModelDataService}.
 */
public class AndroidModuleModelDataServiceTest extends AndroidGradleTestCase {
  @Mock private AndroidModuleSetup myModuleSetup;
  @Mock private AndroidModuleValidator myValidator;
  @Mock private AndroidModuleCleanupStep myCleanupStep;

  private AndroidModuleModelDataService myService;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    AndroidModuleValidator.Factory validatorFactory = mock(AndroidModuleValidator.Factory.class);
    when(validatorFactory.create(getProject())).thenReturn(myValidator);

    myService = new AndroidModuleModelDataService(myModuleSetup, validatorFactory, myCleanupStep);
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

    verify(myModuleSetup).setUpModule(appModule, modelsProvider, androidModel, null, null, false);
    verify(myValidator).validate(appModule, androidModel);
    verify(myValidator).fixAndReportFoundIssues();
  }

  public void testImportDataWithoutModels() {
    Module appModule = createModule("app");
    IdeModifiableModelsProvider modelsProvider = new IdeModifiableModelsProviderImpl(getProject());

    myService.importData(Collections.emptyList(), getProject(), modelsProvider, Collections.emptyMap());
    verify(myCleanupStep).cleanUpModule(appModule, modelsProvider);
  }

  public void testOnModelsNotFound() {
    Module appModule = createModule("app");
    IdeModifiableModelsProvider modelsProvider = new IdeModifiableModelsProviderImpl(getProject());
    myService.onModelsNotFound(modelsProvider);
    verify(myCleanupStep).cleanUpModule(appModule, modelsProvider);
  }

  public void testAndroidModuleSetupSteps() {
    myService = new AndroidModuleModelDataService();

    int indexOfContentRootsModuleSetupStep = -1;
    int indexOfDependenciesModuleSetupStep = -1;
    AndroidModuleSetupStep[] setupSteps = myService.getModuleSetup().getSetupSteps();
    for (int i = 0; i < setupSteps.length; i++) {
      AndroidModuleSetupStep setupStep = setupSteps[i];
      if (setupStep instanceof ContentRootsModuleSetupStep) {
        indexOfContentRootsModuleSetupStep = i;
        continue;
      }
      if (setupStep instanceof DependenciesAndroidModuleSetupStep) {
        indexOfDependenciesModuleSetupStep = i;
      }
    }

    // ContentRootsModuleSetupStep should go before DependenciesModuleSetupStep, otherwise any excluded jars set up by
    // DependenciesModuleSetupStep will be ignored.
    assertThat(indexOfContentRootsModuleSetupStep).isLessThan(indexOfDependenciesModuleSetupStep);
  }
}