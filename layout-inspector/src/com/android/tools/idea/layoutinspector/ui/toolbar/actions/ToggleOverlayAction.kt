/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.ui.toolbar.actions

import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.FlowLayout
import java.io.IOException
import javax.imageio.ImageIO
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSlider

private const val SLIDER_KEY = "SliderKey"
const val INITIAL_ALPHA_VALUE = 0.6f

/**
 * An action group containing an action to provide an overlay image and a slider to control the
 * image alpha. The slider is shown only when the image is present.
 */
class OverlayActionGroup(
  inspectorModel: InspectorModel,
  getImage: () -> ByteArray?,
  setImage: (image: ByteArray?) -> Unit,
  setAlpha: (alpha: Float) -> Unit,
) : DefaultActionGroup() {

  private val toggleButton = ToggleOverlayAction(inspectorModel, getImage, setImage)
  private val alphaSlider =
    AlphaSliderAction(setAlpha = setAlpha, isVisible = { getImage() != null })

  override fun getChildren(e: AnActionEvent?): Array<out AnAction> {
    return arrayOf(toggleButton, alphaSlider)
  }
}

/** Action that loads an image from the user's file system. */
private class ToggleOverlayAction(
  private val inspectorModel: InspectorModel,
  private val getImage: () -> ByteArray?,
  private val setImage: (ByteArray?) -> Unit,
) : AnAction(StudioIcons.LayoutInspector.Toolbar.LOAD_OVERLAY) {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)
    if (getImage() != null) {
      e.presentation.icon = StudioIcons.LayoutInspector.Toolbar.CLEAR_OVERLAY
      e.presentation.text = "Clear Overlay"
    } else {
      e.presentation.icon = StudioIcons.LayoutInspector.Toolbar.LOAD_OVERLAY
      e.presentation.text = "Load Overlay"
    }
    e.presentation.isEnabled = !inspectorModel.isEmpty
  }

  override fun actionPerformed(e: AnActionEvent) {
    if (getImage() != null) {
      setImage(null)
    } else {
      loadOverlay(e)
    }
  }

  private fun loadOverlay(e: AnActionEvent) {
    // choose image
    val descriptor =
      FileChooserDescriptorFactory.createSingleFileDescriptor()
        .withTitle("Choose Overlay")
        .withExtensionFilter("Image files", "svg", "png", "jpg")
    val fileChooserDialog =
      FileChooserFactory.getInstance().createFileChooser(descriptor, null, null)
    val toSelect =
      LocalFileSystem.getInstance().refreshAndFindFileByPath(e.project?.basePath ?: "/")
    val files = fileChooserDialog.choose(null, toSelect!!)
    if (files.isEmpty()) {
      return
    }
    assert(files.size == 1)

    setImage(loadImageFile(files[0]))
  }

  private fun loadImageFile(file: VirtualFile): ByteArray? {
    return try {
      // Read the image to make sure it's valid, if not show an error.
      ImageIO.read(file.inputStream)
      file.inputStream.readAllBytes()
    } catch (e: IOException) {
      Messages.showErrorDialog(
        "Failed to read image from \"" + file.name + "\" Error: " + e.message,
        "Error",
      )
      return null
    }
  }
}

/** Action that shows a slider to control the overlay image's transparency. */
private class AlphaSliderAction(
  private val setAlpha: (Float) -> Unit,
  private val isVisible: () -> Boolean,
) : AnAction(), CustomComponentAction {
  override fun actionPerformed(event: AnActionEvent) {
    val component =
      event.presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY) ?: return
    val slider = component.getClientProperty(SLIDER_KEY) as JSlider
    // The event for Custom components actions are constructed differently than normal actions.
    // If this action is shown in a popup toolbar (when there is not enough space to show the whole
    // toolbar in-place),
    // go through the action toolbar data context to find the model.
    setAlpha(slider.value / 100.0f)
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    val panel = JPanel(FlowLayout(FlowLayout.CENTER, JBUI.scale(5), 0))
    panel.add(JLabel("Overlay Alpha:"))
    val slider = JSlider(JSlider.HORIZONTAL, 0, 100, (INITIAL_ALPHA_VALUE * 100).toInt())
    slider.addChangeListener {
      val dataContext = DataManager.getInstance().getDataContext(slider)
      actionPerformed(
        AnActionEvent.createEvent(
          dataContext,
          presentation,
          ActionPlaces.TOOLBAR,
          ActionUiKind.TOOLBAR,
          null,
        )
      )
    }
    panel.add(slider)
    panel.putClientProperty(SLIDER_KEY, slider)
    return panel
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = isVisible()
  }
}
