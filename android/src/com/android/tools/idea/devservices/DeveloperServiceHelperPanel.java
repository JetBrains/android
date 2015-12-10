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
package com.android.tools.idea.devservices;

import com.android.tools.idea.structure.services.DeveloperService;
import com.android.tools.idea.structure.services.DeveloperServiceMetadata;
import com.android.utils.HtmlBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Component to wrap a given API's title, description, icon, and an 'Add' button.  This class also
 * contains the UI elements to present the confirmation dialog
 */
public final class DeveloperServiceHelperPanel extends JComponent {
  private JPanel myRootPanel;
  private JButton myAddButton;
  private JLabel myIconLabel;
  private JTextPane myDescriptionPane;
  private JTextPane myTitlePane;
  @NotNull private DeveloperService myDeveloperService;

  public DeveloperServiceHelperPanel(@NotNull DeveloperService service) {
    setLayout(new BorderLayout());
    myDeveloperService = service;
    DeveloperServiceMetadata metadata = service.getMetadata();
    myTitlePane.setText(metadata.getName());
    myDescriptionPane.setText(metadata.getDescription());
    myIconLabel.setIcon(metadata.getIcon());

    myAddButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        ConfirmAddDialog confirm = new ConfirmAddDialog(myDeveloperService.getMetadata());
        confirm.pack();
        confirm.showAndGet();
      }
    });

    add(myRootPanel);
  }

  private class ConfirmAddDialog extends DialogWrapper {

    private ProjectChangesPanel myContentPanel;

    private ConfirmAddDialog(@NotNull DeveloperServiceMetadata metadata) {
      super(getParent(), false);
      myContentPanel = new ProjectChangesPanel(metadata);
      init();
      setTitle("Add " + metadata.getName());
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      return myContentPanel;
    }
  }

  public static class ProjectChangesPanel extends JPanel {
    private JPanel myRootPanel;
    private JEditorPane myDetailsPane;
    private JBLabel mySummaryLabel;

    public ProjectChangesPanel(@NotNull DeveloperServiceMetadata metadata) {
      super(new BorderLayout());
      final String serviceName = metadata.getName();

      mySummaryLabel.setText("Adding " + serviceName + " will make the following changes to your project.");

      // TODO: Break out dialog contents to separate builder
      // Changes to build.gradle
      HtmlBuilder builder = new HtmlBuilder();
      builder.openHtmlBody();
      builder.addBold("build.gradle");
      builder.add(" will include these new dependencies:");
      builder.newline();

      builder.beginDiv("font-family:monospace;");  // Begin font-family:monospace;
      for (String dependency : metadata.getDependencies()) {
        builder.add("compile ");
        builder.beginSpan("color:green;font-weight:bold;");
        builder.add(dependency);
        builder.endSpan();
        builder.endDiv();  // End color:green;font-weight:bold;display:inline;
      }
      builder.endDiv();  // End font-family:monospace;
      builder.addHtml("<hr>");  // Horizontal line between sections.

      // Configuration file.
      builder.addBold("firebase-project.json");
      builder.add(" will be added to the project root directory");
      builder.addHtml("<hr>");  // Horizontal line between sections.
      builder.closeHtmlBody();

      System.out.println(builder.getHtml());
      myDetailsPane.setText(builder.getHtml());
      add(myRootPanel);
    }
  }
}