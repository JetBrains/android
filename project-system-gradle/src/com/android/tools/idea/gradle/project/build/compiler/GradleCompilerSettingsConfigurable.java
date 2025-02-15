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
package com.android.tools.idea.gradle.project.build.compiler;

import com.google.common.base.Objects;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import java.awt.Insets;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.google.common.base.Strings.nullToEmpty;

/**
 * Configuration page for Gradle compiler settings.
 */
public class GradleCompilerSettingsConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private final CompilerConfiguration myCompilerConfiguration;
  private final AndroidGradleBuildConfiguration myBuildConfiguration;

  private JPanel myContentPanel;

  private JCheckBox myParallelBuildCheckBox;

  @SuppressWarnings("UnusedDeclaration")
  private HyperlinkLabel myParallelBuildDocHyperlinkLabel;

  private RawCommandLineEditor myCommandLineOptionsEditor;
  @SuppressWarnings("UnusedDeclaration")
  private HyperlinkLabel myCommandLineOptionsDocHyperlinkLabel;
  private JCheckBox myContinueBuildWithErrors;

  private final String myDisplayName;

  public GradleCompilerSettingsConfigurable(@NotNull Project project, @NotNull String displayName) {
    myDisplayName = displayName;
    setupUI();
    myCompilerConfiguration = CompilerConfiguration.getInstance(project);
    myBuildConfiguration = AndroidGradleBuildConfiguration.getInstance(project);
  }

  @Override
  @NotNull
  public String getId() {
    return "gradle.compiler";
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
    return myCompilerConfiguration.isParallelCompilationEnabled() != isParallelBuildsEnabled() ||
           myBuildConfiguration.CONTINUE_FAILED_BUILD != isContinueWithFailuresEnabled() ||
           !Objects.equal(getCommandLineOptions(), myBuildConfiguration.COMMAND_LINE_OPTIONS);
  }

  @Override
  public void apply() {
    if (myCompilerConfiguration.isParallelCompilationEnabled() != isParallelBuildsEnabled()) {
      myCompilerConfiguration.setParallelCompilationEnabled(isParallelBuildsEnabled());
    }
    myBuildConfiguration.COMMAND_LINE_OPTIONS = getCommandLineOptions();
    myBuildConfiguration.CONTINUE_FAILED_BUILD = isContinueWithFailuresEnabled();
  }

  private boolean isParallelBuildsEnabled() {
    return myParallelBuildCheckBox.isSelected();
  }

  private boolean isContinueWithFailuresEnabled() {
    return myContinueBuildWithErrors.isSelected();
  }

  @NotNull
  private String getCommandLineOptions() {
    return myCommandLineOptionsEditor.getText().trim();
  }

  @Override
  public void reset() {
    myParallelBuildCheckBox.setSelected(myCompilerConfiguration.isParallelCompilationEnabled());
    String commandLineOptions = nullToEmpty(myBuildConfiguration.COMMAND_LINE_OPTIONS);
    myContinueBuildWithErrors.setSelected(myBuildConfiguration.CONTINUE_FAILED_BUILD);
    myCommandLineOptionsEditor.setText(commandLineOptions);
  }

  @Override
  public void disposeUIResources() {
  }

  private void createUIComponents() {
    myParallelBuildDocHyperlinkLabel =
      createHyperlinkLabel("This option is in \"incubation\" and should only be used with ", "decoupled projects", ".",
                           "https://developer.android.com/r/tools/gradle-multi-project-decoupled-projects");

    myCommandLineOptionsDocHyperlinkLabel =
      createHyperlinkLabel("Example: --stacktrace --debug (for more information, please read Gradle's ", "documentation", ".)",
                           "http://www.gradle.org/docs/current/userguide/gradle_command_line.html");

    myCommandLineOptionsEditor = new RawCommandLineEditor();
    myCommandLineOptionsEditor.setDialogCaption("Command-line Options");
  }

  private void setupUI() {
    createUIComponents();
    myContentPanel = new JPanel();
    myContentPanel.setLayout(new GridLayoutManager(11, 3, new Insets(0, 0, 0, 0), -1, -1));
    final Spacer spacer1 = new Spacer();
    myContentPanel.add(spacer1, new GridConstraints(10, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                    GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    myParallelBuildCheckBox = new JCheckBox();
    myParallelBuildCheckBox.setText("Compile independent modules in parallel (may require larger heap size)");
    myContentPanel.add(myParallelBuildCheckBox, new GridConstraints(2, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                    GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                    null, null, null, 0, false));
    myContentPanel.add(myParallelBuildDocHyperlinkLabel,
                       new GridConstraints(3, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0,
                                           false));
    final JBLabel jBLabel1 = new JBLabel();
    jBLabel1.setText("<html><br></html>");
    myContentPanel.add(jBLabel1, new GridConstraints(4, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                     GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                     0, false));
    final JBLabel jBLabel2 = new JBLabel();
    jBLabel2.setText("<html><b>Note:</b> These settings are used for <b>compiling</b> Gradle-based Android projects.</html>");
    myContentPanel.add(jBLabel2, new GridConstraints(0, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                     GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                     0, false));
    final JBLabel jBLabel3 = new JBLabel();
    jBLabel3.setText("<html><br></html>");
    myContentPanel.add(jBLabel3, new GridConstraints(1, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                     GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                     0, false));
    final JBLabel jBLabel4 = new JBLabel();
    jBLabel4.setText("<html><br></html>");
    myContentPanel.add(jBLabel4, new GridConstraints(7, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                     GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                     0, false));
    final JBLabel jBLabel5 = new JBLabel();
    jBLabel5.setText("Command-line Options:");
    myContentPanel.add(jBLabel5, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                     0, false));
    myContentPanel.add(myCommandLineOptionsEditor,
                       new GridConstraints(5, 1, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                           GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myContentPanel.add(myCommandLineOptionsDocHyperlinkLabel,
                       new GridConstraints(6, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0,
                                           false));
    myContinueBuildWithErrors = new JCheckBox();
    myContinueBuildWithErrors.setText("Continue the build after failures");
    myContentPanel.add(myContinueBuildWithErrors, new GridConstraints(8, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                      GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                      null, null, null, 0, false));
    final JBLabel jBLabel6 = new JBLabel();
    jBLabel6.setText("<html><br></html>");
    myContentPanel.add(jBLabel6, new GridConstraints(9, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                     GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                     0, false));
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
