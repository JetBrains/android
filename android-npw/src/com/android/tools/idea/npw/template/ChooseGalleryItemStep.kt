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
import com.android.tools.adtui.device.FormFactor
import com.android.tools.idea.model.StudioAndroidModuleInfo
import com.android.tools.idea.npw.COMPOSE_MIN_AGP_VERSION
import com.android.tools.idea.npw.hasComposeMinAgpVersion
import com.android.tools.idea.npw.model.RenderTemplateModel
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.npw.project.getModuleTemplates
import com.android.tools.idea.npw.ui.WizardGallery
import com.android.tools.idea.npw.ui.getTemplateIcon
import com.android.tools.idea.npw.ui.getTemplateTitle
import com.android.tools.idea.observable.ListenerManager
import com.android.tools.idea.observable.core.ObservableBool
import com.android.tools.idea.observable.core.OptionalProperty
import com.android.tools.idea.observable.core.OptionalValueProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.android.tools.idea.wizard.model.SkippableWizardStep
import com.android.tools.idea.wizard.model.WizardModel
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.Template
import com.android.tools.idea.wizard.template.TemplateConstraint
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

class BlankModel: WizardModel() {
  override fun handleFinished() {
    // Nothing to do
  }
}
abstract class ChooseGalleryItemStep(
  private val renderModel: RenderTemplateModel,
  formFactor: FormFactor,
  private val moduleTemplates: List<NamedModuleTemplate>,
  private val messageKeys: WizardGalleryItemsStepMessageKeys,
  private val emptyItemLabel: String,
  private val androidSdkInfo: OptionalProperty<AndroidVersionsInfo.VersionItem> = OptionalValueProperty.absent()
) : SkippableWizardStep<WizardModel>(BlankModel(), message(messageKeys.addMessage, formFactor.id), formFactor.icon) {

  abstract val templateRenderers: List<TemplateRenderer>
  private val itemGallery = WizardGallery(title, { t: NewTemplateRenderer? -> t!!.icon }, { t: NewTemplateRenderer? -> t!!.label })
  private val validatorPanel = ValidatorPanel(this, JBScrollPane(itemGallery)).also {
    FormScalingUtil.scaleComponentTree(this.javaClass, it)
  }

  private val invalidParameterMessage = StringValueProperty()
  private val listeners = ListenerManager()

  protected val isNewModule: Boolean
    get() = renderModel.module == null

  constructor(
    renderModel: RenderTemplateModel,
    formFactor: FormFactor,
    targetDirectory: VirtualFile,
    messageKeys: WizardGalleryItemsStepMessageKeys,
    emptyItemLabel: String
  ) : this(renderModel, formFactor, renderModel.androidFacet!!.getModuleTemplates(targetDirectory), messageKeys, emptyItemLabel)

  override fun getComponent(): JComponent = validatorPanel

  override fun getPreferredFocusComponent(): JComponent? = itemGallery

  public override fun createDependentSteps(): Collection<ModelWizardStep<*>> =
    listOf(ConfigureTemplateParametersStep(renderModel, message(messageKeys.stepTitle), moduleTemplates))

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
        renderModel.newTemplate = this.template
        wizard.updateNavigationProperties()
      }
      validateTemplate()
    }

    itemGallery.run {
      model = JBList.createDefaultListModel(templateRenderers)
      selectedIndex = getDefaultSelectedTemplateIndex(templateRenderers, emptyItemLabel)
    }
  }

  override fun canGoForward(): ObservableBool = validatorPanel.hasErrors().not()

  override fun onEntering() = validateTemplate()

  /**
   * See also [com.android.tools.idea.actions.NewAndroidComponentAction.update]
   */
  private fun validateTemplate() {
    val sdkInfo = androidSdkInfo.valueOrNull
    val facet = renderModel.androidFacet

    fun AndroidFacet.getModuleInfo() = StudioAndroidModuleInfo.getInstance(this)

    val moduleApiLevel = sdkInfo?.minApiLevel ?: facet?.getModuleInfo()?.minSdkVersion?.featureLevel ?: Integer.MAX_VALUE

    val project = renderModel.project
    val isAndroidxProject = project.isAndroidx()

    invalidParameterMessage.set(
      renderModel.newTemplate.validate(moduleApiLevel, isNewModule, isAndroidxProject, renderModel.language.value, messageKeys)
    )

    if (invalidParameterMessage.get() == "" && !hasComposeMinAgpVersion(project, renderModel.newTemplate.category)) {
      invalidParameterMessage.set(message("android.wizard.validate.module.needs.new.agp", COMPOSE_MIN_AGP_VERSION))
    }
  }

  interface TemplateRenderer {
    val label: String
    /** Return the image associated with the current template, if it specifies one, or null otherwise. */
    val icon: Icon?
    val exists: Boolean
  }

  open class NewTemplateRenderer(internal val template: Template) : TemplateRenderer {
    override val label: String
      get() = getTemplateTitle(template)

    override val icon: Icon?
      get() = getTemplateIcon(template)

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
fun Template.validate(moduleApiLevel: Int,
                      isNewModule: Boolean,
                      isAndroidxProject: Boolean,
                      language: Language,
                      messageKeys: WizardGalleryItemsStepMessageKeys
): String = when {
  this == Template.NoActivity -> if (isNewModule) "" else message(messageKeys.itemNotFound)
  moduleApiLevel < this.minSdk -> message(messageKeys.invalidMinSdk, this.minSdk)
  constraints.contains(TemplateConstraint.AndroidX) && !isAndroidxProject -> message(messageKeys.invalidAndroidX)
  constraints.contains(TemplateConstraint.Kotlin) && language != Language.Kotlin && isNewModule -> message(messageKeys.invalidNeedsKotlin)
  else -> ""
}

data class WizardGalleryItemsStepMessageKeys(
  val addMessage: String,
  val stepTitle: String,
  val itemNotFound: String,
  val invalidMinSdk: String,
  val invalidAndroidX: String,
  val invalidNeedsKotlin: String
)
