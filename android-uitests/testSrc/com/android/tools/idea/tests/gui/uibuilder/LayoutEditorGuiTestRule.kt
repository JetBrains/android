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
package com.android.tools.idea.tests.gui.uibuilder

import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.Conditions
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.LeakHunter
import com.intellij.util.PairProcessor
import com.intellij.util.ThrowableRunnable
import com.intellij.util.ref.DebugReflectionUtil
import java.util.*

/**
 * [Regex] that extracts the field from the backlink trace
 */
val SIMPLE_TRACE_EXTRACTOR = Regex("via '(.*?)'", RegexOption.MULTILINE)

/**
 * Whitelisted traces of known leaks
 */
val WHITELISTED_TRACES = listOf(
  // b/111111235
  listOf(
    "com.android.tools.adtui.workbench.SideModel.myContext",
    "com.android.tools.adtui.workbench.AttachedToolWindow.myModel"
  )
)

/**
 * Given a full [LeakHunter] trace, it returns a simplified view containing only the fields point to the leak.
 */
private fun extractSimpleTrace(fullTrace: String): List<String> =
  SIMPLE_TRACE_EXTRACTOR.findAll(fullTrace).flatMap { it.groupValues.asSequence().drop(1) }.toList()

/**
 * Returns whether the given simplified traces match. The [expected] signature can contain only a few elements of the stack, if all match,
 * the [actualTrace] is considered to match.
 */
private fun traceSignatureMatches(expected: List<String>, actualTrace: List<String>): Boolean {
  for (i in 0 until expected.size) {
    if (!actualTrace[i].startsWith(expected[i])) return false
  }

  return true
}

/**
 * Rule for Layout Editor UI tests. This rule is based on [GuiTestRule] and adds some additional checking that allows to detect
 * common leaks in the editor.
 */
class LayoutEditorGuiTestRule : GuiTestRule() {
  override fun tearDownProject() {
    val fileEditorManager = FileEditorManager.getInstance(ideFrame().project)
    EdtTestUtil.runInEdtAndWait(ThrowableRunnable {
      for (file in fileEditorManager.openFiles) {
        fileEditorManager.closeFile(file)
      }
    })

    // Checked for any leaked NlDesignSurfaces
    val nlDesignSurfaceLeaks = HashMap<Any, DebugReflectionUtil.BackLink>()
    DebugReflectionUtil.walkObjects(10000, LeakHunter.allRoots().get(),
                                    NlDesignSurface::class.java,
                                    Conditions.TRUE,
                                    PairProcessor { obj, backLink ->
                                      nlDesignSurfaceLeaks[obj] = backLink
                                      false
                                    })


    val leak = nlDesignSurfaceLeaks.values
      .map { Pair(it.toString(), extractSimpleTrace(it.toString())) }
      .filterNot { (_, simpleTrace) -> WHITELISTED_TRACES.any { traceSignatureMatches(it, simpleTrace) } }
      .firstOrNull()

    if (leak != null) {
      val (fullTrace, _) = leak
      throw AssertionError("Leak detected for class NlDesignSurface: $fullTrace")
    }

    super.tearDownProject()
  }
}
