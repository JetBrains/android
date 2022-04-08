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
import java.util.Collections

class AndroidCodeAndResourcesGraph(
  private val graph: MutableMap<PsiElement, MutableMap<PsiElement, Int>>,
  private val roots: MutableSet<PsiElement>,
  private val referencedExternally: MutableSet<PsiElement>,
) : RefactoringUtil.Graph<PsiElement> {

  override fun getVertices(): MutableSet<PsiElement> {
    return graph.keys
  }

  override fun getTargets(source: PsiElement?): MutableSet<PsiElement> {
    val targets = graph.get(source)
    return if (targets == null) Collections.emptySet() else targets.keys
  }

  fun getRoots(): MutableSet<PsiElement> {
    return roots
  }

  fun getReferencedOutsideScope(): MutableSet<PsiElement> {
    val stack = Stack<PsiElement>()
    val visited = HashSet<PsiElement>(graph.size)

    for (root in referencedExternally) {
      stack.push(root)
    }
    while (!stack.isEmpty()) {
      val current = stack.pop()
      if (visited.add(current)) {
        for (succ in getTargets(current)) {
          stack.push(succ)
        }
      }
    }

    return visited
  }

  fun getFrequency(source: PsiElement?, target: PsiElement?): Int {
    val targets = graph.get(source)
    if (targets == null) {
      return 0
    }
    return targets.getOrDefault(target, 0)
  }

  class Builder {
    private val roots: MutableSet<PsiElement> = mutableSetOf()
    private val referenceGraph: MutableMap<PsiElement, MutableMap<PsiElement, Int>> = mutableMapOf()
    private val referencedExternally: MutableSet<PsiElement> = mutableSetOf()

    fun markReference(source: PsiElement, target: PsiElement): Unit {
      val references = referenceGraph.computeIfAbsent(source, { k -> mutableMapOf() })
      val count = references.getOrDefault(target, 0)
      references.put(target, count + 1)
    }
    fun markReferencedOutsideScope(elm: PsiElement): Unit {
      if (!roots.contains(elm)) {
        referencedExternally.add(elm)
      }
    }
    fun addRoot(root: PsiElement): Unit {
      roots.add(root)
    }
    fun build(): AndroidCodeAndResourcesGraph {
      return AndroidCodeAndResourcesGraph(referenceGraph, roots, referencedExternally)
    }
  }
}