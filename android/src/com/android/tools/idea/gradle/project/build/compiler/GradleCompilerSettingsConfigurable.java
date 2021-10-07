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

import static com.google.common.base.Strings.nullToEmpty;

import com.android.tools.idea.project.AndroidProjectInfo;
import com.google.common.base.Objects;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.ide.PowerSaveMode;
import com.intellij.ide.actionsOnSave.ActionOnSaveBackedByOwnConfigurable;
import com.intellij.ide.actionsOnSave.ActionOnSaveComment;
import com.intellij.ide.actionsOnSave.ActionOnSaveContext;
import com.intellij.ide.actionsOnSave.ActionOnSaveInfo;
import com.intellij.ide.actionsOnSave.ActionOnSaveInfoProvider;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.components.ActionLink;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.swing.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Configuration page for Gradle compiler settings.
 */
public class GradleCompilerSettingsConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private static final String CONFIGURABLE_ID = "gradle.compiler";

  private final CompilerWorkspaceConfiguration myCompilerWorkspaceConfiguration;
  private final CompilerConfiguration myCompilerConfiguration;
  private final AndroidGradleBuildConfiguration myBuildConfiguration;

  private JPanel myContentPanel;

  private JCheckBox myParallelBuildCheckBox;

  @SuppressWarnings("UnusedDeclaration")
  private HyperlinkLabel myParallelBuildDocHyperlinkLabel;

  private JCheckBox myAutoMakeCheckBox;

  private RawCommandLineEditor myCommandLineOptionsEditor;
  @SuppressWarnings("UnusedDeclaration")
  private HyperlinkLabel myCommandLineOptionsDocHyperlinkLabel;
  private JCheckBox myContinueBuildWithErrors;

  private final String myDisplayName;

  public GradleCompilerSettingsConfigurable(@NotNull Project project, @NotNull String displayName) {
    myDisplayName = displayName;
    myCompilerWorkspaceConfiguration = CompilerWorkspaceConfiguration.getInstance(project);
    myCompilerConfiguration = CompilerConfiguration.getInstance(project);
    myBuildConfiguration = AndroidGradleBuildConfiguration.getInstance(project);
  }

  @Override
  @NotNull
  public String getId() {
    return CONFIGURABLE_ID;
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
           myCompilerWorkspaceConfiguration.MAKE_PROJECT_ON_SAVE != isAutoMakeEnabled() ||
           myBuildConfiguration.CONTINUE_FAILED_BUILD != isContinueWithFailuresEnabled() ||
           !Objects.equal(getCommandLineOptions(), myBuildConfiguration.COMMAND_LINE_OPTIONS);
  }

  @Override
  public void apply() {
    if (myCompilerConfiguration.isParallelCompilationEnabled() != isParallelBuildsEnabled()) {
      myCompilerConfiguration.setParallelCompilationEnabled(isParallelBuildsEnabled());
    }
    myCompilerWorkspaceConfiguration.MAKE_PROJECT_ON_SAVE = isAutoMakeEnabled();
    myBuildConfiguration.COMMAND_LINE_OPTIONS = getCommandLineOptions();
    myBuildConfiguration.CONTINUE_FAILED_BUILD = isContinueWithFailuresEnabled();
  }

  private boolean isParallelBuildsEnabled() {
    return myParallelBuildCheckBox.isSelected();
  }

  private boolean isAutoMakeEnabled() {
    return myAutoMakeCheckBox.isSelected();
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
    myAutoMakeCheckBox.setSelected(myCompilerWorkspaceConfiguration.MAKE_PROJECT_ON_SAVE);
    myAutoMakeCheckBox.setText("Make project automatically (only works while not running / debugging" +
                               (PowerSaveMode.isEnabled() ? ", disabled in Power Save mode" : "") +
                               ")");
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
                           "http://www.gradle.org/docs/current/userguide/multi_project_builds.html#sec:decoupled_projects");

    myCommandLineOptionsDocHyperlinkLabel =
      createHyperlinkLabel("Example: --stacktrace --debug (for more information, please read Gradle's ", "documentation", ".)",
                           "http://www.gradle.org/docs/current/userguide/gradle_command_line.html");

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


  public static class BuildOnSaveInfoProvider extends ActionOnSaveInfoProvider {
    @Override
    protected @NotNull Collection<? extends ActionOnSaveInfo> getActionOnSaveInfos(@NotNull ActionOnSaveContext context) {
      if (context.getSettings().find(CONFIGURABLE_ID) == null) {
        return Collections.emptyList();
      }

      // The condition must be the opposite of what is in com.android.tools.idea.gradle.project.build.compiler.HideCompilerOptions.isAvailable()
      return AndroidProjectInfo.getInstance(context.getProject()).requiresAndroidModel()
             ? List.of(new BuildOnSaveInfo(context))
             : Collections.emptyList();
    }

    @Override
    public Collection<String> getSearchableOptions() {
      return List.of(JavaCompilerBundle.message("settings.actions.on.save.page.build.project.on.save.checkbox"));
    }
  }


  /**
   * Pretty much the same as {@link com.intellij.compiler.options.BuildOnSaveInfo} but for {@link GradleCompilerSettingsConfigurable}.
   */
  private static class BuildOnSaveInfo extends ActionOnSaveBackedByOwnConfigurable<GradleCompilerSettingsConfigurable> {
    private BuildOnSaveInfo(@NotNull ActionOnSaveContext context) {
      super(context, CONFIGURABLE_ID, GradleCompilerSettingsConfigurable.class);
    }

    @Override
    public @NotNull String getActionOnSaveName() {
      return JavaCompilerBundle.message("settings.actions.on.save.page.build.project.on.save.checkbox");
    }

    @Override
    protected @Nullable ActionOnSaveComment getCommentAccordingToStoredState() {
      return ActionOnSaveComment.info(JavaCompilerBundle.message("settings.actions.on.save.page.build.project.on.save.checkbox.comment"));
    }

    @Override
    protected @Nullable ActionOnSaveComment getCommentAccordingToUiState(@NotNull GradleCompilerSettingsConfigurable configurable) {
      return ActionOnSaveComment.info(JavaCompilerBundle.message("settings.actions.on.save.page.build.project.on.save.checkbox.comment"));
    }

    @Override
    protected boolean isActionOnSaveEnabledAccordingToStoredState() {
      return CompilerWorkspaceConfiguration.getInstance(getProject()).MAKE_PROJECT_ON_SAVE;
    }

    @Override
    protected boolean isActionOnSaveEnabledAccordingToUiState(@NotNull GradleCompilerSettingsConfigurable configurable) {
      return configurable.myAutoMakeCheckBox.isSelected();
    }

    @Override
    protected void setActionOnSaveEnabled(@NotNull GradleCompilerSettingsConfigurable configurable, boolean enabled) {
      configurable.myAutoMakeCheckBox.setSelected(enabled);
    }

    @Override
    public @NotNull List<? extends ActionLink> getActionLinks() {
      String linkText = JavaCompilerBundle.message("settings.actions.on.save.page.compiler.settings.link");
      return List.of(createGoToPageInSettingsLink(linkText, CONFIGURABLE_ID));
    }

    @Override
    protected @NotNull String getActivatedOnDefaultText() {
      return getAnySaveAndExternalChangeText();
    }
  }
}
