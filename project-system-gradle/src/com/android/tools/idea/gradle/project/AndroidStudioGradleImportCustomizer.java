package com.android.tools.idea.gradle.project;

import org.jetbrains.plugins.gradle.service.project.GradleImportCustomizer;

public class AndroidStudioGradleImportCustomizer extends GradleImportCustomizer {
  @Override
  public String getPlatformPrefix() {
    return "AndroidStudio";
  }

  @Override
  public boolean useExtraJvmArgs() {
    return true;
  }
}
