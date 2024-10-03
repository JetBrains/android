/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.bazel.codeinsight;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import java.util.List;

/** Compat class for LineWrappingUtil */
public final class LineWrappingUtil {
  public static void doWrapLongLinesIfNecessary(
      final Editor editor,
      final Project project,
      Document document,
      int startOffset,
      int endOffset,
      List<? extends TextRange> enabledRanges,
      int rightMargin) {
    com.intellij.formatting.LineWrappingUtil.doWrapLongLinesIfNecessary(
        editor, project, document, startOffset, endOffset, enabledRanges, rightMargin);
  }
}
