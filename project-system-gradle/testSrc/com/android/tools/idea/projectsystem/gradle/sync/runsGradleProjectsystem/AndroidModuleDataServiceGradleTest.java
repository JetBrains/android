/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.projectsystem.gradle.sync.runsGradleProjectsystem;

import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.ANDROID_MODEL;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.model.GradleAndroidDependencyModel;
import com.android.tools.idea.gradle.project.model.GradleAndroidModelData;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.gradle.project.sync.validation.android.AndroidModuleValidator;
import com.android.tools.idea.projectsystem.gradle.sync.AndroidModuleDataService;
import com.android.tools.idea.testing.AndroidGradleProjectRule;
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
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.RunsInEdt;
import java.util.Collections;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.mockito.Mock;

@RunsInEdt
public class AndroidModuleDataServiceGradleTest {

  @Mock private AndroidModuleValidator myValidator;
  @Mock private ModuleSetupContext.Factory myModuleSetupContextFactory;
  @Mock private ModuleSetupContext myModuleSetupContext;
  private IdeModifiableModelsProvider myModelsProvider;

  private AndroidModuleDataService myService;

  public AndroidGradleProjectRule projectRule = new AndroidGradleProjectRule();

  @Rule
  public RuleChain rule = RuleChain.outerRule(new EdtRule()).around(projectRule);

  @Before
  public void setup() throws Exception {
    initMocks(this);

    AndroidModuleValidator.Factory validatorFactory = mock(AndroidModuleValidator.Factory.class);
    when(validatorFactory.create(projectRule.getProject())).thenReturn(myValidator);

    myService = new AndroidModuleDataService(validatorFactory);
    myModelsProvider = ProjectDataManager.getInstance().createModifiableModelsProvider(projectRule.getProject());
    projectRule.loadProject(SIMPLE_APPLICATION);
  }

  @After
  public void tearDown() throws Exception {
    myModelsProvider.dispose();
  }

  @Test
  public void testImportData() throws Exception {
    Project project = projectRule.getProject();
    Module appModule = TestModuleUtil.findAppModule(project);

    GradleAndroidDependencyModel androidModel = GradleAndroidDependencyModel.get(appModule);
    assertThat(androidModel).isNotNull();

    ExternalProjectInfo externalInfo =
      ProjectDataManager.getInstance().getExternalProjectData(project, GradleConstants.SYSTEM_ID, project.getBasePath());
    assertThat(externalInfo).named("Initial import failed").isNotNull();
    DataNode<ProjectData> projectStructure = externalInfo.getExternalProjectStructure();
    assertThat(projectStructure).named("No project structure was found").isNotNull();

    //noinspection unchecked
    DataNode<GradleAndroidModelData> androidModelNode = (DataNode<GradleAndroidModelData>)ExternalSystemApiUtil
      .findFirstRecursively(projectStructure, (node) -> ANDROID_MODEL.equals(node.getKey()));

    when(myModuleSetupContextFactory.create(appModule, myModelsProvider)).thenReturn(myModuleSetupContext);
    myService.importData(Collections.singletonList(androidModelNode), mock(ProjectData.class), project, myModelsProvider);

    assertThat(FacetManager.getInstance(appModule).findFacet(AndroidFacet.ID, AndroidFacet.NAME)).isNotNull();
    verify(myValidator).validate(same(appModule), argThat(it -> ((GradleAndroidDependencyModel) it).containsTheSameDataAs(androidModel)));
    verify(myValidator).fixAndReportFoundIssues();
  }

  @Test
  public void testOnSuccessSetsNewProjectToFalse() throws Exception {
    GradleProjectInfo gradleProjectInfo = GradleProjectInfo.getInstance(projectRule.getProject());
    assertThat(gradleProjectInfo.isNewProject()).isFalse();
  }
}
