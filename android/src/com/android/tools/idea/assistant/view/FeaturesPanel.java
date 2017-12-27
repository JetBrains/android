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


import com.android.tools.idea.assistant.AssistActionHandler;
import com.android.tools.idea.assistant.datamodel.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.assistant.view.TutorialChooser.NAVIGATION_KEY;

/**
 * Entry point for the complete set of services and tutorials associated with
 * Firebase. Initializes presentation data from xml and arranges into cards for
 * navigation purposes.
 */
public class FeaturesPanel extends JPanel implements ItemListener, ActionListener {
  private final List<String> myCardKeys = new ArrayList<>();
  private JPanel myCards;
  private CardLayout myCardLayout;
  private AnalyticsProvider myAnalyticsProvider;
  private Project myProject;

  /**
   * If non-null, the key of the currently open tutorial.
   * Used for analytics tracking purposes.
   */
  private TutorialMetadata myOpenTutorial;

  public FeaturesPanel(@NotNull TutorialBundleData bundle, @NotNull Project project, @NotNull AnalyticsProvider analyticsProvider) {
    super(new BorderLayout());

    myAnalyticsProvider = analyticsProvider;
    myProject = project;

    setBackground(UIUtils.getBackgroundColor());

    myCardLayout = new CardLayout();
    myCards = new JPanel(myCardLayout);
    myCards.setOpaque(false);
    myCardLayout.setVgap(0);

    // NOTE: the card labels cannot be from an enum since the views will be
    // built up from xml.
    List<? extends FeatureData> featureList = bundle.getFeatures();
    // Note: Hides Tutorial Chooser panel if there is only one feature and one tutorial.
    boolean hideChooserAndNavigationalBar = false;
    if (featureList.size() == 1 && featureList.get(0).getTutorials().size() == 1) {
      hideChooserAndNavigationalBar = true;
      getLog().debug("Tutorial chooser and head/bottom navigation bars are hidden because the assistant panel contains only one tutorial.");
    }
    else {
      addCard(new TutorialChooser(this, bundle, myAnalyticsProvider, myProject), NAVIGATION_KEY);
    }

    // Add all tutorial cards.
    for (FeatureData feature : bundle.getFeatures()) {
      for (TutorialData tutorial : feature.getTutorials()) {
        addCard(
          new TutorialCard(this, tutorial, feature, bundle.getName(), myProject, hideChooserAndNavigationalBar, bundle.isStepByStep()),
          tutorial.getKey());
      }
    }
    add(myCards);
  }

  private static Logger getLog() {
    return Logger.getInstance(FeaturesPanel.class);
  }

  private void addCard(Component c, String key) {
    myCards.add(c, key);
    myCardKeys.add(key);
  }

  // TODO: Determine if this should just throw instead, we're not navigating via
  // controls that surface this event.
  @Override
  public void itemStateChanged(ItemEvent e) {
    CardLayout cl = (CardLayout)(myCards.getLayout());
    cl.show(myCards, (String)e.getItem());
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    Object source = e.getSource();

    // TODO: Refactor this code to avoid bloat. This should generally be a dispatcher to more specific classes that manage a given action
    // type. Current thinking is to use extensions so that it's completely generic.
    if (source instanceof NavigationButton) {
      NavigationButton t = (NavigationButton)e.getSource();
      // Track that user has navigated away from a tutorial. Note that the
      // "chooser" card is special cased in {@code #showCard} such that
      // no myOpenTutorial + myTimeTutorialOpened are not set.
      if (myOpenTutorial != null) {
        myAnalyticsProvider.trackTutorialClosed(myOpenTutorial.getKey(), myOpenTutorial.getReadDuration(), myProject);
        myOpenTutorial = null;
      }
      showCard(t.getKey());
    }
    else if (source instanceof StatefulButton.ActionButton) {
      StatefulButton.ActionButton a = (StatefulButton.ActionButton)e.getSource();
      String actionId = a.getKey();

      AssistActionHandler handler = null;
      for (AssistActionHandler actionHandler : AssistActionHandler.EP_NAME.getExtensions()) {
        if (actionHandler.getId().equals(actionId)) {
          handler = actionHandler;
          break;
        }
      }
      if (handler == null) {
        throw new IllegalArgumentException("Unhandled action, no handler found for key \"" + actionId + "\".");
      }

      ActionData actionData = a.getActionData();
      handler.handleAction(actionData, a.getProject());
      a.updateState();
    }
    else {
      throw new RuntimeException("Unhandled action, \"" + e.getActionCommand() + "\".");
    }
  }

  /**
   * Shows the card matching the given key.
   *
   * @param key The key of the card to show.
   */
  private void showCard(@NotNull String key) {
    if (!myCardKeys.contains(key)) {
      throw new IllegalArgumentException("No views exist with key: " + key);
    }
    getLog().debug("Received request to navigate to view with key: " + key);
    if (!key.equals(NAVIGATION_KEY)) {
      myAnalyticsProvider.trackTutorialOpen(key, myProject);
      myOpenTutorial = new TutorialMetadata(key);
    }
    myCardLayout.show(myCards, key);
  }

  static class TutorialMetadata {
    private final String myKey;
    private final long myTimeOpenedMs;

    TutorialMetadata(@NotNull String key) {
      myKey = key;
      myTimeOpenedMs = System.currentTimeMillis();
    }

    long getReadDuration() {
      return System.currentTimeMillis() - myTimeOpenedMs;
    }

    @NotNull
    String getKey() {
      return myKey;
    }
  }
}
