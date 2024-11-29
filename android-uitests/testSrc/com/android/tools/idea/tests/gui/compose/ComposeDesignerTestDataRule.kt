/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.compose

import com.android.testutils.TestUtils
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.util.io.FileUtil
import java.io.File

/**
 * A [GuiTestRule] which overrides the project path to point to the Compose Designer test data.
 *
 * Tests using this rule will find their project data under
 * `tools/adt/idea/compose-designer/testData/projects`.
 */
class ComposeDesignerTestDataRule : GuiTestRule() {

  @Override
  override fun getMasterProjectDirPath(projectDirName: String): File {
    val testDataPath =
      ExternalSystemApiUtil.toCanonicalPath(
        FileUtil.toSystemDependentName(
          TestUtils.resolveWorkspacePath("tools/adt/idea/compose-designer/testData").toString()
        )
      );

    val composeProjectsRoot = File(testDataPath, "projects")
    return File(composeProjectsRoot, projectDirName);
  }
}