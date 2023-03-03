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

import com.android.tools.idea.assistant.AssistantToolWindowService;
import com.android.tools.idea.assistant.ScrollHandler;
import com.android.tools.idea.assistant.TutorialCardRefreshNotifier;
import com.android.tools.idea.assistant.datamodel.FeatureData;
import com.android.tools.idea.assistant.datamodel.StepData;
import com.android.tools.idea.assistant.datamodel.TutorialBundleData;
import com.android.tools.idea.assistant.datamodel.TutorialData;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.util.Arrays;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoundedRangeModel;
import javax.swing.Box;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Generic view for tutorial content. Represents a single view in a collection
 * of tutorials. Content is rendered via XML configured content and couples to
 * {@code TutorialChooser} where each card appears as a line item below their
 * related service.
 */
public class TutorialCard extends CardViewPanel {
  @NotNull private final JBScrollPane myContentsScroller;
  @NotNull private final TutorialData myTutorial;
  @NotNull private final Project myProject;
  @NotNull private final TutorialBundleData myBundle;
  @Nullable private final ScrollHandler myScrollHandler;

  private MessageBusConnection myConnection;
  private final boolean myHideChooserAndNavigationBar;
  private boolean myAdjustmentListenerInitialized;
  private int myStepIndex;

  // The card will not draw anything when it's not visible. This boolean checks if the card is drawn for the first time.
  private boolean myFirstTimeDrawn = false;

  TutorialCard(@NotNull FeaturesPanel featuresPanel,
               @NotNull TutorialData tutorial,
               @NotNull FeatureData feature,
               boolean hideChooserAndNavigationalBar,
               @NotNull TutorialBundleData bundle) {
    super(featuresPanel);
    myContentsScroller = new JBScrollPane();
    myTutorial = tutorial;
    myProject = featuresPanel.getProject();
    myBundle = bundle;
    myHideChooserAndNavigationBar = hideChooserAndNavigationalBar;
    myStepIndex = 0;
    myScrollHandler = ScrollHandler.EP_NAME.getExtensionList().stream()
      .filter(handler -> handler.getId().equals(bundle.getBundleCreatorId()))
      .findAny()
      .orElse(null);

    if (!myHideChooserAndNavigationBar) {
      // TODO: Add a short label to the xml and use that here instead.
      add(new HeaderNav(feature.getName()), BorderLayout.NORTH);
    }

    add(myContentsScroller, BorderLayout.CENTER);

    // add nav for step by step tutorials
    if (myBundle.isStepByStep()) {
      add(new StepByStepFooter(), BorderLayout.SOUTH);
    }

    if (!tutorial.shouldLoadLazily()) {
      // Draw now. Cards initially visible will not go through the {@link #setVisibility} calls.
      // Else, card can be loaded later. Draw when visibility changes.
      myFirstTimeDrawn = true;
      redraw();
    }
  }

  /**
   * Using the visibility being enabled on card change as a cheap way to do re-init of of the component.
   */
  @Override
  public void setVisible(boolean aFlag) {
    if (aFlag && !myFirstTimeDrawn) {
      // First time the card is visible.
      myFirstTimeDrawn = true;
      redraw();
    }
    super.setVisible(aFlag);
    JScrollBar verticalScrollBar = myContentsScroller.getVerticalScrollBar();
    JScrollBar horizontalScrollBar = myContentsScroller.getHorizontalScrollBar();
    verticalScrollBar.setValue(verticalScrollBar.getMinimum());
    horizontalScrollBar.setValue(horizontalScrollBar.getMinimum());

    if (!myAdjustmentListenerInitialized) {
      myAdjustmentListenerInitialized = true;

      if (myScrollHandler != null) {
        // Dispatch scrolledToBottom to the correct extension, can be used for metrics
        myContentsScroller.getVerticalScrollBar().addAdjustmentListener(this::trackScrolledToBottom);

        // If the window is opened large enough that a scrollbar is not needed, we consider this as scrolled to bottom
        if (!myContentsScroller.getVerticalScrollBar().isShowing()) {
          myScrollHandler.scrolledToBottom(myProject);
        }
      }
    }
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    if (myConnection != null) {
      myConnection.disconnect();
    }
  }

  @Override
  public void addNotify() {
    super.addNotify();
    myConnection = myProject.getMessageBus().connect();
    myConnection.subscribe(TutorialCardRefreshNotifier.TUTORIAL_CARD_TOPIC,
                           (TutorialCardRefreshNotifier)stepNumberToShow -> refreshView(stepNumberToShow));
  }

  private void refreshView(int stepNumberToShow) {
    // the currently visible card is the one that should be updated
    if (isVisible()) {
      redraw();
      // default refresh behavior is to go to the top of the view
      if (stepNumberToShow > 0 && isViewportExpectedLayout(myContentsScroller)) {
        // refresh will trigger queues on the EDT to repaint and revalidate the layout
        // need to set the position after the painting is done
        ApplicationManager.getApplication().invokeLater(() -> {
          myContentsScroller.getHorizontalScrollBar().setValue(0);
          int positionToSetTo = getVerticalScrollbarPosition(stepNumberToShow, myContentsScroller);
          myContentsScroller.getVerticalScrollBar().setValue(positionToSetTo);
        });
      }
    }
  }

  // gives the vertical location of the n-th (zero based indexing) tutorialStepNumber if available otherwise 0
  @VisibleForTesting
  int getVerticalScrollbarPosition(int tutorialStepNumber, JScrollPane jScrollPane) {
    JPanel tutorialCardView = (JPanel)jScrollPane.getViewport().getComponent(0);
    return Arrays.stream(tutorialCardView.getComponents())
      .filter(c -> c instanceof TutorialStep)
      .skip(tutorialStepNumber)
      .map(Component::getY)
      .findFirst().orElse(0);
  }

  @VisibleForTesting
  boolean isViewportExpectedLayout(JScrollPane scrollPane) {
    // the viewport is constructed correctly should have all the TutorialSteps inside the first component which is a JPanel
    return scrollPane != null
           && scrollPane.getViewport() != null
           && scrollPane.getViewport().getComponents().length > 0
           && scrollPane.getViewport().getComponent(0) instanceof JPanel;
  }

  private void trackScrolledToBottom(@NotNull AdjustmentEvent event) {
    if (myScrollHandler == null || event.getValueIsAdjusting()) {
      return;
    }
    BoundedRangeModel model = ((JScrollBar)event.getAdjustable()).getModel();
    if (model.getExtent() + event.getValue() >= model.getMaximum()) {
      myScrollHandler.scrolledToBottom(myProject);
    }
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
    title.setFont(title.getFont().deriveFont(Font.PLAIN, JBUIScale.scaleFontSize(24)));
    title.setBorder(JBUI.Borders.empty(10, 10, 0, 10));
    if (myTutorial.getIcon() != null) {
      title.setIcon(myTutorial.getIcon());
    }
    contents.add(title, c);
    c.gridy++;

    if (myTutorial.getDescription() != null && !myTutorial.getDescription().isEmpty()) {
      TutorialDescription description = new TutorialDescription();
      StringBuilder sb = new StringBuilder();
      String descriptionContent = myTutorial.getDescription();
      if (myTutorial.hasLocalHTMLPaths()) {
        descriptionContent = UIUtils.addLocalHTMLPaths(myTutorial.getResourceClass(), descriptionContent);
      }
      sb.append("<p class=\"description\">").append(descriptionContent);
      if (myTutorial.getRemoteLink() != null && myTutorial.getRemoteLinkLabel() != null) {
        sb.append("<br><br><a href=\"").append(myTutorial.getRemoteLink()).append("\" target=\"_blank\">")
          .append(myTutorial.getRemoteLinkLabel()).append("</a>");
      }
      sb.append("</p>");
      UIUtils.setHtml(description, sb.toString(), ".description { margin: 10px;}");

      contents.add(description, c);
      c.gridy++;
    }

    // Add extra padding for tutorial steps.
    c.insets = JBUI.insets(0, 5, 0, 5);

    boolean hideStepIndex = myBundle.hideStepIndex();
    if (myBundle.isStepByStep()) {
      List<? extends StepData> steps = myTutorial.getSteps();
      if (!steps.isEmpty()) {
        contents.add(new TutorialStep(steps.get(myStepIndex), myStepIndex, myListener,
                                      myProject, hideStepIndex, myTutorial.hasLocalHTMLPaths(), myTutorial.getResourceClass()), c);
        c.gridy++;
      }
    }
    else {
      // Add each of the tutorial steps in order.
      int numericLabel = 0;

      for (StepData step : myTutorial.getSteps()) {
        TutorialStep stepDisplay =
          new TutorialStep(step, numericLabel, myListener,
                           myProject, hideStepIndex, myTutorial.hasLocalHTMLPaths(), myTutorial.getResourceClass());
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
      c.insets = JBInsets.emptyInsets();
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
    myContentsScroller.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
  }

  private static class TutorialDescription extends JEditorPane {
    TutorialDescription() {
      super();
      setOpaque(false);
      setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UIUtils.getSeparatorColor()));
    }

    // Set the pane to be as small as possible: this will make long lines wrap properly instead of forcing the pane to extend as
    // long as the line. When a line can't be wrapped (e.g. a long word or an image), the horizontal scrollbar should appear.
    @Override
    public Dimension getPreferredSize() {
      return getMinimumSize();
    }
  }

  /**
   * A fixed header component to be displayed above tutorial cards. This control serves as:
   * 1. rudimentary breadcrumbs
   * 2. a title for the card
   * 3. navigation back to the root view
   * TODO: Consider stealing more from NavBarPanel.
   */
  private class HeaderNav extends JPanel {
    public final String ROOT_TITLE = "<html><b>" + myBundle.getName() + "</b> &nbsp;&rsaquo;</html>";

    HeaderNav(@NotNull String location) {
      super(new HorizontalLayout(5, SwingConstants.CENTER));
      setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

      add(new BackButton(ROOT_TITLE));

      JBLabel label = new JBLabel(location);
      label.setForeground(UIUtils.getSecondaryColor());
      add(label);
    }
  }

  private class FooterNav extends JPanel {
    FooterNav() {
      super(new FlowLayout(FlowLayout.LEADING));
      setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UIUtils.getSeparatorColor()));
      setOpaque(false);
      add(new BackButton("Back to " + myBundle.getName()));
    }
  }

  private class StepByStepFooter extends JPanel {
    @NotNull
    private final StepButton myPrevButton;
    @NotNull
    private final StepButton myNextButton;

    StepByStepFooter() {
      super(new BorderLayout());
      setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UIUtils.getSeparatorColor()));
      setOpaque(false);
      JPanel containerPanel = new JPanel(new BorderLayout());
      containerPanel.setBorder(JBUI.Borders.empty(5));

      myPrevButton = new StepButton("Previous", StepButton.Direction.PREV, this::handleStepButtonClick);
      containerPanel.add(myPrevButton, BorderLayout.LINE_START);
      myNextButton = new StepButton("Next", StepButton.Direction.NEXT, this::handleStepButtonClick);
      containerPanel.add(myNextButton, BorderLayout.LINE_END);
      add(containerPanel);

      updateVisibility();
    }

    private void handleStepButtonClick(@NotNull ActionEvent event) {
      Object source = event.getSource();
      StepButton stepButton = (StepButton)source;
      if (stepButton.myDirection == StepButton.Direction.NEXT) {
        if (myStepIndex < myTutorial.getSteps().size() - 1) {
          myStepIndex++;
        }
        else {
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
      ToolWindow assistantToolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(AssistantToolWindowService.TOOL_WINDOW_TITLE);
      if (assistantToolWindow != null) {
        assistantToolWindow.hide();
      }
      myStepIndex = 0; // reset index back to first page on close.
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

    private StepButton(@Nullable String label, @NotNull Direction direction, @NotNull ActionListener listener) {
      super(label, TutorialChooser.NAVIGATION_KEY, listener);
      myDirection = direction;
      if (Direction.NEXT == direction) {
        setIcon(AllIcons.Actions.Forward);
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
    private BackButton(@Nullable String label) {
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
