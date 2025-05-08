package com.android.tools.idea.gradle.project;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
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
    IdeaGradleSystemSettingsControlBuilder gradleSettingsControlBuilder;
    if (initialSettings.getProject().isDefault()) {
      Disposable disposable = Disposer.newDisposable("AndroidStudioGradleSettingsControlProvider.disposable");
      gradleSettingsControlBuilder = Registry.is("gradle.daemon.jvm.criteria.new.project")
                                     ? new AndroidDefaultGradleJvmCriteriaControlBuilder(initialSettings, disposable)
                                     : new AndroidDefaultGradleJdkControlBuilder(initialSettings, disposable);
    } else {
      gradleSettingsControlBuilder = new IdeaGradleSystemSettingsControlBuilder(initialSettings);
    }

    return gradleSettingsControlBuilder
      .dropVmOptions()
      .dropDefaultProjectSettings()
      .dropStoreExternallyCheckBox();
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
