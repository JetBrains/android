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
package com.android.tools.idea.refactoring.modularize

import com.intellij.psi.PsiElement
import com.intellij.refactoring.util.RefactoringUtil
import com.intellij.util.containers.Stack

class AndroidCodeAndResourcesGraph(
  private val graph: Map<PsiElement, Map<PsiElement, Int>>,
  val roots: Set<PsiElement>,
  private val referencedExternally: Set<PsiElement>,
) : RefactoringUtil.Graph<PsiElement> {

  override fun getVertices() = graph.keys

  override fun getTargets(source: PsiElement?): Set<PsiElement> = graph[source]?.keys ?: emptySet()

  val referencedOutsideScope: Set<PsiElement>
    get() {
      val stack = Stack<PsiElement>()
      val visited = HashSet<PsiElement>(graph.size)

      stack.addAll(referencedExternally)
      while (!stack.empty()) {
        val current = stack.pop()
        if (current !in visited) {
          visited += current
          stack += getTargets(current)
        }
      }

      return visited
    }

  fun getFrequency(source: PsiElement?, target: PsiElement?) = graph[source]?.get(target) ?: 0

  class Builder {
    private val roots: MutableSet<PsiElement> = hashSetOf()
    private val graph: MutableMap<PsiElement, MutableMap<PsiElement, Int>> = hashMapOf()
    private val referencedExternally: MutableSet<PsiElement> = hashSetOf()

    fun markReference(source: PsiElement, target: PsiElement) {
      val references = graph.getOrPut(source) { hashMapOf() }
      val count = references[target] ?: 0
      references[target] = count + 1
    }

    fun markReferencedOutsideScope(elm: PsiElement) {
      if (elm !in roots) {
        referencedExternally += elm
      }
    }

    fun addRoot(root: PsiElement) {
      roots += root
    }

    fun build() = AndroidCodeAndResourcesGraph(graph, roots, referencedExternally)
  }
}