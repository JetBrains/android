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

import com.android.tools.idea.lint.LintIdeClient
import com.android.tools.idea.lint.LintIdeRequest
import com.android.tools.lint.checks.SupportAnnotationDetector.UI_THREAD_ANNOTATION
import com.android.tools.lint.checks.SupportAnnotationDetector.WORKER_THREAD_ANNOTATION
import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.LintDriver
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.intellij.analysis.AnalysisScope
import com.intellij.analysis.BaseAnalysisAction
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.uast.UFile
import org.jetbrains.uast.getContainingFile
import java.util.*
import kotlin.system.measureTimeMillis

private val LOG = Logger.getInstance(InterproceduralThreadAnnotationChecker::class.java)

data class AnnotatedCallPath(
    val searchNodes: List<SearchNode>,
    val sourceAnnotation: String,
    val sinkAnnotation: String
)

/** Returns a collection of call paths that violate thread annotations found in source code. */
fun searchForInterproceduralThreadAnnotationViolations(callGraph: CallGraph,
                                                       nonContextualReceiverEval: CallReceiverEvaluator): Collection<AnnotatedCallPath> {
  fun PsiModifierListOwner.isAnnotatedWith(annotation: String) =
      AnnotationUtil.isAnnotated(this, annotation, /*inHierarchy*/ true, /*skipExternal*/ false)

  fun CallGraph.nodesAnnotatedWith(annotation: String) = nodes.filter {
    val target = it.caller
    when (target) {
      is CallTarget.Method -> {
        target.element.isAnnotatedWith(annotation) || target.element.containingClass?.isAnnotatedWith(annotation) ?: false
      }
      is CallTarget.Lambda -> target.element.annotations.any { it.qualifiedName == annotation }
      is CallTarget.DefaultConstructor -> target.element.isAnnotatedWith(annotation)
    }
  }

  val uiNodes = callGraph.nodesAnnotatedWith(UI_THREAD_ANNOTATION)
  val workerNodes = callGraph.nodesAnnotatedWith(WORKER_THREAD_ANNOTATION)
  val uiPaths = callGraph.searchForPaths(uiNodes, workerNodes, nonContextualReceiverEval)
      .map { AnnotatedCallPath(it, UI_THREAD_ANNOTATION, WORKER_THREAD_ANNOTATION) }
  val workerPaths = callGraph.searchForPaths(workerNodes, uiNodes, nonContextualReceiverEval)
      .map { AnnotatedCallPath(it, WORKER_THREAD_ANNOTATION, UI_THREAD_ANNOTATION) }
  return uiPaths + workerPaths
}

/** Uses a call graph to more precisely check for thread annotation violations. */
class InterproceduralThreadAnnotationChecker : BaseAnalysisAction(ANALYSIS_NAME, ANALYSIS_NAME) {

  companion object {
    private const val ANALYSIS_NAME = "Interprocedural Thread Annotation Checker"
  }

  override fun analyze(project: Project, scope: AnalysisScope) {
    val time = measureTimeMillis {
      val client = LintIdeClient.forBatch(project, mutableMapOf(), scope, setOf(InterproceduralThreadAnnotationDetector.ISSUE))
      try {
        val files = ArrayList<VirtualFile>()
        scope.accept { files.add(it) }
        val modules = ModuleManager.getInstance(project).modules.toList()
        val request = LintIdeRequest(client, project, files, modules, /*incremental*/ false)
        request.setScope(InterproceduralThreadAnnotationDetector.SCOPE)
        val issue = object : IssueRegistry() {
          override fun getIssues() = listOf(InterproceduralThreadAnnotationDetector.ISSUE)
        }
        LintDriver(issue, client, request).analyze()
      }
      finally {
        Disposer.dispose(client)
      }
    }
    LOG.info("Interprocedural thread annotation check: ${time}ms")
  }
}

class AndroidLintInterproceduralThreadAnnotationInspection : AndroidLintInspectionBase(
    AndroidBundle.message("android.lint.inspections.wrong.thread"),
    InterproceduralThreadAnnotationDetector.ISSUE)

// This could also implement Detector.ClassScanner in order to analyze byte code, at least
// for building a more complete class hierarchy.
class InterproceduralThreadAnnotationDetector : Detector(), Detector.UastScanner {
  private val nonContextualReceiverEval = IntraproceduralReceiverVisitor()
  private val classHierarchyVisitor = ClassHierarchyVisitor()
  private val callGraphVisitor = CallGraphVisitor(nonContextualReceiverEval, classHierarchyVisitor.classHierarchy)
  private val fileContexts = HashMap<UFile, JavaContext>()

  enum class State { GatheringContext, BuildingCallGraph }
  private var state = State.GatheringContext

  override fun getApplicableUastTypes() = listOf(UFile::class.java)

  override fun beforeCheckFile(context: Context) {
    if (context is JavaContext) {
      context.uastFile?.let { fileContexts[it] = context }
    }
    super.beforeCheckFile(context)
  }

  override fun createUastHandler(context: JavaContext): UElementHandler =
      object : UElementHandler() {
        override fun visitFile(uFile: UFile) {
          when (state) {
            State.GatheringContext -> {
              LOG.info("Gathering context from ${uFile.psi.name}")
              uFile.accept(nonContextualReceiverEval)
              uFile.accept(classHierarchyVisitor)
            }
            State.BuildingCallGraph -> {
              LOG.info("Building call graph for ${uFile.psi.name}")
              uFile.accept(callGraphVisitor)
            }
          }
        }
      }

  override fun afterCheckProject(context: Context) {
    if (state == State.GatheringContext) {
      context.driver.requestRepeat(this, SCOPE)
      state = State.BuildingCallGraph
      return
    }
    val badPaths = searchForInterproceduralThreadAnnotationViolations(callGraphVisitor.callGraph, nonContextualReceiverEval)
    for ((searchNodes, sourceAnnotation, sinkAnnotation) in badPaths) {
      if (searchNodes.size == 1) {
        // This means that a node in the graph was annotated with both UiThread and WorkerThread.
        // This can happen if an overriding method changes the annotation.
        continue
      }
      val (_, second) = searchNodes
      val pathBeginning = second.cause
      val containingFile = pathBeginning.getContainingFile() ?: continue
      val javaContext = fileContexts[containingFile] ?: continue
      javaContext.setJavaFile(containingFile.psi) // Needed for getLocation.
      val location = javaContext.getLocation(pathBeginning)
      val pathStr = searchNodes.joinToString(separator = " -> ") { it.node.shortName }
      val sourceStr = sourceAnnotation.substringAfterLast('.')
      val sinkStr = sinkAnnotation.substringAfterLast('.')
      val message = "Interprocedural thread annotation violation (${sourceStr} to ${sinkStr}):\n${pathStr}"
      context.report(ISSUE, location, message, null)
      LOG.info(message)
    }
  }

  companion object {
    val SCOPE: EnumSet<Scope> = EnumSet.of(Scope.JAVA_FILE)
    val ISSUE = Issue.create(
        "InterproceduralThreadAnnotationInspection",
        "Wrong Thread",
        "This lint check searches for interprocedural call paths that violate thread annotations in the program.",
        Category.CORRECTNESS,
        /*priority*/ 6,
        Severity.ERROR,
        Implementation(InterproceduralThreadAnnotationDetector::class.java, SCOPE)
    )
  }
}