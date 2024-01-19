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

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.TOOLS_URI
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.common.command.NlWriteCommandActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.IncorrectOperationException
import com.intellij.util.ThrowableRunnable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.jetbrains.android.AndroidTestCase
import org.mockito.Mockito.mock

class NlComponentBackendXmlTest : AndroidTestCase() {

  val TEST_TIMEOUT_MS: Long = 500

  fun testDebugOff() {
    assertFalse(NlComponentBackendXml.DEBUG)
  }

  fun testAffectedFileWriteAccess() {
    val editText =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
        "<layout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
        "    xmlns:tools123=\"http://schemas.android.com/tools\">\n" +
        "\n" +
        "    <RelativeLayout />\n" +
        "</layout>\n"
    val xmlFile = myFixture.addFileToProject("res/layout/layout.xml", editText) as XmlFile
    val rootTag = xmlFile.rootTag!!.subTags[0]
    val backend = createBackend(rootTag)

    ApplicationManager.getApplication().invokeLater {
      WriteCommandAction.runWriteCommandAction(project) { assertNotNull(backend.getAffectedFile()) }
    }
  }

  fun testAffectedFileReadAccess() {
    val editText =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
        "<layout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
        "    xmlns:tools123=\"http://schemas.android.com/tools\">\n" +
        "\n" +
        "    <RelativeLayout />\n" +
        "</layout>\n"
    val xmlFile = myFixture.addFileToProject("res/layout/layout.xml", editText) as XmlFile
    val rootTag = xmlFile.rootTag!!.subTags[0]
    val backend = createBackend(rootTag)

    ApplicationManager.getApplication().runReadAction { assertNotNull(backend.getAffectedFile()) }
  }

  fun testAffectedFileWrongAccess() {
    val editText =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
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
    whenever(invalidTag.name).thenReturn("")
    val backend = createBackend(invalidTag)

    ApplicationManager.getApplication().runReadAction { assertNull(backend.getAffectedFile()) }
  }

  fun testGetAttributeReadPermittedThread() {
    val expected = "Hello World"
    val editText =
      """
        <?xml version="1.0" encoding="utf-8"?>
        <TextView
            xmlns:android="${ANDROID_URI}"
            xmlns:tools="${TOOLS_URI}"
            android:text="${expected}"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content" >
        </TextView>
      """
        .trimIndent()
    val xmlFile = myFixture.addFileToProject("res/layout/layout.xml", editText) as XmlFile
    val rootTag = xmlFile.rootTag!!
    val backend = createBackend(rootTag)

    ApplicationManager.getApplication().runReadAction {
      assertEquals(expected, backend.getAttribute("text", ANDROID_URI))
    }
  }

  fun testGetAttributeReadNotPermittedThread() {
    val expected = "Hello World"
    val editText =
      """
        <?xml version="1.0" encoding="utf-8"?>
        <TextView
            xmlns:android="${ANDROID_URI}"
            xmlns:tools="${TOOLS_URI}"
            android:text="${expected}"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content" >
        </TextView>
      """
        .trimIndent()
    val xmlFile = myFixture.addFileToProject("res/layout/layout.xml", editText) as XmlFile
    val rootTag = xmlFile.rootTag!!
    val backend = createBackend(rootTag)

    val latch = CountDownLatch(1)
    val thread = Thread {
      assertFalse(ApplicationManager.getApplication().isReadAccessAllowed)
      assertEquals(expected, backend.getAttribute("text", ANDROID_URI))
      latch.countDown()
    }

    thread.start()
    latch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
  }

  fun testSetAttributeWriteNotPermittedThread() {
    val expected = "Hello World"
    val changed = "Changed"
    val editText =
      """
        <?xml version="1.0" encoding="utf-8"?>
        <TextView
            xmlns:android="${ANDROID_URI}"
            xmlns:tools="${TOOLS_URI}"
            android:text="${expected}"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content" >
        </TextView>
      """
        .trimIndent()
    val xmlFile = myFixture.addFileToProject("res/layout/layout.xml", editText) as XmlFile
    val rootTag = xmlFile.rootTag!!
    val backend = createBackend(rootTag)

    val latch = CountDownLatch(1)
    val thread = Thread {
      assertFalse(ApplicationManager.getApplication().isReadAccessAllowed)
      assertFalse(backend.setAttribute("text", ANDROID_URI, changed))
      // Hasn't changed
      assertEquals(expected, backend.getAttribute("text", ANDROID_URI))
      latch.countDown()
    }

    thread.start()
    latch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
  }

  fun testSetAttributeWriteCommandAction() {
    val expected = "Hello World"
    val changed = "Changed"
    val editText =
      """
        <?xml version="1.0" encoding="utf-8"?>
        <TextView
            xmlns:android="${ANDROID_URI}"
            xmlns:tools="${TOOLS_URI}"
            android:text="${expected}"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content" >
        </TextView>
      """
        .trimIndent()
    val xmlFile = myFixture.addFileToProject("res/layout/layout.xml", editText) as XmlFile
    val rootTag = xmlFile.rootTag!!
    val backend = createBackend(rootTag)

    WriteCommandAction.runWriteCommandAction(
      myModule.project,
      Runnable {
        assertTrue(ApplicationManager.getApplication().isReadAccessAllowed)
        assertTrue(backend.setAttribute("text", ANDROID_URI, changed))
        // Changed
        assertEquals(changed, backend.getAttribute("text", ANDROID_URI))
      },
    )
  }

  fun testSetAttributeNlWriteCommandAction() {
    val model = mock(NlModel::class.java)
    whenever(model.project).thenReturn(myModule.project)

    val expected = "Hello World"
    val changed = "Changed"
    val editText =
      """
        <?xml version="1.0" encoding="utf-8"?>
        <TextView
            xmlns:android="${ANDROID_URI}"
            xmlns:tools="${TOOLS_URI}"
            android:text="${expected}"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content" >
        </TextView>
      """
        .trimIndent()
    val xmlFile = myFixture.addFileToProject("res/layout/layout.xml", editText) as XmlFile
    val rootTag = xmlFile.rootTag!!
    val component = NlComponent(model, rootTag)
    val backend = component.backend

    NlWriteCommandActionUtil.run(
      component,
      "Set attribute",
      Runnable {
        assertTrue(ApplicationManager.getApplication().isReadAccessAllowed)
        assertTrue(backend.setAttribute("text", ANDROID_URI, changed))
        // Changed
        assertEquals(changed, backend.getAttribute("text", ANDROID_URI))
      },
    )
  }

  fun testSetAttributeWritePermittedThread() {
    val expected = "Hello World"
    val changed = "Changed"
    val editText =
      """
        <?xml version="1.0" encoding="utf-8"?>
        <TextView
            xmlns:android="${ANDROID_URI}"
            xmlns:tools="${TOOLS_URI}"
            android:text="${expected}"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content" >
        </TextView>
      """
        .trimIndent()
    val xmlFile = myFixture.addFileToProject("res/layout/layout.xml", editText) as XmlFile
    val rootTag = xmlFile.rootTag!!
    val backend = createBackend(rootTag)

    ApplicationManager.getApplication().runWriteAction {
      assertTrue(ApplicationManager.getApplication().isReadAccessAllowed)
      assertThrows(
        IncorrectOperationException::class.java,
        ThrowableRunnable<IncorrectOperationException> {
          backend.setAttribute("text", ANDROID_URI, changed)
        },
      )
    }
  }

  fun testReformat() {
    val editText =
      // language=xml
      """
        <?xml version="1.0" encoding="utf-8"?>
        <TextView
            xmlns:android="${ANDROID_URI}" xmlns:tools="${TOOLS_URI}" android:text="Text"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content" />
      """
        .trimIndent()
    val xmlFile = myFixture.addFileToProject("res/layout/layout.xml", editText) as XmlFile
    val rootTag = xmlFile.rootTag!!
    val backend = createBackend(rootTag)

    WriteCommandAction.runWriteCommandAction(project) { backend.reformatAndRearrange() }
    assertEquals(
      // language=xml
      """
        <?xml version="1.0" encoding="utf-8"?>
        <TextView xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:tools="http://schemas.android.com/tools"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Text" />
      """
        .trimIndent(),
      xmlFile.text,
    )
  }

  private fun createBackend(tag: XmlTag): NlComponentBackendXml {
    return NlComponentBackendXml(myFixture.project, tag, createTagPointer(tag))
  }

  private fun createTagPointer(tag: XmlTag): SmartPsiElementPointer<XmlTag> {
    val tagPointer = mock(SmartPsiElementPointer::class.java) as SmartPsiElementPointer<XmlTag>

    whenever(tagPointer.element).thenReturn(tag)
    return tagPointer
  }
}
