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

import com.android.tools.idea.assistant.datamodel.StepData;
import com.android.tools.idea.assistant.datamodel.TutorialData;
import com.android.tools.idea.structure.services.DeveloperService;
import com.intellij.icons.AllIcons;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.HorizontalLayout;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;

/**
 * Generic view for tutorial content. Represents a single view in a collection
 * of tutorials. Content is rendered via XML configured content and couples to
 * {@code TutorialChooser} where each card appears as a line item below their
 * related service.
 *
 * TODO: Attempt to migrate render logic to a form.
 */
public class TutorialCard extends CardViewPanel {

  JBScrollPane myContentsScroller = new JBScrollPane();

  /**
   * Partial label used in the back button.
   */
  String myMutorialsTitle;

  TutorialCard(@NotNull ActionListener listener,
               @NotNull TutorialData tutorial,
               @NotNull String featureTitle,
               @NotNull String tutorialsTitle,
               @NotNull DeveloperService service) {
    super(listener);
    myMutorialsTitle = tutorialsTitle;

    // TODO: Add a short label to the xml and use that here instead.
    add(new HeaderNav(featureTitle, myListener), BorderLayout.NORTH);

    TutorialDescription description = new TutorialDescription();
    String text = "<p class=\"title\">" + tutorial.getLabel() + "</p><p class=\"description\">" +
                  tutorial.getDescription() +
                  "<br><br><a href=\"" +
                  tutorial.getRemoteLink() +
                  "\" target=\"_blank\">" +
                  tutorial.getRemoteLinkLabel() +
                  "</a></p>";
    UIUtils.setHtml(description, text,
                    ".title {margin: 10px 10px 0 10px; font-size: 16px; font-weight: bold;}\n.description { margin: 10px;}");

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
    c.insets = new Insets(0, 0, 5, 0);

    contents.add(description, c);
    c.gridy++;

    // Add extra padding for tutorial steps.
    c.insets = new Insets(0, 5, 5, 5);

    // Add each of the tutorial steps in order.
    int numericLabel = 1;
    for (StepData step : tutorial.getSteps()) {
      TutorialStep stepDisplay = new TutorialStep(step, numericLabel, listener, service);
      contents.add(stepDisplay, c);
      c.gridy++;
      numericLabel++;
    }

    GridBagConstraints glueConstraints = UIUtils.getVerticalGlueConstraints(c.gridy);
    contents.add(Box.createVerticalGlue(), glueConstraints);
    c.gridy++;

    // remove insets for footer.
    c.insets = new Insets(0, 0, 0, 0);
    contents.add(new FooterNav(), c);

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
    add(myContentsScroller, BorderLayout.CENTER);
  }

  /**
   * Using the visibility being enabled on card change as a cheap way to do re-init of of the component.
   */
  @Override
  public void setVisible(boolean aFlag) {
    super.setVisible(aFlag);

    // HACK ALERT: For an unknown reason, a subset of html components fail to report the correct sizing and run into an order of events
    // issue with layouts (overlapping and clipping). Thus far the only thing that corrects the problem is resize. Force a resize to
    // address this in the short term.
    processEvent(new ComponentEvent(this, ComponentEvent.COMPONENT_RESIZED));

    JScrollBar verticalScrollBar = myContentsScroller.getVerticalScrollBar();
    JScrollBar horizontalScrollBar = myContentsScroller.getHorizontalScrollBar();
    verticalScrollBar.setValue(verticalScrollBar.getMinimum());
    horizontalScrollBar.setValue(horizontalScrollBar.getMinimum());
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

    public final String ROOT_TITLE = "<html><b>" + myMutorialsTitle + "</b> &nbsp;&rsaquo;</html>";

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
    private final String BACK_LABEL = "Back to " + myMutorialsTitle;

    FooterNav() {
      super(new FlowLayout(FlowLayout.LEADING));
      setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UIUtils.getSeparatorColor()));
      setOpaque(false);
      add(new BackButton(BACK_LABEL));
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
