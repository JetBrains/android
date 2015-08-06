package com.android.tools.idea.gradle.compiler;

import com.android.tools.idea.startup.AndroidStudioInitializer;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableProvider;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
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
    final String displayName = AndroidStudioInitializer.isAndroidStudio() ? "Compiler" : "Gradle-Android Compiler";
    return new GradleCompilerSettingsConfigurable(myProject, displayName);
  }
}
