/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.actions.annotations

import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.JBUI
import javax.swing.JCheckBox
import javax.swing.JPanel
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.declaredMemberProperties

/**
 * Persistent settings for the annotation inference.
 *
 * Other potential settings:
 * * Number of passes through the source code (currently
 *   [InferAnnotationsAction.Companion.MAX_PASSES])
 * * Whether to flow inference from parameter to call (fun m(@Annotation p:
 *   Int), m(a) => @Annotation on a)
 * * Whether to flow inference from call to parameter (fun m(p: Int),
 *   @Annotation var a: Int; m(a) => @Annotation on p)
 */
class InferAnnotationsSettings {
  /**
   * Whether to open up a report listing all the inferences along with their
   * causes
   */
  var generateReport = true

  /**
   * Whether to include in the report inferences made in code outside of the
   * project, such as in libraries. These cannot be inserted as annotations,
   * but it might be useful to include the *reasons* in case these inferences
   * flow into other constraints in the code.
   */
  var includeBinaries = false

  /** Whether to only suggest annotations for public elements */
  var publicOnly = false

  /**
   * Whether to analyze the module dependencies and add the annotations
   * artifact to any modules missing it.
   */
  var checkDependencies = false

  /** Whether to annotate local variables too */
  var annotateLocalVariables = false

  /**
   * Whether we're going to be aggressive or optimistic nad take vague clues
   * and turn them into annotations, or whether we're going to be careful and
   * only make suggestions we're certain of.
   */
  var optimistic = true

  /**
   * Whether to make inferences around resource types, e.g. a return value of
   * `R.string.foo` would imply adding `@StringRes`.
   */
  var resources = true

  /**
   * Whether to look for reflection calls and for APIs accessed by
   * reflection, add `@Keep`.
   */
  var reflection = true

  /**
   * Whether to look for permission checks and if found, add
   * `@RequiresPermission`.
   */
  var permissions = true

  /**
   * Whether to look for permission checks and if found, add `@IntDef` or
   * `StringDef.
   */
  var typedefs = true

  /** Whether to look for ranges, and if found, add `@IntRange` or `Size`. */
  var ranges = true

  /** Whether to look for threading annotations or implied constraints. */
  var threads = true

  /**
   * Whether for Kotlin we'll add the annotation to *all* applicable
   * annotation use sites instead of just the getter.
   */
  var allUsageSites = false

  /**
   * Whether we inherit annotations - e.g. from corresponding parameter and
   * return annotations on super methods
   */
  var inherit = true

  /**
   * Whether to look for @hide markers in the javadocs and skip annotation
   * generation from hidden APIs. This is primarily used when this action is
   * invoked on the framework itself.
   */
  var filterHidden = true

  override fun toString(): String {
    val defaultValues = InferAnnotationsSettings()
    return InferAnnotationsSettings::class.declaredMemberProperties
      .filter { it.get(this) != it.get(defaultValues) }
      .joinToString(",") { it.name + "=" + it.get(this) }
  }

  fun apply(string: String) {
    if (string.isNotEmpty()) {
      val properties = InferAnnotationsSettings::class.declaredMemberProperties
      string.split(",").forEach { assignment ->
        val key = assignment.substringBefore('=')
        val value = assignment.substringAfter('=').toBoolean()
        val property = properties.firstOrNull { it.name == key }
        @Suppress("UNCHECKED_CAST")
        (property as? KMutableProperty1<InferAnnotationsSettings, Boolean>)?.set(this, value)
      }
    }
  }

  inner class SettingsPanel : JPanel(VerticalFlowLayout()) {
    private val checkDependenciesCb: JCheckBox = JBCheckBox("Ensure all modules depend on androidx.annotations", checkDependencies)
    private val generateReportCb: JCheckBox = JBCheckBox("Generate report", generateReport)
    private val includeBinariesCb: JCheckBox = JBCheckBox("Include inferences made in code outside the project", includeBinaries)
    private val publicOnlyCb: JCheckBox = JBCheckBox("Only offer suggestions on public APIs", publicOnly)
    private val annotateLocalVariablesCb: JCheckBox = JBCheckBox("Annotate local variables", annotateLocalVariables)
    private val inheritCb: JCheckBox = JBCheckBox("Inherit annotations from overridden methods", inherit)
    private val aggressiveCb: JCheckBox = JBCheckBox("Optimistic/Aggressive", optimistic)
    private val resourcesCb: JCheckBox = JBCheckBox("Infer resources", resources)
    private val permissionsCb: JCheckBox = JBCheckBox("Infer permissions", permissions)
    private val typedefsCb: JCheckBox = JBCheckBox("Infer type aliases (IntDef/StringDef)", typedefs)
    private val rangesCb: JCheckBox = JBCheckBox("Infer ranges", ranges)
    private val threadsCb: JCheckBox = JBCheckBox("Infer threading constraints", threads)

    init {

      add(TitledSeparator())
      add(checkDependenciesCb)
      add(generateReportCb)
      includeBinariesCb.border = JBUI.Borders.emptyLeft(20)
      add(includeBinariesCb) // indented: place right after generateReportCb
      add(publicOnlyCb)
      add(annotateLocalVariablesCb)
      add(inheritCb)
      // Not yet implemented: don't offer
      // add(aggressiveCb)
      add(resourcesCb)
      add(permissionsCb)
      // Not yet implemented: don't offer
      // add(typedefsCb)
      add(rangesCb)
      add(threadsCb)
    }

    fun apply() {
      checkDependencies = checkDependenciesCb.isSelected
      generateReport = generateReportCb.isSelected
      includeBinaries = includeBinariesCb.isSelected
      publicOnly = publicOnlyCb.isSelected
      annotateLocalVariables = annotateLocalVariablesCb.isSelected
      inherit = inheritCb.isSelected
      optimistic = aggressiveCb.isSelected
      resources = resourcesCb.isSelected
      permissions = permissionsCb.isSelected
      typedefs = typedefsCb.isSelected
      ranges = rangesCb.isSelected
      threads = threadsCb.isSelected
    }
  }
}