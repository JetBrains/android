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
package com.android.tools.idea.run.tasks;

import static com.android.tools.deployer.PreswapCheck.MODIFYING_ANDROID_MANIFEST_XML_FILES_NOT_SUPPORTED;
import static com.android.tools.deployer.PreswapCheck.RESOURCE_MODIFICATION_NOT_ALLOWED;
import static com.android.tools.deployer.PreswapCheck.STATIC_LIB_MODIFIED_ERROR;
import static com.android.tools.idea.run.tasks.DeploymentErrorHandler.APPLY_CHANGES_OPTION;
import static com.android.tools.idea.run.tasks.UnifiedDeployTask.DeployType.CODE_SWAP;
import static com.android.tools.idea.run.tasks.UnifiedDeployTask.DeployType.FULL_SWAP;
import static com.android.tools.idea.run.tasks.DeploymentErrorHandler.RERUN_OPTION;
import static com.google.common.truth.Truth.assertThat;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public class DeploymentErrorHandlerTest {
  @Test
  public void ensureErrorFormatterCorrectness() {
    checkEquivalentFormattedDeploymentErrors("", false);

    checkEquivalentFormattedDeploymentErrors(STATIC_LIB_MODIFIED_ERROR, false);

    checkEquivalentFormattedDeploymentErrors(MODIFYING_ANDROID_MANIFEST_XML_FILES_NOT_SUPPORTED, false);

    checkEquivalentFormattedDeploymentErrors(
      String.join("\n", STATIC_LIB_MODIFIED_ERROR, MODIFYING_ANDROID_MANIFEST_XML_FILES_NOT_SUPPORTED), false);

    checkEquivalentFormattedDeploymentErrors(
      String.join("\n", MODIFYING_ANDROID_MANIFEST_XML_FILES_NOT_SUPPORTED, RESOURCE_MODIFICATION_NOT_ALLOWED), false);

    checkEquivalentFormattedDeploymentErrors(String.join("\n", STATIC_LIB_MODIFIED_ERROR, RESOURCE_MODIFICATION_NOT_ALLOWED), false);

    checkEquivalentFormattedDeploymentErrors(
      String.join("\n", STATIC_LIB_MODIFIED_ERROR, MODIFYING_ANDROID_MANIFEST_XML_FILES_NOT_SUPPORTED, RESOURCE_MODIFICATION_NOT_ALLOWED),
      false);

    String result = new DeploymentErrorHandler(CODE_SWAP, RESOURCE_MODIFICATION_NOT_ALLOWED).getFormattedErrorString();
    assertThat(result).contains(APPLY_CHANGES_OPTION);
  }

  private void checkEquivalentFormattedDeploymentErrors(@NotNull String errorString, boolean containsApplyChanges) {
    String result = new DeploymentErrorHandler(CODE_SWAP, errorString).getFormattedErrorString();
    assertThat(result).contains(RERUN_OPTION);
    if (containsApplyChanges) {
      assertThat(result).contains(APPLY_CHANGES_OPTION);
    }
    else {
      assertThat(result).doesNotContain(APPLY_CHANGES_OPTION);
    }

    result = new DeploymentErrorHandler(FULL_SWAP, errorString).getFormattedErrorString();
    assertThat(result).contains(RERUN_OPTION);
    if (containsApplyChanges) {
      assertThat(result).contains(APPLY_CHANGES_OPTION);
    }
    else {
      assertThat(result).doesNotContain(APPLY_CHANGES_OPTION);
    }
  }
}
