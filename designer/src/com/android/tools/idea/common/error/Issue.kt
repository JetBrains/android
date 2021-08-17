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
package com.android.tools.idea.common.error

import com.android.tools.idea.common.command.NlWriteCommandActionUtil
import com.android.tools.idea.common.model.NlAttributesHolder
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurface
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.pom.Navigatable
import java.util.stream.Stream
import javax.swing.event.HyperlinkListener

data class NlComponentIssueSource(val component: NlComponent) : IssueSource, NlAttributesHolder {
  override val displayText: String = listOfNotNull(
    component.model.modelDisplayName,
    component.id,
    "<${component.tagName}>").joinToString(" ")
  override val onIssueSelected: (DesignSurface) -> Unit = {
    it.selectionModel.setSelection(listOf(component))

    // Navigate to the selected element if possible
    val element = component.backend.tag?.navigationElement
    if (element is Navigatable && PsiNavigationSupport.getInstance().canNavigate(element)) {
      (element as Navigatable).navigate(false)
    }
  }

  override fun getAttribute(namespace: String?, attribute: String): String? {
    return component.getAttribute(namespace, attribute)
  }

  override fun setAttribute(namespace: String?, attribute: String, value: String?) {
    NlWriteCommandActionUtil.run(component, "Update issue source") {
      component.setAttribute(namespace, attribute, value)
    }
  }
}

fun IssueSource.isFromNlComponent(component: NlComponent): Boolean {
    return this is NlComponentIssueSource && this.component == component
}

private data class NlModelIssueSource(private val model: NlModel) : IssueSource {
  override val displayText: String = model.modelDisplayName.orEmpty()
  override val onIssueSelected: (DesignSurface) -> Unit = {}
}

/**
 * Interface that represents the source for a given [Issue].
 */
interface IssueSource {
  /** The display text to show in the issue panel. */
  val displayText: String
  /** Handler for the action when an Issue with this source is selected. */
  val onIssueSelected: (DesignSurface) -> Unit

  companion object {
    @JvmField
    val NONE = object : IssueSource {
      override val displayText: String = ""
      override val onIssueSelected: (DesignSurface) -> Unit = {}

      override fun equals(other: Any?): Boolean = other === this
    }

    @JvmStatic
    fun fromNlComponent(component: NlComponent): IssueSource = NlComponentIssueSource(component)

    @JvmStatic
    fun fromNlModel(model: NlModel): IssueSource = NlModelIssueSource(model)
  }
}

/**
 * Represent an Error that can be displayed in the [IssuePanel].
 */
abstract class Issue {
  /** A short summary of the error description */
  abstract val summary: String

  /** The description of the error. It can contains some HTML tag */
  abstract val description: String

  abstract val severity: HighlightSeverity

  /** An indication of the origin of the error like the Component causing the error. */
  abstract val source: IssueSource

  /** The priority between 1 and 10. */
  abstract val category: String

  /** Allows the [Issue] to return an HyperlinkListener to handle embedded links */
  open val hyperlinkListener: HyperlinkListener? = null

  /**
   * Returns a Steam of pair containing the description of the fix as the first element
   * and a [Runnable] to execute the fix
   */
  open val fixes: Stream<Fix>
    get() = Stream.empty()

  override fun equals(other: Any?): Boolean {
    if (other === this) return true
    if (other !is Issue) return false
    return other.severity == severity
           && other.summary == summary
           && other.description == description
           && other.category == category
           && other.source == source
  }

  override fun hashCode(): Int {
    var result = 13
    result += 17 * severity.hashCode()
    result += 19 * summary.hashCode()
    result += 23 * description.hashCode()
    result += 29 * category.hashCode()
    result += 31 * source.hashCode()
    return result
  }

  /**
   * Representation of a quick fix for the issue.
   * @param description Description of the fix
   * @param runnable    Action to execute the fix
   */
  data class Fix(val buttonText: String = "Fix", val description: String, val runnable: Runnable)

  companion object {
    const val EXECUTE_FIX = "Execute Fix: "
  }
}