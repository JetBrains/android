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

package org.jetbrains.android.facet;

import com.android.SdkConstants;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.OrderedSet;
import org.jetbrains.android.compiler.AndroidDexCompiler;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.android.tools.idea.gradle.util.GradleUtil.getOutput;
import static com.android.tools.idea.gradle.util.PropertiesUtil.getProperties;
import static com.intellij.openapi.util.io.FileUtil.getRelativePath;
import static com.intellij.openapi.util.io.FileUtil.*;
import static com.intellij.openapi.vfs.VfsUtilCore.isAncestor;
import static com.intellij.openapi.vfs.VfsUtilCore.*;
import static org.jetbrains.android.compiler.AndroidCompileUtil.getOutputPackage;
import static org.jetbrains.android.maven.AndroidMavenUtil.isMavenizedModule;
import static org.jetbrains.android.sdk.AndroidSdkUtils.isAndroidSdk;
import static org.jetbrains.android.util.AndroidCommonUtils.ANNOTATIONS_JAR_RELATIVE_PATH;
import static org.jetbrains.android.util.AndroidCommonUtils.CLASSES_JAR_FILE_NAME;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidRootUtil {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.facet.AndroidRootUtil");
  @NonNls public static final String DEFAULT_PROPERTIES_FILE_NAME = "default.properties";

  private AndroidRootUtil() {
  }

  /**
   * Returns the main manifest file of the module.
   *
   * @deprecated Modules can have multiple manifests. If you really want the main manifest
   * of the module, use {@link #getPrimaryManifestFile(AndroidFacet)}, but to test if
   * a given file is a manifest, or to process all of them, use
   * {@link IdeaSourceProvider#isManifestFile(AndroidFacet, VirtualFile)} or
   * {@link IdeaSourceProvider#getManifestFiles(AndroidFacet)}.
   */
  @Nullable
  @Deprecated
  public static VirtualFile getManifestFile(@NotNull AndroidFacet facet) {
    if (facet.requiresAndroidModel()) {
      return facet.getMainIdeaSourceProvider().getManifestFile();
    }
    return getFileByRelativeModulePath(facet.getModule(), facet.getProperties().MANIFEST_FILE_RELATIVE_PATH, true);
  }

  /**
   * Returns the main manifest file of the module. Note that a module can have multiple
   * manifests so only use this if you really know you need to only look at the main manifests.
   * To look at all manifests, use  {@link IdeaSourceProvider#getManifestFiles(AndroidFacet)}.
   */
  @Nullable
  public static VirtualFile getPrimaryManifestFile(@NotNull AndroidFacet facet) {
    return facet.getMainIdeaSourceProvider().getManifestFile();
  }

  @Nullable
  public static VirtualFile getCustomManifestFileForCompiler(@NotNull AndroidFacet facet) {
    return getFileByRelativeModulePath(facet.getModule(), facet.getProperties().CUSTOM_COMPILER_MANIFEST, false);
  }

  // DO NOT get PSI or DOM from this file, because it may be excluded (f.ex. it can be in /target/ directory)
  @Nullable
  public static VirtualFile getManifestFileForCompiler(@NotNull AndroidFacet facet) {
    return facet.getProperties().USE_CUSTOM_COMPILER_MANIFEST ? getCustomManifestFileForCompiler(facet) : getPrimaryManifestFile(facet);
  }

  /**
   * @deprecated You must use {@link AndroidFacet#getAllResourceDirectories()} instead
   */
  @Deprecated
  @Nullable
  public static VirtualFile getResourceDir(@NotNull AndroidFacet facet) {
    return facet.getPrimaryResourceDir();
  }

  @Nullable
  private static String suggestResourceDirPath(@NotNull AndroidFacet facet) {
    Module module = facet.getModule();

    VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
    if (contentRoots.length == 0) {
      return null;
    }

    VirtualFile root = contentRoots[0];

    if (contentRoots.length > 1) {
      String moduleFileParentDirPath = toSystemIndependentName(new File(module.getModuleFilePath()).getParent());
      VirtualFile moduleFileParentDir = LocalFileSystem.getInstance().findFileByPath(moduleFileParentDirPath);
      if (moduleFileParentDir != null) {
        for (VirtualFile contentRoot : contentRoots) {
          if (Comparing.equal(contentRoot, moduleFileParentDir)) {
            root = contentRoot;
          }
        }
      }
    }
    return root.getPath() + facet.getProperties().RES_FOLDER_RELATIVE_PATH;
  }

  @Nullable
  public static String getResourceDirPath(@NotNull AndroidFacet facet) {
    VirtualFile resourceDir = getResourceDir(facet);
    return resourceDir != null ? resourceDir.getPath() : suggestResourceDirPath(facet);
  }

  @Nullable
  public static VirtualFile getFileByRelativeModulePath(Module module, String relativePath, boolean lookInContentRoot) {
    if (relativePath == null || relativePath.length() == 0) {
      return null;
    }

    VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();

    String moduleDirPath = new File(module.getModuleFilePath()).getParent();
    if (moduleDirPath != null) {
      String absPath = toSystemIndependentName(moduleDirPath + relativePath);
      VirtualFile file = LocalFileSystem.getInstance().findFileByPath(absPath);
      if (file != null) {
        return file;
      }
    }

    if (lookInContentRoot) {
      for (VirtualFile contentRoot : contentRoots) {
        String absPath = toSystemIndependentName(contentRoot.getPath() + relativePath);
        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(absPath);
        if (file != null) {
          return file;
        }
      }
    }
    return null;
  }

  @Nullable
  public static VirtualFile getAssetsDir(@NotNull AndroidFacet facet) {
    return getFileByRelativeModulePath(facet.getModule(), facet.getProperties().ASSETS_FOLDER_RELATIVE_PATH, false);
  }

  @Nullable
  public static VirtualFile getLibsDir(@NotNull AndroidFacet facet) {
    return getFileByRelativeModulePath(facet.getModule(), facet.getProperties().LIBS_FOLDER_RELATIVE_PATH, false);
  }

  @Nullable
  public static VirtualFile getAidlGenDir(@NotNull AndroidFacet facet) {
    String genPath = getAidlGenSourceRootPath(facet);
    return genPath != null ? LocalFileSystem.getInstance().findFileByPath(genPath) : null;
  }

  @Nullable
  public static VirtualFile getAaptGenDir(@NotNull AndroidFacet facet) {
    String genPath = getAptGenSourceRootPath(facet);
    return genPath != null ? LocalFileSystem.getInstance().findFileByPath(genPath) : null;
  }

  @Nullable
  public static VirtualFile getRenderscriptGenDir(@NotNull AndroidFacet facet) {
    String path = getRenderscriptGenSourceRootPath(facet);
    return path != null ? LocalFileSystem.getInstance().findFileByPath(path) : null;
  }

  @Nullable
  public static VirtualFile getBuildconfigGenDir(@NotNull AndroidFacet facet) {
    String path = getBuildconfigGenSourceRootPath(facet);
    return path != null ? LocalFileSystem.getInstance().findFileByPath(path) : null;
  }

  // works even if there is no Android facet in a module

  @Nullable
  public static VirtualFile getStandardGenDir(@NotNull Module module) {
    return getFileByRelativeModulePath(module, '/' + SdkConstants.FD_GEN_SOURCES, false);
  }

  private static void collectClassFilesAndJars(@NotNull VirtualFile root,
                                               @NotNull Set<VirtualFile> result,
                                               @NotNull Set<VirtualFile> visited) {
    if (!visited.add(root)) {
      return;
    }
    for (VirtualFile child : root.getChildren()) {
      if (child.exists()) {
        if (child.isDirectory()) {
          collectClassFilesAndJars(child, result, visited);
        }
        else if ("jar".equals(child.getExtension()) || "class".equals(child.getExtension())) {
          if (child.getFileSystem() instanceof JarFileSystem) {
            VirtualFile localFile = JarFileSystem.getInstance().getVirtualFileForJar(child);
            if (localFile != null) {
              result.add(localFile);
            }
          }
          else {
            result.add(child);
          }
        }
      }
    }
  }

  private static void fillExternalLibrariesAndModules(@NotNull final Module module,
                                                      @NotNull final Set<VirtualFile> outputDirs,
                                                      @NotNull final Set<Module> visited,
                                                      @Nullable final Set<VirtualFile> libraries,
                                                      final boolean exportedLibrariesOnly,
                                                      final boolean recursive) {
    if (!visited.add(module)) {
      return;
    }
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        ModuleRootManager manager = ModuleRootManager.getInstance(module);
        for (OrderEntry entry : manager.getOrderEntries()) {
          if (!(entry instanceof ExportableOrderEntry) || ((ExportableOrderEntry)entry).getScope() != DependencyScope.COMPILE) {
            continue;
          }
          if (libraries != null && entry instanceof LibraryOrderEntry) {
            LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)entry;
            Library library = libraryOrderEntry.getLibrary();
            if (library != null && (!exportedLibrariesOnly || libraryOrderEntry.isExported())) {
              for (VirtualFile file : library.getFiles(OrderRootType.CLASSES)) {
                if (!file.exists()) {
                  continue;
                }

                if (file.getFileType() instanceof ArchiveFileType) {
                  if (file.getFileSystem() instanceof JarFileSystem) {
                    VirtualFile localFile = JarFileSystem.getInstance().getVirtualFileForJar(file);
                    if (localFile != null) {
                      libraries.add(localFile);
                    }
                  }
                  else {
                    libraries.add(file);
                  }
                }
                else if (file.isDirectory() && !(file.getFileSystem() instanceof JarFileSystem)) {
                  collectClassFilesAndJars(file, libraries, new HashSet<VirtualFile>());
                }
              }
            }
          }
          else if (entry instanceof ModuleOrderEntry) {
            Module depModule = ((ModuleOrderEntry)entry).getModule();
            if (depModule == null) {
              continue;
            }
            AndroidFacet facet = AndroidFacet.getInstance(depModule);
            boolean libraryProject = facet != null && facet.isLibraryProject();

            CompilerModuleExtension extension = CompilerModuleExtension.getInstance(depModule);
            if (extension != null) {
              VirtualFile classDir = extension.getCompilerOutputPath();

              if (libraryProject) {
                VirtualFile tmpArtifactsDir = AndroidDexCompiler.getOutputDirectoryForDex(depModule);

                if (tmpArtifactsDir != null) {
                  VirtualFile packedClassesJar = tmpArtifactsDir.findChild(CLASSES_JAR_FILE_NAME);
                  if (packedClassesJar != null) {
                    outputDirs.add(packedClassesJar);
                  }
                }
              }
              // do not support android-app->android-app compile dependencies
              else if (facet == null && !outputDirs.contains(classDir) && classDir != null && classDir.exists()) {
                outputDirs.add(classDir);
              }
            }
            if (recursive) {
              fillExternalLibrariesAndModules(depModule, outputDirs, visited, libraries, !libraryProject || exportedLibrariesOnly, true);
            }
          }
        }
      }
    });
  }

  @NotNull
  public static List<VirtualFile> getExternalLibraries(Module module) {
    Set<VirtualFile> files = new HashSet<VirtualFile>();
    OrderedSet<VirtualFile> libs = new OrderedSet<VirtualFile>();
    // In a module imported from Maven dependencies are transitive, so we don't need to traverse all dependency tree
    // and compute all jars referred by library modules. Moreover it would be incorrect,
    // because Maven has dependency resolving algorithm based on versioning
    boolean recursive = !isMavenizedModule(module);
    fillExternalLibrariesAndModules(module, files, new HashSet<Module>(), libs, false, recursive);

    addAnnotationsJar(module, libs);
    return libs;
  }

  private static void addAnnotationsJar(@NotNull Module module, @NotNull OrderedSet<VirtualFile> libs) {
    Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    if (sdk == null || !isAndroidSdk(sdk)) {
      return;
    }
    String sdkHomePath = sdk.getHomePath();
    if (sdkHomePath == null) {
      return;
    }
    AndroidPlatform platform = AndroidPlatform.getInstance(module);

    if (platform != null && platform.needToAddAnnotationsJarToClasspath()) {
      String annotationsJarPath = toSystemIndependentName(sdkHomePath) + ANNOTATIONS_JAR_RELATIVE_PATH;
      VirtualFile annotationsJar = LocalFileSystem.getInstance().findFileByPath(annotationsJarPath);

      if (annotationsJar != null) {
        libs.add(annotationsJar);
      }
    }
  }

  @NotNull
  public static Set<VirtualFile> getDependentModules(@NotNull Module module, @NotNull VirtualFile moduleOutputDir) {
    Set<VirtualFile> files = new HashSet<VirtualFile>();
    fillExternalLibrariesAndModules(module, files, new HashSet<Module>(), null, false, true);
    files.remove(moduleOutputDir);
    return files;
  }

  @NotNull
  public static VirtualFile[] getResourceOverlayDirs(@NotNull AndroidFacet facet) {
    List<String> overlayFolders = facet.getProperties().RES_OVERLAY_FOLDERS;
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    for (String overlayFolder : overlayFolders) {
      VirtualFile overlayDir = getFileByRelativeModulePath(facet.getModule(), overlayFolder, true);
      if (overlayDir != null) {
        result.add(overlayDir);
      }
    }
    return toVirtualFileArray(result);
  }

  @Nullable
  public static String getModuleDirPath(Module module) {
    String moduleFilePath = module.getModuleFilePath();
    String moduleDirPath = new File(moduleFilePath).getParent();
    if (moduleDirPath != null) {
      moduleDirPath = toSystemIndependentName(moduleDirPath);
    }
    return moduleDirPath;
  }

  @Nullable
  public static String getRenderscriptGenSourceRootPath(@NotNull AndroidFacet facet) {
    // todo: return correct path for mavenized module when it'll be supported
    return getAidlGenSourceRootPath(facet);
  }

  @Nullable
  public static String getBuildconfigGenSourceRootPath(@NotNull AndroidFacet facet) {
    // todo: return correct path for mavenized module when it'll be supported
    return getAptGenSourceRootPath(facet);
  }

  @Nullable
  public static VirtualFile getMainContentRoot(@NotNull AndroidFacet facet) {
    Module module = facet.getModule();

    VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
    if (contentRoots.length == 0) {
      return null;
    }

    if (contentRoots.length == 1) {
      return contentRoots[0];
    }
    VirtualFile manifestFile = getPrimaryManifestFile(facet);
    if (manifestFile != null) {
      for (VirtualFile root : contentRoots) {
        if (isAncestor(root, manifestFile, true)) {
          return root;
        }
      }
    }
    return contentRoots[0];
  }

  @Nullable
  public static Pair<PropertiesFile, VirtualFile> findPropertyFile(@NotNull Module module, @NotNull String propertyFileName) {
    for (VirtualFile contentRoot : ModuleRootManager.getInstance(module).getContentRoots()) {
      VirtualFile vFile = contentRoot.findChild(propertyFileName);
      if (vFile != null) {
        PsiFile psiFile = AndroidPsiUtils.getPsiFileSafely(module.getProject(), vFile);
        if (psiFile instanceof PropertiesFile) {
          return Pair.create((PropertiesFile)psiFile, vFile);
        }
      }
    }
    return null;
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  @Nullable
  public static Pair<Properties, VirtualFile> readPropertyFile(@NotNull Module module, @NotNull String propertyFileName) {
    for (VirtualFile contentRoot : ModuleRootManager.getInstance(module).getContentRoots()) {
      Pair<Properties, VirtualFile> result = readPropertyFile(contentRoot, propertyFileName);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  @Nullable
  public static Pair<Properties, VirtualFile> readProjectPropertyFile(@NotNull Module module) {
    Pair<Properties, VirtualFile> pair = readPropertyFile(module, SdkConstants.FN_PROJECT_PROPERTIES);
    return pair != null ? pair : readPropertyFile(module, DEFAULT_PROPERTIES_FILE_NAME);
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  @Nullable
  private static Pair<Properties, VirtualFile> readPropertyFile(@NotNull VirtualFile contentRoot, @NotNull String propertyFileName) {
    VirtualFile vFile = contentRoot.findChild(propertyFileName);
    if (vFile != null) {
      try {
        File file = virtualToIoFile(vFile);
        Properties properties = getProperties(file);
        return Pair.create(properties, vFile);
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }
    return null;
  }

  @Nullable
  public static Pair<Properties, VirtualFile> readProjectPropertyFile(@NotNull VirtualFile contentRoot) {
    Pair<Properties, VirtualFile> pair = readPropertyFile(contentRoot, SdkConstants.FN_PROJECT_PROPERTIES);
    return pair != null ? pair : readPropertyFile(contentRoot, DEFAULT_PROPERTIES_FILE_NAME);
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  @Nullable
  public static Pair<String, VirtualFile> getPropertyValue(@NotNull Module module,
                                                           @NotNull String propertyFileName,
                                                           @NotNull String propertyKey) {
    Pair<Properties, VirtualFile> pair = readPropertyFile(module, propertyFileName);
    if (pair != null) {
      String value = pair.first.getProperty(propertyKey);
      if (value != null) {
        return Pair.create(value, pair.second);
      }
    }
    return null;
  }

  @Nullable
  public static Pair<String, VirtualFile> getProjectPropertyValue(@NotNull Module module, @NotNull String propertyName) {
    Pair<String, VirtualFile> result = getPropertyValue(module, SdkConstants.FN_PROJECT_PROPERTIES, propertyName);
    return result != null ? result : getPropertyValue(module, DEFAULT_PROPERTIES_FILE_NAME, propertyName);
  }

  @Nullable
  public static String getAptGenSourceRootPath(@NotNull AndroidFacet facet) {
    String path = facet.getProperties().GEN_FOLDER_RELATIVE_PATH_APT;
    if (path.length() == 0) return null;
    String moduleDirPath = getModuleDirPath(facet.getModule());
    return moduleDirPath != null ? moduleDirPath + path : null;
  }

  @Nullable
  public static String getAidlGenSourceRootPath(@NotNull AndroidFacet facet) {
    String path = facet.getProperties().GEN_FOLDER_RELATIVE_PATH_AIDL;
    if (path.length() == 0) return null;
    String moduleDirPath = getModuleDirPath(facet.getModule());
    return moduleDirPath != null ? moduleDirPath + path : null;
  }

  @Nullable
  public static String getApkPath(@NotNull AndroidFacet facet) {
    if (facet.requiresAndroidModel()) {
      AndroidGradleModel androidGradleModel = AndroidGradleModel.get(facet);
      if (androidGradleModel != null) {
        // For Android-Gradle projects, AndroidModel is not null.
        AndroidArtifact mainArtifact = androidGradleModel.getMainArtifact();
        AndroidArtifactOutput output = getOutput(mainArtifact);
        File outputFile = output.getMainOutputFile().getOutputFile();
        return outputFile.getAbsolutePath();
      } else {
        return null;
      }
    }
    String path = facet.getProperties().APK_PATH;
    if (path.length() == 0) {
      return getOutputPackage(facet.getModule());
    }
    String moduleDirPath = getModuleDirPath(facet.getModule());
    return moduleDirPath != null ? toSystemDependentName(moduleDirPath + path) : null;
  }

  @Nullable
  public static String getPathRelativeToModuleDir(@NotNull Module module, @NotNull String path) {
    String moduleDirPath = getModuleDirPath(module);
    if (moduleDirPath == null) {
      return null;
    }
    if (moduleDirPath.equals(path)) {
      return "";
    }
    return getRelativePath(moduleDirPath, path, '/');
  }
}
