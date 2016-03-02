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
package com.android.tools.idea.gradle.project;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.HyperlinkAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLDocument;
import java.awt.*;

import static com.android.SdkConstants.GRADLE_PLUGIN_LATEST_VERSION;
import static com.intellij.ide.BrowserUtil.browse;
import static com.intellij.util.ui.UIUtil.getLabelFont;
import static javax.swing.Action.NAME;

public class PluginVersionUpgradeDialog extends DialogWrapper {
  private static final String SHOW_DO_NOT_ASK_TO_UPGRADE_PLUGIN_PROPERTY_NAME = "show.do.not.ask.upgrade.gradle.plugin";

  @NotNull private final Project myProject;

  private JPanel myCenterPanel;
  private JEditorPane myMessagePane;

  public PluginVersionUpgradeDialog(@NotNull Project project) {
    super(project);
    myProject = project;
    setTitle("Android Gradle Plugin Upgrade Recommended");
    setDoNotAskOption(new PropertyBasedDoNotAskOption(project, SHOW_DO_NOT_ASK_TO_UPGRADE_PLUGIN_PROPERTY_NAME) {
      @Override
      @NotNull
      public String getDoNotShowMessage() {
        return "Don't remind me again for this project";
      }
    });
    init();

    String msg = "<b>The project is using an old version of the Android Gradle plugin.</b><br/<br/>" +
                 "To take advantage of all the latest features, such as <a href=''>Instant Run</a>, we strongly recommend " +
                 "that you update the Android Gradle plugin to version " +
                 GRADLE_PLUGIN_LATEST_VERSION + ".<br/><br/>" +
                 "You can learn more about this version of the plugin from the " +
                 "<a href='http://developer.android.com/tools/revisions/gradle-plugin.html'>release notes</a>.<br/><br/>";
    myMessagePane.setContentType("text/html");
    myMessagePane.setEditable(false);
    myMessagePane.setOpaque(false);
    myMessagePane.setText(msg);
    Font font1 = getLabelFont();
    String bodyRule = "body { font-family: " + font1.getFamily() + "; " + "font-size: " + font1.getSize() + "pt; }";
    ((HTMLDocument)myMessagePane.getDocument()).getStyleSheet().addRule(bodyRule);

    myMessagePane.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        browse(e.getURL());
      }
    });
  }

  @Override
  @NotNull
  protected Action getOKAction() {
    Action action = super.getOKAction();
    action.putValue(NAME, "Update");
    return action;
  }

  @Override
  @NotNull
  protected Action getCancelAction() {
    Action action = super.getCancelAction();
    action.putValue(NAME, "Remind me later");
    return action;
  }

  @Override
  public void show() {
    if (PropertiesComponent.getInstance(myProject).getBoolean(SHOW_DO_NOT_ASK_TO_UPGRADE_PLUGIN_PROPERTY_NAME, true)) {
      super.show();
    }
    // By default the exit code is CANCEL_EXIT_CODE
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myCenterPanel;
  }
}
