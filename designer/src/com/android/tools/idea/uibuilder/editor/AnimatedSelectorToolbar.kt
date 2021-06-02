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
import com.android.resources.ResourceUrl
import com.android.tools.adtui.model.stdui.CommonComboBoxModel
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.adtui.stdui.CommonComboBox
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.uibuilder.type.TEMP_ANIMATED_SELECTOR_FOLDER
import com.android.tools.idea.util.toIoFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.source.xml.XmlTagImpl
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.ui.JBUI
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.core.util.toVirtualFile
import java.io.File
import java.util.function.Consumer
import javax.swing.DefaultComboBoxModel

private const val NO_ANIMATION_TOOLTIP = "There is no animation to play"

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

  init {
    val previewOptions = animatedSelectorModel.getPreviewOption()
    // If there is no transitions, we don't offer dropdown menu for preview transitions.
    if (previewOptions.size > 1) {
      val boxModel = AnimationOptionComboBoxModel(previewOptions)
      val box = CommonComboBox(boxModel)
      box.size = JBUI.size(100, 22)
      controlBar.add(box)

      box.item = ID_ANIMATED_SELECTOR_MODEL

      box.addActionListener {
        // Stop the animation (if playing) when switching the preview option.
        myAnalyticsManager.trackAction(myToolbarType, AnimationToolbarAction.SELECT_ANIMATION)
        stop()
        val transitionId = box.item
        animatedSelectorModel.setPreviewOption(transitionId)
        updateControlBar(transitionId != ID_ANIMATED_SELECTOR_MODEL)
      }
    }
    // The default select item should be ID_ANIMATED_SELECTOR_MODEL, which is not an animation
    updateControlBar(false)
  }

  /**
   * Set up the ability and visibility of control buttons.
   * When [canPlay] is true, it means there is an animation to play, we enable backFrame, stop, play, and forwardFrame buttons and setup
   * the property tooltips.
   *
   * When [canPlay] is false, it means there is no animation to play. We still keep them visible but disable them, and setup the tooltips
   * to notify user there is no animation.
   */
  private fun updateControlBar(canPlay: Boolean) {
    if (canPlay) {
      setEnabledState(/*play*/ true, /*pause*/ false, /*stop*/ true, /*frame*/ true)
      setVisibilityState(/*play*/ true, /*pause*/ false, /*stop*/ true, /*frame*/ true)
      setTooltips(DEFAULT_PLAY_TOOLTIP,
                  DEFAULT_PAUSE_TOOLTIP,
                  DEFAULT_STOP_TOOLTIP,
                  DEFAULT_FRAME_FORWARD_TOOLTIP,
                  DEFAULT_FRAME_BACK_TOOLTIP)
    }
    else {
      setEnabledState(/*play*/ false, /*pause*/ false, /*stop*/ false, /*frame*/ false)
      setVisibilityState(/*play*/ true, /*pause*/ false, /*stop*/ true, /*frame*/ true)
      setTooltips(NO_ANIMATION_TOOLTIP, NO_ANIMATION_TOOLTIP, NO_ANIMATION_TOOLTIP, NO_ANIMATION_TOOLTIP, NO_ANIMATION_TOOLTIP)
    }
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
      return AnimatedSelectorToolbar(parentDisposable, animatedSelectorModel, listener, tickStepMs, minTimeMs, -1)
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
  override fun update(model: NlModel, newRoot: XmlTag?, roots: MutableList<NlModel.TagSnapshotTreeNode>) = Unit
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

  private val idContentMap: Map<String, String>
  private val tempModelFile: VirtualFile
  private val nlModelOfTempFile: NlModel

  init {
    ApplicationManager.getApplication().assertWriteAccessAllowed()

    val xmlFile = originalFile.toPsiFile(project) as XmlFile

    val transitions = xmlFile.rootTag!!.subTags
      .asSequence()
      .filter { it.name == SdkConstants.TAG_TRANSITION }
      .toList()

    val maps = mutableMapOf<String, String>()
    maps[ID_ANIMATED_SELECTOR_MODEL] = (xmlFile.rootTag!! as XmlTagImpl).chars.toString()

    tempModelFile = createTempAnimatedSelectorFile(xmlFile.name)
    nlModelOfTempFile = createModelWithFile(parentDisposable, project, facet, componentRegistrar, config, tempModelFile)

    for (transition in transitions) {
      val fromIdAttribute = transition.getAttribute("android:fromId")?.value ?: continue
      val toIdAttribute = transition.getAttribute("android:toId")?.value ?: continue
      val fromId = ResourceUrl.parse(fromIdAttribute)?.name ?: continue
      val toId = ResourceUrl.parse(toIdAttribute)?.name ?: continue
      val animatedVectorTag = transition.subTags.firstOrNull { it.name == SdkConstants.TAG_ANIMATED_VECTOR } ?: continue
      val transitionId = "$fromId to $toId"
      maps[transitionId] = getTransitionContent(animatedVectorTag)
    }
    idContentMap = maps

    setPreviewOption(ID_ANIMATED_SELECTOR_MODEL)
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

  private fun createTempAnimatedSelectorFile(originalFileName: String): VirtualFile {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    val nameWithoutSuffix = originalFileName.substringBefore(".")
    val systemTempDir = File(FileUtilRt.getTempDirectory()).toVirtualFile()!!
    val tempDrawableDir = systemTempDir.findChild(TEMP_ANIMATED_SELECTOR_FOLDER)
                          ?: systemTempDir.createChildDirectory(this, TEMP_ANIMATED_SELECTOR_FOLDER)
    val physicalChildInTempDrawableFile = FileUtilRt.createTempFile(tempDrawableDir.toIoFile(), "fake_of_$nameWithoutSuffix", ".xml", true, true)
    return physicalChildInTempDrawableFile.toVirtualFile()!!
  }

  private fun getTransitionContent(animatedVectorTag: XmlTag): String {
    val originalText = (animatedVectorTag as XmlTagImpl).chars.toString()
    val tag = SdkConstants.TAG_ANIMATED_VECTOR
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
    val content = idContentMap[option] ?: return
    WriteCommandAction.runWriteCommandAction(nlModelOfTempFile.project) {
      tempModelFile.getOutputStream(this).writer().use { it.write(content) }
    }
  }

  /**
   * Get the id of preview options. It must have [ID_ANIMATED_SELECTOR_MODEL] in it.
   */
  fun getPreviewOption(): Set<String> = idContentMap.keys
}
