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

import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_TEST_REQUESTED;

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import java.io.File;

/**
 * Tests for {@link GradleAwareSourceRootRenameValidator}.
 */
public class GradleAwareSourceRootRenameValidatorTest extends AndroidGradleTestCase {
  private GradleAwareSourceRootRenameValidator myValidator;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myValidator = new GradleAwareSourceRootRenameValidator();
  }

  public void testIsInputValid() throws Exception {
    verifyErrorMessage();
  }

  private void verifyErrorMessage() throws Exception {
    loadSimpleApplication();
    // Generate buildConfig.
    requestSyncAndWait(new GradleSyncInvoker.Request(TRIGGER_TEST_REQUESTED));
    generateSources();

    Project project = getProject();
    File sourceRoot = new File(project.getBasePath(), "app/build/generated/source/buildConfig/debug");
    PsiDirectory psiElement = PsiManager.getInstance(getProject()).findDirectory(
      LocalFileSystem.getInstance().refreshAndFindFileByIoFile(sourceRoot));
    String newName = "debug1";

    // Call validator.
    myValidator.isInputValid(newName, psiElement, null);
    // Verify that warning message is shown.
    assertNotNull(myValidator.getErrorMessage(newName, project));
  }
}
