package com.google.idea.sdkcompat.codeinsight;

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
