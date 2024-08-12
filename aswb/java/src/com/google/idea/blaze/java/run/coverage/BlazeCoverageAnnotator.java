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

import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.CoverageDataManagerImpl;
import com.intellij.coverage.CoverageSuite;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.coverage.SimpleCoverageAnnotator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.rt.coverage.data.ProjectData;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/** Controls coverage annotation of files and directories. */
public class BlazeCoverageAnnotator extends SimpleCoverageAnnotator {

  /** List of file paths to display coverage data for. Used to filter parent lists. */
  private final List<String> coverageFilePaths = new ArrayList<>();

  public static BlazeCoverageAnnotator getInstance(Project project) {
    return project.getService(BlazeCoverageAnnotator.class);
  }

  public BlazeCoverageAnnotator(Project project) {
    super(project);
  }

  @Override
  public void onSuiteChosen(CoverageSuitesBundle newSuite) {
    super.onSuiteChosen(newSuite);
    coverageFilePaths.clear();
  }

  @Nullable
  @Override
  public String getFileCoverageInformationString(
      PsiFile psiFile, CoverageSuitesBundle currentSuite, CoverageDataManager manager) {
    return showCoverage(psiFile)
        ? super.getFileCoverageInformationString(psiFile, currentSuite, manager)
        : null;
  }

  @Nullable
  @Override
  public String getDirCoverageInformationString(
      PsiDirectory directory, CoverageSuitesBundle currentSuite, CoverageDataManager manager) {
    return showCoverage(directory)
        ? super.getDirCoverageInformationString(directory, currentSuite, manager)
        : null;
  }

  private boolean showCoverage(PsiFileSystemItem psiFile) {
    if (coverageFilePaths.isEmpty()) {
      return false;
    }
    VirtualFile vf = psiFile.getVirtualFile();
    if (vf == null) {
      return false;
    }
    String filePath = normalizeFilePath(vf.getPath());
    for (String path : coverageFilePaths) {
      if (filePath.startsWith(path)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Override default implementation to stop annotating at the deepest common parent directory.
   *
   * <p>We can't simply modify the parent class implementation without duplicating the entire class,
   * as it uses private subclasses. So instead, we let it run, then filter the output later.
   */
  @Nullable
  @Override
  protected Runnable createRenewRequest(
      CoverageSuitesBundle suites, CoverageDataManager dataManager) {
    ProjectData data = suites.getCoverageData();
    if (data == null) {
      return null;
    }
    Runnable parentRunnable = super.createRenewRequest(suites, dataManager);
    if (parentRunnable == null) {
      return null;
    }
    return () -> {
      coverageFilePaths.clear();
      coverageFilePaths.addAll(collectRootPaths(suites));
      parentRunnable.run();
      ApplicationManager.getApplication().invokeLater(() -> rebuildUi(dataManager));
    };
  }

  /**
   * The upstream coverage code is racy. Work around that by manually rebuilding the UI after the
   * coverage data is available.
   */
  private static void rebuildUi(CoverageDataManager dataManager) {
    if (dataManager instanceof CoverageDataManagerImpl) {
      ((CoverageDataManagerImpl) dataManager).fireAfterSuiteChosen();
    }
  }

  private static List<String> collectRootPaths(CoverageSuitesBundle suites) {
    List<String> paths = new ArrayList<>();
    for (CoverageSuite suite : suites.getSuites()) {
      if (!(suite instanceof BlazeCoverageSuite)) {
        continue;
      }
      File file = ((BlazeCoverageSuite) suite).getDeepestRootDirectory();
      if (file != null) {
        paths.add(normalizeFilePath(file.getPath()));
      }
    }
    return paths;
  }
}
