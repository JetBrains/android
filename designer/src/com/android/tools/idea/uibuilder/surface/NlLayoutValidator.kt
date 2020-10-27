/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.surface

import android.view.View
import com.android.tools.idea.common.error.Issue
import com.android.tools.idea.common.error.IssueModel
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.rendering.RenderResult
import com.android.tools.idea.uibuilder.model.viewInfo
import com.android.tools.idea.validator.ValidatorData
import com.android.tools.idea.validator.ValidatorResult
import com.android.tools.pixelprobe.util.Lists
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Layout validator for [NlDesignSurface].
 * It retrieves validation results from the [RenderResult] and update the lint accordingly.
 */
class NlLayoutValidator(issueModel: IssueModel, parent: Disposable): Disposable {

  interface Listener {
    fun lintUpdated(result: ValidatorResult?)
  }

  /** Helper class for displaying output to lint system */
  private val lintIntegrator = AccessibilityLintIntegrator(issueModel)

  /**
   * Original map required for Accessibility Testing Framework.
   * This is used to link a11y lint output with the source [NlComponent].
   */
  private val originMap: BiMap<View, NlComponent> = HashBiMap.create()

  private val listeners: ArrayList<Listener> = ArrayList()

  init {
    Disposer.register(parent, this)
  }

  /**
   * Validate the layout and update the lint accordingly.
   */
  fun validateAndUpdateLint(renderResult: RenderResult, model: NlModel) {
    val validatorResult = renderResult.validatorResult

    if (validatorResult == null || validatorResult !is ValidatorResult) {
      // Result not available.
      listeners.forEach { it.lintUpdated(null) }
      return
    }

    lintIntegrator.disableAccessibilityLint()
    originMap.clear()
    val components = model.components
    if (components.isEmpty()) {
      listeners.forEach { it.lintUpdated(null) }
      return
    }

    val root = components[0]
    buildComponentToViewMap(root)
    validatorResult.issues.forEach { lintIntegrator.createIssue(it, findComponent(it, validatorResult.srcMap)) }
    lintIntegrator.populateLints()

    listeners.forEach { it.lintUpdated(validatorResult) }
  }

  fun addListener(listener: Listener) {
    listeners.add(listener)
  }

  /**
   * Disable the validator. It removes any existing issue visible to the panel.
   */
  fun disable() {
    lintIntegrator.disableAccessibilityLint()
  }

  /**
   * Find the source [NlComponent] based on issue. If no source is found it returns null.
   */
  private fun findComponent(result: ValidatorData.Issue, map: BiMap<Long, View>): NlComponent? {
    val view = map[result.mSrcId] ?: return null
    return originMap[view] ?: return null
  }

  /**
   * It's needed to build bridge from [Long] to [View] to [NlComponent].
   */
  private fun buildComponentToViewMap(component: NlComponent) {
    component.viewInfo?.viewObject?.let { viewObj ->
      val view = viewObj as View
      originMap[view] = component

      component.children.forEach { buildComponentToViewMap(it) }
    }
  }

  override fun dispose() {
    originMap.clear()
    listeners.clear()
    lintIntegrator.disableAccessibilityLint()
  }
}