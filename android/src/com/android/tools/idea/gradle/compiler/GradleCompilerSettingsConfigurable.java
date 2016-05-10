/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.compiler;

import com.google.common.base.Objects;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.google.common.base.Strings.nullToEmpty;

/**
 * Configuration page for Gradle compiler settings.
 */
public class GradleCompilerSettingsConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private final CompilerWorkspaceConfiguration myCompilerConfiguration;
  private final AndroidGradleBuildConfiguration myBuildConfiguration;

  private JCheckBox myParallelBuildCheckBox;

  @SuppressWarnings("UnusedDeclaration")
  private HyperlinkLabel myParallelBuildDocHyperlinkLabel;

  private JCheckBox myAutoMakeCheckBox;
  private JBLabel myUseInProcessBuildLabel;
  private JCheckBox myUseInProcessBuildCheckBox;
  private JPanel myContentPanel;

  private RawCommandLineEditor myCommandLineOptionsEditor;
  @SuppressWarnings("UnusedDeclaration")
  private HyperlinkLabel myCommandLineOptionsDocHyperlinkLabel;

  private JCheckBox myConfigureOnDemandCheckBox;
  @SuppressWarnings("UnusedDeclaration")
  private HyperlinkLabel myConfigureOnDemandDocHyperlinkLabel;
  private JBLabel myUseInProcessBuildSpacing;

  private final String myDisplayName;

  public GradleCompilerSettingsConfigurable(@NotNull Project project, @NotNull String displayName) {
    myDisplayName = displayName;
    myCompilerConfiguration = CompilerWorkspaceConfiguration.getInstance(project);
    myBuildConfiguration = AndroidGradleBuildConfiguration.getInstance(project);
  }

  @Override
  @NotNull
  public String getId() {
    return "gradle.compiler";
  }

  @Override
  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }

  @Override
  @Nls
  public String getDisplayName() {
    return myDisplayName;
  }

  @Override
  @Nullable
  public String getHelpTopic() {
    return "reference.projectsettings.compiler.gradle";
  }

  @Override
  @Nullable
  public JComponent createComponent() {
    return myContentPanel;
  }

  @Override
  public boolean isModified() {
    return myCompilerConfiguration.PARALLEL_COMPILATION != isParallelBuildsEnabled() ||
           myCompilerConfiguration.MAKE_PROJECT_ON_SAVE != isAutoMakeEnabled() ||
           myBuildConfiguration.USE_EXPERIMENTAL_FASTER_BUILD != isExperimentalBuildEnabled() ||
           myBuildConfiguration.USE_CONFIGURATION_ON_DEMAND != isConfigurationOnDemandEnabled() ||
           !Objects.equal(getCommandLineOptions(), myBuildConfiguration.COMMAND_LINE_OPTIONS);
  }

  @Override
  public void apply() {
    myCompilerConfiguration.PARALLEL_COMPILATION = isParallelBuildsEnabled();
    myCompilerConfiguration.MAKE_PROJECT_ON_SAVE = isAutoMakeEnabled();
    myBuildConfiguration.USE_EXPERIMENTAL_FASTER_BUILD = isExperimentalBuildEnabled();
    myBuildConfiguration.COMMAND_LINE_OPTIONS = getCommandLineOptions();
    myBuildConfiguration.USE_CONFIGURATION_ON_DEMAND = isConfigurationOnDemandEnabled();
  }

  private boolean isParallelBuildsEnabled() {
    return myParallelBuildCheckBox.isSelected();
  }

  private boolean isAutoMakeEnabled() {
    return myAutoMakeCheckBox.isSelected();
  }

  private boolean isExperimentalBuildEnabled() {
    return myUseInProcessBuildCheckBox.isSelected();
  }

  private boolean isConfigurationOnDemandEnabled() {
    return myConfigureOnDemandCheckBox.isSelected();
  }

  @NotNull
  private String getCommandLineOptions() {
    return myCommandLineOptionsEditor.getText().trim();
  }

  @Override
  public void reset() {
    myParallelBuildCheckBox.setSelected(myCompilerConfiguration.PARALLEL_COMPILATION);
    myAutoMakeCheckBox.setSelected(myCompilerConfiguration.MAKE_PROJECT_ON_SAVE);
    myUseInProcessBuildCheckBox.setSelected(myBuildConfiguration.USE_EXPERIMENTAL_FASTER_BUILD);
    myConfigureOnDemandCheckBox.setSelected(myBuildConfiguration.USE_CONFIGURATION_ON_DEMAND);
    myAutoMakeCheckBox.setText("Make project automatically (only works while not running / debugging" +
                               (PowerSaveMode.isEnabled() ? ", disabled in Power Save mode" : "") +
                               ")");
    String commandLineOptions = nullToEmpty(myBuildConfiguration.COMMAND_LINE_OPTIONS);
    myCommandLineOptionsEditor.setText(commandLineOptions);
    myConfigureOnDemandCheckBox.setSelected(myBuildConfiguration.USE_CONFIGURATION_ON_DEMAND);
  }

  @Override
  public void disposeUIResources() {
  }

  private void createUIComponents() {
    myParallelBuildDocHyperlinkLabel =
      createHyperlinkLabel("This option is in \"incubation\" and should only be used with ", "decoupled projects", ".",
                           "http://www.gradle.org/docs/current/userguide/multi_project_builds.html#sec:decoupled_projects");

    myCommandLineOptionsDocHyperlinkLabel =
      createHyperlinkLabel("Example: --stacktrace --debug (for more information, please read Gradle's ", "documentation", ".)",
                           "http://www.gradle.org/docs/current/userguide/gradle_command_line.html");

    myConfigureOnDemandDocHyperlinkLabel =
      createHyperlinkLabel("This option may speed up builds. This option is in \"incubation.\" Please read Gradle's ", "documentation", ".",
                           "http://www.gradle.org/docs/current/userguide/multi_project_builds.html#sec:configuration_on_demand");

    myCommandLineOptionsEditor = new RawCommandLineEditor();
    myCommandLineOptionsEditor.setDialogCaption("Command-line Options");
  }

  @NotNull
  private static HyperlinkLabel createHyperlinkLabel(@NotNull String beforeLinkText,
                                                     @NotNull String linkText,
                                                     @NotNull String afterLinkText,
                                                     @NotNull String target) {
    HyperlinkLabel label = new HyperlinkLabel();
    label.setHyperlinkText(beforeLinkText, linkText, afterLinkText);
    label.setHyperlinkTarget(target);
    return label;
  }
}
