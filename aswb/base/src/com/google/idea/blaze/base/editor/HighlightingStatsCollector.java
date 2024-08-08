/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.editor;

import com.google.common.collect.Multimap;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.HighlightVisitor;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import java.util.Set;

/**
 * Class to visit all highlights in a file, and pipe them to {@link BlazeHighlightStatsCollector}
 * for collecting stats on the highlighting information.
 *
 * <p>{@link com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass} shows how classes
 * implementing {@link HighlightVisitor} are called.
 *
 * <pre>
 * Call order:
 *   1. {@link #clone()} : once for each file
 *   2. {@link #suitableForFile(PsiFile)} : to filter visitors
 *   3. {@link #analyze(PsiFile, boolean, HighlightInfoHolder, Runnable)} : analyze is used to
 *          propagate results. Runnable must be run, and the method must return `true`.
 *   4. {@link #visit(PsiElement)} : called once per psiElement in the file.
 *          highlightInfoHolder will contain relevant HighlightInfo for the PsiElement
 * </pre>
 */
public class HighlightingStatsCollector implements HighlightVisitor {

  private static final BoolExperiment enabled =
      new BoolExperiment("blaze.highlight.visitor.enabled", true);

  private HighlightInfoHolder highlightInfoHolder;
  private Multimap<HighlightInfoType, BlazeHighlightStatsCollector> infoTypesToCollectors;

  @Override
  public boolean suitableForFile(PsiFile psiFile) {
    if (!enabled.getValue()) {
      return false;
    }
    return !BlazeHighlightStatsCollector.getCollectorsSupportingFile(psiFile).isEmpty();
  }

  @Override
  public void visit(PsiElement psiElement) {
    if (highlightInfoHolder == null) {
      return;
    }

    // Compute the collectors pertaining to each HighlightInfoType
    // `visit` is called for every Highlight in the file, which include keyword highlighting and
    // such.
    // Number of calls to `visit` can quickly blow up for a large file, so we want to avoid any
    // repeated computations
    if (infoTypesToCollectors == null) {
      PsiFile psiFile = psiElement.getContainingFile();
      if (psiFile == null) {
        return;
      }
      Set<BlazeHighlightStatsCollector> collectors =
          BlazeHighlightStatsCollector.getCollectorsSupportingFile(psiFile);
      infoTypesToCollectors =
          BlazeHighlightStatsCollector.getCollectorsByHighlightInfoTypes(collectors);
    }

    for (int i = 0; i < highlightInfoHolder.size(); i++) {
      HighlightInfo highlightInfo = highlightInfoHolder.get(i);
      infoTypesToCollectors
          .get(highlightInfo.type)
          .forEach(c -> c.processHighlight(psiElement, highlightInfo));
    }
  }

  @Override
  public boolean analyze(
      PsiFile psiFile,
      boolean updateWholeFile,
      HighlightInfoHolder highlightInfoHolder,
      Runnable runnable) {
    if (!enabled.getValue()) {
      runnable.run();
      return true;
    }

    // Only analyze if the whole file is being highlighted.
    // We avoid analyzing partial highlights because they typically happen when the user is editing
    // a file. This creates noise as IntelliJ tries to keep highlights up to date with each
    // user keystroke.
    if (updateWholeFile) {
      this.highlightInfoHolder = highlightInfoHolder;
    }

    try {
      runnable.run();
    } finally {
      this.highlightInfoHolder = null;
    }
    return true;
  }

  @Override
  public HighlightVisitor clone() {
    return new HighlightingStatsCollector();
  }
}
