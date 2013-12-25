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
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.TestProjects;
import com.android.tools.idea.gradle.project.AndroidContentRoot.ContentRootStorage;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.android.tools.idea.gradle.stubs.android.VariantStub;
import com.google.common.collect.Maps;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tests for {@link com.android.tools.idea.gradle.project.AndroidContentRoot}.
 */
public class AndroidContentRootTest extends IdeaTestCase {
  private ContentRootSourcePaths myExpectedSourcePaths;
  private AndroidProjectStub myAndroidProject;
  private Map<ExternalSystemSourceType, List<String>> myStoredPaths;
  private ContentRootStorage myStorage;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myExpectedSourcePaths = new ContentRootSourcePaths();

    myStoredPaths = Maps.newHashMap();
    for (ExternalSystemSourceType sourceType : ContentRootSourcePaths.ALL_SOURCE_TYPES) {
      setUpStoredPaths(sourceType);
    }

    myStorage = new ContentRootStorage() {
      @Override
      @NotNull
      public String getRootDirPath() {
        return myAndroidProject.getRootDir().getPath();
      }

      @Override
      public void storePath(@NotNull ExternalSystemSourceType sourceType, @NotNull File dir) {
        List<String> paths = myStoredPaths.get(sourceType);
        paths.add(FileUtil.toSystemIndependentName(dir.getPath()));
      }
    };
  }

  private void setUpStoredPaths(ExternalSystemSourceType sourceType) {
    myStoredPaths.put(sourceType, new ArrayList<String>());
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
    VariantStub selectedVariant = myAndroidProject.getFirstVariant();
    assertNotNull(selectedVariant);

    File rootDir = myAndroidProject.getRootDir();
    IdeaAndroidProject project =
      new IdeaAndroidProject(myAndroidProject.getName(), rootDir, myAndroidProject, selectedVariant.getName());
    AndroidContentRoot.storePaths(project, myStorage);

    myExpectedSourcePaths.storeExpectedSourcePaths(myAndroidProject);

    for (ExternalSystemSourceType sourceType : ContentRootSourcePaths.ALL_SOURCE_TYPES) {
      if (sourceType.equals(ExternalSystemSourceType.EXCLUDED)) {
        continue;
      }
      myExpectedSourcePaths.assertCorrectStoredDirPaths(myStoredPaths.get(sourceType), sourceType);
    }
  }
}
