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
package com.android.tools.idea.gradle.project.sync.ng;

import static com.android.tools.idea.gradle.project.sync.setup.post.PostSyncProjectSetup.createProjectSetupFromCacheTaskWithStartMessage;
import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static java.util.Arrays.asList;

import com.android.builder.model.AndroidProject;
import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.ide.common.gradle.model.level2.IdeDependenciesFactory;
import com.android.java.model.ArtifactModel;
import com.android.java.model.JavaProject;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.ProjectBuildFileChecksums;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.gradle.project.model.JavaModuleModelFactory;
import com.android.tools.idea.gradle.project.sync.GradleModuleModels;
import com.android.tools.idea.gradle.project.sync.GradleSync;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.PsdModuleModels;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.gradle.project.sync.ng.caching.CachedProjectModels;
import com.android.tools.idea.gradle.project.sync.setup.post.PostSyncProjectSetup;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFileManager;
import java.io.File;
import java.util.Collections;
import java.util.List;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.gradle.GradleScript;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NewGradleSync implements GradleSync {
  private static final Logger LOG = Logger.getInstance(NewGradleSync.class);
  @NotNull private final Project myProject;
  @NotNull private final GradleSyncMessages mySyncMessages;
  @NotNull private final SyncExecutor mySyncExecutor;
  @NotNull private final SyncResultHandler myResultHandler;
  @NotNull private final ProjectBuildFileChecksums.Loader myBuildFileChecksumsLoader;
  @NotNull private final CachedProjectModels.Loader myProjectModelsCacheLoader;
  @NotNull private final SyncExecutionCallback.Factory myCallbackFactory;
  public static final String NOT_ELIGIBLE_FOR_SINGLE_VARIANT_SYNC = "not.eligible.for.single.variant.sync";

  public static boolean isLevel4Model() {
    return StudioFlags.L4_DEPENDENCY_MODEL.get();
  }

  public static boolean isEnabled(@NotNull Project project) {
    return StudioFlags.NEW_SYNC_INFRA_ENABLED.get() || isSingleVariantSync(project);
  }

  public static boolean isSingleVariantSync(@NotNull Project project) {
    return StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.get() ||
           (GradleExperimentalSettings.getInstance().USE_SINGLE_VARIANT_SYNC &&
            !PropertiesComponent.getInstance(project).getBoolean(NOT_ELIGIBLE_FOR_SINGLE_VARIANT_SYNC));
  }

  public static boolean isCompoundSync(@NotNull Project project) {
    // Since Gradle plugin don't have the concept of selected variant and we don't want to generate sources for all variants, we only
    // activate Compound Sync if Single Variant Sync is also enabled.
    return StudioFlags.COMPOUND_SYNC_ENABLED.get() && isEnabled(project) && isSingleVariantSync(project);
  }

  public NewGradleSync(@NotNull Project project) {
    this(project, GradleSyncMessages.getInstance(project), new SyncExecutor(project), new SyncResultHandler(project),
         new ProjectBuildFileChecksums.Loader(), new CachedProjectModels.Loader(), new SyncExecutionCallback.Factory());
  }

  @VisibleForTesting
  NewGradleSync(@NotNull Project project,
                @NotNull GradleSyncMessages syncMessages,
                @NotNull SyncExecutor syncExecutor,
                @NotNull SyncResultHandler resultHandler,
                @NotNull ProjectBuildFileChecksums.Loader buildFileChecksumsLoader,
                @NotNull CachedProjectModels.Loader projectModelsCacheLoader,
                @NotNull SyncExecutionCallback.Factory callbackFactory) {
    myProject = project;
    mySyncMessages = syncMessages;
    mySyncExecutor = syncExecutor;
    myResultHandler = resultHandler;
    myBuildFileChecksumsLoader = buildFileChecksumsLoader;
    myProjectModelsCacheLoader = projectModelsCacheLoader;
    myCallbackFactory = callbackFactory;
  }

  @Override
  public void sync(@NotNull GradleSyncInvoker.Request request, @Nullable GradleSyncListener listener) {
    Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode()) {
      mySyncMessages.removeAllMessages();
      sync(request, new EmptyProgressIndicator(), listener);
      return;
    }
    Task task = createSyncTask(request, listener);
    application.invokeLater(() -> {
      // IDEA's own sync infrastructure removes all messages at the beginning of every sync: ExternalSystemUtil#refreshProject.
      mySyncMessages.removeAllMessages();
      task.queue();
    }, ModalityState.defaultModalityState());
  }

  @VisibleForTesting
  @NotNull
  Task createSyncTask(@NotNull GradleSyncInvoker.Request request, @Nullable GradleSyncListener listener) {
    String title = "Gradle Sync"; // TODO show Gradle feedback

    ProgressExecutionMode executionMode = request.getProgressExecutionMode();
    Task syncTask;
    switch (executionMode) {
      case MODAL_SYNC:
        syncTask = new Task.Modal(myProject, title, true) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            sync(request, indicator, listener);
          }
        };
        break;
      case IN_BACKGROUND_ASYNC:
        syncTask = new Task.Backgroundable(myProject, title, true) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            sync(request, indicator, listener);
          }
        };
        break;
      default:
        throw new IllegalArgumentException(executionMode + " is not a supported execution mode");
    }
    return syncTask;
  }

  private void sync(@NotNull GradleSyncInvoker.Request request, @NotNull ProgressIndicator indicator,
                    @Nullable GradleSyncListener syncListener) {
    PostSyncProjectSetup.Request setupRequest = createPostSyncRequest(request);

    if (trySyncWithCachedGradleModels(setupRequest, indicator, syncListener)) {
      return;
    }
    else {
      request.useCachedGradleModels = false;
      setupRequest.usingCachedGradleModels = false;
    }

    boolean isVariantOnlySync = request.variantOnlySyncOptions != null;
    boolean isCompoundSync = isCompoundSync(myProject) && request.generateSourcesOnSuccess;

    SyncExecutionCallback callback = myCallbackFactory.create();
    callback.doWhenRejected(() -> myResultHandler.onSyncFailed(callback, syncListener));

    if (isCompoundSync) {
      callback.doWhenDone(() -> myResultHandler.onCompoundSyncModels(callback, setupRequest, indicator, syncListener, isVariantOnlySync));
    }
    else if (isVariantOnlySync) {
      callback.doWhenDone(() -> myResultHandler.onVariantOnlySyncFinished(callback, setupRequest, indicator, syncListener));
    }
    else {
      callback.doWhenDone(() -> myResultHandler.onSyncFinished(callback, setupRequest, indicator, syncListener));
    }

    mySyncExecutor.syncProject(indicator, callback, request.variantOnlySyncOptions, syncListener, request, myResultHandler, isCompoundSync);
  }

  /**
   * Returns true if loading of cached models was successful, false otherwise
   */
  private boolean trySyncWithCachedGradleModels(@NotNull PostSyncProjectSetup.Request setupRequest, @NotNull ProgressIndicator indicator,
                                                @Nullable GradleSyncListener syncListener) {
    if (!setupRequest.usingCachedGradleModels) {
      return false;
    }
    try {
      // Use models from the disk cache.
      ProjectBuildFileChecksums buildFileChecksums = myBuildFileChecksumsLoader.loadFromDisk(myProject);

      if (buildFileChecksums == null || !buildFileChecksums.canUseCachedData()) {
        return false;
      }

      CachedProjectModels projectModelsCache = myProjectModelsCacheLoader.loadFromDisk(myProject);

      if (projectModelsCache == null) {
        return false;
      }

      // The library jar files are missing from disk, this will happen if Gradle cache is removed from disk.
      if (areCachedFilesMissing(myProject)) {
        Logger.getInstance(NewGradleSync.class).info("Cached library files are missing from disk. Performing a Gradle Sync.");
        return false;
      }

      setupRequest.generateSourcesAfterSync = true;
      setupRequest.lastSyncTimestamp = buildFileChecksums.getLastGradleSyncTimestamp();

      ExternalSystemTaskId taskId = createProjectSetupFromCacheTaskWithStartMessage(myProject);

      try {
        myResultHandler.onSyncSkipped(projectModelsCache, setupRequest, indicator, syncListener, taskId);
      }
      catch (Throwable e) {
        mySyncExecutor.generateFailureEvent(taskId);
        Logger.getInstance(NewGradleSync.class).warn("Restoring project state from cache failed. Performing a Gradle Sync.", e);
        return false;
      }
    }
    catch (Throwable ex) {
      LOG.error("Sync with cached Gradle models failed.", ex);
      return false;
    }
    return true;
  }

  /**
   * @return true if the expected jars from cached libraries don't exist on disk.
   */
  public static boolean areCachedFilesMissing(@NotNull Project project) {
    final Ref<Boolean> missingFileFound = Ref.create(false);
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
      rootManager.orderEntries().withoutModuleSourceEntries().withoutDepModules().forEach(entry -> {
        for (OrderRootType type : OrderRootType.getAllTypes()) {
          List<String> expectedUrls = asList(entry.getUrls(type));
          // CLASSES root contains jar file and res folder, and none of them are guaranteed to exist. Fail validation only if
          // all files are missing.
          if (type.equals(CLASSES)) {
            if (expectedUrls.stream().noneMatch(url -> VirtualFileManager.getInstance().findFileByUrl(url) != null)) {
              missingFileFound.set(true);
              return false; // Don't continue with processor.
            }
          }
          // For other types of root, fail validation if any file is missing. This includes annotation processor, sources and javadoc.
          else {
            if (expectedUrls.stream().anyMatch(url -> VirtualFileManager.getInstance().findFileByUrl(url) == null)) {
              missingFileFound.set(true);
              return false; // Don't continue with processor.
            }
          }
        }
        return true;
      });
      if (missingFileFound.get()) {
        return true;
      }
    }
    return missingFileFound.get();
  }

  private static PostSyncProjectSetup.Request createPostSyncRequest(@NotNull GradleSyncInvoker.Request request) {
    PostSyncProjectSetup.Request setupRequest = new PostSyncProjectSetup.Request();

    setupRequest.usingCachedGradleModels = request.useCachedGradleModels;
    setupRequest.generateSourcesAfterSync = request.generateSourcesOnSuccess;
    setupRequest.cleanProjectAfterSync = request.cleanProject;
    setSkipAndroidPluginUpgrade(request, setupRequest);

    return setupRequest;
  }

  private static void setSkipAndroidPluginUpgrade(@NotNull GradleSyncInvoker.Request syncRequest,
                                                  @NotNull PostSyncProjectSetup.Request setupRequest) {
    if (ApplicationManager.getApplication().isUnitTestMode() && syncRequest.skipAndroidPluginUpgrade) {
      setupRequest.skipAndroidPluginUpgrade = true;
    }
  }

  @Override
  @NotNull
  public List<GradleModuleModels> fetchGradleModels(@NotNull ProgressIndicator indicator) {
    List<SyncModuleModels> models = mySyncExecutor.fetchGradleModels(indicator);
    ImmutableList.Builder<GradleModuleModels> builder = ImmutableList.builder();

    IdeDependenciesFactory dependenciesFactory = new IdeDependenciesFactory();
    JavaModuleModelFactory javaModelFactory = new JavaModuleModelFactory();
    String emptyVariantName = "";

    for (SyncModuleModels moduleModels : models) {
      GradleProject gradleProject = moduleModels.findModel(GradleProject.class);
      if (gradleProject != null) {
        String name = moduleModels.getModuleName();
        PsdModuleModels newModels = new PsdModuleModels(name);
        builder.add(newModels);

        GradleScript buildScript = null;
        try {
          buildScript = gradleProject.getBuildScript();
        }
        catch (Throwable e) {
          // Ignored. We got here because the project is using Gradle 1.8 or older.
        }

        AndroidProject androidProject = moduleModels.findModel(AndroidProject.class);
        // Note: currently getModelVersion() matches the AGP version and it is the only way to get the AGP version.
        // Note: agpVersion is currently not available for Java modules.
        String agpVersion = androidProject != null ? androidProject.getModelVersion() : null;

        File buildFilePath = buildScript != null ? buildScript.getSourceFile() : null;
        GradleModuleModel gradleModel =
          new GradleModuleModel(name, gradleProject, Collections.emptyList(), buildFilePath, null, agpVersion);
        newModels.addModel(GradleModuleModel.class, gradleModel);

        File moduleRootPath = gradleProject.getProjectDirectory();

        if (androidProject != null) {
          AndroidModuleModel androidModel = new AndroidModuleModel(name, moduleRootPath, androidProject, emptyVariantName,
                                                                   dependenciesFactory);
          newModels.addModel(AndroidModuleModel.class, androidModel);
          continue;
        }

        JavaProject javaProject = moduleModels.findModel(JavaProject.class);
        if (javaProject != null) {
          JavaModuleModel javaModel = javaModelFactory.create(moduleRootPath, gradleProject, javaProject);
          newModels.addModel(JavaModuleModel.class, javaModel);
          continue;
        }

        ArtifactModel jarAarProject = moduleModels.findModel(ArtifactModel.class);
        if (!gradleProject.getPath().equals(":") && jarAarProject != null) {
          JavaModuleModel javaModel = javaModelFactory.create(moduleRootPath, gradleProject, jarAarProject);
          newModels.addModel(JavaModuleModel.class, javaModel);
        }
      }
    }
    return builder.build();
  }
}
