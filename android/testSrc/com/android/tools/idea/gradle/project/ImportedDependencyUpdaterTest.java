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
package com.android.tools.idea.gradle.project;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.AndroidProjectKeys;
import com.android.tools.idea.gradle.ProjectImportEventMessage;
import com.android.tools.idea.gradle.dependency.LibraryDependency;
import com.android.tools.idea.gradle.dependency.ModuleDependency;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.BooleanFunction;
import com.intellij.util.containers.ContainerUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Tests for {@link ImportedDependencyUpdater}.
 */
public class ImportedDependencyUpdaterTest extends TestCase {
  private File myTempDir;
  private DataNode<ProjectData> myProjectInfo;
  private DataNode<ModuleData> myModuleInfo;
  private ImportedDependencyUpdater importer;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myTempDir = Files.createTempDir();

    File projectDir = new File(myTempDir, "project1");
    FileUtil.createDirectory(projectDir);
    File buildFile = new File(projectDir, SdkConstants.FN_BUILD_GRADLE);
    FileUtil.createIfDoesntExist(buildFile);
    ProjectData projectData = new ProjectData(GradleConstants.SYSTEM_ID, projectDir.getPath(), buildFile.getPath());

    myProjectInfo = new DataNode<ProjectData>(ProjectKeys.PROJECT, projectData, null);
    myModuleInfo = addModule("module1");
    addModule("module2");

    importer = new ImportedDependencyUpdater(myProjectInfo);
  }

  @NotNull
  private DataNode<ModuleData> addModule(@NotNull String moduleName) {
    ModuleData moduleData = new ModuleData(moduleName, GradleConstants.SYSTEM_ID, StdModuleTypes.JAVA.getId(),
                                           moduleName, myProjectInfo.getData().getIdeProjectFileDirectoryPath(), "");
    return myProjectInfo.createChild(ProjectKeys.MODULE, moduleData);
  }

  @Override
  protected void tearDown() throws Exception {
    if (myTempDir != null) {
      FileUtil.delete(myTempDir);
    }
    super.tearDown();
  }

  public void testImportDependenciesWithLibraryDependency() {
    final String jarFilePath = "~/repo/guava/guava-11.0.2.jar";
    File jarFile = new File(jarFilePath);
    final LibraryDependency dependency = new LibraryDependency(jarFile, DependencyScope.TEST);
    List<LibraryDependency> dependencies = Lists.newArrayList(dependency);

    importer.updateDependencies(myModuleInfo, dependencies);

    // verify that a library was added.
    DataNode<LibraryData> libraryInfo =
      ExternalSystemApiUtil.find(myProjectInfo, ProjectKeys.LIBRARY, new BooleanFunction<DataNode<LibraryData>>() {
        @Override
        public boolean fun(DataNode<LibraryData> node) {
          LibraryData other = node.getData();
          return dependency.getName().equals(other.getName());
        }
      });
    assertNotNull(libraryInfo);
    LibraryData libraryData = libraryInfo.getData();
    Set<String> paths = libraryData.getPaths(LibraryPathType.BINARY);
    assertEquals(1, paths.size());
    String actualPath = ContainerUtil.getFirstItem(paths);
    assertNotNull(actualPath);
    assertTrue(FileUtil.toSystemIndependentName(actualPath).endsWith(jarFilePath));

    // verify that the library dependency was added.
    DataNode<LibraryDependencyData> dependencyInfo = findLibraryDependency(dependency);
    assertNotNull(dependencyInfo);
    LibraryDependencyData dependencyData = dependencyInfo.getData();
    assertEquals(DependencyScope.TEST, dependencyData.getScope());
    assertSame(libraryData, dependencyData.getTarget());
    assertTrue(dependencyData.isExported());
  }

  public void testImportDependenciesWithModuleDependency() {
    final ModuleDependency dependency = new ModuleDependency("abc:module2", DependencyScope.TEST);
    List<ModuleDependency> dependencies = Lists.newArrayList(dependency);

    importer.updateDependencies(myModuleInfo, dependencies);

    // verify that the module dependency was added.
    DataNode<ModuleDependencyData> dependencyInfo =
      ExternalSystemApiUtil.find(myModuleInfo, ProjectKeys.MODULE_DEPENDENCY, new BooleanFunction<DataNode<ModuleDependencyData>>() {
        @Override
        public boolean fun(DataNode<ModuleDependencyData> node) {
          ModuleDependencyData data = node.getData();
          return dependency.getName().equals(data.getName());
        }
      });
    assertNotNull(dependencyInfo);
    ModuleDependencyData dependencyData = dependencyInfo.getData();
    assertEquals(DependencyScope.TEST, dependencyData.getScope());
    assertTrue(dependencyData.isExported());
  }

  public void testImportDependenciesWithNonExistingModuleAndBackupJar() {
    ModuleDependency dependency = new ModuleDependency("abc:module3", DependencyScope.TEST);
    File jarFile = new File("~/repo/guava/guava-11.0.2.jar");
    final LibraryDependency backup = new LibraryDependency(jarFile, DependencyScope.TEST);
    dependency.setBackupDependency(backup);

    List<ModuleDependency> dependencies = Lists.newArrayList(dependency);

    importer.updateDependencies(myModuleInfo, dependencies);

    verifyZeroModuleDependencies();

    // verify that the library dependency was added.
    DataNode<LibraryDependencyData> dependencyInfo = findLibraryDependency(backup);
    assertNotNull(dependencyInfo);

    assertMessageLogged();
  }

  @Nullable
  private DataNode<LibraryDependencyData> findLibraryDependency(@NotNull final com.android.tools.idea.gradle.dependency.LibraryDependency source) {
    return ExternalSystemApiUtil.find(myModuleInfo, ProjectKeys.LIBRARY_DEPENDENCY, new BooleanFunction<DataNode<LibraryDependencyData>>() {
      @Override
      public boolean fun(DataNode<LibraryDependencyData> node) {
        LibraryDependencyData data = node.getData();
        LibraryData other = data.getTarget();
        return source.getName().equals(other.getName());
      }
    });
  }

  public void testImportDependenciesWithNonExistingModuleAndWithoutBackupJar() {
    ModuleDependency dependency = new ModuleDependency("abc:module3", DependencyScope.TEST);
    List<ModuleDependency> dependencies = Lists.newArrayList(dependency);

    importer.updateDependencies(myModuleInfo, dependencies);

    // verify there are no dependencies.
    verifyZeroModuleDependencies();
    assertEquals(0, ExternalSystemApiUtil.findAll(myModuleInfo, ProjectKeys.LIBRARY_DEPENDENCY).size());

    assertMessageLogged();
  }

  private void verifyZeroModuleDependencies() {
    assertEquals(0, ExternalSystemApiUtil.findAll(myModuleInfo, ProjectKeys.MODULE_DEPENDENCY).size());
  }

  private void assertMessageLogged() {
    Collection<DataNode<ProjectImportEventMessage>> messages =
      ExternalSystemApiUtil.findAll(myModuleInfo, AndroidProjectKeys.IMPORT_EVENT_MSG);
    assertEquals(1, messages.size());
  }
}
