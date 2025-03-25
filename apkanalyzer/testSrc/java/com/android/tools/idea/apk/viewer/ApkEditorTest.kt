/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.apk.viewer

import com.android.testutils.TestResources
import com.android.testutils.waitForCondition
import com.android.tools.adtui.TreeWalker
import com.android.tools.apk.analyzer.ArchiveNode
import com.android.tools.apk.analyzer.ArchivePathEntry
import com.android.tools.apk.analyzer.dex.tree.DexClassNode
import com.android.tools.apk.analyzer.internal.ArchiveTreeNode
import com.android.tools.idea.apk.viewer.arsc.ArscViewer
import com.android.tools.idea.apk.viewer.dex.DexFileViewer
import com.android.tools.idea.apk.viewer.testing.FakeAndroidApplicationInfoProvider
import com.android.tools.idea.testing.ApplicationServiceRule
import com.google.common.truth.Truth.assertThat
import com.google.devrel.gmscore.tools.apk.arsc.BinaryResourceFile
import com.google.devrel.gmscore.tools.apk.arsc.Chunk
import com.google.devrel.gmscore.tools.apk.arsc.ChunkWithChunks
import com.intellij.diff.util.FileEditorBase
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.ui.asSequence
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.LoadingNode
import com.intellij.ui.treeStructure.Tree
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import kotlin.io.path.pathString
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

private val TIMEOUT = 3.seconds

/**
 * Tests for [ApkEditor]
 */
@RunsInEdt
@RunWith(JUnit4::class)
class ApkEditorTest {
  private val projectRule = ProjectRule()
  private val project get() = projectRule.project
  private val disposableRule = DisposableRule()

  @get:Rule
  val rule = RuleChain(
    projectRule,
    disposableRule,
    ApplicationServiceRule(FileEditorProviderManager::class.java, mockFileEditorProviderManager()),
    EdtRule()
  )

  @Test
  fun newEditor_createsPanel() {
    val apkEditor = apkEditor("/test.apk")

    assertThat(apkEditor.getTopPaneName()).isEqualTo("ApkViewPanel")
    assertThat(apkEditor.getBottomPaneName()).isEqualTo("EmptyPanel")
  }

  @Test
  fun newEditor_createsNodes() {
    val apkEditor = apkEditor("/test.apk")

    assertThat(apkEditor.getNodes()).containsExactly(
      "/",
      "/AndroidManifest.xml",
      "/instant-run.zip",
      "/instant-run",
      "/instant-run/classes1.dex",
      "/res",
      "/res/anim",
      "/res/anim/fade.xml"
    )
  }

  @Test
  fun selectRoot_createsEmptyPanel() {
    val apkEditor = apkEditor("/test-app.apk")
    // Select XML node first
    apkEditor.getEditor<FileEditorComponent>(apkEditor.getNode("/res/xml/backup_rules.xml"))

    val editor = apkEditor.getEditor<ApkFileEditorComponent>(apkEditor.getNode("/"))

    assertThat(editor).isInstanceOf(EmptyPanel::class.java)
  }

  @Test
  fun selectDex_createsDexEditor() {
    val apkEditor = apkEditor("/test-app.apk")

    val editor = apkEditor.getEditor<DexFileViewer>(apkEditor.getNode("/classes2.dex"))

    assertThat(editor.getClassCount()).isEqualTo(84)
  }

  @Test
  fun selectMultipleDex_createsDexEditor() {
    val apkEditor = apkEditor("/test-app.apk")

    val editor = apkEditor.getEditor<DexFileViewer>(
      apkEditor.getNode("/classes2.dex"),
      apkEditor.getNode("/classes4.dex"),
      )

    assertThat(editor.getClassCount()).isEqualTo(112)
  }

  @Test
  fun selectXml_createsXmlEditor() {
    val apkEditor = apkEditor("/test.apk")

    val editor = apkEditor.getEditor<FileEditorComponent>(apkEditor.getNode("/res/anim/fade.xml"))

    val fileEditor = editor.editor as TestFileEditor
    assertThat(fileEditor.fileType).isEqualTo("XML")
    assertThat(fileEditor.fileContents).isEqualTo("""
      12333

    """.trimIndent())
  }

  @Test
  fun selectBinaryXml_createsXmlEditor() {
    val apkEditor = apkEditor("/test-app.apk")

    val editor = apkEditor.getEditor<FileEditorComponent>(apkEditor.getNode("/res/xml/backup_rules.xml"))

    val fileEditor = editor.editor as TestFileEditor
    assertThat(fileEditor.fileType).isEqualTo("XML")
    assertThat(fileEditor.fileContents).isEqualTo("""
      <?xml version="1.0" encoding="utf-8"?>
      <full-backup-content />

    """.trimIndent())
  }

  @Test
  fun selectProtoXml_createsXmlEditor() {
    val apkEditor = apkEditor("/android-app-bundle.aab")

    // ArchivesTest.protoXml_manifest demonstrates that this file is a Proto XML
    val editor = apkEditor.getEditor<FileEditorComponent>(apkEditor.getNode("/dynamic_feature2/manifest/AndroidManifest.xml"))

    val fileEditor = editor.editor as TestFileEditor
    assertThat(fileEditor.fileType).isEqualTo("XML")
    assertThat(fileEditor.fileContents).startsWith("""<?xml version="1.0" encoding="utf-8"?>""")
  }

  @Test
  fun selectBaselinePerf_createsTextEditor() {
    val apkEditor = apkEditor("/app-benchmark.apk")

    val editor = apkEditor.getEditor<FileEditorComponent>(apkEditor.getNode("/assets/dexopt/baseline.prof"))

    val fileEditor = editor.editor as TestFileEditor
    assertThat(fileEditor.fileContents).contains("Lcom/example/emptyactivity/MainActivity;")
  }

  @Test
  fun selectArsc_createsArscEditor() {
    val apkEditor = apkEditor("/test-app.apk")

    val editor = apkEditor.getEditor<ArscViewer>(apkEditor.getNode("/resources.arsc"))

    assertThat(editor.file.getAllChunks().count()).isEqualTo(124)
  }

  @Test
  fun selectKotlinBuiltin_createsEmptyPanel() {
    val apkEditor = apkEditor("/test-app.apk")

    val editor = apkEditor.getEditor<ApkFileEditorComponent>(apkEditor.getNode("/kotlin/kotlin.kotlin_builtins"))

    assertThat(editor).isInstanceOf(EmptyPanel::class.java)
  }

  private fun apkEditor(path: String): ApkEditor {
    val file = TestResources.getFile(path)
    val archive = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file) ?: fail("File not found: $path")
    val root = ApkFileSystem().getRootByLocal(archive) ?: fail("Invalid archive: $path")
    val apkEditor = ApkEditor(project, archive, root, FakeAndroidApplicationInfoProvider())
    Disposer.register(disposableRule.disposable, apkEditor)
    waitForCondition { apkEditor.isLoaded() }
    return apkEditor
  }

  private inline fun <reified T : ApkFileEditorComponent> ApkEditor.getEditor(vararg nodes: ArchiveTreeNode): T {
    val editor = getEditor(nodes)
    // Since we call ApkEditor.getEditor() directly, it doesn't get disposed automatically when ApkEditor is disposed.
    Disposer.register(disposableRule.disposable, editor)
    return editor as? T ?: fail("Expected ${T::class.java.name} but got ${editor::class.java.name}")
  }

  /**
   * Creates a mock [FileEditorProviderManager]
   *
   * Using the default `FileEditorProviderManager` results in a [com.intellij.openapi.fileEditor.impl.text.TextEditorImpl] being created for
   * XML and text files.
   *
   * When a `TextEditorImpl` is created, it launches several asynchronous tasks that fail the test because they run interfere with disposal.
   */
  private fun mockFileEditorProviderManager(): FileEditorProviderManager {
    val fileEditorProviderManager = mock<FileEditorProviderManager>()
    val fileEditorProvider = mock<FileEditorProvider>()
    whenever(fileEditorProvider.accept(any(), any())).thenReturn(true)
    whenever(fileEditorProvider.createEditor(any(), any())).thenAnswer {
      val file = it.arguments[1] as VirtualFile
      return@thenAnswer TestFileEditor(file)
    }
    whenever(fileEditorProviderManager.getProviderList(any(), any())).thenReturn(listOf(fileEditorProvider))
    return fileEditorProviderManager
  }

  /**
   * A Test [FileEditor]
   *
   * Exposes information needed for verification and doesn't interfere with disposal.
   */
  private class TestFileEditor(virtualFile: VirtualFile): FileEditorBase() {
    val fileType = virtualFile.fileType.name

    val fileContents = virtualFile.readText()

    override fun getComponent() = JPanel()

    override fun getName() = "TestFileEditor"

    override fun getPreferredFocusedComponent() = null
  }
}

private fun ApkEditor.isLoaded(): Boolean {
  val root = getNodesModel().root as? ArchiveTreeNode ?: return false
  return root.data.downloadFileSize > 0
}

private fun ApkEditor.getTopPane() = (component as Splitter).firstComponent

private fun ApkEditor.getTopPaneName() = getTopPane().name

private fun ApkEditor.getBottomPane(): JComponent = (component as Splitter).secondComponent

private fun ApkEditor.getBottomPaneName() = getBottomPane().name

private fun ApkEditor.getNodesModel() = getTopPane().findComponent<Tree>("nodeTree").model

private fun ApkEditor.getNodes(): List<String> {
  return getNodesModel().asSequence().toList().map { it.getFilePath() }
}

private fun ApkEditor.getNode(path: String): ArchiveTreeNode {
  waitForCondition {
    getNodesModel().root is ArchiveNode
  }
  val nodes = getNodesModel().asSequence()
  return nodes.first { it.getFilePath() == path } as ArchiveTreeNode
}

private fun DefaultMutableTreeNode.getFilePath() = (userObject as ArchivePathEntry).path.pathString

private inline fun <reified T : JComponent> JComponent.findComponent(name: String): T {
  return TreeWalker(this).descendants().filterIsInstance<T>().find { it.name == name }
         ?: fail("${T::class.simpleName} named $name was not found")
}

private fun waitForCondition(condition: () -> Boolean) = waitForCondition(TIMEOUT, condition)

private fun DexFileViewer.getClassCount(): Int {
  val tree = component.findComponent<Tree>("DexTree")
  return tree.getClassCount()
}

private fun Tree.getClassCount(): Int {
  waitForCondition { model.root !is LoadingNode }
  return model.asSequence().count { it is DexClassNode }
}

private fun BinaryResourceFile.getAllChunks(): Sequence<Any> {
  return sequence {
    chunks.forEach {
      yield(it)
      if (it is ChunkWithChunks) {
        yieldAll(it.getAllChunks())
      }
    }
  }
}

private fun ChunkWithChunks.getAllChunks(): Sequence<Chunk> {
  return sequence {
     chunks.values.forEach {
      yield(it)
       if (it is ChunkWithChunks) {
         // recursive call
         yieldAll(it.getAllChunks())
       }
    }
  }
}