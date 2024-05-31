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

import com.android.SdkConstants
import com.android.tools.idea.databinding.BindingLayout
import com.android.tools.idea.databinding.BindingLayoutGroup
import com.android.tools.idea.databinding.index.BindingLayoutType.DATA_BINDING_LAYOUT
import com.android.tools.idea.databinding.index.BindingXmlIndex
import com.android.tools.idea.databinding.index.VariableData
import com.android.tools.idea.databinding.index.ViewIdData
import com.android.tools.idea.databinding.module.LayoutBindingModuleCache
import com.android.tools.idea.databinding.util.findVariableTag
import com.android.tools.idea.databinding.util.getViewBindingClassName
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.xml.XmlTag
import com.intellij.util.application
import org.jetbrains.android.facet.AndroidFacet

/** All values needed to generate a specific [LightBindingClass] */
interface LightBindingClassConfig {
  val facet: AndroidFacet

  val targetLayout: BindingLayout

  /** The fully-qualified name of the class which this class inherits from. */
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

  /** The (unqualified) view type that this binding's `getRoot` method should return. */
  val rootType: String

  /**
   * A list of all `<variable>` tags paired with their corresponding PSI XML tag.
   *
   * Note: Even if a binding is being generated for a target layout, its API may still include
   * variables from alternate layouts in the group. This is why we need to return the XML tags
   * explicitly, as they may refer to a layout configuration outside of our own.
   */
  val variableTags: List<Pair<VariableData, XmlTag>>

  /**
   * Returns a list of all views with IDs that fields should be created for, e.g. `<Button
   * android:id="@+id/example_button">` -> "exampleButton", scoped by the binding layout they were
   * defined in.
   *
   * Note: These fields should only get generated for the base "Binding" class.
   */
  val scopedViewIds: Map<BindingLayout, Collection<ViewIdData>>

  /**
   * Returns `true` if the generated light binding class should have a full method API, e.g. getters
   * for its `<variable>` fields and other static methods.
   */
  fun shouldGenerateGettersAndStaticMethods(): Boolean

  /**
   * Returns `true` if we generate setters for a base class that should delegate to child
   * implementation classes.
   */
  fun settersShouldBeAbstract(): Boolean
}

private fun BindingLayoutGroup.getAggregatedVariables(
  project: Project
): List<Pair<VariableData, XmlTag>> {
  val aggregatedVariables = mutableListOf<Pair<VariableData, XmlTag>>()
  val alreadySeen = mutableSetOf<String>()
  for (layout in layouts) {
    val xmlFile = layout.toXmlFile()
    if (xmlFile == null) {
      thisLogger()
        .error(
          "getAggregatedVariables: Binding layout should always be backed by an xml file. " +
            "Dumb mode: ${DumbService.isDumb(project)}. Layout file: ${layout.file.name}"
        )
      continue
    }
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
 * A "Binding" class should always be created. "BindingImpl"s should only be created if there are
 * multiple layout configurations.
 */
data class BindingClassConfig(
  override val facet: AndroidFacet,
  private val group: BindingLayoutGroup,
) : LightBindingClassConfig {
  override val targetLayout: BindingLayout
    get() = group.mainLayout

  override val superName: String
    get() {
      // Main layout generates a binding class that inherits from the ViewDataBinding classes.
      return if (targetLayout.data.layoutType === DATA_BINDING_LAYOUT) {
        LayoutBindingModuleCache.getInstance(facet).dataBindingMode.viewDataBinding
      } else {
        facet.module.project.getViewBindingClassName()
      }
    }

  override val className = group.mainLayout.className
  override val qualifiedName = group.mainLayout.qualifiedClassName

  /**
   * Returns the specialized root type if set consistently across all layout configurations, e.g.
   * "LinearLayout", or "View" otherwise.
   */
  override val rootType: String
    get() {
      val mainTag = group.mainLayout.data.rootTag
      if (group.layouts.all { it.data.rootTag == mainTag }) {
        return mainTag
      } else {
        return SdkConstants.VIEW
      }
    }

  override val variableTags: List<Pair<VariableData, XmlTag>>
    get() = group.getAggregatedVariables(facet.module.project)

  override val scopedViewIds: Map<BindingLayout, Collection<ViewIdData>>
    get() {
      if (DumbService.isDumb(facet.module.project)) {
        thisLogger().info("scopedViewIds: called while in dumb mode")
      }
      if (!application.isReadAccessAllowed) {
        thisLogger().error("scopedViewIds: called without read access")
      }

      val viewIds = mutableMapOf<BindingLayout, Collection<ViewIdData>>()
      for (layout in group.layouts) {
        val xmlFile = layout.toXmlFile()
        if (xmlFile == null) {
          thisLogger()
            .info(
              "scopedViewIds: Binding layout should always be backed by an xml file. " +
                "Dumb mode: ${DumbService.isDumb(facet.module.project)}. " +
                "Qualified name: $qualifiedName. Layout file: ${layout.file.name}"
            )
          continue
        }
        val xmlData = BindingXmlIndex.getDataForFile(xmlFile)
        if (xmlData == null) {
          thisLogger()
            .info(
              "scopedViewIds: Every binding layout should have indexed data. " +
                "Dumb mode: ${DumbService.isDumb(facet.module.project)}. " +
                "Qualified name: $qualifiedName. Layout file: ${layout.file.name}"
            )
          continue
        }
        viewIds[layout] = xmlData.viewIds
      }
      return viewIds
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
data class BindingImplClassConfig(
  override val facet: AndroidFacet,
  private val group: BindingLayoutGroup,
  private val layoutIndex: Int,
) : LightBindingClassConfig {
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
  override val rootType = targetLayout.data.rootTag

  override val variableTags: List<Pair<VariableData, XmlTag>>
    get() = group.getAggregatedVariables(facet.module.project)

  override val scopedViewIds: Map<BindingLayout, Collection<ViewIdData>>
    get() = mapOf() // Only provided by base "Binding" class.

  override fun shouldGenerateGettersAndStaticMethods() = false

  override fun settersShouldBeAbstract() = false
}

/**
 * Implementation of [LightBindingClassConfig] that requires all data to be known at the time of
 * construction.
 *
 * This is being used to address b/330744400. The hope is that by evaluating this data at
 * construction, the contained data will never be out of date or fetched at the wrong time (ie,
 * during indexing). If this works as intended, this class can be removed and the other config
 * implementations can be updated to similarly evaluate their data on construction.
 */
data class EagerLightBindingClassConfig(
  override val facet: AndroidFacet,
  override val targetLayout: BindingLayout,
  override val superName: String,
  override val className: String,
  override val qualifiedName: String,
  override val rootType: String,
  override val variableTags: List<Pair<VariableData, XmlTag>>,
  override val scopedViewIds: Map<BindingLayout, Collection<ViewIdData>>,
  private val shouldGenerateGettersAndStaticMethods: Boolean,
  private val settersShouldBeAbstract: Boolean,
) : LightBindingClassConfig {

  /** Creates a [LightBindingClassConfig] by eagerly evaluating another config class's data. */
  constructor(
    other: LightBindingClassConfig
  ) : this(
    other.facet,
    other.targetLayout,
    other.superName,
    other.className,
    other.qualifiedName,
    other.rootType,
    other.variableTags,
    other.scopedViewIds,
    other.shouldGenerateGettersAndStaticMethods(),
    other.settersShouldBeAbstract(),
  )

  override fun shouldGenerateGettersAndStaticMethods() = shouldGenerateGettersAndStaticMethods

  override fun settersShouldBeAbstract() = settersShouldBeAbstract
}
