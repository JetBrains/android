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
package com.android.tools.idea.databinding.psiclass

import com.android.tools.idea.databinding.BindingLayout
import com.android.tools.idea.databinding.BindingLayoutGroup
import com.android.tools.idea.databinding.ModuleDataBinding
import com.android.tools.idea.databinding.util.findVariableTag
import com.android.tools.idea.databinding.util.getViewBindingClassName
import com.android.tools.idea.databinding.index.BindingLayoutType.DATA_BINDING_LAYOUT
import com.android.tools.idea.databinding.index.BindingXmlIndex
import com.android.tools.idea.databinding.index.VariableData
import com.android.tools.idea.databinding.index.ViewIdData
import com.intellij.psi.xml.XmlTag
import org.jetbrains.android.facet.AndroidFacet

/**
 * All values needed to generate a specific [LightBindingClass]
 */
interface LightBindingClassConfig {
  val facet: AndroidFacet

  val targetLayout: BindingLayout

  /**
   * The fully-qualified name of the class which this class inherits from.
   */
  val superName: String

  /**
   * The exact class name that should be generated for this light binding class, potentially
   * including an "Impl" suffix.
   */
  val className: String

  /**
   * The exact qualified name that should be generated for this light binding class, potentially
   * including an "Impl" suffix.
   */
  val qualifiedName: String

  /**
   * A list of all `<variable>` tags paired with their corresponding PSI XML tag.
   *
   * Note: Even if a binding is being generated for a target layout, its API may still include
   * variables from alternate layouts in the group. This is why we need to return the XML tags
   * explicitly, as they may refer to a layout configuration outside of our own.
   */
  val variableTags: List<Pair<VariableData, XmlTag>>

  /**
   * Returns a list of all views with IDs that fields should be created for,
   * e.g. `<Button android:id="@+id/example_button">` -> "exampleButton"
   *
   * Note: These fields should only get generated for the base "Binding" class.
   */
  val viewIds: List<ViewIdData>

  /**
   * Returns `true` if the generated light binding class should have a full method API, e.g.
   * getters for its `<variable>` fields and other static methods.
   */
  fun shouldGenerateGettersAndStaticMethods(): Boolean

  /**
   * Returns `true` if we generate setters for a base class that should delegate to
   * child implementation classes.
   */
  fun settersShouldBeAbstract(): Boolean
}

private fun BindingLayoutGroup.getAggregatedVariables(): List<Pair<VariableData, XmlTag>> {
  val aggregatedVariables = mutableListOf<Pair<VariableData, XmlTag>>()
  val alreadySeen = mutableSetOf<String>()
  for (layout in layouts) {
    val xmlFile = layout.toXmlFile()
    val layoutData = layout.data
    for (variable in layoutData.variables) {
      val variableTag = xmlFile.findVariableTag(variable.name)
      if (variableTag != null && alreadySeen.add(variable.name)) {
        aggregatedVariables.add(variable to variableTag)
      }
    }
  }

  return aggregatedVariables
}

/**
 * Used to generate a "Binding" class.
 *
 * A "Binding" class should always be created. "BindingImpl"s should only be created if there
 * are multiple layout configurations.
 */
class BindingClassConfig(override val facet: AndroidFacet, private val group: BindingLayoutGroup) : LightBindingClassConfig {
  override val targetLayout: BindingLayout
    get() = group.mainLayout

  override val superName: String
    get() {
      // Main layout generates a binding class that inherits from the ViewDataBinding classes.
      return if (targetLayout.data.layoutType === DATA_BINDING_LAYOUT) {
        ModuleDataBinding.getInstance(facet).dataBindingMode.viewDataBinding
      }
      else {
        facet.module.project.getViewBindingClassName()
      }
    }

  override val className = group.mainLayout.className
  override val qualifiedName = group.mainLayout.qualifiedClassName

  override val variableTags: List<Pair<VariableData, XmlTag>>
    get() = group.getAggregatedVariables()

  override val viewIds: List<ViewIdData>
    get() {
      return group.layouts
        .mapNotNull { layout -> BindingXmlIndex.getDataForFile(layout.toXmlFile()) }
        .flatMap { data -> data.viewIds }
    }

  override fun shouldGenerateGettersAndStaticMethods() = true

  // Make setters abstract if we are also going to generate "BindingImpl" classes which implement
  // them.
  override fun settersShouldBeAbstract() = group.layouts.size > 1
}

/**
 * Used to generate a "BindingImpl" class.
 *
 * This config should only be used when there are alternate layouts defined in addition to the main
 * one; otherwise, just use [BindingClassConfig].
 */
class BindingImplClassConfig(override val facet: AndroidFacet,
                             private val group: BindingLayoutGroup,
                             private val layoutIndex: Int) : LightBindingClassConfig {
  override val targetLayout: BindingLayout
    get() = group.layouts[layoutIndex]

  /**
   * The super class for an impl is the main binding class.
   *
   * Its name is always this layout's name without the impl suffix.
   */
  override val superName: String
    get() = targetLayout.qualifiedClassName

  override val className = targetLayout.className + targetLayout.getImplSuffix()
  override val qualifiedName = targetLayout.qualifiedClassName + targetLayout.getImplSuffix()

  override val variableTags: List<Pair<VariableData, XmlTag>>
    get() = group.getAggregatedVariables()

  override val viewIds: List<ViewIdData>
    get() = listOf() // Only provided by base "Binding" class.

  override fun shouldGenerateGettersAndStaticMethods() = false
  override fun settersShouldBeAbstract() = false
}
