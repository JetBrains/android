/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.android.sdk;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.IAndroidTarget.OptionalLibrary;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.ddms.adb.AdbService;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.SelectSdkDialog;
import com.android.tools.idea.sdk.VersionCheck;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.CommonBundle;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.OSProcessManager;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.android.actions.AndroidEnableAdbServiceAction;
import org.jetbrains.android.actions.AndroidRunDdmsAction;
import org.jetbrains.android.actions.RunAndroidSdkManagerAction;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.android.SdkConstants.*;
import static com.android.sdklib.IAndroidTarget.RESOURCES;
import static com.android.tools.idea.sdk.Jdks.chooseOrCreateJavaSdk;
import static com.android.tools.idea.sdk.Jdks.createJdk;
import static com.android.tools.idea.startup.AndroidStudioInitializer.isAndroidStudio;
import static com.android.tools.idea.startup.ExternalAnnotationsSupport.attachJdkAnnotations;
import static com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil.createUniqueSdkName;
import static com.intellij.openapi.roots.ModuleRootModificationUtil.setModuleSdk;
import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static com.intellij.openapi.roots.OrderRootType.SOURCES;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.openapi.vfs.JarFileSystem.JAR_SEPARATOR;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static org.jetbrains.android.actions.AndroidEnableAdbServiceAction.setAdbServiceEnabled;
import static org.jetbrains.android.facet.AndroidRootUtil.getProjectPropertyValue;
import static org.jetbrains.android.facet.AndroidRootUtil.getPropertyValue;
import static org.jetbrains.android.sdk.AndroidSdkData.getSdkData;
import static org.jetbrains.android.sdk.AndroidSdkType.DEFAULT_EXTERNAL_DOCUMENTATION_URL;
import static org.jetbrains.android.sdk.AndroidSdkType.SDK_NAME;
import static org.jetbrains.android.util.AndroidCommonUtils.ANNOTATIONS_JAR_RELATIVE_PATH;
import static org.jetbrains.android.util.AndroidCommonUtils.platformToolPath;
import static org.jetbrains.android.util.AndroidUtils.ANDROID_TARGET_PROPERTY;

/**
 * @author Eugene.Kudelevsky
 */
public final class AndroidSdkUtils {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.sdk.AndroidSdkUtils");

  public static final String DEFAULT_PLATFORM_NAME_PROPERTY = "AndroidPlatformName";
  public static final String SDK_NAME_PREFIX = "Android ";
  public static final String DEFAULT_JDK_NAME = "JDK";

  private static AndroidSdkData ourSdkData;

  private AndroidSdkUtils() {
  }

  @NotNull
  public static List<VirtualFile> getPlatformAndAddOnJars(@NotNull IAndroidTarget target) {
    VirtualFile platformDir = getPlatformDir(target);
    if (platformDir == null) {
      return Collections.emptyList();
    }

    VirtualFile androidJar = platformDir.findChild(FN_FRAMEWORK_LIBRARY);
    if (androidJar == null) {
      return Collections.emptyList();
    }

    List<VirtualFile> result = Lists.newArrayList();

    VirtualFile androidJarRoot = findFileInJarFileSystem(androidJar.getPath());
    if (androidJarRoot != null) {
      result.add(androidJarRoot);
    }

    List<OptionalLibrary> libs = target.getAdditionalLibraries();
    for (OptionalLibrary lib : libs) {
      VirtualFile libRoot = findFileInJarFileSystem(lib.getJar().getAbsolutePath());
      if (libRoot != null) {
        result.add(libRoot);
      }
    }
    return result;
  }

  @NotNull
  public static List<OrderRoot> getLibraryRootsForTarget(@NotNull IAndroidTarget target,
                                                         @Nullable String sdkPath,
                                                         boolean addPlatformAndAddOnJars) {
    List<OrderRoot> result = Lists.newArrayList();

    if (addPlatformAndAddOnJars) {
      for (VirtualFile file : getPlatformAndAddOnJars(target)) {
        result.add(new OrderRoot(file, CLASSES));
      }
    }
    VirtualFile platformDir = getPlatformDir(target);
    if (platformDir == null) return result;

    VirtualFile targetDir = platformDir;
    if (!target.isPlatform()) {
      targetDir = findFileInLocalFileSystem(target.getLocation());
    }
    boolean docsOrSourcesFound = false;

    if (targetDir != null) {
      docsOrSourcesFound = addJavaDocAndSources(result, targetDir);
    }
    VirtualFile sdkDir = sdkPath != null ? findFileInLocalFileSystem(sdkPath) : null;
    VirtualFile sourcesDir = null;
    if (sdkDir != null) {
      docsOrSourcesFound = addJavaDocAndSources(result, sdkDir) || docsOrSourcesFound;
      sourcesDir = sdkDir.findChild(FD_PKG_SOURCES);
    }

    // todo: replace it by target.getPath(SOURCES) when it'll be up to date
    if (sourcesDir != null && sourcesDir.isDirectory()) {
      VirtualFile platformSourcesDir = sourcesDir.findChild(platformDir.getName());
      if (platformSourcesDir != null && platformSourcesDir.isDirectory()) {
        result.add(new OrderRoot(platformSourcesDir, SOURCES));
        docsOrSourcesFound = true;
      }
    }

    if (!docsOrSourcesFound) {
      VirtualFile javadoc = VirtualFileManager.getInstance().findFileByUrl(DEFAULT_EXTERNAL_DOCUMENTATION_URL);
      if (javadoc != null) {
        result.add(new OrderRoot(javadoc, JavadocOrderRootType.getInstance()));
      }
    }

    String resFolderPath = target.getPath(RESOURCES);
    if (resFolderPath != null) {
      VirtualFile resFolder = findFileInLocalFileSystem(resFolderPath);
      if (resFolder != null) {
        result.add(new OrderRoot(resFolder, CLASSES));
      }
    }

    // Explicitly add annotations.jar unless the target platform already provides it (API16+).
    if (sdkPath != null && needsAnnotationsJarInClasspath(target)) {
      String annotationsJarPath = toSystemIndependentName(sdkPath) + ANNOTATIONS_JAR_RELATIVE_PATH;
      VirtualFile annotationsJar = findFileInJarFileSystem(annotationsJarPath);
      if (annotationsJar != null) {
        result.add(new OrderRoot(annotationsJar, CLASSES));
      }
    }

    return result;
  }

  @Nullable
  private static VirtualFile getPlatformDir(@NotNull IAndroidTarget target) {
    String platformPath = target.isPlatform() ? target.getLocation() : target.getParent().getLocation();
    if (platformPath == null) {
      return null;
    }
    return LocalFileSystem.getInstance().refreshAndFindFileByPath(toSystemIndependentName(platformPath));
  }

  @Nullable
  private static VirtualFile findFileInLocalFileSystem(@NotNull String path) {
    return LocalFileSystem.getInstance().findFileByPath(toSystemIndependentName(path));
  }

  @Nullable
  private static VirtualFile findFileInJarFileSystem(@NotNull String path) {
    return JarFileSystem.getInstance().findFileByPath(path + JAR_SEPARATOR);
  }

  /**
   * Indicates whether annotations.jar needs to be added to the classpath of an Android SDK. annotations.jar is not needed for API 16
   * or newer. The annotations are already included in android.jar.
   */
  public static boolean needsAnnotationsJarInClasspath(@NotNull IAndroidTarget target) {
    return target.getVersion().getApiLevel() <= 15;
  }

  @Nullable
  private static VirtualFile findJavadocDir(@NotNull VirtualFile dir) {
    VirtualFile docsDir = dir.findChild(FD_DOCS);
    if (docsDir != null) {
      return docsDir.findChild(FD_DOCS_REFERENCE);
    }
    return null;
  }

  private static boolean addJavaDocAndSources(@NotNull List<OrderRoot> orderRoots, @NotNull VirtualFile dir) {
    boolean found = false;

    VirtualFile javadocDir = findJavadocDir(dir);
    if (javadocDir != null) {
      orderRoots.add(new OrderRoot(javadocDir, JavadocOrderRootType.getInstance()));
      found = true;
    }

    VirtualFile sourcesDir = dir.findChild(FD_SOURCES);
    if (sourcesDir != null) {
      orderRoots.add(new OrderRoot(sourcesDir, SOURCES));
      found = true;
    }
    return found;
  }

  @NotNull
  public static String getPresentableTargetName(@NotNull IAndroidTarget target) {
    IAndroidTarget parentTarget = target.getParent();
    String name = target.getName();
    if (parentTarget != null) {
      name = name + " (" + parentTarget.getVersionName() + ')';
    }
    return name;
  }

  /**
   * Creates a new IDEA Android SDK. User is prompt for the paths of the Android SDK and JDK if necessary.
   *
   * @param sdkPath the path of Android SDK.
   * @return the created IDEA Android SDK, or {@null} if it was not possible to create it.
   */
  @Nullable
  public static Sdk createNewAndroidPlatform(@Nullable String sdkPath, boolean promptUser) {
    Sdk jdk = chooseOrCreateJavaSdk();
    if (sdkPath != null && jdk != null) {
      sdkPath = toSystemIndependentName(sdkPath);
      IAndroidTarget target = findBestTarget(sdkPath);
      if (target != null) {
        Sdk sdk = createNewAndroidPlatform(target, sdkPath, chooseNameForNewLibrary(target), jdk, true);
        if (sdk != null) {
          return sdk;
        }
      }
    }
    String jdkPath = jdk == null ? null : jdk.getHomePath();
    return promptUser ? promptUserForSdkCreation(null, sdkPath, jdkPath) : null;
  }

  @Nullable
  private static IAndroidTarget findBestTarget(@NotNull String sdkPath) {
    AndroidSdkData sdkData = getSdkData(sdkPath);
    if (sdkData != null) {
      IAndroidTarget[] targets = sdkData.getTargets();
      if (targets.length == 1) {
        return targets[0];
      }
      return findBestTarget(targets);
    }
    return null;
  }

  @Nullable
  private static IAndroidTarget findBestTarget(@NotNull IAndroidTarget[] targets) {
    IAndroidTarget bestTarget = null;
    int maxApiLevel = -1;
    for (IAndroidTarget target : targets) {
      AndroidVersion version = target.getVersion();
      if (target.isPlatform() && !version.isPreview() && version.getApiLevel() > maxApiLevel) {
        bestTarget = target;
        maxApiLevel = version.getApiLevel();
      }
    }
    return bestTarget;
  }

  @Nullable
  public static Sdk createNewAndroidPlatform(@NotNull IAndroidTarget target, @NotNull String sdkPath, boolean addRoots) {
    return createNewAndroidPlatform(target, sdkPath, chooseNameForNewLibrary(target), addRoots);
  }

  @Nullable
  public static Sdk createNewAndroidPlatform(@NotNull IAndroidTarget target,
                                             @NotNull String sdkPath,
                                             @NotNull String sdkName,
                                             boolean addRoots) {
    Sdk jdk = chooseOrCreateJavaSdk();
    return jdk != null ? createNewAndroidPlatform(target, sdkPath, sdkName, jdk, addRoots) : null;
  }

  @Nullable
  public static Sdk createNewAndroidPlatform(@NotNull IAndroidTarget target,
                                             @NotNull String sdkPath,
                                             @NotNull String sdkName,
                                             @Nullable Sdk jdk,
                                             boolean addRoots) {
    if (!target.getAdditionalLibraries().isEmpty()) {
      // Do not create an IntelliJ SDK for add-ons. Add-ons should be handled as module-level library dependencies.
      return null;
    }

    ProjectJdkTable table = ProjectJdkTable.getInstance();
    String tmpName = createUniqueSdkName(SDK_NAME, Arrays.asList(table.getAllJdks()));

    final Sdk sdk = table.createSdk(tmpName, AndroidSdkType.getInstance());

    SdkModificator sdkModificator = sdk.getSdkModificator();
    sdkModificator.setHomePath(sdkPath);
    sdkModificator.commitChanges();

    setUpSdk(sdk, sdkName, table.getAllJdks(), target, jdk, addRoots);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        ProjectJdkTable.getInstance().addJdk(sdk);
      }
    });
    return sdk;
  }

  @NotNull
  public static String chooseNameForNewLibrary(@NotNull IAndroidTarget target) {
    if (target.isPlatform()) {
      return SDK_NAME_PREFIX + target.getVersion().toString() + " Platform";
    }
    IAndroidTarget parentTarget = target.getParent();
    String name = SDK_NAME_PREFIX;
    if (parentTarget != null) {
      name = name + parentTarget.getVersionName() + ' ';
    }
    return name + target.getName();
  }

  public static String getTargetPresentableName(@NotNull IAndroidTarget target) {
    return target.isPlatform() ? target.getName() : target.getName() + " (" + target.getVersionName() + ')';
  }

  public static void setUpSdk(@NotNull Sdk androidSdk,
                              @NotNull String sdkName,
                              @NotNull Sdk[] allSdks,
                              @NotNull IAndroidTarget target,
                              @Nullable Sdk jdk,
                              boolean addRoots) {
    AndroidSdkAdditionalData data = new AndroidSdkAdditionalData(androidSdk, jdk);
    data.setBuildTarget(target);

    String name = createUniqueSdkName(sdkName, Arrays.asList(allSdks));

    SdkModificator sdkModificator = androidSdk.getSdkModificator();
    findAndSetPlatformSources(target, sdkModificator);

    sdkModificator.setName(name);
    if (jdk != null) {
      sdkModificator.setVersionString(jdk.getVersionString());
    }
    sdkModificator.setSdkAdditionalData(data);

    if (addRoots) {
      sdkModificator.removeAllRoots();
      for (OrderRoot orderRoot : getLibraryRootsForTarget(target, androidSdk.getHomePath(), true)) {
        sdkModificator.addRoot(orderRoot.getFile(), orderRoot.getType());
      }
      attachJdkAnnotations(sdkModificator);
    }

    sdkModificator.commitChanges();
  }

  public static void findAndSetPlatformSources(@NotNull IAndroidTarget target, @NotNull SdkModificator sdkModificator) {
    File sources = findPlatformSources(target);
    if (sources != null) {
      VirtualFile virtualFile = findFileByIoFile(sources, true);
      if (virtualFile != null) {
        for (VirtualFile file : sdkModificator.getRoots(SOURCES)) {
          if (file.equals(virtualFile)) {
            return;
          }
        }
        sdkModificator.addRoot(virtualFile, SOURCES);
      }
    }
  }

  public static boolean targetHasId(@NotNull IAndroidTarget target, @NotNull String id) {
    return id.equals(target.getVersion().getApiString()) || id.equals(target.getVersionName());
  }

  @NotNull
  public static Collection<String> getAndroidSdkPathsFromExistingPlatforms() {
    List<String> result = Lists.newArrayList();
    for (Sdk androidSdk : getAllAndroidSdks()) {
      AndroidPlatform androidPlatform = AndroidPlatform.getInstance(androidSdk);
      if (androidPlatform != null) {
        // Put default platforms in the list before non-default ones so they'll be looked at first.
        String sdkPath = toSystemIndependentName(androidPlatform.getSdkData().getLocation().getPath());
        if (result.contains(sdkPath)) continue;
        if (androidSdk.getName().startsWith(SDK_NAME_PREFIX)) {
          result.add(0, sdkPath);
        }
        else {
          result.add(sdkPath);
        }
      }
    }
    return result;
  }

  @NotNull
  public static List<Sdk> getAllAndroidSdks() {
    List<Sdk> allSdks = ProjectJdkTable.getInstance().getSdksOfType(AndroidSdkType.getInstance());
    return allSdks != null ? allSdks : Collections.<Sdk>emptyList();
  }

  private static boolean tryToSetAndroidPlatform(@NotNull Module module, @NotNull Sdk sdk) {
    AndroidPlatform platform = AndroidPlatform.parse(sdk);
    if (platform != null) {
      setModuleSdk(module, sdk);
      return true;
    }
    return false;
  }

  private static void setupPlatform(@NotNull Module module) {
    String targetHashString = getTargetHashStringFromPropertyFile(module);
    if (targetHashString != null && findAndSetSdkWithHashString(module, targetHashString)) {
      return;
    }

    PropertiesComponent component = PropertiesComponent.getInstance();
    if (component.isValueSet(DEFAULT_PLATFORM_NAME_PROPERTY)) {
      String defaultPlatformName = component.getValue(DEFAULT_PLATFORM_NAME_PROPERTY);
      Sdk defaultLib = ProjectJdkTable.getInstance().findJdk(defaultPlatformName, AndroidSdkType.getInstance().getName());
      if (defaultLib != null && tryToSetAndroidPlatform(module, defaultLib)) {
        return;
      }
    }
    for (Sdk sdk : getAllAndroidSdks()) {
      AndroidPlatform platform = AndroidPlatform.getInstance(sdk);

      if (platform != null &&
          checkSdkRoots(sdk, platform.getTarget(), false) &&
          tryToSetAndroidPlatform(module, sdk)) {
        component.setValue(DEFAULT_PLATFORM_NAME_PROPERTY, sdk.getName());
        return;
      }
    }
  }

  @Nullable
  public static Sdk findSuitableAndroidSdk(@NotNull String targetHash) {
    Set<String> foundSdkHomePaths = Sets.newHashSet();
    List<Sdk> notCompatibleSdks = Lists.newArrayList();

    for (Sdk sdk : getAllAndroidSdks()) {
      AndroidSdkAdditionalData originalData = getAndroidSdkAdditionalData(sdk);
      if (originalData == null) {
        continue;
      }
      String sdkHomePath = sdk.getHomePath();
      if (!foundSdkHomePaths.contains(sdkHomePath) && targetHash.equals(originalData.getBuildTargetHashString())) {
        if (VersionCheck.isCompatibleVersion(sdkHomePath)) {
          return sdk;
        }
        notCompatibleSdks.add(sdk);
        if (sdkHomePath != null) {
          foundSdkHomePaths.add(sdkHomePath);
        }
      }
    }

    return notCompatibleSdks.isEmpty() ? null : notCompatibleSdks.get(0);
  }

  @Nullable
  private static String getTargetHashStringFromPropertyFile(@NotNull Module module) {
    Pair<String, VirtualFile> targetProp = getProjectPropertyValue(module, ANDROID_TARGET_PROPERTY);
    return targetProp != null ? targetProp.getFirst() : null;
  }

  private static boolean findAndSetSdkWithHashString(@NotNull Module module, @NotNull String targetHashString) {
    Pair<String, VirtualFile> sdkDirProperty = getPropertyValue(module, FN_LOCAL_PROPERTIES, "sdk.dir");
    String sdkDir = sdkDirProperty != null ? sdkDirProperty.getFirst() : null;
    return findAndSetSdk(module, targetHashString, sdkDir);
  }

  /**
   * Finds a matching Android SDK and sets it in the given module.
   *
   * @param module           the module to set the found SDK to.
   * @param targetHashString compile target.
   * @param sdkPath          path, in the file system, of the Android SDK.
   * @return {@code true} if a matching Android SDK was found and set in the module; {@code false} otherwise.
   */
  public static boolean findAndSetSdk(@NotNull Module module, @NotNull String targetHashString, @Nullable String sdkPath) {
    if (sdkPath != null) {
      sdkPath = toSystemIndependentName(sdkPath);
    }

    Sdk sdk = findSuitableAndroidSdk(targetHashString);
    if (sdk != null) {
      setModuleSdk(module, sdk);
      return true;
    }

    if (sdkPath != null && tryToCreateAndSetAndroidSdk(module, sdkPath, targetHashString)) {
      return true;
    }

    String androidHomeValue = System.getenv(ANDROID_HOME_ENV);
    if (androidHomeValue != null && tryToCreateAndSetAndroidSdk(module, toSystemIndependentName(androidHomeValue), targetHashString)) {
      return true;
    }

    for (String dir : getAndroidSdkPathsFromExistingPlatforms()) {
      if (tryToCreateAndSetAndroidSdk(module, dir, targetHashString)) {
        return true;
      }
    }
    return false;
  }

  public static void clearLocalPkgInfo(@NotNull Sdk sdk) {
    AndroidSdkAdditionalData data = getAndroidSdkAdditionalData(sdk);
    if (data == null) {
      return;
    }

    if (data.getAndroidPlatform() != null) {
      data.getAndroidPlatform().getSdkData().getSdkHandler().getSdkManager(new StudioLoggerProgressIndicator(AndroidSdkUtils.class))
        .markInvalid();
    }
    data.clearAndroidPlatform();
  }

  /**
   * Reload SDK information and update the source root of the SDK.
   */
  public static void updateSdkSourceRoot(@NotNull Sdk sdk) {
    AndroidPlatform platform = AndroidPlatform.getInstance(sdk);
    if (platform != null) {
      IAndroidTarget target = platform.getTarget();
      SdkModificator sdkModificator = sdk.getSdkModificator();
      sdkModificator.removeRoots(SOURCES);
      findAndSetPlatformSources(target, sdkModificator);
      sdkModificator.commitChanges();
    }
  }

  @VisibleForTesting
  static boolean tryToCreateAndSetAndroidSdk(@NotNull Module module, @NotNull String sdkPath, @NotNull String targetHashString) {
    File path = new File(toSystemDependentName(sdkPath));
    Sdk sdk = tryToCreateAndroidSdk(path, targetHashString);
    if (sdk != null) {
      setModuleSdk(module, sdk);
      return true;
    }
    return false;
  }

  @Nullable
  public static Sdk tryToCreateAndroidSdk(@NotNull File sdkPath, @NotNull String targetHashString) {
    AndroidSdkData sdkData = getSdkData(sdkPath);
    if (sdkData != null) {
      sdkData.getSdkHandler().getSdkManager(new StudioLoggerProgressIndicator(AndroidSdkUtils.class)).markInvalid();
      IAndroidTarget target = sdkData.findTargetByHashString(targetHashString);
      if (target != null) {
        return createNewAndroidPlatform(target, sdkData.getLocation().getPath(), true);
      }
    }
    return null;
  }

  @Nullable
  private static Sdk promptUserForSdkCreation(@Nullable final IAndroidTarget target,
                                              @Nullable final String androidSdkPath,
                                              @Nullable final String jdkPath) {
    final Ref<Sdk> sdkRef = new Ref<Sdk>();
    Runnable task = new Runnable() {
      @Override
      public void run() {
        SelectSdkDialog dlg = new SelectSdkDialog(jdkPath, androidSdkPath);
        dlg.setModal(true);
        if (dlg.showAndGet()) {
          Sdk sdk = createNewAndroidPlatform(target, dlg.getAndroidHome(), dlg.getJdkHome());
          sdkRef.set(sdk);
          if (sdk != null) {
            RunAndroidSdkManagerAction.updateInWelcomePage(dlg.getContentPanel());
          }
        }
      }
    };
    Application application = ApplicationManager.getApplication();
    if (application.isDispatchThread()) {
      task.run();
      return sdkRef.get();
    }
    application.invokeAndWait(task, ModalityState.any());
    return sdkRef.get();
  }

  @Nullable
  private static Sdk createNewAndroidPlatform(@Nullable IAndroidTarget target, @NotNull String androidSdkPath, @NotNull String jdkPath) {
    if (isNotEmpty(jdkPath)) {
      jdkPath = toSystemIndependentName(jdkPath);
      Sdk jdk = createJdk(jdkPath);
      if (jdk != null) {
        androidSdkPath = toSystemIndependentName(androidSdkPath);
        if (target == null) {
          target = findBestTarget(androidSdkPath);
        }
        if (target != null) {
          return createNewAndroidPlatform(target, androidSdkPath, chooseNameForNewLibrary(target), jdk, true);
        }
      }
    }
    return null;
  }

  public static void setupAndroidPlatformIfNecessary(@NotNull Module module, boolean forceImportFromProperties) {
    Sdk currentSdk = ModuleRootManager.getInstance(module).getSdk();
    if (currentSdk == null || !isAndroidSdk(currentSdk)) {
      setupPlatform(module);
      return;
    }
    if (forceImportFromProperties) {
      AndroidPlatform platform = AndroidPlatform.getInstance(currentSdk);
      if (platform != null) {
        String targetHashString = getTargetHashStringFromPropertyFile(module);
        String currentTargetHashString = platform.getTarget().hashString();

        if (targetHashString != null && !targetHashString.equals(currentTargetHashString)) {
          findAndSetSdkWithHashString(module, targetHashString);
        }
      }
    }
  }

  public static void openModuleDependenciesConfigurable(@NotNull Module module) {
    ProjectSettingsService.getInstance(module.getProject()).openModuleDependenciesSettings(module, null);
  }

  @Nullable
  public static Sdk findAppropriateAndroidPlatform(@NotNull IAndroidTarget target, @NotNull AndroidSdkData sdkData, boolean forMaven) {
    for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
      String homePath = sdk.getHomePath();

      if (homePath != null && isAndroidSdk(sdk)) {
        AndroidSdkData currentSdkData = getSdkData(homePath);

        if (sdkData.equals(currentSdkData)) {
          AndroidSdkAdditionalData data = getAndroidSdkAdditionalData(sdk);
          if (data != null) {
            IAndroidTarget currentTarget = data.getBuildTarget(currentSdkData);
            if (currentTarget != null &&
                target.hashString().equals(currentTarget.hashString()) &&
                checkSdkRoots(sdk, target, forMaven)) {
              return sdk;
            }
          }
        }
      }
    }
    return null;
  }

  public static boolean isAndroidSdk(@NotNull Sdk sdk) {
    return sdk.getSdkType() == AndroidSdkType.getInstance();
  }

  public static boolean checkSdkRoots(@NotNull Sdk sdk, @NotNull IAndroidTarget target, boolean forMaven) {
    String homePath = sdk.getHomePath();
    if (homePath == null) {
      return false;
    }
    AndroidSdkAdditionalData sdkAdditionalData = getAndroidSdkAdditionalData(sdk);
    Sdk javaSdk = sdkAdditionalData != null ? sdkAdditionalData.getJavaSdk() : null;
    if (javaSdk == null) {
      return false;
    }
    Set<VirtualFile> filesInSdk = Sets.newHashSet(sdk.getRootProvider().getFiles(CLASSES));

    List<VirtualFile> platformAndAddOnJars = getPlatformAndAddOnJars(target);
    for (VirtualFile file : platformAndAddOnJars) {
      if (filesInSdk.contains(file) == forMaven) {
        return false;
      }
    }
    boolean containsJarFromJdk = false;

    for (VirtualFile file : javaSdk.getRootProvider().getFiles(CLASSES)) {
      if (file.getFileType() instanceof ArchiveFileType && filesInSdk.contains(file)) {
        containsJarFromJdk = true;
      }
    }
    return containsJarFromJdk == forMaven;
  }

  @Nullable
  public static File getAdb(@NotNull Project project) {
    AndroidSdkData data = getProjectSdkData(project);
    if (data == null) {
      data = getFirstAndroidModuleSdkData(project);
    }
    File adb = data == null ? null : new File(data.getLocation(), platformToolPath(FN_ADB));
    return adb != null && adb.exists() ? adb : null;
  }

  @Nullable
  private static AndroidSdkData getFirstAndroidModuleSdkData(Project project) {
    List<AndroidFacet> facets = ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID);
    for (AndroidFacet facet : facets) {
      AndroidPlatform androidPlatform = facet.getConfiguration().getAndroidPlatform();
      if (androidPlatform != null) {
        return androidPlatform.getSdkData();
      }
    }
    return null;
  }

  @Nullable
  private static AndroidSdkData getProjectSdkData(Project project) {
    Sdk projectSdk = ProjectRootManager.getInstance(project).getProjectSdk();
    if (projectSdk != null) {
      AndroidPlatform platform = AndroidPlatform.getInstance(projectSdk);
      return platform != null ? platform.getSdkData() : null;
    }
    return null;
  }

  @Nullable
  public static AndroidSdkAdditionalData getAndroidSdkAdditionalData(@NotNull Sdk sdk) {
    SdkAdditionalData sdkAdditionalData = sdk.getSdkAdditionalData();
    return sdkAdditionalData instanceof AndroidSdkAdditionalData ? (AndroidSdkAdditionalData)sdkAdditionalData : null;
  }

  public static boolean activateDdmsIfNecessary(@NotNull Project project) {
    if (AndroidEnableAdbServiceAction.isAdbServiceEnabled()) {
      AndroidDebugBridge bridge = getDebugBridge(project);
      if (bridge != null && AdbService.isDdmsCorrupted(bridge)) {
        LOG.info("DDMLIB is corrupted and will be restarted");
        AdbService.getInstance().restartDdmlib(project);
      }
    }
    else {
      final OSProcessHandler ddmsProcessHandler = AndroidRunDdmsAction.getDdmsProcessHandler();
      if (ddmsProcessHandler != null) {
        String message = "Monitor will be closed to enable ADB integration. Continue?";
        int result = Messages.showYesNoDialog(project, message, "ADB Integration", Messages.getQuestionIcon());
        if (result != Messages.YES) {
          return false;
        }

        Runnable destroyingRunnable = new Runnable() {
          @Override
          public void run() {
            if (!ddmsProcessHandler.isProcessTerminated()) {
              OSProcessManager.getInstance().killProcessTree(ddmsProcessHandler.getProcess());
              ddmsProcessHandler.waitFor();
            }
          }
        };
        if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(destroyingRunnable, "Closing Monitor", true, project)) {
          return false;
        }
        setAdbServiceEnabled(project, true);
        return true;
      }

      int result = Messages.showYesNoDialog(project, AndroidBundle.message("android.ddms.disabled.error"),
                                            AndroidBundle.message("android.ddms.disabled.dialog.title"), Messages.getQuestionIcon());
      if (result != Messages.YES) {
        return false;
      }
      setAdbServiceEnabled(project, true);
    }
    return true;
  }

  public static boolean isAndroidSdkAvailable() {
    return tryToChooseAndroidSdk() != null;
  }

  /**
   * @return AndroidSdkData for the current SDK. In the normal case, this should always be set up. During the first run or if for some
   * reason we can't find the SDK this can be null.
   */
  @Nullable
  public static AndroidSdkData tryToChooseAndroidSdk() {
    if (ourSdkData == null) {
      if (isAndroidStudio()) {
        File path = IdeSdks.getAndroidSdkPath();
        if (path != null) {
          ourSdkData = getSdkData(path.getPath());
          if (ourSdkData != null) {
            return ourSdkData;
          }
        }
      }

      for (String s : getAndroidSdkPathsFromExistingPlatforms()) {
        ourSdkData = getSdkData(s);
        if (ourSdkData != null) {
          break;
        }
      }
    }
    return ourSdkData;
  }

  @NotNull
  public static AndroidSdkHandler tryToChooseSdkHandler() {
    AndroidSdkData data = tryToChooseAndroidSdk();
    if (data == null) {
      return AndroidSdkHandler.getInstance(null);
    }
    return data.getSdkHandler();
  }

  public static void setSdkData(@Nullable AndroidSdkData data) {
    ourSdkData = data;
  }

  /**
   * Finds the root source code folder for the given android target, if any
   */
  @Nullable
  public static File findPlatformSources(@NotNull IAndroidTarget target) {
    String path = target.getPath(IAndroidTarget.SOURCES);
    if (path != null) {
      File platformSource = new File(path);
      if (platformSource.isDirectory()) {
        return platformSource;
      }
    }
    return null;
  }

  /**
   * For a given target, returns a brief user-facing string that describes the platform, including the API level, platform version number,
   * and codename. Does the right thing with pre-release platforms.
   */
  @NotNull
  public static String getTargetLabel(@NotNull IAndroidTarget target) {
    if (!target.isPlatform()) {
      return String.format("%1$s (API %2$s)", target.getFullName(), target.getVersion().getApiString());
    }
    AndroidVersion version = target.getVersion();
    if (version.isPreview()) {
      return String.format("API %d+: %s", target.getVersion().getApiLevel(), target.getName());
    }
    String name = SdkVersionInfo.getAndroidName(target.getVersion().getApiLevel());
    if (isNotEmpty(name)) {
      return name;
    }
    String release = target.getProperty("ro.build.version.release"); //$NON-NLS-1$
    if (release != null) {
      return String.format("API %1$d: Android %2$s", version.getApiLevel(), release);
    }
    return String.format("API %1$d", version.getApiLevel());
  }

  @Nullable
  public static AndroidDebugBridge getDebugBridge(@NotNull Project project) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    AndroidSdkData data = getProjectSdkData(project);
    if (data == null) {
      data = getFirstAndroidModuleSdkData(project);
    }
    if (data == null) {
      return null;
    }

    AndroidDebugBridge bridge = null;
    boolean retry;
    do {
      File adb = getAdb(project);
      if (adb == null) {
        LOG.error("Unable to locate adb within SDK");
        return null;
      }

      Future<AndroidDebugBridge> future = AdbService.getInstance().getDebugBridge(adb);
      MyMonitorBridgeConnectionTask task = new MyMonitorBridgeConnectionTask(project, future);
      ProgressManager.getInstance().run(task);

      if (task.wasCanceled()) { // if the user cancelled the dialog
        return null;
      }

      retry = false;
      try {
        bridge = future.get();
      }
      catch (InterruptedException e) {
        break;
      }
      catch (ExecutionException e) {
        // timed out waiting for bridge, ask the user what to do
        String message = "ADB not responding. If you'd like to retry, then please manually kill \"" + FN_ADB + "\" and click 'Restart'";
        retry = Messages.showYesNoDialog(project, message, CommonBundle.getErrorTitle(), "&Restart", "&Cancel", Messages.getErrorIcon()) ==
                Messages.YES;
      }
    }
    while (retry);

    return bridge;
  }

  private static class MyMonitorBridgeConnectionTask extends Task.Modal {
    private final Future<AndroidDebugBridge> myFuture;
    private boolean myCancelled; // set/read only on EDT

    public MyMonitorBridgeConnectionTask(@Nullable Project project, Future<AndroidDebugBridge> future) {
      super(project, "Waiting for adb", true);
      myFuture = future;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      indicator.setIndeterminate(true);
      while (!myFuture.isDone()) {
        try {
          myFuture.get(200, TimeUnit.MILLISECONDS);
        }
        catch (Exception ignored) {
          // all we need to know is whether the future completed or not..
        }

        if (indicator.isCanceled()) {
          return;
        }
      }
    }

    @Override
    public void onCancel() {
      myCancelled = true;
    }

    public boolean wasCanceled() {
      return myCancelled;
    }
  }
}
