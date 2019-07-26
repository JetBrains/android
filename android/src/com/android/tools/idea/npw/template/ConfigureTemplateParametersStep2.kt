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

import com.android.tools.adtui.TabularLayout
import com.android.tools.adtui.TooltipLabel
import com.android.tools.adtui.validation.ValidatorPanel
import com.android.tools.idea.npw.FormFactor
import com.android.tools.idea.npw.model.RenderTemplateModel
import com.android.tools.idea.npw.platform.Language
import com.android.tools.idea.npw.project.getSourceProvider
import com.android.tools.idea.npw.template.ConfigureTemplateParametersStep.RowEntry
import com.android.tools.idea.npw.template.components.CheckboxProvider2
import com.android.tools.idea.npw.template.components.EnumComboProvider2
import com.android.tools.idea.npw.template.components.LabelWithEditButtonProvider2
import com.android.tools.idea.npw.template.components.LanguageComboProvider
import com.android.tools.idea.npw.template.components.ModuleTemplateComboProvider
import com.android.tools.idea.npw.template.components.PackageComboProvider2
import com.android.tools.idea.npw.template.components.SeparatorProvider
import com.android.tools.idea.npw.template.components.TextFieldProvider2
import com.android.tools.idea.observable.BindingsManager
import com.android.tools.idea.observable.ListenerManager
import com.android.tools.idea.observable.core.ObjectProperty
import com.android.tools.idea.observable.core.ObservableBool
import com.android.tools.idea.observable.core.StringProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.observable.expressions.Expression
import com.android.tools.idea.observable.ui.IconProperty
import com.android.tools.idea.observable.ui.SelectedItemProperty
import com.android.tools.idea.observable.ui.TextProperty
import com.android.tools.idea.observable.ui.VisibleProperty
import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel.wrappedWithVScroll
import com.android.tools.idea.ui.wizard.WizardUtils
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.android.tools.idea.wizard.template.BooleanParameter
import com.android.tools.idea.wizard.template.Constraint
import com.android.tools.idea.wizard.template.Constraint.ACTIVITY
import com.android.tools.idea.wizard.template.Constraint.API_LEVEL
import com.android.tools.idea.wizard.template.Constraint.APP_PACKAGE
import com.android.tools.idea.wizard.template.Constraint.CLASS
import com.android.tools.idea.wizard.template.Constraint.DRAWABLE
import com.android.tools.idea.wizard.template.Constraint.ID
import com.android.tools.idea.wizard.template.Constraint.LAYOUT
import com.android.tools.idea.wizard.template.Constraint.MODULE
import com.android.tools.idea.wizard.template.Constraint.PACKAGE
import com.android.tools.idea.wizard.template.Constraint.SOURCE_SET_FOLDER
import com.android.tools.idea.wizard.template.Constraint.STRING
import com.android.tools.idea.wizard.template.Constraint.URI_AUTHORITY
import com.android.tools.idea.wizard.template.EnumParameter
import com.android.tools.idea.wizard.template.Parameter
import com.android.tools.idea.wizard.template.Separator
import com.android.tools.idea.wizard.template.StringParameter
import com.android.tools.idea.wizard.template.Template
import com.google.common.cache.CacheBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.ui.RecentsManager
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER
import com.intellij.uiDesigner.core.GridConstraints.ANCHOR_NORTHWEST
import com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST
import com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH
import com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL
import com.intellij.uiDesigner.core.GridConstraints.FILL_NONE
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.JBUI
import org.jetbrains.kotlin.utils.addToStdlib.firstNotNullResult
import java.awt.Dimension
import java.awt.Font
import java.util.EnumSet
import java.util.Optional
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.SwingConstants

private val log get() = logger<ConfigureTemplateParametersStep2>()

val TYPE_CONSTRAINTS: EnumSet<Constraint> = EnumSet.of(
  ACTIVITY, API_LEVEL, CLASS, PACKAGE, APP_PACKAGE, MODULE, LAYOUT, DRAWABLE, ID, SOURCE_SET_FOLDER, STRING, URI_AUTHORITY
)

fun Parameter<*>.isRelated(p: Parameter<*>): Boolean =
  p is StringParameter && this is StringParameter && p !== this &&
  TYPE_CONSTRAINTS.intersect(constraints).intersect(p.constraints).isNotEmpty()

/**
 * A step which takes a template and wraps a UI around it, allowing a user to modify its various parameters.
 *
 * Far from being generic data, the template edited by this step is very Android specific, and  needs to be aware of things like
 * the current project/module, package name, min supported API, previously configured values, etc.
 */
class ConfigureTemplateParametersStep2(model: RenderTemplateModel, title: String, private val templates: List<NamedModuleTemplate>)
  : ModelWizardStep<RenderTemplateModel>(model, title) {
  private val bindings = BindingsManager()
  private val listeners = ListenerManager()
  private val thumbnailsCache = CacheBuilder.newBuilder().build(IconLoader())!!
  private val parameterRows = hashMapOf<Parameter<in Any>, RowEntry<*>>()
  private val userValues = hashMapOf<Parameter<*>, Any>()
  private val thumbPath = StringValueProperty()
  /**
   * Validity check of all parameters is performed when any parameter changes, and the first error found is set here.
   * This is then registered as its own validator with [validatorPanel].
   * This vastly simplifies validation, as we no longer have to worry about implicit relationships between parameters
   * (when changing one makes another valid/invalid).
   */
  private val invalidParameterMessage = StringValueProperty()

  private val templateDescriptionLabel = JLabel().apply {
    font = Font("Default", Font.BOLD, 18)
  }
  private val templateThumbLabel = JLabel().apply {
    horizontalTextPosition = SwingConstants.CENTER
    verticalAlignment = SwingConstants.TOP
  }
  private var parametersPanel = JPanel(TabularLayout("Fit-,*").setVGap(10))
  private val footerSeparator = JSeparator()
  private val parameterDescriptionLabel = TooltipLabel().apply {
    setScope(parametersPanel)
    // Add an extra blank line under the template description to separate it from the main body
    border = JBUI.Borders.emptyBottom(templateDescriptionLabel.font.size)
  }

  private val rootPanel = JPanel(GridLayoutManager(4, 3)).apply {
    val anySize = Dimension(-1, -1)
    add(templateDescriptionLabel, GridConstraints(0, 1, 1, 1, ANCHOR_WEST, FILL_BOTH, 3, 0, anySize, anySize, anySize, 0, false))
    add(templateThumbLabel, GridConstraints(1, 0, 1, 1, ANCHOR_NORTHWEST, FILL_NONE, 0, 0, anySize, anySize, anySize, 0, false))
    add(parametersPanel, GridConstraints(1, 1, 1, 2, ANCHOR_CENTER, FILL_BOTH, 3, 7, anySize, anySize, anySize, 0, false))
    add(footerSeparator, GridConstraints(2, 1, 1, 1, ANCHOR_CENTER, FILL_HORIZONTAL, 3, 0, anySize, anySize, anySize, 0, false))
    add(parameterDescriptionLabel, GridConstraints(3, 1, 1, 1, ANCHOR_WEST, FILL_NONE, 1, 2, anySize, anySize, anySize, 0, false))
  }

  private val validatorPanel: ValidatorPanel = ValidatorPanel(this, wrappedWithVScroll(rootPanel))

  private var evaluationState = EvaluationState.NOT_EVALUATING

  private val isNewModule: Boolean
    get() = model.module == null

  /**
   * Get the current thumbnail path.
   */
  private val thumbnailPath: String
    get() = model.newTemplate.thumb().path.path

  /**
   * Given a parameter, return a String key we can use to interact with IntelliJ's [RecentsManager] system.
   */
  private fun getRecentsKeyForParameter(parameter: Parameter<*>) = "android.template.${parameter.hashCode()}"

  override fun shouldShow(): Boolean = model.newTemplate !== Template.NoActivity

  @Suppress("UNCHECKED_CAST")
  override fun onEntering() {
    // The Model TemplateHandle may have changed, rebuild the panel
    resetPanel()

    val newTemplate = model.newTemplate

    ApplicationManager.getApplication().invokeLater(
      {
        // We want to set the label's text AFTER the wizard has been packed. Otherwise, its
        // width calculation gets involved and can really stretch out some wizards if the label is
        // particularly long (see Master/Detail Activity for example).
        templateDescriptionLabel.text = WizardUtils.toHtmlString(newTemplate.description)
      }, ModalityState.any())

    icon = FormFactor[newTemplate.formFactor.toString()].icon // TODO(qumeric): do not use strings (may fail with Generic, etc.)

    val thumb = IconProperty(templateThumbLabel)
    val thumbVisibility = VisibleProperty(templateThumbLabel)
    bindings.bind(thumb, object : Expression<Optional<Icon>>(thumbPath) {
      override fun get(): Optional<Icon> = thumbnailsCache.getUnchecked(newTemplate.thumb().path)
    })
    bindings.bind(thumbVisibility, object : Expression<Boolean>(thumb) {
      override fun get(): Boolean = thumb.get().isPresent
    })
    thumbPath.set(thumbnailPath)

    val parameterDescription = TextProperty(parameterDescriptionLabel)
    bindings.bind(VisibleProperty(footerSeparator), object : Expression<Boolean>(parameterDescription) {
      override fun get(): Boolean = parameterDescription.get().isNotEmpty()
    })

    for (parameter in newTemplate.parameters(model.wizardParameterData)) {
      val row = createRowForParameter(model.module, parameter)
      val property = row.property
      property?.addListener {
        // If not evaluating, change comes from the user (or user pressed "Back" and updates are "external". eg Template changed)
        if (evaluationState != EvaluationState.EVALUATING && rootPanel.isShowing) {
          userValues[parameter] = property.get()
          // Evaluate later to prevent modifying Swing values that are locked during read
          enqueueEvaluateParameters()
        }
      }
      parameterRows[parameter as Parameter<in Any>] = row
      row.addToPanel(parametersPanel)
      row.setValue(parameter.defaultValue)
    }

    if (templates.size > 1) {
      val row = RowEntry("Target Source Set", ModuleTemplateComboProvider(templates))
      row.setEnabled(true)
      row.addToPanel(parametersPanel)

      val template = (row.property as SelectedItemProperty<NamedModuleTemplate>)
      // ModuleTemplateComboProvider always sets this
      bindings.bind(model.template, ObjectProperty.wrap(template))
      template.addListener { enqueueEvaluateParameters() }
    }

    validatorPanel.registerMessageSource(invalidParameterMessage)

    evaluateParameters()
  }

  /**
   * Every template parameter, based on its type, can generate a row of* components. For example, a text parameter becomes a
   * "Label: Textfield" set, while a list of choices becomes "Label: pulldown".
   *
   * This method takes an input [Parameter] and returns a generated [RowEntry] for  it, which neatly encapsulates its UI.
   * The caller should use [RowEntry.addToPanel] after receiving it.
   */
  private fun createRowForParameter(module: Module?, parameter: Parameter<*>): RowEntry<*> {
    requireNotNull(parameter.name)
    val name = parameter.name

    // TODO(qumeric): Extract this logic into an extension point at some point, in order to be more friendly to third-party plugins with templates?
    return when (parameter) {
      is StringParameter -> when (parameter.type) {
        StringParameter.Type.STRING -> RowEntry(name, TextFieldProvider2(parameter))
        StringParameter.Type.LANGUAGE -> RowEntry(name, LanguageComboProvider()).also {
          val language = (it.property as SelectedItemProperty<Language>)
          // LanguageComboProvider always sets this
          bindings.bindTwoWay(ObjectProperty.wrap(language), model.renderLanguage)
        }
        StringParameter.Type.PACKAGE_NAME -> {
          val rowEntry = if (module != null)
            RowEntry(name, PackageComboProvider2(module.project, parameter, model.packageName.get(), getRecentsKeyForParameter(parameter)))
          else
            RowEntry(name, LabelWithEditButtonProvider2(parameter))

          // All ATTR_PACKAGE_NAME providers should be string types and provide StringProperties
          val packageName = rowEntry.property as StringProperty
          bindings.bindTwoWay(packageName, model.packageName)
          // Model.packageName is used for parameter evaluation, but updated asynchronously. Do new evaluation when value changes.
          listeners.listen(model.packageName) { enqueueEvaluateParameters() }
          rowEntry
        }
      }
      is BooleanParameter -> RowEntry(CheckboxProvider2(parameter), false)
      is Separator -> RowEntry(SeparatorProvider(), true)
      is EnumParameter -> RowEntry(name, EnumComboProvider2(parameter))
      else -> TODO("Support DataParameter and maybe IntParameter")
    }
  }

  /**
   * Instead of evaluating all parameters immediately, invoke the request to run later. This option allows us to avoid the situation where
   * a value has just changed, is forcefully re-evaluated immediately, and causes Swing to throw an exception between we're editing a
   * value while it's in a locked read-only state.
   */
  private fun enqueueEvaluateParameters() {
    if (evaluationState == EvaluationState.REQUEST_ENQUEUED) {
      return
    }
    evaluationState = EvaluationState.REQUEST_ENQUEUED

    ApplicationManager.getApplication().invokeLater({ this.evaluateParameters() }, ModalityState.any())
  }

  /**
   * Run through all parameters for our current template and update their values,
   * including visibility, enabled state, and actual values.
   *
   * Because our templating system is opaque to us, this operation is relatively overkill
   * (we evaluate all parameters every time, not just ones we suspect have changed),
   * but this should only get run in response to user input, which isn't too often.
   */
  private fun evaluateParameters() {
    evaluationState = EvaluationState.EVALUATING

    val parameters = model.newTemplate.parameters(model.wizardParameterData)

    // TODO: val parameterValues = ParameterValueResolver.resolve(parameters, userValues, additionalValues, ParameterDeduplicator())

    parameters.forEach {
      val enabled = it.enabled
      parameterRows[it]!!.setEnabled(enabled)

      val visible = it.isVisibleAndEnabled
      parameterRows[it]!!.setVisible(visible)
    }
    // Aggressively update the icon path just in case it changed
    thumbPath.set(thumbnailPath)

    evaluationState = EvaluationState.NOT_EVALUATING

    invalidParameterMessage.set(validateAllParameters() ?: "")
  }

  private fun validateAllParameters(): String? {
    val parameters = model.newTemplate.parameters(model.wizardParameterData)
    val project = model.project.valueOrNull
    val sourceProvider = model.template.get().getSourceProvider()

    return parameters.firstNotNullResult { parameter ->
      val property = parameterRows[parameter]?.property
      if (property == null || !parameter.isVisibleAndEnabled) {
        return@firstNotNullResult null
      }
      null
      /* TODO: do actual validation
      when (parameter) {
        is StringParameter -> parameter.validate(
          project, model.module, sourceProvider, model.packageName.get(), property.get(), getRelatedValues(parameter))
        else -> null
      }
       */
    }
  }

  override fun getComponent(): JComponent = validatorPanel

  override fun getPreferredFocusComponent(): JComponent? = parametersPanel.components.firstOrNull {
    val child = it as JComponent
    child.componentCount == 0 && child.isFocusable && child.isVisible
  } as? JComponent

  override fun canGoForward(): ObservableBool = validatorPanel.hasErrors().not()

  private fun resetPanel() {
    parametersPanel.removeAll()
    parameterRows.clear()
    dispose()
  }

  override fun dispose() {
    bindings.releaseAll()
    listeners.releaseAll()
    thumbnailsCache.invalidateAll()
  }

  /**
   * When finished with this step, calculate and install a bunch of values.
   */
  override fun onProceeding() {
    // Some parameter values should be saved for later runs through this wizard, so do that first.
    parameterRows.values.forEach(RowEntry<*>::accept)

    parameterRows.forEach { (p, row) ->
      p.value = row.property!!.get() // FIXME row may have no property? (e.g. separator)
    }
  }

  /**
   * Fetches the values of all parameters that are related to the target parameter. This is useful when validating a parameter's value.
   */
  private fun getRelatedValues(parameter: Parameter<*>): Set<Any> =
    model.newTemplate.parameters(model.wizardParameterData)
      .filter { parameter.isRelated(it) }
      .mapNotNull { parameterRows[it]?.property?.get() }.toSet()

  /**
   * Because the FreeMarker templating engine is mostly opaque to us, any time any parameter changes, we need to re-evaluate all parameters.
   * Parameter evaluation can be started immediately via [evaluateParameters] or with a delay using [enqueueEvaluateParameters].
   */
  private enum class EvaluationState {
    NOT_EVALUATING,
    REQUEST_ENQUEUED,
    EVALUATING
  }
}