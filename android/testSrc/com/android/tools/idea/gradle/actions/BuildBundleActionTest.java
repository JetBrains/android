/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.ide.common.gradle.model.IdeAndroidArtifact;
import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.ide.common.gradle.model.IdeVariant;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.ProjectStructure;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.AndroidModelFeatures;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.run.OutputBuildAction;
import com.android.tools.idea.testing.Facets;
import com.android.tools.idea.testing.IdeComponents;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import java.util.Collections;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.android.tools.idea.testing.Facets.createAndAddGradleFacet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link BuildApkAction}.
 */
public class BuildBundleActionTest extends IdeaTestCase {
  @Mock private GradleProjectInfo myGradleProjectInfo;
  @Mock private GradleBuildInvoker myBuildInvoker;
  @Mock private ProjectStructure myProjectStructure;
  @Mock private AndroidModuleModel myAndroidModel;
  @Mock private IdeAndroidProject myIdeAndroidProject;
  @Mock private IdeVariant myIdeVariant;
  @Mock private IdeAndroidArtifact myMainArtifact;
  private BuildBundleAction myAction;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    new IdeComponents(myProject).replaceProjectService(GradleBuildInvoker.class, myBuildInvoker);
    new IdeComponents(myProject).replaceProjectService(GradleProjectInfo.class, myGradleProjectInfo);
    new IdeComponents(myProject).replaceProjectService(ProjectStructure.class, myProjectStructure);
    myAction = new BuildBundleAction();
  }

  public void testActionPerformed() {
    Module appModule = createModule("app1");
    setUpModuleAsAndroidModule(appModule, myAndroidModel, myIdeAndroidProject, myIdeVariant, myMainArtifact);
    // Ignore return value, as we just want to make sure the "bundle" action does not apply to all modules
    createModule("app2");

    Module[] appModules = {appModule};
    when(myProjectStructure.getAppModules()).thenReturn(ImmutableList.copyOf(appModules));
    when(myGradleProjectInfo.isBuildWithGradle()).thenReturn(true);

    AnActionEvent event = mock(AnActionEvent.class);
    when(event.getProject()).thenReturn(getProject());

    myAction.actionPerformed(event);

    verify(myBuildInvoker).bundle(eq(appModules), eq(Collections.emptyList()), any(OutputBuildAction.class));
  }

  private static void setUpModuleAsAndroidModule(@NotNull Module module,
                                                 @NotNull AndroidModuleModel androidModel,
                                                 @NotNull IdeAndroidProject ideAndroidProject,
                                                 @NotNull IdeVariant ideVariant,
                                                 @NotNull IdeAndroidArtifact mainArtifact) {
    setUpModuleAsGradleModule(module);

    when(androidModel.getAndroidProject()).thenReturn(ideAndroidProject);
    when(androidModel.getSelectedVariant()).thenReturn(ideVariant);
    when(ideVariant.getMainArtifact()).thenReturn(mainArtifact);
    when(mainArtifact.getBundleTaskName()).thenReturn("bundleDebug");

    AndroidModelFeatures androidModelFeatures = mock(AndroidModelFeatures.class);
    when(androidModel.getFeatures()).thenReturn(androidModelFeatures);

    AndroidFacet androidFacet = Facets.createAndAddAndroidFacet(module);
    androidFacet.getConfiguration().setModel(androidModel);
  }

  private static void setUpModuleAsGradleModule(@NotNull Module module) {
    GradleFacet gradleFacet = createAndAddGradleFacet(module);
    gradleFacet.getConfiguration().GRADLE_PROJECT_PATH = GRADLE_PATH_SEPARATOR + module.getName();

    GradleModuleModel
      model = new GradleModuleModel(module.getName(), Collections.emptyList(), GRADLE_PATH_SEPARATOR + module.getName(), null, null);
    gradleFacet.setGradleModuleModel(model);
  }
}