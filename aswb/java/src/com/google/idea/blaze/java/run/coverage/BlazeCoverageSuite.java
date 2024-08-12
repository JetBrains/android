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

import com.intellij.coverage.BaseCoverageSuite;
import com.intellij.coverage.CoverageEngine;
import com.intellij.coverage.CoverageFileProvider;
import com.intellij.coverage.CoverageRunner;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.rt.coverage.data.ProjectData;
import java.io.File;
import java.util.Set;
import javax.annotation.Nullable;

/** Coverage suite implementation for Blaze run configurations. */
public class BlazeCoverageSuite extends BaseCoverageSuite {
  @Override
  public CoverageEngine getCoverageEngine() {
    return BlazeCoverageEngine.getInstance();
  }

  BlazeCoverageSuite() {
    super();
  }

  BlazeCoverageSuite(
      Project project,
      String name,
      CoverageFileProvider fileProvider,
      CoverageRunner coverageRunner) {
    super(
        name,
        fileProvider,
        System.currentTimeMillis(),
        false,
        false,
        false,
        coverageRunner,
        project);
  }

  /**
   * The deepest directory below which this suite's coverage data lies, or null if there is no
   * coverage data.
   */
  @Nullable
  File getDeepestRootDirectory() {
    ProjectData data = getCoverageData();
    if (data == null) {
      return null;
    }
    @SuppressWarnings("unchecked")
    Set<String> files = data.getClasses().keySet();
    File root = null;
    for (String path : files) {
      if (root == null) {
        root = new File(path).getParentFile();
      } else {
        root = FileUtil.findAncestor(root, new File(path));
      }
    }
    return root;
  }
}
