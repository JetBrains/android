/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync;

import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A filter to disable highlights when query sync is enabled and dependencies are not built. */
public class QuerySyncHighlightingFilter implements HighlightInfoFilter {

  @Override
  public boolean accept(@NotNull HighlightInfo highlightInfo, @Nullable PsiFile psiFile) {
    if (psiFile == null) {
      return true;
    }

    if (Blaze.getProjectType(psiFile.getProject()) == ProjectType.QUERY_SYNC) {
      return QuerySyncManager.getInstance(psiFile.getProject()).isReadyForAnalysis(psiFile);
    } else {
      return true;
    }
  }
}
