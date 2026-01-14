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
import com.android.tools.idea.apk.viewer.ApkViewPanel.ApkTreeModel
import com.android.tools.idea.apk.viewer.arsc.ArscViewer
import com.android.tools.idea.apk.viewer.dex.DexFileViewer
import com.android.tools.idea.apk.viewer.pagealign.AlignmentWarningViewer
import com.android.tools.idea.apk.viewer.testing.FakeAndroidApplicationInfoProvider
import com.android.tools.idea.testing.ApplicationServiceRule
import com.android.tools.idea.testing.TemporaryDirectoryRule
import com.google.common.truth.Truth.assertThat
import com.google.devrel.gmscore.tools.apk.arsc.Chunk
import com.google.devrel.gmscore.tools.apk.arsc.ChunkWithChunks
import com.google.devrel.gmscore.tools.apk.arsc.ResourceFile
import com.intellij.diff.util.FileEditorBase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.ui.asSequence
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.readText
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.LoadingNode
import com.intellij.ui.treeStructure.Tree
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createParentDirectories
import kotlin.io.path.pathString
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import com.intellij.ui.SimpleColoredComponent
import java.awt.Component
import java.awt.Container
import javax.swing.JLabel
import javax.swing.text.JTextComponent
import javax.swing.tree.TreePath


// Timeout to use when waiting for isLoaded condition
// IsLoaded occurs when the APK has been read and the download size is available.
// At this point app info isn't necessarily available nor have warning nodes been
// expanded.
private val IS_LOADED_TIMEOUT = 9.seconds
// Timeout to use when waiting for isUpdated condition.
// IsUpdated occurs when tree data has been populated, when app info has been read,
// and tree nodes with warning messages have been expanded.
private val IS_UPDATED_TIMEOUT = 9.seconds

/**
 * Tests for [ApkEditor]
 */
@RunsInEdt
@RunWith(Parameterized::class)
class ApkEditorTest(
  val isPageAlignFeatureEnabled: Boolean
) {
  private val projectRule = ProjectRule()
  private val project get() = projectRule.project
  private val disposableRule = DisposableRule()
  val temporaryDirectoryRule = TemporaryDirectoryRule()

  companion object {
    @Parameterized.Parameters(name = "isPageAlignFeatureEnabled={0}")
    @JvmStatic fun data() = arrayOf(true, false)
  }

  @get:Rule
  val rule = RuleChain(
    projectRule,
    disposableRule,
    ApplicationServiceRule(FileEditorProviderManager::class.java, FakeFileEditorProviderManager()),
    temporaryDirectoryRule,
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
  fun newEditor_apkChanged_reloaded() {
    val apk1 = TestResources.getFile("/2.apk").toPath()
    val apk = temporaryDirectoryRule.newPath("file.apk")
    Files.copy(apk1, apk)
    val apkEditor = apkEditor(apk.pathString, isResource = false)
    assertThat(apkEditor.getNodes()).containsExactly(
      "/",
      "/AndroidManifest.xml",
      "/instant-run",
      "/instant-run.zip",
      "/instant-run/classes1.dex",
      "/res",
      "/res/anim",
      "/res/anim/fade.xml",
    )

    val apk2 = TestResources.getFile("/1.apk").toPath()
    Files.copy(apk2, apk, REPLACE_EXISTING)
    val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(apk.toFile()) ?: fail("Can't find file")
    @Suppress("UnstableApiUsage")
    runWriteAction {
      ApplicationManager.getApplication().messageBus.syncPublisher(VirtualFileManager.VFS_CHANGES).after(
        listOf(VFileContentChangeEvent(this, virtualFile, 0, 0)))
    }


    waitForCondition {
      apkEditor.getNodes().sorted() == listOf(
        "/",
        "/AndroidManifest.xml",
        "/res",
        "/res/anim",
        "/res/anim/fade.xml"
      )
    }
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
  fun selectDexFolder_createsEmptyPanel() {
    val apkEditor = apkEditor("/app-benchmark.aab")
    // Select XML node first
    apkEditor.getEditor<FileEditorComponent>(apkEditor.getNode("/base/res/layout/activity_main.xml"))

    val editor = apkEditor.getEditor<ApkFileEditorComponent>(apkEditor.getNode("/base/dex"))

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
  fun selectSo_createsSoEditor() {
    val apkEditor = apkEditor("/16kb.apk")
    val fileNode = apkEditor.getNode("/lib/x86_64/liba16kbbash.so")
    if (isPageAlignFeatureEnabled) {
      val editor = apkEditor.getEditor<AlignmentWarningViewer>(fileNode)
      assertThat(editor).isNotNull()
    } else {
      val editor = apkEditor.getEditor<FileEditorComponent>(fileNode)
      val fileEditor = editor.editor as TestFileEditor
      assertThat(fileEditor.fileType).isEqualTo("UNKNOWN")
    }
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

  @Test
  fun proguardMapping_fromBundle() {
    val apkEditor = apkEditor("/app-benchmark.aab")

    assertThat(apkEditor.proguardMapping).isNotNull()
  }

  @Test
  fun proguardMapping_fromApk() {
    val appDir = temporaryDirectoryRule.newPath("app")
    val apk = appDir.resolve("module/build/outputs/apk/release/app-release.apk")
    val mapping = appDir.resolve("module/build/outputs/mapping/release/mapping.txt")
    apk.createParentDirectories()
    mapping.createParentDirectories()
    TestResources.getFile("/obfuscated-app.apk").copyTo(apk.toFile())
    mapping.bufferedWriter().use {
      it.write(TestResources.getFile("/obfuscated-app-mapping-first-half-so-its-smaller-than-12mb.txt").readText())
      it.write(TestResources.getFile("/obfuscated-app-mapping-second-half-so-its-smaller-than-12mb.txt").readText())
    }

    val apkEditor = apkEditor(apk.pathString, isResource = false)

    assertThat(apkEditor.proguardMapping).isNotNull()
  }

  // APK .so is:
  // - compressed and not zip-aligned
  // - RELRO end is not aligned
  // expect:
  // - top level warning message and per-.so RELRO end warning
  // note:
  //   Requires building with:
  //     - NDK 18.1.5063045
  //     - Linker flags: -fuse-ld=lld -Wl,-z,relro -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=4096
  //     - C++ code to bloat the RELRO section:
  //          int target_var = 100;
  //          int * const relro_block[4096] = { [0 ... 4095] = &target_var };
  //          int rw_var = 200;
  @Test
  fun `check tree pagealign-compressed-RELRO-end-not-aligned APK`() {
    val editor = apkEditor("/pagealign-compressed-RELRO-end-not-aligned.apk")
    val dump = editor.dumpAlignmentTree()
    if (isPageAlignFeatureEnabled) {
      assertThat(dump).isEqualTo("""
        Does not support 16 KB devices
          lib
            arm64-v8a
              liba16kbbugbashflamingo.so | RELRO is not a suffix and its end is not 16 KB aligned
      """.trimIndent())
      editor.assertAlignmentWarning("/lib/arm64-v8a/liba16kbbugbashflamingo.so", """
          *All ELF Alignment Problems*
          - PT_GNU_RELRO start: 0x00024000 [end: 0x0002d000] align: 0x00000001 RELRO is not a suffix and its end is not 16 KB aligned
        """.trimIndent())
    } else {
      assertThat(dump).isEqualTo("")
      editor.assertAlignmentWarning("/lib/arm64-v8a/liba16kbbugbashflamingo.so", "")
    }
  }

  // AAB .so is:
  // - compressed and not zip-aligned
  // - RELRO end is not aligned
  // expect:
  // - top level warning message and per-.so RELRO end warning
  // note:
  //   Requires building with:
  //     - NDK 18.1.5063045
  //     - Linker flags: -fuse-ld=lld -Wl,-z,relro -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=4096
  //     - C++ code to bloat the RELRO section:
  //          int target_var = 100;
  //          int * const relro_block[4096] = { [0 ... 4095] = &target_var };
  //          int rw_var = 200;
  @Test
  fun `check tree pagealign-compressed-RELRO-end-not-aligned AAB`() {
    val editor = apkEditor("/pagealign-compressed-RELRO-end-not-aligned.aab")
    val dump = editor.dumpAlignmentTree()
    if (isPageAlignFeatureEnabled) {
      assertThat(dump).isEqualTo("""
        Does not support 16 KB devices
          base
            lib
              arm64-v8a
                liba16kbbugbashflamingo.so | RELRO is not a suffix and its end is not 16 KB aligned
      """.trimIndent())
      editor.assertAlignmentWarning("/base/lib/arm64-v8a/liba16kbbugbashflamingo.so", """
          *All ELF Alignment Problems*
          - PT_GNU_RELRO start: 0x00024000 [end: 0x0002d000] align: 0x00000001 RELRO is not a suffix and its end is not 16 KB aligned
        """.trimIndent())
    } else {
      assertThat(dump).isEqualTo("")
      editor.assertAlignmentWarning("/base/lib/arm64-v8a/liba16kbbugbashflamingo.so", "")
    }
  }

  // APK .so is:
  // - compressed and not zip-aligned
  // - not LOAD aligned
  // expect:
  // - top level warning message and per-.so warnings
  @Test
  fun `check tree pagealign-compressed-so-not-LOAD-aligned APK`() {
    val editor = apkEditor("/pagealign-compressed-so-not-LOAD-aligned.apk")
    val dump = editor.dumpAlignmentTree()
    if (isPageAlignFeatureEnabled) {
      assertThat(dump).isEqualTo("""
        Does not support 16 KB devices
          lib
            x86_64
              libtensorflowlite_jni.so | 4 KB LOAD section alignment, but 16 KB is required
              liba16kbbash.so | 4 KB LOAD section alignment, but 16 KB is required
            arm64-v8a
              libtensorflowlite_jni.so | 4 KB LOAD section alignment, but 16 KB is required
              liba16kbbash.so | 4 KB LOAD section alignment, but 16 KB is required
      """.trimIndent())
      editor.assertAlignmentWarning("/lib/arm64-v8a/libtensorflowlite_jni.so", """
        *All ELF Alignment Problems*
        - PT_LOAD start: 0x00000000 end: 0x0039c394 [align: 0x00001000] 4 KB LOAD section alignment, but 16 KB is required
        - PT_GNU_RELRO start: 0x0039d778 [end: 0x003a6000] align: 0x00000001 RELRO is not a suffix and its end is not 16 KB aligned
        """.trimIndent())
      editor.assertAlignmentWarning("/lib/x86_64/liba16kbbash.so", """
        *All ELF Alignment Problems*
        - PT_LOAD start: 0x00000000 end: 0x00054380 [align: 0x00001000] 4 KB LOAD section alignment, but 16 KB is required
        """.trimIndent())
    } else {
      assertThat(dump).isEqualTo("")
      editor.assertAlignmentWarning("/lib/arm64-v8a/libtensorflowlite_jni.so", "")
      editor.assertAlignmentWarning("/lib/x86_64/liba16kbbash.so", "")
    }
  }

  // AAB .so is:
  // - compressed and not zip-aligned
  // - not LOAD aligned
  // expect:
  // - top level warning message and per-.so warnings
  @Test
  fun `check tree pagealign-compressed-so-not-LOAD-aligned AAB`() {
    val editor = apkEditor("/pagealign-compressed-so-not-LOAD-aligned.aab")
    val dump = editor.dumpAlignmentTree()
    if (isPageAlignFeatureEnabled) {
      assertThat(dump).isEqualTo("""
        Does not support 16 KB devices
          base
            lib
              x86_64
                libtensorflowlite_jni.so | 4 KB LOAD section alignment, but 16 KB is required
                liba16kbbash.so | 4 KB LOAD section alignment, but 16 KB is required
              arm64-v8a
                libtensorflowlite_jni.so | 4 KB LOAD section alignment, but 16 KB is required
                liba16kbbash.so | 4 KB LOAD section alignment, but 16 KB is required
      """.trimIndent())
      editor.assertAlignmentWarning("/base/lib/arm64-v8a/libtensorflowlite_jni.so", """
        *All ELF Alignment Problems*
        - PT_LOAD start: 0x00000000 end: 0x0039c394 [align: 0x00001000] 4 KB LOAD section alignment, but 16 KB is required
        - PT_GNU_RELRO start: 0x0039d778 [end: 0x003a6000] align: 0x00000001 RELRO is not a suffix and its end is not 16 KB aligned
        """.trimIndent())
      editor.assertAlignmentWarning("/base/lib/x86_64/liba16kbbash.so", """
        *All ELF Alignment Problems*
        - PT_LOAD start: 0x00000000 end: 0x00054380 [align: 0x00001000] 4 KB LOAD section alignment, but 16 KB is required
        """.trimIndent())
    } else {
      assertThat(dump).isEqualTo("")
      editor.assertAlignmentWarning("/base/lib/arm64-v8a/libtensorflowlite_jni.so", "")
      editor.assertAlignmentWarning("/base/lib/x86_64/liba16kbbash.so", "")
    }
  }

  // APK .so is:
  // - compressed and not zip-aligned
  // - LOAD aligned
  // expect:
  // - no top level warning message
  @Test
  fun `check tree pagealign-compressed-so-not-zipaligned APK`() {
    val editor = apkEditor("/pagealign-compressed-so-not-zipaligned.apk")
    val dump = editor.dumpAlignmentTree()
    assertThat(dump).isEqualTo("")
  }

  // AAB .so is:
  // - compressed and not zip-aligned
  // - LOAD aligned
  // expect:
  // - no top level warning message
  @Test
  fun `check tree pagealign-compressed-so-not-zipaligned AAB`() {
    val editor = apkEditor("/pagealign-compressed-so-not-zipaligned.aab")
    val dump = editor.dumpAlignmentTree()
    assertThat(dump).isEqualTo("")
  }

  private fun apkEditor(path: String, isResource: Boolean = true): ApkEditor {
    val file = if (isResource) TestResources.getFile(path) else File(path)
    val archive = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file) ?: fail("File not found: $path")
    val root = ApkFileSystem().getRootByLocal(archive) ?: fail("Invalid archive: $path")
    val apkEditor = ApkEditor(
      project,
      archive,
      root,
      FakeAndroidApplicationInfoProvider(),
      isPageAlignFeatureEnabled)
    Disposer.register(disposableRule.disposable, apkEditor)
    apkEditor.waitForLoaded()
    return apkEditor
  }

  private inline fun <reified T : ApkFileEditorComponent> ApkEditor.getEditor(vararg nodes: ArchiveTreeNode): T {
    val editor = getEditor(nodes)
    // Since we call ApkEditor.getEditor() directly, it doesn't get disposed automatically when ApkEditor is disposed.
    Disposer.register(disposableRule.disposable, editor)
    return editor as? T ?: fail("Expected ${T::class.java.name} but got ${editor::class.java.name}")
  }

  /**
   * Assert the content of the [AlignmentWarningViewer].
   */
  private fun ApkEditor.assertAlignmentWarning(file: String, expected: String) {
    val node = getNode(file)
    if (expected.isEmpty()) {
      // Make sure it's not a warning viewer
      getEditor<FileEditorComponent>(node)
      return
    }
    val fe = getEditor<AlignmentWarningViewer>(node)
    val warning = htmlToMarkdown(fe.warningContent())
    if (warning != expected) {
      println(warning)
      assertThat(warning).isEqualTo(expected)
    }
  }

  /**
   * A simple converter from HTML to Markdown for more readable tests.
   */
  private fun htmlToMarkdown(html: String): String {
    return html
      .replace("<h3>", "*")
      .replace("</h3>", "*\n")
      .replace("<br/>", "\n")
      .replace("<li>", "- ")
      .replace("</li>", "\n")
      .replace("<strong>", "[")
      .replace("</strong>", "]")
      .trim()
  }

  /**
   * Creates a mock [FileEditorProviderManager]
   *
   * Using the default `FileEditorProviderManager` results in a [com.intellij.openapi.fileEditor.impl.text.TextEditorImpl] being created for
   * XML and text files.
   *
   * When a `TextEditorImpl` is created, it launches several asynchronous tasks that fail the test because they run interfere with disposal.
   */
  private class FakeFileEditorProvider : FileEditorProvider {
    override fun accept(project: Project, file: VirtualFile): Boolean = true
    override fun createEditor(project: Project, file: VirtualFile): FileEditor = TestFileEditor(file)
    override fun getEditorTypeId(): String = "apk-editor-test-fake"
    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.NONE
  }

  private class FakeFileEditorProviderManager : FileEditorProviderManager {
    private val provider: FileEditorProvider = FakeFileEditorProvider()
    private val providers: List<FileEditorProvider> = listOf(provider)

    override fun getProviderList(project: Project, file: VirtualFile): List<FileEditorProvider> = providers
    override suspend fun getProvidersAsync(project: Project, file: VirtualFile): List<FileEditorProvider> = providers
    override suspend fun getDumbUnawareProviders(project: Project, file: VirtualFile, excludeIds: Set<String>): List<FileEditorProvider> =
      providers.filterNot { excludeIds.contains(it.editorTypeId) }
    override fun getProvider(editorTypeId: String): FileEditorProvider? =
      provider.takeIf { it.editorTypeId == editorTypeId }
  }

  /**
   * A Test [FileEditor]
   *
   * Exposes information needed for verification and doesn't interfere with disposal.
   */
  private class TestFileEditor(virtualFile: VirtualFile) : FileEditorBase() {
    val fileType = virtualFile.fileType.name

    val fileContents = virtualFile.readText()

    override fun getComponent() = JPanel()

    override fun getName() = "TestFileEditor"

    override fun getPreferredFocusedComponent() = null
  }
}

private fun ApkEditor.waitForLoaded() {
  waitForCondition(IS_LOADED_TIMEOUT) {
    (getNodesModel().root as? ArchiveTreeNode)?.let {
      it.data.downloadFileSize > 0
    } ?: false
  }
}

private fun ApkEditor.waitForUpdateComplete() {
  waitForCondition(IS_UPDATED_TIMEOUT) {
    val tree = getTopPane().findComponent<Tree>("nodeTree")
    val model = tree.model as ApkTreeModel
    model.isUpdateComplete
  }
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

/**
 * Dump the content of the tree for rows that have alignment issues reported.
 */
private fun ApkEditor.dumpAlignmentTree() = dumpTree("Name", "AlignmentCell") { type, value ->
  type == "AlignmentCell" && value != ""
}

/**
 * Dump the content of the tree by calling the individual renderers.
 * Dump the columns with renderers matching [columns].
 * Take only the rows where [predicate] returns true for one of the cells.
 */
private fun ApkEditor.dumpTree(
  vararg columns: String,
  predicate: (columnName: String, columnValue: String) -> Boolean
): String {
  val columns = columns.map { "${it}Renderer" }
  fun columnPredicate(columnName: String, columnValue: String) =
    predicate(columnName.substringBefore("Renderer"), columnValue)
  // Wait for tree node warnings to complete expanding.
  waitForUpdateComplete()
  val tree = getTopPane().findComponent<Tree>("nodeTree")
  val sb = StringBuilder()
  val renderer = tree.cellRenderer

  data class RowSnapshot(
    val path: TreePath,
    val formattedText: String,
    val depth: Int
  )

  fun collectRowValues(
    component: Component,
    accumulator: MutableList<Pair<String, String>>
  ) {
    val renderType = component.javaClass.simpleName

    if (renderType in columns) {
      val text = when (component) {
        is JLabel -> component.text
        is JTextComponent -> component.text
        is SimpleColoredComponent -> component.toString()
        else -> null
      }

      if (!text.isNullOrBlank()) {
        accumulator.add(renderType to text)
      }
    }

    if (component is Container) {
      for (child in component.components) {
        collectRowValues(child, accumulator)
      }
    }
  }

  val rows = mutableListOf<RowSnapshot>()
  val pathsToKeep = HashSet<TreePath>()

  // PASS 1: Extract data, check predicate, and mark ancestors
  for (i in 0 until tree.rowCount) {
    val path = tree.getPathForRow(i) ?: continue
    val value = path.lastPathComponent

    val component = renderer.getTreeCellRendererComponent(
      tree,
      value,
      tree.isRowSelected(i),
      tree.isExpanded(i),
      tree.model.isLeaf(value),
      i,
      tree.hasFocus() && tree.leadSelectionRow == i
    )

    val rowValues = mutableListOf<Pair<String, String>>()
    collectRowValues(component, rowValues)

    // Check if this specific row matches
    val isMatch = rowValues.any { (type, value) -> columnPredicate(type, value) }

    if (isMatch) {
      // Mark this path AND all ancestors as "keep"
      var currentPath: TreePath? = path
      while (currentPath != null) {
        pathsToKeep.add(currentPath)
        currentPath = currentPath.parentPath
      }
    }

    rows.add(
      RowSnapshot(
        path = path,
        formattedText = rowValues.joinToString(" | ") { it.second },
        depth = (path.pathCount - 1).coerceAtLeast(0)
      )
    )
  }

  // PASS 2: Render only the kept paths
  for (row in rows) {
    if (pathsToKeep.contains(row.path)) {
      sb.append("  ".repeat(row.depth))
      sb.append(row.formattedText)
      sb.append("\n")
    }
  }

  return sb.toString().trimEnd()
}

private fun DefaultMutableTreeNode.getFilePath(): String {
  return when (userObject) {
    is ArchivePathEntry -> (userObject as ArchivePathEntry).path.pathString
    else -> userObject.toString()
  }
}

private inline fun <reified T : JComponent> JComponent.findComponent(name: String): T {
  return TreeWalker(this).descendants().filterIsInstance<T>().find { it.name == name }
         ?: fail("${T::class.simpleName} named $name was not found")
}

private fun waitForCondition(condition: () -> Boolean) = waitForCondition(IS_LOADED_TIMEOUT, condition)

private fun DexFileViewer.getClassCount(): Int {
  val tree = component.findComponent<Tree>("DexTree")
  return tree.getClassCount()
}

private fun Tree.getClassCount(): Int {
  waitForCondition { model.root !is LoadingNode }
  return model.asSequence().count { it is DexClassNode }
}

private fun ResourceFile.getAllChunks(): Sequence<Any> {
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
