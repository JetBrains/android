/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.actions.annotations

import com.android.tools.idea.actions.annotations.InferSupportAnnotations.Companion.generateReport
import com.intellij.analysis.AnalysisScope
import com.intellij.codeInsight.JavaCodeInsightTestCase
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.usageView.UsageInfo
import org.jetbrains.android.AndroidTestBase

private const val INFER_PATH = "/infer/"

class InferSupportAnnotationsTest : JavaCodeInsightTestCase() {
  override fun getTestDataPath(): String {
    return AndroidTestBase.getTestDataPath()
  }

  fun testInferParameterFromUsage() {
    doTest(
      false,
      """
      Class InferParameterFromUsage:
        Method inferParameterFromMethodCall:
          Parameter int id:
            @DimenRes because it calls InferParameterFromUsage#getDimensionPixelSize
      """.trimIndent()
    )
  }

  fun testInferResourceFromArgument() {
    doTest(
      false,
      """
      Class InferResourceFromArgument:
        Method inferredParameterFromOutsideCall:
          Parameter int inferredDimension:
            @DimenRes because it's passed R.dimen.some_dimension in a call
      """.trimIndent()
    )
  }

  fun testInferMethodAnnotationFromReturnValue() {
    doTest(
      false,
      """
      Class InferMethodAnnotationFromReturnValue:
        Method test:
            @DrawableRes because it returns R.mipmap.some_image
            @IdRes because it returns id
      """.trimIndent()
    )
  }

  fun testInferFromInheritance() {
    doTest(
      false,
      """
      Class Child:
        Method foo:
            @DrawableRes because it extends or is overridden by an annotated method
        Method something:
          Parameter int foo:
            @DrawableRes because it extends a method with that parameter annotated or inferred
      """.trimIndent()
    )
  }

  fun testEnforcePermission() {
    doTest(
      false,
      """
      Class EnforcePermission:
        Method impliedPermission:
            @RequiresPermission(EnforcePermission.MY_PERMISSION) because it calls enforceCallingOrSelfPermission
        Method unconditionalPermission:
            @RequiresPermission(EnforcePermission.MY_PERMISSION) because it calls EnforcePermission#impliedPermission
      """.trimIndent()
    )
  }

  fun testConditionalPermission() {
    if (!InferSupportAnnotationsAction.ENABLED) {
      return
    }

    // None of the permission requirements should transfer; all calls are conditional
    try {
      doTest(false, "Nothing found.")
      fail("Should infer nothing")
    } catch (e: RuntimeException) {
      if (!Comparing.strEqual(e.message, "Nothing found to infer")) {
        fail()
      }
    }
  }

  fun testIndirectPermission() {
    // Not yet implemented: Expected fail!
    doTest(false, null)
  }

  fun testReflection() {
    // TODO: implement
    doTest(false, null)
  }

  fun testThreadFlow() {
    // Not yet implemented: Expected fail!
    doTest(false, null)
  }

  fun testMultiplePasses() {
    // Not yet implemented: Expected fail!
    doTest(
      false,
      """
      Class A:
        Method fromA:
          Parameter int id:
            @DimenRes because it calls A#something
      
      Class D:
        Method d:
          Parameter int id:
            @DrawableRes because it calls D#something
      """.trimIndent(),
      findVirtualFile(INFER_PATH + "A.java"),
      findVirtualFile(INFER_PATH + "B.java"),
      findVirtualFile(INFER_PATH + "C.java"),
      findVirtualFile(INFER_PATH + "D.java")
    )
  }

  fun testPutValue() {
    if (!InferSupportAnnotationsAction.ENABLED) {
      return
    }

    // Ensure that if we see somebody putting a resource into
    // an intent map, we don't then conclude that ALL values put
    // into the map must be of that type
    try {
      doTest(false, "Nothing found.")
      fail("Should infer nothing")
    } catch (e: RuntimeException) {
      if (!Comparing.strEqual(e.message, "Nothing found to infer")) {
        fail()
      }
    }
  }

  private fun doTest(annotateLocalVariables: Boolean, summary: String?, vararg files: VirtualFile) {
    if (!InferSupportAnnotationsAction.ENABLED) {
      println("Ignoring " + this.javaClass.simpleName + ": Functionality disabled")
      return
    }
    val annotationsJar = "$testDataPath/infer/data.jar"
    val aLib = LocalFileSystem.getInstance().findFileByPath(annotationsJar)
    if (aLib != null) {
      val file = JarFileSystem.getInstance().getJarRootForLocalFile(aLib)
      if (file != null) {
        ModuleRootModificationUtil.addModuleLibrary(myModule, file.url)
      }
    }
    val inference = InferSupportAnnotations(annotateLocalVariables, project)
    val scope: AnalysisScope
    if (files.isNotEmpty()) {
      configureByFiles(null, *files)
      scope = AnalysisScope(project, listOf(*files))
      for (i in 0 until InferSupportAnnotationsAction.MAX_PASSES) {
        for (virtualFile in files) {
          val psiFile = psiManager.findFile(virtualFile)
          assertNotNull(psiFile)
          inference.collect(psiFile!!)
        }
      }
    } else {
      configureByFile(INFER_PATH + "before" + getTestName(false) + ".java")
      inference.collect(file)
      scope = AnalysisScope(file)
    }
    if (summary != null) {
      val infos = mutableListOf<UsageInfo>()
      inference.collect(infos, scope)
      var s = generateReport(infos.toTypedArray())
      s = StringUtil.trimStart(
        s,
        """
           INFER SUPPORT ANNOTATIONS REPORT
           ================================
           
           
        """.trimIndent()
      )
      assertEquals(summary, s.trim { it <= ' ' })
    }
    if (files.isEmpty()) {
      inference.apply(project)
      checkResultByFile(INFER_PATH + "after" + getTestName(false) + ".java")
    }
  }
}