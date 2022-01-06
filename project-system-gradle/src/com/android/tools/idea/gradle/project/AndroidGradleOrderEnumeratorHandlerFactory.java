/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import static com.android.tools.idea.io.FilePaths.pathToIdeaUrl;

import com.android.tools.idea.gradle.model.IdeAndroidArtifact;
import com.android.tools.idea.gradle.model.IdeBaseArtifact;
import com.android.tools.idea.gradle.model.IdeJavaArtifact;
import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.android.tools.idea.io.FilePaths;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderRootType;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.execution.GradleOrderEnumeratorHandler;
import org.jetbrains.plugins.gradle.execution.GradleOrderEnumeratorHandler.FactoryImpl;
import org.jetbrains.plugins.gradle.settings.GradleLocalSettings;

/**
 * {@link AndroidGradleOrderEnumeratorHandlerFactory} was introduced to make
 * order entries enumeration of Android modules consistent with the Android gradle importer.
 * Android gradle importer uses first-level dependencies for android modules,
 * and each module has to "export" their dependencies to make them visible to dependent modules.
 * <p>
 * Non-android gradle modules don't have such restriction (there will always be fully resolved dependency graph as a flat list)
 * and should not be affected by the recursive enumeration.
 * Which can lead to unexpected runtime classpath and performance degradation.
 */
public class AndroidGradleOrderEnumeratorHandlerFactory extends FactoryImpl {
  @Override
  public boolean isApplicable(@NotNull Module module) {
    return GradleAndroidModel.get(module) != null;
  }

  @NotNull
  @Override
  public GradleOrderEnumeratorHandler createHandler(@NotNull Module module) {
    String rootProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module);
    // Always recurse for Android modules.
    boolean shouldRecurse = GradleAndroidModel.get(module) != null;
    if (rootProjectPath != null && !shouldRecurse) {
      // Only recurse when the Gradle version is less than 2.5. This is taken from the GradleOrderEnumeratorHandler to make sure that
      // for non-android modules we return a consistent value.
      String gradleVersion = GradleLocalSettings.getInstance(module.getProject()).getGradleVersion(rootProjectPath);
      shouldRecurse = gradleVersion != null && GradleVersion.version(gradleVersion).compareTo(GradleVersion.version("2.5")) < 0;
    }
    final boolean finalShouldRecurse = shouldRecurse;

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
        return finalShouldRecurse;
      }

      @Override
      public boolean addCustomModuleRoots(@NotNull OrderRootType type,
                                          @NotNull ModuleRootModel rootModel,
                                          @NotNull Collection<String> result,
                                          boolean includeProduction,
                                          boolean includeTests) {
        GradleAndroidModel androidModel = GradleAndroidModel.get(rootModel.getModule());
        if (androidModel == null) {
          return super.addCustomModuleRoots(type, rootModel, result, includeProduction, includeTests);
        }

        if (!type.equals(OrderRootType.CLASSES)) {
          return false;
        }

        super.addCustomModuleRoots(type, rootModel, result, includeProduction, includeTests);
        getAndroidCompilerOutputFolders(androidModel, includeProduction, includeTests).stream().filter((root) -> !result.contains(root))
          .forEachOrdered(result::add);
        return true;
      }
    };
  }

  @NotNull
  private static List<String> getAndroidCompilerOutputFolders(@NotNull GradleAndroidModel androidModel,
                                                              boolean includeProduction,
                                                              boolean includeTests) {
    List<String> toAdd = new LinkedList<>();
    // The test artifact must be added to the classpath before the main artifact, this is so that tests pick up the correct classes
    // if multiple definitions of the same class exist in both the test and the main artifact.
    if (includeTests) {
      if (androidModel.getSelectedVariant().getUnitTestArtifact() != null) {
        addFoldersFromJavaArtifact(androidModel.getSelectedVariant().getUnitTestArtifact(), toAdd);
      }
      if (androidModel.getSelectedVariant().getAndroidTestArtifact() != null) {
        addFoldersFromAndroidArtifact(androidModel.getSelectedVariant().getAndroidTestArtifact(), toAdd);
      }
      if (androidModel.getSelectedVariant().getTestFixturesArtifact() != null) {
        addFoldersFromAndroidArtifact(androidModel.getSelectedVariant().getTestFixturesArtifact(), toAdd);
      }
    }
    if (includeProduction) {
      addFoldersFromAndroidArtifact(androidModel.getSelectedVariant().getMainArtifact(), toAdd);
    }
    return toAdd;
  }

  private static void addFoldersFromBaseArtifact(@NotNull IdeBaseArtifact artifact, @NotNull List<String> toAdd) {
    artifact.getClassesFolder().stream()
      .filter(Objects::nonNull)
      .map(FilePaths::pathToIdeaUrl)
      .forEach(toAdd::add);
  }

  private static void addFoldersFromJavaArtifact(@NotNull IdeJavaArtifact artifact, @NotNull List<String> toAdd) {
    addFoldersFromBaseArtifact(artifact, toAdd);
  }

  private static void addFoldersFromAndroidArtifact(@NotNull IdeAndroidArtifact artifact, @NotNull List<String> toAdd) {
    addFoldersFromBaseArtifact(artifact, toAdd);
    artifact.getGeneratedResourceFolders().stream()
      .filter(Objects::nonNull)
      .map(FilePaths::pathToIdeaUrl)
      .forEach(toAdd::add);
  }
}
