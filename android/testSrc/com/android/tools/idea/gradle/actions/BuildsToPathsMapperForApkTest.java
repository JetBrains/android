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

import static com.android.tools.idea.gradle.util.GradleUtil.getGradlePath;
import static com.android.tools.idea.testing.TestProjectPaths.INSTANT_APP;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.build.OutputFile;
import com.android.builder.model.InstantAppProjectBuildOutput;
import com.android.builder.model.InstantAppVariantBuildOutput;
import com.android.builder.model.ProjectBuildOutput;
import com.android.builder.model.VariantBuildOutput;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.run.OutputBuildAction;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Tests for {@link BuildsToPathsMapper}.
 */
public class BuildsToPathsMapperForApkTest extends AndroidGradleTestCase {
  private static final String buildVariant = "FreeDebug";
  private Module myModule;
  private BuildsToPathsMapper myTask;
  private List<String> myBuildVariants = new ArrayList<>();

  private void initSimpleApp() throws Exception {
    loadSimpleApplication();
    myModule = getModule("app");
    myTask = BuildsToPathsMapper.getInstance(getProject());
  }

  private void initInstantApp() throws Exception {
    // Use a plugin version with instant app support
    loadProject(INSTANT_APP, null, null, "3.5.0");
    myModule = getModule("instant-app");
    myTask = BuildsToPathsMapper.getInstance(getProject());
  }

  public void testSingleOutputFromPostBuildModel() throws Exception {
    initSimpleApp();
    File output = new File("path/to/apk");
    AndroidModuleModel androidModel = AndroidModuleModel.get(myModule);
    String myBuildVariant = androidModel.getSelectedVariant().getName();
    Map<String, File> myBuildsAndBundlePaths = myTask.getBuildsToPaths(createPostBuildModel(Collections.singleton(output), myBuildVariant),
                                                                       myBuildVariants,
                                                                       Collections.singleton(myModule),
                                                                       false,
                                                                       null);
    assertSameElements(myBuildsAndBundlePaths.keySet(), myModule.getName());
    assertEquals(output, myBuildsAndBundlePaths.get(myModule.getName()));
  }

  public void testMultipleOutputFromPostBuildModel() throws Exception {
    initSimpleApp();
    File output1 = new File("path/to/apk1");
    File output2 = new File("path/to/apk2");
    assertEquals(output1.getParentFile(), output2.getParentFile());
    AndroidModuleModel androidModel = AndroidModuleModel.get(myModule);
    String myBuildVariant = androidModel.getSelectedVariant().getName();
    Map<String, File> myBuildsAndBundlePaths =
      myTask.getBuildsToPaths(createPostBuildModel(Lists.newArrayList(output1, output2), myBuildVariant),
                              myBuildVariants,
                              Collections.singleton(myModule),
                              false,
                              null);
    assertSameElements(myBuildsAndBundlePaths.keySet(), myModule.getName());
    assertEquals(output1.getParentFile(), myBuildsAndBundlePaths.get(myModule.getName()));
  }

  public void testSingleOutputFromInstantAppPostBuildModel() throws Exception {
    initInstantApp();
    File output = new File("path/to/bundle");
    AndroidModuleModel androidModel = AndroidModuleModel.get(myModule);
    String myBuildVariant = androidModel.getSelectedVariant().getName();
    Map<String, File> myBuildsAndBundlePaths = myTask.getBuildsToPaths(createInstantAppPostBuildModel(output, myBuildVariant),
                                                                       myBuildVariants,
                                                                       Collections.singleton(myModule),
                                                                       false,
                                                                       null);
    assertSameElements(myBuildsAndBundlePaths.keySet(), myModule.getName());
    assertEquals(output, myBuildsAndBundlePaths.get(myModule.getName()));
  }

  public void testSingleOutputFromPreBuildModel() throws Exception {
    initSimpleApp();
    Map<String, File> myBuildsAndBundlePaths = myTask.getBuildsToPaths(null, myBuildVariants, Collections.singleton(myModule), false, null);
    assertSameElements(myBuildsAndBundlePaths.keySet(), myModule.getName());

    File expectedOutput =
      AndroidModuleModel.get(myModule).getSelectedVariant().getMainArtifact().getOutputs().iterator().next().getOutputFile();
    assertEquals(expectedOutput, myBuildsAndBundlePaths.get(myModule.getName()));
  }

  private void initSimpleAppForSignedApk() throws Exception {
    loadSimpleApplication();
    myModule = getModule("app");
    myBuildVariants.add(buildVariant);
    myTask = BuildsToPathsMapper.getInstance(getProject());
  }

  private void initInstantAppForSignedApk() throws Exception {
    // Use a plugin version with instant app support
    loadProject(INSTANT_APP, null, null, "3.5.0");
    myModule = getModule("instant-app");
    myBuildVariants.add(buildVariant);
    myTask = BuildsToPathsMapper.getInstance(getProject());
  }

  public void testSingleOutputFromPostBuildModelForSignedApk() throws Exception {
    initSimpleAppForSignedApk();
    File output = new File("path/to/apk");
    Map<String, File> myBuildsAndBundlePaths =
      myTask.getBuildsToPaths(createPostBuildModel(Collections.singleton(output), myBuildVariants.get(0)),
                              myBuildVariants,
                              Collections.singleton(myModule),
                              false,
                              "");
    assertSameElements(myBuildsAndBundlePaths.keySet(), myBuildVariants.get(0));
    assertEquals(output, myBuildsAndBundlePaths.get(myBuildVariants.get(0)));
  }

  public void testMultipleOutputFromPostBuildModelForSignedApk() throws Exception {
    initSimpleAppForSignedApk();
    File output1 = new File("path/to/apk1");
    File output2 = new File("path/to/apk2");
    assertEquals(output1.getParentFile(), output2.getParentFile());
    Map<String, File> myBuildsAndBundlePaths =
      myTask.getBuildsToPaths(createPostBuildModel(Lists.newArrayList(output1, output2), myBuildVariants.get(0)),
                              myBuildVariants,
                              Collections.singleton(myModule),
                              false,
                              "");
    assertSameElements(myBuildsAndBundlePaths.keySet(), myBuildVariants.get(0));
    assertEquals(output1.getParentFile(), myBuildsAndBundlePaths.get(myBuildVariants.get(0)));
  }

  public void testSingleOutputFromInstantAppPostBuildModelForSignedApk() throws Exception {
    initInstantAppForSignedApk();
    File output = new File("path/to/bundle");
    Map<String, File> myBuildsAndBundlePaths = myTask.getBuildsToPaths(createInstantAppPostBuildModel(output, myBuildVariants.get(0)),
                                                                       myBuildVariants,
                                                                       Collections.singleton(myModule),
                                                                       false,
                                                                       "");
    assertSameElements(myBuildsAndBundlePaths.keySet(), myBuildVariants.get(0));
    assertEquals(output, myBuildsAndBundlePaths.get(myBuildVariants.get(0)));
  }

  @NotNull
  private OutputBuildAction.PostBuildProjectModels createPostBuildModel(@NotNull Collection<File> outputs, String buildVariant) {
    ProjectBuildOutput projectBuildOutput = createProjectBuildOutputMock(buildVariant, outputs);
    OutputBuildAction.PostBuildModuleModels postBuildModuleModels =
      new PostBuildModuleModelsMockBuilder().setProjectBuildOutput(projectBuildOutput).build();
    return new PostBuildProjectModelsMockBuilder().setPostBuildModuleModels(getGradlePath(myModule), postBuildModuleModels).build();
  }

  @NotNull
  private OutputBuildAction.PostBuildProjectModels createInstantAppPostBuildModel(@NotNull File output, String buildVariant) {
    InstantAppProjectBuildOutput instantAppProjectBuildOutput = createInstantAppProjectBuildOutputMock(buildVariant, output);
    OutputBuildAction.PostBuildModuleModels postBuildModuleModels =
      new PostBuildModuleModelsMockBuilder().setInstantAppProjectBuildOutput(instantAppProjectBuildOutput).build();
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

  private static class PostBuildModuleModelsMockBuilder {
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

  private static class PostBuildProjectModelsMockBuilder {
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
