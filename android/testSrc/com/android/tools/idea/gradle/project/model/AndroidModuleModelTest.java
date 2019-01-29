/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.model;

import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.Variant;
import com.android.ide.common.gradle.model.level2.IdeDependenciesFactory;
import com.android.tools.idea.gradle.TestProjects;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.android.tools.idea.testing.AndroidGradleTestCase;

import java.io.*;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.testing.TestProjectPaths.PROJECT_WITH_APPAND_LIB;
import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link AndroidModuleModel}.
 */
public class AndroidModuleModelTest extends AndroidGradleTestCase {
  private AndroidProjectStub myAndroidProject;
  private AndroidModuleModel myAndroidModel;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    File rootDirPath = getBaseDirPath(getProject());
    myAndroidProject = TestProjects.createFlavorsProject();
    myAndroidModel =
      new AndroidModuleModel(myAndroidProject.getName(), rootDirPath, myAndroidProject, "f1fa-debug", new IdeDependenciesFactory());
  }

  public void /*test*/FindBuildType() throws Exception {
    String buildTypeName = "debug";
    BuildTypeContainer buildType = myAndroidModel.findBuildType(buildTypeName);
    assertNotNull(buildType);
    assertSame(myAndroidProject.findBuildType(buildTypeName), buildType);
  }

  public void /*test*/FindProductFlavor() throws Exception {
    String flavorName = "fa";
    ProductFlavorContainer flavor = myAndroidModel.findProductFlavor(flavorName);
    assertNotNull(flavor);
    assertSame(myAndroidProject.findProductFlavor(flavorName), flavor);
  }

  public void /*test*/GetSelectedVariant() throws Exception {
    Variant selectedVariant = myAndroidModel.getSelectedVariant();
    assertNotNull(selectedVariant);
    assertSame(myAndroidProject.getFirstVariant(), selectedVariant);
  }

  public void /*test*/ReadWriteObject() throws Exception {
    loadProject(PROJECT_WITH_APPAND_LIB);

    AndroidModuleModel androidModel = AndroidModuleModel.get(myAndroidFacet);

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ObjectOutputStream oos;
    //noinspection IOResourceOpenedButNotSafelyClosed
    oos = new ObjectOutputStream(outputStream);
    oos.writeObject(androidModel);
    oos.close();

    ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
    //noinspection IOResourceOpenedButNotSafelyClosed
    ObjectInputStream ois = new ObjectInputStream(inputStream);
    AndroidModuleModel newAndroidModel = (AndroidModuleModel)ois.readObject();
    ois.close();

    assert androidModel != null;
    assertEquals(androidModel.getProjectSystemId(), newAndroidModel.getProjectSystemId());
    assertEquals(androidModel.getModuleName(), newAndroidModel.getModuleName());
    assertEquals(androidModel.getRootDirPath(), newAndroidModel.getRootDirPath());
    assertEquals(androidModel.getAndroidProject().getName(), newAndroidModel.getAndroidProject().getName());
    assertEquals(androidModel.getSelectedVariant().getName(), newAndroidModel.getSelectedVariant().getName());
  }

  public void testSelectedVariantExistsButNotRequested() {
    AndroidProjectStub androidProject = new AndroidProjectStub("MyApp");
    androidProject.clearVariants();
    // Simulate the case that variant names are "release" and "debug", but only "release" variant is requested.
    androidProject.addVariant("release");
    androidProject.setVariantNames("debug", "release");

    // Create AndroidModuleModel with "debug" as selected variant.
    AndroidModuleModel androidModel =
      new AndroidModuleModel(androidProject.getName(), getBaseDirPath(getProject()), androidProject, "debug", new IdeDependenciesFactory());

    // Verify that "release" is set as selected variant.
    assertThat(androidModel.getSelectedVariant().getName()).isEqualTo("release");

    // Verify that findVariantToSelect selects specified variant if it has been requested, selects the first available one otherwise.
    assertThat(androidModel.findVariantToSelect("release")).isEqualTo("release");
    assertThat(androidModel.findVariantToSelect("debug")).isEqualTo("release");
  }

  public void testSelectedVariantWasRequested() {
    AndroidProjectStub androidProject = new AndroidProjectStub("MyApp");
    androidProject.clearVariants();
    // Simulate the case that variant names are "release" and "debug", but only "release" variant is requested.
    androidProject.addVariant("release");
    androidProject.setVariantNames("debug", "release");

    // Create AndroidModuleModel with "release" as selected variant.
    AndroidModuleModel androidModel =
      new AndroidModuleModel(androidProject.getName(), getBaseDirPath(getProject()), androidProject, "release",
                             new IdeDependenciesFactory());
    // Verify that "release" is set as selected variant.
    assertThat(androidModel.getSelectedVariant().getName()).isEqualTo("release");
  }
}
