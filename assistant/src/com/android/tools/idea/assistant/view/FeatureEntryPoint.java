/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.idea.assistant.datamodel.AnalyticsProvider;
import com.android.tools.idea.assistant.datamodel.FeatureData;
import com.android.tools.idea.assistant.datamodel.TutorialData;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import org.jetbrains.annotations.NotNull;

import javax.accessibility.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Renderer and behaviors for a single feature in the {@code TutorialChooser}.
 */
public class FeatureEntryPoint extends JPanel implements Accessible {
  private boolean myExpanded = false;
  private JPanel myTutorialsList;
  private ActionListener myListener;
  private JLabel myArrow;
  private JPanel myTargetPane;
  private AnalyticsProvider myAnalyticsProvider;
  private FeatureData myFeature;
  private Project myProject;

  public FeatureEntryPoint(
    @NotNull FeatureData feature,
    @NotNull ActionListener listener,
    @NotNull AnalyticsProvider analyticsProvider,
    @NotNull Project project) {
    super(new VerticalFlowLayout(0, 0));
    setOpaque(false);

    myListener = listener;
    myAnalyticsProvider = analyticsProvider;
    myFeature = feature;
    myProject = project;

    String label = myFeature.getName();
    String description = myFeature.getDescription();

    myTargetPane = new JPanel();
    myTargetPane.setOpaque(false);
    myTargetPane.setLayout(new BoxLayout(myTargetPane, BoxLayout.X_AXIS));
    add(myTargetPane);

    SummaryHandler summaryMouseHandler = new SummaryHandler();
    KeyboardHandler keyboardhandler = new KeyboardHandler();

    myArrow = new JLabel();
    addHandlers(myArrow, summaryMouseHandler, keyboardhandler);
    myArrow.setIcon(AllIcons.General.ArrowRight);
    myArrow.setFocusable(true);
    myArrow.setBorder(BorderFactory.createEmptyBorder(9, 5, 0, 10));
    myArrow.setAlignmentY(Component.TOP_ALIGNMENT);
    myTargetPane.add(myArrow);

    JPanel summary = new JPanel(new VerticalFlowLayout(0, 0));
    summary.setOpaque(false);
    summary.setAlignmentY(Component.TOP_ALIGNMENT);
    myTargetPane.add(summary);

    // Amount to horizontally offset contents to adjust for the presence
    // of a feature icon.
    int innerContentsOffset = 0;

    JBLabel featureLabel = new JBLabel();
    addHandlers(featureLabel, summaryMouseHandler, keyboardhandler);
    featureLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 5));
    featureLabel.setFont(featureLabel.getFont().deriveFont(Font.BOLD));
    featureLabel.setText(label);
    Icon featureIcon = myFeature.getIcon();
    if (featureIcon != null) {
      featureLabel.setIcon(featureIcon);
      featureLabel.setIconTextGap(5);
      innerContentsOffset += featureIcon.getIconWidth() + featureLabel.getIconTextGap();
    }
    summary.add(featureLabel);

    JTextPane descriptionPane = new JTextPane();
    descriptionPane.setOpaque(false);
    addHandlers(descriptionPane, summaryMouseHandler, keyboardhandler);
    UIUtils.setHtml(descriptionPane, description, "body {color: " + UIUtils.getCssColor(UIUtils.getSecondaryColor()) + "}");
    descriptionPane.setBorder(BorderFactory.createEmptyBorder(0, innerContentsOffset, 5, 10));
    summary.add(descriptionPane);

    myTutorialsList = new JPanel();
    myTutorialsList.setOpaque(false);
    myTutorialsList.setLayout(new BoxLayout(myTutorialsList, BoxLayout.Y_AXIS));
    myTutorialsList.setBorder(BorderFactory.createEmptyBorder(5, myArrow.getPreferredSize().width + innerContentsOffset, 0, 5));
    myTutorialsList.setVisible(false);
    for (TutorialData tutorial : myFeature.getTutorials()) {
      addTutorial(tutorial.getLabel(), tutorial.getKey());
    }
    add(myTutorialsList);
    if (feature.displayTutorials()) {
     toggleTutorials();
    }
  }

  private void addHandlers(JComponent component, SummaryHandler summaryMouseHandler, KeyboardHandler keyboardHandler) {
    component.addMouseListener(summaryMouseHandler);
    component.registerKeyboardAction(keyboardHandler, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), JComponent.WHEN_FOCUSED);
  }

  private static Logger getLog() {
    return Logger.getInstance(FeatureEntryPoint.class);
  }

  private void addTutorial(String label, String key) {
    TutorialButton t = new TutorialButton(label, key, myListener);
    myTutorialsList.add(t);
    myTutorialsList.add(Box.createRigidArea(new Dimension(0, 10)));
  }

  /**
   * Toggles the visibility of the tutorial list associated with the given
   * feature.
   */
  private void toggleTutorials() {
    myExpanded = !myExpanded;
    getLog().debug("Toggled feature summary view to expand state: " + myExpanded);
    if (myExpanded) {
      myAnalyticsProvider.trackFeatureGroupExpanded(myFeature.getName(), myProject);
    }
    // Update the related icon to show whether the feature summary is in an
    // expanded state.
    myArrow.setIcon(myExpanded ? AllIcons.General.ArrowDown : AllIcons.General.ArrowRight);
    myTutorialsList.setVisible(myExpanded);
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (this.accessibleContext == null) {
      this.accessibleContext = new AccessibleFeatureEntryPoint();
    }

    return this.accessibleContext;
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

  private class KeyboardHandler implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      toggleTutorials();
    }
  }

  /**
   * Accessible class for {@link FeatureEntryPoint} treats it as an expandable (toggle) button.
   */
  private class AccessibleFeatureEntryPoint extends JComponent.AccessibleJComponent implements AccessibleAction {
    @Override
    public AccessibleRole getAccessibleRole() {
      return AccessibleRole.TOGGLE_BUTTON;
    }

    @Override
    public String getAccessibleName() {
      return myFeature.getName();
    }

    @Override
    public String getAccessibleDescription() {
      return AccessibleContextUtil.getUniqueDescription(this, myFeature.getDescription());
    }

    @Override
    public AccessibleIcon[] getAccessibleIcon() {
      Icon icon = AllIcons.General.ArrowRight;
      if (icon instanceof Accessible) {
        AccessibleContext context = ((Accessible)icon).getAccessibleContext();
        if (context != null && context instanceof AccessibleIcon) {
          return new AccessibleIcon[]{(AccessibleIcon)context};
        }
      }

      return null;
    }

    @Override
    public AccessibleStateSet getAccessibleStateSet() {
      AccessibleStateSet stateSet = super.getAccessibleStateSet();
      stateSet.add(myExpanded ? AccessibleState.EXPANDED : AccessibleState.EXPANDABLE);
      if (FeatureEntryPoint.this.isFocusOwner()) {
        stateSet.add(AccessibleState.FOCUSED);
      }
      return stateSet;
    }

    @Override
    public AccessibleAction getAccessibleAction() {
      return this;
    }

    @Override
    public int getAccessibleActionCount() {
      return 1;
    }

    @Override
    public String getAccessibleActionDescription(int index) {
      return index == 0 ? UIManager.getString("AbstractButton.clickText") : null;
    }

    @Override
    public boolean doAccessibleAction(int index) {
      if (index == 0) {
        toggleTutorials();
        return true;
      }
      return false;
    }
  }
}
