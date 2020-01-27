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

import static com.android.tools.idea.gradle.actions.BuildsToPathsMapper.tryToGetOutputPreBuild;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.ide.common.gradle.model.IdeAndroidArtifact;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.google.common.collect.Lists;
import java.io.File;
import java.util.Collections;
import java.util.Map;

/**
 * Tests for {@link BuildsToPathsMapper}.
 */
public class BuildsToPathsMapperForApkTest extends BuildsToPathsMapperForApkTestCase {
  private void initSimpleApp() throws Exception {
    loadSimpleApplication();
    myModule = getModule("app");
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

  public void testSingleOutputFromPreBuildModel() throws Exception {
    initSimpleApp();
    Map<String, File> myBuildsAndBundlePaths = myTask.getBuildsToPaths(null, myBuildVariants, Collections.singleton(myModule), false, null);
    assertSameElements(myBuildsAndBundlePaths.keySet(), myModule.getName());

    File expectedOutput =
      AndroidModuleModel.get(myModule).getSelectedVariant().getMainArtifact().getOutputs().iterator().next().getOutputFile();
    assertEquals(expectedOutput, myBuildsAndBundlePaths.get(myModule.getName()));
  }

  public void testEmptyOutputFromPreBuildModel() {
    // Simulate the case that output files are empty.
    AndroidModuleModel androidModel = mock(AndroidModuleModel.class);
    IdeAndroidArtifact artifact = mock(IdeAndroidArtifact.class);
    when(androidModel.getMainArtifact()).thenReturn(artifact);
    when(artifact.getOutputs()).thenReturn(Collections.emptyList());

    // Verify tryToGetOutputPreBuild returns null.
    assertNull(tryToGetOutputPreBuild(androidModel));
  }

  private void initSimpleAppForSignedApk() throws Exception {
    loadSimpleApplication();
    myModule = getModule("app");
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
}
