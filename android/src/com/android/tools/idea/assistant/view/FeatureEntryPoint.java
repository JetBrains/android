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
package com.android.tools.idea.assistant.view;

import com.android.tools.idea.assistant.datamodel.FeatureData;
import com.android.tools.idea.assistant.datamodel.TutorialData;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.VerticalFlowLayout;
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
  private SummaryHandler mySummaryMouseHandler = new SummaryHandler();
  private JPanel myTargetPane;

  public FeatureEntryPoint(FeatureData feature, ActionListener listener) {
    super(new VerticalFlowLayout(0, 5));
    setOpaque(false);

    myLabel = feature.getName();
    myDescription = feature.getDescription();
    myListener = listener;

    myTargetPane = new JPanel();
    myTargetPane.setOpaque(false);
    myTargetPane.setLayout(new BoxLayout(myTargetPane, BoxLayout.X_AXIS));
    add(myTargetPane);

    myArrow = new JLabel();
    myArrow.addMouseListener(mySummaryMouseHandler);
    myArrow.setIcon(AllIcons.Nodes.TreeRightArrow);
    myArrow.setFocusable(true);
    myArrow.setBorder(BorderFactory.createEmptyBorder(9, 5, 0, 5));
    myArrow.setAlignmentY(Component.TOP_ALIGNMENT);
    myTargetPane.add(myArrow);

    JPanel summary = new JPanel(new VerticalFlowLayout(0,0));
    summary.setOpaque(false);
    summary.setAlignmentY(Component.TOP_ALIGNMENT);
    myTargetPane.add(summary);

    // Amount to horizontally offset contents to adjust for the presence
    // of a feature icon.
    int innerContentsOffset = 0;

    JBLabel serviceLabel = new JBLabel();
    serviceLabel.addMouseListener(mySummaryMouseHandler);
    serviceLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 5));
    serviceLabel.setFont(serviceLabel.getFont().deriveFont(Font.BOLD));
    serviceLabel.setText(myLabel);
    Icon featureIcon = feature.getIcon();
    if (featureIcon != null) {
      serviceLabel.setIcon(featureIcon);
      innerContentsOffset +=  featureIcon.getIconWidth() + serviceLabel.getIconTextGap();
    }
    summary.add(serviceLabel);

    JTextPane descriptionPane = new JTextPane();
    descriptionPane.setOpaque(false);
    descriptionPane.addMouseListener(mySummaryMouseHandler);
    UIUtils.setHtml(descriptionPane, myDescription, "body {color: " + UIUtils.getCssColor(UIUtils.getSecondaryColor()) + "}");
    descriptionPane.setBorder(BorderFactory.createEmptyBorder(0, innerContentsOffset, 5, 10));
    summary.add(descriptionPane);

    myTutorialsList = new JPanel();
    myTutorialsList.setOpaque(false);
    myTutorialsList.setLayout(new BoxLayout(myTutorialsList, BoxLayout.Y_AXIS));
    myTutorialsList.setBorder(BorderFactory.createEmptyBorder(0, 50 + innerContentsOffset, 0, 5));
    myTutorialsList.setVisible(false);
    for (TutorialData tutorial : feature.getTutorials()) {
      addTutorial(tutorial.getLabel(), tutorial.getKey());
    }
    add(myTutorialsList);
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
    myArrow.setIcon(myExpanded ? AllIcons.Nodes.TreeDownArrow : AllIcons.Nodes.TreeRightArrow);
    myTutorialsList.setVisible(myExpanded);
  }

  private class SummaryHandler extends MouseAdapter {

    @Override
    public void mouseClicked(MouseEvent e) {
      toggleTutorials();
    }

    @Override
    public void mouseEntered(MouseEvent e) {
      // Null out the background before setting otherwise Swing doesn't appear to realize there's a change and doesn't update.
      myTargetPane.setBackground(null);
      myTargetPane.setOpaque(true);
      myTargetPane.setBackground(UIUtils.getBackgroundHoverColor());
    }

    @Override
    public void mouseExited(MouseEvent e) {
      myTargetPane.setOpaque(false);
      myTargetPane.setBackground(null);
    }
  }

}
