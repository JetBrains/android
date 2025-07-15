/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.compose

import com.android.testutils.TestUtils.resolveWorkspacePath
import com.intellij.codeInsight.daemon.ProblemHighlightFilter
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.psi.PsiFile
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import java.nio.file.Files

fun getComposePluginTestDataPath(): String {
  val adtPath = resolveWorkspacePath("tools/adt/idea/compose-ide-plugin/testData")
  return if (Files.exists(adtPath)) adtPath.toString()
  else PathManagerEx.findFileUnderCommunityHome("plugins/android-compose-ide-plugin").path
}

fun maskKotlinProblemHighlightFilter(fixture: JavaCodeInsightTestFixture) {
  val extension =
    object : ProblemHighlightFilter() {
      override fun shouldHighlight(file: PsiFile): Boolean = true

      override fun shouldProcessInBatch(file: PsiFile) = true
    }
  ExtensionTestUtil.maskExtensions(
    ProblemHighlightFilter.EP_NAME,
    listOf(extension),
    fixture.testRootDisposable,
  )
}
