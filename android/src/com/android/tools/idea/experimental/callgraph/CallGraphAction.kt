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

import com.intellij.analysis.AnalysisScope
import com.intellij.analysis.BaseAnalysisAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.getContainingClass
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.IOException
import java.io.PrintWriter

/** Builds a call graph and prints a description, optionally to a dot file. */
class CallGraphAction : BaseAnalysisAction("Call Graph", "Call Graph") {
  private val LOG = Logger.getInstance(CallGraphAction::class.java)

  override fun analyze(project: Project, scope: AnalysisScope) {
    val callGraph = buildCallGraph(project, scope)
    LOG.info(callGraph.toString())
    dump(callGraph)
  }

  private fun dump(callGraph: CallGraph, dotFile: String? = null) {
    fun prettyPrint(callable: PsiElement): String =
      buildString {
        append(callable.getContainingClass()?.name ?: "anonymous")
        append('#')
        append((callable as? PsiMethod)?.name ?: "lambda")
      }

    if (LOG.isTraceEnabled) {
      for (node in callGraph.nodes) {
        val callees = node.likelyEdges
        LOG.trace(prettyPrint(node.method))
        callees.forEach { LOG.trace("    ${prettyPrint(it.node.method)} [${it.kind}]") }
      }
    }

    if (LOG.isTraceEnabled && dotFile != null) {
      try {
        PrintWriter(BufferedWriter(FileWriter(dotFile))).use { writer ->
          writer.println("digraph {")
          for (node in callGraph.nodes) {
            for ((callee, _) in node.likelyEdges) {
              writer.print("  \"${prettyPrint(node.method)}\" -> \"${prettyPrint(callee.method)}\"")
            }
          }
          writer.println("}")
        }
      }
      catch (e: IOException) {
        LOG.warn(e)
      }
    }
  }
}