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
package com.android.tools.idea.gradle.run;

import com.android.builder.model.InstantAppProjectBuildOutput;
import com.android.builder.model.ProjectBuildOutput;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Arrays;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link OutputBuildAction}.
 */
public class OutputBuildActionTest {
  @Mock private BuildController myBuildController;
  @Mock private GradleBuild myGradleBuild;
  @Mock private BasicGradleProject myRootProject;

  private OutputBuildAction myAction;

  @Before
  public void setUp() {
    initMocks(this);

    when(myBuildController.getBuildModel()).thenReturn(myGradleBuild);
    when(myGradleBuild.getRootProject()).thenReturn(myRootProject);

    myAction = new OutputBuildAction(Arrays.asList("a", "b", "c", "d"));
  }

  @Test
  public void execute() {
    GradleProject root = mock(GradleProject.class);
    when(myBuildController.findModel(myRootProject, GradleProject.class)).thenReturn(root);

    GradleProject moduleA = mock(GradleProject.class);
    when(root.findByPath("a")).thenReturn(moduleA);
    when(moduleA.getPath()).thenReturn("a");

    ProjectBuildOutput buildOutputA = mock(ProjectBuildOutput.class);
    when(myBuildController.findModel(moduleA, ProjectBuildOutput.class)).thenReturn(buildOutputA);

    GradleProject moduleB = mock(GradleProject.class);
    when(root.findByPath("b")).thenReturn(moduleB);
    when(moduleB.getPath()).thenReturn("b");

    ProjectBuildOutput buildOutputB = mock(ProjectBuildOutput.class);
    when(myBuildController.findModel(moduleB, ProjectBuildOutput.class)).thenReturn(buildOutputB);

    GradleProject moduleD = mock(GradleProject.class);
    when(root.findByPath("d")).thenReturn(moduleD);
    when(moduleD.getPath()).thenReturn("d");

    InstantAppProjectBuildOutput buildOutputD = mock(InstantAppProjectBuildOutput.class);
    when(myBuildController.findModel(moduleD, InstantAppProjectBuildOutput.class)).thenReturn(buildOutputD);

    OutputBuildAction.PostBuildProjectModels outputs = myAction.execute(myBuildController);

    OutputBuildAction.PostBuildModuleModels moduleModelsA = outputs.getModels("a");
    assertNotNull(moduleModelsA);
    assertSame(buildOutputA, moduleModelsA.findModel(ProjectBuildOutput.class));
    assertNull(moduleModelsA.findModel(InstantAppProjectBuildOutput.class));

    OutputBuildAction.PostBuildModuleModels moduleModelsB = outputs.getModels("b");
    assertNotNull(moduleModelsB);
    assertSame(buildOutputB, moduleModelsB.findModel(ProjectBuildOutput.class));
    assertNull(moduleModelsB.findModel(InstantAppProjectBuildOutput.class));

    OutputBuildAction.PostBuildModuleModels moduleModelsC = outputs.getModels("c");
    assertNull(moduleModelsC);

    OutputBuildAction.PostBuildModuleModels moduleModelsD = outputs.getModels("d");
    assertNotNull(moduleModelsD);
    assertNull(moduleModelsD.findModel(ProjectBuildOutput.class));
    assertSame(buildOutputD, moduleModelsD.findModel(InstantAppProjectBuildOutput.class));
  }
}