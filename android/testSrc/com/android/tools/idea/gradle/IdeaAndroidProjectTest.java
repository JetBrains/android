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
 */package com.android.tools.idea.gradle;

import com.android.builder.model.*;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.android.tools.idea.gradle.stubs.android.VariantStub;
import com.android.tools.idea.templates.AndroidGradleTestCase;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.*;

/**
 * Tests for {@link IdeaAndroidProject}.
 */
public class IdeaAndroidProjectTest extends AndroidGradleTestCase {
  private AndroidProjectStub myDelegate;
  private IdeaAndroidProject myAndroidProject;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    File rootDirPath = new File(getProject().getBasePath());
    myDelegate = TestProjects.createFlavorsProject();
    myAndroidProject = new IdeaAndroidProject(GradleConstants.SYSTEM_ID, myDelegate.getName(), rootDirPath, myDelegate, "f1fa-debug",
                                              AndroidProject.ARTIFACT_ANDROID_TEST);
  }

  public void testFindBuildType() throws Exception {
    String buildTypeName = "debug";
    BuildTypeContainer buildType = myAndroidProject.findBuildType(buildTypeName);
    assertNotNull(buildType);
    assertSame(myDelegate.findBuildType(buildTypeName), buildType);
  }

  public void testFindProductFlavor() throws Exception {
    String flavorName = "fa";
    ProductFlavorContainer flavor = myAndroidProject.findProductFlavor(flavorName);
    assertNotNull(flavor);
    assertSame(myDelegate.findProductFlavor(flavorName), flavor);
  }

  public void testFindSelectedTestArtifactInSelectedVariant() throws Exception {
    BaseArtifact instrumentationTestArtifact = myAndroidProject.findSelectedTestArtifactInSelectedVariant();
    VariantStub firstVariant = myDelegate.getFirstVariant();
    assertNotNull(firstVariant);
    assertSame(firstVariant.getInstrumentTestArtifact(), instrumentationTestArtifact);
  }

  public void testGetSelectedVariant() throws Exception {
    Variant selectedVariant = myAndroidProject.getSelectedVariant();
    assertNotNull(selectedVariant);
    assertSame(myDelegate.getFirstVariant(), selectedVariant);
  }

  public void testReadWriteObject() throws Exception {
    if (!CAN_SYNC_PROJECTS) {
      System.err.println("IdeaAndroidProjectDataSerializationTest.testReadWriteObject temporarily disabled");
      return;
    }

    loadProject("projects/projectWithAppandLib");

    IdeaAndroidProject ideaAndroidProject = myAndroidFacet.getIdeaAndroidProject();

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ObjectOutputStream oos;
    oos = new ObjectOutputStream(outputStream);
    oos.writeObject(ideaAndroidProject);
    oos.close();

    ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
    ObjectInputStream ois = new ObjectInputStream(inputStream);
    IdeaAndroidProject newIdeaAndroidProject = (IdeaAndroidProject)ois.readObject();
    ois.close();

    assertEquals(ideaAndroidProject.getProjectSystemId(), newIdeaAndroidProject.getProjectSystemId());
    assertEquals(ideaAndroidProject.getModuleName(), newIdeaAndroidProject.getModuleName());
    assertEquals(ideaAndroidProject.getRootDirPath(), newIdeaAndroidProject.getRootDirPath());
    assertEquals(ideaAndroidProject.getDelegate().getName(), newIdeaAndroidProject.getDelegate().getName());
    assertEquals(ideaAndroidProject.getSelectedVariant().getName(), newIdeaAndroidProject.getSelectedVariant().getName());
    assertEquals(ideaAndroidProject.getSelectedTestArtifactName(), newIdeaAndroidProject.getSelectedTestArtifactName());
  }
}
