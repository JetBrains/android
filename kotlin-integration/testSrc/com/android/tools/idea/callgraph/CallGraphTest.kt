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

import com.android.tools.lint.detector.api.interprocedural.*
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import junit.framework.TestCase
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UastContext
import org.jetbrains.uast.convertWithParent

fun buildInterproceduralAnalysesForTest(
    virtualFile: VirtualFile,
    project: Project): Triple<ClassHierarchy, IntraproceduralDispatchReceiverEvaluator, CallGraph> {
  val uastContext = ServiceManager.getService(project, UastContext::class.java)
  val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: throw Error("Failed to find PsiFile")
  val file = uastContext.convertWithParent<UFile>(psiFile) ?: throw Error("Failed to convert PsiFile to UFile")
  val cha = ClassHierarchyVisitor()
      .also { file.accept(it) }
      .classHierarchy
  val receiverEval = IntraproceduralDispatchReceiverVisitor(cha)
      .also { file.accept(it) }
      .receiverEval
  val graph = CallGraphVisitor(receiverEval, cha)
      .also { file.accept(it) }
      .callGraph
  return Triple(cha, receiverEval, graph)
}

class CallGraphTest : AndroidTestCase() {

  fun testJavaCallGraph() = doTest(".java")

  fun testKotlinCallGraph() = doTest(".kt")

  private fun doTest(ext: String) {
    myFixture.testDataPath = PathManager.getHomePath() + "/../adt/idea/kotlin-integration/testData"
    val virtualFile = myFixture.copyFileToProject("callgraph/CallGraph" + ext, "src/CallGraph" + ext)
    val (_, receiverEval, graph) = buildInterproceduralAnalysesForTest(virtualFile, myFixture.project)
    val nodeMap = graph.nodes.associateBy({ it.shortName })

    fun String.assertCalls(vararg callees: String) {
      val node = nodeMap[this] ?: if (callees.isEmpty()) return else throw Error("No node found for ${this}")
      val actual = node.likelyEdges.map { it.node.shortName }.toSet().toList()
      TestCase.assertEquals("Unexpected callees for ${this}", callees.sorted(), actual.sorted())
    }

    fun String.findPath(callee: String): List<String>? {
      val source = nodeMap.getValue(this)
      val sink = nodeMap.getValue(callee)
      val paths = graph.searchForPaths(listOf(source), listOf(sink), receiverEval)
      return paths.firstOrNull()?.map { (contextualNode, _) -> contextualNode.node.shortName }
    }

    fun String.assertReaches(callee: String) = TestCase.assertNotNull("${this} should reach $callee", this.findPath(callee))
    fun String.assertDoesNotReach(callee: String) = TestCase.assertNull("${this} should not reach $callee", this.findPath(callee))

    // Check simple call chains.
    "Trivial#empty".assertCalls(/*nothing*/)
    for (kind in listOf("static", "private", "public")) {
      val prefix = "Trivial#$kind"
      "${prefix}1".assertCalls("${prefix}2")
      "${prefix}2".assertCalls("${prefix}3")
      "${prefix}3".assertCalls(/*nothing*/)
      "${prefix}1".assertReaches("${prefix}3")
      "${prefix}3".assertDoesNotReach("${prefix}1")
    }

    // Check calls relying on call hierarchy analysis and type estimates for local variables.
    "SimpleLocal#notUnique".assertCalls(/*nothing*/)
    "SimpleLocal#unique".assertCalls("Impl#implUnique")
    "SimpleLocal#typeEvidencedSubImpl".assertCalls("SubImpl#f", "SubImpl#SubImpl")
    "SimpleLocal#typeEvidencedImpl".assertCalls("Impl#f", "Impl#Impl")
    "SimpleLocal#typeEvidencedBoth".assertCalls("SubImpl#f", "Impl#f", "SubImpl#SubImpl", "Impl#Impl")

    // Check calls through fields and array elements.
    for (kind in listOf("Field", "Array")) {
      "Simple$kind#notUnique".assertCalls(/*nothing*/)
      "Simple$kind#unique".assertCalls("Impl#implUnique")
      "Simple$kind#typeEvidencedSubImpl".assertCalls("SubImpl#f")
      "Simple$kind#typeEvidencedImpl".assertCalls("Impl#f")
      "Simple$kind#typeEvidencedBoth".assertCalls("SubImpl#f", "Impl#f")
    }

    // Test special calls through super.
    "Special#Special".assertCalls("Special#h", "Object#Object")
    "SubSpecial#SubSpecial".assertCalls("Special#Special")
    "SubSpecial#f".assertCalls("Special#f")
    "SubSubSpecial#SubSubSpecial".assertCalls("SubSpecial#SubSpecial")
    "SubSubSpecial#g".assertCalls("Special#g")
    "SubSubSubSpecial#SubSubSubSpecial".assertCalls("SubSubSpecial#SubSubSpecial")

    // Test class and field initializers.
    "Initializers#Initializers".assertCalls("Object#Object", "Nested#f", "Empty#Empty", "Inner#Inner", "Nested#g")
    "Inner#Inner".assertCalls("Object#Object")
    "Nested#Nested".assertCalls("Nested#h", "Object#Object")

    // Test return values.
    "Return#unique".assertCalls("Return#createRetUniqueIt", "RetUnique#f")
    "Return#ambig".assertCalls("Return#createRetAmbigNull")
    "Return#evidenced".assertCalls("Return#evidenced1", "RetAmbigA#f")

    // Test lambdas.
    "Lambdas#g".assertCalls("Lambdas#f")
    "Lambdas#h".assertCalls("Lambdas#h#lambda")
    "Lambdas#h#lambda".assertCalls("Lambdas#f", "Lambdas#g")
    "Lambdas#i".assertReaches("Lambdas#f")

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
    "Contextual#implicitThisB".assertReaches("Contextual#g")
    "Contextual#implicitThisB".assertReaches("ImplicitThisB#myRun")
    "Contextual#implicitThisB".assertDoesNotReach("Contextual#f")
    "Contextual#implicitThisB".assertDoesNotReach("ImplicitThisA#myRun")
    "ImplicitThisA#myRun".assertReaches("Contextual#f")
    "ImplicitThisA#myRun".assertDoesNotReach("Contextual#g")

    // Test long contextual paths.
    "Contextual#c".assertReaches("Contextual#f")
    "Contextual#c".assertReaches("Contextual#run")
    "Contextual#c".assertDoesNotReach("Contextual#g")
    "Contextual#run3".assertReaches("Contextual#f")
    "Contextual#run3".assertDoesNotReach("Contextual#g")

    // Kotlin-specific (e.g., top-level functions).
    if (ext == ".kt") {
      "CallGraphKt#topLevelA".assertCalls("CallGraphKt#topLevelB")
      "CallGraphKt#topLevelB".assertCalls("CallGraphKt#topLevelC")
      "CallGraphKt#topLevelA".assertReaches("CallGraphKt#topLevelC")
      "CallGraphKt#topLevelC".assertDoesNotReach("CallGraphKt#topLevelA")
    }
    else {
      assert(ext == ".java")
    }
  }
}