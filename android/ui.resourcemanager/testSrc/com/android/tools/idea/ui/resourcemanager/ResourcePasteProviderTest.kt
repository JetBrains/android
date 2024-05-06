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
package com.android.tools.idea.ui.resourcemanager

import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.ui.resourcemanager.model.RESOURCE_URL_FLAVOR
import com.android.tools.idea.ui.resourcemanager.model.ResourcePasteProvider
import com.google.common.truth.Truth
import com.intellij.mock.MockVirtualFileSystem
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.command.impl.UndoManagerImpl
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.actions.PasteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPointerManager
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.Producer
import org.intellij.lang.annotations.Language
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException

private val DEFAULT_RESOURCE_URL = ResourceUrl.create("namespace", ResourceType.DRAWABLE,
                                                      "my_resource")

@Language("kotlin")
private const val DEFAULT_KOTLIN_FILE_CONTENT = "package com.example.myapplication\n" +
                                          "\n" +
                                          "import android.os.Bundle\n" +
                                          "import androidx.appcompat.app.AppCompatActivity\n" +
                                          "\n" +
                                          "class MainActivity : AppCompatActivity() {\n" +
                                          "\n" +
                                          "    override fun onCreate(savedInstanceState: Bundle?) {\n" +
                                          "        super.onCreate(savedInstanceState)\n" +
                                          "        setContentView(R.layout.activity_main)\n" +
                                          "        val color = R.color.my_color\n" +
                                          "    }\n" +
                                          "}"

private fun ResourcePasteProvider.paste(dataContext: DataContext) {
  runInEdtAndWait { runUndoTransparentWriteAction { performPaste(dataContext) } }
}

internal class ResourcePasteProviderTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory() // TODO(KT-28244) Fallback to ProjectRule when https://youtrack.jetbrains.com/issue/KT-28244 is fixed

  private lateinit var project: Project
  private lateinit var editor: Editor

  @Before
  fun setUp() {
    project = projectRule.project
  }

  @After
  fun tearDown() {
    runInEdtAndWait {
      EditorFactory.getInstance().releaseEditor(editor)
      with(UndoManager.getGlobalInstance() as UndoManagerImpl) {
        // For AndroidProjectRule, have to manually clear the UndoManager.
        dropHistoryInTests()
        flushCurrentCommandMerger()
      }
    }
  }

  @Test
  fun pasteOnKotlinMethodArgument() {
    testPasteOnKotlinFile(stringToMoveCaret = "activity_main", expectedChange = "setContentView(namespace.R.drawable.my_resource")
  }

  @Test
  fun pasteOnKotlinMethodCall() {
    testPasteOnKotlinFile(stringToMoveCaret = "ContentView",
                          expectedChange = "setContentView(R.layout.activity_main,namespace.R.drawable.my_resource)")
  }

  @Test
  fun pasteOnKotlinPropertyInitialization() {
    testPasteOnKotlinFile(stringToMoveCaret = "my_color",
                          expectedChange = "val color = namespace.R.drawable.my_resource")
  }

  @Test
  fun pasteOnKotlinPropertyInitialization2() {
    testPasteOnKotlinFile(stringToMoveCaret = " color = ",
                          expectedChange = "val color = namespace.R.drawable.my_resource")
  }

  @Test
  fun pasteOnKotlinUnknownElement() {
    testPasteOnKotlinFile(stringToMoveCaret = "super.onCreate",
                          expectedChange = "namespace.R.drawable.my_resourcesuper.onCreate(savedInstanceState)")
  }

  @Test
  fun pasteOntoImageView() {
    val content = """<ImageView
        |      xmlns:android="http://schemas.android.com/apk/res/android"
        |    android:layout_width="wrap_content"
        |    android:layout_height="wrap_content"/>""".trimMargin()

    val psiFile = psiFile(content)
    editor = createEditor(psiFile)

    runInEdtAndWait { editor.caretModel.moveToOffset(2) }
    val dataContext = createDataContext(editor, psiFile)

    val resourcePasteProvider = ResourcePasteProvider()
    resourcePasteProvider.paste(dataContext)

    Truth.assertThat(editor.document.text).contains("android:src=\"@namespace:drawable/my_resource")
  }

  @Test
  fun pasteOnAttributeValue() {
    val content = """<ImageView
        |      xmlns:android="http://schemas.android.com/apk/res/android"
        |    android:layout_width="wrap_content"
        |    android:layout_height="wrap_content"
        |    android:background="a random background"
        |    />""".trimMargin()

    val psiFile = psiFile(content)
    editor = createEditor(psiFile)
    val attributeIndex = content.indexOf("android:background=")
    val valueIndex = content.indexOf("a random background")
    runInEdtAndWait { editor.caretModel.moveToOffset(valueIndex) }
    val dataContext = createDataContext(editor, psiFile)

    val resourcePasteProvider = ResourcePasteProvider()
    resourcePasteProvider.paste(dataContext)

    Truth.assertThat(editor.document.text.substring(attributeIndex)).startsWith("android:background=\"@namespace:drawable/my_resource")
  }

  @Test
  fun pasteInDataBindingAttributeValue() {
    val content = """<ImageView
        |      xmlns:android="http://schemas.android.com/apk/res/android"
        |    android:layout_width="wrap_content"
        |    android:layout_height="wrap_content"
        |    android:background="@{databinding}"
        |    />""".trimMargin()

    val psiFile = psiFile(content)
    editor = createEditor(psiFile)
    val attributeIndex = content.indexOf("android:background=")
    val valueIndex = content.indexOf("binding")
    runInEdtAndWait { editor.caretModel.moveToOffset(valueIndex) }
    val dataContext = createDataContext(editor, psiFile)

    val resourcePasteProvider = ResourcePasteProvider()
    resourcePasteProvider.paste(dataContext)

    Truth.assertThat(editor.document.text.substring(attributeIndex)).startsWith("android:background=\"@{@namespace:drawable/my_resource}")
  }

  @Test
  fun pasteOutsideDataBindingAttributeValue() {
    val content = """<ImageView
        |      xmlns:android="http://schemas.android.com/apk/res/android"
        |    android:layout_width="wrap_content"
        |    android:layout_height="wrap_content"
        |    android:background="@{databinding}"
        |    />""".trimMargin()

    val psiFile = psiFile(content)
    editor = createEditor(psiFile)
    val attributeIndex = content.indexOf("android:background=")
    val valueIndex = content.indexOf("{databinding")
    runInEdtAndWait { editor.caretModel.moveToOffset(valueIndex) }
    val dataContext = createDataContext(editor, psiFile)

    val resourcePasteProvider = ResourcePasteProvider()
    resourcePasteProvider.paste(dataContext)

    Truth.assertThat(editor.document.text.substring(attributeIndex)).startsWith("android:background=\"@namespace:drawable/my_resource")
  }

  @Test
  fun pasteOnAttribute() {
    val content = """<ImageView
        |      xmlns:android="http://schemas.android.com/apk/res/android"
        |    android:layout_width="wrap_content"
        |    android:layout_height="wrap_content"
        |    android:background="a random background"
        |    />""".trimMargin()

    val psiFile = psiFile(content)
    editor = createEditor(psiFile)
    val attributeIndex = content.indexOf("android:background=")
    runInEdtAndWait { editor.caretModel.moveToOffset(attributeIndex + 1) }
    val dataContext = createDataContext(editor, psiFile)

    val resourcePasteProvider = ResourcePasteProvider()
    resourcePasteProvider.paste(dataContext)

    Truth.assertThat(editor.document.text.substring(attributeIndex)).startsWith("android:background=\"@namespace:drawable/my_resource")
  }

  @Test
  fun pasteOnWhiteSpace() {
    val content = """
      |<Tag1 xmlns:android="http://schemas.android.com/apk/res/android">
      |   <Tag2>
      |   </Tag2>
      |</Tag1>
    """.trimMargin()

    val psiFile = psiFile(content)
    val psiFilePointer = runInEdtAndGet { SmartPointerManager.createPointer(psiFile) }
    editor = createEditor(psiFile)
    val tagIndex = content.indexOf("<Tag2>") + "<Tag2>".length
    runInEdtAndWait { editor.caretModel.moveToOffset(tagIndex) }
    val dataContext = createDataContext(editor, psiFile)

    val resourcePasteProvider = ResourcePasteProvider()
    resourcePasteProvider.paste(dataContext)

    Truth.assertThat(editor.document.text).isEqualTo("""
    <Tag1 xmlns:android="http://schemas.android.com/apk/res/android">
       <Tag2>

           <ImageView
               android:layout_width="wrap_content"
               android:layout_height="wrap_content"
               android:src="@namespace:drawable/my_resource" />
       </Tag2>
    </Tag1>
    """.trimIndent())

    run {
      val file = runInEdtAndGet { psiFilePointer.element!! }
      resourcePasteProvider.paste(createDataContext(editor, file, ResourceUrl.parse("@drawable/resource2")!!))

      Truth.assertThat(editor.document.text).isEqualTo("""
    <Tag1 xmlns:android="http://schemas.android.com/apk/res/android">
       <Tag2>

           <ImageView
               android:layout_width="wrap_content"
               android:layout_height="wrap_content"
               android:src="@drawable/resource2" />

           <ImageView
               android:layout_width="wrap_content"
               android:layout_height="wrap_content"
               android:src="@namespace:drawable/my_resource" />
       </Tag2>
    </Tag1>
  """.trimIndent())
    }

    val tagIndex2 = editor.document.text.indexOf("</Tag2>") + "</Tag2>".length
    runInEdtAndWait { editor.caretModel.moveToOffset(tagIndex2) }

    run {
      val file = runInEdtAndGet { psiFilePointer.element!! }
      resourcePasteProvider.paste(createDataContext(editor, file, ResourceUrl.parse("@drawable/resource3")!!))

      Truth.assertThat(editor.document.text).isEqualTo("""
    <Tag1 xmlns:android="http://schemas.android.com/apk/res/android">
       <Tag2>

           <ImageView
               android:layout_width="wrap_content"
               android:layout_height="wrap_content"
               android:src="@drawable/resource2" />

           <ImageView
               android:layout_width="wrap_content"
               android:layout_height="wrap_content"
               android:src="@namespace:drawable/my_resource" />
       </Tag2>

        <ImageView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:src="@drawable/resource3" />
    </Tag1>
    """.trimIndent())
    }

    runInEdtAndWait { editor.caretModel.moveToOffset(tagIndex2) }

    run {
      val file = runInEdtAndGet { psiFilePointer.element!! }
      resourcePasteProvider.paste(createDataContext(editor, file, ResourceUrl.parse("@layout/my_layout")!!))

      Truth.assertThat(editor.document.text).isEqualTo("""
    <Tag1 xmlns:android="http://schemas.android.com/apk/res/android">
       <Tag2>

           <ImageView
               android:layout_width="wrap_content"
               android:layout_height="wrap_content"
               android:src="@drawable/resource2" />

           <ImageView
               android:layout_width="wrap_content"
               android:layout_height="wrap_content"
               android:src="@namespace:drawable/my_resource" />
       </Tag2>

        <include
            layout="@layout/my_layout"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content" />

        <ImageView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:src="@drawable/resource3" />
    </Tag1>
    """.trimIndent())
    }
  }

  private fun createEditor(psiFile: PsiFile): Editor {
    val document = runReadAction { PsiDocumentManager.getInstance(project).getDocument(psiFile)!! }
    val editorFactory = EditorFactory.getInstance()
    return runInEdtAndGet { editorFactory.createEditor(document, project) }
  }

  private fun psiKtFile(content: String): PsiFile {
    val fileSystem = MockVirtualFileSystem()
    val activityFile: VirtualFile = fileSystem.file("/main/MainActivity.kt", content).refreshAndFindFileByPath("/main/MainActivity.kt")!!
    return runReadAction {
      PsiManager.getInstance(project).findFile(activityFile)!!
    }
  }

  private fun psiFile(content: String): PsiFile {
    val fileSystem = MockVirtualFileSystem()
    val layoutFile: VirtualFile = fileSystem.file("/layout/layout.xml", content).refreshAndFindFileByPath("/layout/layout.xml")!!
    return runReadAction {
      PsiManager.getInstance(project).findFile(layoutFile)!!
    }
  }

  private fun createDataContext(editor: Editor,
                                psiFile: PsiFile,
                                resourceUrl: ResourceUrl = DEFAULT_RESOURCE_URL): DataContext =
    MapDataContext(mapOf(
      PasteAction.TRANSFERABLE_PROVIDER to Producer<Transferable> { createTransferable(resourceUrl) },
      CommonDataKeys.CARET to editor.caretModel.currentCaret,
      CommonDataKeys.PSI_FILE to psiFile
    ))

  private fun createTransferable(resourceUrl: ResourceUrl) = object : Transferable {
    override fun getTransferData(flavor: DataFlavor?): Any = when (flavor) {
      RESOURCE_URL_FLAVOR -> resourceUrl
      DataFlavor.stringFlavor -> resourceUrl.toString()
      else -> throw UnsupportedFlavorException(flavor)
    }

    override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean = true

    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(RESOURCE_URL_FLAVOR)
  }

  private fun testPasteOnKotlinFile(fileContents: String = DEFAULT_KOTLIN_FILE_CONTENT, stringToMoveCaret: String, expectedChange: String) {
    val psiKtFile = psiKtFile(fileContents)
    editor = createEditor(psiKtFile)
    runInEdtAndWait { editor.caretModel.moveToOffset(editor.document.text.indexOf(stringToMoveCaret)) }
    val dataContext = createDataContext(editor, psiKtFile)

    val resourcePasteProvider = ResourcePasteProvider()
    resourcePasteProvider.paste(dataContext)

    Truth.assertThat(editor.document.text).contains(expectedChange)
    Truth.assertThat(runInEdtAndGet { editor.selectionModel.selectedText!! }).isEqualTo("namespace.R.drawable.my_resource")
  }
}