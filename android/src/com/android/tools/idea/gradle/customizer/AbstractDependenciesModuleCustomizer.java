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
package com.android.tools.idea.gradle.customizer;

import com.android.tools.idea.gradle.customizer.dependency.DependencySetupErrors;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.gradle.util.Projects.getDependencySetupErrors;
import static com.android.tools.idea.gradle.util.Projects.setDependencySetupErrors;
import static com.intellij.openapi.roots.OrderRootType.SOURCES;
import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import static com.intellij.openapi.util.io.FileUtilRt.extensionEquals;
import static com.intellij.openapi.vfs.StandardFileSystems.FILE_PROTOCOL;
import static com.intellij.openapi.vfs.StandardFileSystems.JAR_PROTOCOL;
import static com.intellij.openapi.vfs.VirtualFileManager.constructUrl;
import static com.intellij.util.io.URLUtil.JAR_SEPARATOR;
import static java.io.File.separatorChar;

public abstract class AbstractDependenciesModuleCustomizer<T> implements ModuleCustomizer<T> {
  @Override
  public void customizeModule(@NotNull Project project,
                              @NotNull Module module,
                              @NotNull IdeModifiableModelsProvider modelsProvider,
                              @Nullable T externalProjectModel) {
    if (externalProjectModel == null) {
      return;
    }

    final ModifiableRootModel moduleModel = modelsProvider.getModifiableRootModel(module);
    removeExistingDependencies(moduleModel);
    setUpDependencies(module, modelsProvider, externalProjectModel);
  }

  protected abstract void setUpDependencies(@NotNull Module module,
                                            @NotNull IdeModifiableModelsProvider modelsProvider,
                                            @NotNull T model);

  @NotNull
  protected DependencySetupErrors getSetupErrors(@NotNull Project project) {
    DependencySetupErrors setupErrors = getDependencySetupErrors(project);
    if (setupErrors == null) {
      setupErrors = new DependencySetupErrors();
      setDependencySetupErrors(project, setupErrors);
    }
    return setupErrors;
  }

  private static void removeExistingDependencies(@NotNull ModifiableRootModel model) {
    DependencyRemover dependencyRemover = new DependencyRemover(model);
    for (OrderEntry orderEntry : model.getOrderEntries()) {
      orderEntry.accept(dependencyRemover, null);
    }
  }

  protected static void setUpLibraryDependency(@NotNull Module module,
                                               @NotNull IdeModifiableModelsProvider modelsProvider,
                                               @NotNull String libraryName,
                                               @NotNull DependencyScope scope,
                                               @NotNull Collection<String> binaryPaths) {
    Collection<String> empty = Collections.emptyList();
    setUpLibraryDependency(module, modelsProvider, libraryName, scope, binaryPaths, empty, empty);
  }

  protected static void setUpLibraryDependency(@NotNull Module module,
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
          updateLibrarySourcesIfAbsent(library, Collections.singletonList(annotations.getPath()), AnnotationOrderRootType.getInstance(),
                                       modelsProvider);
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

  private static class DependencyRemover extends RootPolicy<Object> {
    @NotNull private final ModifiableRootModel myModel;

    DependencyRemover(@NotNull ModifiableRootModel model) {
      myModel = model;
    }

    @Override
    public Object visitLibraryOrderEntry(LibraryOrderEntry libraryOrderEntry, Object value) {
      myModel.removeOrderEntry(libraryOrderEntry);
      return value;
    }

    @Override
    public Object visitModuleOrderEntry(ModuleOrderEntry moduleOrderEntry, Object value) {
      myModel.removeOrderEntry(moduleOrderEntry);
      return value;
    }
  }
}
