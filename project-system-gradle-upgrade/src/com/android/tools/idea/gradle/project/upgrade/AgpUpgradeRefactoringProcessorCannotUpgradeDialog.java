/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ui.JBDimension;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
    setupUI();

    setTitle("Android Gradle Plugin Upgrade Assistant");
    init();

    setUpAsHtmlLabel(myEditorPane);
    myEditorPane.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(@NotNull HyperlinkEvent e) {
        browse(e.getURL());
      }
    });

    StringBuilder sb = new StringBuilder();
    sb.append("<p><b>Incompatibility between Android Studio and Android Gradle plugin</b></p>");
    sb.append("<p>This project is using Android Gradle plugin version ").append(processor.getCurrent())
      .append(", which is incompatible with this version of Android Studio.</p>");
    List<AgpUpgradeComponentRefactoringProcessor> blockedComponents = processor.getComponentRefactoringProcessors().stream()
      .filter((c) -> c.isEnabled() && c.isBlocked()).collect(Collectors.toList());
    sb.append("<br/><p>The Upgrade Assistant failed to upgrade this project, finding no way of performing the following command")
      .append(blockedComponents.size() == 1 ? "" : "s").append(":</p>");
    sb.append("<ul>");
    blockedComponents.forEach((c) -> {
      sb.append("<li>").append(c.getCommandName()).append("<ul>");
      c.blockProcessorReasons().forEach((r) -> {
        sb.append("<li>").append(r.getShortDescription());
        String description = r.getDescription();
        if (description != null) {
          sb.append("<br>").append(description);
        }
      });
      sb.append("</ul>");
    });
    sb.append("</ul>");

    myEditorPane.setText(sb.toString());
    myPanel.setPreferredSize(new JBDimension(500, -1));
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction()};
  }

  private void setupUI() {
    myPanel = new JPanel();
    myPanel.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
    myEditorPane = new JEditorPane();
    myPanel.add(myEditorPane, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                  GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_WANT_GROW, null,
                                                  new Dimension(150, 50), null, 0, false));
    final Spacer spacer1 = new Spacer();
    myPanel.add(spacer1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                             GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
  }
}
