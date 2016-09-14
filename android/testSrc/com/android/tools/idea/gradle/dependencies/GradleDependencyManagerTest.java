/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.dependencies;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.collect.ImmutableList;

import java.util.List;

import static com.android.tools.idea.testing.TestProjectPaths.SPLIT_BUILD_FILES;

/**
 * Tests for {@link GradleDependencyManager}.
 */
public class GradleDependencyManagerTest extends AndroidGradleTestCase {
  private static final GradleCoordinate existingDependency = new GradleCoordinate("com.android.support", "appcompat-v7", "+");
  private static final GradleCoordinate dummyDependency = new GradleCoordinate("dummy.group", "dummy.artifact", "0.0.0");
  private static final List<GradleCoordinate> dependencies = ImmutableList.of(existingDependency, dummyDependency);

  public void testFindMissingDependenciesWithRegularProject() throws Exception {
    loadSimpleApplication();
    GradleDependencyManager dependencyManager = GradleDependencyManager.getInstance(getProject());
    List<GradleCoordinate> missingDependencies = dependencyManager.findMissingDependencies(myModules.getAppModule(), dependencies);
    assertEquals(1, missingDependencies.size());
    assertEquals(dummyDependency, missingDependencies.get(0));
  }

  public void testFindMissingDependenciesInProjectWithSplitBuildFiles() throws Exception {
    loadProject(SPLIT_BUILD_FILES);
    GradleDependencyManager dependencyManager = GradleDependencyManager.getInstance(getProject());
    List<GradleCoordinate> missingDependencies = dependencyManager.findMissingDependencies(myModules.getAppModule(), dependencies);
    assertEquals(1, missingDependencies.size());
    assertEquals(dummyDependency, missingDependencies.get(0));
  }
}
