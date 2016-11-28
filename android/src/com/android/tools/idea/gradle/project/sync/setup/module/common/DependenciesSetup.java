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

import com.android.tools.idea.gradle.project.sync.setup.module.SyncLibraryRegistry;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

import static com.android.SdkConstants.*;
import static com.intellij.openapi.roots.OrderRootType.SOURCES;
import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import static com.intellij.openapi.util.io.FileUtilRt.extensionEquals;
import static com.intellij.openapi.vfs.StandardFileSystems.FILE_PROTOCOL;
import static com.intellij.openapi.vfs.StandardFileSystems.JAR_PROTOCOL;
import static com.intellij.openapi.vfs.VirtualFileManager.constructUrl;
import static com.intellij.util.io.URLUtil.JAR_SEPARATOR;
import static java.io.File.separatorChar;

public class DependenciesSetup {
  public void setUpLibraryDependency(@NotNull Module module,
                                     @NotNull IdeModifiableModelsProvider modelsProvider,
                                     @NotNull String libraryName,
                                     @NotNull DependencyScope scope,
                                     @NotNull Collection<String> binaryPaths) {
    Collection<String> empty = Collections.emptyList();
    setUpLibraryDependency(module, modelsProvider, libraryName, scope, binaryPaths, empty, empty);
  }

  public void setUpLibraryDependency(@NotNull Module module,
                                     @NotNull IdeModifiableModelsProvider modelsProvider,
                                     @NotNull String libraryName,
                                     @NotNull DependencyScope scope,
                                     @NotNull Collection<String> binaryPaths,
                                     @NotNull Collection<String> sourcePaths,
                                     @NotNull Collection<String> documentationPaths) {
    Library library = modelsProvider.getLibraryByName(libraryName);
    if (library == null) {
      // Create library.
      library = modelsProvider.createLibrary(libraryName);
      updateLibraryBinaryPaths(library, binaryPaths, modelsProvider);
    }
    else {
      SyncLibraryRegistry registry = SyncLibraryRegistry.getInstance(module.getProject());
      registry.markAsUsed(library);
    }

    // It is common that the same dependency is used by more than one module. Here we update the "sources" and "documentation" paths if they
    // were not set before.

    // Example:
    // In a multi-project project, there are 2 modules: 'app'' (an Android app) and 'util' (a Java lib.) Both of them depend on Guava. Since
    // Android artifacts do not support source attachments, the 'app' module may not indicate where to find the sources for Guava, but the
    // 'util' method can, since it is a plain Java module.
    // If the 'Guava' library was already defined when setting up 'app', it won't have source attachments. When setting up 'util' we may
    // have source attachments, but the library may have been already created. Here we just add the "source" paths if they were not already
    // set.
    updateLibrarySourcesIfAbsent(library, sourcePaths, SOURCES, modelsProvider);
    updateLibrarySourcesIfAbsent(library, documentationPaths, JavadocOrderRootType.getInstance(), modelsProvider);

    // Add external annotations.
    // TODO: Add this to the model instead!
    for (String binaryPath : binaryPaths) {
      if (binaryPath.endsWith(FD_RES) && binaryPath.length() > FD_RES.length() &&
          binaryPath.charAt(binaryPath.length() - FD_RES.length() - 1) == separatorChar) {
        File annotations = new File(binaryPath.substring(0, binaryPath.length() - FD_RES.length()), FN_ANNOTATIONS_ZIP);
        if (annotations.isFile()) {
          updateLibrarySourcesIfAbsent(library, annotations, AnnotationOrderRootType.getInstance(), modelsProvider);
        }
      }
      else if (libraryName.startsWith("support-annotations-") && binaryPath.endsWith(DOT_JAR)) {
        // The support annotations is a Java library, not an Android library, so it's not distributed as an AAR
        // with its own external annotations. However, there are a few that we want to make available in the
        // IDE (for example, code completion on @VisibleForTesting(otherwise = |), so we bundle these in the
        // platform annotations zip file instead. We'll also need to add this as a root here.
        File annotations = new File(binaryPath.substring(0, binaryPath.length() - DOT_JAR.length()) + "-" + FN_ANNOTATIONS_ZIP);
        if (annotations.isFile()) {
          updateLibrarySourcesIfAbsent(library, annotations, AnnotationOrderRootType.getInstance(), modelsProvider);
        }
      }
    }

    for (OrderEntry orderEntry : modelsProvider.getModifiableRootModel(module).getOrderEntries()) {
      if (orderEntry instanceof LibraryOrderEntry) {
        Library entryLibrary = ((LibraryOrderEntry)orderEntry).getLibrary();
        if (entryLibrary != null && libraryName.equals(entryLibrary.getName())) {
          // Dependency already set up.
          return;
        }
      }
    }

    LibraryOrderEntry orderEntry = modelsProvider.getModifiableRootModel(module).addLibraryEntry(library);
    orderEntry.setScope(scope);
    orderEntry.setExported(true);
  }

  private static void updateLibraryBinaryPaths(@NotNull Library library,
                                               @NotNull Collection<String> binaryPaths,
                                               @NotNull IdeModifiableModelsProvider modelsProvider) {
    Library.ModifiableModel libraryModel = modelsProvider.getModifiableLibraryModel(library);
    for (String path : binaryPaths) {
      String url = pathToUrl(path);
      libraryModel.addRoot(url, OrderRootType.CLASSES);
    }
  }

  private static void updateLibrarySourcesIfAbsent(@NotNull Library library,
                                                   @NotNull File path,
                                                   @NotNull OrderRootType pathType,
                                                   @NotNull IdeModifiableModelsProvider modelsProvider) {
    updateLibrarySourcesIfAbsent(library, Collections.singletonList(path.getPath()), pathType, modelsProvider);
  }

  private static void updateLibrarySourcesIfAbsent(@NotNull Library library,
                                                   @NotNull Collection<String> paths,
                                                   @NotNull OrderRootType pathType,
                                                   @NotNull IdeModifiableModelsProvider modelsProvider) {
    if (paths.isEmpty() || library.getFiles(pathType).length > 0) {
      return;
    }
    // We only update paths if the library does not have any already defined.
    Library.ModifiableModel libraryModel = modelsProvider.getModifiableLibraryModel(library);
    for (String path : paths) {
      libraryModel.addRoot(pathToUrl(path), pathType);
    }
  }

  @NotNull
  public static String pathToUrl(@NotNull String path) {
    File file = new File(path);

    String name = file.getName();
    boolean isJarFile = extensionEquals(name, EXT_JAR) || extensionEquals(name, EXT_ZIP);
    // .jar files require an URL with "jar" protocol.
    String protocol = isJarFile ? JAR_PROTOCOL : FILE_PROTOCOL;
    String url = constructUrl(protocol, toSystemIndependentName(file.getPath()));
    if (isJarFile) {
      url += JAR_SEPARATOR;
    }
    return url;
  }
}
