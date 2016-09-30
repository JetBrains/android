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

import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessages;
import com.android.tools.idea.gradle.project.sync.setup.module.AndroidModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.setup.project.PostSyncProjectSetupStep;
import com.android.tools.idea.gradle.project.sync.validation.AndroidProjectValidator;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
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

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.ANDROID_MODEL;
import static com.android.tools.idea.gradle.project.sync.messages.GroupNames.EXTRA_GENERATED_SOURCES;
import static com.android.tools.idea.gradle.project.sync.messages.MessageType.INFO;
import static com.android.tools.idea.gradle.project.sync.messages.MessageType.WARNING;

/**
 * Service that sets an Android SDK and facets to the modules of a project that has been imported from an Android-Gradle project.
 */
public class AndroidGradleModelDataService extends AbstractProjectDataService<AndroidGradleModel, Void> {
  private static final Logger LOG = Logger.getInstance(AndroidGradleModelDataService.class);

  private final List<AndroidModuleSetupStep> mySetupSteps;

  // This constructor is called by the IDE. See this module's plugin.xml file, implementation of extension 'externalProjectDataService'.
  @SuppressWarnings("unused")
  public AndroidGradleModelDataService() {
    this(ImmutableList.copyOf(AndroidModuleSetupStep.getExtensions()));
  }

  @VisibleForTesting
  AndroidGradleModelDataService(@NotNull List<AndroidModuleSetupStep> setupSteps) {
    mySetupSteps = setupSteps;
  }

  @NotNull
  @Override
  public Key<AndroidGradleModel> getTargetDataKey() {
    return ANDROID_MODEL;
  }

  /**
   * Sets an Android SDK and facets to the modules of a project that has been imported from an Android-Gradle project.
   *
   * @param toImport contains the Android-Gradle project.
   * @param project  IDEA project to configure.
   */
  @Override
  public void importData(@NotNull Collection<DataNode<AndroidGradleModel>> toImport,
                         @Nullable ProjectData projectData,
                         @NotNull Project project,
                         @NotNull IdeModifiableModelsProvider modelsProvider) {
    if (!toImport.isEmpty()) {
      try {
        doImport(toImport, project, modelsProvider);
      }
      catch (Throwable e) {
        LOG.info(String.format("Failed to set up Android modules in project '%1$s'", project.getName()), e);
        String msg = e.getMessage();
        if (msg == null) {
          msg = e.getClass().getCanonicalName();
        }
        GradleSyncState.getInstance(project).syncFailed(msg);
      }
    }
  }

  private void doImport(@NotNull Collection<DataNode<AndroidGradleModel>> toImport,
                        @NotNull Project project,
                        @NotNull IdeModifiableModelsProvider modelsProvider) throws Throwable {
    RunResult result = new WriteCommandAction.Simple(project) {
      @Override
      protected void run() throws Throwable {
        SyncMessages messages = SyncMessages.getInstance(project);
        boolean hasExtraGeneratedFolders = false;

        AndroidProjectValidator projectValidator = new AndroidProjectValidator(project);
        Map<String, AndroidGradleModel> androidModelsByModuleName = indexByModuleName(toImport);

        for (Module module : modelsProvider.getModules()) {
          AndroidGradleModel androidModel = androidModelsByModuleName.get(module.getName());

          setUpModule(module, modelsProvider, androidModel);
          if (androidModel != null) {
            projectValidator.validate(module, androidModel);

            // Warn users that there are generated source folders at the wrong location.
            File[] sourceFolders = androidModel.getExtraGeneratedSourceFolders();
            if (sourceFolders.length > 0) {
              hasExtraGeneratedFolders = true;
            }
            for (File folder : sourceFolders) {
              // Have to add a word before the path, otherwise IDEA won't show it.
              String[] text = {"Folder " + folder.getPath()};
              messages.report(new SyncMessage(EXTRA_GENERATED_SOURCES, WARNING, text));
            }
          }
        }

        projectValidator.fixAndReportFoundIssues();

        if (hasExtraGeneratedFolders) {
          messages.report(new SyncMessage(EXTRA_GENERATED_SOURCES, INFO, "3rd-party Gradle plug-ins may be the cause"));
        }

        for (PostSyncProjectSetupStep projectSetupStep : PostSyncProjectSetupStep.getExtensions()) {
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
  private static Map<String, AndroidGradleModel> indexByModuleName(@NotNull Collection<DataNode<AndroidGradleModel>> dataNodes) {
    Map<String, AndroidGradleModel> index = Maps.newHashMap();
    for (DataNode<AndroidGradleModel> d : dataNodes) {
      AndroidGradleModel androidModel = d.getData();
      index.put(androidModel.getModuleName(), androidModel);
    }
    return index;
  }

  private void setUpModule(@NotNull Module module,
                           @NotNull IdeModifiableModelsProvider modelsProvider,
                           @Nullable AndroidGradleModel androidModel) {
    for (AndroidModuleSetupStep setupStep : mySetupSteps) {
      setupStep.setUpModule(module, modelsProvider, androidModel, null, null);
    }
  }
}
