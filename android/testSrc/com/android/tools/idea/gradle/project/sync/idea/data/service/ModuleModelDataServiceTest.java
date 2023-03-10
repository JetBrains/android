// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.gradle.project.sync.idea.data.service;

import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.util.io.NioPathUtil;
import java.io.File;
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

    var linkedProjectPath1 = NioPathUtil.toCanonicalPath(linkedProject1.toPath());
    var linkedProjectPath2 = NioPathUtil.toCanonicalPath(linkedProject2.toPath());
    GradleImportingTestUtil.waitForProjectReload(linkedProjectPath1, () ->
      GradleImportingTestUtil.waitForProjectReload(linkedProjectPath2, () -> {
        GradleProjectImportUtil.linkAndRefreshGradleProject(linkedProject1.getAbsolutePath(), getProject());
        GradleProjectImportUtil.linkAndRefreshGradleProject(linkedProject2.getAbsolutePath(), getProject());
        return null;
      })
    );

    List<AndroidFacet> androidFacets = ProjectFacetManager.getInstance(getProject()).getFacets(AndroidFacet.ID);
    assertEquals(8, androidFacets.size());

    List<GradleFacet> androidGradleFacets = ProjectFacetManager.getInstance(getProject()).getFacets(GradleFacet.getFacetTypeId());
    assertEquals(2, androidGradleFacets.size());
  }

  public void testLinkedProjectsDoNotRemoveEachOtherFacetsSequential() throws Exception {
    File linkedProject1 = new File(myFixture.getTempDirFixture().findOrCreateDir("linked1").getPath());
    File linkedProject2 = new File(myFixture.getTempDirFixture().findOrCreateDir("linked2").getPath());

    prepareProjectForImport(SIMPLE_APPLICATION, linkedProject1);
    prepareProjectForImport(SIMPLE_APPLICATION, linkedProject2);

    GradleImportingTestUtil.waitForProjectReload(() -> {
      GradleProjectImportUtil.linkAndRefreshGradleProject(linkedProject1.getAbsolutePath(), getProject());
      return null;
    });

    GradleImportingTestUtil.waitForProjectReload(() -> {
      GradleProjectImportUtil.linkAndRefreshGradleProject(linkedProject2.getAbsolutePath(), getProject());
      return null;
    });

    List<AndroidFacet> androidFacets = ProjectFacetManager.getInstance(getProject()).getFacets(AndroidFacet.ID);
    assertEquals(8, androidFacets.size());

    List<GradleFacet> androidGradleFacets = ProjectFacetManager.getInstance(getProject()).getFacets(GradleFacet.getFacetTypeId());
    assertEquals(2, androidGradleFacets.size());
  }
}
