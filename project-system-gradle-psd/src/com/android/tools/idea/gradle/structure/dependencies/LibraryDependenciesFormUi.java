/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.dependencies;

import com.intellij.ui.SimpleColoredComponent;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;

class LibraryDependenciesFormUi {

  protected JPanel myMainPanel;
  protected SimpleColoredComponent myLibraryLabel;
  protected JPanel mySearchPanelHost;

  LibraryDependenciesFormUi() {
    myLibraryLabel.setBorder(UIUtil.getTextFieldBorder());
    myLibraryLabel.setIpad(JBInsets.emptyInsets());
    myLibraryLabel.clear();
  }
}
