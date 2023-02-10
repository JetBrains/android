/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.api;

import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.configurations.ConfigurationsModel;
import com.android.tools.idea.gradle.dsl.api.crashlytics.CrashlyticsModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.dsl.api.ext.ExtModel;
import com.android.tools.idea.gradle.dsl.api.java.JavaModel;
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoriesModel;
import com.android.tools.idea.gradle.dsl.api.settings.PluginsModel;
import com.android.tools.idea.gradle.dsl.api.GradlePropertiesModel;
import com.android.tools.idea.gradle.dsl.api.util.GradleDslContextModel;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import java.io.File;
import java.util.Map;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.List;
import java.util.Set;

public interface GradleBuildModel extends GradleDslContextModel, GradleFileModel, PluginsModel {
  /**
   * Runs the given supplier and returns the result if no exception was thrown. If an exception was thrown then
   * log it to back intellijs logs and the AndroidStudioCrashReporter and return null.
   *
   * @param supplier supplier to run
   * @return supplied value or null if an exception was thrown
   */
  @Nullable
  static <T> T tryOrLog(@NotNull Supplier<T> supplier) {
    try {
      return supplier.get();
    }
    catch (Exception e) {
      if (e instanceof ControlFlowException) {
        // Control-Flow exceptions should not be logged and reported.
        return null;
      }
      Logger logger = Logger.getInstance(ProjectBuildModel.class);
      logger.error(e);
      return null;
    }
  }

  /**
   * Obtains an instance of {@link GradleBuildModel} for the given projects root build.gradle file.
   * Care should be taken when calling this method repeatedly since it runs over the whole PSI tree in order to build the model.
   * In most cases if you want to use this method you should use {@link ProjectBuildModel} instead since it prevents files from being
   * parsed more than once and ensures changes in applied files are mirrored by any model obtained from it.
   *
   * @deprecated Use {@link ProjectBuildModel#get(Project)} instead.
   */
  @Deprecated
  @Nullable
  static GradleBuildModel get(@NotNull Project project) {
    return tryOrLog(() -> GradleModelProvider.getInstance().getBuildModel(project));
  }

  /**
   * Obtains an instance of {@link GradleBuildModel} for the given modules build.gradle file.
   * Care should be taken when calling this method repeatedly since it runs over the whole PSI tree in order to build the model.
   *
   * @deprecated Use {@link ProjectBuildModel#get(Project)} instead.
   */
  @Deprecated
  @Nullable
  static GradleBuildModel get(@NotNull Module module) {
    return tryOrLog(() -> GradleModelProvider.getInstance().getBuildModel(module));
  }

  /**
   * Obtains an instance of {@link GradleBuildModel} by parsing the given file.
   * Care should be taken when calling this method repeatedly since it runs over the whole PSI tree in order to build the model.
   *
   * @deprecated Use {@link ProjectBuildModel#getModuleBuildModel(Module)} instead.
   */
  @Deprecated
  @NotNull
  static GradleBuildModel parseBuildFile(@NotNull VirtualFile file, @NotNull Project project) {
    return GradleModelProvider.getInstance().parseBuildFile(file, project);
  }

  /**
   * In most cases in the Dsl api, we can obtain a PsiElement for an element from the corresponding model.  In the specific case of plugins,
   * where there are multiple forms of expression (including `apply plugin: ...` which has no corresponding block element) there is no
   * representation of the collection as a Dsl entity, only as a Java List.  This is therefore needed at this level for clients wishing
   * to interact with the Psi corresponding to a plugins { ... } block, if any.
   */
  @Nullable
  PsiElement getPluginsPsiElement();

  @NotNull
  AndroidModel android();

  @NotNull
  BuildScriptModel buildscript();

  @NotNull
  ConfigurationsModel configurations();

  @NotNull
  CrashlyticsModel crashlytics();

  @NotNull
  DependenciesModel dependencies();

  @NotNull
  ExtModel ext();

  @NotNull
  JavaModel java();

  @NotNull
  RepositoriesModel repositories();

  /**
   * @return the models for files that are used by this GradleBuildModel.
   */
  @NotNull
  Set<GradleFileModel> getInvolvedFiles();

  @NotNull
  Map<String, List<BuildModelNotification>> getNotifications();

  /**
   * @return the root directory of the module corresponding to this model.  Implementations are permitted to provide a best-effort
   * estimate for an answer.
   */
  @NotNull
  File getModuleRootDirectory();

  /**
   * @return a model for the properties file associated with this build model (typically only the root module's model), or null if
   * no such properties file exists.
   */
  @Nullable GradlePropertiesModel getPropertiesModel();

  /**
   * Removes repository property.
   */
  @TestOnly
  void removeRepositoriesBlocks();
}
