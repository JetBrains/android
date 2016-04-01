/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.lint;

import org.jetbrains.annotations.NotNull;
import com.android.tools.idea.uibuilder.editor.NlEditorPanel;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.codeHighlighting.HighlightingPass;
import org.jetbrains.annotations.NotNull;

public class NlBackgroundEditorHighlighter implements BackgroundEditorHighlighter {
  private final HighlightingPass[] myHighlightingPasses;

  public NlBackgroundEditorHighlighter(@NotNull NlEditorPanel editorPanel) {
    myHighlightingPasses = new HighlightingPass[]{new NlLintHighlightingPass(editorPanel.getSurface())};
  }

  @NotNull
  @Override
  public HighlightingPass[] createPassesForEditor() {
    return myHighlightingPasses;
  }

  @NotNull
  @Override
  public HighlightingPass[] createPassesForVisibleArea() {
    return HighlightingPass.EMPTY_ARRAY;
  }
}
