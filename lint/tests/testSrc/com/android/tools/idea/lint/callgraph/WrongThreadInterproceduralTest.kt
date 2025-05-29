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
package com.android.tools.idea.lint.callgraph

import com.android.test.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.lint.checks.searchForInterproceduralThreadAnnotationViolations
import com.android.tools.lint.detector.api.interprocedural.shortName
import com.android.tools.tests.AdtTestProjectDescriptors
import com.intellij.testFramework.LightProjectDescriptor
import junit.framework.TestCase
import org.jetbrains.android.LightJavaCodeInsightFixtureAdtTestCase

class WrongThreadInterproceduralTest : LightJavaCodeInsightFixtureAdtTestCase() {

  override fun getProjectDescriptor(): LightProjectDescriptor = AdtTestProjectDescriptors.kotlin()

  fun testJavaThreadAnnotations() = doTest(".java")

  fun testKotlinThreadAnnotations() = doTest(".kt")

  private fun addFile(file: String) = myFixture.copyFileToProject("callgraph/$file", "src/$file")

  private fun doTest(ext: String) {
    myFixture.testDataPath =
      resolveWorkspacePath("tools/adt/idea/lint/tests/testData/lint").toString()

    // Most of the test uses the new AndroidX annotations, but we make sure that the old annotations
    // work too.
    addFile("AndroidxAnnotations$ext")
    addFile("SupportAnnotations$ext")

    val virtualFile = addFile("ThreadAnnotations$ext")
    val (_, receiverEval, graph) =
      buildInterproceduralAnalysesForTest(virtualFile, myFixture.project)
    val paths = searchForInterproceduralThreadAnnotationViolations(graph, receiverEval)

    val pathStrs =
      paths
        .map { (searchNodes, _, _) ->
          searchNodes.joinToString(separator = " -> ") { (contextualNode, _) ->
            contextualNode.node.shortName
          }
        }
        .toSortedSet()
        .joinToString(separator = "\n")

    val expectedPathStrs =
      listOf(
          "Test#oldAnnotationA -> Test#oldAnnotationB -> Test#oldAnnotationC",
          "Test#uiThreadStatic -> Test#unannotatedStatic -> Test#workerThreadStatic",
          "Test#uiThread -> Test#unannotated -> Test#workerThread",
          "Test#callRunIt -> Test#runIt -> Test#callRunIt#lambda -> Test#runUi",
          "A#run -> Test#b",
          "B#run -> Test#a",
          "Test#callInvokeLater#lambda -> Test#c",
          "Test#callInvokeInBackground#lambda -> Test#d",
        )
        .toSortedSet()
        .joinToString(separator = "\n")

    // Comparing the results as multiline strings helps with diff readability.
    TestCase.assertEquals(expectedPathStrs, pathStrs)
  }
}
