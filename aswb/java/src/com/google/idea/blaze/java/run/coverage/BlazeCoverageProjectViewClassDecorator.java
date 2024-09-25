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

import com.intellij.coverage.AbstractCoverageProjectViewNodeDecorator;
import com.intellij.coverage.CoverageAnnotator;
import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.packageDependencies.ui.PackageDependenciesNode;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.ui.ColoredTreeCellRenderer;
import javax.annotation.Nullable;

/**
 * Adds coverage information to java classes in the project view. We need java-specific handling
 * here because the default decorator only handles PsiFile elements.
 */
public class BlazeCoverageProjectViewClassDecorator
    extends AbstractCoverageProjectViewNodeDecorator {

  BlazeCoverageProjectViewClassDecorator(Project project) {
    super(project);
  }

  @Override
  public void decorate(@SuppressWarnings("rawtypes") ProjectViewNode node, PresentationData data) {
    Project project = node.getProject();
    CoverageDataManager manager = getCoverageDataManager(project);
    CoverageSuitesBundle currentSuite = manager.getCurrentSuitesBundle();

    BlazeCoverageAnnotator annotator = getAnnotator(project, currentSuite);
    if (annotator == null) {
      return;
    }
    PsiFile file = getPsiFileForJavaClass(getPsiElement(node));
    if (file == null) {
      return;
    }
    String string = annotator.getFileCoverageInformationString(file, currentSuite, manager);
    if (string != null) {
      data.setLocationString(string);
    }
  }

  @Override
  public void decorate(PackageDependenciesNode node, ColoredTreeCellRenderer cellRenderer) {
    PsiFile file = getPsiFileForJavaClass(node.getPsiElement());
    if (file == null) {
      return;
    }
    Project project = file.getProject();
    CoverageDataManager manager = getCoverageDataManager(project);
    CoverageSuitesBundle currentSuite = manager.getCurrentSuitesBundle();
    BlazeCoverageAnnotator annotator = getAnnotator(project, currentSuite);
    if (annotator == null) {
      return;
    }
    String string = annotator.getFileCoverageInformationString(file, currentSuite, manager);
    if (string != null) {
      appendCoverageInfo(cellRenderer, string);
    }
  }

  @Nullable
  private static PsiFile getPsiFileForJavaClass(@Nullable PsiElement element) {
    if (element instanceof PsiClass && element.isValid()) {
      return element.getContainingFile();
    }
    return null;
  }

  @Nullable
  private static PsiElement getPsiElement(@SuppressWarnings("rawtypes") AbstractTreeNode node) {
    Object value = node.getValue();
    if (value instanceof PsiElement) {
      return (PsiElement) value;
    }
    if (value instanceof SmartPsiElementPointer) {
      return ((SmartPsiElementPointer) value).getElement();
    }
    return null;
  }

  @Nullable
  private static BlazeCoverageAnnotator getAnnotator(
      Project project, @Nullable CoverageSuitesBundle currentSuite) {
    if (currentSuite == null) {
      return null;
    }
    CoverageAnnotator coverageAnnotator = currentSuite.getAnnotator(project);
    return coverageAnnotator instanceof BlazeCoverageAnnotator
        ? (BlazeCoverageAnnotator) coverageAnnotator
        : null;
  }
}
