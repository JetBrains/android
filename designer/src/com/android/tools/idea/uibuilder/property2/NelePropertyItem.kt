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
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.ResourceResolver
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.adtui.model.stdui.EDITOR_NO_ERROR
import com.android.tools.adtui.model.stdui.EditingErrorCategory
import com.android.tools.adtui.model.stdui.EditingSupport
import com.android.tools.idea.common.command.NlWriteCommandAction
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.property2.api.ActionIconButton
import com.android.tools.idea.common.property2.api.PropertyItem
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.res.*
import com.android.tools.idea.uibuilder.property2.support.ColorSelectionAction
import com.android.tools.idea.uibuilder.property2.support.OpenResourceManagerAction
import com.android.tools.idea.uibuilder.property2.support.ToggleShowResolvedValueAction
import com.android.utils.HashCodes
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag
import com.intellij.util.text.nullize
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import org.jetbrains.android.dom.attrs.AttributeDefinition
import org.jetbrains.android.resourceManagers.ModuleResourceManagers
import java.awt.Color
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
) : PropertyItem {

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

  override val tooltipForName: String
    get() = computeTooltipForName()

  override val tooltipForValue: String
    get() = computeTooltipForValue()

  override val isReference: Boolean
    get() = isReferenceValue(rawValue)

  open val rawValue: String?
    get() = getCommonComponentValue()

  override val resolvedValue: String?
    get() = resolveValue(rawValue)

  // TODO: Use the namespace resolver in ResourceHelper when it no longer returns [ResourceNamespace.Resolver.TOOLS_ONLY].
  // We need to find the prefix even when namespacing is turned off.
  val namespaceResolver: ResourceNamespace.Resolver
    get() {
      val element = firstTag ?: return ResourceNamespace.Resolver.EMPTY_RESOLVER

      fun withTag(compute: (XmlTag) -> String?): String? {
        return ReadAction.compute<String, RuntimeException> {
          if (!element.isValid) {
            null
          }
          else {
            val tag = PsiTreeUtil.getParentOfType(element, XmlTag::class.java, false)
            tag?.let(compute).let(StringUtil::nullize)
          }
        }
      }

      return object : ResourceNamespace.Resolver {
        override fun uriToPrefix(namespaceUri: String): String? = withTag { tag -> tag.getPrefixByNamespace(namespaceUri) }
        override fun prefixToUri(namespacePrefix: String): String? = withTag { tag -> tag.getNamespaceByPrefix(namespacePrefix).nullize() }
      }
    }

  override val editingSupport = object : EditingSupport {
    override val completion = { getCompletionValues() }
    override val validation = { text: String -> validate(text) }
    override val execution = { runnable: Runnable -> ApplicationManager.getApplication().executeOnPooledThread(runnable) }
  }

  override val designProperty: NelePropertyItem
    get() = if (namespace == TOOLS_URI) this else NelePropertyItem(TOOLS_URI, name, type, definition, libraryName, model, components)

  override fun equals(other: Any?) =
    when (other) {
      is NelePropertyItem -> namespace == other.namespace && name == other.name
      else -> false
    }

  override fun hashCode() = HashCodes.mix(namespace.hashCode(), name.hashCode())

  private fun resolveValue(value: String?): String? {
    return resolveValueUsingResolver(value)
  }

  private fun resolveValueUsingResolver(value: String?): String? {
    if (value != null && !isReferenceValue(value)) return value
    val resValue = asResourceValue(value) ?: return null
    when (resValue.resourceType) {
      ResourceType.BOOL,
      ResourceType.DIMEN,
      ResourceType.FRACTION,
      ResourceType.ID,
      ResourceType.STYLE_ITEM,  // Hack for default values from LayoutLib
      ResourceType.INTEGER,
      ResourceType.STRING -> if (resValue.value != null) return resValue.value
      ResourceType.COLOR -> if (resValue.value?.startsWith("#") == true) return resValue.value
      else -> {}
    }
    // The value of the remaining resource types are file names, which we don't want to display.
    // Instead show the url of this resolved resource.
    return resValue.asReference().getRelativeResourceUrl(defaultNamespace, namespaceResolver).toString()
  }

  val resolver: ResourceResolver?
    get() = nlModel?.configuration?.resourceResolver

  val tagName: String
    get() {
      val tagName = firstComponent?.tagName ?: return ""
      for (component in components) {
        if (component.tagName != tagName) {
          return ""
        }
      }
      return tagName
    }

  protected open val firstComponent: NlComponent?
    get() = components.firstOrNull()

  private fun asResourceValue(value: String?): ResourceValue? {
    val resRef = when (value) {
      null -> {
        val defValue = model.provideDefaultValue(this)
        defValue?.reference ?: return defValue
      }
      else -> ResourceUrl.parse(value)?.resolve(defaultNamespace, namespaceResolver)
    } ?: return null

    if (resRef.resourceType == ResourceType.ATTR) {
      val resValue = resolver?.findItemInTheme(resRef) ?: return null
      return resolver?.resolveResValue(resValue)
    }
    else {
      return resolver?.getResolvedResource(resRef)
    }
  }

  private val project: Project
    get() = model.facet.module.project

  protected val firstTag: XmlTag?
    get() = firstComponent?.tag

  private val nlModel: NlModel?
    get() = firstComponent?.model

  private val defaultNamespace: ResourceNamespace
    get() = ReadAction.compute<ResourceNamespace, RuntimeException> { ResourceRepositoryManager.getOrCreateInstance(model.facet).namespace }

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
    if (project.isDisposed || components.isEmpty()) {
      return
    }
    val componentName = if (components.size == 1) components[0].tagName else "Multiple"

    TransactionGuard.submitTransaction(model, Runnable {
      NlWriteCommandAction.run(components, "Set $componentName.$name to $newValue") {
        components.forEach { it.setAttribute(namespace, name, newValue) }
        model.logPropertyValueChanged(this)
      }
    })
  }

  private fun computeTooltipForName(): String {
    val sb = StringBuilder(100)
    sb.append(findNamespacePrefix())
    sb.append(name)
    val value = definition?.getDescription(null) ?: ""
    if (value.isNotEmpty()) {
      sb.append(": ")
      sb.append(value)
    }
    return sb.toString()
  }

  private fun computeTooltipForValue(): String {
    val currentValue = rawValue
    val actualValue = rawValue ?: valueOf(model.provideDefaultValue(this))
    val computedResolvedValue = resolvedValue
    if (currentValue == computedResolvedValue) return ""
    val defaultText = if (currentValue == null) "[default] " else ""
    val keyStroke = KeymapUtil.getShortcutText(ToggleShowResolvedValueAction.SHORTCUT)
    val resolvedText = if (computedResolvedValue != actualValue) " = \"$computedResolvedValue\" ($keyStroke)" else ""
    return "$defaultText\"$actualValue\"$resolvedText"
  }

  private fun valueOf(value: ResourceValue?): String? {
    if (value == null) {
      return null
    }
    return value.reference?.getRelativeResourceUrl(defaultNamespace, namespaceResolver)?.toString() ?: value.value
  }

  private fun findNamespacePrefix(): String {
    val resolver = namespaceResolver
    // TODO: This should not be required, but it is for as long as getNamespaceResolver returns TOOLS_ONLY:
    if (resolver == ResourceNamespace.Resolver.TOOLS_ONLY && namespace == ANDROID_URI) return PREFIX_ANDROID
    val prefix = namespaceResolver.uriToPrefix(namespace) ?: return ""
    @Suppress("ConvertToStringTemplate")
    return prefix + ":"
  }

  protected open fun getCompletionValues(): List<String> {
    val values = mutableListOf<String>()
    if (definition != null && definition.values.isNotEmpty()) {
      values.addAll(definition.values)
    }
    val resourceManagers = ModuleResourceManagers.getInstance(model.facet)
    val localRepository = resourceManagers.localResourceManager.resourceRepository
    val frameworkRepository = resourceManagers.frameworkResourceManager?.resourceRepository
    val namespaceResolver = namespaceResolver
    val types = type.resourceTypes
    val toName = { item: ResourceItem -> item.referenceToSelf.getRelativeResourceUrl(defaultNamespace, namespaceResolver).toString() }
    if (types.isNotEmpty()) {
      for (type in types) {
        localRepository.getResources(defaultNamespace, type).values().filter { it.libraryName == null }.mapTo(values, toName)
      }
      for (type in types) {
        localRepository.getPublicResources(ResourceNamespace.TODO(), type).filter { it.libraryName != null }.mapTo(values, toName)
      }
      for (type in types) {
        frameworkRepository?.getPublicResources(ResourceNamespace.ANDROID, type)?.mapTo(values, toName)
      }
    }
    return values
  }

  // TODO: implement validate
  private fun validate(text: String): Pair<EditingErrorCategory, String> {
    return EDITOR_NO_ERROR
  }

  override val browseButton = createBrowseButton()

  override val colorButton = createColorButton()

  // region Implementation of browseButton

  private fun createBrowseButton(): ActionIconButton? {
    if (name == ATTR_ID || type.resourceTypes.isEmpty()) {
      return null
    }
    return BrowseActionIconButton()
  }

  private inner class BrowseActionIconButton: ActionIconButton {
    override val actionButtonFocusable
      get() = true

    override fun getActionIcon(focused: Boolean): Icon {
      val reference = isReferenceValue(rawValue)
      return when {
        reference && !focused -> StudioIcons.Common.PROPERTY_BOUND
        reference && focused -> StudioIcons.Common.PROPERTY_BOUND_FOCUS
        !reference && !focused -> StudioIcons.Common.PROPERTY_UNBOUND
        else -> StudioIcons.Common.PROPERTY_UNBOUND_FOCUS
      }
    }

    override val action: AnAction?
      get() = OpenResourceManagerAction(this@NelePropertyItem)
  }

  // endregion

  // region Implementation of colorButton

  private fun createColorButton(): ActionIconButton? {
    if (!type.resourceTypes.contains(ResourceType.COLOR)) {
      return null
    }
    return ColorActionIconButton()
  }

  private inner class ColorActionIconButton: ActionIconButton {
    override val actionButtonFocusable
      get() = true

    override fun getActionIcon(focused: Boolean): Icon {
      return resolveValueAsIcon() ?: return StudioIcons.LayoutEditor.Extras.PIPETTE
    }

    override val action: AnAction?
      get() {
        val color = resolveValueAsColor()
        return ColorSelectionAction(this@NelePropertyItem, color)
      }

      private fun resolveValueAsColor(): Color? {
        val value = rawValue
        if (value != null && !isReferenceValue(value)) {
          return parseColor(value)
        }
        val resValue = asResourceValue(value) ?: return null
        return resolver?.resolveColor(resValue, project)
      }

      private fun resolveValueAsIcon(): Icon? {
        val value = rawValue
        if (value != null && !isReferenceValue(value)) {
          val color = parseColor(value) ?: return null
          return JBUI.scale(ColorIcon(RESOURCE_ICON_SIZE, color, false))
        }
        val resValue = asResourceValue(value) ?: return null
        return resolver?.resolveAsIcon(resValue, project)
      }
    }

  // endregion
}
