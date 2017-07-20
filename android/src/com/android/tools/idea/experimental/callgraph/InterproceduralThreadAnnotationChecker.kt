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

  fun CallTarget.isAnnotatedWith(annotation: String) = when (this) {
    is CallTarget.Method -> {
      element.isAnnotatedWith(annotation) || element.containingClass?.isAnnotatedWith(annotation) ?: false
    }
    is CallTarget.Lambda -> element.annotations.any { it.qualifiedName == annotation }
    is CallTarget.DefaultConstructor -> element.isAnnotatedWith(annotation)
  }

  val allSearchNodes = callGraph.buildAllReachableSearchNodes(nonContextualReceiverEval)
  val uiSearchNodes = allSearchNodes.filter { it.node.caller.isAnnotatedWith(UI_THREAD_ANNOTATION) }
  val workerSearchNodes = allSearchNodes.filter { it.node.caller.isAnnotatedWith(WORKER_THREAD_ANNOTATION) }

  // Some methods take in a lambda (say) and run it on a different thread.
  // By default our analysis would see that there is no direct call through the parameter, and correctly end the corresponding call path.
  // But we would also like to check that the parameter is able to run on the new thread.
  // To do this we find each parameter with a thread annotation, and treat all contextual receivers for that parameters as if they
  // had the thread annotation directly.
  fun invokeLaterSearchNodes(annotation: String) = allSearchNodes
      .flatMap { searchNode ->
        searchNode.paramContext.params
            .filter { (param, _) ->
              // Unwrapping the psi parameter is important for equality checks during annotation lookup.
              param.psi.isAnnotatedWith(annotation)
            }
            .map { it.second } // Pulls out receivers.
      }
      .mapNotNull { receiver ->
        val target = when (receiver) {
          is Receiver.Class -> null
          is Receiver.Lambda -> receiver.toTarget()
          is Receiver.CallableReference -> receiver.toTarget()
        }
        // We use an empty parameter context for the lambda (say) that will be invoked on the new thread,
        // as we don't know what arguments will be used when it is invoked later.
        target?.let { SearchNode(callGraph.getNode(it.element), ParamContext.EMPTY, cause = it.element) }
      }

  val allUiSearchNodes = uiSearchNodes + invokeLaterSearchNodes(UI_THREAD_ANNOTATION)
  val allWorkerSearchNodes = workerSearchNodes + invokeLaterSearchNodes(WORKER_THREAD_ANNOTATION)
  val uiPaths = callGraph.searchForPathsFromSearchNodes(allUiSearchNodes, allWorkerSearchNodes, nonContextualReceiverEval)
      .map { AnnotatedCallPath(it, UI_THREAD_ANNOTATION, WORKER_THREAD_ANNOTATION) }
  val workerPaths = callGraph.searchForPathsFromSearchNodes(allWorkerSearchNodes, allUiSearchNodes, nonContextualReceiverEval)
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
  private val classHierarchyVisitor = ClassHierarchyVisitor()
  private val nonContextualReceiverEval = IntraproceduralReceiverVisitor(classHierarchyVisitor.classHierarchy)
  private val callGraphVisitor = CallGraphVisitor(nonContextualReceiverEval, classHierarchyVisitor.classHierarchy)
  private val fileContexts = HashMap<UFile, JavaContext>()

  enum class State { BuildingClassHierarchy, EvaluatingReceivers, BuildingCallGraph }
  private var state = State.EvaluatingReceivers

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
            State.BuildingClassHierarchy -> uFile.accept(classHierarchyVisitor)
            State.EvaluatingReceivers -> uFile.accept(nonContextualReceiverEval)
            State.BuildingCallGraph -> uFile.accept(callGraphVisitor)
          }
        }
      }

  /** Advance the analysis state, returning false when there are no more state changes left. */
  private fun advanceState(): Boolean {
    when (state) {
      State.BuildingClassHierarchy -> state = State.EvaluatingReceivers
      State.EvaluatingReceivers -> state = State.BuildingCallGraph
      State.BuildingCallGraph -> return false
    }
    return true
  }

  override fun afterCheckProject(context: Context) {
    if (advanceState()) {
      context.driver.requestRepeat(this, SCOPE)
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
      val message = "Interprocedural thread annotation violation ($sourceStr to $sinkStr):\n$pathStr"
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