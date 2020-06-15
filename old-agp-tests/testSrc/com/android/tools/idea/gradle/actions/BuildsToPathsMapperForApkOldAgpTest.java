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

import static com.android.tools.idea.testing.TestProjectPaths.INSTANT_APP;

import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import java.io.File;
import java.util.Collections;
import java.util.Map;

/**
 * Tests for {@link BuildsToPathsMapper}.
 */
public class BuildsToPathsMapperForApkOldAgpTest extends BuildsToPathsMapperForApkTestCase {
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

  private void initInstantApp() throws Exception {
    // Use a plugin version with instant app support
    loadProject(INSTANT_APP, null, null, "3.5.0");
    myModule = getModule("instant-app");
    myTask = BuildsToPathsMapper.getInstance(getProject());
  }

  private void initInstantAppForSignedApk() throws Exception {
    // Use a plugin version with instant app support
    loadProject(INSTANT_APP, null, null, "3.5.0");
    myModule = getModule("instant-app");
    myBuildVariants.add(buildVariant);
    myTask = BuildsToPathsMapper.getInstance(getProject());
  }
}
