/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.editor

import com.android.SdkConstants
import com.android.ide.common.rendering.api.ViewInfo
import com.android.resources.ResourceUrl
import com.android.tools.adtui.model.stdui.CommonComboBoxModel
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.adtui.stdui.CommonComboBox
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.uibuilder.type.TEMP_ANIMATED_SELECTOR_FOLDER
import com.android.tools.idea.util.toIoFile
import com.android.tools.idea.util.toVirtualFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileEvent
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.ui.JBUI
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.core.util.toVirtualFile
import java.io.File
import java.util.UUID
import java.util.function.Consumer
import javax.swing.DefaultComboBoxModel

/**
 * Control that provides controls for animations (play, pause, stop and frame-by-frame steps).
 */
class AnimatedSelectorToolbar
/**
 * Constructs a new AnimatedSelectorToolbar for animated selector file.
 *
 * @param parentDisposable Parent [Disposable]
 * @param listener         [AnimationListener] that will be called in every tick
 * @param tickStepMs       Number of milliseconds to advance in every animator tick
 * @param minTimeMs        Start milliseconds for the animation
 * @param initialMaxTimeMs Maximum number of milliseconds for the animation or -1 if there is no time limit
 */
private constructor(
  parentDisposable: Disposable,
  private val animatedSelectorModel: AnimatedSelectorModel,
  listener: AnimationListener, tickStepMs: Long,
  minTimeMs: Long, initialMaxTimeMs: Long
) : AnimationToolbar(parentDisposable, listener, tickStepMs, minTimeMs, initialMaxTimeMs,
                     AnimationToolbarType.ANIMATED_SELECTOR), Disposable {

  private var comboBox: ComboBox<String>? = null

  init {
    val previewOptions = animatedSelectorModel.getPreviewOption()
    // If there is no transitions, we don't offer dropdown menu for preview transitions.
    if (previewOptions.size > 1) {
      val boxModel = AnimationOptionComboBoxModel(previewOptions)
      val box = CommonComboBox(boxModel)
      comboBox = box
      box.size = JBUI.size(100, 22)
      controlBar.add(box, 0)

      box.item = ID_ANIMATED_SELECTOR_MODEL

      box.addActionListener {
        // Stop the animation (if playing) when switching the preview option.
        myAnalyticsManager.trackAction(toolbarType, AnimationToolbarAction.SELECT_ANIMATION)
        stop()
        val transitionId = box.item
        animatedSelectorModel.setPreviewOption(transitionId)
        // Update visibility of slider bar
        setTimeSliderVisibility(SdkConstants.TAG_ANIMATION_LIST == animatedSelectorModel.getPreviewOptionTagName(transitionId))
        onTransitionChanged(transitionId != ID_ANIMATED_SELECTOR_MODEL)
      }
    }
    // The default select item should be ID_ANIMATED_SELECTOR_MODEL, which is not an animation
    setTimeSliderVisibility(false)
    onTransitionChanged(false)
  }

  /**
   * Set up the ability and visibility of control buttons.
   * When [playable] is true, it means there is an animation to play, we enable backFrame, stop, play, and forwardFrame buttons and setup
   * the property tooltips.
   *
   * When [playable] is false, it means there is no animation to play. We still keep them visible but disable them, and setup the tooltips
   * to notify user there is no animation.
   */
  private fun onTransitionChanged(playable: Boolean) {
    if (playable) {
      setEnabledState(play = true, pause = false, stop = false, frame = true, speed = true)
      setVisibilityOfPlayAndPauseButtons(playing = false)
      setTooltips(DEFAULT_PLAY_TOOLTIP, DEFAULT_PAUSE_TOOLTIP, DEFAULT_STOP_TOOLTIP)
    }
    else {
      setEnabledState(play = false, pause = false, stop = false, frame = false, speed = false)
      setVisibilityOfPlayAndPauseButtons(playing = false)
      setTooltips(NO_ANIMATION_TOOLTIP, NO_ANIMATION_TOOLTIP, NO_ANIMATION_TOOLTIP)
    }
  }

  private fun setTimeSliderVisibility(visibility: Boolean) {
    myTimeSlider?.isVisible = visibility
    timeSliderSeparator?.isVisible = visibility
    if (!visibility) {
      // Set maxtimeMs to -1 to indicate it is infinity animation. The slider is invisible whe animation is infinitely.
      setMaxTimeMs(-1)
    }
  }

  /**
   * Do not select any transition. This should reset the box item to [ID_ANIMATED_SELECTOR_MODEL].
   */
  fun setNoTransition() {
    comboBox?.selectedIndex = 0
  }

  /**
   * Return if the any transition is selected. This means the selected item is [ID_ANIMATED_SELECTOR_MODEL].
   */
  fun isTransitionSelected(): Boolean {
    return (comboBox?.selectedIndex ?: -1) > 0
  }

  companion object {
    /**
     * Constructs a new toolbar for animated selector file.
     */
    fun createToolbar(
      parentDisposable: Disposable,
      animatedSelectorModel: AnimatedSelectorModel,
      listener: AnimationListener,
      tickStepMs: Long,
      minTimeMs: Long
    ): AnimatedSelectorToolbar {
      return AnimatedSelectorToolbar(parentDisposable, animatedSelectorModel, listener, tickStepMs, minTimeMs, 0)
    }
  }
}

/**
 * The box model for selecting the animation to preview.
 */
private class AnimationOptionComboBoxModel(ids: Set<String>) : DefaultComboBoxModel<String>(), CommonComboBoxModel<String> {

  init {
    ids.forEach { addElement(it) }
  }

  override var value = selectedItem as String

  override var text = selectedItem as String

  override var editable = false
    private set

  override fun addListener(listener: ValueChangedListener) = Unit

  override fun removeListener(listener: ValueChangedListener) = Unit
}

private object EmptyModelUpdater : NlModel.NlModelUpdaterInterface {
  // Do nothing because the file content will be re-wrote frequently.
  override fun updateFromTagSnapshot(model: NlModel, newRoot: XmlTag?, roots: MutableList<NlModel.TagSnapshotTreeNode>) = Unit
  override fun updateFromViewInfo(model: NlModel, viewInfos: MutableList<ViewInfo>) = Unit
}

private const val ID_ANIMATED_SELECTOR_MODEL = "Select Transition..."
private const val XMLNS_ANDROID_NAMESPACE = "${SdkConstants.XMLNS_ANDROID}=\"${SdkConstants.ANDROID_URI}\""

/**
 * For creating and saving the data of animated selector file. It creates a temp file and replacing the content of animated selector file
 * when switching between transitions.
 */
class AnimatedSelectorModel(originalFile: VirtualFile,
                            parentDisposable: Disposable,
                            project: Project,
                            facet: AndroidFacet,
                            componentRegistrar: Consumer<NlComponent>,
                            config: Configuration) {

  private var animationTags: Map<String, XmlTag>
  private val tempModelFile: VirtualFile
  private val nlModelOfTempFile: NlModel
  private var currentOption: String? = null

  init {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    val xmlFile = originalFile.toPsiFile(project) as XmlFile
    tempModelFile = createTempAnimatedSelectorFile()
    nlModelOfTempFile = createModelWithFile(parentDisposable, project, facet, componentRegistrar, config, tempModelFile)

    animationTags = createIdAnimationMap(xmlFile)

    // Update (id, XmlTag) maps when the animated-selector file is edited.
    VirtualFileManager.getInstance().addVirtualFileListener(object : VirtualFileListener {
      override fun contentsChanged(event: VirtualFileEvent) {
        if (event.file == originalFile) {
          animationTags = createIdAnimationMap(xmlFile)
          setPreviewOption(currentOption ?: ID_ANIMATED_SELECTOR_MODEL)
        }
      }
    })
    setPreviewOption(ID_ANIMATED_SELECTOR_MODEL)
  }

  /**
   * Create the (id, XmlTag) pairs as an index map. It is used to find the target XmlTag by animation id.
   */
  private fun createIdAnimationMap(xmlFile: XmlFile): Map<String, XmlTag> {
    val rootTag = xmlFile.rootTag!!
    val transitions = rootTag.subTags.asSequence().filter { it.name == SdkConstants.TAG_TRANSITION }.toList()

    val maps = mutableMapOf(ID_ANIMATED_SELECTOR_MODEL to rootTag)
    for (transition in transitions) {
      val fromIdAttribute = transition.getAttribute(SdkConstants.ATTR_FROM_ID, SdkConstants.ANDROID_URI)?.value ?: continue
      val toIdAttribute = transition.getAttribute(SdkConstants.ATTR_TO_ID, SdkConstants.ANDROID_URI)?.value ?: continue
      val fromId = ResourceUrl.parse(fromIdAttribute)?.name ?: continue
      val toId = ResourceUrl.parse(toIdAttribute)?.name ?: continue
      val animationTag = transition.subTags.firstOrNull {
        it.name == SdkConstants.TAG_ANIMATED_VECTOR || it.name == SdkConstants.TAG_ANIMATION_LIST
      } ?: continue
      val transitionId = "$fromId to $toId"
      maps[transitionId] = animationTag
    }
    return maps
  }

  private fun createModelWithFile(parentDisposable: Disposable,
                          project: Project,
                          facet: AndroidFacet,
                          componentRegistrar: Consumer<NlComponent>,
                          config: Configuration,
                          file: VirtualFile): NlModel {
    val psiXmlFile = file.toPsiFile(project) as XmlFile
    psiXmlFile.putUserData(ModuleUtilCore.KEY_MODULE, facet.module)

    return NlModel.builder(facet, file, config)
      .withParentDisposable(parentDisposable)
      .withComponentRegistrar(componentRegistrar)
      .withModelUpdater(EmptyModelUpdater)
      .withXmlProvider { _, _ -> psiXmlFile }
      .build()
  }

  private fun createTempAnimatedSelectorFile(): VirtualFile {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    val fileName = "drawable_" + UUID.randomUUID().toString().replace("-", "_")
    val systemTempDir = File(FileUtilRt.getTempDirectory()).toVirtualFile()!!
    val tempDrawableDir = systemTempDir.findChild(TEMP_ANIMATED_SELECTOR_FOLDER)
                          ?: systemTempDir.createChildDirectory(this, TEMP_ANIMATED_SELECTOR_FOLDER)
    val physicalChildInTempDrawableFile = FileUtilRt.createTempFile(tempDrawableDir.toIoFile(), fileName, ".xml", true, true)
    return physicalChildInTempDrawableFile.toVirtualFile(true)!!
  }

  private fun getTransitionContent(embeddedAnimationTag: XmlTag): String {
    val originalText = embeddedAnimationTag.text
    val tag = embeddedAnimationTag.name
    // We appending android namespace string into the embedded <animated-vector> tag.
    return "${originalText.substringBefore(tag)}$tag $XMLNS_ANDROID_NAMESPACE ${originalText.substringAfter(tag)}"
  }

  /**
   * Get the [NlModel] for animated selector file. Which should be created by a temp file.
   */
  fun getNlModel() = nlModelOfTempFile

  /**
   * Set the content of given the [option], this will change the content of temp file to the given option.
   */
  fun setPreviewOption(option: String) {
    if (currentOption == option) {
      return
    }
    currentOption = option
    val tag = animationTags[option] ?: return
    WriteCommandAction.runWriteCommandAction(nlModelOfTempFile.project) {
      tempModelFile.getOutputStream(this).writer().use { it.write(getTransitionContent(tag)) }
    }
  }

  fun getPreviewOptionTagName(option: String): String? {
    return animationTags[option]?.name
  }

  /**
   * Get the id of preview options. It must have [ID_ANIMATED_SELECTOR_MODEL] in it.
   */
  fun getPreviewOption(): Set<String> = animationTags.keys
}
