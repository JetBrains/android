/*
 * Copyright (C) 2013 The Android Open Source Project
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
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.dependency.Dependency;
import com.android.tools.idea.gradle.dependency.LibraryDependency;
import com.android.tools.idea.gradle.dependency.ModuleDependency;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.project.AndroidGradleNotification;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * Sets the dependencies of a module imported from an {@link com.android.builder.model.AndroidProject}.
 */
public class DependenciesModuleCustomizer implements ModuleCustomizer {
  private static final Logger LOG = Logger.getInstance(DependenciesModuleCustomizer.class);

  @Override
  public void customizeModule(@NotNull Module module, @NotNull Project project, @Nullable IdeaAndroidProject androidProject) {
    if (androidProject == null) {
      return;
    }
    List<String> errorsFound = Lists.newArrayList();

    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    ModifiableRootModel model = moduleRootManager.getModifiableModel();
    try {
      // remove existing dependencies.
      DependencyRemover dependencyRemover = new DependencyRemover(model);
      for (OrderEntry orderEntry : model.getOrderEntries()) {
        orderEntry.accept(dependencyRemover, null);
      }

      Collection<Dependency> dependencies = Dependency.extractFrom(androidProject);
      setUpDependencies(model, dependencies, errorsFound);
    }
    finally {
      model.commit();
    }

    if (!errorsFound.isEmpty()) {
      StringBuilder msgBuilder = new StringBuilder();
      for (String error : errorsFound) {
        msgBuilder.append("  - ").append(error).append("\n");
      }
      AndroidGradleNotification notification = AndroidGradleNotification.getInstance(project);
      String title = String.format("Error(s) found while populating dependencies of module '%1$s'.", module.getName());
      notification.showBalloon(title, msgBuilder.toString(), NotificationType.ERROR);
    }
  }

  private static void setUpDependencies(@NotNull ModifiableRootModel model,
                                        @NotNull Collection<Dependency> dependencies,
                                        @NotNull List<String> errorsFound) {
    for (Dependency dependency : dependencies) {
      if (dependency instanceof LibraryDependency) {
        updateDependency(model, (LibraryDependency)dependency);
      }
      else if (dependency instanceof ModuleDependency) {
        updateDependency(model, (ModuleDependency)dependency, errorsFound);
      }
      else {
        // This will NEVER happen.
        String description = dependency == null ? ": null" : "type: " + dependency.getClass().getName();
        throw new IllegalArgumentException("Unsupported dependency " + description);
      }
    }
  }

  private static void updateDependency(@NotNull ModifiableRootModel model, @NotNull LibraryDependency dependency) {
    LibraryTable libraryTable = ProjectLibraryTable.getInstance(model.getProject());
    Library library = libraryTable.getLibraryByName(dependency.getName());
    if (library == null) {
      // Create library.
      LibraryTable.ModifiableModel libraryTableModel = libraryTable.getModifiableModel();
      try {
        library = libraryTableModel.createLibrary(dependency.getName());
        updateLibraryPaths(library, dependency);
      }
      finally {
        libraryTableModel.commit();
      }
    }
    LibraryOrderEntry orderEntry = model.addLibraryEntry(library);
    orderEntry.setScope(dependency.getScope());
    orderEntry.setExported(true);
  }

  private static void updateLibraryPaths(@NotNull Library library, @NotNull LibraryDependency dependency) {
    Library.ModifiableModel libraryModel = library.getModifiableModel();
    try {
      Collection<String> binaryPaths = dependency.getPaths(LibraryDependency.PathType.BINARY);
      for (String binaryPath : binaryPaths) {
        File file = new File(binaryPath);

        boolean isJarFile = FileUtilRt.extensionEquals(file.getName(), SdkConstants.EXT_JAR);
        // .jar files require an URL with "jar" protocol.
        String protocol = isJarFile ? StandardFileSystems.JAR_PROTOCOL : StandardFileSystems.FILE_PROTOCOL;
        String filePath = FileUtil.toSystemIndependentName(file.getPath());
        String url = VirtualFileManager.constructUrl(protocol, filePath);
        if (isJarFile) {
          url += StandardFileSystems.JAR_SEPARATOR;
        }

        libraryModel.addRoot(url, OrderRootType.CLASSES);
      }
    }
    finally {
      libraryModel.commit();
    }
  }

  private static void updateDependency(@NotNull ModifiableRootModel model,
                                       @NotNull ModuleDependency dependency,
                                       @NotNull List<String> errorsFound) {
    ModuleManager moduleManager = ModuleManager.getInstance(model.getProject());
    Module moduleDependency = null;
    for (Module module : moduleManager.getModules()) {
      AndroidGradleFacet androidGradleFacet = AndroidGradleFacet.getInstance(module);
      if (androidGradleFacet != null) {
        String gradlePath = androidGradleFacet.getConfiguration().GRADLE_PROJECT_PATH;
        if (Objects.equal(gradlePath, dependency.getGradlePath())) {
          moduleDependency = module;
          break;
        }
      }
    }
    if (moduleDependency != null) {
      ModuleOrderEntry orderEntry = model.addModuleOrderEntry(moduleDependency);
      orderEntry.setExported(true);
      return;
    }

    LibraryDependency backup = dependency.getBackupDependency();
    boolean hasLibraryBackup = backup != null;
    String msg = String.format("Unable fo find module '%1$s'.", dependency.getName());
    if (hasLibraryBackup) {
      msg += String.format(" Linking to library '%1$s' instead.", backup.getName());
    }
    LOG.info(msg);
    errorsFound.add(msg);

    // fall back to library dependency, if available.
    if (hasLibraryBackup) {
      updateDependency(model, backup);
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
