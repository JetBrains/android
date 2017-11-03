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
package com.android.tools.idea.assistant;

import com.android.tools.idea.assistant.datamodel.AnalyticsProvider;
import com.android.tools.idea.assistant.datamodel.FeatureData;
import com.android.tools.idea.assistant.datamodel.TutorialBundleData;
import com.android.tools.idea.assistant.view.FeaturesPanel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.xml.bind.JAXBException;
import java.awt.*;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * Panel for "assistant" flows such as tutorials, domain specific tools, etc.
 */
public final class AssistSidePanel extends JPanel {

  public AssistSidePanel(@NotNull String actionId, @NotNull Project project) {
    super(new BorderLayout());
    setBorder(BorderFactory.createEmptyBorder());
    setOpaque(false);

    Component assistContents = null;

    for (AssistantBundleCreator creator : AssistantBundleCreator.EP_NAME.getExtensions()) {
      if (creator.getBundleId().equals(actionId)) {
        TutorialBundleData bundle;

        // Instantiate the bundle from a configuration file using the default bundle mapping.
        // If null, creator must provide the bundle instance themselves.
        URL config = null;
        try {
          config = creator.getConfig();
        }
        catch (FileNotFoundException e) {
          getLog().warn(e);
        }
        // Config provided, use that with the default bundle.
        if (config != null) {
          bundle = getBundle(config);
        }
        else {
          bundle = creator.getBundle(project);
        }

        if (bundle == null) {
          getLog().error("Unable to get Assistant configuration for action: " + actionId);
          return;
        }

        // Provide the creator's class for classloading purposes.
        bundle.setResourceClass(creator.getClass());
        for (FeatureData feature : bundle.getFeatures()) {
          feature.setResourceClass(creator.getClass());
        }

        AnalyticsProvider analyticsProvider = creator.getAnalyticsProvider();
        analyticsProvider.trackPanelOpened(project);

        assistContents = new FeaturesPanel(bundle, project, analyticsProvider);
        break;
      }
    }

    if (assistContents == null) {
      throw new RuntimeException("Unable to find configuration for the selected action: " + actionId);
    }
    add(assistContents);
  }

  private static Logger getLog() {
    return Logger.getInstance(AssistSidePanel.class);
  }

  private static TutorialBundleData getBundle(@NotNull URL config) {
    InputStream inputStream = null;
    TutorialBundleData bundle;
    try {
      inputStream = config.openStream();
      bundle = DefaultTutorialBundle.parse(inputStream);
    }
    catch (IOException e) {
      throw new RuntimeException("Unable to parse " + config.toString() +
                                 " to read services configuration.", e);
    }
    catch (JAXBException e) {
      throw new RuntimeException(e);
    }
    finally {
      try {
        if (inputStream != null) inputStream.close();
      }
      catch (Exception e) {
        getLog().warn(e);
      }
    }
    return bundle;
  }
}
