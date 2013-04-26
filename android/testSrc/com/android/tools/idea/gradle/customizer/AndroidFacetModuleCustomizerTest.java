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
package com.android.tools.idea.gradle.customizer;

import com.android.builder.model.SourceProvider;
import com.android.tools.idea.gradle.util.Facets;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.TestProjects;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.android.tools.idea.gradle.stubs.android.VariantStub;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;

import java.io.File;
import java.util.Collection;
import java.util.Set;

/**
 * Tests for {@link AndroidFacetModuleCustomizer}.
 */
public class AndroidFacetModuleCustomizerTest extends IdeaTestCase {
  private AndroidProjectStub myAndroidProject;
  private AndroidFacetModuleCustomizer myCustomizer;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    File rootDirPath = new File(myProject.getBasePath());
    myAndroidProject = TestProjects.createBasicProject(rootDirPath);
    myAndroidProject.setIsLibrary(true);
    myCustomizer = new AndroidFacetModuleCustomizer();
  }

  @Override
  protected void tearDown() throws Exception {
    if (myAndroidProject != null) {
      myAndroidProject.dispose();
    }
    super.tearDown();
  }

  public void testCustomizeModule() {
    String rootDirPath = myAndroidProject.getRootDir().getAbsolutePath();
    VariantStub selectedVariant = myAndroidProject.getFirstVariant();
    assertNotNull(selectedVariant);
    String selectedVariantName = selectedVariant.getName();
    IdeaAndroidProject project = new IdeaAndroidProject(rootDirPath, myAndroidProject, selectedVariantName);
    myCustomizer.customizeModule(myModule, myProject, project);

    // Verify that AndroidFacet was added and configured.
    AndroidFacet facet = Facets.getFirstFacet(myModule, AndroidFacet.ID);
    assertNotNull(facet);
    assertSame(project, facet.getIdeaAndroidProject());

    JpsAndroidModuleProperties facetState = facet.getConfiguration().getState();
    assertFalse(facetState.ALLOW_USER_CONFIGURATION);
    assertEquals(myAndroidProject.isLibrary(), facetState.LIBRARY_PROJECT);
    assertEquals(selectedVariantName, facetState.SELECTED_BUILD_VARIANT);

    SourceProvider sourceProvider = myAndroidProject.getDefaultConfig().getSourceProvider();

    File manifestFile = sourceProvider.getManifestFile();
    assertEquals(getRelativePath(manifestFile), facetState.MANIFEST_FILE_RELATIVE_PATH);

    Set<File> assetsDirs = sourceProvider.getAssetsDirectories();
    assertEquals(getRelativePath(assetsDirs), facetState.ASSETS_FOLDER_RELATIVE_PATH);

    Set<File> resDirs = sourceProvider.getResDirectories();
    assertEquals(getRelativePath(resDirs), facetState.RES_FOLDER_RELATIVE_PATH);
  }

  @NotNull
  private String getRelativePath(@NotNull Collection<File> dirs) {
    File first = ContainerUtil.getFirstItem(dirs);
    assertNotNull(first);
    return getRelativePath(first);
  }

  @NotNull
  private String getRelativePath(@NotNull File file) {
    String basePath = myProject.getBasePath();
    String relativePath = FileUtilRt.getRelativePath(basePath, file.getAbsolutePath(), File.separatorChar);
    assertNotNull(relativePath);
    return "/" + FileUtilRt.toSystemIndependentName(relativePath);
  }
}
