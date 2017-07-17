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

import com.android.tools.idea.experimental.callgraph.*
import com.intellij.analysis.AnalysisScope
import junit.framework.TestCase
import org.jetbrains.android.AndroidTestCase

class InterproceduralThreadAnnotationTest : AndroidTestCase() {

  fun testJavaThreadAnnotations() = doTest(".java")

  private fun doTest(ext: String) {
    myFixture.copyFileToProject("callgraph/ThreadAnnotations" + ext)
    val files = buildUFiles(project, AnalysisScope(project))
    val nonContextualReceiverEval = buildIntraproceduralReceiverEval(files)
    val classHierarchy = buildClassHierarchy(files)
    val callGraph = buildCallGraph(files, nonContextualReceiverEval, classHierarchy)
    val paths = searchForInterproceduralThreadAnnotationViolations(callGraph, nonContextualReceiverEval)
    val namedPathSet = paths.map { (searchNodes, _, _) -> searchNodes.map { (node, _) -> node.shortName } }.toSet()
    val prefix = "ThreadAnnotations#"
    val expectedPathSet = setOf(
        arrayListOf("${prefix}uiThreadStatic", "${prefix}unannotatedStatic", "${prefix}workerThreadStatic"),
        arrayListOf("${prefix}uiThread", "${prefix}unannotated", "${prefix}workerThread"),
        arrayListOf("${prefix}callRunIt", "${prefix}runIt", "${prefix}callRunIt#lambda", "${prefix}runUi"),
        arrayListOf("A#run", "${prefix}b"),
        arrayListOf("B#run", "${prefix}a")
    )
    TestCase.assertEquals(expectedPathSet, namedPathSet)
  }
}