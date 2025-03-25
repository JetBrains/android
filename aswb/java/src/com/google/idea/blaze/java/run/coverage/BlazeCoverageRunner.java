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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.Ints;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.java.run.coverage.BlazeCoverageData.FileData;
import com.intellij.coverage.CoverageEngine;
import com.intellij.coverage.CoverageRunner;
import com.intellij.coverage.CoverageSuite;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.rt.coverage.data.ClassData;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.ProjectData;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.annotation.Nullable;

/** Loads coverage data when blaze invocation is complete. */
public class BlazeCoverageRunner extends CoverageRunner {
  private static final Logger logger = Logger.getInstance(BlazeCoverageRunner.class);

  private static final String ID = "BlazeCoverageRunner";

  @Nullable
  @Override
  public ProjectData loadCoverageData(File sessionDataFile, @Nullable CoverageSuite suite) {
    if (!(suite instanceof BlazeCoverageSuite)) {
      return null;
    }
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(suite.getProject()).getBlazeProjectData();
    if (blazeProjectData == null) {
      return null;
    }
    try (FileInputStream stream = new FileInputStream(sessionDataFile)) {
      return parseCoverage(blazeProjectData.getWorkspacePathResolver(), stream);
    } catch (IOException e) {
      logger.warn(e);
    }
    return null;
  }

  @VisibleForTesting
  static ProjectData parseCoverage(WorkspacePathResolver pathResolver, InputStream stream)
      throws IOException {
    ProjectData data = new ProjectData();
    BlazeCoverageData blazeData = BlazeCoverageData.parse(stream);
    for (String filePath : blazeData.perFileData.keySet()) {
      File file = pathResolver.resolveToFile(filePath);
      ClassData classData = data.getOrCreateClassData(file.getPath());
      classData.setLines(fromFileData(blazeData.perFileData.get(filePath)));
    }
    return data;
  }

  private static LineData[] fromFileData(FileData fileData) {
    LineData[] lines = new LineData[maxLineNumber(fileData) + 1];
    fileData.lineHits.forEach(
        (line, hits) -> {
          LineData newLine = new LineData(line, null);
          newLine.setHits(hits);
          lines[line] = newLine;
        });
    return lines;
  }

  private static int maxLineNumber(FileData fileData) {
    return Ints.max(fileData.lineHits.keySet().toIntArray());
  }

  @Override
  public String getPresentableName() {
    return Blaze.defaultBuildSystemName();
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public String getDataFileExtension() {
    return "dat";
  }

  @Override
  public boolean acceptsCoverageEngine(CoverageEngine engine) {
    return engine instanceof BlazeCoverageEngine;
  }
}
