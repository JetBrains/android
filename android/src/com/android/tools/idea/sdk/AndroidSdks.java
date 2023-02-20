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

import static com.android.SdkConstants.FD_DOCS;
import static com.android.SdkConstants.FD_DOCS_REFERENCE;
import static com.android.SdkConstants.FD_PKG_SOURCES;
import static com.android.SdkConstants.FD_SOURCES;
import static com.android.SdkConstants.FN_FRAMEWORK_LIBRARY;
import static com.android.sdklib.IAndroidTarget.RESOURCES;
import static com.android.tools.idea.startup.ExternalAnnotationsSupport.attachJdkAnnotations;
import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.refreshAndFindFileByIoFile;
import static com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil.createUniqueSdkName;
import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static com.intellij.openapi.roots.OrderRootType.SOURCES;
import static com.intellij.openapi.util.io.FileUtil.toCanonicalPath;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import static com.intellij.openapi.vfs.JarFileSystem.JAR_SEPARATOR;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtil.refreshAndFindChild;
import static com.android.tools.sdk.AndroidSdkData.getSdkData;
import static org.jetbrains.android.sdk.AndroidSdkType.DEFAULT_EXTERNAL_DOCUMENTATION_URL;
import static org.jetbrains.android.sdk.AndroidSdkType.SDK_NAME;
import static org.jetbrains.android.util.AndroidBuildCommonUtils.ANNOTATIONS_JAR_RELATIVE_PATH;

import com.android.annotations.NonNull;
import com.android.prefs.AndroidLocationsSingleton;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.OptionalLibrary;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.io.FilePaths;
import com.android.tools.idea.progress.StudioLoggerProgressIndicator;
import com.android.tools.sdk.Annotations;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.serviceContainer.NonInjectable;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import com.android.tools.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidPlatforms;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import com.android.tools.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidSdks {
  @NonNls public static final String SDK_NAME_PREFIX = "Android ";

  @NotNull private final IdeInfo myIdeInfo;
  @Nullable private AndroidSdkData mySdkData;

  @NotNull
  public static AndroidSdks getInstance() {
    return ApplicationManager.getApplication().getService(AndroidSdks.class);
  }

  public AndroidSdks() {
    this(IdeInfo.getInstance());
  }

  @NonInjectable
  @VisibleForTesting
  public AndroidSdks(@NotNull IdeInfo ideInfo) {
    myIdeInfo = ideInfo;
  }

  @Nullable
  public Sdk findSuitableAndroidSdk(@NotNull String targetHash) {
    for (Sdk sdk : getAllAndroidSdks()) {
      AndroidSdkAdditionalData originalData = AndroidSdkAdditionalData.from(sdk);
      if (originalData == null) {
        continue;
      }
      if (targetHash.equals(originalData.getBuildTargetHashString())) {
        return sdk;
      }
    }

    return null;
  }

  public void setSdkData(@Nullable AndroidSdkData data) {
    mySdkData = data;
  }

  @NotNull
  public AndroidSdkHandler tryToChooseSdkHandler() {
    AndroidSdkData data = tryToChooseAndroidSdk();
    return data != null ? data.getSdkHandler() : AndroidSdkHandler.getInstance(AndroidLocationsSingleton.INSTANCE, null);
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
      if (myIdeInfo.isAndroidStudio() || myIdeInfo.isGameTools()) {
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
      AndroidPlatform androidPlatform = AndroidPlatforms.getInstance(androidSdk);
      if (androidPlatform != null) {
        // Put default platforms in the list before non-default ones so they'll be looked at first.
        File sdkPath = androidPlatform.getSdkData().getLocationFile();
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
    return ReadAction.compute(() -> ProjectJdkTable.getInstance().getSdksOfType(AndroidSdkType.getInstance()));
  }

  @Nullable
  public Sdk tryToCreate(@NotNull File sdkPath, @NotNull String targetHashString) {
    AndroidSdkData sdkData = getSdkData(sdkPath);
    if (sdkData != null) {
      sdkData.getSdkHandler().getSdkManager(new StudioLoggerProgressIndicator(AndroidSdks.class)).markInvalid();
      IAndroidTarget target = sdkData.findTargetByHashString(targetHashString);
      if (target != null) {
        return create(target, sdkData.getLocationFile(), true /* add roots */);
      }
    }
    return null;
  }

  @Nullable
  public Sdk create(@NotNull IAndroidTarget target, @NotNull File sdkPath, boolean addRoots) {
    return create(target, sdkPath, chooseNameForNewLibrary(target), addRoots);
  }

  @Nullable
  public Sdk create(@NotNull IAndroidTarget target, @NotNull File sdkPath, @NotNull String sdkName, boolean addRoots) {
    int androidPlatformToAutocreate = StudioFlags.ANDROID_PLATFORM_TO_AUTOCREATE.get();
    if (androidPlatformToAutocreate > 0) {
      int actualLevel = target.getVersion().getApiLevel();
      if (actualLevel != androidPlatformToAutocreate) {
        throw new IllegalStateException(String.format(
          Locale.US,
          "Created an Android platform with API-level==%d, but expected to create one with API-level==%d. This could mean that you need to " +
          "manifest an additional dependency.",
          actualLevel, androidPlatformToAutocreate));
      }
    }

    if (!target.getAdditionalLibraries().isEmpty()) {
      // Do not create an IntelliJ SDK for add-ons. Add-ons should be handled as module-level library dependencies.
      // Instead, create the add-on parent, if missing
      String parentHashString = target.getParent() == null ? null : target.getParent().hashString();
      if (parentHashString != null && findSuitableAndroidSdk(parentHashString) == null) {
        tryToCreate(sdkPath, parentHashString);
      }

      return null;
    }

    ProjectJdkTable table = ProjectJdkTable.getInstance();
    String tempName = createUniqueSdkName(SDK_NAME, Arrays.asList(table.getAllJdks()));

    Sdk sdk = table.createSdk(tempName, AndroidSdkType.getInstance());

    SdkModificator sdkModificator = getAndInitialiseSdkModificator(sdk, target);
    sdkModificator.setHomePath(toSystemIndependentName(sdkPath.getPath()));
    setUpSdkAndCommit(sdkModificator, sdkName, Arrays.asList(table.getAllJdks()), addRoots);

    ApplicationManager.getApplication().runWriteAction(() -> ProjectJdkTable.getInstance().addJdk(sdk));
    return sdk;
  }

  public void setUpSdk(@NotNull Sdk androidSdk,
                       @NotNull IAndroidTarget target,
                       @NotNull String sdkName,
                       @NotNull Collection<Sdk> allSdks) {
    setUpSdkAndCommit(getAndInitialiseSdkModificator(androidSdk, target), sdkName, allSdks, true /* add roots */);
  }

  @NotNull
  private static SdkModificator getAndInitialiseSdkModificator(@NotNull Sdk androidSdk,
                                                               @NotNull IAndroidTarget target) {
    SdkModificator sdkModificator = androidSdk.getSdkModificator();
    AndroidSdkAdditionalData data = new AndroidSdkAdditionalData(androidSdk);
    data.setBuildTarget(target);
    sdkModificator.setSdkAdditionalData(data);
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
      List<OrderRoot> newRoots = getLibraryRootsForTarget(target, FilePaths.stringToFile(sdkModificator.getHomePath()), true);
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
    String path = target.getPath(IAndroidTarget.SOURCES).toString();
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
    VirtualFile sdkDir = sdkPath != null ? refreshAndFindFileByIoFile(sdkPath) : null;
    VirtualFile sourcesDir = null;
    if (sdkDir != null) {
      docsOrSourcesFound = addJavaDocAndSources(result, sdkDir) || docsOrSourcesFound;
      sourcesDir = refreshAndFindChild(sdkDir, FD_PKG_SOURCES);
    }

    // todo: replace it by target.getPath(SOURCES) when it'll be up to date
    if (sourcesDir != null && sourcesDir.isDirectory()) {
      VirtualFile platformSourcesDir = refreshAndFindChild(sourcesDir, platformFolder.getName());
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

    String resFolderPath = target.getPath(RESOURCES).toString();
    VirtualFile resFolder = findFileInLocalFileSystem(resFolderPath);
    if (resFolder != null) {
      result.add(new OrderRoot(resFolder, CLASSES));
    }

    // Explicitly add annotations.jar unless the target platform already provides it (API16+).
    if (sdkPath != null && Annotations.needsAnnotationsJarInClasspath(target)) {
      File annotationsJarPath = new File(sdkPath, toSystemDependentName(ANNOTATIONS_JAR_RELATIVE_PATH));
      VirtualFile annotationsJar = findFileInJarFileSystem(annotationsJarPath.getPath());
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
      VirtualFile androidJar = refreshAndFindChild(platformFolder, FN_FRAMEWORK_LIBRARY);
      if (androidJar != null) {
        Path androidJarPath = androidJar.toNioPath();
        VirtualFile androidJarRoot = findFileInJarFileSystem(androidJarPath.toString());
        if (androidJarRoot != null) {
          result.add(androidJarRoot);
        }

        List<OptionalLibrary> libraries = target.getAdditionalLibraries();
        for (OptionalLibrary library : libraries) {
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
  private VirtualFile getRoot(@NotNull OptionalLibrary library) {
    Path jar = library.getJar();
    if (jar != null) {
      return findFileInJarFileSystem(jar.toString());
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

    VirtualFile sourcesFolder = refreshAndFindChild(sdkFolder, FD_SOURCES);
    if (sourcesFolder != null) {
      orderRoots.add(new OrderRoot(sourcesFolder, SOURCES));
      found = true;
    }
    return found;
  }

  @Nullable
  private static VirtualFile findJavadocFolder(@NotNull VirtualFile folder) {
    VirtualFile docsFolder = refreshAndFindChild(folder, FD_DOCS);
    return docsFolder != null ? refreshAndFindChild(docsFolder, FD_DOCS_REFERENCE) : null;
  }

  @Nullable
  private static VirtualFile findFileInLocalFileSystem(@NotNull String path) {
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(path));
  }

  @Nullable
  private static VirtualFile findFileInJarFileSystem(@NotNull String path) {
    String canonicalPath = toCanonicalPath(path);
    return JarFileSystem.getInstance().refreshAndFindFileByPath(canonicalPath + JAR_SEPARATOR);
  }

  @NotNull
  public String chooseNameForNewLibrary(@NotNull IAndroidTarget target) {
    if (target.isPlatform()) {
      return SDK_NAME_PREFIX + target.getVersion() + " Platform";
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
   * Refresh the library {@link VirtualFile}s in the given {@link Sdk}.
   * <p>
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
