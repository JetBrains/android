/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.ui;

import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.TextComponentAccessors;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.TextFieldWithStoredHistory;
import javax.annotation.Nullable;

/** A file selector panel with text field, browse button and stored history. */
public class FileSelectorWithStoredHistory
    extends ComponentWithBrowseButton<TextFieldWithStoredHistory> {

  public static FileSelectorWithStoredHistory create(String historyKey, String title) {
    TextFieldWithStoredHistory textField = new TextFieldWithStoredHistory(historyKey);
    return new FileSelectorWithStoredHistory(textField, title);
  }

  private FileSelectorWithStoredHistory(TextFieldWithStoredHistory textField, String title) {
    super(textField, null);

    addBrowseFolderListener(
        title,
        "",
        null,
        BrowseFilesListener.SINGLE_FILE_DESCRIPTOR,
        TextComponentAccessors.TEXT_FIELD_WITH_STORED_HISTORY_WHOLE_TEXT);
  }

  /** Set the text without altering the history. */
  public void setText(@Nullable String text) {
    if (text == null) {
      getChildComponent().reset();
    } else {
      getChildComponent().setText(text);
    }
  }

  public void setTextWithHistory(@Nullable String text) {
    setText(text);
    if (text != null) {
      getChildComponent().addCurrentTextToHistory();
    }
  }

  @Nullable
  public String getText() {
    String text = getChildComponent().getText();
    return StringUtil.nullize(text);
  }
}
