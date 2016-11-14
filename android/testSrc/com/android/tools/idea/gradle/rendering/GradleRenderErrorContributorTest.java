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
package com.android.tools.idea.gradle.rendering;

import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.TestProjects;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.rendering.RenderErrorModelFactory;
import com.android.tools.idea.rendering.RenderLogger;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.errors.ui.RenderErrorModel;
import com.android.tools.idea.testing.IdeComponents;
import com.google.common.collect.ImmutableList;
import com.intellij.facet.FacetManager;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.mock.MockPsiFile;
import com.intellij.mock.MockPsiManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;

import java.io.File;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

public class GradleRenderErrorContributorTest extends IdeaTestCase {
  private GradleRenderErrorContributor.GradleProvider provider;
  private RenderErrorModel renderErrorModel;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    IdeComponents.replaceServiceWithMock(myProject, GradleProjectInfo.class);
    when(GradleProjectInfo.getInstance(myProject).isBuildWithGradle()).thenReturn(true);

    setUpAndroidFacetWithGradleModelWithIssue();

    RenderResult result = createResultWithBrokenClass();
    renderErrorModel = RenderErrorModelFactory.createErrorModel(result, null);

    // For the isApplicable tests.
    provider = new GradleRenderErrorContributor.GradleProvider();
  }

  public void testProviderIsApplicable() {
    assertThat(provider.isApplicable(myProject)).isTrue();
  }

  public void testProviderNotApplicableIfNotBuildWithGradle() {
    when(GradleProjectInfo.getInstance(myProject).isBuildWithGradle()).thenReturn(false);
    assertThat(provider.isApplicable(myProject)).isFalse();
  }

  public void testReportIssue170841() {
    ImmutableList<RenderErrorModel.Issue> issues = renderErrorModel.getIssues();
    assertThat(issues).isNotEmpty();
    RenderErrorModel.Issue issue170841 = null;
    for (RenderErrorModel.Issue issue : issues) {
      if (issue.getSummary().equals(
        "Using an obsolete version of the Gradle plugin (1.2.2); " +
        "this can lead to layouts not rendering correctly.")) {
        // We expect exactly one of this issue.
        assertThat(issue170841).isNull();
        issue170841 = issue;
      }
    }
    assertThat(issue170841).isNotNull();
    assertThat(issue170841.getSeverity()).isEqualTo(HighlightSeverity.ERROR);
    String htmlContent = issue170841.getHtmlContent();
    assertThat(htmlContent).contains("update the Gradle plugin build version to 1.2.3");
    assertThat(htmlContent).contains("or downgrade to version 1.1.3");
  }

  private RenderResult createResultWithBrokenClass() {
    PsiFile file = new MockPsiFile(new MockPsiManager(myProject));
    RenderLogger logger = new RenderLogger(null, myModule);
    logger.addBrokenClass("com.google.Class", new Exception());
    return RenderResult.createBlank(file);
  }

  private void setUpAndroidFacetWithGradleModelWithIssue() {
    ApplicationManager.getApplication().runWriteAction(
      () -> {
        FacetManager.getInstance(myModule).addFacet(AndroidFacet.getFacetType(), AndroidFacet.NAME, null);
      }
    );
    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    assertThat(facet).isNotNull();

    File root = Projects.getBaseDirPath(myProject);
    AndroidProjectStub androidProject = TestProjects.createBasicProject();

    // The problematic plugin version.
    // https://code.google.com/p/android/issues/detail?id=170841
    androidProject.setModelVersion("1.2.2");

    AndroidGradleModel model = new AndroidGradleModel(androidProject.getName(), root, androidProject, "debug");
    facet.setAndroidModel(model);
    model = AndroidGradleModel.get(myModule);

    assertThat(model).isNotNull();
    assertThat(model.getFeatures().isLayoutRenderingIssuePresent()).isTrue();
  }
}
