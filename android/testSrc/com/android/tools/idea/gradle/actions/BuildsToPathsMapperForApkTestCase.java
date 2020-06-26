/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.tools.idea.gradle.util.GradleUtil.getGradlePath;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.build.OutputFile;
import com.android.builder.model.InstantAppProjectBuildOutput;
import com.android.builder.model.InstantAppVariantBuildOutput;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.VariantBuildOutput;
import com.android.tools.idea.gradle.run.OutputBuildAction;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.module.Module;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public abstract class BuildsToPathsMapperForApkTestCase extends AndroidGradleTestCase {
  protected static final String buildVariant = "FreeDebug";
  protected Module myModule;
  protected BuildsToPathsMapper myTask;
  protected List<String> myBuildVariants = new ArrayList<>();

  @NotNull
  protected OutputBuildAction.PostBuildProjectModels createInstantAppPostBuildModel(@NotNull File output, String buildVariant) {
    InstantAppProjectBuildOutput instantAppProjectBuildOutput = createInstantAppProjectBuildOutputMock(buildVariant, output);
    OutputBuildAction.PostBuildModuleModels postBuildModuleModels =
      new BuildsToPathsMapperForApkTestCase.PostBuildModuleModelsMockBuilder().setInstantAppProjectBuildOutput(instantAppProjectBuildOutput).build();
    return new BuildsToPathsMapperForApkTestCase.PostBuildProjectModelsMockBuilder().setPostBuildModuleModels(getGradlePath(myModule), postBuildModuleModels).build();
  }

  private static InstantAppProjectBuildOutput createInstantAppProjectBuildOutputMock(@NotNull String variant, @NotNull File file) {
    InstantAppProjectBuildOutput projectBuildOutput = mock(InstantAppProjectBuildOutput.class);
    InstantAppVariantBuildOutput variantBuildOutput = mock(InstantAppVariantBuildOutput.class);
    OutputFile outputFile = mock(OutputFile.class);
    when(projectBuildOutput.getInstantAppVariantsBuildOutput()).thenReturn(Collections.singleton(variantBuildOutput));
    when(variantBuildOutput.getName()).thenReturn(variant);
    when(variantBuildOutput.getOutput()).thenReturn(outputFile);
    when(outputFile.getOutputFile()).thenReturn(file);
    return projectBuildOutput;
  }

  @NotNull
  protected OutputBuildAction.PostBuildProjectModels createPostBuildModel(@NotNull Collection<File> outputs, String buildVariant) {
    ProjectBuildOutput projectBuildOutput = createProjectBuildOutputMock(buildVariant, outputs);
    OutputBuildAction.PostBuildModuleModels postBuildModuleModels =
      new PostBuildModuleModelsMockBuilder().setProjectBuildOutput(projectBuildOutput).build();
    return new PostBuildProjectModelsMockBuilder().setPostBuildModuleModels(getGradlePath(myModule), postBuildModuleModels).build();
  }

  private static ProjectBuildOutput createProjectBuildOutputMock(@NotNull String variant, @NotNull Collection<File> files) {
    ProjectBuildOutput projectBuildOutput = mock(ProjectBuildOutput.class);
    VariantBuildOutput variantBuildOutput = mock(VariantBuildOutput.class);
    List<OutputFile> outputFiles = new ArrayList<>();
    for (File file : files) {
      OutputFile outputFile = mock(OutputFile.class);
      when(outputFile.getOutputFile()).thenReturn(file);
      outputFiles.add(outputFile);
    }

    when(projectBuildOutput.getVariantsBuildOutput()).thenReturn(Collections.singleton(variantBuildOutput));
    when(variantBuildOutput.getName()).thenReturn(variant);
    when(variantBuildOutput.getOutputs()).thenReturn(outputFiles);
    return projectBuildOutput;
  }

  private static final class PostBuildModuleModelsMockBuilder {
    @NotNull private final OutputBuildAction.PostBuildModuleModels myPostBuildModuleModels;

    private PostBuildModuleModelsMockBuilder() {
      myPostBuildModuleModels = mock(OutputBuildAction.PostBuildModuleModels.class);
    }

    private PostBuildModuleModelsMockBuilder setProjectBuildOutput(@NotNull ProjectBuildOutput projectBuildOutput) {
      when(myPostBuildModuleModels.findModel(eq(ProjectBuildOutput.class))).thenReturn(projectBuildOutput);
      return this;
    }

    private PostBuildModuleModelsMockBuilder setInstantAppProjectBuildOutput(@NotNull InstantAppProjectBuildOutput instantAppProjectBuildOutput) {
      when(myPostBuildModuleModels.findModel(eq(InstantAppProjectBuildOutput.class))).thenReturn(instantAppProjectBuildOutput);
      return this;
    }

    private OutputBuildAction.PostBuildModuleModels build() {
      return myPostBuildModuleModels;
    }
  }

  private static final class PostBuildProjectModelsMockBuilder {
    @NotNull private final OutputBuildAction.PostBuildProjectModels myPostBuildProjectModels;

    private PostBuildProjectModelsMockBuilder() {
      myPostBuildProjectModels = mock(OutputBuildAction.PostBuildProjectModels.class);
    }

    private PostBuildProjectModelsMockBuilder setPostBuildModuleModels(@NotNull String gradlePath,
                                                                       @NotNull OutputBuildAction.PostBuildModuleModels postBuildModuleModels) {
      when(myPostBuildProjectModels.getModels(eq(gradlePath))).thenReturn(postBuildModuleModels);
      return this;
    }

    private OutputBuildAction.PostBuildProjectModels build() {
      return myPostBuildProjectModels;
    }
  }
}
