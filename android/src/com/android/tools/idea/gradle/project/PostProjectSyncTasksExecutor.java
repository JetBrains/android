/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.SdkConstants;
import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.customizer.AbstractDependenciesModuleCustomizer;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.messages.Message;
import com.android.tools.idea.gradle.messages.ProjectSyncMessages;
import com.android.tools.idea.gradle.messages.navigatable.OpenAndroidSdkNavigatable;
import com.android.tools.idea.gradle.parser.GradleSettingsFile;
import com.android.tools.idea.gradle.service.notification.CustomNotificationListener;
import com.android.tools.idea.gradle.service.notification.NotificationHyperlink;
import com.android.tools.idea.gradle.util.ProjectBuilder;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.gradle.variant.VariantSelectionVerifier;
import com.android.tools.idea.rendering.ProjectResourceRepository;
import com.android.tools.idea.sdk.DefaultSdks;
import com.android.tools.idea.startup.AndroidStudioSpecificInitializer;
import com.android.tools.idea.stats.StatsTimeCollector;
import com.android.tools.idea.stats.StatsKeys;
import com.android.tools.idea.templates.TemplateManager;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.jarFinder.InternetAttachSourceProvider;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.module.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.util.List;

import static com.android.tools.idea.gradle.messages.CommonMessageGroupNames.FAILED_TO_SET_UP_SDK;

public class PostProjectSyncTasksExecutor {
  @NotNull private final Project myProject;

  private static final boolean DEFAULT_GENERATE_SOURCES_AFTER_SYNC = true;
  private volatile boolean myGenerateSourcesAfterSync = DEFAULT_GENERATE_SOURCES_AFTER_SYNC;

  @NotNull
  public static PostProjectSyncTasksExecutor getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, PostProjectSyncTasksExecutor.class);
  }

  public PostProjectSyncTasksExecutor(@NotNull Project project) {
    myProject = project;
  }

  public void onProjectSetupCompletion() {
    if (ProjectSyncMessages.getInstance(myProject).getErrorCount() > 0) {
      displayProjectSetupMessages();
      GradleSyncState.getInstance(myProject).syncEnded();
      return;
    }

    attachSourcesToLibraries();
    ensureAllModulesHaveSdk();
    Projects.enforceExternalBuild(myProject);

    if (AndroidStudioSpecificInitializer.isAndroidStudio()) {
      // We remove modules not present in settings.gradle in Android Studio only. IDEA allows to have non-Gradle modules in Gradle projects.
      removeModulesNotInGradleSettingsFile();
    }
    else {
      AndroidGradleProjectComponent.getInstance(myProject).checkForSupportedModules();
    }

    findAndShowVariantSelectionConflicts();

    ProjectResourceRepository.moduleRootsChanged(myProject);

    GradleSyncState.getInstance(myProject).syncEnded();

    if (myGenerateSourcesAfterSync) {
      ProjectBuilder.getInstance(myProject).generateSourcesOnly();
    } else {
      // set default value back.
      myGenerateSourcesAfterSync = DEFAULT_GENERATE_SOURCES_AFTER_SYNC;
    }

    TemplateManager.getInstance().refreshDynamicTemplateMenu();

    StatsTimeCollector.stop(StatsKeys.GRADLE_SYNC_TIME);
  }

  private void ensureAllModulesHaveSdk() {
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    for (Module module : moduleManager.getModules()) {
      ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      ModifiableRootModel model = moduleRootManager.getModifiableModel();
      try {
        if (model.getSdk() == null) {
          Sdk jdk = DefaultSdks.getDefaultJdk();
          model.setSdk(jdk);
        }
      }
      finally {
        model.commit();
      }
    }
  }

  private void attachSourcesToLibraries() {
    LibraryTable libraryTable = ProjectLibraryTable.getInstance(myProject);
    for (Library library : libraryTable.getLibraries()) {
      if (library.getFiles(OrderRootType.SOURCES).length > 0) {
        // has sources already.
        continue;
      }

      for (VirtualFile classFile : library.getFiles(OrderRootType.CLASSES)) {
        if (!SdkConstants.EXT_JAR.equals(classFile.getExtension())) {
          // we only attach sources to jar files for now.
          continue;
        }
        VirtualFile sourceJar = findSourceJarFor(classFile);
        if (sourceJar != null) {
          Library.ModifiableModel model = library.getModifiableModel();
          try {
            String url = AbstractDependenciesModuleCustomizer.pathToUrl(sourceJar.getPath());
            model.addRoot(url, OrderRootType.SOURCES);
          }
          finally {
            model.commit();
          }
        }
      }
    }
  }

  @Nullable
  private static VirtualFile findSourceJarFor(@NotNull VirtualFile jarFile) {
    String sourceFileName = jarFile.getNameWithoutExtension() + "-sources.jar";

    // We need to get the real jar file. The one that we received is just a wrapper around a URL. Getting the parent from this file returns
    // null.
    File jarFilePath = getJarFromJarUrl(jarFile.getUrl());
    if (jarFilePath == null) {
      return null;
    }
    VirtualFile realJarFile = VfsUtil.findFileByIoFile(jarFilePath, true);

    if (realJarFile == null) {
      // Unlikely to happen. At this point the jar file should exist.
      return null;
    }

    VirtualFile parent = realJarFile.getParent();
    if (parent != null) {
      // Try finding sources in the same folder as the jar file. This is the layout of Maven repositories.
      VirtualFile sourceJar = parent.findChild(sourceFileName);
      if (sourceJar != null) {
        return sourceJar;
      }

      // Try the parent's parent. This is the layout of the repository cache in .gradle folder.
      parent = parent.getParent();
      if (parent != null) {
        for (VirtualFile child : parent.getChildren()) {
          if (!child.isDirectory()) {
            continue;
          }
          sourceJar = child.findChild(sourceFileName);
          if (sourceJar != null) {
            return sourceJar;
          }
        }
      }
    }

    // Try IDEA's own cache.
    File librarySourceDirPath = InternetAttachSourceProvider.getLibrarySourceDir();
    File sourceJar = new File(librarySourceDirPath, sourceFileName);
    return VfsUtil.findFileByIoFile(sourceJar, true);
  }

  @Nullable
  private static File getJarFromJarUrl(@NotNull String url) {
    // URLs for jar file start with "jar://" and end with "!/".
    if (!url.startsWith(StandardFileSystems.JAR_PROTOCOL_PREFIX)) {
      return null;
    }
    String path = url.substring(StandardFileSystems.JAR_PROTOCOL_PREFIX.length());
    int index = path.lastIndexOf(URLUtil.JAR_SEPARATOR);
    if (index != -1) {
      path = path.substring(0, index);
    }
    return new File(FileUtil.toSystemDependentName(path));
  }


  private void removeModulesNotInGradleSettingsFile() {
    GradleSettingsFile gradleSettingsFile = GradleSettingsFile.get(myProject);
    final List<Module> modulesToRemove = Lists.newArrayList();

    final ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    Module[] modules = moduleManager.getModules();

    if (gradleSettingsFile == null) {
      // If there is no settings.gradle file, it means that the top-level module is the only module recognized by Gradle.
      if (modules.length == 1) {
        return;
      }
      boolean topLevelModuleFound = false;
      for (Module module : modules) {
        if (!topLevelModuleFound && isTopLevel(module)) {
          topLevelModuleFound = true;
        }
        else {
          modulesToRemove.add(module);
        }
      }
    }
    else {
      for (Module module : modules) {
        if (isNonGradleModule(module) || isOrphanGradleModule(module, gradleSettingsFile)) {
          modulesToRemove.add(module);
        }
      }
    }

    if (!modulesToRemove.isEmpty()) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          ModifiableModuleModel moduleModel = moduleManager.getModifiableModel();
          try {
            for (Module module : modulesToRemove) {
              removeDependencyLinks(module, moduleManager);
              moduleModel.disposeModule(module);
            }
          }
          finally {
            moduleModel.commit();
          }
        }
      });
    }
  }

  private static boolean isNonGradleModule(@NotNull Module module) {
    ModuleType moduleType = ModuleType.get(module);
    if (moduleType instanceof JavaModuleType) {
      String externalSystemId = module.getOptionValue(ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY);
      return !GradleConstants.SYSTEM_ID.getId().equals(externalSystemId);
    }
    return false;
  }

  private static boolean isOrphanGradleModule(@NotNull Module module, @NotNull GradleSettingsFile settingsFile) {
    if (isTopLevel(module)) {
      return false;
    }
    AndroidGradleFacet facet = AndroidGradleFacet.getInstance(module);
    if (facet == null) {
      return true;
    }
    String gradleProjectPath = facet.getConfiguration().GRADLE_PROJECT_PATH;
    Iterable<String> allModules = settingsFile.getModules();
    return !Iterables.contains(allModules, gradleProjectPath);
  }

  private static boolean isTopLevel(@NotNull Module module) {
    AndroidGradleFacet facet = AndroidGradleFacet.getInstance(module);
    if (facet == null) {
      // if this is the top-level module, it may not have the Gradle facet but it is still valid, because it represents the project.
      String moduleRootDirPath = new File(FileUtil.toSystemDependentName(module.getModuleFilePath())).getParent();
      return moduleRootDirPath.equals(module.getProject().getBasePath());
    }
    String gradleProjectPath = facet.getConfiguration().GRADLE_PROJECT_PATH;
    // top-level modules have Gradle path ":"
    return SdkConstants.GRADLE_PATH_SEPARATOR.equals(gradleProjectPath);
  }

  private static void removeDependencyLinks(@NotNull Module module, @NotNull ModuleManager moduleManager) {
    List<Module> dependents = moduleManager.getModuleDependentModules(module);
    for (Module dependent : dependents) {
      if (dependent.isDisposed()) {
        continue;
      }
      ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(dependent);
      ModifiableRootModel modifiableModel = moduleRootManager.getModifiableModel();
      try {
        for (OrderEntry orderEntry : modifiableModel.getOrderEntries()) {
          if (orderEntry instanceof ModuleOrderEntry) {
            Module orderEntryModule = ((ModuleOrderEntry)orderEntry).getModule();
            if (module.equals(orderEntryModule)) {
              modifiableModel.removeOrderEntry(orderEntry);
            }
          }
        }
      }
      finally {
        modifiableModel.commit();
      }
    }
  }

  private void findAndShowVariantSelectionConflicts() {
    VariantSelectionVerifier.getInstance(myProject).findAndShowSelectionConflicts();
    ProjectSyncMessages messages = ProjectSyncMessages.getInstance(myProject);
    if (!messages.isEmpty()) {
      displayProjectSetupMessages();
    }
  }

  private void displayProjectSetupMessages() {
    final ProjectSyncMessages messages = ProjectSyncMessages.getInstance(myProject);

    int sdkErrorCount = messages.getMessageCount(FAILED_TO_SET_UP_SDK);
    if (sdkErrorCount > 0) {
      // If we have errors due to platforms not being installed, we add an extra message that prompts user to open Android SDK manager and
      // install any missing platforms.
      Navigatable quickFix = new OpenAndroidSdkNavigatable(myProject);
      String text = "Double-click here to open Android SDK Manager and install all missing platforms.";
      Message quickFixMsg = new Message(FAILED_TO_SET_UP_SDK, Message.Type.INFO, quickFix, text);
      messages.add(quickFixMsg);
    }

    // Now we only show one balloon, telling user that errors can be found in the "Messages" window.
    String title = String.format("Failed to set up project '%1$s':\n", myProject.getName());
    NotificationHyperlink hyperlink = new NotificationHyperlink("open.messages.view", "Open Messages Window") {
      @Override
      protected void execute(@NotNull Project project) {
        messages.activateView();
      }
    };
    String text = String.format("You can find all errors in the 'Messages' window, under the '%1$s' tab.\n",
                                ExternalSystemNotificationManager.getContentDisplayName(NotificationSource.PROJECT_SYNC, GradleConstants.SYSTEM_ID));
    text+= hyperlink.toString();
    NotificationListener listener = new CustomNotificationListener(myProject, hyperlink);
    AndroidGradleNotification.getInstance(myProject).showBalloon(title, text, NotificationType.ERROR, listener);
  }

  public void setGenerateSourcesAfterSync(boolean generateSourcesAfterSync) {
    myGenerateSourcesAfterSync = generateSourcesAfterSync;
  }
}
