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

import com.android.tools.lint.checks.SupportAnnotationDetector.UI_THREAD_ANNOTATION
import com.android.tools.lint.checks.SupportAnnotationDetector.WORKER_THREAD_ANNOTATION
import com.intellij.analysis.AnalysisScope
import com.intellij.analysis.BaseAnalysisAction
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor
import kotlin.system.measureTimeMillis

private val LOG = Logger.getInstance(InterproceduralThreadAnnotationChecker::class.java)

/** Returns a collection of call paths that violate thread annotations found in source code. */
fun searchForInterproceduralThreadAnnotationViolations(project: Project, scope: AnalysisScope): Collection<List<PsiElement>> {
  val uiThreadMethods = ArrayList<UMethod>()
  val workerThreadMethods = ArrayList<UMethod>()
  val threadAnnotatedMethodFinder = object : AbstractUastVisitor() {
    override fun visitClass(node: UClass): Boolean {
      node.annotations.map { it.qualifiedName }.filterNotNull().forEach {
        when (it) {
          UI_THREAD_ANNOTATION -> uiThreadMethods.addAll(node.getMethods())
          WORKER_THREAD_ANNOTATION -> workerThreadMethods.addAll(node.getMethods())
        }
      }
      return super.visitClass(node)
    }

    override fun visitMethod(node: UMethod): Boolean {
      node.annotations.map { it.qualifiedName }.filterNotNull().forEach {
        when (it) {
          UI_THREAD_ANNOTATION -> uiThreadMethods.add(node)
          WORKER_THREAD_ANNOTATION -> workerThreadMethods.add(node)
        }
      }
      return super.visitMethod(node)
    }
  }

  val uastContext = ServiceManager.getService(project, UastContext::class.java)
  scope.accept { virtualFile ->
    if (!uastContext.isFileSupported(virtualFile.name)) return@accept true
    val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return@accept true
    val file = uastContext.convertWithParent<UFile>(psiFile) ?: return@accept true
    LOG.info("Finding annotated methods in ${psiFile.name}")
    file.accept(threadAnnotatedMethodFinder)
    true
  }

  val callGraph = buildCallGraph(project, scope)
  val uiNodes = uiThreadMethods.map { callGraph.getNode(it.psi) }
  val workerNodes = workerThreadMethods.map { callGraph.getNode(it.psi) }
  fun getNeighbors(node: CallGraph.Node) = node.likelyEdges.map { it.node }
  val paths = searchForPaths(uiNodes, workerNodes, ::getNeighbors) + searchForPaths(workerNodes, uiNodes, ::getNeighbors)
  return paths.map { path -> path.map { it.method } }
}

private const val ANALYSIS_NAME = "Interprocedural Thread Annotation Checker"

/** Uses a call graph to more precisely check for thread annotation violations. */
class InterproceduralThreadAnnotationChecker : BaseAnalysisAction(ANALYSIS_NAME, ANALYSIS_NAME) {

  override fun analyze(project: Project, scope: AnalysisScope) {
    val time = measureTimeMillis {
      val paths = searchForInterproceduralThreadAnnotationViolations(project, scope)
      paths.forEach { LOG.info("Found bad path: ${it}") }
    }
    LOG.info("Interprocedural thread annotation check: ${time}ms")
  }
}