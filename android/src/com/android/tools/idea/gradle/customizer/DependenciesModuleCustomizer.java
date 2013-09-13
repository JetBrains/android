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
import com.android.tools.idea.gradle.dependency.*;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.util.Facets;
import com.google.common.base.Objects;
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
import java.util.Collection;

/**
 * Sets the dependencies of a module imported from an {@link com.android.builder.model.AndroidProject}.
 */
public class DependenciesModuleCustomizer extends DependencyUpdater<ModifiableRootModel> implements ModuleCustomizer {
  private static final Logger LOG = Logger.getInstance(DependenciesModuleCustomizer.class);

  @Override
  public void customizeModule(@NotNull Module module, @NotNull Project project, @Nullable IdeaAndroidProject ideaAndroidProject) {
    if (ideaAndroidProject == null) {
      return;
    }
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    ModifiableRootModel model = moduleRootManager.getModifiableModel();
    try {
      removeExistingDependencies(model);
      Collection<Dependency> dependencies = Dependency.extractFrom(ideaAndroidProject);
      updateDependencies(model, dependencies);
    } finally {
      model.commit();
    }
  }

  private static void removeExistingDependencies(@NotNull final ModifiableRootModel moduleModel) {
    RootPolicy<Object> dependencyRemover = new RootPolicy<Object>() {
      @Override
      public Object visitLibraryOrderEntry(LibraryOrderEntry libraryOrderEntry, Object value) {
        moduleModel.removeOrderEntry(libraryOrderEntry);
        return value;
      }

      @Override
      public Object visitModuleOrderEntry(ModuleOrderEntry moduleOrderEntry, Object value) {
        moduleModel.removeOrderEntry(moduleOrderEntry);
        return value;
      }
    };
    for (OrderEntry orderEntry : moduleModel.getOrderEntries()) {
      orderEntry.accept(dependencyRemover, null);
    }
  }

  @Override
  protected void updateDependency(@NotNull ModifiableRootModel moduleModel, @NotNull LibraryDependency dependency) {
    LibraryTable libraryTable = ProjectLibraryTable.getInstance(moduleModel.getProject());
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
    LibraryOrderEntry orderEntry = moduleModel.addLibraryEntry(library);
    orderEntry.setScope(dependency.getScope());
    orderEntry.setExported(true);
  }

  private static void updateLibraryPaths(@NotNull Library library, @NotNull LibraryDependency dependency) {
    Library.ModifiableModel libraryModel = library.getModifiableModel();
    try {
      Collection<String> binaryPaths = dependency.getPaths(LibraryDependency.PathType.BINARY);
      for (String binaryPath : binaryPaths) {
        File file = new File(binaryPath);
        VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
        if (virtualFile == null) {
          // TODO: log and show balloon
          String msg = String.format("Unable to find file at path '%1$s', library '%2$s'", file.getPath(), library.getName());
          LOG.warn(msg);
          continue;
        }
        if (virtualFile.isDirectory()) {
          libraryModel.addRoot(virtualFile, OrderRootType.CLASSES);
          continue;
        }
        VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(virtualFile);
        if (jarRoot == null) {
          // TODO: log and show balloon
          String msg = String.format("Unable to parse contents of jar file '%1$s', library '%2$s'", file.getPath(), library.getName());
          LOG.warn(msg);
          continue;
        }
        libraryModel.addRoot(jarRoot, OrderRootType.CLASSES);
      }
    }
    finally {
      libraryModel.commit();
    }
  }

  @Override
  protected boolean tryUpdating(@NotNull ModifiableRootModel moduleModel, @NotNull ModuleDependency dependency) {
    ModuleManager moduleManager = ModuleManager.getInstance(moduleModel.getProject());
    Module moduleDependency = null;
    for (Module module : moduleManager.getModules()) {
      AndroidGradleFacet androidGradleFacet = Facets.getFirstFacetOfType(module, AndroidGradleFacet.TYPE_ID);
      if (androidGradleFacet != null) {
        String gradlePath = androidGradleFacet.getConfiguration().GRADLE_PROJECT_PATH;
        if (Objects.equal(gradlePath, dependency.getGradlePath())) {
          moduleDependency = module;
          break;
        }
      }
    }
    if (moduleDependency != null) {
      ModuleOrderEntry orderEntry = moduleModel.addModuleOrderEntry(moduleDependency);
      orderEntry.setExported(true);
      return true;
    }
    return false;
  }

  @NotNull
  @Override
  protected String getNameOf(@NotNull ModifiableRootModel moduleModel) {
    return moduleModel.getModule().getName();
  }

  @Override
  protected void log(ModifiableRootModel moduleModel, String category, String msg) {
    // TODO: log and show balloon.
  }
}
