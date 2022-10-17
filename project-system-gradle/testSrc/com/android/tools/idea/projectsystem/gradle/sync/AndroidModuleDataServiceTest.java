/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.projectsystem.gradle.sync;

import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.ANDROID_MODEL;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.android.tools.idea.gradle.project.model.GradleAndroidModelData;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.gradle.project.sync.validation.android.AndroidModuleValidator;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.ProjectFiles;
import com.android.tools.idea.testing.TestModuleUtil;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.util.Collections;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.mockito.Mock;

/**
 * Tests for {@link AndroidModuleDataService}.
 */
public class AndroidModuleDataServiceTest extends AndroidGradleTestCase {
  @Mock private AndroidModuleValidator myValidator;
  @Mock private ModuleSetupContext.Factory myModuleSetupContextFactory;
  @Mock private ModuleSetupContext myModuleSetupContext;
  private IdeModifiableModelsProvider myModelsProvider;

  private AndroidModuleDataService myService;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    AndroidModuleValidator.Factory validatorFactory = mock(AndroidModuleValidator.Factory.class);
    when(validatorFactory.create(getProject())).thenReturn(myValidator);

    myService = new AndroidModuleDataService(validatorFactory);
    myModelsProvider = ProjectDataManager.getInstance().createModifiableModelsProvider(getProject());
  }

  @Override
  public void tearDown() throws Exception {
    try {
      myModelsProvider.dispose();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testGetTargetDataKey() {
    assertSame(ANDROID_MODEL, myService.getTargetDataKey());
  }

  public void testImportData() throws Exception {
    loadSimpleApplication();
    Module appModule = TestModuleUtil.findAppModule(getProject());

    GradleAndroidModel androidModel = GradleAndroidModel.get(appModule);
    assertNotNull(androidModel);

    ExternalProjectInfo externalInfo =
      ProjectDataManager.getInstance().getExternalProjectData(getProject(), GradleConstants.SYSTEM_ID, getProjectFolderPath().getPath());
    assertNotNull("Initial import failed", externalInfo);
    DataNode<ProjectData> projectStructure = externalInfo.getExternalProjectStructure();
    assertNotNull("No project structure was found", projectStructure);

    //noinspection unchecked
    DataNode<GradleAndroidModelData> androidModelNode = (DataNode<GradleAndroidModelData>)ExternalSystemApiUtil
      .findFirstRecursively(projectStructure, (node) -> ANDROID_MODEL.equals(node.getKey()));
    Project project = getProject();

    when(myModuleSetupContextFactory.create(appModule, myModelsProvider)).thenReturn(myModuleSetupContext);
    myService.importData(Collections.singletonList(androidModelNode), mock(ProjectData.class), project, myModelsProvider);

    assertNotNull(FacetManager.getInstance(appModule).findFacet(AndroidFacet.ID, AndroidFacet.NAME));
    verify(myValidator).validate(same(appModule), argThat(it -> it.getData().equals(androidModel.getData())));
    verify(myValidator).fixAndReportFoundIssues();
  }

  public void testImportDataWithoutModels() {
    Module appModule = ProjectFiles.createModule(getProject(), "app");
    FacetManager.getInstance(appModule).createFacet(AndroidFacet.getFacetType(), AndroidFacet.NAME, null);
    IdeModifiableModelsProvider modelsProvider = ProjectDataManager.getInstance().createModifiableModelsProvider(getProject());

    myService.importData(Collections.emptyList(), getProject(), modelsProvider, Collections.emptyMap());
    assertNull(FacetManager.getInstance(appModule).findFacet(AndroidFacet.ID, AndroidFacet.NAME));
  }

  public void testOnSuccessSetsNewProjectToFalse() throws Exception {
    loadSimpleApplication();
    GradleProjectInfo gradleProjectInfo = GradleProjectInfo.getInstance(getProject());
    assertFalse(gradleProjectInfo.isNewProject());
  }
}