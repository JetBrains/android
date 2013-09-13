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
package com.android.tools.idea.gradle.dependency;

import com.android.tools.idea.gradle.stubs.gradle.IdeaModuleDependencyStub;
import com.android.tools.idea.gradle.stubs.gradle.IdeaModuleStub;
import com.android.tools.idea.gradle.stubs.gradle.IdeaProjectStub;
import com.android.tools.idea.gradle.stubs.gradle.IdeaSingleEntryLibraryDependencyStub;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.util.containers.ContainerUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;

/**
 * Tests for {@link Dependency#extractFrom(org.gradle.tooling.model.idea.IdeaModule)}.
 */
public class ExtractJavaDependenciesTest extends TestCase {
  private IdeaProjectStub myIdeaProject;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myIdeaProject = new IdeaProjectStub("test");
  }

  public void testExtractFromUsingModuleDependency() {
    // module2 depends on module1
    IdeaModuleStub module1 = myIdeaProject.addModule("module1");
    IdeaModuleStub module2 = myIdeaProject.addModule("module2");
    module2.addDependency(new IdeaModuleDependencyStub(module1));

    Collection<Dependency> dependencies = Dependency.extractFrom(module2);
    assertEquals(1, dependencies.size());

    ModuleDependency dependency = (ModuleDependency)ContainerUtil.getFirstItem(dependencies);
    assertNotNull(dependency);
    assertEquals(module1.getName(), dependency.getName());
    assertEquals(module1.getGradleProject().getPath(), dependency.getGradlePath());
    assertEquals(DependencyScope.COMPILE, dependency.getScope());
  }

  public void testExtractFromUsingLibraryDependency() {
    File javadocFile = new File("~/repo/guava/guava-11.0.2-javadoc.jar");
    File jarFile = new File("~/repo/guava/guava-11.0.2.jar");
    File sourceFile = new File("~/repo/guava/guava-11.0.2-src.jar");

    IdeaSingleEntryLibraryDependencyStub ideaDependency = new IdeaSingleEntryLibraryDependencyStub(jarFile);
    ideaDependency.setJavadoc(javadocFile);
    ideaDependency.setSource(sourceFile);

    IdeaModuleStub module1 = myIdeaProject.addModule("module1");
    module1.addDependency(ideaDependency);

    Collection<Dependency> dependencies = Dependency.extractFrom(module1);
    assertEquals(1, dependencies.size());

    LibraryDependency dependency = (LibraryDependency)ContainerUtil.getFirstItem(dependencies);
    assertNotNull(dependency);
    assertEquals("guava-11.0.2", dependency.getName());
    assertEquals(DependencyScope.COMPILE, dependency.getScope());
    assertHasEqualPath(javadocFile, dependency.getPaths(LibraryDependency.PathType.DOC));
    assertHasEqualPath(jarFile, dependency.getPaths(LibraryDependency.PathType.BINARY));
    assertHasEqualPath(sourceFile, dependency.getPaths(LibraryDependency.PathType.SOURCE));
  }

  private static void assertHasEqualPath(@NotNull File expected, @NotNull Collection<String> actualPaths) {
    assertEquals(1, actualPaths.size());
    assertEquals(expected.getPath(), ContainerUtil.getFirstItem(actualPaths));
  }
}
