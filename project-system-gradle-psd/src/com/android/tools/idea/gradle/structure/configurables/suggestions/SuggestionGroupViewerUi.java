// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.gradle.structure.configurables.suggestions;

import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.panels.VerticalLayout;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class SuggestionGroupViewerUi {
  protected JPanel myView;
  protected JPanel myPanel;

  public SuggestionGroupViewerUi(String borderTitle) {
    myView.setLayout(new VerticalLayout(0));
    myView.setBorder(IdeBorderFactory.createBorder());
    myView.setLayout(new BoxLayout(myView, BoxLayout.Y_AXIS));
    myPanel.setBorder(IdeBorderFactory.createTitledBorder(borderTitle, false));
  }

  @NotNull
  public JPanel getView() {
    return myView;
  }

  @NotNull
  public JPanel getPanel() {
    return myPanel;
  }
}
