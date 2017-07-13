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

import com.android.builder.model.ProjectBuildOutput;
import com.google.common.truth.Truth;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;
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

    myAction = new OutputBuildAction(Arrays.asList("a", "b", "c"));
  }

  @Test
  public void execute() {
    GradleProject root = mock(GradleProject.class);
    when(myBuildController.findModel(myRootProject, GradleProject.class)).thenReturn(root);

    GradleProject moduleA = mock(GradleProject.class);
    when(root.findByPath("a")).thenReturn(moduleA);

    ProjectBuildOutput buildOutputA = mock(ProjectBuildOutput.class);
    when(myBuildController.findModel(moduleA, ProjectBuildOutput.class)).thenReturn(buildOutputA);

    GradleProject moduleB = mock(GradleProject.class);
    when(root.findByPath("b")).thenReturn(moduleB);

    ProjectBuildOutput buildOutputB = mock(ProjectBuildOutput.class);
    when(myBuildController.findModel(moduleB, ProjectBuildOutput.class)).thenReturn(buildOutputB);

    List<OutputBuildAction.ModuleBuildOutput> outputs = myAction.execute(myBuildController);
    assertThat(outputs).hasSize(2); // Outputs only for 'a' and 'b'

    Map<String, ProjectBuildOutput> outputsByGradlePath = new HashMap<>();
    for (OutputBuildAction.ModuleBuildOutput output : outputs) {
      outputsByGradlePath.put(output.getModulePath(), output.getOutput());
    }

    assertSame(buildOutputA, outputsByGradlePath.get("a"));
    assertSame(buildOutputB, outputsByGradlePath.get("b"));
    assertNull(outputsByGradlePath.get("c"));
  }
}