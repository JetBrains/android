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

import com.android.tools.idea.gradle.ContentRootSourcePaths;
import com.android.tools.idea.gradle.TestProjects;
import com.android.tools.idea.gradle.model.android.AndroidProjectStub;
import com.android.tools.idea.gradle.model.android.VariantStub;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ContentRootData;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.StdModuleTypes;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.Collection;

/**
 * Tests for {@link AndroidContentRoot}.
 */
public class AndroidContentRootTest extends TestCase {
  private ContentRootSourcePaths myExpectedSourcePaths;
  private AndroidProjectStub myAndroidProject;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myExpectedSourcePaths = new ContentRootSourcePaths();
  }

  @Override
  protected void tearDown() throws Exception {
    if (myAndroidProject != null) {
      myAndroidProject.dispose();
    }
    super.tearDown();
  }

  public void testStorePathsWithBasicProject() {
    myAndroidProject = TestProjects.createBasicProject();
    doTestContentRootSetUp();
  }

  public void testStorePathsWithFlavorsProject() {
    myAndroidProject = TestProjects.createFlavorsProject();
    doTestContentRootSetUp();
  }

  private void doTestContentRootSetUp() {
    ContentRootData contentRootData = createContentRoot();

    String projectRootPath = myAndroidProject.getRootDir().getAbsolutePath();
    assertEquals("Root path", projectRootPath, contentRootData.getRootPath());

    myExpectedSourcePaths.storeExpectedSourcePaths(myAndroidProject);
    myExpectedSourcePaths.assertCorrectSourceDirectoryPaths(contentRootData);
  }

  @NotNull
  private ContentRootData createContentRoot() {
    String rootDirPath = myAndroidProject.getRootDir().getAbsolutePath();
    ModuleData moduleData = new ModuleData(GradleConstants.SYSTEM_ID, StdModuleTypes.JAVA.getId(), myAndroidProject.getName(),
                                           rootDirPath);
    DataNode<ModuleData> moduleInfo = new DataNode<ModuleData>(ProjectKeys.MODULE, moduleData, null);
    AndroidContentRoot contentRoot = new AndroidContentRoot(rootDirPath);
    VariantStub selectedVariant = myAndroidProject.getFirstVariant();
    assertNotNull(selectedVariant);
    contentRoot.storePaths(myAndroidProject, selectedVariant);
    contentRoot.addTo(moduleInfo);

    Collection<DataNode<ContentRootData>> contentRoots = ExternalSystemApiUtil.getChildren(moduleInfo, ProjectKeys.CONTENT_ROOT);
    assertEquals("Content root count", 1, contentRoots.size());
    return contentRoots.iterator().next().getData();
  }
}
