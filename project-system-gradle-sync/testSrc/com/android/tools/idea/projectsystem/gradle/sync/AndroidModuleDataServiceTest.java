/*
 * Copyright (C) 2020 The Android Open Source Project
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.gradle.project.sync.validation.android.AndroidModuleValidator;
import com.android.tools.idea.gradle.run.MakeBeforeRunTaskProvider;
import com.android.tools.idea.testartifacts.junit.AndroidJUnitConfiguration;
import com.android.tools.idea.testartifacts.junit.AndroidJUnitConfigurationType;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.AndroidGradleTestUtilsKt;
import com.android.tools.idea.testing.AndroidModuleModelBuilder;
import com.android.tools.idea.testing.AndroidProjectBuilder;
import com.android.tools.idea.testing.ProjectFiles;
import com.android.tools.idea.testing.TestModuleUtil;
import com.google.common.collect.ImmutableList;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
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

    AndroidModuleModel androidModel = AndroidModuleModel.get(appModule);
    assertNotNull(androidModel);

    DataNode<AndroidModuleModel> dataNode = new DataNode<>(ANDROID_MODEL, androidModel, null);
    Project project = getProject();

    when(myModuleSetupContextFactory.create(appModule, myModelsProvider)).thenReturn(myModuleSetupContext);
    myService.importData(Collections.singletonList(dataNode), mock(ProjectData.class), project, myModelsProvider);

    assertNotNull(FacetManager.getInstance(appModule).findFacet(AndroidFacet.ID, AndroidFacet.NAME));
    verify(myValidator).validate(appModule, androidModel);
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
    assertFalse(gradleProjectInfo.isImportedProject());
    assertFalse(gradleProjectInfo.isNewProject());
  }

  public void testOnSuccessJUnitRunConfigurationSetup() throws Exception {
    AndroidGradleTestUtilsKt.setupTestProjectFromAndroidModel(
      getProject(),
      new File(getProject().getBasePath()),
      new AndroidModuleModelBuilder(":", "debug", new AndroidProjectBuilder()));

    RunManagerEx runManager = RunManagerImpl.getInstanceImpl(getProject());

    new AndroidModuleDataService().onSuccessImport(
      ImmutableList.of(), null, getProject(), mock(IdeModifiableModelsProvider.class)
    );
    ConfigurationFactory configurationFactory = AndroidJUnitConfigurationType.getInstance().getConfigurationFactories()[0];
    Project project = getProject();
    AndroidJUnitConfiguration jUnitConfiguration = new AndroidJUnitConfiguration(project, configurationFactory);
    RunnerAndConfigurationSettings settings = runManager.createConfiguration(jUnitConfiguration, configurationFactory);
    settings.storeInDotIdeaFolder();
    runManager.addConfiguration(settings);

    List<RunConfiguration> junitRunConfigurations = runManager.getConfigurationsList(AndroidJUnitConfigurationType.getInstance());
    for (RunConfiguration runConfiguration : junitRunConfigurations) {
      assertSize(1, runManager.getBeforeRunTasks(runConfiguration));
      assertEquals(MakeBeforeRunTaskProvider.ID, runManager.getBeforeRunTasks(runConfiguration).get(0).getProviderId());
    }

    RunConfiguration runConfiguration = junitRunConfigurations.get(0);
    List<BeforeRunTask> tasks = new LinkedList<>(runManager.getBeforeRunTasks(runConfiguration));

    MakeBeforeRunTaskProvider taskProvider = new MakeBeforeRunTaskProvider(project);
    BeforeRunTask newTask = taskProvider.createTask(runConfiguration);
    newTask.setEnabled(true);
    tasks.add(newTask);
    runManager.setBeforeRunTasks(runConfiguration, tasks);

    new AndroidModuleDataService().onSuccessImport(
      ImmutableList.of(), null, getProject(), mock(IdeModifiableModelsProvider.class)
    );
    assertSize(2, runManager.getBeforeRunTasks(runConfiguration));
  }
}