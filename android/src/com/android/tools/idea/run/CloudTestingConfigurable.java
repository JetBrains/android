/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.io.IOException;
import java.net.URISyntaxException;

public class CloudTestingConfigurable implements SearchableConfigurable, Configurable.NoScroll {

  private static final String ENABLE_CLOUD_TESTING_PROPERTY = "com.google.gct.testing.enable";

  private JPanel panel;
  private JCheckBox enableCloudTesting = new JCheckBox();


  public CloudTestingConfigurable() {
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Cloud Test Lab";
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return null;
  }

  @Override
  public JComponent createComponent() {
    panel = new JPanel(new BorderLayout(5, 10));
    JPanel content = new JPanel(new BorderLayout());
    panel.add(content, BorderLayout.NORTH);
    JEditorPane warningPane =
      new JEditorPane(UIUtil.HTML_MIME,
                      "<html>Google Cloud Test Lab provides a wide array of virtual and physical devices, to use for running tests and debugging."
                      + " Use it to run your tests against a matrix of device combinations with a single click.<br><br>"
                      + "<a href='https://developers.google.com/cloud-test-lab'>More about Google Cloud Test Lab</a><br><br>"
                      + "<a href='https://cloud.google.com/pricing'>Pricing information</a><br><br></html>");
    if (!CloudConfigurationProvider.canEnable()) {
      warningPane.setText("<html>This feature is coming soon. Stay tuned!<br><br>"
                          + "For more information, please visit <a href='https://developers.google.com/cloud-test-lab'>Google Cloud Test Lab</a>.<br><br></html>");
    }
    warningPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
    warningPane.setFont(enableCloudTesting.getFont());
    warningPane.setEditable(false);
    warningPane.setBackground(panel.getBackground());
    HyperlinkListener hyperlinkListener = getHyperlinkListener();
    warningPane.addHyperlinkListener(hyperlinkListener);
    content.add(warningPane, BorderLayout.NORTH);
    if (CloudConfigurationProvider.canEnable()) {
      enableCloudTesting.setText("Enable testing and debugging in the cloud");
      content.add(enableCloudTesting, BorderLayout.WEST);

      JEditorPane legalDisclaimer =
        new JEditorPane(UIUtil.HTML_MIME,
                        "<html><br><br><br>The Google Cloud Platform Terms of Service notwithstanding, your use of "
                        + "Google Cloud Test Lab is governed by the "
                        + "<a href='https://drive.google.com/open?id=0B9D68w_vPl-dX01mQzF2SEM4VE0'>Trusted Testers Agreement</a> and "
                        + "by using Google Cloud Test Lab, you agree to its terms.");
      legalDisclaimer.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
      legalDisclaimer.setFont(enableCloudTesting.getFont().deriveFont(Font.ITALIC));
      legalDisclaimer.setEditable(false);
      legalDisclaimer.setBackground(panel.getBackground());
      legalDisclaimer.addHyperlinkListener(hyperlinkListener);
      panel.add(legalDisclaimer, BorderLayout.SOUTH);
    }
    return panel;
  }

  private HyperlinkListener getHyperlinkListener() {
    return new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(final HyperlinkEvent linkEvent) {
        if (linkEvent.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            @Override
            public void run() {
              try {
                Desktop.getDesktop().browse(linkEvent.getURL().toURI());
              }
              catch (IOException e) {
                // ignore
              }
              catch (URISyntaxException e) {
                // ignore
              }
            }
          });
        }
      }
    };
  }

  @Override
  public boolean isModified() {
    return getPersistedEnableProperty() != enableCloudTesting.isSelected();
  }

  @Override
  public void apply() throws ConfigurationException {
    PropertiesComponent.getInstance().setValue(ENABLE_CLOUD_TESTING_PROPERTY, String.valueOf(enableCloudTesting.isSelected()));
  }

  @Override
  public void reset() {
    enableCloudTesting.setSelected(getPersistedEnableProperty());
  }

  public static boolean getPersistedEnableProperty() {
    return PropertiesComponent.getInstance().getBoolean(ENABLE_CLOUD_TESTING_PROPERTY, false);
  }

  @Override
  public void disposeUIResources() {
    panel = null;
    enableCloudTesting = null;
  }

  @NotNull
  @Override
  public String getId() {
    return "cloud.testing";
  }

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

}
