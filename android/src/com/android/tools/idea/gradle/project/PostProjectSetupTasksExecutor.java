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
import com.android.builder.model.AndroidProject;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.customizer.AbstractDependenciesModuleCustomizer;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.facet.JavaGradleFacet;
import com.android.tools.idea.gradle.messages.AbstractNavigatable;
import com.android.tools.idea.gradle.messages.Message;
import com.android.tools.idea.gradle.messages.ProjectSyncMessages;
import com.android.tools.idea.gradle.parser.GradleSettingsFile;
import com.android.tools.idea.gradle.service.notification.CustomNotificationListener;
import com.android.tools.idea.gradle.service.notification.NotificationHyperlink;
import com.android.tools.idea.gradle.service.notification.OpenAndroidSdkManagerHyperlink;
import com.android.tools.idea.gradle.service.notification.OpenFileHyperlink;
import com.android.tools.idea.gradle.structure.AndroidProjectSettingsService;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.gradle.util.ProjectBuilder;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.gradle.variant.conflict.Conflict;
import com.android.tools.idea.gradle.variant.conflict.ConflictSet;
import com.android.tools.idea.gradle.variant.profiles.ProjectProfileSelectionDialog;
import com.android.tools.idea.rendering.ProjectResourceRepository;
import com.android.tools.idea.sdk.DefaultSdks;
import com.android.tools.idea.sdk.Jdks;
import com.android.tools.idea.sdk.VersionCheck;
import com.android.tools.idea.sdk.wizard.SdkQuickfixWizard;
import com.android.tools.idea.startup.AndroidStudioSpecificInitializer;
import com.android.tools.idea.templates.TemplateManager;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.jarFinder.InternetAttachSourceProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.module.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.SystemProperties;
import com.intellij.util.io.URLUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.util.Collection;
import java.util.List;

import static com.android.tools.idea.gradle.messages.CommonMessageGroupNames.FAILED_TO_SET_UP_SDK;
import static com.android.tools.idea.gradle.util.Projects.hasErrors;
import static com.android.tools.idea.gradle.variant.conflict.ConflictResolution.solveSelectionConflicts;
import static com.android.tools.idea.gradle.variant.conflict.ConflictSet.findConflicts;
import static com.intellij.notification.NotificationType.INFORMATION;

public class PostProjectSetupTasksExecutor {
  private static boolean ourSdkVersionWarningShown;

  @NotNull private final Project myProject;

  private static final boolean DEFAULT_GENERATE_SOURCES_AFTER_SYNC = true;
  private volatile boolean myGenerateSourcesAfterSync = DEFAULT_GENERATE_SOURCES_AFTER_SYNC;

  @NotNull
  public static PostProjectSetupTasksExecutor getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, PostProjectSetupTasksExecutor.class);
  }

  public PostProjectSetupTasksExecutor(@NotNull Project project) {
    myProject = project;
  }


  public void onProjectRestoreFromDisk() {
    ProjectSyncMessages messages = ProjectSyncMessages.getInstance(myProject);

    boolean checkJdkVersion = true;

    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    for (Module module : moduleManager.getModules()) {
      AndroidFacet androidFacet = AndroidFacet.getInstance(module);
      if (androidFacet != null && androidFacet.getIdeaAndroidProject() != null) {
        IdeaAndroidProject androidProject = androidFacet.getIdeaAndroidProject();
        Collection<String> unresolved = androidProject.getDelegate().getUnresolvedDependencies();
        messages.reportUnresolvedDependencies(unresolved, module);
        if (checkJdkVersion && !hasCorrectJdkVersion(module, androidProject)) {
          // we already displayed the error, no need to check each module.
          checkJdkVersion = false;
        }
        continue;
      }
      JavaGradleFacet javaFacet = JavaGradleFacet.getInstance(module);
      if (javaFacet != null && javaFacet.getJavaModel() != null) {
        List<String> unresolved = javaFacet.getJavaModel().getUnresolvedDependencyNames();
        messages.reportUnresolvedDependencies(unresolved, module);
      }
    }

    if (hasErrors(myProject)) {
      addSdkLinkIfNecessary();
      checkSdkVersion(myProject);
      return;
    }

    findAndShowVariantConflicts();
    addSdkLinkIfNecessary();
    checkSdkVersion(myProject);

    TemplateManager.getInstance().refreshDynamicTemplateMenu(myProject);
  }

  public void onProjectSyncCompletion() {
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    for (Module module : moduleManager.getModules()) {
      if (!hasCorrectJdkVersion(module)) {
        // we already displayed the error, no need to check each module.
        break;
      }
    }

    if (hasErrors(myProject)) {
      addSdkLinkIfNecessary();
      checkSdkVersion(myProject);
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

    findAndShowVariantConflicts();
    checkSdkVersion(myProject);
    addSdkLinkIfNecessary();

    ProjectResourceRepository.moduleRootsChanged(myProject);

    GradleSyncState.getInstance(myProject).syncEnded();

    if (myGenerateSourcesAfterSync) {
      ProjectBuilder.getInstance(myProject).generateSourcesOnly();
    }
    else {
      // set default value back.
      myGenerateSourcesAfterSync = DEFAULT_GENERATE_SOURCES_AFTER_SYNC;
    }

    TemplateManager.getInstance().refreshDynamicTemplateMenu(myProject);
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

  private void findAndShowVariantConflicts() {
    ConflictSet conflicts = findConflicts(myProject);

    List<Conflict> structureConflicts = conflicts.getStructureConflicts();
    if (!structureConflicts.isEmpty() && SystemProperties.getBooleanProperty("enable.project.profiles", false)) {
      ProjectProfileSelectionDialog dialog = new ProjectProfileSelectionDialog(myProject, structureConflicts);
      dialog.show();
    }

    List<Conflict> selectionConflicts = conflicts.getSelectionConflicts();
    if (!selectionConflicts.isEmpty()) {
      boolean atLeastOneSolved = solveSelectionConflicts(selectionConflicts);
      if (atLeastOneSolved) {
        conflicts = findConflicts(myProject);
      }
    }
    conflicts.showSelectionConflicts();
  }

  private void addSdkLinkIfNecessary() {
    ProjectSyncMessages messages = ProjectSyncMessages.getInstance(myProject);

    int sdkErrorCount = messages.getMessageCount(FAILED_TO_SET_UP_SDK);
    if (sdkErrorCount > 0) {
      // If we have errors due to platforms not being installed, we add an extra message that prompts user to open Android SDK manager and
      // install any missing platforms.
      String text = "Open Android SDK Manager and install all missing platforms.";
      Message hint = new Message(FAILED_TO_SET_UP_SDK, Message.Type.INFO, AbstractNavigatable.NOT_NAVIGATABLE, text);
      messages.add(hint, new OpenAndroidSdkManagerHyperlink());
    }
  }

  private static void checkSdkVersion(@NotNull Project project) {
    if (project.isDisposed() || ourSdkVersionWarningShown) {
      return;
    }
    File androidHome = DefaultSdks.getDefaultAndroidHome();
    if (androidHome != null && !VersionCheck.isCompatibleVersion(androidHome)) {
      InstallBuildToolsHyperlink hyperlink = new InstallBuildToolsHyperlink(VersionCheck.MIN_TOOLS_REV);
      CustomNotificationListener listener = new CustomNotificationListener(project, hyperlink);

      AndroidGradleNotification notification = AndroidGradleNotification.getInstance(project);

      String message = "Version " + VersionCheck.MIN_TOOLS_REV + " is available." + "<br>\n" + hyperlink.toString();
      notification.showBalloon("Android SDK Tools", message, INFORMATION, listener);
      ourSdkVersionWarningShown = true;
    }
  }

  private boolean hasCorrectJdkVersion(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null && facet.getIdeaAndroidProject() != null) {
      return hasCorrectJdkVersion(module, facet.getIdeaAndroidProject());
    }
    return true;
  }

  private boolean hasCorrectJdkVersion(@NotNull Module module, @NotNull IdeaAndroidProject model) {
    AndroidProject androidProject = model.getDelegate();
    String compileTarget = androidProject.getCompileTarget();
    // TODO this is good for now, adjust this in the future to deal with 22, 23, etc.
    if ("android-L".equals(compileTarget) || "android-21".equals(compileTarget)) {
      Sdk jdk = DefaultSdks.getDefaultJdk();
      if (jdk != null && !Jdks.isApplicableJdk(jdk, LanguageLevel.JDK_1_7)) {
        List<NotificationHyperlink> hyperlinks = Lists.newArrayList();
        ProjectSettingsService service = ProjectSettingsService.getInstance(myProject);
        if (service instanceof AndroidProjectSettingsService) {
          hyperlinks.add(new OpenSdkSettingsHyperlink((AndroidProjectSettingsService)service));
        }
        Message msg;
        String text = "compileSdkVersion " + compileTarget + " requires compiling with JDK 7";
        VirtualFile buildFile = GradleUtil.getGradleBuildFile(module);
        if (buildFile != null) {
          hyperlinks.add(new OpenFileHyperlink(buildFile.getPath()));
          msg = new Message(myProject, "Project Configuration", Message.Type.ERROR, buildFile, -1, -1, text);
        }
        else {
          msg = new Message("Project Configuration", Message.Type.ERROR, AbstractNavigatable.NOT_NAVIGATABLE, text);
        }

        ProjectSyncMessages messages = ProjectSyncMessages.getInstance(myProject);
        messages.add(msg, hyperlinks.toArray(new NotificationHyperlink[hyperlinks.size()]));

        myProject.putUserData(Projects.HAS_WRONG_JDK, true);
        return false;
      }
    }
    return true;
  }

  public void setGenerateSourcesAfterSync(boolean generateSourcesAfterSync) {
    myGenerateSourcesAfterSync = generateSourcesAfterSync;
  }

  private static class OpenSdkSettingsHyperlink extends NotificationHyperlink {
    @NotNull private final AndroidProjectSettingsService mySettingsService;

    OpenSdkSettingsHyperlink(@NotNull AndroidProjectSettingsService settingsService) {
      super("open.sdk.settings", "Open SDK Settings");
      mySettingsService = settingsService;
    }

    @Override
    protected void execute(@NotNull Project project) {
      mySettingsService.openSdkSettings();
    }
  }

  private static class InstallBuildToolsHyperlink extends NotificationHyperlink {
    @NotNull private final FullRevision myVersion;

    InstallBuildToolsHyperlink(@NotNull FullRevision version) {
      super("install.build.tools", "Install Tools " + version);
      myVersion = version;
    }

    @Override
    protected void execute(@NotNull Project project) {
      List<IPkgDesc> requested = Lists.newArrayList();
      requested.add(PkgDesc.Builder.newTool(myVersion, myVersion).create());
      SdkQuickfixWizard wizard = new SdkQuickfixWizard(project, null, requested);
      wizard.init();
      if (wizard.showAndGet()) {
        GradleProjectImporter.getInstance().requestProjectSync(project, null);
      }
    }
  }
}
