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

import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.sync.setup.module.GradleModuleSetup;
import com.android.tools.idea.gradle.stubs.gradle.GradleProjectStub;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.PlatformTestCase;
import org.gradle.tooling.model.GradleProject;
import org.mockito.Mock;

import java.util.Collection;
import java.util.Collections;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.GRADLE_MODULE_MODEL;
import static java.util.Collections.emptyList;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link GradleModuleModelDataService}.
 */
public class GradleModuleModelDataServiceTest extends PlatformTestCase {
  @Mock private GradleModuleSetup myModuleSetup;

  private IdeModifiableModelsProvider myModelsProvider;
  private GradleModuleModelDataService myDataService;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    myModelsProvider = new IdeModifiableModelsProviderImpl(getProject());
    myDataService = new GradleModuleModelDataService(myModuleSetup);
  }

  public void testGetTargetDataKey() {
    assertSame(GRADLE_MODULE_MODEL, myDataService.getTargetDataKey());
  }

  public void testImportData() {
    String appModuleName = "app";
    Module appModule = createModule(appModuleName);

    GradleProject gradleProjectStub = new GradleProjectStub(emptyList(), ":app", getBaseDirPath(getProject()));
    GradleModuleModel model = new GradleModuleModel(appModuleName, gradleProjectStub, emptyList(), null, null, null, null);

    DataNode<GradleModuleModel> dataNode = new DataNode<>(GRADLE_MODULE_MODEL, model, null);
    Collection<DataNode<GradleModuleModel>> dataNodes = Collections.singleton(dataNode);

    myDataService.importData(dataNodes, null, getProject(), myModelsProvider);

    verify(myModuleSetup).setUpModule(appModule, myModelsProvider, model);
  }
}