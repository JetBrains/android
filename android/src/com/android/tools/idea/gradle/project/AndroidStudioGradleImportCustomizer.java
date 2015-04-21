package com.android.tools.idea.gradle.project;

import org.jetbrains.plugins.gradle.service.project.GradleImportCustomizer;

/**
 * @author Vladislav.Soroka
 * @since 4/21/2015
 */
public class AndroidStudioGradleImportCustomizer extends GradleImportCustomizer {
  @Override
  public String getPlatformPrefix() {
    return "AndroidStudio";
  }

  @Override
  public boolean useExtraJvmArgs() {
    return false;
  }
}
