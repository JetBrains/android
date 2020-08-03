/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.imports

import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.getIntentionAction
import com.android.tools.idea.testing.highlightedAs
import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.testing.moveCaret
import com.intellij.lang.annotation.HighlightSeverity.ERROR
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.psi.PsiManager
import org.jetbrains.android.dom.inspections.AndroidUnresolvableTagInspection

class AndroidMavenImportFixTest : AndroidGradleTestCase() {
  fun testMissingClassHighlightingAndAddLibraryQuickfix() {
    if (SystemInfoRt.isWindows) {
      return  // TODO(b/162746659) failing on windows
    }
    val inspection = AndroidUnresolvableTagInspection()
    myFixture.enableInspections(inspection)

    loadProject(TestProjectPaths.MIGRATE_TO_APP_COMPAT) // project not using AndroidX
    assertBuildGradle { !it.contains("com.android.support:recyclerview-v7:") } // not already using recyclerview

    myFixture.loadNewFile(
      "app/src/main/res/layout/my_layout.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <${"android.support.v7.widget.RecyclerView".highlightedAs(ERROR, "Cannot resolve class android.support.v7.widget.RecyclerView")} />
      """.trimIndent()
    )

    myFixture.checkHighlighting(true, false, false)
    myFixture.moveCaret("Recycler|View")
    val action = myFixture.getIntentionAction("Add dependency on com.android.support:recyclerview-v7")!!

    assertTrue(action.isAvailable(myFixture.project, myFixture.editor, myFixture.file))
    WriteCommandAction.runWriteCommandAction(myFixture.project, Runnable {
      action.invoke(myFixture.project, myFixture.editor, myFixture.file)
    })

    // Wait for the sync
    requestSyncAndWait() // this is redundant but we can't get a handle on the internal sync state of the first action

    assertBuildGradle { it.contains("implementation 'com.android.support:recyclerview-v7:") }
  }

  private fun assertBuildGradle(check: (String) -> Unit) {
    val buildGradle = project.guessProjectDir()!!.findFileByRelativePath("app/build.gradle")
    val buildGradlePsi = PsiManager.getInstance(project).findFile(buildGradle!!)
    check(buildGradlePsi!!.text)
  }
}
