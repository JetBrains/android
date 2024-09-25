/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.java.run.coverage;

import static com.google.common.truth.Truth.assertThat;

import com.intellij.rt.coverage.data.ProjectData;
import java.io.File;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link BlazeCoverageSuite} */
@RunWith(JUnit4.class)
public class BlazeCoverageSuiteTest {

  @Test
  public void deepestRootDirectory_singleFile_returnsParent() {
    BlazeCoverageSuite suite = suiteWithFiles("/usr/home/test.txt");
    assertThat(suite.getDeepestRootDirectory()).isEqualTo(new File("/usr/home"));
  }

  @Test
  public void deepestRootDirectory_sameParent_returnsParent() {
    BlazeCoverageSuite suite = suiteWithFiles("/usr/home/test.txt", "/usr/home/foo");
    assertThat(suite.getDeepestRootDirectory()).isEqualTo(new File("/usr/home"));
  }

  @Test
  public void deepestRootDirectory_differentParents_returnsCommonAncestor() {
    BlazeCoverageSuite suite = suiteWithFiles("/usr/home/test.txt", "/usr/bin/foo");
    assertThat(suite.getDeepestRootDirectory()).isEqualTo(new File("/usr"));

    suite = suiteWithFiles("/usr/home/test.txt", "/usr/foo");
    assertThat(suite.getDeepestRootDirectory()).isEqualTo(new File("/usr"));
  }

  private static BlazeCoverageSuite suiteWithFiles(String... filePaths) {
    BlazeCoverageSuite suite = new BlazeCoverageSuite();
    ProjectData data = new ProjectData();
    for (String filePath : filePaths) {
      data.getOrCreateClassData(filePath);
    }
    suite.setCoverageData(data);
    return suite;
  }
}
