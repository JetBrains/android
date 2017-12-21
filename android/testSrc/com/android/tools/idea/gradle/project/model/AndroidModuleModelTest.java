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
import com.android.tools.idea.gradle.stubs.android.TestAndroidProject;
import com.android.tools.idea.testing.AndroidGradleTestCase;

import java.io.*;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.testing.TestProjectPaths.PROJECT_WITH_APPAND_LIB;

/**
 * Tests for {@link AndroidModuleModel}.
 */
public class AndroidModuleModelTest extends AndroidGradleTestCase {
  public void testDisabled() {
    // http://b/35788105
  }

  private TestAndroidProject myAndroidProject;
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
}
