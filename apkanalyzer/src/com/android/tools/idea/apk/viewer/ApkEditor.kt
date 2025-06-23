/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.SdkConstants.EXT_DEX
import com.android.tools.apk.analyzer.ApkSizeCalculator
import com.android.tools.apk.analyzer.Archive
import com.android.tools.apk.analyzer.ArchiveContext
import com.android.tools.apk.analyzer.Archives
import com.android.tools.apk.analyzer.BinaryXmlParser
import com.android.tools.apk.analyzer.dex.ProguardMappings
import com.android.tools.apk.analyzer.internal.AppBundleArchive
import com.android.tools.apk.analyzer.internal.ArchiveTreeNode
import com.android.tools.idea.FileEditorUtil
import com.android.tools.idea.apk.viewer.arsc.ArscViewer
import com.android.tools.idea.apk.viewer.dex.DexFileViewer
import com.android.tools.idea.apk.viewer.diff.ApkDiffPanel
import com.android.tools.idea.log.LogWrapper
import com.android.tools.instrumentation.threading.agent.callback.ThreadingCheckerUtil
import com.android.tools.proguard.ProguardMap
import com.android.utils.FileUtils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.limits.FileSizeLimit
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import org.jetbrains.annotations.VisibleForTesting
import java.beans.PropertyChangeListener
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Optional
import javax.swing.JComponent
import javax.swing.LayoutFocusTraversalPolicy
import kotlin.io.path.name
import kotlin.math.max

internal class ApkEditor(
  private val project: Project,
  private val baseFile: VirtualFile,
  private val root: VirtualFile,
  private val applicationInfoProvider: AndroidApplicationInfoProvider
) : UserDataHolderBase(), FileEditor, ApkViewPanel.Listener {
  private var baseFileHash: String = ""
  private var apkViewPanel: ApkViewPanel? = null
  private var archiveContext: ArchiveContext? = null

  private val splitter: JBSplitter
  private var currentEditor: ApkFileEditorComponent? = null
  @VisibleForTesting
  var proguardMapping: ProguardMappings? = null

  init {
    FileEditorUtil.DISABLE_GENERATED_FILE_NOTIFICATION_KEY.set(this, true)

    splitter = JBSplitter(true, "android.apk.viewer", 0.62f)
    splitter.setName("apkViewerContainer")

    // Setup focus root for a11y purposes
    // Given that
    // 1) IdeFrameImpl sets up a custom focus traversal policy that unconditionally set the focus to the preferred component
    //    of the editor window.
    // 2) IdeFrameImpl is the default focus cycle root for editor windows
    // (see https://github.com/JetBrains/intellij-community/commit/65871b384739b52b1c0450235bc742d2ba7fb137#diff-5b11919bab177bf9ab13c335c32874be)
    //
    // We need to declare the root component of this custom editor to be a focus cycle root and
    // set up the default focus traversal policy (layout) to ensure the TAB key cycles through all
    // the components of this custom panel.
    splitter.setFocusCycleRoot(true)
    splitter.setFocusTraversalPolicy(LayoutFocusTraversalPolicy())

    // The APK Analyzer uses a copy of the APK to open it as an Archive. It does so far two reasons:
    // 1. We don't want the editor holding a lock on an APK (applies only to Windows)
    // 2. Since an Archive creates a FileSystem under the hood, we don't want the zip file's contents
    // to change while the FileSystem is open, since this may lead to JVM crashes
    // But if we do a copy, we need to update it whenever the real file changes. So we listen to changes
    // in the VFS as long as this editor is open.
    val connection = project.messageBus.connect(this)
    connection.subscribe<BulkFileListener>(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: MutableList<out VFileEvent>) {
        val basePath = baseFile.path
        for (event in events) {
          if (FileUtil.pathsEqual(basePath, event.getPath())) {
            if (baseFile.isValid) { // If the file is deleted, the editor is automatically closed.
              if (baseFileHash != generateHash(Path.of(event.getPath()))) {
                refreshApk(baseFile)
              }
            }
          }
        }
      }
    })

    refreshApk(baseFile)
    splitter.setSecondComponent(EmptyPanel().getComponent())
  }

  private fun refreshApk(apkVirtualFile: VirtualFile) {
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Reading APK contents") {
      override fun run(indicator: ProgressIndicator) {
        disposeArchive()
        try {
          // This temporary copy is destroyed while disposing the archive, the disposeArchive method.
          val copyOfApk = Files.createTempFile(apkVirtualFile.nameWithoutExtension, "." + apkVirtualFile.getExtension())
          FileUtils.copyFile(VfsUtilCore.virtualToIoFile(apkVirtualFile).toPath(), copyOfApk)
          val context = Archives.open(copyOfApk, LogWrapper(log))
          archiveContext = context
          proguardMapping = loadProguardMapping(context.getArchive(), apkVirtualFile.toNioPath())
          // TODO(b/244771241) ApkViewPanel should be created on the UI thread
          val panel = ThreadingCheckerUtil.withChecksDisabledForSupplier {
            ApkViewPanel(
              ApkParser(context, ApkSizeCalculator.getDefault()),
              apkVirtualFile.name,
              applicationInfoProvider
            )
          }
          apkViewPanel = panel
          panel.setListener(this@ApkEditor)
          ApplicationManager.getApplication().invokeLater {
            splitter.setFirstComponent(panel.container)
            selectionChanged(null)
          }
          val hash = generateHash(apkVirtualFile.toNioPath())
          if (hash != null) {
            baseFileHash = hash
          }
        } catch (e: IOException) {
          log.error(e)
          disposeArchive()
          splitter.setFirstComponent(JBLabel(e.toString()))
        }
      }
    })
  }

  /**
   * Changes the editor displayed based on the path selected in the tree.
   */
  override fun selectionChanged(entries: Array<ArchiveTreeNode>?) {
    if (currentEditor != null) {
      Disposer.dispose(currentEditor!!)
      // Null out the field immediately after disposal, in case an exception is thrown later in the method.
      currentEditor = null
    }

    val editor = getEditor(entries)
    splitter.setSecondComponent(editor.getComponent())
    currentEditor = editor
  }

  override fun selectApkAndCompare() {
    val desc = FileChooserDescriptor(true, false, false, false, false, false)
    desc.withFileFilter(Condition { file: VirtualFile? -> ApkFileSystem.EXTENSIONS.contains(file!!.getExtension()) })
    val file = FileChooser.chooseFile(desc, project, null)
    if (file == null) {
      return  // User canceled.
    }
    val oldApk: VirtualFile? = checkNotNull(ApkFileSystem.getInstance().getRootByLocal(file))
    val builder = DialogBuilder(project)
    builder.setTitle(oldApk!!.name + " (old) vs " + root.name + " (new)")
    val panel = ApkDiffPanel(oldApk, root)
    builder.setCenterPanel(panel.container)
    builder.setPreferredFocusComponent(panel.preferredFocusedComponent)
    builder.addCloseButton()
    builder.show()
  }

  override fun getComponent(): JComponent {
    return splitter
  }

  override fun getPreferredFocusedComponent(): JComponent? {
    if (apkViewPanel == null) {
      return null
    }
    return apkViewPanel!!.preferredFocusedComponent
  }

  override fun getName(): String {
    return baseFile.name
  }

  override fun setState(state: FileEditorState) {
  }

  override fun isModified(): Boolean {
    return false
  }

  override fun isValid(): Boolean {
    return baseFile.isValid
  }

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {
  }

  override fun removePropertyChangeListener(listener: PropertyChangeListener) {
  }

  override fun getFile(): VirtualFile {
    return baseFile
  }

  override fun dispose() {
    if (currentEditor != null) {
      Disposer.dispose(currentEditor!!)
      currentEditor = null
    }
    log.info("Disposing ApkEditor with ApkViewPanel: $apkViewPanel")
    disposeArchive()
  }

  private fun disposeArchive() {
    if (apkViewPanel != null) {
      apkViewPanel!!.clearArchive()
    }
    if (archiveContext != null) {
      try {
        archiveContext!!.close()
        // The archive was constructed out of a temporary file.
        Files.deleteIfExists(archiveContext!!.getArchive().getPath())
      } catch (e: IOException) {
        log.warn(e)
      }
      archiveContext = null
    }
  }

  @VisibleForTesting
  fun getEditor(nodes: Array<out ArchiveTreeNode>?): ApkFileEditorComponent {
    if (nodes.isNullOrEmpty()) {
      return EmptyPanel()
    }

    // Check if multiple dex files are selected and return a multiple dex viewer.
    var allDex = true
    for (path in nodes) {
      val dataPath = path.data.path
      val fileName = dataPath.fileName
      if (fileName == null || Files.isDirectory(dataPath) || !fileName.toString().endsWith(".$EXT_DEX")) {
        allDex = false
        break
      }
    }

    if (allDex) {
      val paths = nodes.map { it.data.path }.toTypedArray()
      return DexFileViewer(project, paths, baseFile.parent, proguardMapping)
    }

    // Only one file or many files with different extensions are selected. We can only show
    // a single editor for a single filetype, so arbitrarily pick the first file:
    val n: ArchiveTreeNode = nodes[0]
    val p = n.data.path
    val fileName = p.fileName
    if (fileName == null) {
      return EmptyPanel()
    }
    if ("resources.arsc" == fileName.toString()) {
      val arscContent: ByteArray?
      try {
        arscContent = Files.readAllBytes(p)
      } catch (_: IOException) {
        return EmptyPanel()
      }
      return ArscViewer(arscContent)
    }

    // Attempting to view these kinds of files is going to trigger the Kotlin metadata decompilers, which all assume the .class files
    // accompanying them can be found next to them. But in our case the class files have been dexed, so the Kotlin compiler backend is going
    // to attempt code generation, and that will fail with some rather cryptic errors.
    if (p.toString().endsWith("kotlin_builtins") || p.toString().endsWith("kotlin_metadata")) {
      return EmptyPanel()
    }

    val file = createVirtualFile(n.data.archive, p)
    val providers = getFileEditorProviders(file)
    if (providers.isEmpty) {
      return EmptyPanel()
    } else if (file != null) {
      val editor = providers.get().createEditor(project, file)
      return FileEditorComponent(editor)
    } else {
      return EmptyPanel()
    }
  }

  private fun createVirtualFile(archive: Archive, p: Path): VirtualFile? {
    val name = p.fileName
    if (name == null) {
      return null
    }

    // No virtual file for directories.
    if (Files.isDirectory(p)) {
      return null
    }

    // Read file contents and decode it.
    var content: ByteArray?
    try {
      content = Files.readAllBytes(p)
    } catch (e: IOException) {
      log.warn(String.format("Error loading entry \"%s\" from archive", p), e)
      return null
    }

    if (archive.isBinaryXml(p, content)) {
      content = BinaryXmlParser.decodeXml(content)
      return ApkVirtualFile.create(p, content)
    }

    if (archive.isProtoXml(p, content)) {
      try {
        val prettyPrinter: ProtoXmlPrettyPrinter = ProtoXmlPrettyPrinterImpl()
        val text = prettyPrinter.prettyPrint(content)
        return ApkVirtualFile.createText(p, text)
      } catch (e: IOException) {
        // Ignore error, show encoded content.
        log.warn(String.format("Error decoding XML entry \"%s\" from archive", p), e)
      }
      return ApkVirtualFile.create(p, content)
    }

    if (archive.isBaselineProfile(p, content)) {
      @Suppress("UnstableApiUsage") val text: String? =
        getPrettyPrintedBaseline(baseFile, content, p, FileSizeLimit.getContentLoadLimit(baseFile.getExtension()))
      return when (text) {
        null -> ApkVirtualFile.create(p, content)
        else -> ApkVirtualFile.createText(p, text)
      }
    }

    val file = JarFileSystem.getInstance().findFileByPath(archive.getPath().toString())
    return file?.findFileByRelativePath(p.toString()) ?: ApkVirtualFile.create(p, content)
  }

  private fun getFileEditorProviders(file: VirtualFile?): Optional<FileEditorProvider> {
    if (file == null || file.isDirectory) {
      return Optional.empty<FileEditorProvider>()
    }

    val providers = FileEditorProviderManager.getInstance().getProviderList(project, file)

    // Skip 9 patch editor since nine patch information has been stripped out.
    return providers.stream()
      .filter { fileEditorProvider: FileEditorProvider? -> fileEditorProvider!!.javaClass.getName() != "com.android.tools.idea.editors.NinePatchEditorProvider" }
      .findFirst()
  }

  companion object {
    private fun generateHash(path: Path): String? {
      try {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = Files.readAllBytes(path)
        val hashBytes = digest.digest(bytes)
        val hashString = java.lang.StringBuilder()
        for (b in hashBytes) {
          // Append each byte as a two-character hexadecimal string.
          hashString.append(String.format("%02x", b))
        }
        return hashString.toString()
      }
      catch (_: NoSuchAlgorithmException) {
        return null
      }
      catch (_: IOException) {
        return null
      }
    }

    private val log: Logger
      get() = Logger.getInstance(ApkEditor::class.java)

    @VisibleForTesting
    fun getPrettyPrintedBaseline(basefile: VirtualFile, content: ByteArray, path: Path, maxChars: Int): String? {
      val text: String?
      try {
        text = BaselineProfilePrettyPrinter.prettyPrint(basefile, path, content)
      } catch (e: IOException) {
        log.warn(String.format("Error decoding baseline entry \"%s\" from archive", path), e)
        return null
      }

      if (text.length > maxChars) {
        val truncated = StringBuilder(100000)
        val length = text.toByteArray(Charsets.UTF_8).size
        truncated.append(
          """
              The contents of this baseline file is too large to show by default.
              You can increase the maximum buffer size by setting the property
                  idea.max.content.load.filesize=$length
              (or higher)
              
              
          """.trimIndent()
        )

        try {
          val file = File.createTempFile("baseline", ".txt")
          file.writeText(text, Charsets.UTF_8)
          truncated.append(
            """
              Alternatively, the full contents have been written to the following
              temp file:
              ${file.path}
              
              
            """.trimIndent()
          )
        } catch (_: IOException) {
          // Couldn't write temp file -- oh well, we just aren't including a mention of it.
        }

        // The header has taken up around 380 characters. It varies slightly, based on the path
        // to the temporary file. We want to make the test output stable (such that the "truncated X chars"
        // string doesn't vary from run to run) so we'll round up to say 450, instead of using
        // truncated.length() here.
        val truncateAt = max(0, maxChars - 450)
        truncated.append(text, 0, truncateAt).append("\n....truncated ").append(length - truncateAt).append(" characters.")

        return truncated.toString()
      }
      return text
    }

    private fun loadProguardMapping(archive: Archive, path: Path): ProguardMappings? {
      if (archive is AppBundleArchive) {
        // App bundles contain their mapping.
        return archive.loadProguardMapping()
      }

      // Try to find the mapping file assuming file structure:
      //  module/build/outputs/apk/variant/app-variant.apk
      //  module/build/outputs/mapping/variant/mapping.txt
      val parent = path.parent  ?: return null
      val variant = parent.name
      val mapping = parent.parent?.parent?.resolve("mapping/$variant/mapping.txt") ?: return null
      return try {
        val proguardMap = ProguardMap()
        proguardMap.readFromFile(mapping.toFile())
        ProguardMappings(proguardMap, null, null)
      } catch (e: IOException) {
        log.warn("Error loading Proguard mapping from $mapping", e)
        null
      }
    }
  }
}
