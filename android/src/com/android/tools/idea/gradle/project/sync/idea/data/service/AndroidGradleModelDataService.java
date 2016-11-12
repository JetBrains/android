/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.idea.data.service;

import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.setup.module.AndroidModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.project.PostSyncProjectSetupStep;
import com.android.tools.idea.gradle.project.sync.validation.android.AndroidModuleValidator;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.RunResult;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.ANDROID_MODEL;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

/**
 * Service that sets an Android SDK and facets to the modules of a project that has been imported from an Android-Gradle project.
 */
public class AndroidGradleModelDataService extends AbstractProjectDataService<AndroidModuleModel, Void> {
  @NotNull private final AndroidModuleSetup myModuleSetup;
  @NotNull private final AndroidModuleValidator.Factory myModuleValidatorFactory;
  @NotNull private final PostSyncProjectSetupStep[] myProjectSetupSteps;

  // This constructor is called by the IDE. See this module's plugin.xml file, implementation of extension 'externalProjectDataService'.
  @SuppressWarnings("unused")
  public AndroidGradleModelDataService() {
    this(new AndroidModuleSetup(), new AndroidModuleValidator.Factory(), PostSyncProjectSetupStep.getExtensions());
  }

  @VisibleForTesting
  AndroidGradleModelDataService(@NotNull AndroidModuleSetup moduleSetup,
                                @NotNull AndroidModuleValidator.Factory moduleValidatorFactory,
                                @NotNull PostSyncProjectSetupStep[] projectSetupSteps) {
    myModuleSetup = moduleSetup;
    myModuleValidatorFactory = moduleValidatorFactory;
    myProjectSetupSteps = projectSetupSteps;
  }

  @Override
  @NotNull
  public Key<AndroidModuleModel> getTargetDataKey() {
    return ANDROID_MODEL;
  }

  /**
   * Sets an Android SDK and facets to the modules of a project that has been imported from an Android-Gradle project.
   *
   * @param toImport contains the Android-Gradle project.
   * @param project  IDEA project to configure.
   */
  @Override
  public void importData(@NotNull Collection<DataNode<AndroidModuleModel>> toImport,
                         @Nullable ProjectData projectData,
                         @NotNull Project project,
                         @NotNull IdeModifiableModelsProvider modelsProvider) {
    if (!toImport.isEmpty()) {
      try {
        doImport(toImport, project, modelsProvider);
      }
      catch (Throwable e) {
        getLog().info(String.format("Failed to set up Android modules in project '%1$s'", project.getName()), e);
        String msg = e.getMessage();
        GradleSyncState.getInstance(project).syncFailed(isNotEmpty(msg) ? msg : e.getClass().getCanonicalName());
      }
    }
  }

  private void doImport(@NotNull Collection<DataNode<AndroidModuleModel>> toImport,
                        @NotNull Project project,
                        @NotNull IdeModifiableModelsProvider modelsProvider) throws Throwable {
    RunResult result = new WriteCommandAction.Simple(project) {
      @Override
      protected void run() throws Throwable {
        AndroidModuleValidator moduleValidator = myModuleValidatorFactory.create(project);
        Map<String, AndroidModuleModel> androidModelsByModuleName = indexByModuleName(toImport);

        for (Module module : modelsProvider.getModules()) {
          AndroidModuleModel androidModel = androidModelsByModuleName.get(module.getName());
          setUpModule(module, moduleValidator, modelsProvider, androidModel);
        }

        moduleValidator.fixAndReportFoundIssues();

        for (PostSyncProjectSetupStep projectSetupStep : myProjectSetupSteps) {
          projectSetupStep.setUpProject(project, modelsProvider, null);
        }
      }
    }.execute();
    Throwable error = result.getThrowable();
    if (error != null) {
      throw error;
    }
  }

  @NotNull
  private static Map<String, AndroidModuleModel> indexByModuleName(@NotNull Collection<DataNode<AndroidModuleModel>> dataNodes) {
    Map<String, AndroidModuleModel> index = Maps.newHashMap();
    for (DataNode<AndroidModuleModel> dataNode : dataNodes) {
      AndroidModuleModel androidModel = dataNode.getData();
      index.put(androidModel.getModuleName(), androidModel);
    }
    return index;
  }

  private void setUpModule(@NotNull Module module,
                           @NotNull AndroidModuleValidator moduleValidator,
                           @NotNull IdeModifiableModelsProvider modelsProvider,
                           @Nullable AndroidModuleModel androidModel) {
    myModuleSetup.setUpModule(module, modelsProvider, androidModel, null, null);
    if (androidModel != null) {
      moduleValidator.validate(module, androidModel);
    }
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(AndroidGradleModelDataService.class);
  }
}
