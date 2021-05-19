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
package com.android.tools.idea.gradle.project.upgrade;

import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static com.android.tools.adtui.HtmlLabel.setUpAsHtmlLabel;
import static com.android.tools.idea.gradle.project.upgrade.GradlePluginUpgrade.releaseNotesUrl;
import static com.android.tools.idea.gradle.project.upgrade.UpgradeDialogMetricUtilsKt.recordUpgradeDialogEvent;
import static com.google.wireless.android.sdk.stats.GradlePluginUpgradeDialogStats.UserAction.CANCEL;
import static com.google.wireless.android.sdk.stats.GradlePluginUpgradeDialogStats.UserAction.DO_NOT_ASK_AGAIN;
import static com.google.wireless.android.sdk.stats.GradlePluginUpgradeDialogStats.UserAction.OK;
import static com.google.wireless.android.sdk.stats.GradlePluginUpgradeDialogStats.UserAction.REMIND_ME_TOMORROW;
import static com.intellij.ide.BrowserUtil.browse;
import static com.intellij.util.ui.JBUI.Borders.empty;
import static com.intellij.util.ui.JBUI.Borders.emptyTop;
import static javax.swing.Action.MNEMONIC_KEY;
import static javax.swing.Action.NAME;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.PropertyBasedDoNotAskOption;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.OnePixelDivider;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.border.CustomLineBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.event.HyperlinkEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RecommendedPluginVersionUpgradeDialog extends DialogWrapper {
  @NotNull private final Project myProject;
  @NotNull private final GradleVersion myCurrentPluginVersion;
  @NotNull private final GradleVersion myRecommendedPluginVersion;
  @NotNull private final RecommendedUpgradeReminder myUpgradeReminder;
  @NotNull private final PropertyBasedDoNotAskOption myDoNotAskOption;

  private JPanel myCenterPanel;
  private JEditorPane myMessagePane;
  private JButton[] myButtons;

  public static class Factory {
    @NotNull
    public RecommendedPluginVersionUpgradeDialog create(@NotNull Project project,
                                                        @NotNull GradleVersion current,
                                                        @NotNull GradleVersion recommended) {
      return new RecommendedPluginVersionUpgradeDialog(project, current, recommended, new RecommendedUpgradeReminder(project));
    }
  }

  @VisibleForTesting
  RecommendedPluginVersionUpgradeDialog(@NotNull Project project,
                                        @NotNull GradleVersion current,
                                        @NotNull GradleVersion recommended,
                                        @NotNull RecommendedUpgradeReminder upgradeReminder) {
    super(project);
    myProject = project;
    myCurrentPluginVersion = current;
    myRecommendedPluginVersion = recommended;
    myUpgradeReminder = upgradeReminder;
    if (StudioFlags.AGP_UPGRADE_ASSISTANT.get()) {
      setTitle("Android Gradle Plugin Upgrade Assistant");
    }
    else {
      setTitle("Android Gradle Plugin Update Recommended");
    }
    myDoNotAskOption = new PropertyBasedDoNotAskOption(project, upgradeReminder.getDoNotAskForProjectPropertyString()) {
      @Override
      @NotNull
      public String getDoNotShowMessage() {
        return "Don't remind me again for this project";
      }

      @Override
      public void setToBeShown(boolean toBeShown, int exitCode) {
        // Stores the state of the checkbox into the property.
        String valueToSave = "";
        if (!toBeShown) {
          valueToSave = myCurrentPluginVersion.toString();
        }
        myUpgradeReminder.setDoNotAskForVersion(valueToSave);
      }
    };
    init();

    setUpAsHtmlLabel(myMessagePane);
    String msg = "";
    String url = releaseNotesUrl(recommended);

    if (StudioFlags.AGP_UPGRADE_ASSISTANT.get()) {
      msg += "<p>To take advantage of the latest features, improvements, and security fixes, we strongly recommend " +
             "that you upgrade the Android Gradle Plugin in this project (" + myProject.getName() +
             ") from the current version " + current + " to version " +
             recommended + ". " +
             "<a href='" + url + "'>Release notes</a></p>";
    }
    else {
      msg += "To take advantage of the latest features, improvements, and security fixes, we strongly recommend " +
             "that you update the Android Gradle plugin in this project (" + myProject.getName() +
             ") from the current version " + current
             + " to version " + recommended + " and Gradle to version " + GRADLE_LATEST_VERSION + ". " +
             "<a href='" + url + "'>Release notes</a>";

      if (current.compareTo("3.2.0") < 0) {
        msg += "<br/><br/>" +
               "Android plugin 3.2.0 and higher now support building the <i>Android App Bundle</i>—" +
               "a new upload format that defers APK generation and signing to compatible app stores, " +
               "such as Google Play. With app bundles, you no longer have to build, sign, and manage multiple APKs, " +
               "and users get smaller, more optimized downloads. " +
               "<a href='http://d.android.com/r/studio-ui/dynamic-delivery/overview'>Learn more</a>";
      }
    }
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
    List<JButton> buttons = new ArrayList<>();

    JPanel panel = new JPanel(new BorderLayout());

    if (actions.length > 0) {
      JPanel buttonsPanel = createButtons(actions, buttons);
      panel.add(buttonsPanel, BorderLayout.CENTER);
      myButtons = buttons.toArray(new JButton[0]);
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
      return new Action[]{new DoNotAskAction(), new RemindMeTomorrowAction(), getOKAction()};
    }
    return new Action[]{getOKAction(), new RemindMeTomorrowAction(), new DoNotAskAction()};
  }

  @Override
  @NotNull
  protected Action getOKAction() {
    Action action = super.getOKAction();
    if (StudioFlags.AGP_UPGRADE_ASSISTANT.get()) {
      action.putValue(NAME, "Begin Upgrade");
    }
    else {
      action.putValue(NAME, "Update");
    }
    return action;
  }

  @Override
  public void doCancelAction() {
    // User closed dialog without making a selection, don't do anything.
    // Show dialog again when the project is opened next time.
    recordUpgradeDialogEvent(myProject, myCurrentPluginVersion, myRecommendedPluginVersion, CANCEL);
    close(CANCEL_EXIT_CODE);
  }

  @Override
  protected void doOKAction() {
    recordUpgradeDialogEvent(myProject, myCurrentPluginVersion, myRecommendedPluginVersion, OK);
    super.doOKAction();
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
      recordUpgradeDialogEvent(myProject, myCurrentPluginVersion, myRecommendedPluginVersion, DO_NOT_ASK_AGAIN);
      close(CANCEL_EXIT_CODE);
    }
  }

  /**
   * The action when user select "Remind me tomorrow" button. User will be reminded about the upgrade one day later.
   */
  @VisibleForTesting
  class RemindMeTomorrowAction extends DialogWrapperAction {
    RemindMeTomorrowAction() {
      super("Remind me tomorrow");
    }

    @Override
    protected void doAction(ActionEvent e) {
      // This is the "Remind me tomorrow" button.
      myUpgradeReminder.updateLastTimestamp();
      recordUpgradeDialogEvent(myProject, myCurrentPluginVersion, myRecommendedPluginVersion, REMIND_ME_TOMORROW);

      close(CANCEL_EXIT_CODE);
    }
  }
}
