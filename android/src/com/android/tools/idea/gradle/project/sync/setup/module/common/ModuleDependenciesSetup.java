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
package com.android.tools.idea.gradle.project.sync.setup.module.common;

import static com.android.tools.idea.io.FilePaths.pathToIdeaUrl;
import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static com.intellij.openapi.roots.OrderRootType.SOURCES;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import java.io.File;
import java.io.UncheckedIOException;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ModuleDependenciesSetup {
  private static final Logger LOG = Logger.getInstance(ModuleDependenciesSetup.class);

  protected void updateLibraryRootTypePaths(@NotNull Library library,
                                            @NotNull OrderRootType pathType,
                                            @NotNull IdeModifiableModelsProvider modelsProvider,
                                            @NotNull File... paths) {
    if (paths.length == 0) {
      return;
    }
    // We only update paths if the library does not have any already defined.
    Library.ModifiableModel libraryModel = modelsProvider.getModifiableLibraryModel(library);
    for (File path : paths) {
      libraryModel.addRoot(pathToIdeaUrl(path), pathType);
    }
  }

  protected void addLibraryAsDependency(@NotNull Library library,
                                        @NotNull String libraryName,
                                        @NotNull DependencyScope scope,
                                        @NotNull Module module,
                                        @NotNull IdeModifiableModelsProvider modelsProvider,
                                        boolean exported) {
    for (OrderEntry orderEntry : modelsProvider.getModifiableRootModel(module).getOrderEntries()) {
      if (orderEntry instanceof LibraryOrderEntry) {
        Library entryLibrary = ((LibraryOrderEntry)orderEntry).getLibrary();
        DependencyScope entryScope = ((LibraryOrderEntry)orderEntry).getScope();
        if (entryLibrary != null && libraryName.equals(entryLibrary.getName()) && scope.equals(entryScope)) {
          // Dependency already set up.
          return;
        }
      }
    }

    LibraryOrderEntry orderEntry = modelsProvider.getModifiableRootModel(module).addLibraryEntry(library);
    orderEntry.setScope(scope);
    orderEntry.setExported(exported);
    // Make sure library roots are updated in virtual file system.
    updateLibraryRootsInFileSystem(orderEntry);
  }

  private static void updateLibraryRootsInFileSystem(@NotNull LibraryOrderEntry orderEntry) {
    VirtualFileManager manager = VirtualFileManager.getInstance();
    for (OrderRootType type : OrderRootType.getAllTypes()) {
      for (String url : orderEntry.getUrls(type)) {
        VirtualFile file = manager.findFileByUrl(url);
        if (file == null) {
          file = manager.refreshAndFindFileByUrl(url);
        }
        if (file == null && LOG.isDebugEnabled()) {
          LOG.debug(String.format("Can't find %s of the library '%s' at path '%s'", type, orderEntry.getLibraryName(), url));
        }
      }
    }
  }

  /**
   * Check if the cached Library instance is still valid, by comparing the cached classes url and the current
   * binary url. This can be false if a library is recompiled with updated sources but the same version, in which
   * case the artifact will be exploded to a different directory.
   * This method also compares cached javadoc/sources with the expected javadoc/sources. This method returns false
   * if the expected javadoc/sources is not empty while the cached url is empty, or if they point to different locations.
   * <p>
   * If the library has already been created for another module during the current sync, treat library as valid,
   * because recreating it leads to "already disposed" exception when committing the models. Although the library
   * might have different binary paths due to different classpath in modules, using the previous path works because
   * it was returned by the same Gradle Sync invocation.
   */
  protected static boolean isLibraryValid(@NotNull Library.ModifiableModel library,
                                          @NotNull File[] binaryPaths,
                                          @Nullable File javadocJarPath,
                                          @Nullable File sourceJarPath) {
    // The same library model has been setup by previous module. Don't recreate the library to avoid "already disposed" error.
    if (library.isChanged()) {
      return true;
    }

    String[] cachedJavadoc = library.getUrls(JavadocOrderRootType.getInstance());
    String[] cachedSource = library.getUrls(SOURCES);
    if (isNewOrUpdated(cachedJavadoc, javadocJarPath) ||
        isNewOrUpdated(cachedSource, sourceJarPath)) {
      return false;
    }

    String[] cachedUrls = library.getUrls(CLASSES);
    if (cachedUrls.length != binaryPaths.length) {
      return false;
    }

    if (binaryPaths.length == 0) {
      return true;
    }

    // All of the class files are extracted to the same Gradle cache folder, it is sufficient to only check one of the files.
    String newUrl = VfsUtil.getUrlForLibraryRoot(binaryPaths[0]);
    for (String url : cachedUrls) {
      try {
        if (Objects.equals(url, newUrl)) {
          return true;
        }
      }
      catch (UncheckedIOException ignored) {
        return false;
      }
    }
    return false;
  }

  /**
   * Return true if expected file is not null, but cached url is empty, or if they both exist but point to different locations.
   * <p>
   * Note: this method returns false, if expected file is null, but cached url is not empty.
   */
  private static boolean isNewOrUpdated(@NotNull String[] cachedUrls,
                                        @Nullable File expectedFile) {
    // do not remove attached or manually added sources/javadoc if there is nothing to add.
    if (expectedFile == null) {
      return false;
    }
    // The jar is not in cache, but is expected.
    if (cachedUrls.length == 0) {
      return true;
    }
    // Both cache and expected file exist, compare if the expected file is already included.
    String expectedUrl = VfsUtil.getUrlForLibraryRoot(expectedFile);
    for (String url : cachedUrls) {
      if (Objects.equals(url, expectedUrl)) {
        return false;
      }
    }
    return true;
  }
}
