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

import com.google.common.collect.Lists;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.border.CustomLineBorder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;

import static com.android.SdkConstants.GRADLE_PLUGIN_LATEST_VERSION;
import static com.intellij.ide.BrowserUtil.browse;
import static com.intellij.util.ui.JBUI.Borders.empty;
import static com.intellij.util.ui.JBUI.Borders.emptyTop;
import static javax.swing.Action.MNEMONIC_KEY;
import static javax.swing.Action.NAME;
import static org.jetbrains.android.util.AndroidUiUtil.setUpAsHtmlLabel;

public class PluginVersionRecommendedUpdateDialog extends DialogWrapper {
  private static final String SHOW_DO_NOT_ASK_TO_UPGRADE_PLUGIN_PROPERTY_NAME = "show.do.not.ask.upgrade.gradle.plugin";

  @NotNull private final Project myProject;
  @NotNull private final PropertyBasedDoNotAskOption myDoNotAskOption;

  private JPanel myCenterPanel;
  private JEditorPane myMessagePane;
  private JButton[] myButtons;

  public PluginVersionRecommendedUpdateDialog(@NotNull Project project, @NotNull String currentPluginVersion) {
    super(project);
    myProject = project;
    setTitle("Android Gradle Plugin Update Recommended");
    myDoNotAskOption = new PropertyBasedDoNotAskOption(project, SHOW_DO_NOT_ASK_TO_UPGRADE_PLUGIN_PROPERTY_NAME) {
      @Override
      @NotNull
      public String getDoNotShowMessage() {
        return "Don't remind me again for this project";
      }
    };
    init();

    setUpAsHtmlLabel(myMessagePane);
    String msg = "<b>The project is using an old version of the Android Gradle plugin (" + currentPluginVersion + ").</b><br/<br/>" +
                 "To take advantage of all the latest features, such as " +
                 "<b><a href='http://tools.android.com/tech-docs/instant-run'>Instant Run</a></b>, we strongly recommend " +
                 "that you update the Android Gradle plugin to version " +
                 GRADLE_PLUGIN_LATEST_VERSION + ".<br/><br/>" +
                 "You can learn more about this version of the plugin from the " +
                 "<a href='http://developer.android.com/tools/revisions/gradle-plugin.html'>release notes</a>.<br/><br/>";
    myMessagePane.setText(msg);
    myMessagePane.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        browse(e.getURL());
      }
    });
  }

  @Override
  @NotNull
  protected JComponent createSouthPanel() {
    Action[] actions = createActions();
    List<JButton> buttons = Lists.newArrayList();

    JPanel panel = new JPanel(new BorderLayout());

    if (actions.length > 0) {
      JPanel buttonsPanel = createButtons(actions, buttons);
      panel.add(buttonsPanel, BorderLayout.CENTER);
      myButtons = buttons.toArray(new JButton[buttons.size()]);
    }

    if (getStyle() == DialogStyle.COMPACT) {
      Border line = new CustomLineBorder(OnePixelDivider.BACKGROUND, 1, 0, 0, 0);
      panel.setBorder(new CompoundBorder(line, empty(8, 12)));
    }
    else {
      panel.setBorder(emptyTop(8));
    }

    return panel;
  }

  @Override
  @NotNull
  protected Action[] createActions() {
    if (SystemInfo.isMac) {
      return new Action[]{new DoNotAskAction(), getCancelAction(), getOKAction()};
    }
    return new Action[]{getOKAction(), getCancelAction(), new DoNotAskAction()};
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

  @NotNull
  private JPanel createButtons(@NotNull Action[] actions, @NotNull List<JButton> buttons) {
    // Use FlowLayout to prevent all buttons to have same width. Right now buttons are too long.
    JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.TRAILING));
    for (Action action : actions) {
      JButton button = createJButtonForAction(action);
      Object value = action.getValue(MNEMONIC_KEY);
      if (value instanceof Integer) {
        int mnemonic = ((Integer)value).intValue();
        button.setMnemonic(mnemonic);
      }

      if (action.getValue(FOCUSED_ACTION) != null) {
        myPreferredFocusedComponent = button;
      }

      buttons.add(button);
      buttonsPanel.add(button);
    }
    return buttonsPanel;
  }

  @Override
  public void show() {
    if (PropertiesComponent.getInstance(myProject).getBoolean(SHOW_DO_NOT_ASK_TO_UPGRADE_PLUGIN_PROPERTY_NAME, true)) {
      super.show();
    }
    // By default the exit code is CANCEL_EXIT_CODE
  }

  @Override
  protected void dispose() {
    super.dispose();
    if (myButtons != null) {
      for (JButton button : myButtons) {
        button.setAction(null); // avoid memory leak via KeyboardManager
      }
    }
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myCenterPanel;
  }

  private class DoNotAskAction extends DialogWrapperAction {
    protected DoNotAskAction() {
      super(myDoNotAskOption.getDoNotShowMessage());
    }

    @Override
    protected void doAction(ActionEvent e) {
      myDoNotAskOption.setToBeShown(false, CANCEL_EXIT_CODE);
      doCancelAction();
    }
  }
}
