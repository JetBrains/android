/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.model;

import com.android.builder.model.AndroidProject;
import com.android.tools.idea.gradle.TestProjects;
import com.intellij.testFramework.UsefulTestCase;
import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests for {@link IdeAndroidProject}.
 */
public class IdeAndroidProjectTest extends UsefulTestCase {
  private AndroidProject myAndroidProject = TestProjects.createFlavorsProject();
  private IdeAndroidProject myIdeAndroidProject;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myIdeAndroidProject = new IdeAndroidProject(myAndroidProject);
  }

  public void testCopyAndroidProject() throws AssertionError{
    assertEquals(myIdeAndroidProject, myAndroidProject);
  }

  public void testEquals() throws Exception{
    // Run EqualsVerifier on all classes that add an equals and hashCode functions
    EqualsVerifier.forClass(IdeAaptOptions.class).verify();
    EqualsVerifier.forClass(IdeAndroidArtifact.class)
      .withIgnoredFields("myGradleVersion", "myDependencyGraphs")
      .verify();
    EqualsVerifier.forClass(IdeAndroidArtifactOutput.class).verify();
    EqualsVerifier.forClass(IdeApiVersion.class).verify();
    EqualsVerifier.forClass(IdeAndroidArtifactOutput.class).verify();
    EqualsVerifier.forClass(IdeBaseArtifact.class)
      .withIgnoredFields("myGradleVersion", "myDependencyGraphs")
      .verify();
    EqualsVerifier.forClass(IdeBuildType.class).verify();
    EqualsVerifier.forClass(IdeBuildTypeContainer.class).verify();
    EqualsVerifier.forClass(IdeDependencies.class).verify();
    EqualsVerifier.forClass(IdeDependencyGraphs.class).verify();
    EqualsVerifier.forClass(IdeInstantRun.class).verify();
    EqualsVerifier.forClass(IdeJavaArtifact.class).verify();
    EqualsVerifier.forClass(IdeJavaCompileOptions.class).verify();
    EqualsVerifier.forClass(IdeLintOptions.class).verify();
    EqualsVerifier.forClass(IdeNativeLibrary.class).verify();
    EqualsVerifier.forClass(IdeOutputFile.class).verify();
    EqualsVerifier.forClass(IdeProductFlavor.class).verify();
    EqualsVerifier.forClass(IdeProductFlavorContainer.class).verify();
    EqualsVerifier.forClass(IdeSigningConfig.class).verify();
    EqualsVerifier.forClass(IdeSourceProvider.class).verify();
    EqualsVerifier.forClass(IdeSourceProviderContainer.class).verify();
    EqualsVerifier.forClass(IdeVariant.class).verify();
    EqualsVerifier.forClass(IdeVariantOutput.class).verify();
    EqualsVerifier.forClass(IdeVectorDrawablesOptions.class).verify();
  }
}
