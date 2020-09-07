/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.upgrade;

import static com.android.tools.adtui.HtmlLabel.setUpAsHtmlLabel;
import static com.intellij.ide.BrowserUtil.browse;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.util.ui.JBDimension;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.event.HyperlinkEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AgpUpgradeRefactoringProcessorCannotUpgradeDialog extends DialogWrapper {
  private JEditorPane myEditorPane;
  private JPanel myPanel;

  AgpUpgradeRefactoringProcessorCannotUpgradeDialog(@NotNull AgpUpgradeRefactoringProcessor processor) {
    super(processor.getProject());

    setTitle("Android Gradle Plugin Upgrade Assistant");
    init();

    setUpAsHtmlLabel(myEditorPane);
    myEditorPane.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        browse(e.getURL());
      }
    });

    StringBuilder sb = new StringBuilder();
    sb.append("<p>The Upgrade Assistant failed to upgrade this project, finding no way of performing the ");
    sb.append("<b>").append(processor.getCommandName()).append("</b> command, ");
    sb.append("possibly because the project's build files use features not currently supported by the Upgrade Assistant ");
    sb.append("(for example: using constants defined in <tt>buildSrc</tt>, or other unrecognized constructs, in Gradle build files).</p>");

    myEditorPane.setText(sb.toString());
    myPanel.setPreferredSize(new JBDimension(500, -1));
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected Action @NotNull [] createActions() {
    return new Action[] { getOKAction() };
  }
}
