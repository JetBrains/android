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
package com.android.tools.idea.apk.viewer

import com.android.SdkConstants
import com.android.tools.adtui.common.ColumnTree
import com.android.tools.adtui.common.ColumnTreeBuilder
import com.android.tools.adtui.common.ColumnTreeBuilder.ColumnBuilder
import com.android.tools.adtui.util.getHumanizedSize
import com.android.tools.analytics.UsageTracker.log
import com.android.tools.apk.analyzer.AndroidApplicationInfo
import com.android.tools.apk.analyzer.ArchiveEntry
import com.android.tools.apk.analyzer.ArchiveErrorEntry
import com.android.tools.apk.analyzer.ArchiveNode
import com.android.tools.apk.analyzer.ArchiveTreeStructure
import com.android.tools.apk.analyzer.Archives
import com.android.tools.apk.analyzer.internal.ApkArchive
import com.android.tools.apk.analyzer.internal.ArchiveTreeNode
import com.android.tools.apk.analyzer.internal.InstantAppBundleArchive
import com.android.tools.idea.apk.viewer.ApkParser.Align16kbCompliance
import com.android.tools.idea.apk.viewer.PercentRenderer.PercentProvider
import com.android.tools.idea.apk.viewer.pagealign.AlignmentCellRenderer
import com.android.tools.idea.apk.viewer.pagealign.findPageAlignWarningsPaths
import com.android.tools.idea.apk.viewer.pagealign.getAlignmentFinding
import com.android.tools.idea.concurrency.transform
import com.android.tools.idea.concurrency.transformAsync
import com.android.tools.idea.stats.AnonymizerUtil
import com.google.common.base.Function
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.MoreExecutors
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ApkAnalyzerStats
import com.google.wireless.android.sdk.stats.ApkAnalyzerStats.ApkAnalyzerAlignNative16kbEventType.ALIGN_NATIVE_COMPLIANT_APK_ANALYZED
import com.google.wireless.android.sdk.stats.ApkAnalyzerStats.ApkAnalyzerAlignNative16kbEventType.ALIGN_NATIVE_NON_COMPLIANT_APK_ANALYZED
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.search.SearchUtil.appendFragments
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.IconManager
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.PlatformIcons
import com.intellij.ui.SideBorder
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.treeStructure.Tree
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER
import com.intellij.uiDesigner.core.GridConstraints.ANCHOR_EAST
import com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH
import com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL
import com.intellij.uiDesigner.core.GridConstraints.FILL_NONE
import com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW
import com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK
import com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED
import com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.uiDesigner.core.Spacer
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.FlowLayout
import java.awt.Insets
import java.nio.file.ClosedFileSystemException
import java.nio.file.Files
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.RowSorter.SortKey
import javax.swing.SortOrder
import javax.swing.SwingConstants
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath
import org.jetbrains.annotations.TestOnly
import org.jetbrains.ide.PooledThreadExecutor

private const val SIZE_VARIABLE = SIZEPOLICY_CAN_SHRINK or SIZEPOLICY_CAN_GROW
private const val SIZE_FIXED = SIZEPOLICY_FIXED
private val SPACER_SPEC = GridConstraints(0, 1, 1, 1, ANCHOR_CENTER, FILL_HORIZONTAL, SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false)
private val TREE_SPEC = GridConstraints(2, 0, 1, 2, ANCHOR_CENTER, FILL_BOTH, SIZE_VARIABLE, SIZE_VARIABLE, null, null, null, 0, false)
private val PANEL1_SPEC = GridConstraints(0, 0, 1, 1, ANCHOR_CENTER, FILL_BOTH, SIZE_FIXED, SIZE_FIXED, null, null, null, 0, false)
private val PANEL2_SPEC = GridConstraints(1, 0, 1, 1, ANCHOR_CENTER, FILL_BOTH, SIZE_FIXED, SIZE_FIXED, null, null, null, 0, false)
private val COMPARE_SPEC = GridConstraints(1, 1, 1, 1, ANCHOR_EAST, FILL_NONE, SIZE_FIXED, SIZE_FIXED, null, null, null, 0, false)

internal class ApkViewPanel(
  private val apkParser: ApkParser,
  apkName: String,
  applicationInfoProvider: AndroidApplicationInfoProvider,
  private val isPageAlignFeatureEnabled: Boolean,
) : TreeSelectionListener {
  private val nameComponent = SimpleColoredComponent()
  private val sizeComponent = SimpleColoredComponent()
  private val nameAsyncIcon = AsyncProcessIcon("aapt xmltree manifest")
  private val sizeAsyncIcon = AsyncProcessIcon("estimating apk size")
  private val compareWithButton = JButton("Compare with previous APK...")
  private val tree = Tree(ApkTreeModel(null))
  private val columnTree = buildTree()
  private var archiveDisposed = false

  private var listener: Listener? = null

  val container: JComponent = JPanel()
  val rootComponent: JComponent = container
  val preferredFocusedComponent: JComponent = tree
  val treeModel
    get() = tree.model as ApkTreeModel

  interface Listener {
    fun selectionChanged(entries: Array<ArchiveTreeNode>?)

    fun selectApkAndCompare()
  }

  private fun setupUI() {
    createUIComponents()
    container.setLayout(GridLayoutManager(3, 2, Insets(0, 0, 0, 0), -1, -1))
    val spacer1 = Spacer()
    container.add(spacer1, SPACER_SPEC)
    container.add(columnTree, TREE_SPEC)
    val panel1 = JPanel()
    panel1.setLayout(FlowLayout(FlowLayout.LEFT, 5, 0))
    container.add(panel1, PANEL1_SPEC)
    panel1.add(nameComponent)
    panel1.add(nameAsyncIcon)
    val panel2 = JPanel()
    panel2.setLayout(FlowLayout(FlowLayout.LEFT, 5, 0))
    container.add(panel2, PANEL2_SPEC)
    panel2.add(sizeComponent)
    panel2.add(sizeAsyncIcon)
    container.add(compareWithButton, COMPARE_SPEC)
  }

  init {
    // construct the main tree along with the uncompressed sizes
    setupUI()
    Futures.addCallback<ArchiveNode>(
      apkParser.constructTreeStructure(),
      object : FutureCallBackAdapter<ArchiveNode>() {

        override fun onSuccess(result: ArchiveNode) {
          if (archiveDisposed) {
            return
          }
          setRootNode(result)
          sort()
        }
      },
      EdtExecutorService.getInstance(),
    )

    // kick off computation of the compressed archive, and once it's available, refresh the tree
    val treeStructureFuture =
      apkParser.updateTreeWithDownloadSizes().transform(EdtExecutorService.getInstance()) { node ->
        try {
          if (archiveDisposed) {
            return@transform null
          }
          refreshTree()
          // Download size column has changed so force a sort
          sort(force = true)
        } catch (e: Exception) {
          // Ignore exceptions if the archive was disposed (b/351919218)
          if (!archiveDisposed) {
            throw e // propagate failure
          }
        }
        node
      }
    container.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM))

    compareWithButton.addActionListener { listener?.selectApkAndCompare() }

    // identify and set the application name and version
    nameAsyncIcon.isVisible = true
    nameComponent.append("Parsing Manifest")

    // find a suitable archive that has an AndroidManifest.xml file in the root ("/")
    // for APKs, this will always be the APK itself
    // for ZIP files (AIA bundles), this will be the first found APK using breadth-first search
    val applicationInfo =
      apkParser.constructTreeStructure().transformAsync(PooledThreadExecutor.INSTANCE) { node ->
        val entry = Archives.getFirstManifestArchiveEntry(node)
        return@transformAsync if (entry == null) {
          setToZipMode(apkName)
          Futures.immediateFailedFuture(Exception("Regular .zip, not valid .apk file."))
        } else {
          try {
            applicationInfoProvider.getApplicationInfo(apkParser, entry)
          } catch (e: Exception) {
            setToZipMode(apkName)
            LOG.warn(e)
            Futures.immediateFailedFuture(e)
          }
        }
      }

    val uncompressedApkSize = apkParser.getUncompressedApkSize()
    val compressedFullApkSize = apkParser.getCompressedFullApkSize()
    val align16kbCompliance = apkParser.getAlign16kbCompliance()

    val appInfoUpdated =
      applicationInfo.transform(EdtExecutorService.getInstance()) { info ->
        if (archiveDisposed) {
          return@transform null
        }
        setAppInfo(info)
        info
      }

    Futures.addCallback(
      Futures.successfulAsList(treeStructureFuture, appInfoUpdated),
      object : FutureCallBackAdapter<MutableList<Any?>?>() {
        override fun onSuccess(result: MutableList<Any?>?) {
          treeModel.setUpdateTreeWithDownloadSizesComplete()
          treeModel.setUpdateTreeWithApplicationInfo()
          expandTreeNodesWhenInformationComplete()
        }

        override fun onFailure(t: Throwable) {
          treeModel.setUpdateTreeWithDownloadSizesComplete()
          treeModel.setUpdateTreeWithApplicationInfo()
          expandTreeNodesWhenInformationComplete()
        }
      },
      EdtExecutorService.getInstance(),
    )

    // obtain and set the download size
    sizeAsyncIcon.isVisible = true
    sizeComponent.append("Estimating download size..")
    Futures.addCallback(
      Futures.successfulAsList<Long>(uncompressedApkSize, compressedFullApkSize),
      object : FutureCallBackAdapter<MutableList<Long?>?>() {
        override fun onSuccess(result: MutableList<Long?>?) {
          if (archiveDisposed) {
            return
          }
          if (result != null) {
            val uncompressed = result[0]
            val compressed = result[1]
            // successfulAsList() returns null items for failed futures.
            setApkSizes(uncompressed ?: 0, compressed ?: 0)
          }
        }
      },
      EdtExecutorService.getInstance(),
    )

    val combiner = Futures.whenAllComplete<Any?>(uncompressedApkSize, compressedFullApkSize, applicationInfo)
    combiner
      .call<Any?>(
        {
          // Record stats.
          val applicationId = applicationInfo.get()!!.packageId
          val stats =
            ApkAnalyzerStats.newBuilder().setCompressedSize(compressedFullApkSize.get()!!).setUncompressedSize(uncompressedApkSize.get()!!)
          if (align16kbCompliance.get() != Align16kbCompliance.NO_ELF_FILES) {
            stats.align16Type =
              when (align16kbCompliance.get() == Align16kbCompliance.COMPLIANT) {
                true -> ALIGN_NATIVE_COMPLIANT_APK_ANALYZED
                false -> ALIGN_NATIVE_NON_COMPLIANT_APK_ANALYZED
              }
          }
          log(
            AndroidStudioEvent.newBuilder()
              .setKind(AndroidStudioEvent.EventKind.APK_ANALYZER_STATS)
              .setProjectId(AnonymizerUtil.anonymizeUtf8(applicationId))
              .setRawProjectId(applicationId)
              .setApkAnalyzerStats(stats)
          )
          null
        },
        MoreExecutors.directExecutor(),
      )
      .addListener({}, MoreExecutors.directExecutor())
    container.setName("ApkViewPanel")
  }

  private fun expandTreeNodesWhenInformationComplete() {
    // Exit if required information isn't available yet.
    if (!treeModel.isUpdateTreeWithDownloadSizesComplete || !treeModel.isUpdateTreeWithApplicationInfoComplete) return
    // Exit if we've already expanded nodes.
    if (treeModel.isUpdateTreeWithWarningExpansionsComplete) return
    try {
      if (!isPageAlignFeatureEnabled) return
      val root = treeModel.getRoot()
      if (root is ArchiveNode) {
        tree.expandPaths(findPageAlignWarningsPaths(root, treeModel.extractNativeLibs))
      }
    } finally {
      treeModel.setUpdateTreeWithWarningExpansions()
    }
  }

  private fun setToZipMode(fileName: String) {
    // Reset UI elements for .zip files (instead of .apk).
    compareWithButton.setEnabled(false)
    compareWithButton.isVisible = false
    nameAsyncIcon.isVisible = false
    nameComponent.clear()
    nameComponent.append(fileName)
  }

  private fun createUIComponents() {
    tree.setName("nodeTree")
    tree.setShowsRootHandles(true)
    tree.setRootVisible(true) // show root node only when showing LoadingNode
    tree.setPaintBusy(true)

    tree.addTreeSelectionListener(this)
  }

  fun setListener(listener: Listener) {
    this.listener = listener
  }

  fun clearArchive() {
    archiveDisposed = true
    apkParser.cancelAll()
    // The only other place that sets the root node is another queued runnable also on the EDT thread.
    // Therefore, there will be no interleaving of operations.
    ApplicationManager.getApplication().invokeLater { setRootNode(null) }
    LOG.info("Cleared Archive on ApkViewPanel: $this")
  }

  private fun setRootNode(root: ArchiveNode?) {
    try {
      val treeModel = ApkTreeModel(root)
      if (root != null) {
        tree.setPaintBusy(root.data.downloadFileSize < 0)
      }
      tree.setModel(treeModel)
    } catch (e: Exception) {
      // Ignore exceptions if the archive was disposed (b/351919218)
      if (!archiveDisposed) {
        throw e
      }
    }
  }

  private fun refreshTree() {
    tree.setPaintBusy(false)
    tree.removeTreeSelectionListener(this)
    val selected = tree.getSelectionPaths()
    treeModel.reload()
    tree.selectionPaths = selected
    tree.addTreeSelectionListener(this)
  }

  private fun setApkSizes(uncompressed: Long, compressedFullApk: Long) {
    sizeComponent.clear()

    sizeAsyncIcon.isVisible = false
    Disposer.dispose(sizeAsyncIcon)

    sizeComponent.setIcon(AllIcons.General.BalloonInformation)
    when (apkParser.archive) {
      is ApkArchive -> {
        sizeComponent.append("APK size: ")
        sizeComponent.append(getHumanizedSize(uncompressed), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
        sizeComponent.append(", Download Size: ")
        sizeComponent.setToolTipText(
          """
          1. The <b>APK size</b> reflects the actual size of the file, and is the minimum amount of space it will consume on the disk after installation.
          2. The <b>download size</b> is the estimated size of the file for new installations (Google Play serves a highly compressed version of the file).
          For application updates, Google Play serves patches that are typically much smaller.
          The installation size may be higher than the APK size depending on various other factors.
          """
            .trimIndent()
        )
      }

      is InstantAppBundleArchive -> {
        sizeComponent.append("Zip file size: ")
        sizeComponent.setToolTipText("The <b>zip file size</b> reflects the actual size of the zip file on disk.\n")
      }

      else -> {
        sizeComponent.append("Raw File Size: ")
      }
    }
    sizeComponent.append(getHumanizedSize(compressedFullApk), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
  }

  private fun setAppInfo(appInfo: AndroidApplicationInfo) {
    nameComponent.clear()

    nameAsyncIcon.isVisible = false
    Disposer.dispose(nameAsyncIcon)

    nameComponent.append(appInfo.packageId, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
    nameComponent.append(" (Version Name: ", SimpleTextAttributes.GRAY_ATTRIBUTES)
    nameComponent.append(appInfo.versionName, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    nameComponent.append(", Version Code: ", SimpleTextAttributes.GRAY_ATTRIBUTES)
    nameComponent.append(appInfo.versionCode.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES)
    nameComponent.append(")", SimpleTextAttributes.GRAY_ATTRIBUTES)
    treeModel.extractNativeLibs = appInfo.extractNativeLibs
  }

  override fun valueChanged(e: TreeSelectionEvent) {
    if (listener != null) {
      val paths = (e.getSource() as Tree).getSelectionPaths()
      val components =
        paths
          ?.map {
            val node = it.lastPathComponent as? ArchiveTreeNode
            if (node == null) {
              listener?.selectionChanged(null)
              return
            }
            node
          }
          ?.toTypedArray()

      listener!!.selectionChanged(components)
    }
  }

  class ApkTreeModel(root: TreeNode?) : DefaultTreeModel(root) {
    var extractNativeLibs: Boolean? = null

    // Update flags track the completion of the futures that provide the data needed to
    // render the tree. These flags will be set to true when the corresponding future
    // completes regardless of whether the future completed with success or failure.
    var isUpdateTreeWithDownloadSizesComplete: Boolean = false
      private set

    var isUpdateTreeWithApplicationInfoComplete: Boolean = false
      private set

    // This flag tracks if the tree expansion for warnings has been performed.
    var isUpdateTreeWithWarningExpansionsComplete: Boolean = false
      private set

    fun setUpdateTreeWithDownloadSizesComplete() {
      this.isUpdateTreeWithDownloadSizesComplete = true
    }

    fun setUpdateTreeWithApplicationInfo() {
      this.isUpdateTreeWithApplicationInfoComplete = true
    }

    fun setUpdateTreeWithWarningExpansions() {
      this.isUpdateTreeWithWarningExpansionsComplete = true
    }

    @get:TestOnly
    val isUpdateComplete: Boolean
      /** Return true if the tree has been populated and any warning nodes expanded. */
      get() =
        this.isUpdateTreeWithDownloadSizesComplete &&
          this.isUpdateTreeWithApplicationInfoComplete &&
          this.isUpdateTreeWithWarningExpansionsComplete
  }

  open class FutureCallBackAdapter<V> : FutureCallback<V> {
    override fun onSuccess(result: V) {}

    override fun onFailure(t: Throwable) {}
  }

  class NameRenderer(private val myApkParser: ApkParser, private val mySpeedSearch: TreeSpeedSearch) : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(
      tree: JTree,
      value: Any,
      selected: Boolean,
      expanded: Boolean,
      leaf: Boolean,
      row: Int,
      hasFocus: Boolean,
    ) {
      try {
        if (value !is ArchiveNode) {
          append(value.toString())
          return
        }

        val entry = value.getData()
        setIcon(getIconFor(entry))
        val name = entry.getNodeDisplayString()
        val attr = if (entry is ArchiveErrorEntry) SimpleTextAttributes.ERROR_ATTRIBUTES else SimpleTextAttributes.REGULAR_ATTRIBUTES
        appendFragments(mySpeedSearch.enteredPrefix, name, attr.style, attr.fgColor, attr.bgColor, this)
      } catch (e: Exception) {
        // Ignore exceptions if the file doesn't exist (b/351919218)
        if (Files.exists(myApkParser.archive.path)) {
          throw e
        }
      }
    }

    companion object {
      private fun getIconFor(entry: ArchiveEntry): Icon {
        if (entry is ArchiveErrorEntry) {
          return StudioIcons.Common.WARNING
        }
        val path = entry.path
        val base = path.fileName
        var fileName = base?.toString() ?: ""
        var isDirectory = false
        try {
          isDirectory = Files.isDirectory(path)
        } catch (_: ClosedFileSystemException) {
          // When the APK tab is closed, the APK file gets closed in another thread but the
          // UI still tries to render it (b/402589243).
        }

        if (!isDirectory) {
          if (fileName == SdkConstants.FN_ANDROID_MANIFEST_XML) {
            return StudioIcons.Shell.Filetree.MANIFEST_FILE
          } else if (fileName == "baseline.prof" || fileName == "baseline.profm") {
            // TODO: Use dedicated icon for this.
            return AllIcons.FileTypes.Hprof
          }

          val fileType = FileTypeRegistry.getInstance().getFileTypeByFileName(fileName)
          // Don't override icons for known file types. Just fall back if they are otherwise unknown.
          if (fileType === UnknownFileType.INSTANCE) {
            if (fileName.endsWith(SdkConstants.DOT_DEX)) {
              return AllIcons.FileTypes.JavaClass
            } else if (entry.elfAlignmentProblems != null) {
              return AllIcons.FileTypes.BinaryData
            } else if (fileName.endsWith(".pb")) {
              return AllIcons.FileTypes.BinaryData
            }
          }
          // Use the file type icon if available.
          val ftIcon = fileType.icon
          return ftIcon ?: AllIcons.FileTypes.Any_type
        } else {
          fileName = StringUtil.trimEnd(fileName, "/")
          if (fileName == SdkConstants.FD_RES) {
            return AllIcons.Modules.ResourcesRoot
          } else if (path.toString() == "/lib") {
            return AllIcons.Nodes.NativeLibrariesFolder
          }
          @Suppress("UnstableApiUsage")
          return IconManager.getInstance().getPlatformIcon(PlatformIcons.Package)
        }
      }
    }
  }

  private class SizeRenderer(private val myUseDownloadSize: Boolean) : ColoredTreeCellRenderer() {
    init {
      setTextAlign(SwingConstants.RIGHT)
    }

    override fun customizeCellRenderer(
      tree: JTree,
      value: Any?,
      selected: Boolean,
      expanded: Boolean,
      leaf: Boolean,
      row: Int,
      hasFocus: Boolean,
    ) {
      if (value !is ArchiveTreeNode) {
        return
      }

      val data = value.data
      val size = if (myUseDownloadSize) data.downloadFileSize else data.rawFileSize
      if (size > 0) {
        append(getHumanizedSize(size))
      }
    }
  }

  /** Render information about whether the .so file is aligned at a boundary (4 KB, 16 KB, etc) within the APK. */
  private class ZipAlignmentRenderer : ColoredTreeCellRenderer() {
    init {
      setTextAlign(SwingConstants.LEFT)
    }

    override fun customizeCellRenderer(
      tree: JTree,
      value: Any?,
      selected: Boolean,
      expanded: Boolean,
      leaf: Boolean,
      row: Int,
      hasFocus: Boolean,
    ) {
      if (value !is ArchiveTreeNode) {
        return
      }

      val data = value.data
      append(data.fileAlignment.text)
    }
  }

  private class CompressionRenderer : ColoredTreeCellRenderer() {
    init {
      setTextAlign(SwingConstants.LEFT)
    }

    override fun customizeCellRenderer(
      tree: JTree,
      value: Any?,
      selected: Boolean,
      expanded: Boolean,
      leaf: Boolean,
      row: Int,
      hasFocus: Boolean,
    ) {
      if (value !is ArchiveTreeNode) {
        return
      }

      val data = value.data
      try {
        if (!Files.isDirectory(data.path)) {
          // For page alignment, use "Yes" and "No" to give more horizontal space for the alignment message.
          append(if (data.isFileCompressed) "Yes" else "No")
        }
      } catch (_: ClosedFileSystemException) {
        // When the APK tab is closed, the APK file gets closed in another thread but the
        // UI still tries to render it (b/402589243).
      }
    }
  }

  private fun buildTree(): ColumnTree {
    val treeSpeedSearch =
      TreeSpeedSearch.installOn(
        tree,
        true,
        Function { path: TreePath ->
          val lastPathComponent = path.lastPathComponent
          if (lastPathComponent !is ArchiveTreeNode) {
            return@Function null
          }
          lastPathComponent.data.path.toString()
        },
      )

    // Provides the percentage of the node size to the total size of the APK
    val percentProvider = PercentProvider { tree: JTree, value: Any, _: Int ->
      if (value !is ArchiveTreeNode) {
        return@PercentProvider 0.0
      }
      val rootEntry = tree.model.root as ArchiveTreeNode
      return@PercentProvider when (value.data.downloadFileSize < 0) {
        true -> 0.0
        false -> value.data.downloadFileSize.toDouble() / rootEntry.data.downloadFileSize
      }
    }

    val downloadSizeComparator = compareBy<ArchiveNode> { it.data.downloadFileSize }
    val builder =
      ColumnTreeBuilder(tree)
        .addColumn(
          ColumnBuilder()
            .setName("File")
            .setPreferredWidth(JBUI.scale(270))
            .setHeaderAlignment(SwingConstants.LEADING)
            .setHeaderBorder(JBUI.Borders.empty(TEXT_RENDERER_VERT_PADDING, TEXT_RENDERER_HORIZ_PADDING))
            .setComparator(compareBy<ArchiveNode> { it.data.nodeDisplayString })
            .setRenderer(NameRenderer(apkParser, treeSpeedSearch))
        )
        .addColumn(
          ColumnBuilder()
            .setName("Size")
            .setPreferredWidth(JBUI.scale(80))
            .setHeaderAlignment(SwingConstants.TRAILING)
            .setHeaderBorder(JBUI.Borders.empty(TEXT_RENDERER_VERT_PADDING, TEXT_RENDERER_HORIZ_PADDING))
            .setComparator(compareBy<ArchiveNode> { it.data.rawFileSize })
            .setRenderer(SizeRenderer(false))
        )
        .addColumn(
          ColumnBuilder()
            .setName("Download Size")
            .setPreferredWidth(JBUI.scale(80))
            .setHeaderAlignment(SwingConstants.TRAILING)
            .setHeaderBorder(JBUI.Borders.empty(TEXT_RENDERER_VERT_PADDING, TEXT_RENDERER_HORIZ_PADDING))
            .setComparator(downloadSizeComparator)
            .setRenderer(SizeRenderer(true))
        )
        .addColumn(
          ColumnBuilder()
            .setName("% of Download Size")
            .setPreferredWidth(JBUI.scale(150))
            .setHeaderAlignment(SwingConstants.LEADING)
            .setHeaderBorder(JBUI.Borders.empty(TEXT_RENDERER_VERT_PADDING, TEXT_RENDERER_HORIZ_PADDING))
            .setComparator(downloadSizeComparator)
            .setRenderer(PercentRenderer(percentProvider))
        )
        .addColumn(
          ColumnBuilder()
            .setName("Compressed")
            .setPreferredWidth(JBUI.scale(110))
            .setHeaderAlignment(SwingConstants.LEADING)
            .setHeaderBorder(JBUI.Borders.empty(TEXT_RENDERER_VERT_PADDING, TEXT_RENDERER_HORIZ_PADDING))
            .setComparator(compareBy<ArchiveNode> { it.data.isFileCompressed })
            .setRenderer(CompressionRenderer())
        )

    if (isPageAlignFeatureEnabled) {
      builder.addColumn(
        ColumnBuilder()
          .setName("Alignment")
          .setPreferredWidth(JBUI.scale(320))
          .setHeaderAlignment(SwingConstants.LEADING)
          .setHeaderBorder(JBUI.Borders.empty(TEXT_RENDERER_VERT_PADDING, TEXT_RENDERER_HORIZ_PADDING))
          .setComparator(compareBy<ArchiveNode> { it.data.getAlignmentFinding(treeModel.extractNativeLibs).text })
          .setRenderer(AlignmentCellRenderer())
      )
    } else {
      builder.addColumn(
        ColumnBuilder()
          .setName("Alignment")
          .setPreferredWidth(JBUI.scale(200))
          .setHeaderAlignment(SwingConstants.LEADING)
          .setHeaderBorder(JBUI.Borders.empty(TEXT_RENDERER_VERT_PADDING, TEXT_RENDERER_HORIZ_PADDING))
          .setComparator(compareBy<ArchiveNode> { it.data.fileAlignment.text })
          .setRenderer(ZipAlignmentRenderer())
      )
    }

    builder.setTreeSorter { comparator: Comparator<Any>, _: SortOrder ->
      saveSettings()
      val root = treeModel.root as? ArchiveNode
      if (root != null) {
        val selected = tree.selectionPaths
        @Suppress("UNCHECKED_CAST") ArchiveTreeStructure.sort(root, comparator as Comparator<ArchiveNode>)
        treeModel.reload()
        if (selected != null) {
          tree.selectionPaths = selected
        }
      }
    }

    return builder.build()
  }

  private fun saveSettings() {
    val sortKey = columnTree.getSortKeys()?.firstOrNull() ?: return
    val sortColumn = columnTree.getColumns().find { it.modelIndex == sortKey.column }?.identifier?.toString() ?: return
    val settings = ApkAnalyzerSettings.getInstance()
    settings.sortColumn = sortColumn
    settings.sortOrder = sortKey.sortOrder
  }

  /**
   * Sorts the tree by applying a [SortKey]
   *
   * @param force If true, will clear the SortKey before applying it. This forces a sort if the sort key is unchanged but the data does.
   */
  private fun sort(force: Boolean = false) {
    val settings = ApkAnalyzerSettings.getInstance()
    val col = columnTree.getColumns().find { it.identifier == settings.sortColumn }?.modelIndex ?: return
    if (force) {
      columnTree.setSortKeys(null)
    }
    columnTree.setSortKeys(listOf(SortKey(col, settings.sortOrder)))
  }

  companion object {
    private val LOG = Logger.getInstance(ApkViewPanel::class.java)
    private const val TEXT_RENDERER_HORIZ_PADDING = 6
    private const val TEXT_RENDERER_VERT_PADDING = 4
  }
}
