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

import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.setupTestProjectFromAndroidModel;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;

import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.rendering.RenderErrorModelFactory;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.errors.ui.RenderErrorModel;
import com.android.tools.idea.testing.AndroidModuleModelBuilder;
import com.android.tools.idea.testing.AndroidProjectBuilder;
import com.google.common.collect.ImmutableList;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.mock.MockPsiFile;
import com.intellij.mock.MockPsiManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestCase;
import java.io.File;

public class GradleRenderErrorContributorTest extends PlatformTestCase {
  private GradleRenderErrorContributor.GradleProvider myProvider;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    // For the isApplicable tests.
    myProvider = new GradleRenderErrorContributor.GradleProvider();
  }

  public void testProviderIsApplicable() {
    setUpAndroidFacetWithGradleModelWithIssue();
    assertThat(myProvider.isApplicable(myProject)).isTrue();
  }

  public void testProviderNotApplicableIfNotBuildWithGradle() {
    assertThat(myProvider.isApplicable(myProject)).isFalse();
  }

  public void testReportIssue170841() {
    setUpAndroidFacetWithGradleModelWithIssue();

    RenderResult result = createResultWithBrokenClass();
    RenderErrorModel renderErrorModel = RenderErrorModelFactory.createErrorModel(null, result, null);

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
    file.putUserData(ModuleUtilCore.KEY_MODULE, myModule);
    RenderResult result = RenderResult.createBlank(file);
    result.getLogger().addBrokenClass("com.google.Class", new Exception());
    return result;
  }

  private void setUpAndroidFacetWithGradleModelWithIssue() {
    setupTestProjectFromAndroidModel(
      getProject(),
      new File(getProject().getBasePath()),
      new AndroidModuleModelBuilder(":", null, "1.2.2", "debug", new AndroidProjectBuilder())
    );
    assume().that(AndroidModuleModel.get(myModule).getFeatures().isLayoutRenderingIssuePresent()).isTrue();
  }
}
