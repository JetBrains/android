/*
 * Copyright (C) 2021 The Android Open Source Project
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
package org.jetbrains.android.refactoring.errorreporter;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class HintBasedErrorReporter implements ErrorReporter {
  private final Editor myEditor;

  public HintBasedErrorReporter(@NotNull Editor editor) {
    myEditor = editor;
  }

  @Override
  public void report(@NotNull String message, @NotNull String title) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      throw new IncorrectOperationException(message);
    }
    HintManager.getInstance().showErrorHint(myEditor, message);
  }
}
