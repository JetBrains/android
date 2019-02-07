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
package com.android.tools.idea.common.model

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import org.jetbrains.android.AndroidTestCase
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class NlComponentBackendXmlTest : AndroidTestCase() {

  fun testAffectedFileWriteAccess() {
    val editText = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                   "<layout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                   "    xmlns:tools123=\"http://schemas.android.com/tools\">\n" +
                   "\n" +
                   "    <RelativeLayout />\n" +
                   "</layout>\n"
    val xmlFile = myFixture.addFileToProject("res/layout/layout.xml", editText) as XmlFile
    val rootTag = xmlFile.rootTag!!.subTags[0]
    val backend = createBackend(rootTag)

    ApplicationManager.getApplication().invokeLater {
      WriteCommandAction.runWriteCommandAction(project) {
        assertNotNull(backend.getAffectedFile())
      }
    }
  }

  fun testAffectedFileReadAccess() {
    val editText = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                   "<layout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                   "    xmlns:tools123=\"http://schemas.android.com/tools\">\n" +
                   "\n" +
                   "    <RelativeLayout />\n" +
                   "</layout>\n"
    val xmlFile = myFixture.addFileToProject("res/layout/layout.xml", editText) as XmlFile
    val rootTag = xmlFile.rootTag!!.subTags[0]
    val backend = createBackend(rootTag)

    ApplicationManager.getApplication().runReadAction {
      assertNotNull(backend.getAffectedFile())
    }
  }

  fun testAffectedFileWrongAccess() {
    val editText = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                   "<layout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                   "    xmlns:tools123=\"http://schemas.android.com/tools\">\n" +
                   "\n" +
                   "    <RelativeLayout />\n" +
                   "</layout>\n"
    val xmlFile = myFixture.addFileToProject("res/layout/layout.xml", editText) as XmlFile
    val rootTag = xmlFile.rootTag!!.subTags[0]
    val backend = createBackend(rootTag)

    assertNotNull(backend.getAffectedFile())
  }

  fun testAffectedFileInvalidTag() {
    val invalidTag = mock(XmlTag::class.java)
    `when`(invalidTag.name).thenReturn("")
    val backend = createBackend(invalidTag)

    ApplicationManager.getApplication().runReadAction {
      assertNull(backend.getAffectedFile())
    }
  }

  private fun createBackend(tag: XmlTag): NlComponentBackendXml {
    return NlComponentBackendXml(mock<NlModel>(NlModel::class.java), tag, createTagPointer(tag))
  }

  private fun createTagPointer(tag: XmlTag): SmartPsiElementPointer<XmlTag> {
    val tagPointer = mock(SmartPsiElementPointer::class.java) as SmartPsiElementPointer<XmlTag>

    `when`<XmlTag>(tagPointer.element).thenReturn(tag)
    return tagPointer
  }
}