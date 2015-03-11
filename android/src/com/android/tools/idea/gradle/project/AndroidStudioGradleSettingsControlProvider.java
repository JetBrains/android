package com.android.tools.idea.gradle.project;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.settings.GradleProjectSettingsControlBuilder;
import org.jetbrains.plugins.gradle.service.settings.GradleSettingsControlProvider;
import org.jetbrains.plugins.gradle.service.settings.IdeaGradleProjectSettingsControlBuilder;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;

/**
 * @author Vladislav.Soroka
 * @since 2/24/2015
 */
public class AndroidStudioGradleSettingsControlProvider extends GradleSettingsControlProvider {
  @Override
  public String getPlatformPrefix() {
    return "AndroidStudio";
  }

  @Override
  public GradleProjectSettingsControlBuilder getProjectSettingsControlBuilder(@NotNull GradleProjectSettings initialSettings) {
    return new IdeaGradleProjectSettingsControlBuilder(initialSettings)
      .dropCustomizableWrapperButton()
      .dropUseBundledDistributionButton()
      .dropGradleJdkComponents()
      .dropCreateEmptyContentRootDirectoriesBox()
      .dropUseAutoImportBox();
  }
}
