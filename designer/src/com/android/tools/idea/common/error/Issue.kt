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
import com.android.tools.idea.uibuilder.lint.createDefaultHyperLinkListener
import com.intellij.icons.AllIcons
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.vfs.VirtualFile
import java.lang.ref.WeakReference
import java.util.Objects
import java.util.stream.Stream
import javax.swing.Icon
import javax.swing.event.HyperlinkListener

class NlComponentIssueSource(component: NlComponent) : IssueSource, NlAttributesHolder {
  private val componentRef = WeakReference(component)

  @Suppress("RedundantNullableReturnType") // May be null when using mocked NlModel in the test environment.
  override val file: VirtualFile? = component.model.virtualFile
  override val displayText: String = listOfNotNull(
    component.model.modelDisplayName,
    component.id,
    "<${component.tagName}>").joinToString(" ")

  val component: NlComponent?
    get() = componentRef.get()

  override fun getAttribute(namespace: String?, attribute: String): String? {
    return component?.getAttribute(namespace, attribute)
  }

  override fun setAttribute(namespace: String?, attribute: String, value: String?) {
    component?.let { component ->
      NlWriteCommandActionUtil.run(component, "Update issue source") {
        component.setAttribute(namespace, attribute, value)
      }
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is NlComponentIssueSource) return false

    val component = componentRef.get() ?: return false
    val otherComponent = other.componentRef.get() ?: return false
    if (component != otherComponent) return false
    if (file != other.file) return false
    if (displayText != other.displayText) return false

    return true
  }

  override fun hashCode(): Int =
    Objects.hash(
      component,
      file,
      displayText
    )
}

/**
 * Interface that represents the source for a given [Issue].
 */
interface IssueSource {
  val file: VirtualFile?
  /** The display text to show in the issue panel. */
  val displayText: String

  companion object {
    @JvmField
    val NONE = object : IssueSource {
      override val file: VirtualFile? = null
      override val displayText: String = ""

      override fun equals(other: Any?): Boolean = other === this
    }

    @JvmStatic
    fun fromNlComponent(component: NlComponent): IssueSource = NlComponentIssueSource(component)

    @JvmStatic
    fun fromNlModel(model: NlModel): IssueSource = object: IssueSource {
      @Suppress("RedundantNullableReturnType") // May be null when using mocked NlModel in the test environment.
      override val file: VirtualFile? = model.virtualFile
      override val displayText: String = model.modelDisplayName.orEmpty()

      override fun equals(other: Any?): Boolean =
        other is IssueSource &&
        Objects.equals(file, other.file) &&
        Objects.equals(displayText, other.displayText)

      override fun hashCode(): Int =
        Objects.hash(
          file,
          displayText
        )
    }
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
  open val hyperlinkListener: HyperlinkListener? = createDefaultHyperLinkListener()

  /**
   * Returns a Steam of pair containing the description of the fix as the first element
   * and a [Runnable] to execute the fix
   */
  open val fixes: Stream<Fix>
    get() = Stream.empty()

  open val suppresses: Stream<Suppress>
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
   * Representation of the quick fix action (includes both fix and suppress) for the issues.
   */
  interface QuickFixable {
    val icon: Icon?
    val buttonText: String
    val description: String
    val action: Runnable
  }

  /**
   * Representation of a fix action for the issue.
   */
  data class Fix(override val buttonText: String = "Fix", override val description: String, override val action: Runnable) : QuickFixable {
    override val icon = AllIcons.Actions.RealIntentionBulb
  }

  /**
   * Representation of a suppress action for the issue.
   */
  data class Suppress(override val buttonText: String, override val description: String, override val action: Runnable) : QuickFixable {
    override val icon = AllIcons.Actions.Cancel
  }

  companion object {
    const val EXECUTE_FIX = "Execute Fix: "
    const val EXECUTE_SUPPRESSION = "Execute Suppression: "
  }
}