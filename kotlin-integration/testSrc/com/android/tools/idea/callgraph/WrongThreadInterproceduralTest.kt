/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.callgraph

import com.android.tools.lint.checks.searchForInterproceduralThreadAnnotationViolations
import com.android.tools.lint.detector.api.interprocedural.shortName
import com.intellij.openapi.application.PathManager
import junit.framework.TestCase
import org.jetbrains.android.AndroidTestCase

class WrongThreadInterproceduralTest : AndroidTestCase() {

  fun testJavaThreadAnnotations() = doTest(".java")

  fun testKotlinThreadAnnotations() = doTest(".kt")

  private fun doTest(ext: String) {
    myFixture.testDataPath = PathManager.getHomePath() + "/../adt/idea/kotlin-integration/testData"
    val virtualFile = myFixture.copyFileToProject("callgraph/ThreadAnnotations" + ext, "src/ThreadAnnotations" + ext)
    val (_, receiverEval, graph) = buildInterproceduralAnalysesForTest(virtualFile, myFixture.project)
    val paths = searchForInterproceduralThreadAnnotationViolations(graph, receiverEval)

    val pathStrs = paths
        .map { (searchNodes, _, _) ->
          searchNodes.joinToString(separator = " -> ") { (contextualNode, _) ->
            contextualNode.node.shortName
          }
        }
        .toSortedSet()
        .joinToString(separator = "\n")

    val expectedPathStrs = listOf(
        "Test#uiThreadStatic -> Test#unannotatedStatic -> Test#workerThreadStatic",
        "Test#uiThread -> Test#unannotated -> Test#workerThread",
        "Test#callRunIt -> Test#runIt -> Test#callRunIt#lambda -> Test#runUi",
        "A#run -> Test#b",
        "B#run -> Test#a",
        "Test#callInvokeLater#lambda -> Test#c",
        "Test#callInvokeInBackground#lambda -> Test#d")
        .toSortedSet()
        .joinToString(separator = "\n")

    // Comparing the results as multiline strings helps with diff readability.
    TestCase.assertEquals(expectedPathStrs, pathStrs)
  }
}