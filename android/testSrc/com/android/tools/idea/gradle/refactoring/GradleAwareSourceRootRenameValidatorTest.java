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

import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_USER_REQUEST;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import java.io.File;

/**
 * Tests for {@link GradleAwareSourceRootRenameValidator}.
 */
public class GradleAwareSourceRootRenameValidatorTest extends AndroidGradleTestCase {
  private GradleAwareSourceRootRenameValidator myValidator;
  private boolean myUseNewSyncInfra;
  private boolean myUseSingleVariantSync;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myValidator = new GradleAwareSourceRootRenameValidator();
    myUseNewSyncInfra = StudioFlags.NEW_SYNC_INFRA_ENABLED.get();
    myUseSingleVariantSync = GradleExperimentalSettings.getInstance().USE_SINGLE_VARIANT_SYNC;
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      // back to default value.
      StudioFlags.NEW_SYNC_INFRA_ENABLED.override(myUseNewSyncInfra);
      GradleExperimentalSettings.getInstance().USE_SINGLE_VARIANT_SYNC = myUseSingleVariantSync;
    }
    finally {
      super.tearDown();
    }
  }


  public void testIsInputValidWithIdeaSync() throws Exception {
    StudioFlags.NEW_SYNC_INFRA_ENABLED.override(false);
    GradleExperimentalSettings.getInstance().USE_SINGLE_VARIANT_SYNC = false;
    verifyErrorMessage();
  }

  public void testIsInputValidWithNewSync() throws Exception {
    StudioFlags.NEW_SYNC_INFRA_ENABLED.override(true);
    GradleExperimentalSettings.getInstance().USE_SINGLE_VARIANT_SYNC = false;
    verifyErrorMessage();
  }

  public void testIsInputValidWithSingleVariantSync() throws Exception {
    StudioFlags.NEW_SYNC_INFRA_ENABLED.override(true);
    GradleExperimentalSettings.getInstance().USE_SINGLE_VARIANT_SYNC = true;
    verifyErrorMessage();
  }

  private void verifyErrorMessage() throws Exception {
    loadSimpleApplication();
    // Generate buildConfig.
    GradleSyncInvoker.Request request = new GradleSyncInvoker.Request(TRIGGER_USER_REQUEST);
    request.generateSourcesOnSuccess = true;
    requestSyncAndWait(request);

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
