/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.troubleshooting

import com.android.tools.idea.testing.AndroidProjectRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DesignToolsTroubleInfoCollectorTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun `verify default collectors`() {
    val output = DesignToolsTroubleInfoCollector().collectInfo(projectRule.project)
    assertEquals(
      """
        LastBuildResult: BuildResult(mode=UNKNOWN, status=UNKNOWN, timestampMillis=123)

        FastPreviewStatus: available=true disableReason=null

        Project:
        Module(light_idea_test_case): isLoaded=true isDisposed=false isGradleModule=false
          isAndroidTest=true isUnitTest=true
          useAndroidX=false rClassTransitive=true


        IssuePanelService: nIssues=0


      """
        .trimIndent(),
      output.replace(Regex("timestampMillis=\\d+"), "timestampMillis=123")
    )
  }
}
