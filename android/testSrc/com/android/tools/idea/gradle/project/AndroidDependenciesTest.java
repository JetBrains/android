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
import com.android.tools.idea.gradle.project.AndroidDependencies;
import com.android.tools.idea.gradle.project.AndroidDependencies.DependencyFactory;
import com.android.tools.idea.gradle.stubs.android.AndroidLibraryStub;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.android.tools.idea.gradle.stubs.android.VariantStub;
import com.intellij.openapi.roots.DependencyScope;
import junit.framework.TestCase;

import java.io.File;

import static org.easymock.EasyMock.*;

/**
 * Tests for {@link AndroidDependencies}.
 */
public class AndroidDependenciesTest extends TestCase {
  private AndroidProjectStub myAndroidProject;
  private VariantStub myVariant;
  private IdeaAndroidProject myIdeaAndroidProject;
  private DependencyFactory myDependencyFactory;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myAndroidProject = TestProjects.createFlavorsProject();
    myVariant = myAndroidProject.getFirstVariant();
    assertNotNull(myVariant);

    String rootDirPath = myAndroidProject.getRootDir().getAbsolutePath();
    myIdeaAndroidProject = new IdeaAndroidProject(myAndroidProject.getName(), rootDirPath, myAndroidProject, myVariant.getName());

    myDependencyFactory = createMock(DependencyFactory.class);
  }

  @Override
  protected void tearDown() throws Exception {
    if (myAndroidProject != null) {
      myAndroidProject.dispose();
    }
    super.tearDown();
  }

  public void testAddToWithJarDependency() {
    // Set up a jar dependency in one of the flavors of the selected variant.
    File jarFile = new File("~/repo/guava/guava-11.0.2.jar");
    myVariant.getMainArtifactInfo().getDependencies().addJar(jarFile);

    myDependencyFactory.addLibraryDependency(DependencyScope.COMPILE, "guava-11.0.2", jarFile);
    expectLastCall();

    replay(myDependencyFactory);

    AndroidDependencies.populate(myIdeaAndroidProject, myDependencyFactory, null);

    verify(myDependencyFactory);
  }

  public void testAddToWithLibraryDependency() {
    // Set up a library dependency to the default configuration.
    String rootDirPath = myAndroidProject.getRootDir().getAbsolutePath();
    File libJar = new File(rootDirPath, "library.aar/library.jar");
    AndroidLibraryStub library = new AndroidLibraryStub(libJar);
    myVariant.getMainArtifactInfo().getDependencies().addLibrary(library);

    myDependencyFactory.addLibraryDependency(DependencyScope.COMPILE, "library.aar", libJar);
    expectLastCall();

    replay(myDependencyFactory);

    AndroidDependencies.populate(myIdeaAndroidProject, myDependencyFactory, null);

    verify(myDependencyFactory);
  }
}
