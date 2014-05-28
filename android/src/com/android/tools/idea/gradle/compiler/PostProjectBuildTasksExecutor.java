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
package com.android.tools.idea.gradle.compiler;

import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.output.GradleMessage;
import com.android.tools.idea.gradle.project.AndroidGradleNotification;
import com.android.tools.idea.gradle.project.BuildSettings;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.service.notification.CustomNotificationListener;
import com.android.tools.idea.gradle.service.notification.NotificationHyperlink;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.gradle.util.FilePaths;
import com.android.tools.idea.gradle.util.Projects;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.io.File;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import static com.android.builder.model.AndroidProject.FD_GENERATED;
import static com.android.tools.idea.gradle.customizer.android.ContentRootModuleCustomizer.EXCLUDED_OUTPUT_FOLDER_NAMES;
import static com.android.tools.idea.gradle.util.BuildMode.DEFAULT_BUILD_MODE;
import static com.android.tools.idea.gradle.util.BuildMode.SOURCE_GEN;
import static com.android.tools.idea.gradle.util.Projects.lastGradleSyncFailed;
import static com.intellij.util.ThreeState.YES;

/**
 * After a build is complete, this class will execute the following tasks:
 * <ul>
 * <li>Notify user that unresolved dependencies were detected in offline mode, and suggest to go <em>online</em></li>
 * <li>Refresh Studio's view of the file system (to see generated files)</li>
 * <li>Remove any build-related data stored in the project itself (e.g. modules to build, current "build mode", etc.)</li>
 * <li>Notify projects that source generation is finished (if applicable)</li>
 * </ul>
 * Both JPS and the "direct Gradle invocation" build strategies ares supported.
 */
public class PostProjectBuildTasksExecutor {
  private static final Key<Boolean> UPDATE_JAVA_LANG_LEVEL_AFTER_BUILD = Key.create("android.gradle.project.update.java.lang");

  @NotNull private final Project myProject;

  @NotNull
  public static PostProjectBuildTasksExecutor getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, PostProjectBuildTasksExecutor.class);
  }

  public PostProjectBuildTasksExecutor(@NotNull Project project) {
    myProject = project;
  }

  public void onBuildCompletion(@NotNull CompileContext context) {
    Iterator<String> errors = Iterators.emptyIterator();
    CompilerMessage[] errorMessages = context.getMessages(CompilerMessageCategory.ERROR);
    if (errorMessages.length > 0) {
      errors = new CompilerMessageIterator(errorMessages);
    }
    //noinspection TestOnlyProblems
    onBuildCompletion(errors, errorMessages.length);
  }

  private static class CompilerMessageIterator extends AbstractIterator<String> {
    @NotNull private final CompilerMessage[] myErrors;
    private int counter;

    CompilerMessageIterator(@NotNull CompilerMessage[] errors) {
      myErrors = errors;
    }

    @Override
    @Nullable
    protected String computeNext() {
      if (counter >= myErrors.length) {
        return endOfData();
      }
      return myErrors[counter++].getMessage();
    }
  }

  private static class GradleMessageIterator extends AbstractIterator<String> {
    private final Iterator<GradleMessage> myIterator;

    GradleMessageIterator(@NotNull Collection<GradleMessage> compilerMessages) {
      myIterator = compilerMessages.iterator();
    }

    @Override
    @Nullable
    protected String computeNext() {
      if (!myIterator.hasNext()) {
        return endOfData();
      }
      GradleMessage msg = myIterator.next();
      return msg != null ? msg.getText() : null;
    }
  }

  public void onBuildCompletion(@NotNull GradleInvocationResult result) {
    Iterator<String> errors = Iterators.emptyIterator();
    List<GradleMessage> errorMessages = result.getCompilerMessages(GradleMessage.Kind.ERROR);
    if (!errorMessages.isEmpty()) {
      errors = new GradleMessageIterator(errorMessages);
    }
    //noinspection TestOnlyProblems
    onBuildCompletion(errors, errorMessages.size());
  }

  @VisibleForTesting
  void onBuildCompletion(Iterator<String> errorMessages, int errorCount) {
    if (Projects.isGradleProject(myProject)) {
      UIUtil.invokeAndWaitIfNeeded(new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              excludeOutputFolders();
            }
          });
        }
      });

      if (Projects.isOfflineBuildModeEnabled(myProject)) {
        while (errorMessages.hasNext()) {
          String error = errorMessages.next();
          if (error != null && unresolvedDependenciesFound(error)) {
            notifyUnresolvedDependenciesInOfflineMode();
            break;
          }
        }
      }

      // Refresh Studio's view of the file system after a compile. This is necessary for Studio to see generated code.
      refreshProject();

      BuildSettings buildSettings = BuildSettings.getInstance(myProject);
      BuildMode buildMode = buildSettings.getBuildMode();
      buildSettings.removeAll();

      if (SOURCE_GEN.equals(buildMode)) {
        // Notify facets after project was synced. This only happens after importing a project.
        // Importing a project means:
        // * Creating a new project
        // * Importing an existing project
        // * Syncing with Gradle files
        // * Opening Studio with an already imported project
        notifyProjectSyncCompleted();
      }

      syncJavaLangLevel();

      // We automatically sync the model if:
      // 1. The project build is doing a MAKE, has zero errors and the previous Gradle sync failed. It is likely that if the
      //    project build is successful, Gradle sync will be successful too.
      // 2. If any build.gradle files or setting.gradle file was modified *after* last Gradle sync (we check file timestamps vs the
      //    timestamp of the last Gradle sync.) We don't perform this check if project build is SOURCE_GEN because, in this case,
      //    the project build was triggered by a Gradle sync (thus unlikely to have a stale model.) This sync is performed regardless the
      //    build was successful or not. If isGradleSyncNeeded returns UNSURE, the previous sync may have failed, if this happened
      //    an automatic sync should have been triggered already. No need to trigger a new one.
      if (DEFAULT_BUILD_MODE.equals(buildMode) && lastGradleSyncFailed(myProject) && errorCount == 0 ||
          !SOURCE_GEN.equals(buildMode) && GradleSyncState.getInstance(myProject).isSyncNeeded().equals(YES)) {
        GradleProjectImporter.getInstance().requestProjectSync(myProject, false /* do not generate sources */, null);
      }
    }
  }

  /**
   * Even though {@link com.android.tools.idea.gradle.customizer.android.ContentRootModuleCustomizer} already excluded the folders
   * "$buildDir/intermediates" and "$buildDir/outputs" we go through the children of "$buildDir" and exclude any non-generated folders
   * that may have been created by other plug-ins. We need to be aggressive when excluding folder to prevent over-indexing files, which
   * will degrade the IDE's performance.
   */
  private void excludeOutputFolders() {
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    if (myProject.isDisposed()) {
      return;
    }

    for (Module module : moduleManager.getModules()) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null && facet.isGradleProject()) {
        excludeOutputFolders(facet);
      }
    }
  }

  private static void excludeOutputFolders(@NotNull AndroidFacet facet) {
    IdeaAndroidProject androidProject = facet.getIdeaAndroidProject();
    if (androidProject == null) {
      return;
    }
    File buildFolderPath = androidProject.getDelegate().getBuildFolder();
    if (!buildFolderPath.isDirectory()) {
      return;
    }

    Module module = facet.getModule();
    if (module.getProject().isDisposed()) {
      return;
    }

    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    ModifiableRootModel rootModel = moduleRootManager.getModifiableModel();

    try {
      ContentEntry[] contentEntries = rootModel.getContentEntries();
      ContentEntry parent = findParentContentEntry(buildFolderPath, contentEntries);
      if (parent == null) {
        rootModel.dispose();
        return;
      }

      File[] outputFolderPaths = FileUtil.notNullize(buildFolderPath.listFiles());
      if (outputFolderPaths.length == 0) {
        rootModel.dispose();
        return;
      }

      for (File outputFolderPath : outputFolderPaths) {
        String name = outputFolderPath.getName();
        if (FD_GENERATED.equals(name) || EXCLUDED_OUTPUT_FOLDER_NAMES.contains(name)) {
          continue;
        }
        boolean alreadyExcluded = false;
        for (VirtualFile excluded : parent.getExcludeFolderFiles()) {
          if (FileUtil.filesEqual(outputFolderPath, VfsUtilCore.virtualToIoFile(excluded))) {
            alreadyExcluded = true;
            break;
          }
        }
        if (!alreadyExcluded) {
          parent.addExcludeFolder(FilePaths.pathToIdeaUrl(outputFolderPath));
        }
      }
    }
    finally {
      if (!rootModel.isDisposed()) {
        rootModel.commit();
      }
    }
  }

  @Nullable
  private static ContentEntry findParentContentEntry(@NotNull File path, @NotNull ContentEntry[] contentEntries) {
    for (ContentEntry contentEntry : contentEntries) {
      if (FilePaths.isPathInContentEntry(path, contentEntry)) {
        return contentEntry;
      }
    }
    return null;
  }

  private static boolean unresolvedDependenciesFound(@NotNull String errorMessage) {
    return errorMessage.contains("Could not resolve all dependencies");
  }

  private void notifyUnresolvedDependenciesInOfflineMode() {
    NotificationHyperlink disableOfflineModeHyperlink = new NotificationHyperlink("disable.gradle.offline.mode", "Disable offline mode") {
      @Override
      protected void execute(@NotNull Project project) {
        GradleSettings.getInstance(myProject).setOfflineWork(false);
      }
    };
    NotificationListener notificationListener = new CustomNotificationListener(myProject, disableOfflineModeHyperlink);

    String title = "Unresolved Dependencies";
    String msg = "Unresolved dependencies detected while building project in offline mode. Please disable offline mode and try again. " +
                 disableOfflineModeHyperlink.toString();

    AndroidGradleNotification.getInstance(myProject).showBalloon(title, msg, NotificationType.ERROR, notificationListener);
  }

  /**
   * Refreshes, asynchronously, the cached view of the project's contents.
   */
  private void refreshProject() {
    String projectPath = myProject.getBasePath();
    VirtualFile rootDir = LocalFileSystem.getInstance().findFileByPath(projectPath);
    if (rootDir != null && rootDir.isDirectory()) {
      rootDir.refresh(true, true);
    }
  }

  private void notifyProjectSyncCompleted() {
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    for (Module module : moduleManager.getModules()) {
      AndroidFacet androidFacet = AndroidFacet.getInstance(module);
      if (androidFacet != null) {
        androidFacet.projectSyncCompleted(true);
      }
    }
  }

  public void updateJavaLangLevelAfterBuild() {
    myProject.putUserData(UPDATE_JAVA_LANG_LEVEL_AFTER_BUILD, true);
  }

  private void syncJavaLangLevel() {
    Boolean updateJavaLangLevel = myProject.getUserData(UPDATE_JAVA_LANG_LEVEL_AFTER_BUILD);
    if (updateJavaLangLevel == null || !updateJavaLangLevel.booleanValue()) {
      return;
    }

    myProject.putUserData(UPDATE_JAVA_LANG_LEVEL_AFTER_BUILD, null);

    ExternalSystemApiUtil.executeProjectChangeAction(true, new DisposeAwareProjectChange(myProject) {
      @Override
      public void execute() {
        if (myProject.isOpen()) {
          //noinspection TestOnlyProblems
          LanguageLevel langLevel = getMaxJavaLangLevel();
          if (langLevel != null) {
            LanguageLevelProjectExtension ext = LanguageLevelProjectExtension.getInstance(myProject);
            if (langLevel != ext.getLanguageLevel()) {
              ext.setLanguageLevel(langLevel);
            }
          }
        }
      }
    });
  }

  @VisibleForTesting
  @Nullable
  LanguageLevel getMaxJavaLangLevel() {
    LanguageLevel maxLangLevel = null;

    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet == null) {
        continue;
      }
      IdeaAndroidProject androidProject = facet.getIdeaAndroidProject();
      if (androidProject != null) {
        LanguageLevel langLevel = androidProject.getJavaLanguageLevel();
        if (langLevel != null && (maxLangLevel == null || maxLangLevel.compareTo(langLevel) < 0)) {
          maxLangLevel = langLevel;
        }
      }
    }
    return maxLangLevel;
  }
}
