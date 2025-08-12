/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.refactoring;

import static com.android.tools.idea.testing.AndroidGradleProjectRuleKt.onEdt;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.testing.AndroidGradleProjectRule;
import com.android.tools.idea.testing.EdtAndroidGradleProjectRule;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.RunsInEdt;
import java.io.File;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for {@link GradleAwareSourceRootRenameValidator}.
 */
@RunsInEdt
public class GradleAwareSourceRootRenameValidatorTest {
  AndroidGradleProjectRule projectRule = new AndroidGradleProjectRule();
  @Rule
  public EdtAndroidGradleProjectRule rule = onEdt(projectRule);

  private GradleAwareSourceRootRenameValidator myValidator;

  @Before
  public void setup() throws Exception {
    myValidator = new GradleAwareSourceRootRenameValidator();
  }

  @Test
  public void testIsInputValid() {
    verifyErrorMessage();
  }

  private void verifyErrorMessage() {
    projectRule.loadProject(SIMPLE_APPLICATION);
    // Generate buildConfig.
    projectRule.generateSources();

    Project project = projectRule.getProject();
    File sourceRoot = new File(project.getBasePath(), "app/build/generated/source/buildConfig/debug");
    PsiDirectory psiElement = PsiManager.getInstance(project).findDirectory(
      LocalFileSystem.getInstance().refreshAndFindFileByIoFile(sourceRoot));
    String newName = "debug1";

    // Call validator.
    myValidator.isInputValid(newName, psiElement, null);
    // Verify that warning message is shown.
    assertThat(myValidator.getErrorMessage(newName, project)).isNotNull();
  }
}
