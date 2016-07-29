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

import com.android.ide.common.repository.GradleVersion;
import com.google.common.collect.Lists;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.border.CustomLineBorder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;

import static com.intellij.util.ui.JBUI.Borders.empty;
import static com.intellij.util.ui.JBUI.Borders.emptyTop;
import static javax.swing.Action.MNEMONIC_KEY;
import static javax.swing.Action.NAME;
import static org.jetbrains.android.util.AndroidUiUtil.setUpAsHtmlLabel;

public class GradleVersionRecommendedUpdateDialog extends DialogWrapper {
  private static final String SHOW_DO_NOT_ASK_TO_UPGRADE_GRADLE_PROPERTY_NAME = "show.do.not.ask.upgrade.gradle";

  @NotNull private final Project myProject;
  @NotNull private final PropertyBasedDoNotAskOption myDoNotAskOption;

  private JPanel myMainPanel;
  private JEditorPane myMessagePane;
  private JButton[] myButtons;

  public GradleVersionRecommendedUpdateDialog(@NotNull Project project,
                                              @NotNull GradleVersion recommendedVersion,
                                              @Nullable GradleVersion newPluginVersion) {
    super(project);
    myProject = project;
    setTitle("Gradle Update Recommended");
    myDoNotAskOption = new PropertyBasedDoNotAskOption(project, SHOW_DO_NOT_ASK_TO_UPGRADE_GRADLE_PROPERTY_NAME) {
      @Override
      @NotNull
      public String getDoNotShowMessage() {
        return "Don't remind me again for this project";
      }
    };
    init();

    setUpAsHtmlLabel(myMessagePane);
    String msg = "<b>It is strongly recommended that you update Gradle to version " + recommendedVersion.toString() + " or later.</b>";

    if (newPluginVersion != null) {
      msg +="<br/>As part of the update, the Android plugin will be updated to version " + newPluginVersion + ".";
    }

    msg += "<br/><br/>For more information, please refer to the " +
           "<a href='https://developer.android.com/studio/releases/index.html#Revisions'>release notes</a>.";
    myMessagePane.setText(msg);
  }

  @Override
  @Nullable
  protected JComponent createCenterPanel() {
    return myMainPanel;
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
    if (PropertiesComponent.getInstance(myProject).getBoolean(SHOW_DO_NOT_ASK_TO_UPGRADE_GRADLE_PROPERTY_NAME, true)) {
      super.show();
    }
    else {
      doCancelAction();
    }
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
