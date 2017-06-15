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

import com.android.tools.idea.experimental.callgraph.searchForInterproceduralThreadAnnotationViolations
import com.intellij.analysis.AnalysisScope
import com.intellij.psi.PsiMethod
import junit.framework.TestCase
import org.jetbrains.android.AndroidTestCase

class InterproceduralThreadAnnotationTest : AndroidTestCase() {

  fun testJavaThreadAnnotations() = doTest(".java")

  private fun doTest(ext: String) {
    myFixture.copyFileToProject("callgraph/ThreadAnnotations" + ext)
    val paths = searchForInterproceduralThreadAnnotationViolations(project, AnalysisScope(project))
    val namedPathSet = paths.map { path -> path.map { method -> (method as PsiMethod).name } }.toSet()
    val expectedPathSet = setOf(
        arrayListOf("uiThreadStatic", "unannotatedStatic", "workerThreadStatic"),
        arrayListOf("uiThread", "unannotated", "workerThread")
    )
    TestCase.assertEquals(namedPathSet, expectedPathSet)
  }
}