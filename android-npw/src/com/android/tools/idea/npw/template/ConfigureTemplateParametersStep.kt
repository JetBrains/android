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
import com.android.tools.adtui.validation.ValidatorPanel
import com.android.tools.idea.npw.invokeLater
import com.android.tools.idea.npw.model.RenderTemplateModel
import com.android.tools.idea.npw.project.getSourceProvider
import com.android.tools.idea.npw.template.components.CheckboxProvider
import com.android.tools.idea.npw.template.components.ComponentProvider
import com.android.tools.idea.npw.template.components.EnumComboProvider
import com.android.tools.idea.npw.template.components.LabelFieldProvider
import com.android.tools.idea.npw.template.components.LabelWithEditButtonProvider
import com.android.tools.idea.npw.template.components.LanguageComboProvider
import com.android.tools.idea.npw.template.components.ModuleTemplateComboProvider
import com.android.tools.idea.npw.template.components.PackageComboProvider
import com.android.tools.idea.npw.template.components.SeparatorProvider
import com.android.tools.idea.npw.template.components.TextFieldProvider
import com.android.tools.idea.npw.template.components.UrlLinkProvider
import com.android.tools.idea.npw.toWizardFormFactor
import com.android.tools.idea.observable.AbstractProperty
import com.android.tools.idea.observable.BindingsManager
import com.android.tools.idea.observable.ListenerManager
import com.android.tools.idea.observable.core.ObjectProperty
import com.android.tools.idea.observable.core.ObservableBool
import com.android.tools.idea.observable.core.StringProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.observable.ui.SelectedItemProperty
import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.android.tools.idea.templates.uniquenessSatisfied
import com.android.tools.idea.templates.validate
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.android.tools.idea.wizard.template.CheckBoxWidget
import com.android.tools.idea.wizard.template.Constraint
import com.android.tools.idea.wizard.template.Constraint.ACTIVITY
import com.android.tools.idea.wizard.template.Constraint.APP_PACKAGE
import com.android.tools.idea.wizard.template.Constraint.CLASS
import com.android.tools.idea.wizard.template.Constraint.DRAWABLE
import com.android.tools.idea.wizard.template.Constraint.LAYOUT
import com.android.tools.idea.wizard.template.Constraint.MODULE
import com.android.tools.idea.wizard.template.Constraint.PACKAGE
import com.android.tools.idea.wizard.template.Constraint.SOURCE_SET_FOLDER
import com.android.tools.idea.wizard.template.Constraint.STRING
import com.android.tools.idea.wizard.template.Constraint.URI_AUTHORITY
import com.android.tools.idea.wizard.template.EnumParameter
import com.android.tools.idea.wizard.template.EnumWidget
import com.android.tools.idea.wizard.template.LabelWidget
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.LanguageWidget
import com.android.tools.idea.wizard.template.PackageNameWidget
import com.android.tools.idea.wizard.template.Parameter
import com.android.tools.idea.wizard.template.ParameterWidget
import com.android.tools.idea.wizard.template.Separator
import com.android.tools.idea.wizard.template.StringParameter
import com.android.tools.idea.wizard.template.Template
import com.android.tools.idea.wizard.template.TemplateConstraint
import com.android.tools.idea.wizard.template.TextFieldWidget
import com.android.tools.idea.wizard.template.UrlLinkWidget
import com.android.tools.idea.wizard.template.Widget
import com.android.tools.idea.wizard.ui.WizardUtils
import com.android.tools.idea.wizard.ui.WizardUtils.wrapWithVScroll
import com.google.common.base.Joiner
import com.google.common.io.Files
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.ui.RecentsManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.layout.CCFlags
import com.intellij.ui.layout.panel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import org.jetbrains.android.util.AndroidBundle.message
import java.awt.FlowLayout
import java.util.EnumSet
import java.util.Optional
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane

val TYPE_CONSTRAINTS: EnumSet<Constraint> = EnumSet.of(
  ACTIVITY, CLASS, PACKAGE, APP_PACKAGE, MODULE, LAYOUT, DRAWABLE, SOURCE_SET_FOLDER, STRING, URI_AUTHORITY
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
class ConfigureTemplateParametersStep(model: RenderTemplateModel, title: String, private val templates: List<NamedModuleTemplate>)
  : ModelWizardStep<RenderTemplateModel>(model, title) {
  private val bindings = BindingsManager()
  private val listeners = ListenerManager()
  private val parameterRows = hashMapOf<Parameter<in Any>, RowEntry<*>>()
  private val userValues = hashMapOf<Parameter<*>, Any>()

  /**
   * Validity check of all parameters is performed when any parameter changes, and the first error found is set here.
   * This is then registered as its own validator with [validatorPanel].
   * This vastly simplifies validation, as we no longer have to worry about implicit relationships between parameters
   * (when changing one makes another valid/invalid).
   */
  private val invalidParameterMessage = StringValueProperty()

  private val templateDescriptionLabel = JBLabel().apply {
    font = JBFont.label().lessOn(1f)
  }
  private val templateTitleLabel = JBLabel(title).apply {
    font = JBFont.label().asBold()
  }

  private var parametersPanel = JPanel(TabularLayout("Fit-,*").setVGap(10))

  private val mainPanel = panel {
    row {
      templateTitleLabel()
    }
    row {
      cell(isVerticalFlow = true, isFullWidth = true) {
        templateDescriptionLabel()
      }
    }
    row {
      cell(isFullWidth = true) {
        parametersPanel(constraints = arrayOf(CCFlags.growX))
      }
    }
  }.apply {
    border = JBUI.Borders.emptyTop(32)
  }

  private val validatorPanel: ValidatorPanel = ValidatorPanel(this, mainPanel)
  private val rootPanel: JScrollPane = wrapWithVScroll(validatorPanel)
  private var evaluationState = EvaluationState.NOT_EVALUATING
  private val parameters: Collection<Parameter<*>> get() = model.newTemplate.parameters
  private val project: Project? get() = if (model.isNewProject) null else model.project

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

    invokeLater {
      // We want to set the label's text AFTER the wizard has been packed. Otherwise, its
      // width calculation gets involved and can really stretch out some wizards if the label is
      // particularly long (see Primary/Detail Activity for example).
      templateDescriptionLabel.text = WizardUtils.toHtmlString(newTemplate.description)
    }

    icon = newTemplate.formFactor.toWizardFormFactor().icon

    templateTitleLabel.text = newTemplate.name

    for (widget in model.newTemplate.widgets) {
      if (widget is LanguageWidget && (model.moduleTemplateDataBuilder.isNewModule || model.projectTemplateDataBuilder.isNewProject)) {
        // We should not show language chooser in "New Module" and "New Project" wizards because it should be selected on a previous step.
        continue
      }

      val row = createRowForWidget(model.module, widget).apply { addToPanel(parametersPanel) }

      if (widget !is ParameterWidget<*>) {
        continue
      }

      val property = row.property
      val parameter = widget.parameter as Parameter<Any>
      property?.addListener {
        // If not evaluating, change comes from the user (or user pressed "Back" and updates are "external". eg Template changed)
        if (evaluationState != EvaluationState.EVALUATING && mainPanel.isShowing) {
          userValues[parameter] = property.get()
          parameter.setFromProperty(property)
          // Evaluate later to prevent modifying Swing values that are locked during read
          enqueueEvaluateParameters()
        }
      }
      parameterRows[parameter] = row
      when (widget) {
        // We cannot know a good default value for package in template, but it's being preset in [createRowForWidget]
        is PackageNameWidget -> parameter.value = property!!.get()
        is EnumWidget -> row.setValue((parameter.value as Enum<*>).name)
        else -> row.setValue(parameter.value)
      }
    }

    if (templates.size > 1) {
      val row = RowEntry("Target Source Set", ModuleTemplateComboProvider(templates)).apply {
        setEnabled(true)
        addToPanel(parametersPanel)
        property!!.addListener {
          model.wizardParameterData.sourceProviderName = (property.get() as Optional<NamedModuleTemplate>).get().name
        }
      }

      val template = (row.property as SelectedItemProperty<NamedModuleTemplate>)
      // ModuleTemplateComboProvider always sets this
      bindings.bind(model.template, ObjectProperty.wrap(template))
      template.addListener { enqueueEvaluateParameters() }
    }

    validatorPanel.registerMessageSource(invalidParameterMessage)

    // TODO do not deduplicate package name etc.
    val parameterValues = parameters.filterIsInstance<StringParameter>()
      .associateWith { userValues[it] ?: deduplicate(it) }

    parameters.forEach {
      val resolvedValue = parameterValues[it]
      if (resolvedValue != null) {
        parameterRows[it]!!.setValue(resolvedValue)
      }
    }

    evaluateParameters()
  }

  /**
   * Every template parameter, based on its type, can generate a row of* components. For example, a text parameter becomes a
   * "Label: Textfield" set, while a list of choices becomes "Label: pulldown".
   *
   * This method takes an input [Parameter] and returns a generated [RowEntry] for  it, which neatly encapsulates its UI.
   * The caller should use [RowEntry.addToPanel] after receiving it.
   */
  private fun createRowForWidget(module: Module?, widget: Widget<*>): RowEntry<*> = when (widget) {
    is TextFieldWidget -> RowEntry(widget.p.name, TextFieldProvider(widget.parameter))
    is LabelWidget -> RowEntry(LabelFieldProvider(widget.text))
    is LanguageWidget -> RowEntry(message("android.wizard.language.combo.header"), LanguageComboProvider()).also {
      val language = (it.property as SelectedItemProperty<Language>)
      bindings.bindTwoWay(language, model.language)
      if (TemplateConstraint.Kotlin in model.newTemplate.constraints) {
        model.language.value = Language.Kotlin
        it.setEnabled(false)
        it.setVisible(false)
      }
    }
    is PackageNameWidget -> {
      val rowEntry = if (module != null)
        RowEntry(
          widget.p.name, PackageComboProvider(module.project, widget.p, model.packageName.get(), getRecentsKeyForParameter(widget.p))
        )
      else
        RowEntry(widget.p.name, LabelWithEditButtonProvider(widget.p))

      // All ATTR_PACKAGE_NAME providers should be string types and provide StringProperties
      val packageName = rowEntry.property as StringProperty
      bindings.bindTwoWay(packageName, model.packageName)
      // Model.packageName is used for parameter evaluation, but updated asynchronously. Do new evaluation when value changes.
      listeners.listen(model.packageName) { enqueueEvaluateParameters() }
      rowEntry
    }
    is CheckBoxWidget -> RowEntry(CheckboxProvider(widget.p))
    is UrlLinkWidget -> RowEntry(UrlLinkProvider(widget.urlName, widget.urlAddress))
    is Separator -> RowEntry(SeparatorProvider())
    is EnumWidget<*> -> RowEntry(widget.p.name, EnumComboProvider(widget.p))
    else -> error("Only string and bool parameters are supported for now")
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

    invokeLater { evaluateParameters() }
  }

  /**
   * Run through all parameters for our current template and update their values,
   * including visibility, enabled state, and actual values.
   */
  private fun evaluateParameters() {
    evaluationState = EvaluationState.EVALUATING

    parameters.forEach { p ->
      parameterRows[p]!!.apply {
        setEnabled(p.enabled)
        setVisible(p.isVisibleAndEnabled)
      }
    }

    val parameterValues = parameters.filterIsInstance<StringParameter>()
      .associateWith { userValues[it] ?: deduplicate(it) }

    parameters.forEach {
      val resolvedValue = parameterValues[it]
      if (resolvedValue != null) {
        parameterRows[it]!!.setValue(resolvedValue)
      }
    }

    evaluationState = EvaluationState.NOT_EVALUATING

    invalidParameterMessage.set(validateAllParameters() ?: "")
  }

  private fun validateAllParameters(): String? {
    val sourceProvider = model.template.get().getSourceProvider()

    return parameters
      .filterIsInstance<StringParameter>()
      .filter { it.isVisibleAndEnabled }
      .firstNotNullOfOrNull { parameter ->
        val property = parameterRows[parameter as Parameter<in Any>]?.property ?: return@firstNotNullOfOrNull null
        parameter.validate(project, model.module, sourceProvider, model.packageName.get(), property.get(), getRelatedValues(parameter))
      }
  }

  override fun getComponent(): JComponent = rootPanel

  override fun getPreferredFocusComponent(): JComponent? = parametersPanel.components.firstOrNull {
    val child = it as JComponent
    child.componentCount == 0 && child.isFocusable && child.isVisible
  } as? JComponent

  override fun canGoForward(): ObservableBool = validatorPanel.hasErrors().not()

  private fun resetPanel() {
    parametersPanel.removeAll()
    parameterRows.clear()
    userValues.clear()
    dispose()
  }

  override fun dispose() {
    bindings.releaseAll()
    listeners.releaseAll()
  }

  /**
   * When finished with this step, calculate and install a bunch of values.
   */
  override fun onProceeding() {
    // Some parameter values should be saved for later runs through this wizard, so do that first.
    parameterRows.values.forEach(RowEntry<*>::accept)

    parameterRows.forEach { (p, row) ->
      p.setFromProperty(row.property!!)
    }
  }

  private fun <T> Parameter<T>.setFromProperty(property: AbstractProperty<*>) {
    when (this) {
      is EnumParameter -> {
        this.value = this.fromString(property.get() as String)!!
      }
      else -> {
        @Suppress("UNCHECKED_CAST")
        this.value = property.get() as T // TODO(qumeric): row may have no property? (e.g. separator)
      }
    }
  }

  /**
   * Fetches the values of all parameters that are related to the target parameter. This is useful when validating a parameter's value.
   */
  private fun getRelatedValues(parameter: Parameter<*>): Set<Any> =
    parameters.filter { parameter.isRelated(it) }.mapNotNull { parameterRows[it]?.property?.get() }.toSet()

  /**
   * Because the FreeMarker templating engine is mostly opaque to us, any time any parameter changes, we need to re-evaluate all parameters.
   * Parameter evaluation can be started immediately via [evaluateParameters] or with a delay using [enqueueEvaluateParameters].
   */
  private enum class EvaluationState {
    NOT_EVALUATING,
    REQUEST_ENQUEUED,
    EVALUATING
  }

  /**
   * A template is broken down into separate fields, each which is given a row with optional header.
   * This class wraps all UI elements in the row, providing methods for managing them.
   */
  private class RowEntry<T : JComponent> {
    val component: T
    val property: AbstractProperty<*>?

    private val header: JPanel?
    private val componentProvider: ComponentProvider<T>
    private val container: JPanel = JBPanel<JBPanel<*>>().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
    }

    constructor(headerText: String, componentProvider: ComponentProvider<T>) {
      val headerLabel = JBLabel(headerText)
      header = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
        add(headerLabel)
        add(Box.createHorizontalStrut(20))
      }
      this.componentProvider = componentProvider
      component = componentProvider.createComponent()
      property = componentProvider.createProperty(component)

      headerLabel.labelFor = component
      container.apply {
        add(header)
        add(component)
      }
    }

    constructor(componentProvider: ComponentProvider<T>) {
      header = null
      this.componentProvider = componentProvider
      component = componentProvider.createComponent()
      property = componentProvider.createProperty(component)
      container.add(component)
    }

    fun addToPanel(panel: JPanel) {
      require(panel.layout is TabularLayout)
      val row = panel.componentCount
      panel.add(container, TabularLayout.Constraint(row, 1, 1))
    }

    fun setEnabled(enabled: Boolean) {
      header?.isEnabled = enabled
      component.isEnabled = enabled
    }

    fun setVisible(visible: Boolean) {
      header?.isVisible = visible
      component.isVisible = visible
    }

    @Suppress("UNCHECKED_CAST")
    fun <V : Any> setValue(value: V) {
      checkNotNull(property)
      (property as AbstractProperty<V>).set(value)
    }

    fun accept() {
      componentProvider.accept(component)
    }
  }

  private fun deduplicate(parameter: StringParameter): String {
    val value = parameter.suggest() ?: parameter.value
    if (value.isEmpty() || !parameter.constraints.contains(Constraint.UNIQUE)) {
      return value
    }

    var suggested = value
    val extPart = Files.getFileExtension(value)

    // First remove file extension. Then remove all trailing digits, because we probably were the ones that put them there.
    // For example, if two parameters affect each other, say "Name" and "Layout", you get this:
    // Step 1) Resolve "Name" -> "Name2", causes related "Layout" to become "Layout2"
    // Step 2) Resolve "Layout2" -> "Layout22"
    // Although we may possibly strip real digits from a name, it's much more likely we're not,
    // and a user can always modify the related value manually in that rare case.
    val namePart = value.replace(".$extPart", "").replace("\\d*$".toRegex(), "")
    val filenameJoiner = Joiner.on('.').skipNulls()

    var suffix = 2
    val relatedValues = getRelatedValues(parameter)
    val sourceProvider = model.template.get().getSourceProvider()
    while (!parameter.uniquenessSatisfied(project, model.module, sourceProvider, model.packageName.get(), suggested, relatedValues)) {
      suggested = filenameJoiner.join(namePart + suffix, extPart.ifEmpty { null })
      suffix++
    }
    return suggested
  }
}

