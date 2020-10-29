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
package com.android.tools.idea.deviceManager.avdmanager

import com.android.annotations.concurrency.GuardedBy
import com.android.repository.api.ProgressIndicator
import com.android.repository.api.RepoManager
import com.android.repository.api.RepoManager.RepoLoadedListener
import com.android.repository.impl.meta.RepositoryPackages
import com.android.sdklib.SdkVersionInfo
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.sdklib.repository.targets.SystemImage
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.StudioDownloader
import com.android.tools.idea.sdk.StudioSettingsController
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.sdk.progress.StudioProgressRunner
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.AbstractTableCellEditor
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.font.TextAttribute
import java.awt.font.TextLayout
import java.util.Comparator
import java.util.EventObject
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer
import kotlin.math.ceil

/**
 * A table model for a [SystemImageList]
 */
class SystemImageListModel(
  private val project: Project?, private val indicator: StatusIndicator
) : ListTableModel<SystemImageDescription>() {
  private val sdkHandler: AndroidSdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()

  var isUpdating = false
    private set

  @GuardedBy("lock")
  private var completedCalls = 0
  private val lock = Any()
  fun refreshLocalImagesSynchronously() {
    try {
      indicator.onRefreshStart("Get local images...")
      items = localImages
    }
    finally {
      indicator.onRefreshDone("", true)
    }
  }

  override fun setItems(items: List<SystemImageDescription>) {
    isUpdating = true
    super.setItems(items)
    isUpdating = false
  }

  fun refreshImages(forceRefresh: Boolean) {
    synchronized(lock) { completedCalls = 0 }
    indicator.onRefreshStart("Refreshing...")
    val items = mutableListOf<SystemImageDescription>()
    val localComplete = RepoLoadedListener {
      invokeLater(ModalityState.any()) {
        // getLocalImages() doesn't use SdkPackages, so it's ok that we're not using what's passed in.
        items.addAll(localImages)
        // Update list in the UI immediately with the locally available system images
        setItems(items)
        // Assume the remote has not completed yet
        completedDownload("")
      }
    }
    val remoteComplete = RepoLoadedListener { packages: RepositoryPackages ->
      invokeLater(ModalityState.any()) {
        val remotes = getRemoteImages(packages)
        if (remotes != null) {
          items.addAll(remotes)
          setItems(items)
        }
        completedDownload("")
      }
    }
    val error = Runnable {
      invokeLater(ModalityState.any()) { completedDownload("Error loading remote images") }
    }
    val runner = StudioProgressRunner(false, false, "Loading Images", project)
    sdkHandler.getSdkManager(LOGGER).load(
      if (forceRefresh) 0 else RepoManager.DEFAULT_EXPIRATION_PERIOD_MS,
      listOf(localComplete), listOf(remoteComplete), listOf(error),
      runner, StudioDownloader(), StudioSettingsController.getInstance(), false
    )
  }

  // Report that one of the downloads were done.
  // If this is the first completed message continue to report "Refreshing..."
  private fun completedDownload(message: String) {
    synchronized(lock) {
      completedCalls++
      indicator.onRefreshDone(message, completedCalls < 2)
      if (completedCalls < 2) {
        indicator.onRefreshStart("Refreshing...")
      }
    }
  }

  private val localImages: List<SystemImageDescription>
    get() = sdkHandler.getSystemImageManager(LOGGER).images.map { SystemImageDescription(it) }

  /**
   * This class extends [ColumnInfo] in order to pull a string value from a given
   * [SystemImageDescription].
   * This is the column info used for most of our table, including the Name, Resolution, and API level columns.
   * It uses the text field renderer ([.getRenderer]) and allows for sorting by the lexicographical value
   * of the string displayed by the [JBLabel] rendered as the cell component. An explicit width may be used
   * by calling the overloaded constructor, otherwise the column will auto-scale to fill available space.
   */
  abstract inner class SystemImageColumnInfo(name: String, private val width: Int = -1) : ColumnInfo<SystemImageDescription, String>(name) {
    override fun isCellEditable(systemImageDescription: SystemImageDescription): Boolean = systemImageDescription.isRemote

    override fun getEditor(o: SystemImageDescription): TableCellEditor = SystemImageDescriptionRenderer(o)

    override fun getRenderer(o: SystemImageDescription): TableCellRenderer = SystemImageDescriptionRenderer(o)

    private inner class SystemImageDescriptionRenderer(
      private val image: SystemImageDescription
    ) : AbstractTableCellEditor(), TableCellRenderer {
      override fun getTableCellRendererComponent(
        table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
      ): Component {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
          if (isSelected) {
            if (image.isRemote) {
              background = UIUtil.getListSelectionBackground(false)
            }
            else {
              background = table.selectionBackground
            }
            foreground = table.selectionForeground
          }
          else {
            background = table.background
            foreground = table.foreground
          }
          isOpaque = true
        }

        val label = JBLabel((value as String)).apply {
          if (isSelected && !image.isRemote) {
            background = table.selectionBackground
            foreground = table.selectionForeground
          }
        }

        val labelFont = UIUtil.getLabelFont()
        if (column == 0) {
          label.font = labelFont.deriveFont(Font.BOLD)
        }
        if (image.isRemote) {
          label.font = labelFont.deriveFont(label.font.style or Font.ITALIC)
          label.foreground = UIUtil.getLabelDisabledForeground()
          // on OS X the actual text width isn't computed correctly. Compensating for that..
          if (label.text.isNotEmpty()) {
            val fontMetricsWidth = label.getFontMetrics(label.font).stringWidth(label.text)
            val l = TextLayout(label.text, label.font, label.getFontMetrics(label.font).fontRenderContext)
            val offset = ceil(l.bounds.width).toInt() - fontMetricsWidth
            if (offset > 0) {
              label.border = BorderFactory.createEmptyBorder(0, 0, 0, offset)
            }
          }
          panel.add(label)

          panel.addKeyListener(object : KeyAdapter() {
            override fun keyTyped(e: KeyEvent) {
              if (e.keyChar.toInt() == KeyEvent.VK_ENTER || e.keyChar.toInt() == KeyEvent.VK_SPACE) {
                downloadImage(image)
              }
            }
          })
        }
        if (column == 0) {
          val link = JBLabel("Download").apply {
            background = table.background
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            foreground = JBColor.BLUE
            if (isSelected) {
              this@apply.font = font.deriveFont(mapOf(TextAttribute.UNDERLINE to TextAttribute.UNDERLINE_ON))
            }
            addMouseListener(object : MouseAdapter() {
              override fun mouseClicked(e: MouseEvent) = downloadImage(image)
            })
          }
          panel.add(link)
        }
        return panel
      }

      override fun getTableCellEditorComponent(table: JTable, value: Any, isSelected: Boolean, row: Int, column: Int): Component =
        getTableCellRendererComponent(table, value, isSelected, false, row, column)

      override fun getCellEditorValue(): Any? = null

      override fun isCellEditable(e: EventObject?): Boolean = true
    }

    private fun downloadImage(image: SystemImageDescription) {
      val requestedPackages = listOf(image.remotePackage!!.path)
      val dialog = SdkQuickfixUtils.createDialogForPaths(project, requestedPackages)
      if (dialog != null) {
        dialog.show()
        refreshImages(true)
      }
    }

    override fun getComparator(): Comparator<SystemImageDescription> = object : Comparator<SystemImageDescription> {
      val comparator = ApiLevelComparator()
      override fun compare(o1: SystemImageDescription, o2: SystemImageDescription): Int {
        val res = comparator.compare(valueOf(o1)!!, valueOf(o2)!!)
        return res.takeUnless { res == 0 } ?: o1.tag.compareTo(o2.tag)
      }
    }

    override fun getWidth(table: JTable): Int = width
  }

  interface StatusIndicator {
    fun onRefreshStart(message: String)
    fun onRefreshDone(message: String, localOnly: Boolean)
  }

  companion object {
    private val LOGGER: ProgressIndicator = StudioLoggerProgressIndicator(SystemImageListModel::class.java)

    private fun getRemoteImages(packages: RepositoryPackages): List<SystemImageDescription>? {
      if (packages.newPkgs.isEmpty()) {
        return null
      }

      return packages.newPkgs
        .filter { SystemImageDescription.hasSystemImage(it) }
        .map { SystemImageDescription(it) }
    }

    @VisibleForTesting
    fun releaseDisplayName(systemImage: SystemImageDescription): String = with(systemImage.version) {
      val codeName = (if (isPreview) codename else SdkVersionInfo.getCodeName(apiLevel)) ?: "API $apiLevel"
      val maybeDeprecated = if (systemImage.obsolete() || apiLevel < SdkVersionInfo.LOWEST_ACTIVE_API) " (Deprecated)" else ""
      return codeName + maybeDeprecated
    }
  }

  init {
    /*
     * List of columns present in our table. Each column is represented by a ColumnInfo which tells the table how to get
     * the cell value in that column for a given row item.
     */
    columnInfos = arrayOf(
      object : SystemImageColumnInfo("Release Name") {
        override fun valueOf(systemImage: SystemImageDescription): String = releaseDisplayName(systemImage)
      },
      object : SystemImageColumnInfo("API Level", JBUI.scale(100)) {
        override fun valueOf(systemImage: SystemImageDescription): String = systemImage.version.apiString
      },
      object : SystemImageColumnInfo("ABI", JBUI.scale(100)) {
        override fun valueOf(systemImage: SystemImageDescription): String = systemImage.abiType
      },
      object : SystemImageColumnInfo("Target") {
        override fun valueOf(systemImage: SystemImageDescription): String {
          val tag = systemImage.tag
          val name = systemImage.name
          return String.format("%1\$s%2\$s", name, if (tag == SystemImage.DEFAULT_TAG) "" else " (${tag.display})")
        }
      })
    isSortable = true
  }
}