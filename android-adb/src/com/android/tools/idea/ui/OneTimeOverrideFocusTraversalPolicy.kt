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
package com.android.tools.idea.ui

import java.awt.Component
import java.awt.Container
import java.awt.FocusTraversalPolicy
import java.util.concurrent.atomic.AtomicReference

/**
 * A [FocusTraversalPolicy] that allows overriding each method with a "one time" only [Component]
 *
 * This class is useful when an existing [FocusTraversalPolicy] must be customized temporarily in a
 * very specific context. The "one time" behavior is useful to ensure the likelihood of infinite
 * recursion with the [delegate] is small.
 */
class OneTimeOverrideFocusTraversalPolicy(private val delegate: FocusTraversalPolicy?) :
  FocusTraversalPolicy() {
  val oneTimeComponentAfter = AtomicReference<Component>()
  val oneTimeComponentBefore = AtomicReference<Component>()
  val oneTimeFirstComponent = AtomicReference<Component>()
  val oneTimeLastComponent = AtomicReference<Component>()
  val oneTimeDefaultComponent = AtomicReference<Component>()

  override fun getComponentAfter(aContainer: Container?, aComponent: Component?): Component? {
    return handleOverride(oneTimeComponentAfter) {
      delegate?.getComponentAfter(aContainer, aComponent)
    }
  }

  override fun getComponentBefore(aContainer: Container?, aComponent: Component?): Component? {
    return handleOverride(oneTimeComponentBefore) {
      delegate?.getComponentBefore(aContainer, aComponent)
    }
  }

  override fun getFirstComponent(aContainer: Container?): Component? {
    return handleOverride(oneTimeFirstComponent) { delegate?.getFirstComponent(aContainer) }
  }

  override fun getLastComponent(aContainer: Container?): Component? {
    return handleOverride(oneTimeLastComponent) { delegate?.getLastComponent(aContainer) }
  }

  override fun getDefaultComponent(aContainer: Container?): Component? {
    return handleOverride(oneTimeDefaultComponent) { delegate?.getDefaultComponent(aContainer) }
  }

  private fun handleOverride(
    override: AtomicReference<Component>,
    default: () -> Component?,
  ): Component? {
    val overrideTo = override.getAndSet(null)
    return overrideTo ?: default()
  }

  companion object {
    fun install(component: Container): OneTimeOverrideFocusTraversalPolicy {
      val root = findRoot(component)
      val existingPolicy: FocusTraversalPolicy? = root.focusTraversalPolicy
      val newPolicy = OneTimeOverrideFocusTraversalPolicy(existingPolicy)
      root.focusTraversalPolicy = newPolicy
      return newPolicy
    }

    private fun findRoot(component: Container): Container {
      var candidate = component
      while (true) {
        if (candidate.isFocusCycleRoot || candidate.isFocusTraversalPolicyProvider) {
          return candidate
        }
        candidate = candidate.parent ?: break
      }
      return component
    }
  }
}
