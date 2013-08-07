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

import com.android.SdkConstants;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkManager;
import com.android.tools.idea.sdk.Jdks;
import com.android.tools.idea.sdk.SelectSdkDialog;
import com.android.tools.idea.sdk.VersionCheck;
import com.android.tools.idea.startup.ExternalAnnotationsSupport;
import com.android.utils.ILogger;
import com.android.utils.NullLogger;
import com.android.utils.StdLogger;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
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
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.actions.AndroidEnableAdbServiceAction;
import org.jetbrains.android.actions.AndroidRunDdmsAction;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.logcat.AndroidToolWindowFactory;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public final class AndroidSdkUtils {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.sdk.AndroidSdkUtils");

  public static final String DEFAULT_PLATFORM_NAME_PROPERTY = "AndroidPlatformName";
  @NonNls public static final String ANDROID_HOME_ENV = "ANDROID_HOME";

  private static SdkManager ourSdkManager;

  private AndroidSdkUtils() {
  }

  @Nullable
  private static VirtualFile getPlatformDir(@NotNull IAndroidTarget target) {
    String platformPath = target.isPlatform() ? target.getLocation() : target.getParent().getLocation();
    return LocalFileSystem.getInstance().refreshAndFindFileByPath(platformPath);
  }

  @NotNull
  public static List<VirtualFile> getPlatformAndAddOnJars(@NotNull IAndroidTarget target) {
    VirtualFile platformDir = getPlatformDir(target);
    if (platformDir == null) {
      return Collections.emptyList();
    }

    VirtualFile androidJar = platformDir.findChild(SdkConstants.FN_FRAMEWORK_LIBRARY);
    if (androidJar == null) {
      return Collections.emptyList();
    }

    List<VirtualFile> result = Lists.newArrayList();

    VirtualFile androidJarRoot = JarFileSystem.getInstance().findFileByPath(androidJar.getPath() + JarFileSystem.JAR_SEPARATOR);
    if (androidJarRoot != null) {
      result.add(androidJarRoot);
    }

    IAndroidTarget.IOptionalLibrary[] libs = target.getOptionalLibraries();
    if (libs != null) {
      for (IAndroidTarget.IOptionalLibrary lib : libs) {
        VirtualFile libRoot = JarFileSystem.getInstance().findFileByPath(lib.getJarPath() + JarFileSystem.JAR_SEPARATOR);
        if (libRoot != null) {
          result.add(libRoot);
        }
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
        result.add(new OrderRoot(file, OrderRootType.CLASSES));
      }
    }
    VirtualFile platformDir = getPlatformDir(target);
    if (platformDir == null) return result;

    VirtualFile targetDir = platformDir;
    if (!target.isPlatform()) {
      targetDir = LocalFileSystem.getInstance().findFileByPath(target.getLocation());
    }
    boolean docsOrSourcesFound = false;

    if (targetDir != null) {
      docsOrSourcesFound = addJavaDocAndSources(result, targetDir) || docsOrSourcesFound;
    }
    VirtualFile sdkDir = sdkPath != null ? LocalFileSystem.getInstance().findFileByPath(sdkPath) : null;
    if (sdkDir != null) {
      docsOrSourcesFound = addJavaDocAndSources(result, sdkDir) || docsOrSourcesFound;
    }

    // todo: replace it by target.getPath(SOURCES) when it'll be up to date
    final VirtualFile sourcesDir = sdkDir.findChild(SdkConstants.FD_PKG_SOURCES);
    if (sourcesDir != null && sourcesDir.isDirectory()) {
      final VirtualFile platformSourcesDir = sourcesDir.findChild(platformDir.getName());
      if (platformSourcesDir != null && platformSourcesDir.isDirectory()) {
        result.add(new OrderRoot(platformSourcesDir, OrderRootType.SOURCES));
        docsOrSourcesFound = true;
      }
    }

    if (!docsOrSourcesFound) {
      VirtualFile f = VirtualFileManager.getInstance().findFileByUrl(AndroidSdkType.DEFAULT_EXTERNAL_DOCUMENTATION_URL);
      if (f != null) {
        result.add(new OrderRoot(f, JavadocOrderRootType.getInstance()));
      }
    }

    String resFolderPath = target.getPath(IAndroidTarget.RESOURCES);
    if (resFolderPath != null) {
      VirtualFile resFolder = LocalFileSystem.getInstance().findFileByPath(resFolderPath);
      if (resFolder != null) {
        result.add(new OrderRoot(resFolder, OrderRootType.CLASSES));
      }
    }

    if (sdkPath != null) {
      // todo: check if we should do it for new android platforms (api_level >= 15)
      JarFileSystem jarFileSystem = JarFileSystem.getInstance();
      String annotationsJarPath =
        FileUtil.toSystemIndependentName(sdkPath) + AndroidCommonUtils.ANNOTATIONS_JAR_RELATIVE_PATH + JarFileSystem.JAR_SEPARATOR;
      VirtualFile annotationsJar = jarFileSystem.findFileByPath(annotationsJarPath);
      if (annotationsJar != null) {
        result.add(new OrderRoot(annotationsJar, OrderRootType.CLASSES));
      }
    }

    return result;
  }

  @Nullable
  private static VirtualFile findJavadocDir(@NotNull VirtualFile dir) {
    VirtualFile docsDir = dir.findChild(SdkConstants.FD_DOCS);
    if (docsDir != null) {
      return docsDir.findChild(SdkConstants.FD_DOCS_REFERENCE);
    }
    return null;
  }

  private static boolean addJavaDocAndSources(@NotNull List<OrderRoot> list, @NotNull VirtualFile dir) {
    boolean found = false;

    VirtualFile javadocDir = findJavadocDir(dir);
    if (javadocDir != null) {
      list.add(new OrderRoot(javadocDir, JavadocOrderRootType.getInstance()));
      found = true;
    }

    VirtualFile sourcesDir = dir.findChild(SdkConstants.FD_SOURCES);
    if (sourcesDir != null) {
      list.add(new OrderRoot(sourcesDir, OrderRootType.SOURCES));
      found = true;
    }
    return found;
  }

  public static String getPresentableTargetName(@NotNull IAndroidTarget target) {
    IAndroidTarget parentTarget = target.getParent();
    if (parentTarget != null) {
      return target.getName() + " (" + parentTarget.getVersionName() + ')';
    }
    return target.getName();
  }

  /**
   * Creates a new IDEA Android SDK. User is prompt for the paths of the Android SDK and JDK if necessary.
   *
   * @param sdkPath the path of Android SDK.
   * @return the created IDEA Android SDK, or {@null} if it was not possible to create it.
   */
  @Nullable
  public static Sdk createNewAndroidPlatform(@Nullable String sdkPath) {
    Sdk jdk = Jdks.chooseOrCreateJavaSdk();
    if (sdkPath != null && jdk != null) {
      sdkPath = FileUtil.toSystemIndependentName(sdkPath);
      IAndroidTarget target = findBestTarget(sdkPath);
      if (target != null) {
        Sdk sdk = createNewAndroidPlatform(target, sdkPath, chooseNameForNewLibrary(target), jdk, true);
        if (sdk != null) {
          return sdk;
        }
      }
    }
    String jdkPath = jdk == null ? null : jdk.getHomePath();
    return promptUserForSdkCreation(null, sdkPath, jdkPath);
  }

  @Nullable
  private static IAndroidTarget findBestTarget(@NotNull String sdkPath) {
    AndroidSdkData sdkData = AndroidSdkData.parse(sdkPath, NullLogger.getLogger());
    if (sdkData != null) {
      IAndroidTarget[] targets = sdkData.getTargets();
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
  public static Sdk createNewAndroidPlatform(@NotNull IAndroidTarget target,
                                             @NotNull String sdkPath,
                                             boolean addRoots) {
    return createNewAndroidPlatform(target, sdkPath, chooseNameForNewLibrary(target), addRoots);
  }

  @Nullable
  public static Sdk createNewAndroidPlatform(@NotNull IAndroidTarget target,
                                             @NotNull String sdkPath,
                                             @NotNull String sdkName,
                                             boolean addRoots) {
    Sdk jdk = Jdks.chooseOrCreateJavaSdk();
    if (jdk == null) {
      return null;
    }
    return createNewAndroidPlatform(target, sdkPath, sdkName, jdk, addRoots);
  }

  @Nullable
  private static Sdk createNewAndroidPlatform(@NotNull IAndroidTarget target,
                                              @NotNull String sdkPath,
                                              @NotNull String sdkName,
                                              @Nullable Sdk jdk,
                                              boolean addRoots) {
    if (!VersionCheck.isCompatibleVersion(sdkPath)) {
      return null;
    }
    ProjectJdkTable table = ProjectJdkTable.getInstance();
    String tmpName = SdkConfigurationUtil.createUniqueSdkName(AndroidSdkType.SDK_NAME, Arrays.asList(table.getAllJdks()));

    final Sdk sdk = table.createSdk(tmpName, SdkType.findInstance(AndroidSdkType.class));

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
      return target.getName() + " Platform";
    }
    IAndroidTarget parentTarget = target.getParent();
    if (parentTarget != null) {
      return "Android " + parentTarget.getVersionName() + ' ' + target.getName();
    }
    return "Android " + target.getName();
  }

  public static String getTargetPresentableName(@NotNull IAndroidTarget target) {
    return target.isPlatform() ?
           target.getName() :
           target.getName() + " (" + target.getVersionName() + ')';
  }

  public static void setUpSdk(@NotNull Sdk androidSdk,
                              @NotNull String sdkName,
                              @NotNull Sdk[] allSdks,
                              @NotNull IAndroidTarget target,
                              @Nullable Sdk jdk,
                              boolean addRoots) {
    AndroidSdkAdditionalData data = new AndroidSdkAdditionalData(androidSdk, jdk);
    data.setBuildTarget(target);

    sdkName = SdkConfigurationUtil.createUniqueSdkName(sdkName, Arrays.asList(allSdks));

    SdkModificator sdkModificator = androidSdk.getSdkModificator();
    sdkModificator.setName(sdkName);

    if (jdk != null) {
      //noinspection ConstantConditions
      sdkModificator.setVersionString(jdk.getVersionString());
    }
    sdkModificator.setSdkAdditionalData(data);

    if (addRoots) {
      for (OrderRoot orderRoot : getLibraryRootsForTarget(target, androidSdk.getHomePath(), true)) {
        sdkModificator.addRoot(orderRoot.getFile(), orderRoot.getType());
      }
      ExternalAnnotationsSupport.attachJdkAnnotations(sdkModificator);
    }

    sdkModificator.commitChanges();
  }

  public static boolean targetHasId(@NotNull IAndroidTarget target, @NotNull String id) {
    return id.equals(target.getVersion().getApiString()) || id.equals(target.getVersionName());
  }

  @NotNull
  public static Collection<String> getAndroidSdkPathsFromExistingPlatforms() {
    List<Sdk> androidSdks = getAllAndroidSdks();
    Set<String> result = new HashSet<String>(androidSdks.size());
    for (Sdk androidSdk : androidSdks) {
      AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)androidSdk.getSdkAdditionalData();
      if (data != null) {
        AndroidPlatform androidPlatform = data.getAndroidPlatform();
        if (androidPlatform != null) {
          result.add(FileUtil.toSystemIndependentName(androidPlatform.getSdkData().getLocation()));
        }
      }
    }
    return result;
  }

  @NotNull
  public static List<Sdk> getAllAndroidSdks() {
    List<Sdk> allSdks = ProjectJdkTable.getInstance().getSdksOfType(AndroidSdkType.getInstance());
    return ObjectUtils.notNull(allSdks, Collections.<Sdk>emptyList());
  }

  private static boolean tryToSetAndroidPlatform(Module module, Sdk sdk) {
    AndroidPlatform platform = AndroidPlatform.parse(sdk);
    if (platform != null) {
      ModuleRootModificationUtil.setModuleSdk(module, sdk);
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
      AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
      if (data != null) {
        AndroidPlatform platform = data.getAndroidPlatform();

        if (platform != null &&
            checkSdkRoots(sdk, platform.getTarget(), false) &&
            tryToSetAndroidPlatform(module, sdk)) {
          component.setValue(DEFAULT_PLATFORM_NAME_PROPERTY, sdk.getName());
          return;
        }
      }
    }
  }

  @VisibleForTesting
  @Nullable
  static Sdk findSuitableAndroidSdk(@NotNull String targetHashString, @Nullable String sdkPath, boolean promptUserIfNecessary) {
    for (Sdk sdk : getAllAndroidSdks()) {
      AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
      if (data == null) {
        continue;
      }
      AndroidPlatform androidPlatform = data.getAndroidPlatform();
      if (androidPlatform != null) {
        String baseDir = FileUtil.toSystemIndependentName(androidPlatform.getSdkData().getLocation());
        boolean compatibleVersion = VersionCheck.isCompatibleVersion(baseDir);
        boolean matchingHashString = targetHashString.equals(androidPlatform.getTarget().hashString());
        boolean suitable = compatibleVersion && matchingHashString && checkSdkRoots(sdk, androidPlatform.getTarget(), false);
        if (sdkPath != null && FileUtil.pathsEqual(baseDir, sdkPath)) {
          if (suitable) {
             return sdk;
          }
          if (promptUserIfNecessary) {
            if (!compatibleVersion) {
              // Old SDK, needs to be replaced.
              Sdk jdk = Jdks.chooseOrCreateJavaSdk();
              String jdkPath = jdk == null ? null : jdk.getHomePath();
              return promptUserForSdkCreation(androidPlatform.getTarget(), null, jdkPath);
            }

            if (!matchingHashString) {
              // This is the specified SDK (usually in local.properties file.) We try our best to fix it.
              // TODO download platform;
            }
          }
        }
        else if (suitable) {
          return sdk;
        }
      }
    }
    return null;
  }

  @Nullable
  private static String getTargetHashStringFromPropertyFile(@NotNull Module module) {
    Pair<String, VirtualFile> targetProp = AndroidRootUtil.getProjectPropertyValue(module, AndroidUtils.ANDROID_TARGET_PROPERTY);
    return targetProp != null ? targetProp.getFirst() : null;
  }

  private static boolean findAndSetSdkWithHashString(@NotNull Module module, @NotNull String targetHashString) {
    Pair<String, VirtualFile> sdkDirProperty = AndroidRootUtil.getPropertyValue(module, SdkConstants.FN_LOCAL_PROPERTIES, "sdk.dir");
    String sdkDir = sdkDirProperty != null ? sdkDirProperty.getFirst() : null;
    return findAndSetSdk(module, targetHashString, sdkDir, false);
  }

  /**
   * Finds a matching Android SDK and sets it in the given module.
   *
   * @param module                the module to set the found SDK to.
   * @param targetHashString      compile target.
   * @param sdkPath               path, in the file system, of the Android SDK.
   * @param promptUserIfNecessary indicates whether user can be prompted to enter information in case an Android SDK cannot be found (e.g.
   *                              download a platform, or enter the path of an Android SDK to use.)
   * @return {@code true} if a matching Android SDK was found and set in the module; {@code false} otherwise.
   */
  public static boolean findAndSetSdk(@NotNull Module module,
                                      @NotNull String targetHashString,
                                      @Nullable String sdkPath,
                                      boolean promptUserIfNecessary) {
    if (sdkPath != null) {
      sdkPath = FileUtil.toSystemIndependentName(sdkPath);
    }

    Sdk sdk = findSuitableAndroidSdk(targetHashString, sdkPath, promptUserIfNecessary);
    if (sdk != null) {
      ModuleRootModificationUtil.setModuleSdk(module, sdk);
      return true;
    }

    if (sdkPath != null && tryToCreateAndSetAndroidSdk(module, sdkPath, targetHashString, promptUserIfNecessary)) {
      return true;
    }

    String androidHomeValue = System.getenv(ANDROID_HOME_ENV);
    if (androidHomeValue != null &&
        tryToCreateAndSetAndroidSdk(module, FileUtil.toSystemIndependentName(androidHomeValue), targetHashString, false)) {
      return true;
    }

    for (String dir : getAndroidSdkPathsFromExistingPlatforms()) {
      if (tryToCreateAndSetAndroidSdk(module, dir, targetHashString, false)) {
        return true;
      }
    }
    return false;
  }

  @VisibleForTesting
  static boolean tryToCreateAndSetAndroidSdk(@NotNull Module module,
                                             @NotNull String sdkPath,
                                             @NotNull String targetHashString,
                                             boolean promptUserIfNecessary) {
    AndroidSdkData sdkData = AndroidSdkData.parse(sdkPath, NullLogger.getLogger());
    if (sdkData != null) {
      IAndroidTarget target = sdkData.findTargetByHashString(targetHashString);
      if (target != null) {
        Sdk androidSdk = createNewAndroidPlatform(target, sdkData.getLocation(), true);
        if (androidSdk != null) {
          ModuleRootModificationUtil.setModuleSdk(module, androidSdk);
          return true;
        }
        if (promptUserIfNecessary) {
          // We got here most likely because we could not create a JDK. Prompt user.
          androidSdk = promptUserForSdkCreation(target, sdkPath, null);
          if (androidSdk != null) {
            ModuleRootModificationUtil.setModuleSdk(module, androidSdk);
            return true;
          }
        }
      }
      else if (promptUserIfNecessary) {
        // We got here because we couldn't get target for given SDK path. Most likely it is an old SDK.
        String pathToShow = VersionCheck.isCompatibleVersion(sdkPath) ? sdkPath : null;
        Sdk jdk = Jdks.chooseOrCreateJavaSdk();
        String jdkPath = jdk == null ? null : jdk.getHomePath();
        Sdk androidSdk = promptUserForSdkCreation(null, pathToShow, jdkPath);
        // TODO check platform
        if (androidSdk != null) {
          ModuleRootModificationUtil.setModuleSdk(module, androidSdk);
          return true;
        }
      }
    }
    return false;
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
    if (!Strings.isNullOrEmpty(jdkPath)) {
      jdkPath = FileUtil.toSystemIndependentName(jdkPath);
      Sdk jdk = Jdks.createJdk(jdkPath);
      if (jdk != null) {
        androidSdkPath = FileUtil.toSystemIndependentName(androidSdkPath);
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

  public static void setupAndroidPlatformInNecessary(@NotNull Module module, boolean forceImportFromProperties) {
    Sdk currentSdk = ModuleRootManager.getInstance(module).getSdk();
    if (currentSdk == null || !isAndroidSdk(currentSdk)) {
      setupPlatform(module);
      return;
    }
    if (forceImportFromProperties) {
      SdkAdditionalData data = currentSdk.getSdkAdditionalData();

      if (data instanceof AndroidSdkAdditionalData) {
        AndroidPlatform platform = ((AndroidSdkAdditionalData)data).getAndroidPlatform();

        if (platform != null) {
          String targetHashString = getTargetHashStringFromPropertyFile(module);
          String currentTargetHashString = platform.getTarget().hashString();

          if (targetHashString != null && !targetHashString.equals(currentTargetHashString)) {
            findAndSetSdkWithHashString(module, targetHashString);
          }
        }
      }
    }
  }

  public static void openModuleDependenciesConfigurable(final Module module) {
    ProjectSettingsService.getInstance(module.getProject()).openModuleDependenciesSettings(module, null);
  }

  @Nullable
  public static Sdk findAppropriateAndroidPlatform(@NotNull IAndroidTarget target,
                                                   @NotNull AndroidSdkData sdkData,
                                                   boolean forMaven) {
    for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
      String homePath = sdk.getHomePath();

      if (homePath != null && isAndroidSdk(sdk)) {
        AndroidSdkData currentSdkData = AndroidSdkData.parse(homePath, NullLogger.getLogger());

        if (currentSdkData != null && currentSdkData.equals(sdkData)) {
          AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
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

  private static boolean isAndroidSdk(@NotNull Sdk sdk) {
    return sdk.getSdkType().equals(AndroidSdkType.getInstance());
  }

  public static boolean checkSdkRoots(@NotNull Sdk sdk, @NotNull IAndroidTarget target, boolean forMaven) {
    String homePath = sdk.getHomePath();
    if (homePath == null) {
      return false;
    }
    AndroidSdkAdditionalData sdkAdditionalData = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
    Sdk javaSdk = sdkAdditionalData != null ? sdkAdditionalData.getJavaSdk() : null;
    if (javaSdk == null) {
      return false;
    }
    Set<VirtualFile> filesInSdk = Sets.newHashSet(sdk.getRootProvider().getFiles(OrderRootType.CLASSES));

    for (VirtualFile file : getPlatformAndAddOnJars(target)) {
      if (filesInSdk.contains(file) == forMaven) {
        return false;
      }
    }
    boolean containsJarFromJdk = false;

    for (VirtualFile file : javaSdk.getRootProvider().getFiles(OrderRootType.CLASSES)) {
      if (file.getFileType() instanceof ArchiveFileType && filesInSdk.contains(file)) {
        containsJarFromJdk = true;
      }
    }
    return containsJarFromJdk == forMaven;
  }

  @Nullable
  public static AndroidDebugBridge getDebugBridge(@NotNull Project project) {
    final List<AndroidFacet> facets = ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID);
    for (AndroidFacet facet : facets) {
      final AndroidDebugBridge debugBridge = facet.getDebugBridge();
      if (debugBridge != null) {
        return debugBridge;
      }
    }
    return null;
  }

  public static boolean activateDdmsIfNecessary(@NotNull Project project, @NotNull Computable<AndroidDebugBridge> bridgeProvider) {
    if (AndroidEnableAdbServiceAction.isAdbServiceEnabled()) {
      AndroidDebugBridge bridge = bridgeProvider.compute();
      if (bridge != null && isDdmsCorrupted(bridge)) {
        LOG.info("DDMLIB is corrupted and will be restarted");
        restartDdmlib(project);
      }
    }
    else {
      final OSProcessHandler ddmsProcessHandler = AndroidRunDdmsAction.getDdmsProcessHandler();
      if (ddmsProcessHandler != null) {
        int r = Messages.showYesNoDialog(project, "Monitor will be closed to enable ADB integration. Continue?", "ADB Integration",
                                         Messages.getQuestionIcon());
        if (r != Messages.YES) {
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
        AndroidEnableAdbServiceAction.setAdbServiceEnabled(project, true);
        return true;
      }

      int result = Messages.showYesNoDialog(project, AndroidBundle.message("android.ddms.disabled.error"),
                                            AndroidBundle.message("android.ddms.disabled.dialog.title"),
                                            Messages.getQuestionIcon());
      if (result != Messages.YES) {
        return false;
      }
      AndroidEnableAdbServiceAction.setAdbServiceEnabled(project, true);
    }
    return true;
  }

  public static boolean canDdmsBeCorrupted(@NotNull AndroidDebugBridge bridge) {
    return isDdmsCorrupted(bridge) || allDevicesAreEmpty(bridge);
  }

  private static boolean allDevicesAreEmpty(@NotNull AndroidDebugBridge bridge) {
    for (IDevice device : bridge.getDevices()) {
      if (device.getClients().length > 0) {
        return false;
      }
    }
    return true;
  }

  public static boolean isDdmsCorrupted(@NotNull AndroidDebugBridge bridge) {
    // TODO: find other way to check if debug service is available

    IDevice[] devices = bridge.getDevices();
    if (devices.length > 0) {
      for (IDevice device : devices) {
        Client[] clients = device.getClients();

        if (clients.length > 0) {
          ClientData clientData = clients[0].getClientData();
          return clientData.getVmIdentifier() == null;
        }
      }
    }
    return false;
  }

  public static void restartDdmlib(@NotNull Project project) {
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(
      AndroidToolWindowFactory.TOOL_WINDOW_ID);
    boolean hidden = false;
    if (toolWindow != null && toolWindow.isVisible()) {
      hidden = true;
      toolWindow.hide(null);
    }
    AndroidSdkData.terminateDdmlib();
    if (hidden) {
      toolWindow.show(null);
    }
  }

  public static boolean isAndroidSdkAvailable() {
    return tryToChooseAndroidSdk() != null;
  }

  @Nullable
  public static SdkManager tryToChooseAndroidSdk() {
    if (ourSdkManager == null) {
      ILogger logger = new StdLogger(StdLogger.Level.INFO);
      for (String s : getAndroidSdkPathsFromExistingPlatforms()) {
        ourSdkManager = SdkManager.createManager(s, logger);
        if (ourSdkManager != null) {
          break;
        }
      }
    }
    return ourSdkManager;
  }
}
