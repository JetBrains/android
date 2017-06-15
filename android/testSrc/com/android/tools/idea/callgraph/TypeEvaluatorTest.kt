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

import com.android.tools.idea.experimental.callgraph.StandardTypeEvaluator
import com.intellij.openapi.components.ServiceManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.impl.source.PsiImmediateClassType
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

class TypeEvaluatorTest : AndroidTestCase() {

  fun testJavaTypeEstimates() = doTest(".java")

  // TODO: fun testKotlinTypeEstimates() = doTest(".kt")

  private fun doTest(ext: String) {
    val virtualFile = myFixture.copyFileToProject("callgraph/TypeEstimates" + ext)
    val uastContext = ServiceManager.getService(project, UastContext::class.java)
    val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: throw Error("Failed to find PsiFile")
    val file = uastContext.convertWithParent<UFile>(psiFile) ?: throw Error("Failed to convert PsiFile to UFile")

    val typeEvaluator = StandardTypeEvaluator()
    file.accept(typeEvaluator)

    // Find class and variable references.
    val classMap = HashMap<String, UClass>()
    val varMap = HashMap<String, UVariable>()
    file.accept(object : AbstractUastVisitor() {
      override fun visitClass(node: UClass): Boolean {
        val name = node.psi.getName() ?: throw Error("Failed to get name of class")
        classMap[name] = node
        return super.visitClass(node)
      }

      override fun visitVariable(node: UVariable): Boolean {
        val name = node.psi.getName() ?: throw Error("Failed to get name of variable")
        varMap[name] = node
        return super.visitVariable(node)
      }
    })

    // For a given variable declared in TypeEstimates.java, check that the estimated type matches expectations.
    // A type is "covered" if it is included in the estimate.
    fun check(variable: String, covered: List<String>, notCovered: List<String>) {
      val varRef = varMap[variable] ?: throw Error("Could not find variable $variable")
      fun PsiClass.type() = PsiImmediateClassType(this, PsiSubstitutor.EMPTY)
      fun getTypes(classes: List<String>) = classes.map { classMap[it] }.requireNoNulls().map { it.type() }
      assert(getTypes(covered).all { typeEvaluator[varRef] covers it })
      assert(getTypes(notCovered).none { typeEvaluator[varRef] covers it })
    }

    check("subImpl",
        covered = listOf("SubImpl"),
        notCovered = listOf("It", "Impl"))
    check("impl",
        covered = listOf("Impl"),
        notCovered = listOf("It", "SubImpl"))
    check("uninitialized",
        covered = emptyList(),
        notCovered = listOf("It", "Impl", "SubImpl"))
    check("twiceAssignedUp",
        covered = listOf("SubImpl", "Impl"),
        notCovered = listOf("It"))
    check("twiceAssignedDown",
        covered = listOf("SubImpl", "Impl"),
        notCovered = listOf("It"))
  }
}