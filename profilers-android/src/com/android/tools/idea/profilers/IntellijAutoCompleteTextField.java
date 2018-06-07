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
package com.android.tools.idea.profilers;

import com.android.tools.profilers.AutoCompleteTextField;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.project.Project;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.ui.TextFieldWithAutoCompletionListProvider;
import com.intellij.util.textCompletion.TextFieldWithCompletion;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;

public class IntellijAutoCompleteTextField implements AutoCompleteTextField {
  @NotNull private final TextFieldWithCompletion myComponent;

  IntellijAutoCompleteTextField(@NotNull Project project,
                                @NotNull String placeholder,
                                @NotNull String value,
                                @NotNull Collection<String> variants) {
    TextFieldWithAutoCompletionListProvider<String> provider = new TextFieldWithAutoCompletion.StringsCompletionProvider(
      variants, null);
    myComponent = new TextFieldWithCompletion(project, provider, value, true, true, true);
    myComponent.setPlaceholder(placeholder);
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }

  @Override
  public void addOnDocumentChange(@NotNull Runnable callback) {
    myComponent.addDocumentListener(new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        callback.run();
      }
    });
  }

  @NotNull
  @Override
  public String getText() {
    return myComponent.getText();
  }
}