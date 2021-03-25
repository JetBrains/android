package com.android.tools.idea.gradle.project;

import com.android.tools.idea.flags.StudioFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.settings.*;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

public class AndroidStudioGradleSettingsControlProvider extends GradleSettingsControlProvider {
  @Override
  public String getPlatformPrefix() {
    return "AndroidStudio";
  }

  @Override
  public GradleSystemSettingsControlBuilder getSystemSettingsControlBuilder(@NotNull GradleSettings initialSettings) {
    return new IdeaGradleSystemSettingsControlBuilder(initialSettings).dropVmOptions();
  }

  @Override
  public GradleProjectSettingsControlBuilder getProjectSettingsControlBuilder(@NotNull GradleProjectSettings initialSettings) {
    IdeaGradleProjectSettingsControlBuilder builder = new AndroidGradleProjectSettingsControlBuilder(initialSettings)
      .dropCustomizableWrapperButton()
      .dropUseBundledDistributionButton()
      .dropResolveModulePerSourceSetCheckBox()
      .dropDelegateBuildCombobox()
      .dropTestRunnerCombobox();
    if (!StudioFlags.ALLOW_JDK_PER_PROJECT.get()) {
      builder.dropGradleJdkComponents();
    }
    return builder;
  }
}
