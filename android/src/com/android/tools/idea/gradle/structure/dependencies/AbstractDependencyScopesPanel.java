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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.HyperlinkAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.List;

import static com.intellij.ide.BrowserUtil.browse;
import static com.intellij.util.ui.UIUtil.getTreeFont;
import static javax.swing.BorderFactory.createEmptyBorder;
import static org.jetbrains.android.util.AndroidUiUtil.setUpAsHtmlLabel;

public abstract class AbstractDependencyScopesPanel extends JPanel implements Disposable {
  protected AbstractDependencyScopesPanel() {
    super(new BorderLayout());
  }

  protected void setUpContents(@NotNull JComponent contents, @NotNull String instructions) {
    add(createInstructionsPane(instructions), BorderLayout.NORTH);
    add(contents, BorderLayout.CENTER);
  }

  @NotNull
  protected JEditorPane createInstructionsPane(@NotNull String instructions) {
    JEditorPane instructionsPane = new JEditorPane();
    setUpAsHtmlLabel(instructionsPane, getTreeFont());

    instructionsPane.setText("<html><body><b>Step 2.</b><br/>" + instructions + "</body></html>");
    instructionsPane.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        browse(e.getURL());
      }
    });
    instructionsPane.setBorder(createEmptyBorder(0, 5, 8, 5));
    return instructionsPane;
  }

  @NotNull
  public abstract List<String> getSelectedScopeNames();

  @Nullable
  public abstract ValidationInfo validateInput();
}
