package com.android.tools.idea.gradle.project.build.compiler;

import com.android.tools.idea.IdeInfo;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableProvider;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

public class GradleCompilerSettingsConfigurableProvider extends ConfigurableProvider {
  private final Project myProject;

  public GradleCompilerSettingsConfigurableProvider(Project project) {
    myProject = project;
  }

  @Nullable
  @Override
  public Configurable createConfigurable() {
    if (myProject == null) {
      return null;
    }
    return new GradleCompilerSettingsConfigurable(myProject, "Gradle-Android Compiler");
  }
}
