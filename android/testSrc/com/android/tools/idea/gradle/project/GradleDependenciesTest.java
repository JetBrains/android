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

import com.android.tools.idea.gradle.stubs.gradle.IdeaModuleDependencyStub;
import com.android.tools.idea.gradle.stubs.gradle.IdeaModuleStub;
import com.android.tools.idea.gradle.stubs.gradle.IdeaProjectStub;
import com.android.tools.idea.gradle.stubs.gradle.IdeaSingleEntryLibraryDependencyStub;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import junit.framework.TestCase;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.util.Collection;
import java.util.Set;

/**
 * Tests for {@link GradleDependencies}.
 */
public class GradleDependenciesTest extends TestCase {
  private IdeaProjectStub myProject;
  private IdeaModuleStub myModule;
  private DataNode<ProjectData> myProjectInfo;
  private DataNode<ModuleData> myModuleInfo;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myProject = new IdeaProjectStub("basic");

    String projectName = myProject.getName();
    myModule = myProject.addModule(projectName);

    String rootDirPath = myProject.getRootDir().getAbsolutePath();
    String buildFilePath = myProject.getBuildFile().getAbsolutePath();

    ProjectData projectData = new ProjectData(GradleConstants.SYSTEM_ID, rootDirPath, buildFilePath);
    projectData.setName(projectName);
    myProjectInfo = new DataNode<ProjectData>(ProjectKeys.PROJECT, projectData, null);

    String configPath = rootDirPath + "/build.gradle";
    ModuleData moduleData = new ModuleData(GradleConstants.SYSTEM_ID, StdModuleTypes.JAVA.getId(), projectName, rootDirPath, configPath);
    myModuleInfo = myProjectInfo.createChild(ProjectKeys.MODULE, moduleData);
  }

  public void testAddToWithJarDependency() {
    File jarFile = new File("~/repo/guava/guava-11.0.2.jar");
    IdeaSingleEntryLibraryDependencyStub dependency = new IdeaSingleEntryLibraryDependencyStub(jarFile);
    myModule.addDependency(dependency);

    File sourceFile = new File("~/repo/guava/guava-11.0.2-src.jar");
    dependency.setSource(sourceFile);

    File javadocFile = new File("~/repo/guava/guava-11.0.2-javadoc.jar");
    dependency.setJavadoc(javadocFile);

    GradleDependencies.populate(myModuleInfo, myProjectInfo, myModule);

    Collection<DataNode<LibraryDependencyData>> deps = ExternalSystemApiUtil.getChildren(myModuleInfo, ProjectKeys.LIBRARY_DEPENDENCY);
    assertEquals(1, deps.size());

    DataNode<LibraryDependencyData> dependencyInfo = ContainerUtil.getFirstItem(deps);
    assertNotNull(dependencyInfo);

    LibraryDependencyData dependencyData = dependencyInfo.getData();
    assertEquals("guava-11.0.2", dependencyData.getName());
    assertEquals(DependencyScope.COMPILE, dependencyData.getScope());

    LibraryData libraryData = dependencyData.getTarget();

    Set<String> binaryPaths = libraryData.getPaths(LibraryPathType.BINARY);
    assertEquals(1, binaryPaths.size());
    String binaryPath = ContainerUtil.getFirstItem(binaryPaths);
    assertEquals(FileUtil.toSystemIndependentName(jarFile.getAbsolutePath()), binaryPath);

    Set<String> sourcePaths = libraryData.getPaths(LibraryPathType.SOURCE);
    assertEquals(1, sourcePaths.size());
    String sourcePath = ContainerUtil.getFirstItem(sourcePaths);
    assertEquals(FileUtil.toSystemIndependentName(sourceFile.getAbsolutePath()), sourcePath);

    Set<String> javadocPaths = libraryData.getPaths(LibraryPathType.DOC);
    assertEquals(1, javadocPaths.size());
    String javadocPath = ContainerUtil.getFirstItem(javadocPaths);
    assertEquals(FileUtil.toSystemIndependentName(javadocFile.getAbsolutePath()), javadocPath);
  }

  public void testAddToWithModuleDependency() {
    String dependencyModuleName = "util";

    String rootDirPath = myProject.getRootDir().getAbsolutePath();
    String configPath = rootDirPath + "/build.gradle";
    ModuleData moduleData
      = new ModuleData(GradleConstants.SYSTEM_ID, StdModuleTypes.JAVA.getId(), dependencyModuleName, rootDirPath, configPath);
    myProjectInfo.createChild(ProjectKeys.MODULE, moduleData);

    IdeaModuleStub dependencyModule = myProject.addModule(dependencyModuleName);
    IdeaModuleDependencyStub dependency = new IdeaModuleDependencyStub(dependencyModule);
    myModule.addDependency(dependency);

    GradleDependencies.populate(myModuleInfo, myProjectInfo, myModule);

    Collection<DataNode<ModuleDependencyData>> deps = ExternalSystemApiUtil.getChildren(myModuleInfo, ProjectKeys.MODULE_DEPENDENCY);
    assertEquals(1, deps.size());

    DataNode<ModuleDependencyData> dependencyInfo = ContainerUtil.getFirstItem(deps);
    assertNotNull(dependencyInfo);

    ModuleDependencyData dependencyData = dependencyInfo.getData();
    assertEquals(dependencyModuleName, dependencyData.getName());
    assertEquals(DependencyScope.COMPILE, dependencyData.getScope());
  }
}
