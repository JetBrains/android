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
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import junit.framework.TestCase

// TODO: This should be an AndroidTestCase, but that breaks overriding method search (for unknown reasons).
class CallGraphTest : LightCodeInsightFixtureTestCase() {

  override fun getBasePath(): String = "adt/idea/android/testData/"

  fun testJavaCallGraph() = doTest("callgraph/CallGraph.java")

  private fun doTest(file: String) {
    myFixture.copyFileToProject(file)
    val files = buildUFiles(project, AnalysisScope(project))
    val receiverEval = buildIntraproceduralReceiverEval(files)
    val graph = buildCallGraph(files, receiverEval)
    val nodeMap = graph.nodes.associateBy({ it.shortName })

    fun String.assertCalls(vararg callees: String) {
      val node = nodeMap[this] ?: if (callees.isEmpty()) return else throw Error("No node found for ${this}")
      val actual = node.likelyEdges.map { it.node.shortName }
      TestCase.assertEquals("Unexpected callees for ${this}", callees.sorted(), actual.sorted())
    }

    fun String.findPath(callee: String): List<String>? {
      val source = nodeMap.getValue(this)
      val sink = nodeMap.getValue(callee)
      return graph.searchForPaths(listOf(source), listOf(sink), receiverEval).firstOrNull()?.map { it.shortName }
    }

    fun String.assertReaches(callee: String) = TestCase.assertNotNull("${this} should reach ${callee}", this.findPath(callee))
    fun String.assertDoesNotReach(callee: String) = TestCase.assertNull("${this} should not reach ${callee}", this.findPath(callee))

    // Check simple call chains.
    "Trivial#empty".assertCalls(/*nothing*/)
    for (kind in listOf("static", "private", "public")) {
      val prefix = "Trivial#${kind}"
      "${prefix}1".assertCalls("${prefix}2")
      "${prefix}2".assertCalls("${prefix}3")
      "${prefix}3".assertCalls(/*nothing*/)
      "${prefix}1".assertReaches("${prefix}3")
      "${prefix}3".assertDoesNotReach("${prefix}1")
    }

    // Check calls relying on call hierarchy analysis and type estimates for local variables.
    "SimpleLocal#notUnique".assertCalls(/*nothing*/)
    "SimpleLocal#unique".assertCalls("Impl#implUnique")
    "SimpleLocal#typeEvidencedSubImpl".assertCalls("SubImpl#f", "SubImpl#defaultCtor")
    "SimpleLocal#typeEvidencedImpl".assertCalls("Impl#f", "Impl#defaultCtor")
    "SimpleLocal#typeEvidencedBoth".assertCalls("SubImpl#f", "Impl#f", "SubImpl#defaultCtor", "Impl#defaultCtor")

    // Check calls through fields.
    "SimpleField#notUnique".assertCalls(/*nothing*/)
    "SimpleField#unique".assertCalls("Impl#implUnique")
    "SimpleField#typeEvidencedSubImpl".assertCalls("SubImpl#f")
    "SimpleField#typeEvidencedImpl".assertCalls("Impl#f")
    "SimpleField#typeEvidencedBoth".assertCalls("SubImpl#f", "Impl#f")

    // Test special calls through super.
    "Special#Special".assertCalls("Special#h", "Object#Object")
    "SubSpecial#defaultCtor".assertCalls("Special#Special")
    "SubSpecial#f".assertCalls("Special#f")
    "SubSubSpecial#SubSubSpecial".assertCalls("SubSpecial#defaultCtor")
    "SubSubSpecial#g".assertCalls("Special#g")
    "SubSubSubSpecial#SubSubSubSpecial".assertCalls("SubSubSpecial#SubSubSpecial")

    // Test class and field initializers.
    "Initializers#defaultCtor".assertCalls("Object#Object", "Nested#f", "Empty#defaultCtor", "Inner#Inner", "Nested#g")
    "Inner#defaultCtor".assertCalls(/*nothing*/)
    "Nested#Nested".assertCalls("Nested#h", "Object#Object")
    assert("Nested#defaultCtor" !in nodeMap)

    // Test lambdas.
    "Lambdas#g".assertCalls("Lambdas#f")
    "Lambdas#h".assertCalls("Lambdas#h#lambda")
    "Lambdas#h#lambda".assertCalls("Lambdas#f", "Lambdas#g")

    // Test contextual call paths relying on single argument.
    "Contextual#a".assertReaches("Contextual#f")
    "Contextual#a".assertDoesNotReach("Contextual#g")
    "Contextual#b".assertReaches("Contextual#g")
    "Contextual#b".assertDoesNotReach("Contextual#f")
    "Contextual#run".assertReaches("Contextual#f")
    "Contextual#run".assertReaches("Contextual#g")

    // Test contextual call paths relying on multiple arguments.
    "Contextual#multiArgA".assertReaches("Contextual#f")
    "Contextual#multiArgA".assertReaches("MultiArgA#run")
    "Contextual#multiArgA".assertDoesNotReach("Contextual#g")
    "Contextual#multiArgA".assertDoesNotReach("MultiArgB#run")
    "MultiArgA#run".assertReaches("Contextual#f")
    "MultiArgA#run".assertDoesNotReach("Contextual#g")

    // Test contextual call paths also relying on implicit `this`.
    "Contextual#implicitThisA".assertReaches("Contextual#f")
    "Contextual#implicitThisA".assertReaches("ImplicitThisA#myRun")
    "Contextual#implicitThisA".assertDoesNotReach("Contextual#g")
    "Contextual#implicitThisA".assertDoesNotReach("ImplicitThisB#myRun")
    "ImplicitThisA#myRun".assertReaches("Contextual#f")
    "ImplicitThisA#myRun".assertDoesNotReach("Contextual#g")

    // Test long contextual paths.
    "Contextual#c".assertReaches("Contextual#f")
    "Contextual#c".assertReaches("Contextual#run")
    "Contextual#run3".assertReaches("Contextual#c#lambda")
    "Contextual#run3".assertDoesNotReach("Contextual#g")
  }
}
