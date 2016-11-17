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

import com.android.tools.idea.gradle.plugin.AndroidPluginGeneration;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.HyperlinkAdapter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;

import static com.android.tools.idea.gradle.plugin.AndroidPluginGeneration.COMPONENT;
import static com.intellij.ide.BrowserUtil.browse;
import static javax.swing.Action.NAME;
import static org.jetbrains.android.util.AndroidUiUtil.setUpAsHtmlLabel;

public class PluginVersionForcedUpdateDialog extends DialogWrapper {
  private JPanel myCenterPanel;
  private JEditorPane myMessagePane;

  @NotNull private final String myMessage;

  public PluginVersionForcedUpdateDialog(@NotNull Project project, @NotNull AndroidPluginGeneration pluginGeneration) {
    super(project);

    boolean experimental = pluginGeneration == COMPONENT;
    String pluginType = experimental ? "Experimental " : "";
    setTitle("Android Gradle " + pluginType + "Plugin Update Required");
    init();

    setUpAsHtmlLabel(myMessagePane);
    String pluginVersion = pluginGeneration.getLatestKnownVersion();
    myMessage = "<b>The project is using an incompatible version of the Android Gradle " + pluginType + "plugin.</b><br/<br/>" +
                 "To continue opening the project, the IDE will update the plugin to version " + pluginVersion + ".<br/><br/>" +
                 "You can learn more about this version of the plugin from the " +
                 "<a href='http://tools.android.com/tech-docs/new-build-system" + (experimental ? "/gradle-experimental" : "") +
                 "'>release notes</a>.<br/><br/>";
    myMessagePane.setText(myMessage);
    myMessagePane.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        browse(e.getURL());
      }
    });
  }

  @VisibleForTesting
  @NotNull
  String getDisplayedMessage() {
    return myMessage;
  }

  @Override
  @NotNull
  protected JComponent createCenterPanel() {
    return myCenterPanel;
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
    action.putValue(NAME, "Cancel and update manually");
    return action;
  }
}
