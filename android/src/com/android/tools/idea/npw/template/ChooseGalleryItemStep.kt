/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.npw.template

import com.android.tools.adtui.util.FormScalingUtil
import com.android.tools.adtui.validation.ValidatorPanel
import com.android.tools.idea.model.AndroidModuleInfo
import com.android.tools.idea.npw.FormFactor
import com.android.tools.idea.npw.model.NewModuleModel
import com.android.tools.idea.npw.model.RenderTemplateModel
import com.android.tools.idea.npw.platform.Language
import com.android.tools.idea.npw.project.getModuleTemplates
import com.android.tools.idea.npw.ui.ActivityGallery
import com.android.tools.idea.npw.ui.WizardGallery
import com.android.tools.idea.observable.ListenerManager
import com.android.tools.idea.observable.core.ObservableBool
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.android.tools.idea.templates.TemplateMetadata
import com.android.tools.idea.templates.TemplateMetadata.TemplateConstraint.ANDROIDX
import com.android.tools.idea.templates.TemplateMetadata.TemplateConstraint.KOTLIN
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.android.tools.idea.wizard.model.SkippableWizardStep
import com.android.tools.idea.wizard.template.Template
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.refactoring.isAndroidx
import org.jetbrains.android.util.AndroidBundle.message
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Icon
import javax.swing.JComponent

/**
 * This step allows the user to select which type of component (Activity, Service, etc.) they want to create.
 * TODO: This class and ChooseModuleTypeStep looks to have a lot in common.
 *
 * Should we have something more specific than a ASGallery, that renders "Gallery items"?
 */
abstract class ChooseGalleryItemStep(
  moduleModel: NewModuleModel,
  private val renderModel: RenderTemplateModel,
  formFactor: FormFactor,
  private val moduleTemplates: List<NamedModuleTemplate>,
  private val messageKeys: WizardGalleryItemsStepMessageKeys,
  private val emptyItemLabel: String
) : SkippableWizardStep<NewModuleModel>(moduleModel, message(messageKeys.addMessage, formFactor.id), formFactor.icon) {

  abstract val templateRenders: List<TemplateRenderer>
  private val itemGallery = WizardGallery(title, { t: TemplateRenderer? -> t!!.icon }, { t: TemplateRenderer? -> t!!.label })
  private val validatorPanel = ValidatorPanel(this, JBScrollPane(itemGallery)).also {
    FormScalingUtil.scaleComponentTree(this.javaClass, it)
  }

  private val invalidParameterMessage = StringValueProperty()
  private val listeners = ListenerManager()

  protected val isNewModule: Boolean
    get() = renderModel.module == null

  constructor(
    moduleModel: NewModuleModel,
    renderModel: RenderTemplateModel,
    formFactor: FormFactor,
    targetDirectory: VirtualFile,
    messageKeys: WizardGalleryItemsStepMessageKeys,
    emptyItemLabel: String
  ) : this(moduleModel, renderModel, formFactor,
           renderModel.androidFacet!!.getModuleTemplates(targetDirectory),
           messageKeys, emptyItemLabel)

  override fun getComponent(): JComponent = validatorPanel

  override fun getPreferredFocusComponent(): JComponent? = itemGallery

  public override fun createDependentSteps(): Collection<ModelWizardStep<*>> =
    arrayListOf(ConfigureTemplateParametersStep(renderModel, message(messageKeys.stepTitle), moduleTemplates))

  override fun dispose() = listeners.releaseAll()

  override fun onWizardStarting(wizard: ModelWizard.Facade) {
    validatorPanel.registerMessageSource(invalidParameterMessage)

    itemGallery.setDefaultAction(object : AbstractAction() {
      override fun actionPerformed(actionEvent: ActionEvent) {
        wizard.goForward()
      }
    })

    itemGallery.addListSelectionListener {
      itemGallery.selectedElement?.run {
        when (this) {
          is OldTemplateRenderer -> {
            renderModel.templateHandle = this.template
            renderModel.newTemplate = Template.NoActivity
          }
          is NewTemplateRenderer -> {
            renderModel.templateHandle = null
            renderModel.newTemplate = this.template
          }
        }
        wizard.updateNavigationProperties()
      }
      validateTemplate()
    }

    itemGallery.run {
      model = JBList.createDefaultListModel(templateRenders)
      selectedIndex = getDefaultSelectedTemplateIndex(templateRenders, emptyItemLabel)
    }
  }

  override fun canGoForward(): ObservableBool = validatorPanel.hasErrors().not()

  override fun onEntering() = validateTemplate()

  /**
   * See also [com.android.tools.idea.actions.NewAndroidComponentAction.update]
   */
  private fun validateTemplate() {
    val template = renderModel.templateHandle
    val templateData = template?.metadata
    val androidSdkInfo = model.androidSdkInfo.valueOrNull
    val facet = renderModel.androidFacet

    fun AndroidFacet.getModuleInfo() = AndroidModuleInfo.getInstance(this)

    val moduleApiLevel = androidSdkInfo?.minApiLevel ?: facet?.getModuleInfo()?.minSdkVersion?.featureLevel ?: Integer.MAX_VALUE
    val moduleBuildApiLevel = androidSdkInfo?.buildApiLevel ?: facet?.getModuleInfo()?.buildSdkVersion?.featureLevel ?: Integer.MAX_VALUE

    val project = model.project.valueOrNull
    val isAndroidxProject = project != null && project.isAndroidx()

    invalidParameterMessage.set(
      if (renderModel.newTemplate != Template.NoActivity)
        // TODO(qumeric): pass language?
        renderModel.newTemplate.validate(moduleApiLevel, moduleBuildApiLevel, isNewModule, isAndroidxProject, messageKeys)
      else
        validateTemplate(templateData, moduleApiLevel, moduleBuildApiLevel, isNewModule, isAndroidxProject, model.language.value, messageKeys)
    )
  }

  interface TemplateRenderer {
    val label: String
    /** Return the image associated with the current template, if it specifies one, or null otherwise. */
    val icon: Icon?
    val exists: Boolean
  }

  open class OldTemplateRenderer(internal val template: TemplateHandle?) : TemplateRenderer {
    override val label: String
      get() = ActivityGallery.getTemplateImageLabel(template, false)

    override val icon: Icon?
      get() = ActivityGallery.getTemplateIcon(template, false)

    override val exists: Boolean = template != null

    override fun toString(): String = label
  }

  class NewTemplateRenderer(internal val template: Template) : TemplateRenderer {
    override val label: String
      get() = ActivityGallery.getTemplateImageLabel(template, false)

    override val icon: Icon?
      get() = ActivityGallery.getTemplateIcon(template, false)

    override val exists: Boolean = template != Template.NoActivity

    override fun toString(): String = label
  }
}

fun getDefaultSelectedTemplateIndex(
  templateRenderers: List<ChooseGalleryItemStep.TemplateRenderer>,
  emptyItemLabel: String = "Empty Activity"
): Int = templateRenderers.indices.run {
  val defaultTemplateIndex = firstOrNull { templateRenderers[it].label == emptyItemLabel }
  val firstValidTemplateIndex = firstOrNull { templateRenderers[it].exists }

  defaultTemplateIndex ?: firstValidTemplateIndex ?: throw IllegalArgumentException("No valid Template found")
}

@VisibleForTesting
fun validateTemplate(template: TemplateMetadata?,
                     moduleApiLevel: Int,
                     moduleBuildApiLevel: Int,
                     isNewModule: Boolean,
                     isAndroidxProject: Boolean,
                     language: Language,
                     messageKeys: WizardGalleryItemsStepMessageKeys): String =
  if (template == null) {
    if (isNewModule) "" else message(messageKeys.itemNotFound)
  }
  else if (moduleApiLevel < template.minSdk) {
    message(messageKeys.invalidMinSdk, template.minSdk)
  }
  else if (moduleBuildApiLevel < template.minBuildApi) {
    message(messageKeys.invalidMinBuild, template.minBuildApi)
  }
  else if (template.constraints.contains(ANDROIDX) && !isAndroidxProject) {
    message(messageKeys.invalidAndroidX)
  }
  else if (template.constraints.contains(KOTLIN) && language != Language.KOTLIN) {
    message(messageKeys.invalidNeedsKotlin)
  }
  else ""

@VisibleForTesting
fun Template.validate(moduleApiLevel: Int,
                      moduleBuildApiLevel: Int,
                      isNewModule: Boolean,
                      isAndroidxProject: Boolean,
                      messageKeys: WizardGalleryItemsStepMessageKeys
): String = when {
  this == Template.NoActivity -> if (isNewModule) "" else message(messageKeys.itemNotFound)
  moduleApiLevel < this.minSdk -> message(messageKeys.invalidMinSdk, this.minSdk)
  moduleBuildApiLevel < this.minCompileSdk -> message(messageKeys.invalidMinBuild, this.minCompileSdk)
  this.requireAndroidX && !isAndroidxProject -> message(messageKeys.invalidAndroidX)
  else -> ""
}

data class WizardGalleryItemsStepMessageKeys(
  val addMessage: String,
  val stepTitle: String,
  val itemNotFound: String,
  val invalidMinSdk: String,
  val invalidMinBuild: String,
  val invalidAndroidX: String,
  val invalidNeedsKotlin: String
)
