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
package com.android.tools.idea.structure.developerServices.view;

import com.android.tools.idea.structure.EditorPanel;
import com.android.tools.idea.structure.developerServices.DeveloperService;
import com.android.tools.idea.structure.developerServices.DeveloperServiceMetadata;
import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.swing.SelectedProperty;
import com.android.tools.idea.ui.properties.swing.VisibleProperty;
import com.android.utils.HtmlBuilder;
import com.google.common.base.Joiner;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.util.List;

/**
 * Panel that provides a UI view onto a {@link DeveloperServiceMetadata}.
 */
public final class DeveloperServicePanel extends EditorPanel {

  @NotNull private DeveloperService myService;
  @NotNull private BindingsManager myBindings = new BindingsManager();
  private JPanel myRootPanel;
  private JLabel myHeaderLabel;
  private JPanel myDetailsPanel;
  private JLabel myIcon;
  private JPanel myLinksPanel;
  private JPanel mySummaryPanel;
  private JPanel myOverviewPanel;
  private JCheckBox myEnabledCheckbox;
  private JPanel myCheckboxBorder;

  public DeveloperServicePanel(@NotNull DeveloperService service) {
    super(new BorderLayout());
    myService = service;

    DeveloperServiceMetadata developerServiceMetadata = service.getMetadata();

    initializeHeaderPanel(developerServiceMetadata);
    myDetailsPanel.add(service.getPanel());
    initializeFooterPanel(developerServiceMetadata);

    myBindings.bind(new VisibleProperty(myDetailsPanel), new SelectedProperty(myEnabledCheckbox));
    if (myService.getContext().isInstalled().get()) {
      myEnabledCheckbox.setSelected(true);
    }

    add(myRootPanel);
  }

  private void initializeHeaderPanel(@NotNull DeveloperServiceMetadata developerServiceMetadata) {
    HtmlBuilder htmlBuilder = new HtmlBuilder();
    htmlBuilder.openHtmlBody();
    htmlBuilder.addBold(developerServiceMetadata.getName()).newline();
    htmlBuilder.add(developerServiceMetadata.getDescription());
    htmlBuilder.closeHtmlBody();
    myHeaderLabel.setText(htmlBuilder.getHtml());

    myIcon.setIcon(IconUtil.toSize(developerServiceMetadata.getIcon(), myIcon.getWidth(), myIcon.getHeight()));

    URI learnMoreLink = developerServiceMetadata.getLearnMoreLink();
    if (learnMoreLink != null) {
      addToLinkPanel("Learn More", learnMoreLink);
    }

    URI apiLink = developerServiceMetadata.getApiLink();
    if (apiLink != null) {
      addToLinkPanel("API Documentation", apiLink);
    }
  }

  private void initializeFooterPanel(@NotNull DeveloperServiceMetadata developerServiceMetadata) {
    boolean panelHasContent = false;

    JPanel verticalFlowPanel = new JPanel();
    verticalFlowPanel.setLayout(new BoxLayout(verticalFlowPanel, BoxLayout.PAGE_AXIS));

    verticalFlowPanel.add(Box.createRigidArea(new Dimension(0, 30)));
    final HtmlBuilder htmlBuilder = new HtmlBuilder();
    htmlBuilder.openHtmlBody();
    htmlBuilder.add("Enabling this service will...");
    htmlBuilder.beginList();

    List<String> dependencies = developerServiceMetadata.getDependencies();
    if (!dependencies.isEmpty()) {
      htmlBuilder.listItem().add("Add dependencies: ").addItalic(Joiner.on(", ").join(dependencies));
      panelHasContent = true;
    }

    List<String> permissions = developerServiceMetadata.getPermissions();
    if (!permissions.isEmpty()) {
      htmlBuilder.listItem().add("Add permissions: ").addItalic(Joiner.on(", ").join(permissions));
      panelHasContent = true;
    }

    List<String> modifiedFiles = developerServiceMetadata.getModifiedFiles();
    if (!modifiedFiles.isEmpty()) {
      htmlBuilder.listItem().add("Create/modify files: ").addItalic(Joiner.on(", ").join(modifiedFiles));
      panelHasContent = true;
    }

    htmlBuilder.endList();
    htmlBuilder.closeHtmlBody();

    if (panelHasContent) {
      verticalFlowPanel.add(new JLabel(htmlBuilder.getHtml()));
      mySummaryPanel.add(verticalFlowPanel);
    }
  }

  private void addToLinkPanel(@NotNull String text, @NotNull final URI uri) {
    HyperlinkLabel hyperlinkLabel = new HyperlinkLabel(text);
    hyperlinkLabel.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        try {
          Desktop.getDesktop().browse(uri);
        }
        catch (IOException ex) {
          // Don't care
        }
      }
    });

    // Setting the padding on myLinksPanel puts in ugly leading space, so we instead space links
    // apart using invisible rigid areas instead.
    if (myLinksPanel.getComponentCount() > 0) {
      myLinksPanel.add(Box.createRigidArea(new Dimension(10, 0)));
    }
    myLinksPanel.add(hyperlinkLabel);
  }

  @Override
  public void apply() {
    if (!isModified()) {
      return;
    }

    myService.install();
  }

  @Override
  public boolean isModified() {
    // TODO: Query our active definition's context to see if it is modified
    return false;
  }

  public void dispose() {
    myDetailsPanel.removeAll();
  }
}
