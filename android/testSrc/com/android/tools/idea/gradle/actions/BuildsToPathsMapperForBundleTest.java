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

import com.android.builder.model.AppBundleProjectBuildOutput;
import com.android.builder.model.AppBundleVariantBuildOutput;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.run.OutputBuildAction;
import com.android.tools.idea.testing.AndroidGradleTestCase;
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
public class BuildsToPathsMapperForBundleTest extends AndroidGradleTestCase {
  private static final String buildVariant = "FreeDebug";
  private Module myModule;
  private BuildsToPathsMapper myTask;
  private List<String> myBuildVariants = new ArrayList<>();

  private void initSimpleApp() throws Exception {
    loadSimpleApplication();
    myModule = getModule("app");
    myTask = BuildsToPathsMapper.getInstance(getProject());
  }

  public void testSingleOutputFromPostBuildModel() throws Exception {
    initSimpleApp();
    File output = new File("path/to/bundle");
    AndroidModuleModel androidModel = AndroidModuleModel.get(myModule);
    String myBuildVariant = androidModel.getSelectedVariant().getName();
    Map<String, File> myBuildsAndBundlePaths = myTask.getBuildsToPaths(createPostBuildModel(Collections.singleton(output), myBuildVariant),
                                                                       myBuildVariants,
                                                                       Collections.singleton(myModule),
                                                                       true);
    assertSameElements(myBuildsAndBundlePaths.keySet(), myModule.getName());
    assertEquals(output, myBuildsAndBundlePaths.get(myModule.getName()));
  }

  private void initSimpleAppForSignedBundle() throws Exception {
    loadSimpleApplication();
    myModule = getModule("app");
    myBuildVariants.add(buildVariant);
    myTask = BuildsToPathsMapper.getInstance(getProject());
  }

  public void testSingleOutputFromPostBuildModelForSignedBundle() throws Exception {
    initSimpleAppForSignedBundle();
    File output = new File("path/to/bundle");
    Map<String, File> myBuildsAndBundlePaths =
      myTask.getBuildsToPaths(createPostBuildModel(Collections.singleton(output), myBuildVariants.get(0)),
                              myBuildVariants,
                              Collections.singleton(myModule),
                              true);
    assertSameElements(myBuildsAndBundlePaths.keySet(), myBuildVariants.get(0));
    assertEquals(output, myBuildsAndBundlePaths.get(myBuildVariants.get(0)));
  }

  @NotNull
  private OutputBuildAction.PostBuildProjectModels createPostBuildModel(@NotNull Collection<File> outputs, String buildVariant) {
    AppBundleProjectBuildOutput projectBuildOutput = createProjectBuildOutputMock(buildVariant, outputs);
    OutputBuildAction.PostBuildModuleModels postBuildModuleModels =
      new PostBuildModuleModelsMockBuilder().setProjectBuildOutput(projectBuildOutput).build();
    return new PostBuildProjectModelsMockBuilder().setPostBuildModuleModels(getGradlePath(myModule), postBuildModuleModels).build();
  }

  private static AppBundleProjectBuildOutput createProjectBuildOutputMock(@NotNull String variant, @NotNull Collection<File> files) {
    AppBundleProjectBuildOutput projectBuildOutput = mock(AppBundleProjectBuildOutput.class);
    AppBundleVariantBuildOutput variantBuildOutput = mock(AppBundleVariantBuildOutput.class);
    when(projectBuildOutput.getAppBundleVariantsBuildOutput()).thenReturn(Collections.singleton(variantBuildOutput));
    when(variantBuildOutput.getName()).thenReturn(variant);
    when(variantBuildOutput.getBundleFile()).thenReturn(files.iterator().next());
    return projectBuildOutput;
  }

  private static class PostBuildModuleModelsMockBuilder {
    @NotNull private final OutputBuildAction.PostBuildModuleModels myPostBuildModuleModels;

    private PostBuildModuleModelsMockBuilder() {
      myPostBuildModuleModels = mock(OutputBuildAction.PostBuildModuleModels.class);
    }

    private PostBuildModuleModelsMockBuilder setProjectBuildOutput(@NotNull AppBundleProjectBuildOutput projectBuildOutput) {
      when(myPostBuildModuleModels.findModel(eq(AppBundleProjectBuildOutput.class))).thenReturn(projectBuildOutput);
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
                                                                       @NotNull OutputBuildAction.PostBuildModuleModels
                                                                         postBuildModuleModels) {
      when(myPostBuildProjectModels.getModels(eq(gradlePath))).thenReturn(postBuildModuleModels);
      return this;
    }

    private OutputBuildAction.PostBuildProjectModels build() {
      return myPostBuildProjectModels;
    }
  }
}
