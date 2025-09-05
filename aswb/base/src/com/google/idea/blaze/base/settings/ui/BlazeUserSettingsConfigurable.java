/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.settings.ui;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.settings.BlazeUserSettings.FocusBehavior;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.ui.FileSelectorWithStoredHistory;
import com.google.idea.common.settings.AutoConfigurable;
import com.google.idea.common.settings.ConfigurableSetting;
import com.google.idea.common.settings.ConfigurableSetting.ComponentFactory;
import com.google.idea.common.settings.SearchableText;
import com.google.idea.common.settings.SettingComponent.LabeledComponent;
import com.google.idea.common.settings.SettingComponent.SimpleComponent;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.SwingHelper;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Base blaze settings. */
public class BlazeUserSettingsConfigurable extends AutoConfigurable {

  static class UiContributor implements BlazeUserSettingsCompositeConfigurable.UiContributor {
    @Override
    public UnnamedConfigurable getConfigurable() {
      return new BlazeUserSettingsConfigurable();
    }

    @Override
    public ImmutableCollection<SearchableText> getSearchableText() {
      return SearchableText.collect(
          SETTINGS, TOOL_WINDOW_POPUP_BEHAVIOR_TEXT, SHOW_CONSOLE_TEXT, SHOW_PROBLEMS_VIEW_TEXT);
    }
  }

  private static final SearchableText TOOL_WINDOW_POPUP_BEHAVIOR_TEXT =
      SearchableText.withLabel("Tool window popup behavior")
          .addTags("show", "automatic")
          .addTags(Blaze.defaultBuildSystemName(), "console")
          .addTags("problems", "view")
          .build();

  private static final SearchableText SHOW_CONSOLE_TEXT =
      SearchableText.forLabel(String.format("%s Console", Blaze.defaultBuildSystemName()));
  private static final ConfigurableSetting<?, ? extends LabeledComponent<?, ?>>
      SHOW_CONSOLE_ON_SYNC =
          setting("On Sync:")
              .getter(BlazeUserSettings::getShowBlazeConsoleOnSync)
              .setter(BlazeUserSettings::setShowBlazeConsoleOnSync)
              .componentFactory(LabeledComponent.comboBoxFactory(FocusBehavior.class));
  private static final ConfigurableSetting<?, ? extends LabeledComponent<?, ?>>
      SHOW_CONSOLE_ON_RUN =
          setting("For Run/Debug actions:")
              .getter(BlazeUserSettings::getShowBlazeConsoleOnRun)
              .setter(BlazeUserSettings::setShowBlazeConsoleOnRun)
              .componentFactory(LabeledComponent.comboBoxFactory(FocusBehavior.class));

  private static final SearchableText SHOW_PROBLEMS_VIEW_TEXT =
      SearchableText.forLabel("Problems View");
  private static final ConfigurableSetting<?, ? extends LabeledComponent<?, ?>>
      SHOW_PROBLEMS_VIEW_ON_SYNC =
          setting(SHOW_CONSOLE_ON_SYNC.label())
              .getter(BlazeUserSettings::getShowProblemsViewOnSync)
              .setter(BlazeUserSettings::setShowProblemsViewOnSync)
              .componentFactory(LabeledComponent.comboBoxFactory(FocusBehavior.class));
  private static final ConfigurableSetting<?, ? extends LabeledComponent<?, ?>>
      SHOW_PROBLEMS_VIEW_ON_RUN =
          setting(SHOW_CONSOLE_ON_RUN.label())
              .getter(BlazeUserSettings::getShowProblemsViewOnRun)
              .setter(BlazeUserSettings::setShowProblemsViewOnRun)
              .componentFactory(LabeledComponent.comboBoxFactory(FocusBehavior.class));

  private static final ConfigurableSetting<?, ?> COLLAPSE_PROJECT_VIEW =
      setting("Collapse project view directory roots")
          .getter(BlazeUserSettings::getCollapseProjectView)
          .setter(BlazeUserSettings::setCollapseProjectView)
          .componentFactory(SimpleComponent::createCheckBox);

  private static final ConfigurableSetting<?, ?> ALWAYS_SELECT_NEWEST_CHILD_TASK =
      setting("Always select the newest child task in Blaze view")
          .getter(BlazeUserSettings::getSelectNewestChildTask)
          .setter(BlazeUserSettings::setSelectNewestChildTask)
          .componentFactory(SimpleComponent::createCheckBox);

  private static final ConfigurableSetting<?, ?> FORMAT_BUILD_FILES_ON_SAVE =
      setting("Automatically format BUILD/Starlark files on file save")
          .getter(BlazeUserSettings::getFormatBuildFilesOnSave)
          .setter(BlazeUserSettings::setFormatBuildFilesOnSave)
          .componentFactory(SimpleComponent::createCheckBox);

  public static final ConfigurableSetting<?, ?> SHOW_ADD_FILE_TO_PROJECT =
      setting("Show 'Add source to project' editor notifications")
          .getter(BlazeUserSettings::getShowAddFileToProjectNotification)
          .setter(BlazeUserSettings::setShowAddFileToProjectNotification)
          .componentFactory(SimpleComponent::createCheckBox);

  private static final String BLAZE_BINARY_PATH_KEY = "blaze.binary.path";
  private static final ConfigurableSetting<?, ?> BLAZE_BINARY_PATH =
      setting("Blaze binary location")
          .getter(BlazeUserSettings::getBlazeBinaryPath)
          .setter(BlazeUserSettings::setBlazeBinaryPath)
          .hideIf(() -> !BuildSystemProvider.isBuildSystemAvailable(BuildSystemName.Blaze))
          .componentFactory(fileSelector(BLAZE_BINARY_PATH_KEY, "Specify the blaze binary path"));

  public static final String BAZEL_BINARY_PATH_KEY = "bazel.binary.path";
  private static final ConfigurableSetting<?, ?> BAZEL_BINARY_PATH =
      setting("Bazel binary location")
          .getter(BlazeUserSettings::getBazelBinaryPath)
          .setter(BlazeUserSettings::setBazelBinaryPath)
          .hideIf(() -> !BuildSystemProvider.isBuildSystemAvailable(BuildSystemName.Bazel))
          .componentFactory(fileSelector(BAZEL_BINARY_PATH_KEY, "Specify the bazel binary path"));

  public static final String BUILDIFIER_BINARY_PATH_KEY = "buildifier.binary.path";
  private static final ConfigurableSetting<?, ?> BUILDIFIER_BINARY_PATH =
      setting("Buildifier binary location")
          .getter(BlazeUserSettings::getBuildifierBinaryPath)
          .setter(BlazeUserSettings::setBuildifierBinaryPath)
          .componentFactory(
              fileSelector(BUILDIFIER_BINARY_PATH_KEY, "Specify the buildifier binary path"));

  private static final ImmutableList<ConfigurableSetting<?, ?>> SETTINGS =
      ImmutableList.of(
          SHOW_CONSOLE_ON_SYNC,
          SHOW_CONSOLE_ON_RUN,
          SHOW_PROBLEMS_VIEW_ON_SYNC,
          SHOW_PROBLEMS_VIEW_ON_RUN,
          COLLAPSE_PROJECT_VIEW,
          FORMAT_BUILD_FILES_ON_SAVE,
          SHOW_ADD_FILE_TO_PROJECT,
          ALWAYS_SELECT_NEWEST_CHILD_TASK,
          BLAZE_BINARY_PATH,
          BAZEL_BINARY_PATH,
          BUILDIFIER_BINARY_PATH);

  private static ConfigurableSetting.Builder<BlazeUserSettings> setting(String label) {
    return ConfigurableSetting.builder(BlazeUserSettings::getInstance).label(label);
  }

  private static ComponentFactory<LabeledComponent<String, FileSelectorWithStoredHistory>>
      fileSelector(String historyKey, String title) {
    return LabeledComponent.factory(
        () -> FileSelectorWithStoredHistory.create(historyKey, title),
        s -> Strings.nullToEmpty(s.getText()).trim(),
        FileSelectorWithStoredHistory::setTextWithHistory);
  }

  private BlazeUserSettingsConfigurable() {
    super(SETTINGS);
  }

  @Override
  public JComponent createComponent() {
    return SwingHelper.newLeftAlignedVerticalPanel(
        getFocusBehaviorSettingsUi(),
        createVerticalPanel(
            COLLAPSE_PROJECT_VIEW,
            FORMAT_BUILD_FILES_ON_SAVE,
            SHOW_ADD_FILE_TO_PROJECT,
            ALWAYS_SELECT_NEWEST_CHILD_TASK,
            BLAZE_BINARY_PATH,
            BAZEL_BINARY_PATH,
            BUILDIFIER_BINARY_PATH));
  }

  private JComponent getFocusBehaviorSettingsUi() {
    JPanel panel = new JPanel();
    panel.setBorder(
        IdeBorderFactory.createTitledBorder(
            TOOL_WINDOW_POPUP_BEHAVIOR_TEXT.label(), /* hasIndent= */ true));
    panel.setLayout(new GridLayoutManager(3, 6, JBUI.emptyInsets(), -1, -1));

    // blaze console settings
    JLabel label = new JBLabel(SHOW_CONSOLE_TEXT.label());
    label.setFont(JBFont.create(label.getFont(), /* tryToScale= */ false).asBold());
    panel.add(label, defaultNoGrowConstraints(0, 0, 1, 3));
    LabeledComponent<?, ?> showBlazeConsoleOnSync = getComponent(SHOW_CONSOLE_ON_SYNC);
    LabeledComponent<?, ?> showBlazeConsoleOnRun = getComponent(SHOW_CONSOLE_ON_RUN);
    panel.add(showBlazeConsoleOnSync.getLabel(), defaultNoGrowConstraints(1, 0, 1, 1));
    panel.add(showBlazeConsoleOnRun.getLabel(), defaultNoGrowConstraints(2, 0, 1, 1));
    panel.add(showBlazeConsoleOnSync.getComponent(), defaultNoGrowConstraints(1, 1, 1, 1));
    panel.add(showBlazeConsoleOnRun.getComponent(), defaultNoGrowConstraints(2, 1, 1, 1));
    panel.add(
        Box.createHorizontalGlue(),
        new GridConstraints(
            1,
            2,
            2,
            1,
            GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_GROW,
            null,
            null,
            null,
            0,
            false));

    // problems view settings
    label = new JBLabel(SHOW_PROBLEMS_VIEW_TEXT.label());
    label.setFont(JBFont.create(label.getFont(), /* tryToScale= */ false).asBold());
    panel.add(label, defaultNoGrowConstraints(0, 3, 1, 3));
    LabeledComponent<?, ?> showProblemsViewOnSync = getComponent(SHOW_PROBLEMS_VIEW_ON_SYNC);
    LabeledComponent<?, ?> showProblemsViewOnRun = getComponent(SHOW_PROBLEMS_VIEW_ON_RUN);
    panel.add(showProblemsViewOnSync.getLabel(), defaultNoGrowConstraints(1, 3, 1, 1));
    panel.add(showProblemsViewOnRun.getLabel(), defaultNoGrowConstraints(2, 3, 1, 1));
    panel.add(showProblemsViewOnSync.getComponent(), defaultNoGrowConstraints(1, 4, 1, 1));
    panel.add(showProblemsViewOnRun.getComponent(), defaultNoGrowConstraints(2, 4, 1, 1));
    panel.add(
        Box.createHorizontalGlue(),
        new GridConstraints(
            1,
            5,
            2,
            1,
            GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_GROW,
            null,
            null,
            null,
            0,
            false));
    return panel;
  }

  private static GridConstraints defaultNoGrowConstraints(
      int rowIndex, int columnIndex, int rowSpan, int columnSpan) {
    return new GridConstraints(
        rowIndex,
        columnIndex,
        rowSpan,
        columnSpan,
        GridConstraints.ANCHOR_WEST,
        GridConstraints.FILL_NONE,
        GridConstraints.SIZEPOLICY_FIXED,
        GridConstraints.SIZEPOLICY_FIXED,
        null,
        null,
        null,
        0,
        false);
  }
}
