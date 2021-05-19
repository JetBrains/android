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
package com.android.tools.idea.gradle.project.sync;

import com.android.ide.common.gradle.model.impl.IdeAndroidProjectImpl;
import com.android.tools.idea.testing.TestProjectPaths;
import org.jetbrains.annotations.NotNull;

/**
 * Tests for {@link IdeAndroidProjectImpl} that need to be run on old versions of AGP.
 */
public class IdeAndroidProjectIntegrationOldAgpTest extends IdeAndroidProjectIntegrationTestCase {
  public void testLevel2DependenciesWithGradle2Dot2() throws Exception {
    syncProjectWithGradle2Dot2();
    verifyIdeLevel2DependenciesPopulated();
  }

  public void testLocalAarsAsModulesWithGradle2Dot2() throws Exception {
    syncProjectWithGradle2Dot2(TestProjectPaths.LOCAL_AARS_AS_MODULES_PRE30);
    verifyAarModuleShowsAsAndroidLibrary(":library-debug::null");
  }

  private void syncProjectWithGradle2Dot2() throws Exception {
    syncProjectWithGradle2Dot2(TestProjectPaths.SIMPLE_APPLICATION_PRE30);
  }

  private void syncProjectWithGradle2Dot2(@NotNull String projectName) throws Exception {
    loadProject(projectName, null, "3.5", "2.2.0");
  }
}
