/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property2

import com.android.SdkConstants.*
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ResourceResolver
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.idea.common.command.NlWriteCommandAction
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.property2.api.ActionButtonSupport
import com.android.tools.idea.common.property2.api.PropertyItem
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.res.getNamespaceResolver
import com.android.tools.idea.uibuilder.property2.support.OpenResourceManagerAction
import com.android.tools.idea.uibuilder.property2.support.ShowResolvedValueAction
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.TransactionGuard
import com.intellij.psi.xml.XmlTag
import icons.StudioIcons
import org.jetbrains.android.dom.attrs.AttributeDefinition
import java.util.*
import javax.swing.Icon

/**
 * [PropertyItem] for Nele layouts, menues, preferences.
 *
 * Enables editing of attributes from an XmlTag that is wrapped
 * in one or more [NlComponent]s. If there are multiple components
 * only common values are shown. Setting the [value] property
 * writes the value back to all components.
 *
 * Resolved values are computed using the [ResourceResolver] from
 * the current [Configuration]. If the user changes the current
 * configuration the properties panel should be updated with
 * potentially different resolved values.
 */
open class NelePropertyItem(
  override val namespace: String,
  override val name: String,
  val type: NelePropertyType,
  val definition: AttributeDefinition?,
  val libraryName: String,
  val model: NelePropertiesModel,
  val components: List<NlComponent>
) : PropertyItem, ActionButtonSupport {

  init {
    assert(components.isNotEmpty())
  }

  override var value: String?
    get() {
      val rawValue = rawValue
      return if (model.showResolvedValues) resolveValue(rawValue) else rawValue
    }
    set(value) {
      setCommonComponentValue(value)
    }

  override val namespaceIcon: Icon?
    get() = when (namespace) {
      "",
      ANDROID_URI,
      AUTO_URI -> null
      TOOLS_URI -> StudioIcons.LayoutEditor.Properties.DESIGN_PROPERTY
      else -> StudioIcons.LayoutEditor.Toolbar.INSERT_VERT_CHAIN
    }

  override val tooltip: String
    get() = computeToolTip()

  override val isReference: Boolean
    get() = isReferenceValue(rawValue)

  open val rawValue: String?
    get() = getCommonComponentValue()

  val resolvedValue: String?
    get() = resolveValue(rawValue)

  val namespaceResolver: ResourceNamespace.Resolver
    get() {
      val tag = getTagOfFirstComponent()
      return if (tag != null) getNamespaceResolver(tag) else ResourceNamespace.Resolver.EMPTY_RESOLVER
    }

  override val designProperty: NelePropertyItem
    get() = if (namespace == TOOLS_URI) this else NelePropertyItem(TOOLS_URI, name, type, definition, libraryName, model, components)

  override fun equals(other: Any?) =
    when (other) {
      is NelePropertyItem -> namespace == other.namespace && name == other.name && type == type
      else -> false
    }

  override fun hashCode() = Objects.hash(namespace, name, type)

  private fun resolveValue(value: String?): String? {
    return resolveValueUsingResolver(value ?: model.provideDefaultValue(this))
  }

  private fun resolveValueUsingResolver(value: String?): String? {
    if (value == null || !isReferenceValue(value)) return value
    val resolver = resolver ?: return value
    // TODO: Should an error if the value cannot be parsed and resolved...
    val url = ResourceUrl.parse(value) ?: return value
    val defaultNamespace = ResourceRepositoryManager.getOrCreateInstance(model.facet).namespace
    val resRef = url.resolve(defaultNamespace, namespaceResolver)
    val resValue = resolver.getResolvedResource(resRef) ?: return resolveFrameworkValueUsingResolver(value)
    return if (resValue.resourceType == ResourceType.FONT) resValue.name else resValue.value ?: value
  }

  // TODO: Namespaces. Remove this when the framework & layoutlib is properly using prefixes for framework references
  private fun resolveFrameworkValueUsingResolver(value: String): String {
    val resolver = resolver ?: return value
    val url = ResourceUrl.parse(value) ?: return value
    val resRef = url.resolve(ResourceNamespace.ANDROID, namespaceResolver)
    val resValue = resolver.getResolvedResource(resRef) ?: return value
    return if (resValue.resourceType == ResourceType.FONT) resValue.name else resValue.value ?: value
  }

  val resolver: ResourceResolver?
    get() {
      val configuration: Configuration = getNeleModel()?.configuration ?: return null
      return configuration.resourceResolver
    }

  val tagName: String
    get() {
      val tagName = getFirstComponent()?.tagName ?: return ""
      for (component in components) {
        if (component.tagName != tagName) {
          return ""
        }
      }
      return tagName
    }


  private fun getFirstComponent(): NlComponent? {
    return if (components.isNotEmpty()) components[0] else null
  }

  private fun getTagOfFirstComponent(): XmlTag? {
    return getFirstComponent()?.tag
  }

  private fun getNeleModel(): NlModel? {
    return getFirstComponent()?.model
  }

  private fun isReferenceValue(value: String?): Boolean {
    return value != null && (value.startsWith("?") || value.startsWith("@") && !isId(value))
  }

  private fun isId(value: String): Boolean {
    val url = ResourceUrl.parse(value)
    return url?.type == ResourceType.ID
  }

  private fun getCommonComponentValue(): String? {
    ApplicationManager.getApplication().assertIsDispatchThread()
    var prev: String? = null
    for (component in components) {
      val value = component.getAttribute(namespace, name) ?: return null
      prev = prev ?: value
      if (value != prev) return null
    }
    return prev
  }

  private fun setCommonComponentValue(newValue: String?) {
    assert(ApplicationManager.getApplication().isDispatchThread)
    if (model.facet.module.project.isDisposed) {
      return
    }
    val componentName = if (components.size == 1) components[0].tagName else "Multiple"

    TransactionGuard.submitTransaction(model, Runnable {
      NlWriteCommandAction.run(components, "Set $componentName.$name to $newValue") {
        components.forEach { it.setAttribute(namespace, name, newValue) }
        model.propertyValueChanged(this)
      }
    })
  }

  private fun computeToolTip(): String {
    val sb = StringBuilder(100)
    sb.append(findNamespacePrefix())
    sb.append(name)
    val value = definition?.getDocValue(null) ?: ""
    if (value.isNotEmpty()) {
      sb.append(": ")
      sb.append(value)
    }
    return sb.toString()
  }

  private fun findNamespacePrefix(): String {
    val resolver = namespaceResolver
    // TODO: This should not be required, but it is for as long as getNamespaceResolver returns TOOLS_ONLY:
    if (resolver == ResourceNamespace.Resolver.TOOLS_ONLY && namespace == ANDROID_URI) return PREFIX_ANDROID
    val prefix = namespaceResolver.uriToPrefix(namespace) ?: return ""
    @Suppress("ConvertToStringTemplate")
    return prefix + ":"
  }

  // region Implementation of ActionButtonSupport

  override val showActionButton: Boolean
    get() = type.resourceTypes.isNotEmpty() && name != ATTR_ID

  override val actionButtonFocusable: Boolean
    get() = true

  override fun getActionIcon(focused: Boolean): Icon {
    val reference = isReferenceValue(value)
    return when {
      reference && !focused -> StudioIcons.Common.PROPERTY_BOUND
      reference && focused -> StudioIcons.Common.PROPERTY_BOUND_FOCUS
      !reference && !focused -> StudioIcons.Common.PROPERTY_UNBOUND
      else -> StudioIcons.Common.PROPERTY_UNBOUND_FOCUS
    }
  }

  override fun getAction(): ActionGroup {
    return if (isReferenceValue(value)) makeBoundActionGroup() else makeUnboundActionGroup()
  }

  private fun makeBoundActionGroup(): ActionGroup {
    val group = DefaultActionGroup()
    group.add(ShowResolvedValueAction(this))
    group.addSeparator()
    group.add(OpenResourceManagerAction(this))
    return group
  }

  private fun makeUnboundActionGroup(): ActionGroup {
    val group = DefaultActionGroup()
    group.add(OpenResourceManagerAction(this))
    return group
  }
  // endregion
}
