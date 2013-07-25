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

import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.project.AndroidDependencies;
import com.android.tools.idea.gradle.project.AndroidDependencies.DependencyFactory;
import com.android.tools.idea.gradle.util.Facets;
import com.google.common.base.Objects;
import com.google.common.collect.Maps;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;

/**
 * Sets the dependencies of a module imported from an {@link com.android.builder.model.AndroidProject}.
 */
public class DependenciesModuleCustomizer implements ModuleCustomizer {
  private static final Logger LOG = Logger.getInstance(DependenciesModuleCustomizer.class);

  @Override
  public void customizeModule(@NotNull Module module, @NotNull Project project, @Nullable IdeaAndroidProject ideaAndroidProject) {
    if (ideaAndroidProject == null) {
      return;
    }
    // first pass we update existing libraries or import any missing project-level library
    updateProjectLibraries(project, ideaAndroidProject);

    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    ModifiableRootModel model = moduleRootManager.getModifiableModel();
    try {
      removeExistingDependencies(model);
      populateDependencies(model, project, ideaAndroidProject);
    } finally {
      model.commit();
    }
  }

  private static void updateProjectLibraries(@NotNull Project project, @NotNull IdeaAndroidProject ideaAndroidProject) {
    final LibraryTable libraryTable = ProjectLibraryTable.getInstance(project);
    final LibraryTable.ModifiableModel model = libraryTable.getModifiableModel();
    final Map<File, Library> libraries = Maps.newHashMap();
    try {
      AndroidDependencies.populate(ideaAndroidProject, new DependencyFactory() {
        @Override
        public boolean addLibraryDependency(@NotNull DependencyScope scope, @NotNull String name, @NotNull File binaryPath) {
          Library library = libraryTable.getLibraryByName(name);
          if (library == null) {
            library = model.createLibrary(name);
          }
          libraries.put(binaryPath, library);
          return true;
        }

        @Override
        public boolean addModuleDependency(@NotNull DependencyScope scope, @NotNull String name, @NotNull String modulePath) {
          // We don't need to do anything here. We are just setting up libraries.
          return true;
        }
      }, null);
    }
    finally {
      model.commit();
    }
    registerPaths(OrderRootType.CLASSES, libraries);
  }

  private static void registerPaths(@NotNull OrderRootType type, @NotNull Map<File, Library> libraries) {
    for (Map.Entry<File, Library> entry : libraries.entrySet()) {
      Library library = entry.getValue();
      Library.ModifiableModel model = library.getModifiableModel();
      try {
        File file = entry.getKey();
        VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
        if (virtualFile == null) {
          String msg = String.format("Unable to find file at path '%1$s', library '%2$s'", file.getPath(), library.getName());
          LOG.warn(msg);
          continue;
        }
        if (virtualFile.isDirectory()) {
          model.addRoot(virtualFile, type);
          continue;
        }
        VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(virtualFile);
        if (jarRoot == null) {
          String msg = String.format("Unable to parse contents of jar file '%1$s', library '%2$s'", file.getPath(), library.getName());
          LOG.warn(msg);
          continue;
        }
        model.addRoot(jarRoot, type);
      }
      finally {
        model.commit();
      }
    }
  }

  private static void removeExistingDependencies(@NotNull final ModifiableRootModel model) {
    RootPolicy<Object> dependencyRemover = new RootPolicy<Object>() {
      @Override
      public Object visitLibraryOrderEntry(LibraryOrderEntry libraryOrderEntry, Object value) {
        model.removeOrderEntry(libraryOrderEntry);
        return value;
      }

      @Override
      public Object visitModuleOrderEntry(ModuleOrderEntry moduleOrderEntry, Object value) {
        model.removeOrderEntry(moduleOrderEntry);
        return value;
      }
    };
    for (OrderEntry orderEntry : model.getOrderEntries()) {
      orderEntry.accept(dependencyRemover, null);
    }
  }

  private static void populateDependencies(@NotNull final ModifiableRootModel model,
                                           @NotNull final Project project,
                                           @NotNull IdeaAndroidProject ideaAndroidProject) {
    final LibraryTable libraryTable = ProjectLibraryTable.getInstance(project);
    AndroidDependencies.populate(ideaAndroidProject, new DependencyFactory() {
      @Override
      public boolean addLibraryDependency(@NotNull DependencyScope scope, @NotNull String name, @NotNull File binaryPath) {
        Library library = libraryTable.getLibraryByName(name);
        if (library != null) {
          LibraryOrderEntry orderEntry = model.addLibraryEntry(library);
          orderEntry.setScope(scope);
          orderEntry.setExported(true);
          return true;
        }
        return false;
      }

      @Override
      public boolean addModuleDependency(@NotNull DependencyScope scope, @NotNull String name, @NotNull String modulePath) {
        ModuleManager moduleManager = ModuleManager.getInstance(project);
        Module moduleDependency = null;
        for (Module module : moduleManager.getModules()) {
          AndroidGradleFacet androidGradleFacet = Facets.getFirstFacetOfType(module, AndroidGradleFacet.TYPE_ID);
          if (androidGradleFacet != null) {
            String path = androidGradleFacet.getConfiguration().GRADLE_PROJECT_PATH;
            if (Objects.equal(path, modulePath)) {
              moduleDependency = module;
              break;
            }
          }
        }
        if (moduleDependency != null) {
          ModuleOrderEntry orderEntry = model.addModuleOrderEntry(moduleDependency);
          orderEntry.setExported(true);
          return true;
        }
        return false;
      }
    }, null);
  }
}
