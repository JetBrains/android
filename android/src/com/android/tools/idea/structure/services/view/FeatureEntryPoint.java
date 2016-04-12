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
package com.android.tools.idea.structure.services.view;

import com.android.tools.idea.structure.services.datamodel.FeatureData;
import com.android.tools.idea.structure.services.datamodel.TutorialData;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.components.JBLabel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;


/**
 * Renderer and behaviors for a single service in the {@code TutorialChooser}.
 *
 * TODO: Either move to a form or clean up instantiation of visual elements.
 * TODO: Refactor everything related to "service" to "feature" or "API" as
 * service is an artifact of "Developer Services" which we don't need to
 * inherit in our own code.
 */
public class FeatureEntryPoint extends JPanel {
  private String myLabel;
  private String myDescription;
  private List<TutorialButton> myTutorials = new ArrayList<TutorialButton>();
  private boolean myExpanded = false;
  private JPanel myTutorialsList;
  private JPanel mySummary;
  private ActionListener myListener;
  private JLabel myArrow;
  private SummaryHandler mySummaryClickHandler = new SummaryHandler();

  public FeatureEntryPoint(FeatureData feature, ActionListener listener) {
    myLabel = feature.getName();
    myDescription = feature.getDescription();
    myListener = listener;

    // TODO: Migrate this somewhere central as the same font family should be used
    // for everything except code display.
    String fontFamily = getFont().getFamily();

    // Create encapsulating layout for ease of event handling, entire section
    // is clickable to toggle expansion of tutorials.
    setLayout(new BorderLayout());
    mySummary = new JPanel(new GridBagLayout());
    mySummary.setOpaque(false);
    mySummary.addMouseListener(mySummaryClickHandler);
    setOpaque(false);

    GridBagConstraints arrowConstraints = new GridBagConstraints();
    arrowConstraints.insets = new Insets(5, 10, 0, 5);
    arrowConstraints.gridx = 0;
    arrowConstraints.gridy = 0;
    arrowConstraints.weightx = 0;
    arrowConstraints.weighty = 0;
    arrowConstraints.anchor = GridBagConstraints.NORTHWEST;
    arrowConstraints.gridheight = 1;
    arrowConstraints.gridwidth = 1;
    arrowConstraints.fill = GridBagConstraints.VERTICAL;
    myArrow = new JLabel();
    myArrow.setIcon(AllIcons.Nodes.TreeExpandNode);
    mySummary.add(myArrow, arrowConstraints);

    GridBagConstraints labelConstraints = new GridBagConstraints();
    labelConstraints.insets = new Insets(10, 0, 5, 10);
    labelConstraints.gridx = 1;
    labelConstraints.gridy = 0;
    labelConstraints.weightx = 1;
    labelConstraints.weighty = 1;
    labelConstraints.fill = GridBagConstraints.BOTH;
    labelConstraints.anchor = GridBagConstraints.NORTHWEST;
    labelConstraints.gridheight = 1;
    labelConstraints.gridwidth = 1;
    JBLabel serviceLabel = new JBLabel();
    Font font = serviceLabel.getFont();
    Font boldFont = new Font(font.getFontName(), Font.BOLD, font.getSize());
    serviceLabel.setFont(boldFont);
    serviceLabel.setText(myLabel);
    Icon featureIcon = feature.getIcon();
    if (featureIcon != null) {
      serviceLabel.setIcon(featureIcon);
    }
    mySummary.add(serviceLabel, labelConstraints);

    GridBagConstraints descriptionConstraints = new GridBagConstraints();
    int descriptionWidthOffset = 0;
    // If an icon is present, shift the description to align with label text.
    if (featureIcon != null) {
      descriptionWidthOffset += featureIcon.getIconWidth();
    }
    descriptionConstraints.insets = new Insets(0, descriptionWidthOffset, 0, 10);
    descriptionConstraints.gridx = 1;
    descriptionConstraints.gridy = 1;
    descriptionConstraints.weightx = 1;
    descriptionConstraints.weighty = 1;
    descriptionConstraints.fill = GridBagConstraints.BOTH;
    descriptionConstraints.anchor = GridBagConstraints.NORTHWEST;
    descriptionConstraints.gridheight = 1;
    descriptionConstraints.gridwidth = 1;

    // TODO: Determine if this needs to be html.
    JTextPane descriptionPane = new JTextPane();
    // NOTE: When encapsulated in a scrollpane, content is not wrapping by
    // default. Setting preferred size addresses this (it expands width as
    // necessary) but we then have a fixed height.
    // TODO: Determine how we can add a scroller to the service list without
    // breaking line wrapping.
    descriptionPane.setOpaque(false);
    descriptionPane.setEditable(false);
    descriptionPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
    descriptionPane.setContentType("text/html");
    descriptionPane.setText("<html><head><style>body {font-family: " +
                            fontFamily +
                            ";}</style></head><body>" +
                            myDescription +
                            "</body></html>");

    mySummary.add(descriptionPane, descriptionConstraints);
    add(mySummary, BorderLayout.NORTH);

    myTutorialsList = new JPanel();
    myTutorialsList.setOpaque(false);
    myTutorialsList.setLayout(new BoxLayout(myTutorialsList, BoxLayout.Y_AXIS));
    int tutorialWidthOffset = 50;
    // If an icon is present, the description is shifted, shift the tutorial list similarly.
    if (featureIcon != null) {
      tutorialWidthOffset += featureIcon.getIconWidth();
    }
    myTutorialsList.setBorder(BorderFactory.createEmptyBorder(5, tutorialWidthOffset, 5, 5));
    myTutorialsList.setVisible(false);
    for (TutorialData tutorial : feature.getTutorials()) {
      addTutorial(tutorial.getLabel(), tutorial.getKey());
    }
    add(myTutorialsList, BorderLayout.CENTER);
  }

  private static Logger getLog() {
    return Logger.getInstance(FeatureEntryPoint.class);
  }

  private void addTutorial(String label, String key) {
    TutorialButton t = new TutorialButton(label, key, myListener);
    myTutorialsList.add(t);
    myTutorialsList.add(Box.createRigidArea(new Dimension(0, 10)));
    myTutorials.add(t);
  }

  /**
   * Toggles the visibility of the tutorial list associated with the given
   * service.
   */
  private void toggleTutorials() {
    myExpanded = !myExpanded;
    getLog().debug("Toggled service summary view to expand state: " + myExpanded);
    // Update the related icon to show whether the service summary is in an
    // expanded state.
    myArrow.setIcon(myExpanded ? AllIcons.Nodes.TreeCollapseNode : AllIcons.Nodes.TreeExpandNode);
    myTutorialsList.setVisible(myExpanded);
  }

  private class SummaryHandler extends MouseAdapter {
    @Override
    public void mouseClicked(MouseEvent e) {
      toggleTutorials();
    }
  }

}
