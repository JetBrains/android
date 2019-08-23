/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.actions;

import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.ProjectStructure;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.AndroidModelFeatures;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.run.OutputBuildAction;
import com.android.tools.idea.gradle.stubs.gradle.GradleProjectStub;
import com.android.tools.idea.testing.Facets;
import com.android.tools.idea.testing.IdeComponents;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.PlatformTestCase;
import org.gradle.tooling.model.GradleProject;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.android.builder.model.AndroidProject.PROJECT_TYPE_DYNAMIC_FEATURE;
import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.testing.Facets.createAndAddGradleFacet;
import static java.util.Collections.emptyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link BuildApkAction}.
 */
public class BuildApkActionTest extends PlatformTestCase {
  @Mock private GradleProjectInfo myGradleProjectInfo;
  @Mock private GradleBuildInvoker myBuildInvoker;
  @Mock private ProjectStructure myProjectStructure;
  @Mock private AndroidModuleModel myAndroidModel;
  @Mock private IdeAndroidProject myIdeAndroidProject;
  @Mock private AndroidModuleModel myAndroidModel2;
  @Mock private IdeAndroidProject myIdeAndroidProject2;
  private BuildApkAction myAction;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    new IdeComponents(myProject).replaceProjectService(GradleBuildInvoker.class, myBuildInvoker);
    new IdeComponents(myProject).replaceProjectService(GradleProjectInfo.class, myGradleProjectInfo);
    new IdeComponents(myProject).replaceProjectService(ProjectStructure.class, myProjectStructure);
    myAction = new BuildApkAction();
  }

  public void testActionPerformed() {
    Module app1Module = createModule("app1");
    Module app2Module = createModule("app2");

    Module[] appModules = {app1Module, app2Module};
    when(myProjectStructure.getAppModules()).thenReturn(ImmutableList.copyOf(appModules));
    when(myGradleProjectInfo.isBuildWithGradle()).thenReturn(true);

    AnActionEvent event = mock(AnActionEvent.class);
    when(event.getProject()).thenReturn(getProject());

    myAction.actionPerformed(event);

    verify(myBuildInvoker).assemble(eq(appModules), eq(TestCompileType.ALL), eq(emptyList()), any(OutputBuildAction.class));
  }

  public void testActionPerformedForDynamicApp() {
    // Setup "app" as a dynamic app with a single feature "feature1"
    Module appModule = createModule("app");
    setUpModuleAsAndroidModule(appModule, myAndroidModel, myIdeAndroidProject);

    Module featureModule = createModule("feature1");
    setUpModuleAsAndroidModule(featureModule, myAndroidModel2, myIdeAndroidProject2);
    when(myIdeAndroidProject.getDynamicFeatures()).thenReturn(ImmutableList.of(":feature1"));
    when(myIdeAndroidProject2.getProjectType()).thenReturn(PROJECT_TYPE_DYNAMIC_FEATURE);

    when(myProjectStructure.getAppModules()).thenReturn(ImmutableList.of(appModule));
    when(myGradleProjectInfo.isBuildWithGradle()).thenReturn(true);

    AnActionEvent event = mock(AnActionEvent.class);
    when(event.getProject()).thenReturn(getProject());

    myAction.actionPerformed(event);

    Module[] allModules = {appModule, featureModule};
    verify(myBuildInvoker).assemble(eq(allModules), eq(TestCompileType.ALL), eq(emptyList()), any(OutputBuildAction.class));
  }

  private static void setUpModuleAsAndroidModule(Module module, AndroidModuleModel androidModel, IdeAndroidProject ideAndroidProject) {
    setUpModuleAsGradleModule(module);

    when(androidModel.getAndroidProject()).thenReturn(ideAndroidProject);

    AndroidModelFeatures androidModelFeatures = mock(AndroidModelFeatures.class);
    when(androidModel.getFeatures()).thenReturn(androidModelFeatures);

    AndroidFacet androidFacet = Facets.createAndAddAndroidFacet(module);
    androidFacet.getConfiguration().setModel(androidModel);
  }

  private static void setUpModuleAsGradleModule(@NotNull Module module) {
    GradleFacet gradleFacet = createAndAddGradleFacet(module);
    String gradlePath = GRADLE_PATH_SEPARATOR + module.getName();
    gradleFacet.getConfiguration().GRADLE_PROJECT_PATH = gradlePath;

    GradleProject gradleProjectStub = new GradleProjectStub(emptyList(), gradlePath, getBaseDirPath(module.getProject()));
    GradleModuleModel model = new GradleModuleModel(module.getName(), gradleProjectStub, emptyList(), null, null, null, null);

    gradleFacet.setGradleModuleModel(model);
  }
}
