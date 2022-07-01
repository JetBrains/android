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
package com.android.tools.idea.uibuilder.visual.visuallint

import com.android.SdkConstants
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.uibuilder.visual.analytics.VisualLintUsageTracker
import com.intellij.openapi.command.WriteCommandAction

/**
 * Suppress the issue associated with the given [components]. Note that this doesn't suppress all the files under same qualifiers.
 * TODO: Have two different suppress tasks for: (1) a single file, (2) all variant files.
 *       We also needs another project-level suppress task in the future.
 */
class VisualLintSuppressTask(private val typeToSuppress: VisualLintErrorType, private val components: List<NlComponent>) : Runnable {
  override fun run() {
    val attributeToAdd = typeToSuppress.ignoredAttributeValue
    val transactions = components.mapNotNull { component ->
      // First we check if tools:ignored="" attribute already exists.
      val newIgnoreAttribute: String
      val existIgnored = component.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_IGNORE)
      newIgnoreAttribute = if (existIgnored != null) {
        // It may ignore multiple things by using comma as separator already.
        val ignores = existIgnored.split(",")
        if (ignores.contains(attributeToAdd)) {
          // This model has been suppressed by this type already. Ignore it.
          return@mapNotNull null
        }
        "$existIgnored,$attributeToAdd"
      }
      else {
        attributeToAdd
      }
      component.startAttributeTransaction().apply { setAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_IGNORE, newIgnoreAttribute) }
    }

    if (transactions.isNotEmpty()) {
      val project = transactions.first().component.model.project
      val files = transactions.map { it.component.model.file }.toTypedArray()
      VisualLintUsageTracker.getInstance().trackIssueIgnored(typeToSuppress, transactions.first().component.model.facet)
      // All suppresses should in the same undo/redo action.
      WriteCommandAction.runWriteCommandAction(project, "Suppress: " + typeToSuppress.toSuppressActionDescription(), null, {
        transactions.forEach { it.commit() }
      }, *files)
    }
  }
}
