/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea

import com.intellij.codeInsight.JavaCodeInsightTestCase
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import junit.framework.AssertionFailedError
import junit.framework.TestCase
import org.junit.Test

class AndroidPsiUtilsTest : JavaCodeInsightTestCase() {

  @Test
  fun testLanguageSpecificPsiModificationTracking() {
    val xmlTracker = AndroidPsiUtils.getXmlPsiModificationTracker(project)
    val nonXmlTracker = AndroidPsiUtils.getPsiModificationTrackerIgnoringXml(project)

    val javaFile = createFile("Foo.java", "class Foo {}")
    val kotlinFile = createFile("Foo.kt", "class Foo")
    val xmlFile = createFile("Foo.xml", "<?xml version=\"1.0\" encoding=\"utf-8\"?><Foo/>")

    fun modifyFile(file: PsiFile) {
      WriteCommandAction.runWriteCommandAction(project) {
        val idx = file.text.indexOf("Foo")
        if (idx == -1) throw AssertionFailedError("Did not find string \"Foo\" in the test file")
        val docManager = PsiDocumentManager.getInstance(project)
        docManager.getDocument(file)!!.replaceString(idx, idx + 3, "Bar")
        docManager.commitAllDocuments()
      }
    }

    // Returns a pair of integers giving the change in modification counts for xmlTracker and nonXmlTracker, respectively.
    fun getChangeInCountersAfterModifyingFile(file: PsiFile): Pair<Long, Long> {
      val xmlBefore = xmlTracker.modificationCount
      val nonXmlBefore = nonXmlTracker.modificationCount
      modifyFile(file)
      val xmlAfter = xmlTracker.modificationCount
      val nonXmlAfter = nonXmlTracker.modificationCount
      return Pair(xmlAfter - xmlBefore, nonXmlAfter - nonXmlBefore)
    }

    val (xmlDiffAfterJava, nonXmlDiffAfterJava) = getChangeInCountersAfterModifyingFile(javaFile)
    TestCase.assertTrue(xmlDiffAfterJava == 0L)
    TestCase.assertTrue(nonXmlDiffAfterJava > 0)

    val (xmlDiffAfterKotlin, nonXmlDiffAfterKotlin) = getChangeInCountersAfterModifyingFile(kotlinFile)
    TestCase.assertTrue(xmlDiffAfterKotlin == 0L)
    TestCase.assertTrue(nonXmlDiffAfterKotlin > 0)

    val (xmlDiffAfterXml, nonXmlDiffAfterXml) = getChangeInCountersAfterModifyingFile(xmlFile)
    TestCase.assertTrue(xmlDiffAfterXml > 0)
    TestCase.assertTrue(nonXmlDiffAfterXml == 0L)
  }
}