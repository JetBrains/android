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
import com.android.tools.idea.assistant.datamodel.FeatureData;
import com.android.tools.idea.assistant.datamodel.TutorialBundleData;
import com.android.tools.idea.assistant.datamodel.TutorialData;
import com.android.tools.idea.structure.services.DeveloperService;
import com.android.tools.idea.structure.services.DeveloperServiceMap;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;

/**
 * Entry point for the complete set of services and tutorials associated with
 * Firebase. Initializes presentation data from xml and arranges into cards for
 * navigation purposes.
 *
 * TODO: Attempt to move any presentation logic into a form.
 */
public class FeaturesPanel extends JPanel implements ItemListener, ActionListener {
  private final List<String> myCardKeys = new ArrayList<String>();
  private JPanel myCards;
  private CardLayout myCardLayout;

  @SuppressWarnings("FieldCanBeLocal")
  @NotNull
  private TutorialBundleData myTutorialBundle;

  public FeaturesPanel(@NotNull TutorialBundleData bundle, DeveloperServiceMap serviceMap) {
    myTutorialBundle = bundle;

    setLayout(new BorderLayout());
    setBackground(UIUtils.getBackgroundColor());

    myCardLayout = new CardLayout();
    myCards = new JPanel(myCardLayout);
    myCards.setOpaque(false);
    myCardLayout.setVgap(0);

    // NOTE: the card labels cannot be from an enum since the views will be
    // built up from xml.
    addCard(new TutorialChooser(this, myTutorialBundle), "chooser");

    // Add all tutorial cards.
    for (FeatureData feature : myTutorialBundle.getFeatures()) {
      DeveloperService service = serviceMap.get(feature.getServiceId());
      for (TutorialData tutorial : feature.getTutorials()) {
        addCard(new TutorialCard(this, tutorial, feature.getName(), service), tutorial.getKey());
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
      String key = t.getKey();
      if (!myCardKeys.contains(key)) {
        throw new RuntimeException("No views exist with key: " + key);
      }
      myCardLayout.show(myCards, key);
      getLog().debug("Received request to navigate to view with key: " + key);
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

      DeveloperService service = a.getDeveloperService();
      if (service == null) {
        throw new RuntimeException("Unable to find a service to to complete the requested action.");
      }

      handler.handleAction(a.getActionArgument(), service);
      a.updateState();
    }
    else {
      throw new RuntimeException("Unhandled action, \"" + e.getActionCommand() + "\".");
    }
  }

}
