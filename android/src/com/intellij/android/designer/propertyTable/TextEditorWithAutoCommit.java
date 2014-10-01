/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.intellij.android.designer.propertyTable;

import com.intellij.android.designer.propertyTable.editors.ResourceEditor;
import com.intellij.designer.propertyTable.editors.TextEditorWrapper;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Like the parent class {@link com.intellij.designer.propertyTable.editors.TextEditorWrapper}, but on
 * enter finishes editing instead of staying in edit mode
 */
public class TextEditorWithAutoCommit extends TextEditorWrapper {
  // The IntelliJ Text editor doesn't auto commit values which we want, so override that behavior here
  public TextEditorWithAutoCommit() {
    myTextField.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        fireValueCommitted(false, true);
      }
    });
    ResourceEditor.selectTextOnFocusGain(myTextField);
  }
}
