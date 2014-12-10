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
import com.android.ide.common.repository.SdkMavenRepository;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.sdklib.repository.local.LocalSdk;
import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.customizer.AbstractDependenciesModuleCustomizer;
import com.android.tools.idea.gradle.eclipse.ImportModule;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.facet.JavaGradleFacet;
import com.android.tools.idea.gradle.messages.AbstractNavigatable;
import com.android.tools.idea.gradle.messages.Message;
import com.android.tools.idea.gradle.messages.ProjectSyncMessages;
import com.android.tools.idea.gradle.parser.GradleSettingsFile;
import com.android.tools.idea.gradle.service.notification.hyperlink.InstallPlatformHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.OpenAndroidSdkManagerHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.OpenFileHyperlink;
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
import com.android.tools.idea.startup.ExternalAnnotationsSupport;
import com.android.tools.idea.templates.TemplateManager;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.jarFinder.InternetAttachSourceProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.module.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.io.URLUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.android.SdkConstants.FN_FRAMEWORK_LIBRARY;
import static com.android.tools.idea.gradle.messages.CommonMessageGroupNames.FAILED_TO_SET_UP_SDK;
import static com.android.tools.idea.gradle.service.notification.errors.AbstractSyncErrorHandler.FAILED_TO_SYNC_GRADLE_PROJECT_ERROR_GROUP_FORMAT;
import static com.android.tools.idea.gradle.util.Projects.hasErrors;
import static com.android.tools.idea.gradle.variant.conflict.ConflictResolution.solveSelectionConflicts;
import static com.android.tools.idea.gradle.variant.conflict.ConflictSet.findConflicts;
import static com.intellij.notification.NotificationType.INFORMATION;
import static org.jetbrains.android.sdk.AndroidSdkUtils.isAndroidSdk;

public class PostProjectSetupTasksExecutor {
  private static final String SOURCES_JAR_NAME_SUFFIX = "-sources.jar";

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
    ensureValidSdks();

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

  public void ensureValidSdks() {
    ProjectSyncMessages messages = ProjectSyncMessages.getInstance(myProject);

    boolean checkJdkVersion = true;

    Collection<Sdk> invalidAndroidSdks = Sets.newHashSet();

    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    for (Module module : moduleManager.getModules()) {
      AndroidFacet androidFacet = AndroidFacet.getInstance(module);
      if (androidFacet != null && androidFacet.getIdeaAndroidProject() != null) {
        Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
        if (sdk != null && !invalidAndroidSdks.contains(sdk) && isMissingAndroidLibrary(sdk)) {
          // First try to recreate SDK; workaround for issue 78072
          AndroidSdkAdditionalData additionalData = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
          AndroidSdkData sdkData = AndroidSdkData.getSdkData(sdk);
          if (additionalData != null && sdkData != null) {
            IAndroidTarget target = additionalData.getBuildTarget(sdkData);
            if (target == null) {
              LocalSdk localSdk = sdkData.getLocalSdk();
              localSdk.clearLocalPkg(EnumSet.of(PkgType.PKG_PLATFORM));
              target = localSdk.getTargetFromHashString(additionalData.getBuildTargetHashString());
            }
            if (target != null) {
              SdkModificator sdkModificator = sdk.getSdkModificator();
              sdkModificator.removeAllRoots();
              for (OrderRoot orderRoot : AndroidSdkUtils.getLibraryRootsForTarget(target, sdk.getHomePath(), true)) {
                sdkModificator.addRoot(orderRoot.getFile(), orderRoot.getType());
              }
              ExternalAnnotationsSupport.attachJdkAnnotations(sdkModificator);
              sdkModificator.commitChanges();
            }
          }

          // If attempting to fix up the roots in the SDK fails, install the target over again
          // (this is a truly corrupt install, as opposed to an incorrectly synced SDK which the
          // above workaround deals with)
          if (isMissingAndroidLibrary(sdk)) {
            invalidAndroidSdks.add(sdk);
          }
        }

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

    if (!invalidAndroidSdks.isEmpty()) {
      reinstallMissingPlatforms(invalidAndroidSdks);
    }
  }

  private static boolean isMissingAndroidLibrary(@NotNull Sdk sdk) {
    if (isAndroidSdk(sdk)) {
      for (VirtualFile library : sdk.getRootProvider().getFiles(OrderRootType.CLASSES)) {
        // This code does not through the classes in the Android SDK. It iterates through a list of 3 files in the IDEA SDK: android.jar,
        // annotations.jar and res folder.
        if (library.getName().equals(FN_FRAMEWORK_LIBRARY) && library.exists()) {
          return false;
        }
      }
    }
    return true;
  }

  private void reinstallMissingPlatforms(@NotNull Collection<Sdk> invalidAndroidSdks) {
    ProjectSyncMessages messages = ProjectSyncMessages.getInstance(myProject);

    List<AndroidVersion> versionsToInstall = Lists.newArrayList();
    List<String> missingPlatforms = Lists.newArrayList();

    for (Sdk sdk : invalidAndroidSdks) {
      SdkAdditionalData additionalData = sdk.getSdkAdditionalData();
      if (additionalData instanceof AndroidSdkAdditionalData) {
        String platform = ((AndroidSdkAdditionalData)additionalData).getBuildTargetHashString();
        if (platform != null) {
          missingPlatforms.add("'" + platform + "'");
          AndroidVersion version = AndroidTargetHash.getPlatformVersion(platform);
          if (version != null) {
            versionsToInstall.add(version);
          }
        }
      }
    }

    if (!versionsToInstall.isEmpty()) {
      String group = String.format(FAILED_TO_SYNC_GRADLE_PROJECT_ERROR_GROUP_FORMAT, myProject.getName());
      String text = "Missing Android platform(s) detected: " + Joiner.on(", ").join(missingPlatforms);
      Message msg = new Message(group, Message.Type.ERROR, text);
      messages.add(msg, new InstallPlatformHyperlink(versionsToInstall.toArray(new AndroidVersion[versionsToInstall.size()])));
    }
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
    ensureAllModulesHaveValidSdks();
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

    ensureValidSdks();

    TemplateManager.getInstance().refreshDynamicTemplateMenu(myProject);
  }

  private void ensureAllModulesHaveValidSdks() {
    Set<Sdk> androidSdks = Sets.newHashSet();

    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    for (Module module : moduleManager.getModules()) {
      ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      ModifiableRootModel model = moduleRootManager.getModifiableModel();

      Sdk sdk = model.getSdk();
      if (sdk != null) {
        if (isAndroidSdk(sdk)) {
          androidSdks.add(sdk);
        }
        model.dispose();
        continue;
      }
      try {
        Sdk jdk = DefaultSdks.getDefaultJdk();
        model.setSdk(jdk);
      }
      finally {
        model.commit();
      }
    }

    for (Sdk sdk: androidSdks) {
      refreshLibrariesIn(sdk);
    }
  }

  // After a sync, the contents of an IDEA SDK does not get refreshed. This is an issue when an IDEA SDK is corrupt (e.g. missing libraries
  // like android.jar) and then it is restored by installing the missing platform from within the IDE (using a "quick fix.") After the
  // automatic project sync (triggered by the SDK restore) the contents of the SDK are not refreshed, and references to Android classes are
  // not found in editors. Removing and adding the libraries effectively refreshes the contents of the IDEA SDK, and references in editors
  // work again.
  private static void refreshLibrariesIn(@NotNull Sdk sdk) {
    VirtualFile[] libraries = sdk.getRootProvider().getFiles(OrderRootType.CLASSES);

    SdkModificator sdkModificator = sdk.getSdkModificator();
    sdkModificator.removeRoots(OrderRootType.CLASSES);
    sdkModificator.commitChanges();

    sdkModificator = sdk.getSdkModificator();
    for (VirtualFile library : libraries) {
      sdkModificator.addRoot(library, OrderRootType.CLASSES);
    }
    sdkModificator.commitChanges();
  }

  private void attachSourcesToLibraries() {
    LibraryTable libraryTable = ProjectLibraryTable.getInstance(myProject);
    for (Library library : libraryTable.getLibraries()) {
      if (library.getFiles(OrderRootType.SOURCES).length > 0) {
        // has sources already.
        continue;
      }

      for (VirtualFile classFile : library.getFiles(OrderRootType.CLASSES)) {
        VirtualFile sourceJar = findSourceJarForJar(classFile);
        if (sourceJar != null) {
          Library.ModifiableModel model = library.getModifiableModel();
          try {
            String url = AbstractDependenciesModuleCustomizer.pathToUrl(sourceJar.getPath());
            model.addRoot(url, OrderRootType.SOURCES);
            break;
          }
          finally {
            model.commit();
          }
        }
      }
    }
  }

  @Nullable
  private static VirtualFile findSourceJarForJar(@NotNull VirtualFile jarFile) {
    // We need to get the real jar file. The one that we received is just a wrapper around a URL. Getting the parent from this file returns
    // null.
    File jarFilePath = getJarFromJarUrl(jarFile.getUrl());
    if (jarFilePath == null) {
      return null;
    }

    File sourceJarPath = getSourceJarForAndroidSupportAar(jarFilePath);
    if (sourceJarPath != null) {
      return VfsUtil.findFileByIoFile(sourceJarPath, true);
    }

    VirtualFile realJarFile = VfsUtil.findFileByIoFile(jarFilePath, true);

    if (realJarFile == null) {
      // Unlikely to happen. At this point the jar file should exist.
      return null;
    }

    VirtualFile parent = realJarFile.getParent();
    String sourceFileName = jarFile.getNameWithoutExtension() + SOURCES_JAR_NAME_SUFFIX;
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

  /**
   * Provides the path of the source jar for the libraries in the group "com.android.support" in the Android Support Maven repository (in
   * the Android SDK.)
   * <p>
   * Since AndroidProject (the Gradle model) does not provide the location of the source jar for aar libraries, we can deduce it from the
   * path of the "exploded aar".
   * </p>
   */
  @Nullable
  private static File getSourceJarForAndroidSupportAar(@NotNull File jarFilePath) {
    String path = jarFilePath.getPath();
    if (!path.contains(ImportModule.SUPPORT_GROUP_ID)) {
      return null;
    }

    int startingIndex = -1;
    List<String> pathSegments = FileUtil.splitPath(jarFilePath.getParentFile().getPath());
    int segmentCount = pathSegments.size();
    for (int i = 0; i < segmentCount; i++) {
      if (ResourceFolderManager.EXPLODED_AAR.equals(pathSegments.get(i))) {
        startingIndex = i + 1;
        break;
      }
    }

    if (startingIndex == -1 || startingIndex >= segmentCount) {
      return null;
    }

    List<String> sourceJarRelativePath = Lists.newArrayList();

    String groupId = pathSegments.get(startingIndex++);

    if (ImportModule.SUPPORT_GROUP_ID.equals(groupId)) {
      File androidHomePath = DefaultSdks.getDefaultAndroidHome();

      File repositoryLocation = SdkMavenRepository.ANDROID.getRepositoryLocation(androidHomePath, true);
      if (repositoryLocation != null) {
        sourceJarRelativePath.addAll(Splitter.on('.').splitToList(groupId));

        String artifactId = pathSegments.get(startingIndex++);
        sourceJarRelativePath.add(artifactId);

        String version = pathSegments.get(startingIndex);
        sourceJarRelativePath.add(version);

        String sourceJarName = artifactId + "-" + version + SOURCES_JAR_NAME_SUFFIX;
        sourceJarRelativePath.add(sourceJarName);
        File sourceJar = new File(repositoryLocation, FileUtil.join(ArrayUtil.toStringArray(sourceJarRelativePath)));
        return sourceJar.isFile() ? sourceJar : null;
      }
    }

    return null;
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
      InstallSdkToolsHyperlink hyperlink = new InstallSdkToolsHyperlink(VersionCheck.MIN_TOOLS_REV);
      String message = "Version " + VersionCheck.MIN_TOOLS_REV + " is available.";
      AndroidGradleNotification.getInstance(project).showBalloon("Android SDK Tools", message, INFORMATION, hyperlink);
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

  private static class InstallSdkToolsHyperlink extends NotificationHyperlink {
    @NotNull private final FullRevision myVersion;

    InstallSdkToolsHyperlink(@NotNull FullRevision version) {
      super("install.build.tools", "Install Tools " + version);
      myVersion = version;
    }

    @Override
    protected void execute(@NotNull Project project) {
      List<IPkgDesc> requested = Lists.newArrayList();
      if (myVersion.getMajor() == 23) {
        FullRevision minBuildToolsRev = new FullRevision(20, 0, 0);
        requested.add(PkgDesc.Builder.newPlatformTool(minBuildToolsRev).create());
      }
      requested.add(PkgDesc.Builder.newTool(myVersion, myVersion).create());
      SdkQuickfixWizard wizard = new SdkQuickfixWizard(project, null, requested);
      wizard.init();
      if (wizard.showAndGet()) {
        GradleProjectImporter.getInstance().requestProjectSync(project, null);
      }
    }
  }
}
