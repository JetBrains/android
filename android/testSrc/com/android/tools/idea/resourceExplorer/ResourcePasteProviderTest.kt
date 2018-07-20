/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer

import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.idea.resourceExplorer.view.RESOURCE_URL_FLAVOR
import com.google.common.truth.Truth
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.actions.PasteAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.Producer
import org.junit.ClassRule
import org.junit.Test
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

internal class ResourcePasteProviderTest {

  companion object {
    @ClassRule
    @JvmField
    val projectRule = ProjectRule()
  }

  private val project = projectRule.project

  @Test
  fun pasteOntoImageView() {
    val content = """<ImageView
        |      xmlns:android="http://schemas.android.com/apk/res/android"
        |    android:layout_width="wrap_content"
        |    android:layout_height="wrap_content"/>""".trimMargin()

    val psiFile = psiFile(content)
    val editor = createEditor(psiFile)

    runInEdtAndWait { editor.caretModel.moveToOffset(2) }
    val dataContext = createDataContext(editor, psiFile)

    val resourcePasteProvider = ResourcePasteProvider()
    runInEdtAndWait { runUndoTransparentWriteAction { resourcePasteProvider.performPaste(dataContext) } }

    Truth.assertThat(editor.document.text).contains("android:src=\"@namespace:drawable/my_resource")

    runInEdtAndWait { EditorFactory.getInstance().releaseEditor(editor) }
  }

  private fun createEditor(psiFile: PsiFile): Editor {
    val document = runReadAction { PsiDocumentManager.getInstance(project).getDocument(psiFile)!! }
    val editorFactory = EditorFactory.getInstance()
    return runInEdtAndGet { editorFactory.createEditor(document, project) }
  }

  private fun psiFile(content: String): PsiFile {
    val psiFileFactory = PsiFileFactory.getInstance(project)
    return runReadAction {
      psiFileFactory.createFileFromText("layout.xml", XmlFileType.INSTANCE, content, System.currentTimeMillis(), true)
    }
  }

  private fun createDataContext(editor: Editor, psiFile: PsiFile): DataContext =
    MapDataContext(mapOf(
      PasteAction.TRANSFERABLE_PROVIDER to Producer<Transferable>(this::createTransferable),
      CommonDataKeys.CARET to editor.caretModel.currentCaret,
      CommonDataKeys.PSI_FILE to psiFile
    ))

  private fun createTransferable() = object : Transferable {
    val url = ResourceUrl.create("namespace", ResourceType.DRAWABLE, "my_resource")
    override fun getTransferData(flavor: DataFlavor?): Any? = when (flavor) {
      RESOURCE_URL_FLAVOR -> url
      DataFlavor.stringFlavor -> url.toString()
      else -> null
    }

    override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean = true

    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(RESOURCE_URL_FLAVOR)
  }
}