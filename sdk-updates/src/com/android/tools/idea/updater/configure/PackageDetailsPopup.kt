/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.updater.configure

import com.android.repository.api.UpdatablePackage
import com.android.sdklib.repository.meta.DetailsTypes
import com.android.tools.idea.flags.StudioFlags
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.SelectionProvider
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JTable
import javax.swing.JTextArea
import javax.swing.KeyStroke

object PackageDetailsPopup {
  @JvmStatic
  fun addPackageDetailsPopup(table: JTable) {
    if (!StudioFlags.SDK_MANAGER_SHOW_PACKAGE_DETAILS.get()) {
      return
    }
    table.addMouseListener(
      object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent) {
          showPopup(e)
        }

        override fun mouseReleased(e: MouseEvent) {
          showPopup(e)
        }

        private fun showPopup(e: MouseEvent) {
          if (e.isPopupTrigger) {
            val row = table.rowAtPoint(e.point)
            if (row >= 0 && !table.isRowSelected(row)) {
              table.selectionModel.setSelectionInterval(row, row)
            }
            val selectionProvider = table as? SelectionProvider ?: return
            @Suppress("UNCHECKED_CAST")
            val selection = selectionProvider.selection as? Iterable<SdkUpdaterConfigPanel.MultiStateRow> ?: return
            val packages = mutableListOf<UpdatablePackage>()
            for (r in selection) {
              if (r is DetailsTreeNode) {
                packages.add(r.item)
              }
            }
            if (packages.isNotEmpty()) {
              val popupMenu = JPopupMenu()
              val detailsItem = JMenuItem("Show Package Details")
              detailsItem.addActionListener { showPackageDetails(table, packages) }
              popupMenu.add(detailsItem)
              popupMenu.show(e.component, e.x, e.y)
            }
          }
        }
      }
    )

    table.registerKeyboardAction(
      { showPackageDetails(table, null) },
      KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK),
      JComponent.WHEN_FOCUSED,
    )
  }

  private fun showPackageDetails(table: JTable, explicitPackages: List<UpdatablePackage>?) {
    val packages =
      explicitPackages
        ?: buildList {
          val selectionProvider = table as? SelectionProvider ?: return
          @Suppress("UNCHECKED_CAST")
          val selection = selectionProvider.selection as? Iterable<SdkUpdaterConfigPanel.MultiStateRow> ?: return
          for (row in selection) {
            if (row is DetailsTreeNode) {
              add(row.item)
            }
          }
        }
    if (packages.isEmpty()) {
      return
    }

    val text = packages.joinToString("\n\n----------------------------------------------------------------\n\n") { getPackageDetails(it) }

    PackageDetailsDialog(table, "Package Details", text).show()
  }

  @VisibleForTesting
  fun getPackageDetails(pkg: UpdatablePackage): String {
    val sb = StringBuilder()
    val rep = pkg.representative
    sb.append("Display name: ${rep.displayName}\n")
    sb.append("Path: ${rep.path}\n")
    sb.append("Version: ${rep.version}\n")
    if (rep.obsolete()) {
      sb.append("Obsolete: true\n")
    }
    rep.license?.let { sb.append("License: ${it.id}\n") }
    pkg.local?.let { sb.append("Local location: ${it.location}\n") }
    pkg.remote?.let { remote ->
      remote.source?.let { sb.append("Remote source: ${it.url}\n") }
      val channel = remote.channel
      val channelName =
        when (channel.id) {
          "channel-0" -> "(Stable)"
          "channel-1" -> "(Beta)"
          "channel-2" -> "(Dev)"
          "channel-3" -> "(Canary)"
          else -> ""
        }

      sb.append("Remote channel: ${channel.id} $channelName\n")
      remote.archive?.complete?.let { complete ->
        sb.append("Archive size: ${complete.size} bytes\n")
        sb.append("Archive URL: ${complete.url}\n")
      }
    }
    val details = rep.typeDetails
    if (details is DetailsTypes.ApiDetailsType) {
      with(details) {
        sb.append("API: ${androidVersion.apiStringWithExtension}\n")
        sb.append("Major version: $apiLevel\n")
        sb.append("Minor version: $apiMinorLevel\n")
        codename?.let { sb.append("Codename: $it\n") }
        extensionLevel?.let { sb.append("Extension level: $it\n") }
        sb.append("Base Extension: $isBaseExtension\n")
        betaNumber?.takeIf { it != 0 }?.let { sb.append("Beta: $betaNumber\n") }
        canaryNumber?.takeIf { it != 0 }?.let { sb.append("Canary: $canaryNumber\n") }
        try {
          sb.append("ABIs: ${abis.joinToString(", ")}\n")
        } catch (_: Exception) {
          // Ignore
        }
      }
      if (details is DetailsTypes.SysImgDetailsType) {
        val tags = details.tags
        if (tags.isNotEmpty()) {
          sb.append("Tags:\n${tags.joinToString("") { "  - ${it.display} [${it.id}]\n" }}\n")
        }
      }
      if (details is DetailsTypes.AddonDetailsType) {
        val tag = details.tag
        sb.append("Tag: ${tag.display} [${tag.id}]\n")
      }
    }
    val deps = rep.allDependencies
    if (deps.isNotEmpty()) {
      sb.append("Dependencies:\n")
      for (dep in deps) {
        val minRev = dep.minRevision?.let { " (${it.toRevision()})" } ?: ""
        sb.append("  - ${dep.path}$minRev\n")
      }
    }
    return sb.toString()
  }

  private class PackageDetailsDialog(parent: Component, title: String, private val text: String) : DialogWrapper(parent, true) {
    init {
      setTitle(title)
      isModal = false
      init()
    }

    override fun createCenterPanel(): JComponent {
      val textArea =
        JTextArea(text).apply {
          isEditable = false
          font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        }
      return JBScrollPane(textArea).apply { preferredSize = Dimension(800, 500) }
    }

    override fun createActions(): Array<Action> = arrayOf(okAction)
  }
}
