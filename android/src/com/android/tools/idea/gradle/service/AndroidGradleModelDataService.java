/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.service;

import com.android.builder.model.AndroidProject;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.compiler.PostProjectBuildTasksExecutor;
import com.android.tools.idea.gradle.customizer.ModuleCustomizer;
import com.android.tools.idea.gradle.customizer.android.*;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.messages.Message;
import com.android.tools.idea.gradle.messages.ProjectSyncMessages;
import com.android.tools.idea.gradle.project.AndroidGradleNotification;
import com.android.tools.idea.gradle.service.notification.hyperlink.FixAndroidGradlePluginVersionHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.OpenUrlHyperlink;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.Jdks;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.RunResult;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.gradle.AndroidProjectKeys.ANDROID_MODEL;
import static com.android.tools.idea.gradle.messages.CommonMessageGroupNames.EXTRA_GENERATED_SOURCES;
import static com.android.tools.idea.gradle.messages.CommonMessageGroupNames.UNHANDLED_SYNC_ISSUE_TYPE;
import static com.android.tools.idea.gradle.messages.Message.Type.INFO;
import static com.android.tools.idea.gradle.messages.Message.Type.WARNING;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.gradle.util.GradleUtil.hasLayoutRenderingIssue;
import static com.android.tools.idea.sdk.Jdks.isApplicableJdk;
import static com.android.tools.idea.startup.AndroidStudioInitializer.isAndroidStudio;
import static com.intellij.ide.impl.NewProjectUtil.applyJdkToProject;
import static com.intellij.openapi.externalSystem.service.notification.NotificationSource.PROJECT_SYNC;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static java.util.Collections.sort;

/**
 * Service that sets an Android SDK and facets to the modules of a project that has been imported from an Android-Gradle project.
 */
public class AndroidGradleModelDataService extends AbstractProjectDataService<AndroidGradleModel, Void> {
  private static final Logger LOG = Logger.getInstance(AndroidGradleModelDataService.class);

  private final List<ModuleCustomizer<AndroidGradleModel>> myCustomizers;

  // This constructor is called by the IDE. See this module's plugin.xml file, implementation of extension 'externalProjectDataService'.
  @SuppressWarnings("unused")
  public AndroidGradleModelDataService() {
    this(ImmutableList.of(new AndroidSdkModuleCustomizer(), new AndroidFacetModuleCustomizer(), new ContentRootModuleCustomizer(),
                          new RunConfigModuleCustomizer(), new DependenciesModuleCustomizer(), new CompilerOutputModuleCustomizer()));
  }

  @VisibleForTesting
  AndroidGradleModelDataService(@NotNull List<ModuleCustomizer<AndroidGradleModel>> customizers) {
    myCustomizers = customizers;
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
                         @Nullable final ProjectData projectData,
                         @NotNull final Project project,
                         @NotNull final IdeModifiableModelsProvider modelsProvider) {
    if (!toImport.isEmpty()) {
      try {
        // disable 'Create separate module per source set' feature option, since android import doesn't support it
        if (projectData != null) {
          GradleProjectSettings gradleProjectSettings =
            GradleSettings.getInstance(project).getLinkedProjectSettings(projectData.getLinkedExternalProjectPath());
          if (gradleProjectSettings != null && gradleProjectSettings.isResolveModulePerSourceSet()) {
            gradleProjectSettings.setResolveModulePerSourceSet(false);
            NotificationData notificationData = new NotificationData("Gradle settings were updated",
                                                                     "'Create separate module per source set' feature was disabled. \n" +
                                                                     "It doesn't supported by android projects",
                                                                     NotificationCategory.WARNING, PROJECT_SYNC);
            ExternalSystemNotificationManager.getInstance(project).showNotification(GradleConstants.SYSTEM_ID, notificationData);
          }
        }
        doImport(toImport, project, modelsProvider);
      }
      catch (Throwable e) {
        LOG.error(String.format("Failed to set up Android modules in project '%1$s'", project.getName()), e);
        String msg = e.getMessage();
        if (msg == null) {
          msg = e.getClass().getCanonicalName();
        }
        GradleSyncState.getInstance(project).syncFailed(msg);
      }
    }
  }

  private void doImport(final Collection<DataNode<AndroidGradleModel>> toImport,
                        final Project project,
                        final IdeModifiableModelsProvider modelsProvider) throws Throwable {
    RunResult result = new WriteCommandAction.Simple(project) {
      @Override
      protected void run() throws Throwable {
        LanguageLevel javaLangVersion = null;

        ProjectSyncMessages messages = ProjectSyncMessages.getInstance(project);
        boolean hasExtraGeneratedFolders = false;

        Map<String, AndroidGradleModel> androidModelsByModuleName = indexByModuleName(toImport);

        Charset ideEncoding = EncodingProjectManager.getInstance(project).getDefaultCharset();
        GradleVersion oneDotTwoModelVersion = new GradleVersion(1, 2, 0);

        String nonMatchingModelEncodingFound = null;
        String modelVersionWithLayoutRenderingIssue = null;

        // Module name, build
        List<String> modulesUsingBuildTools23rc1 = Lists.newArrayList();

        for (Module module : modelsProvider.getModules()) {
          AndroidGradleModel androidModel = androidModelsByModuleName.get(module.getName());

          customizeModule(module, project, modelsProvider, androidModel);
          if (androidModel != null) {
            AndroidProject androidProject = androidModel.getAndroidProject();

            checkBuildToolsCompatibility(module, androidProject, modulesUsingBuildTools23rc1);

            // Verify that if Gradle is 2.4 (or newer,) the model is at least version 1.2.0.
            if (modelVersionWithLayoutRenderingIssue == null && hasLayoutRenderingIssue(androidProject)) {
              modelVersionWithLayoutRenderingIssue = androidProject.getModelVersion();
            }

            GradleVersion modelVersion = GradleVersion.parse(androidProject.getModelVersion());
            boolean isModelVersionOneDotTwoOrNewer = modelVersion.compareIgnoringQualifiers(oneDotTwoModelVersion) >= 0;

            // Verify that the encoding in the model is the same as the encoding in the IDE's project settings.
            Charset modelEncoding = null;
            if (isModelVersionOneDotTwoOrNewer) {
              try {
                modelEncoding = Charset.forName(androidProject.getJavaCompileOptions().getEncoding());
              }
              catch (UnsupportedCharsetException ignore) {
                // It's not going to happen.
              }
            }
            if (nonMatchingModelEncodingFound == null && modelEncoding != null && ideEncoding.compareTo(modelEncoding) != 0) {
              nonMatchingModelEncodingFound = modelEncoding.displayName();
            }

            // Get the Java language version from the model.
            if (javaLangVersion == null) {
              javaLangVersion = androidModel.getJavaLanguageLevel();
            }

            // Warn users that there are generated source folders at the wrong location.
            File[] sourceFolders = androidModel.getExtraGeneratedSourceFolders();
            if (sourceFolders.length > 0) {
              hasExtraGeneratedFolders = true;
            }
            for (File folder : sourceFolders) {
              // Have to add a word before the path, otherwise IDEA won't show it.
              String[] text = {"Folder " + folder.getPath()};
              messages.add(new Message(EXTRA_GENERATED_SOURCES, WARNING, text));
            }
          }
        }

        if (!modulesUsingBuildTools23rc1.isEmpty()) {
          reportBuildTools23rc1Usage(modulesUsingBuildTools23rc1, project);
        }

        if (nonMatchingModelEncodingFound != null) {
          setIdeEncodingAndAddEncodingMismatchMessage(nonMatchingModelEncodingFound, project);
        }

        if (modelVersionWithLayoutRenderingIssue != null) {
          addLayoutRenderingIssueMessage(modelVersionWithLayoutRenderingIssue, project);
        }

        if (hasExtraGeneratedFolders) {
          messages.add(new Message(EXTRA_GENERATED_SOURCES, INFO, "3rd-party Gradle plug-ins may be the cause"));
        }

        Sdk jdk = ProjectRootManager.getInstance(project).getProjectSdk();

        if (jdk == null || (!isAndroidStudio() && !isApplicableJdk(jdk, javaLangVersion))) {
          jdk = Jdks.chooseOrCreateJavaSdk(javaLangVersion);
        }
        if (jdk == null) {
          String title = String.format("Problems importing/refreshing Gradle project '%1$s':\n", project.getName());
          LanguageLevel level = javaLangVersion != null ? javaLangVersion : LanguageLevel.JDK_1_6;
          String msg = String.format("Unable to find a JDK %1$s installed.\n", level.getPresentableText());
          msg += "After configuring a suitable JDK in the \"Project Structure\" dialog, sync the Gradle project again.";
          NotificationData notification = new NotificationData(title, msg, NotificationCategory.ERROR, PROJECT_SYNC);
          ExternalSystemNotificationManager.getInstance(project).showNotification(GRADLE_SYSTEM_ID, notification);
        }
        else {
          String homePath = jdk.getHomePath();
          if (homePath != null) {
            applyJdkToProject(project, jdk);
            homePath = toSystemDependentName(homePath);
            IdeSdks.setJdkPath(new File(homePath));
            PostProjectBuildTasksExecutor.getInstance(project).updateJavaLangLevelAfterBuild();
          }
        }
      }
    }.execute();
    Throwable error = result.getThrowable();
    if (error != null) {
      throw error;
    }
  }

  // Build Tools 23 only works with Android plugin 1.3 or newer. Verify that the project is using compatible Build Tools/Android plugin
  // versions.
  private static void checkBuildToolsCompatibility(@NotNull Module module,
                                                   @NotNull AndroidProject project,
                                                   @NotNull List<String> moduleNames) {
    if (isOneDotThreeOrLater(project)) {
      return;
    }
    GradleBuildModel buildModel = GradleBuildModel.get(module);
    if (buildModel != null && "23.0.0 rc1".equals(buildModel.android().buildToolsVersion())) {
      moduleNames.add(module.getName());
    }
  }

  private static boolean isOneDotThreeOrLater(@NotNull AndroidProject project) {
    String modelVersion = project.getModelVersion();
    // getApiVersion doesn't work prior to 1.2, and API level must be at least 3
    return !(modelVersion.startsWith("1.0") || modelVersion.startsWith("1.1")) && project.getApiVersion() >= 3;
  }

  private static void reportBuildTools23rc1Usage(@NotNull List<String> moduleNames, @NotNull Project project) {
    if (!moduleNames.isEmpty()) {
      sort(moduleNames);

      StringBuilder msg = new StringBuilder();
      msg.append("Build Tools 23.0.0 rc1 is <b>deprecated</b>.<br>\n")
         .append("Please update these modules to use Build Tools 23.0.0 rc2 instead:");
      for (String moduleName : moduleNames) {
        msg.append("<br>\n * ").append(moduleName);
      }
      msg.append("<br>\n<br>\nOtherwise the project won't build. ");

      AndroidGradleNotification notification = AndroidGradleNotification.getInstance(project);
      notification.showBalloon("Android Build Tools", msg.toString(), NotificationType.ERROR);
    }
  }

  private static void setIdeEncodingAndAddEncodingMismatchMessage(@NotNull String newEncoding, @NotNull Project project) {
    EncodingProjectManager encodings = EncodingProjectManager.getInstance(project);
    String[] text = {String.format("The project encoding (%1$s) has been reset to the encoding specified in the Gradle build files (%2$s).",
                                   encodings.getDefaultCharset().displayName(), newEncoding),
      "Mismatching encodings can lead to serious bugs."};
    encodings.setDefaultCharsetName(newEncoding);
    NotificationHyperlink openDocHyperlink = new OpenUrlHyperlink("http://tools.android.com/knownissues/encoding", "More Info...");
    ProjectSyncMessages.getInstance(project).add(new Message(UNHANDLED_SYNC_ISSUE_TYPE, INFO, text), openDocHyperlink);
  }

  private static void addLayoutRenderingIssueMessage(String modelVersion, @NotNull Project project) {
    // See https://code.google.com/p/android/issues/detail?id=170841
    NotificationHyperlink quickFix = new FixAndroidGradlePluginVersionHyperlink(false);
    NotificationHyperlink openDocHyperlink =
      new OpenUrlHyperlink("https://code.google.com/p/android/issues/detail?id=170841", "More Info...");
    String[] text =
      {String.format("Using an obsolete version of the Gradle plugin (%1$s); this can lead to layouts not rendering correctly.",
                     modelVersion)};
    ProjectSyncMessages.getInstance(project).add(new Message(UNHANDLED_SYNC_ISSUE_TYPE, WARNING, text), openDocHyperlink, quickFix);
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

  private void customizeModule(@NotNull Module module,
                               @NotNull Project project,
                               @NotNull IdeModifiableModelsProvider modelsProvider,
                               @Nullable AndroidGradleModel androidModel) {
    for (ModuleCustomizer<AndroidGradleModel> customizer : myCustomizers) {
      customizer.customizeModule(project, module, modelsProvider, androidModel);
    }
  }
}
