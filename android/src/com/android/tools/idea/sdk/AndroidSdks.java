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
package com.android.tools.idea.sdk;

import com.android.annotations.NonNull;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

import static com.android.SdkConstants.*;
import static com.android.sdklib.IAndroidTarget.RESOURCES;
import static com.android.tools.idea.io.FilePaths.toSystemDependentPath;
import static com.android.tools.idea.io.FilePaths.pathToIdeaUrl;
import static com.android.tools.idea.startup.ExternalAnnotationsSupport.attachJdkAnnotations;
import static com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil.createUniqueSdkName;
import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static com.intellij.openapi.roots.OrderRootType.SOURCES;
import static com.intellij.openapi.util.io.FileUtil.*;
import static com.intellij.openapi.vfs.JarFileSystem.JAR_SEPARATOR;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.jetbrains.android.sdk.AndroidSdkData.getSdkData;
import static org.jetbrains.android.sdk.AndroidSdkType.DEFAULT_EXTERNAL_DOCUMENTATION_URL;
import static org.jetbrains.android.sdk.AndroidSdkType.SDK_NAME;
import static org.jetbrains.android.util.AndroidCommonUtils.ANNOTATIONS_JAR_RELATIVE_PATH;

public class AndroidSdks {
  @NonNls public static final String SDK_NAME_PREFIX = "Android ";

  @NotNull private final IdeInfo myIdeInfo;
  @NotNull private final Jdks myJdks;

  @Nullable private AndroidSdkData mySdkData;

  @NotNull
  public static AndroidSdks getInstance() {
    return ServiceManager.getService(AndroidSdks.class);
  }

  public AndroidSdks(@NotNull Jdks jdks, @NotNull IdeInfo ideInfo) {
    myIdeInfo = ideInfo;
    myJdks = jdks;
  }

  @Nullable
  public File findPathOfSdkWithoutAddonsFolder(@NotNull Project project) {
    if (myIdeInfo.isAndroidStudio()) {
      File sdkPath = IdeSdks.getInstance().getAndroidSdkPath();
      if (sdkPath != null && isMissingAddonsFolder(sdkPath)) {
        return sdkPath;
      }
    }
    else {
      ModuleManager moduleManager = ModuleManager.getInstance(project);
      for (Module module : moduleManager.getModules()) {
        Sdk moduleSdk = ModuleRootManager.getInstance(module).getSdk();
        if (moduleSdk != null && isAndroidSdk(moduleSdk)) {
          String homePath = moduleSdk.getHomePath();
          if (homePath != null) {
            File sdkHomePath = toSystemDependentPath(homePath);
            if (isMissingAddonsFolder(sdkHomePath)) {
              return sdkHomePath;
            }
          }
        }
      }
    }
    return null;
  }

  private static boolean isMissingAddonsFolder(@NotNull File sdkHomePath) {
    File addonsFolder = new File(sdkHomePath, FD_ADDONS);
    return !addonsFolder.isDirectory() || notNullize(addonsFolder.listFiles()).length == 0;
  }

  @Nullable
  public Sdk findSuitableAndroidSdk(@NotNull String targetHash) {
    Set<String> foundSdkHomePaths = new HashSet<>();
    List<Sdk> notCompatibleSdks = new ArrayList<>();

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
  public AndroidSdkAdditionalData getAndroidSdkAdditionalData(@NotNull Sdk sdk) {
    SdkAdditionalData data = sdk.getSdkAdditionalData();
    return data instanceof AndroidSdkAdditionalData ? (AndroidSdkAdditionalData)data : null;
  }

  public void setSdkData(@Nullable AndroidSdkData data) {
    mySdkData = data;
  }

  @NotNull
  public AndroidSdkHandler tryToChooseSdkHandler() {
    AndroidSdkData data = tryToChooseAndroidSdk();
    return data != null ? data.getSdkHandler() : AndroidSdkHandler.getInstance(null);
  }

  /**
   * Determines if the specified {@link Sdk} has valid docs for the Android Platform specified by the given {@link IAndroidTarget}. If
   * docs are installed for that platform locally then we check that there is at least one reference to the local documentation in the
   * sdk roots. If not then we check that there is at least one link to the web docs in the sdk roots.
   */
  public boolean hasValidDocs(@NotNull Sdk sdk, @NotNull IAndroidTarget target) {
    File javaDocPath = findJavadocFolder(new File(getPlatformPath(target)));

    if (javaDocPath == null) {
      File sdkDir = toSystemDependentPath(sdk.getHomePath());
      if (sdkDir != null) {
        javaDocPath = findJavadocFolder(sdkDir);
      }
    }

    String javaDocUrl = javaDocPath == null ? DEFAULT_EXTERNAL_DOCUMENTATION_URL : pathToIdeaUrl(javaDocPath);
    for (String rootUrl : sdk.getRootProvider().getUrls(JavadocOrderRootType.getInstance())) {
      // We can't tell if Urls that don't match are valid or not, all we can check is whether there is at least one valid link to
      // the documentation we know about.
      if (javaDocUrl.equals(rootUrl)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the {@link AndroidSdkData} for the current SDK.
   *
   * @return the {@link AndroidSdkData} for the current SDK, or {@code null} during the first run or if an error occurred when setting up
   * the SDK.
   */
  @Nullable
  public AndroidSdkData tryToChooseAndroidSdk() {
    if (mySdkData == null) {
      if (myIdeInfo.isAndroidStudio()) {
        // TODO fix circular dependency between IdeSdks and AndroidSdks
        File path = IdeSdks.getInstance().getAndroidSdkPath();
        if (path != null) {
          mySdkData = getSdkData(path);
          if (mySdkData != null) {
            return mySdkData;
          }
        }
      }

      for (File path : getAndroidSdkPathsFromExistingPlatforms()) {
        mySdkData = getSdkData(path);
        if (mySdkData != null) {
          break;
        }
      }
    }
    return mySdkData;
  }

  @NotNull
  public Collection<File> getAndroidSdkPathsFromExistingPlatforms() {
    List<File> result = new ArrayList<>();
    for (Sdk androidSdk : getAllAndroidSdks()) {
      AndroidPlatform androidPlatform = AndroidPlatform.getInstance(androidSdk);
      if (androidPlatform != null) {
        // Put default platforms in the list before non-default ones so they'll be looked at first.
        File sdkPath = androidPlatform.getSdkData().getLocation();
        if (result.contains(sdkPath)) {
          continue;
        }
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
  public List<Sdk> getAllAndroidSdks() {
    List<Sdk> allSdks = ProjectJdkTable.getInstance().getSdksOfType(AndroidSdkType.getInstance());
    return allSdks != null ? allSdks : Collections.emptyList();
  }

  @Nullable
  public Sdk tryToCreate(@NotNull File sdkPath, @NotNull String targetHashString) {
    AndroidSdkData sdkData = getSdkData(sdkPath);
    if (sdkData != null) {
      sdkData.getSdkHandler().getSdkManager(new StudioLoggerProgressIndicator(AndroidSdks.class)).markInvalid();
      IAndroidTarget target = sdkData.findTargetByHashString(targetHashString);
      if (target != null) {
        return create(target, sdkData.getLocation(), true /* add roots */);
      }
    }
    return null;
  }

  @Nullable
  public Sdk create(@NotNull IAndroidTarget target, @NotNull File sdkPath, boolean addRoots) {
    Sdk jdk = myJdks.chooseOrCreateJavaSdk();
    return jdk != null ? create(target, sdkPath, jdk, addRoots) : null;
  }

  @Nullable
  public Sdk create(@NotNull IAndroidTarget target, @NotNull File sdkPath, @NotNull Sdk jdk, boolean addRoots) {
    return create(target, sdkPath, chooseNameForNewLibrary(target), jdk, addRoots);
  }

  @Nullable
  public Sdk create(@NotNull IAndroidTarget target, @NotNull File sdkPath, @NotNull String sdkName, @NotNull Sdk jdk, boolean addRoots) {
    if (!target.getAdditionalLibraries().isEmpty()) {
      // Do not create an IntelliJ SDK for add-ons. Add-ons should be handled as module-level library dependencies.
      return null;
    }

    ProjectJdkTable table = ProjectJdkTable.getInstance();
    String tempName = createUniqueSdkName(SDK_NAME, Arrays.asList(table.getAllJdks()));

    Sdk sdk = table.createSdk(tempName, AndroidSdkType.getInstance());

    SdkModificator sdkModificator = getAndInitialiseSdkModificator(sdk, target, jdk);
    sdkModificator.setHomePath(sdkPath.getPath());
    setUpSdkAndCommit(sdkModificator, sdkName, Arrays.asList(table.getAllJdks()), addRoots);

    ApplicationManager.getApplication().runWriteAction(() -> ProjectJdkTable.getInstance().addJdk(sdk));
    return sdk;
  }

  public void setUpSdk(@NotNull Sdk androidSdk,
                       @NotNull IAndroidTarget target,
                       @NotNull String sdkName,
                       @NotNull Collection<Sdk> allSdks,
                       @Nullable Sdk jdk) {
    setUpSdkAndCommit(getAndInitialiseSdkModificator(androidSdk, target, jdk), sdkName, allSdks, true /* add roots */);
  }

  @NotNull
  private static SdkModificator getAndInitialiseSdkModificator(@NotNull Sdk androidSdk,
                                                               @NotNull IAndroidTarget target,
                                                               @Nullable Sdk jdk) {
    SdkModificator sdkModificator = androidSdk.getSdkModificator();
    AndroidSdkAdditionalData data = new AndroidSdkAdditionalData(androidSdk, jdk);
    data.setBuildTarget(target);
    sdkModificator.setSdkAdditionalData(data);
    if (jdk != null) {
      sdkModificator.setVersionString(jdk.getVersionString());
    }
    return sdkModificator;
  }

  private void setUpSdkAndCommit(@NotNull SdkModificator sdkModificator,
                                 @NotNull String sdkName,
                                 @NotNull Collection<Sdk> allSdks,
                                 boolean addRoots) {
    AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)sdkModificator.getSdkAdditionalData();
    assert data != null;
    AndroidSdkData androidSdkData = getSdkData(sdkModificator.getHomePath());
    assert androidSdkData != null;
    IAndroidTarget target = data.getBuildTarget(androidSdkData);
    assert target != null;

    String name = createUniqueSdkName(sdkName, allSdks);
    sdkModificator.setName(name);

    if (addRoots) {
      List<OrderRoot> newRoots = getLibraryRootsForTarget(target, toSystemDependentPath(sdkModificator.getHomePath()), true);
      sdkModificator.removeAllRoots();
      for (OrderRoot orderRoot : newRoots) {
        sdkModificator.addRoot(orderRoot.getFile(), orderRoot.getType());
      }
      findAndSetPlatformSources(target, sdkModificator);

      // TODO move this method to Jdks.
      attachJdkAnnotations(sdkModificator);
    }
    sdkModificator.commitChanges();
  }

  public void findAndSetPlatformSources(@NotNull IAndroidTarget target, @NotNull SdkModificator sdkModificator) {
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

  /**
   * Finds the root source code folder for the given android target, if any
   */
  @Nullable
  public File findPlatformSources(@NotNull IAndroidTarget target) {
    String path = target.getPath(IAndroidTarget.SOURCES);
    if (path != null) {
      File platformSource = new File(path);
      if (platformSource.isDirectory()) {
        return platformSource;
      }
    }
    return null;
  }

  @NotNull
  public List<OrderRoot> getLibraryRootsForTarget(@NotNull IAndroidTarget target,
                                                  @Nullable File sdkPath,
                                                  boolean addPlatformAndAddOnJars) {
    List<OrderRoot> result = new ArrayList<>();

    if (addPlatformAndAddOnJars) {
      for (VirtualFile file : getPlatformAndAddOnJars(target)) {
        result.add(new OrderRoot(file, CLASSES));
      }
    }
    VirtualFile platformFolder = getPlatformFolder(target);
    if (platformFolder == null) {
      return result;
    }

    VirtualFile targetDir = platformFolder;
    if (!target.isPlatform()) {
      targetDir = findFileInLocalFileSystem(target.getLocation());
    }
    boolean docsOrSourcesFound = false;

    if (targetDir != null) {
      docsOrSourcesFound = addJavaDocAndSources(result, targetDir);
    }
    VirtualFile sdkDir = sdkPath != null ? findFileInLocalFileSystem(sdkPath.getPath()) : null;
    VirtualFile sourcesDir = null;
    if (sdkDir != null) {
      docsOrSourcesFound = addJavaDocAndSources(result, sdkDir) || docsOrSourcesFound;
      sourcesDir = sdkDir.findChild(FD_PKG_SOURCES);
    }

    // todo: replace it by target.getPath(SOURCES) when it'll be up to date
    if (sourcesDir != null && sourcesDir.isDirectory()) {
      VirtualFile platformSourcesDir = sourcesDir.findChild(platformFolder.getName());
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
      File annotationsJarPath = new File(sdkPath, toSystemDependentName(ANNOTATIONS_JAR_RELATIVE_PATH));
      VirtualFile annotationsJar = findFileInJarFileSystem(annotationsJarPath);
      if (annotationsJar != null) {
        result.add(new OrderRoot(annotationsJar, CLASSES));
      }
    }

    return result;
  }

  @NotNull
  public List<VirtualFile> getPlatformAndAddOnJars(@NotNull IAndroidTarget target) {
    List<VirtualFile> result = new ArrayList<>();

    VirtualFile platformFolder = getPlatformFolder(target);
    if (platformFolder != null) {
      VirtualFile androidJar = platformFolder.findChild(FN_FRAMEWORK_LIBRARY);
      if (androidJar != null) {
        File androidJarPath = virtualToIoFile(androidJar);
        VirtualFile androidJarRoot = findFileInJarFileSystem(androidJarPath);
        if (androidJarRoot != null) {
          result.add(androidJarRoot);
        }

        List<IAndroidTarget.OptionalLibrary> libraries = target.getAdditionalLibraries();
        for (IAndroidTarget.OptionalLibrary library : libraries) {
          VirtualFile root = getRoot(library);
          if (root != null) {
            result.add(root);
          }
        }
      }
    }

    return result;
  }

  @NotNull
  private static String getPlatformPath(@NotNull IAndroidTarget target) {
    return target.isPlatform() ? target.getLocation() : target.getParent().getLocation();
  }

  @Nullable
  private static VirtualFile getPlatformFolder(@NotNull IAndroidTarget target) {
    String platformPath = getPlatformPath(target);
    return LocalFileSystem.getInstance().refreshAndFindFileByPath(toSystemIndependentName(platformPath));
  }

  @Nullable
  private static VirtualFile getRoot(@NotNull IAndroidTarget.OptionalLibrary library) {
    File jar = library.getJar();
    if (jar != null) {
      return findFileInJarFileSystem(jar);
    }
    return null;
  }

  private static boolean addJavaDocAndSources(@NotNull List<OrderRoot> orderRoots, @NotNull VirtualFile sdkFolder) {
    boolean found = false;

    VirtualFile javadocFolder = findJavadocFolder(sdkFolder);
    if (javadocFolder != null) {
      orderRoots.add(new OrderRoot(javadocFolder, JavadocOrderRootType.getInstance()));
      found = true;
    }

    VirtualFile sourcesFolder = sdkFolder.findChild(FD_SOURCES);
    if (sourcesFolder != null) {
      orderRoots.add(new OrderRoot(sourcesFolder, SOURCES));
      found = true;
    }
    return found;
  }

  @Nullable
  private static VirtualFile findJavadocFolder(@NotNull VirtualFile folder) {
    VirtualFile docsFolder = folder.findChild(FD_DOCS);
    return docsFolder != null ? docsFolder.findChild(FD_DOCS_REFERENCE) : null;
  }

  @Nullable
  private static File findJavadocFolder(@NotNull File folder) {
    File docsFolder = new File(folder, join(FD_DOCS, FD_DOCS_REFERENCE));
    return docsFolder.isDirectory() ? docsFolder : null;
  }

  @Nullable
  private static VirtualFile findFileInLocalFileSystem(@NotNull String path) {
    return LocalFileSystem.getInstance().findFileByPath(toSystemDependentName(path));
  }

  @Nullable
  private static VirtualFile findFileInJarFileSystem(@NotNull File path) {
    String canonicalPath = toCanonicalPath(path.getPath());
    return JarFileSystem.getInstance().findFileByPath(canonicalPath + JAR_SEPARATOR);
  }

  @NotNull
  public String chooseNameForNewLibrary(@NotNull IAndroidTarget target) {
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

  public boolean isAndroidSdk(@NotNull Sdk sdk) {
    return sdk.getSdkType() == AndroidSdkType.getInstance();
  }

  /**
   * Indicates whether annotations.jar needs to be added to the classpath of an Android SDK. annotations.jar is not needed for API 16
   * or newer. The annotations are already included in android.jar.
   */
  public boolean needsAnnotationsJarInClasspath(@NotNull IAndroidTarget target) {
    return target.getVersion().getApiLevel() <= 15;
  }

  /**
   * Refreshes the docs for a given SDK if required
   *
   * After installing or uninstalling docs we need to check if the doc roots need updating to reflect the new status of the installed
   * documentation
   */
  public void refreshDocsIn(@NotNull Sdk sdk) {
    AndroidSdkAdditionalData additionalData = getAndroidSdkAdditionalData(sdk);
    AndroidSdkData sdkData = getSdkData(sdk);
    if (additionalData != null && sdkData != null) {
      IAndroidTarget target = additionalData.getBuildTarget(sdkData);
      if (target != null && !hasValidDocs(sdk, target)) {
        OrderRootType javaDocType = JavadocOrderRootType.getInstance();
        SdkModificator sdkModificator = sdk.getSdkModificator();

        List<OrderRoot> newRoots = getLibraryRootsForTarget(target, toSystemDependentPath(sdkModificator.getHomePath()), true);
        sdkModificator.removeRoots(javaDocType);
        for (OrderRoot orderRoot : newRoots) {
          if (orderRoot.getType() == javaDocType) {
            sdkModificator.addRoot(orderRoot.getFile(), orderRoot.getType());
          }
        }

        sdkModificator.commitChanges();
      }
    }
  }

  /**
   * Refresh the library {@link VirtualFile}s in the given {@link Sdk}.
   *
   * After changes to installed Android SDK components, the contents of the {@link Sdk}s do not automatically get refreshed.
   * The referenced {@link VirtualFile}s can be obsolete, new files may be created, or files may be deleted. The result is that
   * references to Android classes may not be found in editors.
   * Removing and adding the libraries effectively refreshes the contents of the IDEA SDK, and references in editors work again.
   */
  public void refreshLibrariesIn(@NotNull Sdk sdk) {
    VirtualFile[] libraries = sdk.getRootProvider().getFiles(CLASSES);
    replaceLibraries(sdk, libraries);
  }

  @VisibleForTesting
  void replaceLibraries(@NotNull Sdk sdk, @NotNull VirtualFile[] libraries) {
    SdkModificator sdkModificator = sdk.getSdkModificator();
    sdkModificator.removeRoots(CLASSES);

    for (VirtualFile library : libraries) {
      sdkModificator.addRoot(library, CLASSES);
    }
    sdkModificator.commitChanges();
  }

  public boolean isInAndroidSdk(@NonNull PsiElement element) {
    VirtualFile file = getVirtualFile(element);
    if (file == null) {
      return false;
    }

    ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(element.getProject()).getFileIndex();
    List<OrderEntry> entries = projectFileIndex.getOrderEntriesForFile(file);
    for (OrderEntry entry : entries) {
      if (entry instanceof JdkOrderEntry) {
        Sdk sdk = ((JdkOrderEntry)entry).getJdk();

        if (sdk != null && sdk.getSdkType() instanceof AndroidSdkType) {
          return true;
        }
      }
    }
    return false;
  }

  @Nullable
  private static VirtualFile getVirtualFile(@NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    return file != null ? file.getVirtualFile() : null;
  }
}
