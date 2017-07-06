package com.android.tools.idea.gradle.project;

import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.execution.GradleOrderEnumeratorHandler;
import org.jetbrains.plugins.gradle.execution.GradleOrderEnumeratorHandler.FactoryImpl;

/**
 * {@link AndroidGradleOrderEnumeratorHandlerFactory} was introduced to make
 * order entries enumeration of Android modules consistent with the Android gradle importer.
 * Android gradle importer uses first-level dependencies for android modules,
 * and each module has to "export" their dependencies to make them visible to dependent modules.
 * <p>
 * Non-android gradle modules don't have such restriction (there will always be fully resolved dependency graph as a flat list)
 * and should not be affected by the recursive enumeration.
 * Which can lead to unexpected runtime classpath and performance degradation.
 *
 * @author Vladislav.Soroka
 */
public class AndroidGradleOrderEnumeratorHandlerFactory extends FactoryImpl {
  @Override
  public boolean isApplicable(@NotNull Module module) {
    return AndroidModuleModel.get(module) != null;
  }

  @Override
  public GradleOrderEnumeratorHandler createHandler(@NotNull Module module) {
    return new GradleOrderEnumeratorHandler(module) {
      @Override
      public boolean shouldAddRuntimeDependenciesToTestCompilationClasspath() {
        return false;
      }

      @Override
      public boolean shouldIncludeTestsFromDependentModulesToTestClasspath() {
        return true;
      }

      @Override
      public boolean shouldProcessDependenciesRecursively() {
        return true;
      }
    };
  }
}
