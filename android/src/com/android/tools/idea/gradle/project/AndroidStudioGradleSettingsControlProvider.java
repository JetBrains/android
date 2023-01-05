package com.android.tools.idea.gradle.project;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.settings.GradleProjectSettingsControlBuilder;
import org.jetbrains.plugins.gradle.service.settings.GradleSettingsControlProvider;
import org.jetbrains.plugins.gradle.service.settings.GradleSystemSettingsControlBuilder;
import org.jetbrains.plugins.gradle.service.settings.IdeaGradleSystemSettingsControlBuilder;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

public class AndroidStudioGradleSettingsControlProvider extends GradleSettingsControlProvider {
  @Override
  public String getPlatformPrefix() {
    return "AndroidStudio";
  }

  @Override
  public GradleSystemSettingsControlBuilder getSystemSettingsControlBuilder(@NotNull GradleSettings initialSettings) {
    return new IdeaGradleSystemSettingsControlBuilder(initialSettings)
      .dropVmOptions()
      .dropDefaultProjectSettings();
  }

  @Override
  public GradleProjectSettingsControlBuilder getProjectSettingsControlBuilder(@NotNull GradleProjectSettings initialSettings) {
    return new AndroidGradleProjectSettingsControlBuilder(initialSettings)
      .dropCustomizableWrapperButton()
      .dropUseBundledDistributionButton()
      .dropResolveModulePerSourceSetCheckBox()
      .dropDelegateBuildCombobox()
      .dropTestRunnerCombobox();
  }
}
