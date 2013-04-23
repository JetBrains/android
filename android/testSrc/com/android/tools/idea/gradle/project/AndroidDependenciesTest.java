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

import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.TestProjects;
import com.android.tools.idea.gradle.model.android.AndroidLibraryStub;
import com.android.tools.idea.gradle.model.android.AndroidProjectStub;
import com.android.tools.idea.gradle.model.android.ProductFlavorContainerStub;
import com.android.tools.idea.gradle.model.android.VariantStub;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.util.containers.ContainerUtil;
import junit.framework.TestCase;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.util.Collection;
import java.util.Set;

/**
 * Tests for {@link AndroidDependencies}.
 */
public class AndroidDependenciesTest extends TestCase {
  private AndroidProjectStub myAndroidProject;
  private DataNode<ProjectData> myProjectInfo;
  private DataNode<ModuleData> myModuleInfo;
  private VariantStub myVariant;
  private IdeaAndroidProject myIdeaAndroidProject;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myAndroidProject = TestProjects.createFlavorsProject();
    myVariant = myAndroidProject.getFirstVariant();
    assertNotNull(myVariant);

    String projectName = myAndroidProject.getName();
    String rootDirPath = myAndroidProject.getRootDir().getAbsolutePath();
    myIdeaAndroidProject = new IdeaAndroidProject(rootDirPath, myAndroidProject, myVariant.getName());

    ProjectData projectData = new ProjectData(GradleConstants.SYSTEM_ID, rootDirPath);
    projectData.setName(projectName);
    myProjectInfo = new DataNode<ProjectData>(ProjectKeys.PROJECT, projectData, null);

    ModuleData moduleData = new ModuleData(GradleConstants.SYSTEM_ID, projectName, rootDirPath);
    myModuleInfo = myProjectInfo.createChild(ProjectKeys.MODULE, moduleData);
  }

  public void testAddToWithJarDependency() {
    // Set up a jar dependency in one of the flavors of the selected variant.
    File jarFile = new File("~/repo/guava/guava-11.0.2.jar");
    String flavorName = myVariant.getProductFlavors().get(0);
    ProductFlavorContainerStub productFlavor = (ProductFlavorContainerStub)myAndroidProject.getProductFlavors().get(flavorName);
    productFlavor.getDependencies().addJar(jarFile);

    AndroidDependencies.populate(myModuleInfo, myProjectInfo, myIdeaAndroidProject);

    Collection<DataNode<LibraryDependencyData>> deps = ExternalSystemUtil.getChildren(myModuleInfo, ProjectKeys.LIBRARY_DEPENDENCY);
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
    assertEquals(jarFile.getAbsolutePath(), binaryPath);
  }

  public void testAddToWithLibraryDependency() {
    // Set up a library dependency to the default configuration.
    ProductFlavorContainerStub defaultConfig = myAndroidProject.getDefaultConfig();

    String rootDirPath = myAndroidProject.getRootDir().getAbsolutePath();
    File libJar = new File(rootDirPath, "library.aar/library.jar");
    AndroidLibraryStub library = new AndroidLibraryStub(libJar);
    defaultConfig.getDependencies().addLibrary(library);

    AndroidDependencies.populate(myModuleInfo, myProjectInfo, myIdeaAndroidProject);

    Collection<DataNode<LibraryDependencyData>> deps = ExternalSystemUtil.getChildren(myModuleInfo, ProjectKeys.LIBRARY_DEPENDENCY);
    assertEquals(1, deps.size());

    DataNode<LibraryDependencyData> dependencyInfo = ContainerUtil.getFirstItem(deps);
    assertNotNull(dependencyInfo);

    LibraryDependencyData dependencyData = dependencyInfo.getData();
    assertEquals("library.aar", dependencyData.getName());
    assertEquals(DependencyScope.COMPILE, dependencyData.getScope());

    LibraryData libraryData = dependencyData.getTarget();
    Set<String> binaryPaths = libraryData.getPaths(LibraryPathType.BINARY);
    assertEquals(1, binaryPaths.size());
    String binaryPath = ContainerUtil.getFirstItem(binaryPaths);
    assertEquals(libJar.getAbsolutePath(), binaryPath);
  }
}
