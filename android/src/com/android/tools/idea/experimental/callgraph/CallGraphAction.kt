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

import com.android.tools.lint.detector.api.interprocedural.buildCallGraph
import com.android.tools.lint.detector.api.interprocedural.buildClassHierarchy
import com.android.tools.lint.detector.api.interprocedural.buildIntraproceduralReceiverEval
import com.intellij.analysis.AnalysisScope
import com.intellij.analysis.BaseAnalysisAction
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UastContext
import org.jetbrains.uast.convertWithParent

/** Creates a collection of UFiles from a project and scope. */
fun buildUFiles(project: Project, scope: AnalysisScope): Collection<UFile> {
  val res = ArrayList<UFile>()
  val uastContext = ServiceManager.getService(project, UastContext::class.java)
  scope.accept { virtualFile ->
    if (!uastContext.isFileSupported(virtualFile.name)) return@accept true
    val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return@accept true
    val file = uastContext.convertWithParent<UFile>(psiFile) ?: return@accept true
    res.add(file)
  }
  return res
}

/** Builds a call graph and prints a description. */
class CallGraphAction : BaseAnalysisAction("Call Graph", "Call Graph") {
  private val LOG = Logger.getInstance(CallGraphAction::class.java)

  override fun analyze(project: Project, scope: AnalysisScope) {
    val files = buildUFiles(project, scope)
    val classHierarchy = buildClassHierarchy(files)
    val nonContextualReceiverEval = buildIntraproceduralReceiverEval(files, classHierarchy)
    val callGraph = buildCallGraph(files, nonContextualReceiverEval, classHierarchy)
    LOG.info(callGraph.toString())
    LOG.info(callGraph.dump())
  }
}