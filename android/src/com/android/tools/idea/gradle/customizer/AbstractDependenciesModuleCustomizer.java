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

import com.android.SdkConstants;
import com.android.tools.idea.gradle.messages.AbstractNavigatable;
import com.android.tools.idea.gradle.messages.Message;
import com.android.tools.idea.gradle.messages.ProjectSyncMessages;
import com.android.tools.idea.gradle.service.notification.SearchInBuildFilesHyperlink;
import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.FD_RES;
import static com.android.SdkConstants.FN_ANNOTATIONS_ZIP;
import static com.android.tools.idea.gradle.messages.CommonMessageGroupNames.UNRESOLVED_ANDROID_DEPENDENCIES;
import static com.android.tools.idea.gradle.messages.CommonMessageGroupNames.UNRESOLVED_DEPENDENCIES;
import static java.io.File.separatorChar;

public abstract class AbstractDependenciesModuleCustomizer<T> implements ModuleCustomizer<T> {
  @Override
  public void customizeModule(@NotNull Module module, @NotNull Project project, @Nullable T model) {
    if (model == null) {
      return;
    }
    List<Message> errorsFound = Lists.newArrayList();

    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    ModifiableRootModel rootModel = moduleRootManager.getModifiableModel();
    try {
      removeExistingDependencies(rootModel);
      setUpDependencies(rootModel, model, errorsFound);
    }
    finally {
      rootModel.commit();
    }
    notifyUser(errorsFound, module);
  }

  protected abstract void setUpDependencies(@NotNull ModifiableRootModel rootModel, @NotNull T model, @NotNull List<Message> errorsFound);

  private static void notifyUser(@NotNull List<Message> errorsFound, @NotNull Module module) {
    if (!errorsFound.isEmpty()) {
      ProjectSyncMessages messages = ProjectSyncMessages.getInstance(module.getProject());
      for (Message error : errorsFound) {
        messages.add(error);
      }
    }
  }

  private static void removeExistingDependencies(@NotNull ModifiableRootModel model) {
    DependencyRemover dependencyRemover = new DependencyRemover(model);
    for (OrderEntry orderEntry : model.getOrderEntries()) {
      orderEntry.accept(dependencyRemover, null);
    }
  }

  protected void setUpLibraryDependency(@NotNull ModifiableRootModel model,
                                        @NotNull String libraryName,
                                        @NotNull DependencyScope scope,
                                        @NotNull Collection<String> binaryPaths) {
    Collection<String> empty = Collections.emptyList();
    setUpLibraryDependency(model, libraryName, scope, binaryPaths, empty, empty);
  }

  protected void setUpLibraryDependency(@NotNull ModifiableRootModel model,
                                        @NotNull String libraryName,
                                        @NotNull DependencyScope scope,
                                        @NotNull Collection<String> binaryPaths,
                                        @NotNull Collection<String> sourcePaths,
                                        @NotNull Collection<String> documentationPaths) {
    LibraryTable libraryTable = ProjectLibraryTable.getInstance(model.getProject());
    Library library = libraryTable.getLibraryByName(libraryName);
    if (library == null) {
      // Create library.
      LibraryTable.ModifiableModel libraryTableModel = libraryTable.getModifiableModel();
      try {
        library = libraryTableModel.createLibrary(libraryName);
        updateLibraryBinaryPaths(library, binaryPaths);
      }
      finally {
        libraryTableModel.commit();
      }
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
    updateLibrarySourcesIfAbsent(library, sourcePaths, OrderRootType.SOURCES);
    updateLibrarySourcesIfAbsent(library, documentationPaths, OrderRootType.DOCUMENTATION);

    // Add external annotations.
    // TODO: Add this to the model instead!
    for (String binaryPath : binaryPaths) {
      if (binaryPath.endsWith(FD_RES) && binaryPath.length() > FD_RES.length() &&
        binaryPath.charAt(binaryPath.length() - FD_RES.length() - 1) == separatorChar) {
        File annotations = new File(binaryPath.substring(0, binaryPath.length() - FD_RES.length()),
                                    FN_ANNOTATIONS_ZIP);
        if (annotations.isFile()) {
          updateLibrarySourcesIfAbsent(library, Collections.singletonList(annotations.getPath()), AnnotationOrderRootType.getInstance());
        }
      }
    }

    LibraryOrderEntry orderEntry = model.addLibraryEntry(library);
    orderEntry.setScope(scope);
    orderEntry.setExported(true);
  }

  private static void updateLibraryBinaryPaths(@NotNull Library library, @NotNull Collection<String> binaryPaths) {
    Library.ModifiableModel libraryModel = library.getModifiableModel();
    try {
      for (String path : binaryPaths) {
        String url = pathToUrl(path);
        libraryModel.addRoot(url, OrderRootType.CLASSES);
      }
    }
    finally {
      libraryModel.commit();
    }
  }

  private static void updateLibrarySourcesIfAbsent(@NotNull Library library,
                                                   @NotNull Collection<String> paths,
                                                   @NotNull OrderRootType pathType) {
    if (paths.isEmpty() || library.getFiles(pathType).length > 0) {
      return;
    }
    // We only update paths if the library does not have any already defined.
    Library.ModifiableModel libraryModel = library.getModifiableModel();
    try {
      for (String path : paths) {
        libraryModel.addRoot(pathToUrl(path), pathType);
      }
    }
    finally {
      libraryModel.commit();
    }
  }

  @NotNull
  public static String pathToUrl(@NotNull String path) {
    File file = new File(path);

    String name = file.getName();
    boolean isJarFile = FileUtilRt.extensionEquals(name, SdkConstants.EXT_JAR) ||
                        FileUtilRt.extensionEquals(name, SdkConstants.EXT_ZIP);
    // .jar files require an URL with "jar" protocol.
    String protocol = isJarFile ? StandardFileSystems.JAR_PROTOCOL : StandardFileSystems.FILE_PROTOCOL;
    String filePath = FileUtil.toSystemIndependentName(file.getPath());
    String url = VirtualFileManager.constructUrl(protocol, filePath);
    if (isJarFile) {
      url += URLUtil.JAR_SEPARATOR;
    }
    return url;
  }

  protected void reportUnresolvedDependencies(@NotNull Collection<String> unresolvedDependencies, @NotNull Project project) {
    ProjectSyncMessages messages = ProjectSyncMessages.getInstance(project);

    for (String dep : unresolvedDependencies) {
      String group = dep.startsWith("com.android.support:") ? UNRESOLVED_ANDROID_DEPENDENCIES : UNRESOLVED_DEPENDENCIES;
      Message msg = new Message(group, Message.Type.ERROR, AbstractNavigatable.NOT_NAVIGATABLE, dep);

      messages.add(msg, new SearchInBuildFilesHyperlink(dep));
    }
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
