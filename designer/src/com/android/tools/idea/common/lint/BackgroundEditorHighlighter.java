/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.common.lint;

import com.android.tools.idea.common.editor.DesignerEditorPanel;
import com.intellij.codeHighlighting.HighlightingPass;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;

public class BackgroundEditorHighlighter implements com.intellij.codeHighlighting.BackgroundEditorHighlighter {
  private final HighlightingPass[] myHighlightingPasses;

  public BackgroundEditorHighlighter(@NotNull DesignerEditorPanel editorPanel, @NotNull ModelLintIssueAnnotator annotator) {
    myHighlightingPasses = new HighlightingPass[]{ new DesignerEditorBackgroundHighlightingPass(editorPanel, annotator) };
  }

  @NotNull
  @Override
  public HighlightingPass[] createPassesForEditor() {
    return myHighlightingPasses;
  }
}
