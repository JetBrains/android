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
package com.android.tools.idea.structure.services.view;

import com.android.tools.idea.structure.EditorPanel;
import com.android.tools.idea.structure.services.DeveloperService;
import com.android.tools.idea.structure.services.DeveloperServiceMetadata;
import com.android.tools.idea.structure.services.ServiceContext;
import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.InvalidationListener;
import com.android.tools.idea.ui.properties.ObservableValue;
import com.android.tools.idea.ui.properties.swing.SelectedProperty;
import com.android.tools.idea.ui.properties.swing.VisibleProperty;
import com.android.utils.HtmlBuilder;
import com.google.common.base.Joiner;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.util.List;

import static com.android.tools.idea.ui.properties.expressions.bool.BooleanExpressions.not;

/**
 * Panel that provides a UI view onto a {@link DeveloperServiceMetadata}.
 */
public final class DeveloperServicePanel extends EditorPanel {

  public static final String DELETE_SERVICE_TITLE = "Confirm Uninstall Service";

  public static final String DELETE_SERVICE_MESSAGE =
    "You are about to uninstall the %1$s service. This will remove the following dependencies:\n" +
    "\n" +
    "%2$s\n" +
    "\n" +
    "This may cause compile errors that you'll have to fix manually. Continue?";

  private JPanel myRootPanel;
  private JLabel myHeaderLabel;
  private JPanel myDetailsPanel;
  private JLabel myIcon;
  private JPanel myLinksPanel;
  private JPanel mySummaryPanel;
  private JPanel myOverviewPanel;
  private JCheckBox myEnabledCheckbox;
  private JPanel myCheckboxBorder;

  @NotNull private DeveloperService myService;
  @NotNull private BindingsManager myBindings = new BindingsManager();

  public DeveloperServicePanel(@NotNull DeveloperService service) {
    super(new BorderLayout());
    myService = service;
    ServiceContext context = service.getContext();

    DeveloperServiceMetadata developerServiceMetadata = service.getMetadata();

    initializeHeaderPanel(developerServiceMetadata);
    myDetailsPanel.add(service.getPanel());
    initializeFooterPanel(developerServiceMetadata);

    final SelectedProperty enabledCheckboxSelected = new SelectedProperty(myEnabledCheckbox);
    myBindings.bind(new VisibleProperty(myDetailsPanel), enabledCheckboxSelected.and(not(context.installed())));

    // This definition might be modified from the user interacting with the service earlier but not
    // yet committing to install it.
    myBindings.bind(new SelectedProperty(myEnabledCheckbox), context.installed().or(context.modified()));

    enabledCheckboxSelected.addListener(new InvalidationListener() {
      @Override
      public void onInvalidated(@NotNull ObservableValue<?> sender) {
        if (enabledCheckboxSelected.get()) {
          // User just selected a service which was previous uninstalled
          myService.getContext().beginEditing();
        }
        else {
          if (myService.getContext().installed().get()) {
            // User just deselected a service which was previous installed
            String message = String.format(DELETE_SERVICE_MESSAGE, myService.getMetadata().getName(),
                                           Joiner.on('\n').join(myService.getMetadata().getDependencies()));
            int answer = Messages.showYesNoDialog(myService.getModule().getProject(), message, DELETE_SERVICE_TITLE, null);
            if (answer == Messages.YES) {
              myService.uninstall();
            }
            else {
              enabledCheckboxSelected.set(true);
            }
          }
          else {
            // User just deselected a service they were editing but hadn't installed yet
            myService.getContext().cancelEditing();
          }
        }
      }
    });

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

    verticalFlowPanel.add(Box.createRigidArea(new Dimension(0, JBUI.scale(30))));
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
      myLinksPanel.add(Box.createRigidArea(new Dimension(JBUI.scale(10), 0)));
    }
    myLinksPanel.add(hyperlinkLabel);
  }

  @Override
  public void apply() {
    myService.getContext().finishEditing();

    if (!isModified()) {
      return;
    }

    myService.install();
  }

  @Override
  public boolean isModified() {
    return myService.getContext().modified().get();
  }

  public void dispose() {
    myDetailsPanel.removeAll();
  }
}
