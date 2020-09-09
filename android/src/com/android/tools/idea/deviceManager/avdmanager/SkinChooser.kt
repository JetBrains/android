/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.repository.io.FileOpUtils
import com.android.tools.idea.deviceManager.avdmanager.DeviceManagerConnection.Companion.defaultDeviceManagerConnection
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.ComboboxWithBrowseButton
import java.awt.ItemSelectable
import java.awt.event.ItemEvent
import java.awt.event.ItemListener
import java.io.File
import javax.swing.JComboBox
import javax.swing.JList

/**
 * Combobox that populates itself with the skins used by existing devices. Also allows adding a new skin by browsing.
 */
class SkinChooser(project: Project?,
                  private val resolveSystemImageSkins: Boolean) : ComboboxWithBrowseButton(), ItemListener, ItemSelectable {
  // TODO(qumeric): consider removal
  private val listeners: List<ItemListener> = listOf()

  private fun setItems(items: List<File?>) {
    comboBox.model = CollectionComboBoxModel(items)
  }

  private val skins: MutableList<File?>
    get() {
      val devices = defaultDeviceManagerConnection.devices
      val progress = StudioLoggerProgressIndicator(SkinChooser::class.java)
      val sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()
      return sequence {
        yield(AvdWizardUtils.NO_SKIN)
        val existingSkinFilesOnDevices = devices
          .mapNotNull { AvdWizardUtils.pathToUpdatedSkins(it.defaultHardware.skinFile, null, FileOpUtils.create()) }
          .filter { it.exists() }
        yieldAll(existingSkinFilesOnDevices)
        for (target in sdkHandler.getAndroidTargetManager(progress).getTargets(progress)) {
          yieldAll(target.skins.map { this@SkinChooser.resolve(it) })
        }
        for (img in sdkHandler.getSystemImageManager(progress).images) {
          yieldAll(img.skins.map { this@SkinChooser.resolve(it) })
        }
      }.toMutableList()
    }

  override fun itemStateChanged(e: ItemEvent) {
    val newEvent = ItemEvent(this, e.id, e.item, e.stateChange)
    for (listener in listeners) {
      listener.itemStateChanged(newEvent)
    }
  }

  override fun getSelectedObjects(): Array<Any> = comboBox.selectedObjects

  override fun addItemListener(l: ItemListener) {
    comboBox.addItemListener(l)
  }

  override fun removeItemListener(l: ItemListener) {
    comboBox.removeItemListener(l)
  }

  private fun resolve(skinFile: File?): File? = when {
    skinFile == null -> null
    resolveSystemImageSkins -> AvdWizardUtils.pathToUpdatedSkins(skinFile, null, FileOpUtils.create())
    else -> File(skinFile.name)
  }

  init {
    setItems(skins)
    comboBox.renderer = object : ColoredListCellRenderer<Any?>() {
      override fun customizeCellRenderer(
        list: JList<out Any?>,
        value: Any?,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean
      ) {
        val skinFile = (if (value == null) AvdWizardUtils.NO_SKIN else value as File)!!
        val skinPath = skinFile.path
        when {
          FileUtil.filesEqual(skinFile, AvdWizardUtils.NO_SKIN) -> {
            append("No Skin")
          }
          skinPath.contains("/sdk/platforms/") -> {
            append(skinPath.replace(".*/sdk/platforms/(.*)/skins/(.*)".toRegex(), "$2 ($1)"))
          }
          skinPath.contains("/sdk/system-images/") -> {
            append(skinPath.replace(".*/sdk/system-images/(.*)/(.*)/(.*)/skins/(.*)".toRegex(), "$4 ($1 $3)"))
          }
          else -> {
            append(skinFile.name)
          }
        }
      }
    }
    val skinChooserDescriptor = FileChooserDescriptor(false, true, false, false, false, false)
    addBrowseFolderListener(
      "Select Custom Skin",
      "Select the directory containing your custom skin definition",
      project,
      skinChooserDescriptor,
      object : TextComponentAccessor<JComboBox<*>> {
        override fun getText(component: JComboBox<*>): String = (component.selectedItem as File).path

        override fun setText(component: JComboBox<*>, text: String) {
          val items = skins
          val f = File(text)
          items.add(f)
          setItems(items)
          comboBox.selectedItem = f
        }
      })

    comboBox.addItemListener(this)
    setTextFieldPreferredWidth(20)
  }
}