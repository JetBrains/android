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

import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.AndroidProject;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.ide.android.IdeAndroidArtifact;
import com.android.tools.idea.gradle.project.model.ide.android.IdeAndroidProject;
import com.android.tools.idea.gradle.run.OutputBuildAction;
import com.android.tools.idea.gradle.stubs.FileStructure;
import com.android.tools.idea.gradle.stubs.android.AndroidArtifactStub;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_APP;
import static com.android.builder.model.AndroidProject.PROJECT_TYPE_INSTANTAPP;
import static com.android.builder.model.AndroidProject.PROJECT_TYPE_LIBRARY;
import static com.android.tools.idea.testing.Facets.createAndAddAndroidFacet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link BuildApkAction}.
 */
public class BuildApkActionTest extends IdeaTestCase {
  @Mock private GradleProjectInfo myGradleProjectInfo;
  @Mock private GradleBuildInvoker myBuildInvoker;
  private BuildApkAction myAction;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    IdeComponents.replaceService(myProject, GradleBuildInvoker.class, myBuildInvoker);
    IdeComponents.replaceService(myProject, GradleProjectInfo.class, myGradleProjectInfo);
    myAction = new BuildApkAction();
  }

  public void testActionPerformed() {
    Module app1Module = createAndroidModule("app1", PROJECT_TYPE_APP);
    Module app2Module = createAndroidModule("app2", PROJECT_TYPE_APP);
    Module instantApp = createAndroidModule("instantApp", PROJECT_TYPE_INSTANTAPP);
    Module[] appModules = {app1Module, app2Module, instantApp};
    createAndroidModule("androidLib", PROJECT_TYPE_LIBRARY);

    when(myGradleProjectInfo.isBuildWithGradle()).thenReturn(true);

    AnActionEvent event = mock(AnActionEvent.class);
    when(event.getProject()).thenReturn(getProject());

    myAction.actionPerformed(event);

    verify(myBuildInvoker).assemble(eq(appModules), eq(TestCompileType.ALL), any(OutputBuildAction.class));
  }

  @NotNull
  private Module createAndroidModule(@NotNull String name, int type) {
    Module module = createModule(name);

    AndroidFacet facet = createAndAddAndroidFacet(module);
    AndroidModuleModel androidModel = mock(AndroidModuleModel.class);
    facet.setAndroidModel(androidModel);

    facet.getConfiguration().getState().ASSEMBLE_TASK_NAME = ":" + name + ":debugAssemble";

    IdeAndroidProject androidProject = mock(IdeAndroidProject.class);
    when(androidProject.getProjectType()).thenReturn(type);
    when(androidModel.getAndroidProject()).thenReturn(androidProject);
    IdeAndroidArtifact mainArtifact =
      new AndroidArtifactStub(AndroidProject.ARTIFACT_MAIN, "f1fa-debug", "debug", new FileStructure(new File("debug"))) {
        @Override
        @NotNull
        public Collection<AndroidArtifactOutput> getOutputs() {
          return Collections.emptyList();
        }
      };
    when(androidModel.getMainArtifact()).thenReturn(mainArtifact);

    return module;
  }
}