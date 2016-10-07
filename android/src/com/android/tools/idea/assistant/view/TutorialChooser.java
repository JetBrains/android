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
import com.android.tools.idea.assistant.datamodel.TutorialBundleData;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

/**
 * Introductory view for Firebase. Displays a welcome message as well as
 * summary view for each service (and their child tutorials).
 *
 * TODO: Migrate display properties to a form.
 */
public class TutorialChooser extends CardViewPanel {

  /**
   * Card navigation key, should be used with {@code NavigationButton} to navigate to this view.
   */
  public static final String NAVIGATION_KEY = "chooser";

  public TutorialChooser(ActionListener listener, @NotNull TutorialBundleData bundle) {
    super(listener);

    JPanel header = new TutorialChooserHeader(bundle);

    // TODO: Figure out where extra padding is coming from.
    JTextPane welcome = new JTextPane();
    welcome.setOpaque(false);
    String text = "<p class=\"welcome\">" + bundle.getWelcome() + "</p>";
    UIUtils.setHtml(welcome, text, ".welcome { margin: 10px; color: " + UIUtils.getCssColor(UIUtils.getSecondaryColor()) + "}");
    header.add(welcome);
    add(header, BorderLayout.NORTH);

    // NOTE: BoxLayout doesn't work because the sub elements are greedy and
    // there's no way to add a greedy filler with BoxLayout.
    NaturalWidthScrollClient services = new NaturalWidthScrollClient();
    services.setLayout(new GridBagLayout());
    services.setOpaque(false);

    services.setAlignmentX(LEFT_ALIGNMENT);
    GridBagConstraints c = new GridBagConstraints();
    c.gridx = 0;
    c.gridy = 0;
    c.weightx = 1;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.anchor = GridBagConstraints.NORTHWEST;

    for (FeatureData feature : bundle.getFeatures()) {
      FeatureEntryPoint entryPoint = createFeatureEntryPoint(feature);
      services.add(entryPoint, c);
      // Increment constraints for next service.
      c.gridy += 1;
    }

    // Fill up all remaining space so that the above contents aren't evenly spaced across the view.
    // TODO: Common glue treatment for GridBagLayout, move to a helper.
    GridBagConstraints glueConstraints = new GridBagConstraints();
    glueConstraints.gridx = 0;
    glueConstraints.gridy = c.gridy + 1;
    glueConstraints.gridwidth = 1;
    glueConstraints.gridheight = 1;
    glueConstraints.weightx = 0;
    glueConstraints.weighty = 1;
    glueConstraints.anchor = GridBagConstraints.NORTH;
    glueConstraints.fill = GridBagConstraints.BOTH;
    glueConstraints.insets = new Insets(0, 0, 0, 0);
    glueConstraints.ipadx = 0;
    glueConstraints.ipady = 0;
    services.add(Box.createVerticalGlue(), glueConstraints);

    JBScrollPane serviceScroller = new JBScrollPane(services);
    serviceScroller.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UIUtils.getSeparatorColor()));
    serviceScroller.setViewportBorder(BorderFactory.createEmptyBorder());
    serviceScroller.setOpaque(false);
    serviceScroller.getViewport().setOpaque(false);
    add(serviceScroller, BorderLayout.CENTER);
  }

  /**
   * Creates an expandable summary for a given service. Acts as the entry point
   * to the associated tutorials.
   *
   * @return The component to render.
   */
  private FeatureEntryPoint createFeatureEntryPoint(FeatureData feature) {
    return new FeatureEntryPoint(feature, myListener);
  }

  /**
   * A basic scroll-savvy client that fits to width and scrolls vertically.
   *
   * TODO: Pull this out into a light-weight reusable class for this type of
   * scroll behavior.
   */
  private static class NaturalWidthScrollClient extends JPanel implements Scrollable {

    @Override
    public Dimension getPreferredScrollableViewportSize() {
      return getPreferredSize();
    }

    // TODO: Make this be proportional to container size.
    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
      return 20;
    }

    // TODO: Make this be proportional to container size.
    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
      return 100;
    }

    // Never scroll horizontally, instead force resize.
    @Override
    public boolean getScrollableTracksViewportWidth() {
      return true;
    }

    // Use default vertical scroll behavior.
    @Override
    public boolean getScrollableTracksViewportHeight() {
      return false;
    }
  }

  private static class TutorialChooserHeader extends JPanel {

    TutorialChooserHeader(TutorialBundleData bundle) {
      super(new VerticalFlowLayout());
      setOpaque(false);

      // When a logo is present, this contains the textual representation of the tutorial set and supersedes the name and icon.
      if (bundle.getLogo() != null) {
        JBLabel logo = new JBLabel();
        logo.setAlignmentX(0);
        logo.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        logo.setIcon(bundle.getLogo());
        add(logo);
        return;
      }

      JBLabel title = new JBLabel(bundle.getName());
      title.setAlignmentX(0);
      title.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
      title.setIcon(bundle.getIcon());
      add(title);
    }

  }

}
