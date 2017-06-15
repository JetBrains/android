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
package com.android.tools.idea.experimental.callgraph

import com.android.tools.idea.experimental.callgraph.CallGraph.Edge
import com.intellij.analysis.AnalysisScope
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.OverridingMethodsSearch
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor
import kotlin.system.measureTimeMillis

private val LOG = Logger.getInstance("#com.android.tools.idea.experimental.callgraph.CallGraphBuilder")

fun buildCallGraph(project: Project, scope: AnalysisScope, filterLikely: Boolean = true): CallGraph {
  val uastContext = ServiceManager.getService(project, UastContext::class.java)
  val callGraphVisitor = CallGraphVisitor(project, filterLikely)
  val time = measureTimeMillis {
    scope.accept { virtualFile ->
      if (!uastContext.isFileSupported(virtualFile.name)) return@accept true
      val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return@accept true
      val file = uastContext.convertWithParent<UFile>(psiFile) ?: return@accept true
      LOG.info("Adding ${psiFile.name} to call graph")
      LOG.info("${callGraphVisitor.callGraph}")
      file.accept(callGraphVisitor)
      true
    }
  }
  LOG.info("Call graph built in ${time}ms")
  return callGraphVisitor.callGraph
}

class CallGraphVisitor(val project: Project, val filterLikely: Boolean = true) : AbstractUastVisitor() {
  private val typeEvaluator = StandardTypeEvaluator()
  private val mutableCallGraph: MutableCallGraph = MutableCallGraph()
  val callGraph: CallGraph get() = mutableCallGraph

  override fun visitMethod(node: UMethod): Boolean {
    node.accept(typeEvaluator)
    return super.visitMethod(node)
  }

  override fun visitCallExpression(node: UCallExpression): Boolean {
    // TODO: For field and non-static class initializers, we may want to use each constructor as the implicit caller.
    val baseCallee = node.resolve() ?: return super.visitCallExpression(node)
    val caller = node.getParentOfType(/*strict*/ true, UMethod::class.java, ULambdaExpression::class.java)?.psi
        ?: return super.visitCallExpression(node)

    val callerNode = mutableCallGraph.getNode(caller)

    fun PsiMethod.isCallable() = when {
      // TODO: Update for Kotlin
      hasModifierProperty(PsiModifier.ABSTRACT) -> false
      containingClass?.isInterface == true -> hasModifierProperty(PsiModifier.DEFAULT)
      else -> true
    }

    fun PsiMethod.canBeOverriden(): Boolean {
      // TODO: Update for Kotlin
      val parentClass = containingClass
      return parentClass != null
          && !isConstructor
          && !hasModifierProperty(PsiModifier.STATIC)
          && !hasModifierProperty(PsiModifier.FINAL)
          && !hasModifierProperty(PsiModifier.PRIVATE)
          && parentClass !is PsiAnonymousClass
          && !parentClass.hasModifierProperty(PsiModifier.FINAL)
    }


    if (!baseCallee.canBeOverriden()) {
      mutableCallGraph.addEdge(callerNode, baseCallee, Edge.Kind.DIRECT)
    }
    else {
      // TODO: Searching for overriding methods can be slow; may need to add caching.
      val overrides = OverridingMethodsSearch.search(baseCallee).findAll()
      if (overrides.isEmpty()) {
        mutableCallGraph.addEdge(callerNode, baseCallee, Edge.Kind.UNIQUE_OVERRIDE)
      }
      else if (!baseCallee.isCallable() && overrides.size == 1) {
        mutableCallGraph.addEdge(callerNode, overrides.first(), Edge.Kind.UNIQUE_OVERRIDE)
      }
      else {
        // Use runtime type estimates to try to indicate which overriding methods are likely targets.
        val receiverVar = (node.receiver as? USimpleNameReferenceExpression)?.resolveToUElement() as? UVariable
        val estimatedType = receiverVar?.let { typeEvaluator[it] } ?: TypeEstimate.BOTTOM
        fun PsiMethod.isTypeEvidenced(): Boolean {
          val parentClass = containingClass ?: return false
          val parentClassName = parentClass.qualifiedName ?: return false
          val parentClassType = PsiType.getTypeByName(parentClassName, project, GlobalSearchScope.allScope(project)) ?: return false
          return estimatedType.covers(parentClassType)
        }
        loop@ for (callee in (overrides + baseCallee)) {
          val edgeKind = when {
            callee.isTypeEvidenced() -> Edge.Kind.TYPE_EVIDENCED
            filterLikely -> continue@loop
            else -> Edge.Kind.NON_UNIQUE_OVERRIDE
          }
          mutableCallGraph.addEdge(callerNode, callee, edgeKind)
        }
      }
    }

    return super.visitCallExpression(node)
  }
}