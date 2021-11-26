// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.gradle.project.sync.idea.data.service;

import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.facet.java.JavaFacet;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.facet.ProjectFacetManager;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.plugins.gradle.service.project.open.GradleProjectImportUtil;
import org.jetbrains.plugins.gradle.util.GradleImportingTestUtil;

public class ModuleModelDataServiceTest extends AndroidGradleTestCase {
  public void testLinkedProjectsDoNotRemoveEachOtherFacetsConcurrent() throws Exception {
    File linkedProject1 = new File(myFixture.getTempDirFixture().findOrCreateDir("linked1").getPath());
    File linkedProject2 = new File(myFixture.getTempDirFixture().findOrCreateDir("linked2").getPath());

    prepareProjectForImport(SIMPLE_APPLICATION, linkedProject1);
    prepareProjectForImport(SIMPLE_APPLICATION, linkedProject2);

    List<Path> projectsToReload = Arrays.asList(linkedProject1.toPath(), linkedProject2.toPath());
    GradleImportingTestUtil.waitForMultipleProjectsReload(projectsToReload,
      () -> {
        GradleProjectImportUtil.linkAndRefreshGradleProject(linkedProject1.getAbsolutePath(), getProject());
        GradleProjectImportUtil.linkAndRefreshGradleProject(linkedProject2.getAbsolutePath(), getProject());
        return null;
      }
    );

    List<AndroidFacet> androidFacets = ProjectFacetManager.getInstance(getProject()).getFacets(AndroidFacet.ID);
    assertEquals(2, androidFacets.size());

    List<GradleFacet> androidGradleFacets = ProjectFacetManager.getInstance(getProject()).getFacets(GradleFacet.getFacetTypeId());
    assertEquals(2, androidGradleFacets.size());

    List<JavaFacet> androidJavaFacets = ProjectFacetManager.getInstance(getProject()).getFacets(JavaFacet.getFacetTypeId());
    assertEquals(2, androidJavaFacets.size());
  }

  public void testLinkedProjectsDoNotRemoveEachOtherFacetsSequential() throws Exception {
    File linkedProject1 = new File(myFixture.getTempDirFixture().findOrCreateDir("linked1").getPath());
    File linkedProject2 = new File(myFixture.getTempDirFixture().findOrCreateDir("linked2").getPath());

    prepareProjectForImport(SIMPLE_APPLICATION, linkedProject1);
    prepareProjectForImport(SIMPLE_APPLICATION, linkedProject2);

    GradleImportingTestUtil.waitForProjectReload(
      () -> {
        GradleProjectImportUtil.linkAndRefreshGradleProject(linkedProject1.getAbsolutePath(), getProject());
        return null;
      }
    );

    GradleImportingTestUtil.waitForProjectReload(
      () -> {
        GradleProjectImportUtil.linkAndRefreshGradleProject(linkedProject2.getAbsolutePath(), getProject());
        return null;
      }
    );

    List<AndroidFacet> androidFacets = ProjectFacetManager.getInstance(getProject()).getFacets(AndroidFacet.ID);
    assertEquals(2, androidFacets.size());

    List<GradleFacet> androidGradleFacets = ProjectFacetManager.getInstance(getProject()).getFacets(GradleFacet.getFacetTypeId());
    assertEquals(2, androidGradleFacets.size());

    List<JavaFacet> androidJavaFacets = ProjectFacetManager.getInstance(getProject()).getFacets(JavaFacet.getFacetTypeId());
    assertEquals(2, androidJavaFacets.size());
  }
}