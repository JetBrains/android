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
package com.google.idea.blaze.android.editor;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.editor.BlazeHighlightStatsCollector;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import java.util.Set;
import org.jetbrains.kotlin.idea.KotlinFileType;

/**
 * Stats collector to log the number of unresolved references to resource symbols.
 *
 * <p>Since we want stats per project, all the work is delegated to a {@link
 * ProjectUnresolvedResourceStatsCollector}.
 */
public class UnresolvedResourceStatsCollector implements BlazeHighlightStatsCollector {

  static final BoolExperiment enabled =
      new BoolExperiment("android.unresolved.resource.stats.enabled.2", true);

  private static final ImmutableSet<FileType> SUPPORTED_FILE_TYPES =
      ImmutableSet.of(JavaFileType.INSTANCE, KotlinFileType.INSTANCE);
  private static final ImmutableSet<HighlightInfoType> SUPPORTED_HIGHLIGHT_TYPES =
      ImmutableSet.of(HighlightInfoType.WRONG_REF);

  @Override
  public Set<HighlightInfoType> supportedHighlightInfoTypes() {
    if (!enabled.getValue()) {
      return ImmutableSet.of();
    }
    return SUPPORTED_HIGHLIGHT_TYPES;
  }

  @Override
  public boolean canProcessFile(PsiFile psiFile) {
    if (!enabled.getValue() || !SUPPORTED_FILE_TYPES.contains(psiFile.getFileType())) {
      return false;
    }
    return ProjectUnresolvedResourceStatsCollector.getInstance(psiFile.getProject())
        .canProcessFile(psiFile);
  }

  @Override
  public void processHighlight(PsiElement psiElement, HighlightInfo highlightInfo) {
    if (!enabled.getValue()) {
      return;
    }
    ProjectUnresolvedResourceStatsCollector.getInstance(psiElement.getProject())
        .processHighlight(psiElement, highlightInfo);
  }
}
