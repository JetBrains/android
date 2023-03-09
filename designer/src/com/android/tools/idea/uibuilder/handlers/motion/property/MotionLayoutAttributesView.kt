/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.property

import com.android.SdkConstants
import com.android.tools.adtui.common.AdtSecondaryPanel
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.handlers.constraint.MotionConstraintPanel
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneTag
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionAttributes
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditorSelector
import com.android.tools.idea.uibuilder.handlers.motion.property.action.AddCustomFieldAction
import com.android.tools.idea.uibuilder.handlers.motion.property.action.AddMotionFieldAction
import com.android.tools.idea.uibuilder.handlers.motion.property.action.DeleteCustomFieldAction
import com.android.tools.idea.uibuilder.handlers.motion.property.action.DeleteMotionFieldAction
import com.android.tools.idea.uibuilder.handlers.motion.property.action.SubSectionControlAction
import com.android.tools.idea.uibuilder.property.NlNewPropertyItem
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.idea.uibuilder.property.inspector.InspectorSection
import com.android.tools.idea.uibuilder.property.support.NlEnumSupportProvider
import com.android.tools.idea.uibuilder.property.support.NlTwoStateBooleanControlTypeProvider
import com.android.tools.idea.uibuilder.property.ui.EasingCurvePanel
import com.android.tools.idea.uibuilder.property.ui.EmptyTablePanel
import com.android.tools.idea.uibuilder.property.ui.TransformsPanel
import com.android.tools.idea.uibuilder.property.ui.spring.SpringWidget
import com.android.tools.idea.uibuilder.property.ui.spring.SpringWidgetModel
import com.android.tools.property.panel.api.EditorProvider
import com.android.tools.property.panel.api.FilteredPTableModel
import com.android.tools.property.panel.api.FilteredPTableModel.PTableModelFactory.alphabeticalSortOrder
import com.android.tools.property.panel.api.InspectorBuilder
import com.android.tools.property.panel.api.InspectorLineModel
import com.android.tools.property.panel.api.InspectorPanel
import com.android.tools.property.panel.api.PropertiesModel
import com.android.tools.property.panel.api.PropertiesTable
import com.android.tools.property.panel.api.PropertiesView
import com.android.tools.property.panel.api.TableLineModel
import com.android.tools.property.panel.api.TableUIProvider
import com.google.common.collect.ImmutableList
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.util.Ref
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import org.jetbrains.android.dom.AndroidDomElementDescriptorProvider
import org.jetbrains.annotations.VisibleForTesting
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JSeparator
import javax.swing.SwingConstants

private const val MOTION_VIEW_NAME = "Motion"
private val CONSTRAINT_SECTIONS = listOf(
  MotionSceneAttrs.Tags.LAYOUT,
  MotionSceneAttrs.Tags.MOTION,
  MotionSceneAttrs.Tags.PROPERTY_SET,
  MotionSceneAttrs.Tags.TRANSFORM
)

/**
 * [PropertiesView] for motion layout property editor.
 */
class MotionLayoutAttributesView(model: MotionLayoutAttributesModel) : PropertiesView<NlPropertyItem>(MOTION_VIEW_NAME, model) {

  init {
    val enumSupportProvider = NlEnumSupportProvider(model)
    val controlTypeProvider = NlTwoStateBooleanControlTypeProvider(enumSupportProvider)
    val editorProvider = EditorProvider.create(enumSupportProvider, controlTypeProvider)
    val tableUIProvider = TableUIProvider(controlTypeProvider, editorProvider)
    main.builders.add(SelectedTargetBuilder())
    addTab("").builders.add(MotionInspectorBuilder(model, tableUIProvider, enumSupportProvider))
  }

  @VisibleForTesting
  class MotionInspectorBuilder @VisibleForTesting constructor(
    private val model: MotionLayoutAttributesModel,
    private val tableUIProvider: TableUIProvider,
    private val enumSupportProvider: NlEnumSupportProvider
  ) : InspectorBuilder<NlPropertyItem> {

    private val myDescriptorProvider = AndroidDomElementDescriptorProvider()

    override fun attachToInspector(inspector: InspectorPanel, properties: PropertiesTable<NlPropertyItem>) {
      val any = properties.first ?: return
      val selection = any.optionalValue1 as MotionSelection? ?: return

      when (selection.type) {
        MotionEditorSelector.Type.CONSTRAINT -> {
          val showConstraintPanel = !shouldDisplaySection(MotionSceneAttrs.Tags.LAYOUT, selection)
          addPropertyTable(inspector, selection, MotionSceneAttrs.Tags.CONSTRAINT, model, true, false, showConstraintPanel)
          addTransforms(inspector, model, properties)
          val attributes = ArrayList<String>()
          attributes.add("transitionEasing")
          attributes.add("pathMotionArc")
          attributes.add("transitionPathRotate")
          addTransition(inspector, InspectorSection.TRANSITION, model, "transitionEasing", properties, attributes)
          addSubTagSections(inspector, selection, model)
        }
        MotionEditorSelector.Type.TRANSITION -> {
          addPropertyTable(inspector, selection, selection.motionSceneTagName, model, false, false, false)
          val attributes = ArrayList<String>()
          attributes.add("motionInterpolator")
          attributes.add("staggered")
          attributes.add("autoTransition")
          attributes.add("pathMotionArc")
          attributes.add("layoutDuringTransition")
          addTransition(
            inspector,
            InspectorSection.TRANSITION_MODIFIERS,
            model,
            "motionInterpolator",
            properties,
            attributes
          )
          addSubTagSections(inspector, selection, model)
        }
        else -> {
          addPropertyTable(inspector, selection, selection.motionSceneTagName, model, false, false, false)
          val allProperties = model.allProperties
          if (allProperties.containsKey("KeyAttribute")) {
            addTransforms(inspector, model, properties)
          }
        }
      }
      val showDefaultValues = selection.type == MotionEditorSelector.Type.CONSTRAINT
      addCustomAttributes(inspector, selection, model, showDefaultValues)
    }

    private fun addSubtitle(inspector: InspectorPanel, s: String, titleLine: InspectorLineModel) {
      val component: JComponent = JLabel(s)
      component.border = JBUI.Borders.empty(8)
      inspector.addComponent(component, titleLine)
    }

    private inner class MySeparator : AdtSecondaryPanel(BorderLayout()) {

      init {
        add(JSeparator(SwingConstants.HORIZONTAL), BorderLayout.CENTER)
      }

      override fun updateUI() {
        super.updateUI()
        border = JBUI.Borders.empty(4)
      }
    }

    private fun addTransforms(inspector: InspectorPanel, model: MotionLayoutAttributesModel, properties: PropertiesTable<NlPropertyItem>) {
      val titleModel = inspector.addExpandableTitle(InspectorSection.TRANSFORMS.title, false, emptyList())
      inspector.addComponent(TransformsPanel(model, properties), titleModel)
      val rotationAttributes = ArrayList<String>()
      rotationAttributes.add("rotationX")
      rotationAttributes.add("rotationY")
      rotationAttributes.add("rotation")
      val attributes = ArrayList<String>()
      attributes.add("scaleX")
      attributes.add("scaleY")
      attributes.add("translationX")
      attributes.add("translationY")
      attributes.add("alpha")
      attributes.add("visibility")
      val controlTypeProvider = NlTwoStateBooleanControlTypeProvider(enumSupportProvider)
      val editorProvider = EditorProvider.create(enumSupportProvider, controlTypeProvider)
      for (attributeName in rotationAttributes) {
        val property = properties.getOrNull(SdkConstants.ANDROID_URI, attributeName)
        if (property != null) {
          inspector.addEditor(editorProvider.createEditor(property, false), titleModel)
        }
      }
      inspector.addComponent(MySeparator(), titleModel)
      addSubtitle(inspector, "Other Transforms", titleModel)
      for (attributeName in attributes) {
        val property = properties.getOrNull(SdkConstants.ANDROID_URI, attributeName)
        if (property != null) {
          inspector.addEditor(editorProvider.createEditor(property, false), titleModel)
        }
      }
    }

    private fun addTransition(
      inspector: InspectorPanel,
      title: InspectorSection,
      model: MotionLayoutAttributesModel,
      easingAttributeName: String,
      properties: PropertiesTable<NlPropertyItem>,
      attributes: ArrayList<String>
    ) {
      val titleModel = inspector.addExpandableTitle(title.title, false, emptyList())
      inspector.addComponent(EasingCurvePanel(model, easingAttributeName, properties), titleModel)
      val controlTypeProvider = NlTwoStateBooleanControlTypeProvider(enumSupportProvider)
      val editorProvider = EditorProvider.create(enumSupportProvider, controlTypeProvider)
      for (attributeName in attributes) {
        val property = properties.getOrNull(SdkConstants.AUTO_URI, attributeName)
        if (property != null) {
          inspector.addEditor(editorProvider.createEditor(property, false), titleModel)
        }
      }
    }

    private fun addSubTagSections(
      inspector: InspectorPanel,
      selection: MotionSelection,
      model: MotionLayoutAttributesModel
    ) {
      val xmlTag = selection.getXmlTag(selection.motionSceneTag)
      val elementDescriptor = (if (xmlTag != null) myDescriptorProvider.getDescriptor(xmlTag) else null) ?: return
      val subTagDescriptors = elementDescriptor.getElementsDescriptors(xmlTag)
      for (descriptor in subTagDescriptors) {
        val subTagName = descriptor.name
        if (subTagName != MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE) {
          val showConstraintWidget = subTagName == MotionSceneAttrs.Tags.LAYOUT
          val subModel = SubTagAttributesModel(model, subTagName)
          addPropertyTable(inspector, selection, subTagName, subModel, true, true, showConstraintWidget)
          if (MotionSceneAttrs.Tags.ON_SWIPE == subTagName && StudioFlags.NELE_ON_SWIPE_PANEL.get()) {
            val titleModel = inspector.addExpandableTitle("OnSwipe Behaviour", true, emptyList())
            val springModel: SpringWidgetModel = MotionLayoutSpringModel(model)
            val springPanel = SpringWidget.panelWithUI(springModel)
            inspector.addComponent(springPanel, titleModel)
          }
        }
      }
    }

    private fun addCustomAttributes(
      inspector: InspectorPanel,
      selection: MotionSelection,
      model: MotionLayoutAttributesModel,
      showDefaultValues: Boolean
    ) {
      if (!shouldDisplaySection(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE, selection)) {
        return
      }
      val customModel = SubTagAttributesModel(model, MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE)
      val customLineModelRef = Ref<TableLineModel>(null)
      val filter: (NlPropertyItem) -> Boolean = { item: NlPropertyItem ->
        item.namespace.isEmpty() &&
        (item.rawValue != null || showDefaultValues && item.defaultValue != null)
      }
      val deleteOp: (NlPropertyItem) -> Unit = {}
      val insertOp: (String, String) -> NlPropertyItem? = { name: String, value: String? ->
        val lineModel = customLineModelRef.get()
        if (lineModel != null) {
          val customType = findCustomTypeFromName(name, selection)
          if (customType != null) {
            model.addCustomProperty(name, value!!, customType, selection, lineModel)
          }
        }
        null
      }
      val tableModel = FilteredPTableModel(customModel, filter, insertOp, deleteOp, alphabeticalSortOrder, emptyList(), false, true,
                                           { true }, { false })
      val addFieldAction = AddCustomFieldAction(this.model, selection)
      val deleteFieldAction = DeleteCustomFieldAction()
      val actions: List<AnAction> = ImmutableList.builder<AnAction>().add(addFieldAction).add(deleteFieldAction).build()
      val title = inspector.addExpandableTitle("CustomAttributes", true, actions)
      val lineModel = inspector.addTable(tableModel, true, tableUIProvider, actions, title)
      inspector.addComponent(EmptyTablePanel(addFieldAction, lineModel), title)
      addFieldAction.setLineModel(lineModel)
      deleteFieldAction.setLineModel(lineModel)
      customLineModelRef.set(lineModel)
    }

    private fun findCustomTypeFromName(attrName: String, selection: MotionSelection): CustomAttributeType? {
      val component = selection.componentForCustomAttributeCompletions ?: return null
      for (type in CustomAttributeType.values()) {
        val attributes = MotionAttributes.getCustomAttributesFor(component, type.tagName)
        if (attributes.contains(attrName)) {
          return type
        }
      }
      return null
    }

    private fun addPropertyTable(
      inspector: InspectorPanel,
      selection: MotionSelection,
      sectionTagName: String?,
      model: PropertiesModel<NlPropertyItem>,
      showDefaultValues: Boolean,
      showSectionControl: Boolean,
      showConstraintPanel: Boolean
    ) {
      if (sectionTagName == null || !shouldDisplaySection(sectionTagName, selection)) {
        return
      }
      val any = model.properties.first
      val filter = { item: NlPropertyItem ->
        item.namespace.isNotEmpty() &&
        (item.rawValue != null || showDefaultValues && item.defaultValue != null)
      }
      val deleteOp = { item: NlPropertyItem -> item.value = null }
      val insertOp = { name: String?, value: String? ->
        val newProperty = NlNewPropertyItem(
          (model as MotionLayoutAttributesModel),
          model.properties,
          { item: NlPropertyItem -> item.rawValue == null },
          {})
        newProperty.name = name!!
        if (newProperty.delegate != null) {
          newProperty.value = value
          newProperty
        }
        else null
      }
      val tableModel = FilteredPTableModel(model, filter, insertOp, deleteOp, alphabeticalSortOrder, emptyList(), true, true,
                                           { it !is MotionIdPropertyItem }, { false })
      val controlAction = SubSectionControlAction(any)
      val addFieldAction = AddMotionFieldAction(this.model, model.properties)
      val deleteFieldAction = DeleteMotionFieldAction()
      val actionsBuilder = ImmutableList.builder<AnAction>()
      if (showSectionControl) {
        actionsBuilder.add(controlAction)
      }
      actionsBuilder.add(addFieldAction)
      actionsBuilder.add(deleteFieldAction)
      val actions: List<AnAction> = actionsBuilder.build()
      val title = inspector.addExpandableTitle(sectionTagName, true, actions)
      if (showConstraintPanel) {
        addConstraintPanel(inspector, selection, title)
      }
      val lineModel = inspector.addTable(tableModel, true, tableUIProvider, actions, title)
      inspector.addComponent(EmptyTablePanel(addFieldAction, lineModel), title)
      if (showSectionControl) {
        controlAction.setLineModel(title)
      }
      addFieldAction.setLineModel(lineModel)
      deleteFieldAction.setLineModel(lineModel)
    }

    private fun shouldDisplaySection(section: String, selection: MotionSelection): Boolean {
      val tag = selection.motionSceneTag
      return when (section) {
        MotionSceneAttrs.Tags.CONSTRAINT -> {
          if (selection.type != MotionEditorSelector.Type.CONSTRAINT) {
            return false
          }
          if (tag == null) {
            // Non existent constraint tag.
            // Show the inherited constraint attributes.
            return true
          }
          // If this is not a sectioned constraint then display the tag.
          // If this it is a sectioned constraint but it has significant attributes then display the tag as well.
          val attrs: Set<String> = tag.attrList.keys
          !isSectionedConstraint(tag) || attrs.size > 1 || !attrs.contains(SdkConstants.ATTR_ID)
        }
        MotionSceneAttrs.Tags.LAYOUT, MotionSceneAttrs.Tags.PROPERTY_SET, MotionSceneAttrs.Tags.TRANSFORM, MotionSceneAttrs.Tags.MOTION -> {
          if (selection.type != MotionEditorSelector.Type.CONSTRAINT) {
            false
          }
          else tag != null && isSectionedConstraint(tag)
        }
        MotionSceneAttrs.Tags.ON_CLICK, MotionSceneAttrs.Tags.ON_SWIPE -> selection.type == MotionEditorSelector.Type.TRANSITION
        MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE -> {
          if (tag == null && selection.type == MotionEditorSelector.Type.CONSTRAINT) {
            true
          }
          else tag != null && canHaveCustomAttributes(tag)
        }
        else -> section == selection.motionSceneTagName
      }
    }

    private fun canHaveCustomAttributes(tag: MotionSceneTag): Boolean {
      val xml = tag.xmlTag ?: return false
      val elementDescriptor = myDescriptorProvider.getDescriptor(xml) ?: return false

      return elementDescriptor.getElementsDescriptors(xml).any { it.defaultName == MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE }
    }

    override fun resetCache() {}

    private fun addConstraintPanel(inspector: InspectorPanel, selection: MotionSelection, titleLine: InspectorLineModel) {
      val panel = MotionConstraintPanel(listOfNotNull(selection.component))
      panel.border = JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0)
      inspector.addComponent(panel, titleLine)
    }

    private fun isSectionedConstraint(constraintTag: MotionSceneTag) =
      constraintTag.childTags.any { CONSTRAINT_SECTIONS.contains(it.tagName) }
  }
}