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
import com.android.tools.idea.assistant.datamodel.StepData;
import com.android.tools.idea.assistant.datamodel.TutorialData;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Generic view for tutorial content. Represents a single view in a collection
 * of tutorials. Content is rendered via XML configured content and couples to
 * {@code TutorialChooser} where each card appears as a line item below their
 * related service.
 */
public class TutorialCard extends CardViewPanel {

  @NotNull
  private final TutorialData myTutorial;
  @NotNull
  private final FeatureData myFeature;
  @NotNull
  private final Project myProject;
  private final boolean myHideChooserAndNavigationBar;
  private final boolean myIsStepByStep;
  @NotNull
  private final JBScrollPane myContentsScroller;

  /**
   * Partial label used in the back button.
   */
  @NotNull
  private final String myTutorialsTitle;

  private int myStepIndex;

  TutorialCard(@NotNull ActionListener listener,
               @NotNull TutorialData tutorial,
               @NotNull FeatureData feature,
               @NotNull String tutorialsTitle,
               @NotNull Project project,
               boolean hideChooserAndNavigationalBar,
               boolean isStepByStep) {
    super(listener);
    myContentsScroller = new JBScrollPane();
    myTutorialsTitle = tutorialsTitle;
    myTutorial = tutorial;
    myFeature = feature;
    myProject = project;
    myHideChooserAndNavigationBar = hideChooserAndNavigationalBar;
    myIsStepByStep = isStepByStep;
    myStepIndex = 0;

    if (!myHideChooserAndNavigationBar) {
      // TODO: Add a short label to the xml and use that here instead.
      add(new HeaderNav(myFeature.getName(), myListener), BorderLayout.NORTH);
    }

    add(myContentsScroller, BorderLayout.CENTER);

    // add nav for step by step tutorials
    if (myIsStepByStep) {
      add(new StepByStepFooter(), BorderLayout.SOUTH);
    }
    redraw();
  }

  /**
   * Using the visibility being enabled on card change as a cheap way to do re-init of of the component.
   */
  @Override
  public void setVisible(boolean aFlag) {
    super.setVisible(aFlag);
    JScrollBar verticalScrollBar = myContentsScroller.getVerticalScrollBar();
    JScrollBar horizontalScrollBar = myContentsScroller.getHorizontalScrollBar();
    verticalScrollBar.setValue(verticalScrollBar.getMinimum());
    horizontalScrollBar.setValue(horizontalScrollBar.getMinimum());
  }

  // update the view
  private void redraw() {
    JPanel contents = new JPanel();
    contents.setLayout(new GridBagLayout());
    contents.setOpaque(false);
    contents.setAlignmentX(LEFT_ALIGNMENT);
    GridBagConstraints c = new GridBagConstraints();
    c.gridx = 0;
    c.gridy = 0;
    c.weightx = 1;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.anchor = GridBagConstraints.NORTHWEST;
    c.insets = JBUI.insetsBottom(5);

    JBLabel title = new JBLabel(myTutorial.getLabel());
    title.setFont(title.getFont().deriveFont(Font.BOLD, 16F));
    title.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
    if (myTutorial.getIcon() != null) {
      title.setIcon(myTutorial.getIcon());
    }
    contents.add(title, c);
    c.gridy++;

    TutorialDescription description = new TutorialDescription();
    StringBuffer sb = new StringBuffer();
    sb.append("<p class=\"description\">").append(myTutorial.getDescription());
    if (myTutorial.getRemoteLink() != null && myTutorial.getRemoteLinkLabel() != null) {
      sb.append("<br><br><a href=\"").append(myTutorial.getRemoteLink()).append("\" target=\"_blank\">")
        .append(myTutorial.getRemoteLinkLabel()).append("</a>");
    }
    sb.append("</p>");
    UIUtils.setHtml(description, sb.toString(), ".description { margin: 10px;}");

    contents.add(description, c);
    c.gridy++;

    // Add extra padding for tutorial steps.
    c.insets = JBUI.insets(0, 5, 5, 5);

    if (myIsStepByStep) {
      contents.add(new TutorialStep(myTutorial.getSteps().get(myStepIndex), myStepIndex, myListener, myProject), c);
      c.gridy++;
    }
    else {
      // Add each of the tutorial steps in order.
      int numericLabel = 0;

      for (StepData step : myTutorial.getSteps()) {
        TutorialStep stepDisplay = new TutorialStep(step, numericLabel, myListener, myProject);
        contents.add(stepDisplay, c);
        c.gridy++;
        numericLabel++;
      }
    }

    GridBagConstraints glueConstraints = UIUtils.getVerticalGlueConstraints(c.gridy);
    contents.add(Box.createVerticalGlue(), glueConstraints);
    c.gridy++;

    if (!myHideChooserAndNavigationBar) {
      // remove insets for footer.
      c.insets = JBUI.emptyInsets();
      contents.add(new FooterNav(), c);
    }

    // HACK ALERT: For an unknown reason (possibly race condition calculating inner contents)
    // this scrolls exceptionally slowly without an explicit increment. Using fixed values is not
    // uncommon and the values appear to range by use (ranging from 10 to 20). Choosing a middling
    // rate to account for typically long content.
    myContentsScroller.getVerticalScrollBar().setUnitIncrement(16);
    myContentsScroller.setViewportView(contents);
    myContentsScroller.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UIUtils.getSeparatorColor()));
    myContentsScroller.setViewportBorder(BorderFactory.createEmptyBorder());
    myContentsScroller.setOpaque(false);
    myContentsScroller.getViewport().setOpaque(false);
    myContentsScroller.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
  }

  private static class TutorialDescription extends JTextPane {
    TutorialDescription() {
      super();
      setOpaque(false);
      setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UIUtils.getSeparatorColor()));
    }
  }

  /**
   * A fixed header component to be displayed above tutorial cards. This control serves as:
   * 1. rudimentary breadcrumbs
   * 2. a title for the card
   * 3. navigation back to the root view
   *
   * TODO: Consider stealing more from NavBarPanel.
   */
  private class HeaderNav extends JPanel {

    public final String ROOT_TITLE = "<html><b>" + myTutorialsTitle + "</b> &nbsp;&rsaquo;</html>";

    HeaderNav(String location, ActionListener listener) {
      super(new HorizontalLayout(5, SwingConstants.CENTER));
      setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

      add(new BackButton(ROOT_TITLE));

      JBLabel label = new JBLabel(location);
      label.setForeground(UIUtils.getSecondaryColor());
      add(label);
    }
  }

  private class FooterNav extends JPanel {
    private final String BACK_LABEL = "Back to " + myTutorialsTitle;

    FooterNav() {
      super(new FlowLayout(FlowLayout.LEADING));
      setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UIUtils.getSeparatorColor()));
      setOpaque(false);
      add(new BackButton(BACK_LABEL));
    }
  }

  private class StepByStepFooter extends JPanel {
    @NotNull
    private final StepButton myPrevButton;
    @NotNull
    private final StepButton myNextButton;

    StepByStepFooter() {
      super(new BorderLayout());
      myPrevButton = new StepButton("Previous", StepButton.Direction.PREV, e -> handleStepButtonClick(e));
      add(myPrevButton, BorderLayout.LINE_START);
      myNextButton = new StepButton("Next", StepButton.Direction.NEXT, e -> handleStepButtonClick(e));
      add(myNextButton, BorderLayout.LINE_END);

      updateVisibility();
      setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UIUtils.getSeparatorColor()));
    }

    private void handleStepButtonClick(@NotNull ActionEvent event) {
      Object source = event.getSource();
      StepButton stepButton = (StepButton)source;
      if (stepButton.myDirection == StepButton.Direction.NEXT) {
        if (myStepIndex < myTutorial.getSteps().size() - 1) {
          myStepIndex++;
        } else {
          closeAssistant();
        }
      }
      else if (myStepIndex > 0) {
        myStepIndex--;
      }

      updateVisibility();

      redraw(); // triggers the overall tutorial card to redraw
      repaint();
    }

    private void closeAssistant() {
      ToolWindowManager.getInstance(myProject).getToolWindow("Assistant").hide(() -> {});
    }

    private void updateVisibility() {
      myPrevButton.setVisible(myStepIndex > 0);
      myNextButton.setText(myStepIndex < myTutorial.getSteps().size() - 1 ? "Next" : "Finish");
    }
  }

  private static class StepButton extends NavigationButton {
    public enum Direction {
      NEXT,
      PREV
    }

    public Direction myDirection;

    public StepButton(String label, Direction direction, ActionListener listener) {
      super(label, TutorialChooser.NAVIGATION_KEY, listener);
      myDirection = direction;
      setContentAreaFilled(false);
      setBorderPainted(false);
      setBorder(null);
      setOpaque(false);
      if (Direction.NEXT == direction) {
        setIcon(AllIcons.Diff.CurrentLine);
        setHorizontalTextPosition(LEFT);
      }
      else {
        setIcon(AllIcons.Actions.Back);
        setHorizontalTextPosition(RIGHT);
      }

      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

      // As the button is presented as a label, use the label font.
      Font font = new JBLabel().getFont();
      setFont(font.deriveFont(Font.PLAIN, font.getSize()));
    }
  }

  // Determine why the border, contentfill, etc are reset to default on theme change. Note that this doesn't persist across restart.
  private class BackButton extends NavigationButton {

    public BackButton(String label) {
      super(label, TutorialChooser.NAVIGATION_KEY, myListener);
      setIcon(AllIcons.Actions.Back);
      setHorizontalTextPosition(RIGHT);
      setContentAreaFilled(false);
      setBorderPainted(false);
      setBorder(null);
      setOpaque(false);
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

      // As the button is presented as a label, use the label font.
      Font font = new JBLabel().getFont();
      setFont(new Font(font.getFontName(), Font.PLAIN, font.getSize()));
    }
  }
}
