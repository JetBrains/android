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
package org.jetbrains.android.dom

import com.android.AndroidXConstants
import com.android.SdkConstants
import com.android.ide.common.rendering.api.AttributeFormat
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.repository.GoogleMavenArtifactId
import com.android.ide.common.resources.ResourceItem
import com.android.resources.ResourceType
import com.android.tools.dom.attrs.AttributeDefinition
import com.android.tools.dom.attrs.AttributeDefinitions
import com.android.tools.dom.attrs.StyleableDefinition
import com.android.tools.idea.AndroidTextUtils
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.psi.TagToClassMapper
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.util.dependsOn
import com.google.common.collect.ImmutableSet
import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbService.isDumb
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.xml.XmlTag
import com.intellij.util.xml.Converter
import com.intellij.util.xml.DomElement
import com.intellij.util.xml.XmlName
import com.intellij.util.xml.reflect.DomExtension
import org.jetbrains.android.dom.animation.InterpolatorElement
import org.jetbrains.android.dom.animation.fileDescriptions.InterpolatorDomFileDescription
import org.jetbrains.android.dom.attrs.ToolsAttributeUtil
import org.jetbrains.android.dom.converters.CompositeConverter
import org.jetbrains.android.dom.converters.ManifestPlaceholderConverter
import org.jetbrains.android.dom.converters.ResourceReferenceConverter
import org.jetbrains.android.dom.layout.Data
import org.jetbrains.android.dom.layout.DataBindingElement
import org.jetbrains.android.dom.layout.Fragment
import org.jetbrains.android.dom.layout.LayoutElement
import org.jetbrains.android.dom.layout.LayoutViewElement
import org.jetbrains.android.dom.layout.LayoutViewElementDescriptor
import org.jetbrains.android.dom.layout.Tag
import org.jetbrains.android.dom.manifest.Manifest
import org.jetbrains.android.dom.manifest.ManifestElement
import org.jetbrains.android.dom.manifest.UsesSdk
import org.jetbrains.android.dom.manifest.isRequiredAttribute
import org.jetbrains.android.dom.menu.MenuItem
import org.jetbrains.android.dom.navigation.NavDestinationElement
import org.jetbrains.android.dom.navigation.NavigationSchema
import org.jetbrains.android.dom.raw.XmlRawResourceElement
import org.jetbrains.android.dom.xml.AndroidXmlResourcesUtil
import org.jetbrains.android.dom.xml.AndroidXmlResourcesUtil.PreferenceSource.Companion.getPreferencesSource
import org.jetbrains.android.dom.xml.Intent
import org.jetbrains.android.dom.xml.XmlResourceElement
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.TagFromClassDescriptor
import org.jetbrains.android.facet.findViewClassByName
import org.jetbrains.android.facet.findViewValidInXMLByName
import org.jetbrains.android.resourceManagers.ModuleResourceManagers
import org.jetbrains.android.util.AndroidUtils

/**
 * Utility functions for enumerating available children attribute types in the context of a given
 * XML tag.
 *
 * Entry point is [.processAttributes], look for a Javadoc there.
 */
object AttributeProcessingUtil {
  private const val PREFERENCE_TAG_NAME = "Preference"
  private val SIZE_NOT_REQUIRED_TAG_NAMES =
    ImmutableSet.of(
      SdkConstants.VIEW_MERGE,
      SdkConstants.TABLE_ROW,
      SdkConstants.VIEW_INCLUDE,
      SdkConstants.REQUEST_FOCUS,
      SdkConstants.TAG_LAYOUT,
      SdkConstants.TAG_DATA,
      SdkConstants.TAG_IMPORT,
      SdkConstants.TAG
    )
  private val SIZE_NOT_REQUIRED_PARENT_TAG_NAMES =
    ImmutableSet.of(
      SdkConstants.TABLE_ROW,
      SdkConstants.TABLE_LAYOUT,
      SdkConstants.VIEW_MERGE,
      SdkConstants.GRID_LAYOUT,
      AndroidXConstants.FQCN_GRID_LAYOUT_V7.oldName(),
      AndroidXConstants.FQCN_GRID_LAYOUT_V7.newName(),
      SdkConstants.CLASS_PERCENT_RELATIVE_LAYOUT,
      SdkConstants.CLASS_PERCENT_FRAME_LAYOUT
    )
  private val log: Logger
    private get() = Logger.getInstance(AttributeProcessingUtil::class.java)

  /**
   * Check whether layout tag attribute with given name should be marked as required. Currently,
   * tests for layout_width and layout_height attribute and marks them as required in appropriate
   * context.
   */
  fun isLayoutAttributeRequired(attributeName: XmlName, element: DomElement): Boolean {
    // Mark layout_width and layout_height required - if the context calls for it
    val localName = attributeName.localName
    if (
      !(SdkConstants.ATTR_LAYOUT_WIDTH == localName || SdkConstants.ATTR_LAYOUT_HEIGHT == localName)
    ) {
      return false
    }
    if (
      (element is LayoutViewElement || element is Fragment) &&
        SdkConstants.ANDROID_URI == attributeName.namespaceKey
    ) {
      val xmlElement = element.xmlElement
      val tag = if (xmlElement is XmlTag) xmlElement else null
      val tagName = tag?.name
      if (
        !SIZE_NOT_REQUIRED_TAG_NAMES.contains(tagName) &&
          (tag == null || tag.getAttribute(SdkConstants.ATTR_STYLE) == null)
      ) {
        val parentTag = tag?.parentTag
        val parentTagName = parentTag?.name
        if (!SIZE_NOT_REQUIRED_PARENT_TAG_NAMES.contains(parentTagName)) {
          return true
        }
      }
    }
    return false
  }

  private fun getNamespaceUriByResourcePackage(facet: AndroidFacet, resPackage: String?): String? {
    if (resPackage == null) {
      if (!facet.configuration.isAppProject() || AndroidModel.isRequired(facet)) {
        return SdkConstants.AUTO_URI
      }
      val manifest = Manifest.getMainManifest(facet)
      if (manifest != null) {
        val aPackage = manifest.getPackage().value
        if (aPackage != null && !aPackage.isEmpty()) {
          return SdkConstants.URI_PREFIX + aPackage
        }
      }
    } else if (resPackage == AndroidUtils.SYSTEM_RESOURCE_PACKAGE) {
      return SdkConstants.ANDROID_URI
    }
    return null
  }

  private fun registerStyleableAttributes(
    element: DomElement,
    styleable: StyleableDefinition,
    namespaceUri: String?,
    callback: AttributeProcessor,
    skippedAttributes: MutableSet<XmlName>
  ) {
    for (attrDef in styleable.getAttributes()) {
      val xmlName = getXmlName(attrDef, namespaceUri)
      if (skippedAttributes.add(xmlName)) {
        registerAttribute(attrDef, xmlName, styleable.getName(), element, callback)
      }
    }
  }

  private fun mustBeSoft(converter: Converter<*>, formats: Collection<AttributeFormat>): Boolean {
    return if (converter is CompositeConverter || converter is ResourceReferenceConverter) {
      false
    } else formats.size > 1
  }

  private fun registerAttribute(
    attrDef: AttributeDefinition,
    xmlName: XmlName,
    parentStyleableName: String?,
    element: DomElement,
    callback: AttributeProcessor
  ) {
    val extension = callback.processAttribute(xmlName, attrDef, parentStyleableName) ?: return
    var converter = AndroidDomUtil.getSpecificConverter(xmlName, element)
    if (converter == null) {
      if (SdkConstants.TOOLS_URI == xmlName.namespaceKey) {
        converter = ToolsAttributeUtil.getConverter(attrDef)
      } else {
        converter = AndroidDomUtil.getConverter(attrDef)
        if (converter != null && element.getParentOfType(Manifest::class.java, true) != null) {
          converter = ManifestPlaceholderConverter(converter)
        }
      }
    }
    if (converter != null) {
      extension.setConverter(converter, mustBeSoft(converter, attrDef.formats))
    }

    // Check whether attribute is required. If it is, add an annotation to let
    // IntelliJ know about it so it would be, e.g. inserted automatically on
    // tag completion. If attribute is not required, no additional action is needed.
    if (
      element is LayoutElement && isLayoutAttributeRequired(xmlName, element) ||
        element is ManifestElement && isRequiredAttribute(xmlName, element)
    ) {
      extension.addCustomAnnotation(RequiredImpl())
    }
  }

  private fun registerAttributes(
    facet: AndroidFacet,
    element: DomElement,
    styleableName: String,
    resPackage: String?,
    callback: AttributeProcessor,
    skipNames: MutableSet<XmlName>
  ) {
    val manager = ModuleResourceManagers.getInstance(facet).getResourceManager(resPackage) ?: return
    val attrDefs = manager.getAttributeDefinitions() ?: return
    val namespace = getNamespaceUriByResourcePackage(facet, resPackage)
    val styleable = attrDefs.getStyleableByName(styleableName)
    if (styleable != null) {
      registerStyleableAttributes(element, styleable, namespace, callback, skipNames)
    }
    // It's a good idea to add a warning when styleable not found, to make sure that code doesn't
    // try to use attributes that don't exist. However, current AndroidDomExtender code relies on
    // a lot of "heuristics" that fail quite a lot (like adding a bunch of suffixes to short class
    // names)
    // TODO: add a warning when rest of the code of AndroidDomExtender is cleaned up
  }

  private fun registerAttributesForClassAndSuperclasses(
    facet: AndroidFacet,
    element: DomElement,
    c: PsiClass?,
    callback: AttributeProcessor,
    skipNames: MutableSet<XmlName>
  ) {
    var c = c
    while (c != null) {
      val styleableName = c.name
      if (styleableName != null) {
        registerAttributes(
          facet,
          element,
          styleableName,
          getResourcePackage(c),
          callback,
          skipNames
        )
      }
      val additional = getAdditionalAttributesClass(facet, c)
      if (additional != null) {
        val additionalStyleableName = additional.name
        if (additionalStyleableName != null) {
          registerAttributes(
            facet,
            element,
            additionalStyleableName,
            getResourcePackage(additional),
            callback,
            skipNames
          )
        }
      }
      c = getSuperclass(c)
    }
  }

  /**
   * Returns the class that holds attributes used in the specified class c. This is for classes from
   * support libraries without attrs.xml like support lib v4.
   */
  private fun getAdditionalAttributesClass(facet: AndroidFacet, c: PsiClass): PsiClass? {
    return if (
      AndroidXConstants.CLASS_NESTED_SCROLL_VIEW.isEquals(StringUtil.notNullize(c.qualifiedName))
    ) {
      findViewValidInXMLByName(facet, SdkConstants.SCROLL_VIEW)
    } else null
  }

  private fun getResourcePackage(psiClass: PsiClass): String? {
    // TODO: Replace this with the namespace of the styleableName when that is available.
    val qualifiedName = psiClass.qualifiedName
    return if (
      qualifiedName != null &&
        qualifiedName.startsWith(SdkConstants.ANDROID_PKG_PREFIX) &&
        !qualifiedName.startsWith(SdkConstants.ANDROID_SUPPORT_PKG_PREFIX) &&
        !qualifiedName.startsWith(SdkConstants.ANDROIDX_PKG_PREFIX) &&
        !qualifiedName.startsWith(SdkConstants.ANDROID_ARCH_PKG_PREFIX)
    )
      AndroidUtils.SYSTEM_RESOURCE_PACKAGE
    else null
  }

  private fun getSuperclass(c: PsiClass): PsiClass? {
    return ApplicationManager.getApplication()
      .runReadAction(Computable { if (c.isValid) c.superClass else null })
  }

  /** Yield attributes for resources in xml/ folder */
  fun processXmlAttributes(
    facet: AndroidFacet,
    tag: XmlTag,
    element: XmlResourceElement,
    skipAttrNames: MutableSet<XmlName>,
    callback: AttributeProcessor
  ) {
    val tagName = tag.name
    val styleableName = AndroidXmlResourcesUtil.SPECIAL_STYLEABLE_NAMES[tagName]
    if (styleableName != null) {
      val newSkipAttrNames: MutableSet<XmlName> = HashSet()
      if (element is Intent) {
        newSkipAttrNames.add(XmlName("action", SdkConstants.ANDROID_URI))
      }
      registerAttributes(
        facet,
        element,
        styleableName,
        AndroidUtils.SYSTEM_RESOURCE_PACKAGE,
        callback,
        newSkipAttrNames
      )
    }

    // Handle preferences:
    val prefClassMap: Map<String, PsiClass>
    val preferenceSource = getPreferencesSource(tag, facet)
    prefClassMap =
      TagToClassMapper.getInstance(facet.module).getClassMap(preferenceSource.qualifiedBaseClass)
    val psiClass = prefClassMap[tagName] ?: return

    // Register attributes by preference class:
    registerAttributesForClassAndSuperclasses(facet, element, psiClass, callback, skipAttrNames)
    if (StringUtil.notNullize(psiClass.qualifiedName).startsWith("android.preference.")) {
      // Register attributes from the corresponding widget. This was a convention used in framework
      // preferences, but no longer used in
      // AndroidX.
      val widgetClassName = AndroidTextUtils.trimEndOrNullize(tagName, PREFERENCE_TAG_NAME)
      if (widgetClassName != null) {
        val widgetClass = findViewValidInXMLByName(facet, widgetClassName)
        if (widgetClass != null) {
          registerAttributesForClassAndSuperclasses(
            facet,
            element,
            widgetClass,
            callback,
            skipAttrNames
          )
        }
      }
    }
  }

  /**
   * Returns the expected styleable name for the layout attributes defined by the specified PsiClass
   * of the layout.
   */
  fun getLayoutStyleablePrimary(psiLayoutClass: PsiClass): String? {
    val viewName = psiLayoutClass.name ?: return null
    return when (viewName) {
      SdkConstants.VIEW_GROUP -> "ViewGroup_MarginLayout"
      SdkConstants.TABLE_ROW -> "TableRow_Cell"
      else -> viewName + "_Layout"
    }
  }

  /**
   * Returns a styleable name that is mistakenly used for the layout attributes defined by the
   * specified PsiClass of the layout.
   */
  fun getLayoutStyleableSecondary(psiLayoutClass: PsiClass): String? {
    val viewName = psiLayoutClass.name ?: return null
    return viewName + "_LayoutParams"
  }

  private fun registerAttributesFromSuffixedStyleables(
    facet: AndroidFacet,
    element: DomElement,
    psiClass: PsiClass,
    callback: AttributeProcessor,
    skipAttrNames: MutableSet<XmlName>
  ) {
    val primary = getLayoutStyleablePrimary(psiClass)
    if (primary != null) {
      registerAttributes(
        facet,
        element,
        primary,
        getResourcePackage(psiClass),
        callback,
        skipAttrNames
      )
    }
    val secondary = getLayoutStyleableSecondary(psiClass)
    if (secondary != null) {
      registerAttributes(facet, element, secondary, null, callback, skipAttrNames)
    }
  }

  private fun registerAttributesFromSuffixedStyleables(
    facet: AndroidFacet,
    element: DomElement,
    callback: AttributeProcessor,
    skipAttrNames: MutableSet<XmlName>
  ) {
    registerAttributesFromSuffixedStyleablesForNamespace(
      facet,
      element,
      callback,
      skipAttrNames,
      ResourceNamespace.ANDROID
    )
    registerAttributesFromSuffixedStyleablesForNamespace(
      facet,
      element,
      callback,
      skipAttrNames,
      ResourceNamespace.RES_AUTO
    )
  }

  private fun registerAttributesFromSuffixedStyleablesForNamespace(
    facet: AndroidFacet,
    element: DomElement,
    callback: AttributeProcessor,
    skipAttrNames: MutableSet<XmlName>,
    resourceNamespace: ResourceNamespace
  ) {
    val repo =
      StudioResourceRepositoryManager.getInstance(facet).getResourcesForNamespace(resourceNamespace)
        ?: return

    // @see AttributeProcessingUtil.getLayoutStyleablePrimary and
    // AttributeProcessingUtil.getLayoutStyleableSecondary
    val layoutStyleablesPrimary =
      repo.getResources(resourceNamespace, ResourceType.STYLEABLE) { item: ResourceItem ->
        val name = item.getName()
        name.endsWith("_Layout") ||
          name.endsWith("_LayoutParams") ||
          name == "ViewGroup_MarginLayout" ||
          name == "TableRow_Cell"
      }
    for (item in layoutStyleablesPrimary) {
      val name = item.getName()
      val indexOfLastUnderscore = name.lastIndexOf('_')
      val viewName = name.substring(0, indexOfLastUnderscore)
      val psiClass = findViewClassByName(facet, viewName)
      if (psiClass != null) {
        registerAttributes(
          facet,
          element,
          name,
          getResourcePackage(psiClass),
          callback,
          skipAttrNames
        )
      }
    }
  }

  /** Entry point for XML elements in navigation XMLs. */
  fun processNavAttributes(
    facet: AndroidFacet,
    tag: XmlTag,
    element: NavDestinationElement,
    skipAttrNames: MutableSet<XmlName>,
    callback: AttributeProcessor
  ) {
    try {
      NavigationSchema.createIfNecessary(facet.module)
    } catch (e: ClassNotFoundException) {
      // The nav dependency wasn't added yet. Give up.
      return
    }
    val schema = NavigationSchema.get(facet.module)
    for (psiClass in schema.getStyleablesForTag(tag.name)) {
      registerAttributesForClassAndSuperclasses(facet, element, psiClass, callback, skipAttrNames)
    }
  }

  /** Entry point for XML elements in layout XMLs */
  @JvmStatic
  fun processLayoutAttributes(
    facet: AndroidFacet,
    tag: XmlTag,
    element: LayoutElement,
    skipAttrNames: MutableSet<XmlName>,
    callback: AttributeProcessor
  ) {

    // Add tools namespace attributes to layout tags, but not those that are databinding-specific
    // ones.
    if (element !is DataBindingElement) {
      registerToolsAttribute(SdkConstants.ATTR_TARGET_API, callback)
      registerToolsAttribute(SdkConstants.ATTR_IGNORE, callback)
      if (tag.parentTag == null) {
        registerToolsAttribute(SdkConstants.ATTR_CONTEXT, callback)
        registerToolsAttribute(SdkConstants.ATTR_MENU, callback)
        registerToolsAttribute(SdkConstants.ATTR_ACTION_BAR_NAV_MODE, callback)
        registerToolsAttribute(SdkConstants.ATTR_SHOW_IN, callback)
        registerToolsAttribute(SdkConstants.ATTR_VIEW_BINDING_IGNORE, callback)
      }
      val descriptor = tag.descriptor
      if (descriptor is LayoutViewElementDescriptor && descriptor.clazz != null) {
        val viewClass = descriptor.clazz
        registerToolsAttribute(SdkConstants.ATTR_VIEW_BINDING_TYPE, callback)
        if (InheritanceUtil.isInheritor(viewClass, SdkConstants.FQCN_ADAPTER_VIEW)) {
          registerToolsAttribute(SdkConstants.ATTR_LISTITEM, callback)
          registerToolsAttribute(SdkConstants.ATTR_LISTHEADER, callback)
          registerToolsAttribute(SdkConstants.ATTR_LISTFOOTER, callback)
        }
        if (
          InheritanceUtil.isInheritor(viewClass, AndroidXConstants.CLASS_DRAWER_LAYOUT.newName()) ||
            InheritanceUtil.isInheritor(viewClass, AndroidXConstants.CLASS_DRAWER_LAYOUT.oldName())
        ) {
          registerToolsAttribute(SdkConstants.ATTR_OPEN_DRAWER, callback)
        }
        if (
          InheritanceUtil.isInheritor(viewClass, AndroidXConstants.RECYCLER_VIEW.newName()) ||
            InheritanceUtil.isInheritor(viewClass, AndroidXConstants.RECYCLER_VIEW.oldName())
        ) {
          registerToolsAttribute(SdkConstants.ATTR_ITEM_COUNT, callback)
          registerToolsAttribute(SdkConstants.ATTR_LISTITEM, callback)
        }
      }
    }
    if (element is Tag || element is Data) {
      // don't want view attributes inside these tags
      return
    }
    val tagName = tag.name
    when (tagName) {
      SdkConstants.CLASS_COMPOSE_VIEW ->
        registerToolsAttribute(SdkConstants.ATTR_COMPOSABLE_NAME, callback)
      SdkConstants.VIEW_FRAGMENT -> registerToolsAttribute(SdkConstants.ATTR_LAYOUT, callback)
      SdkConstants.VIEW_TAG -> {
        // In Android layout XMLs, one can write, e.g.
        //   <view class="LinearLayout" />
        //
        // instead of
        //   <LinearLayout />
        //
        // In this case code adds styleables corresponding to the tag-value of "class" attributes
        //
        // See LayoutInflater#createViewFromTag in Android framework for inflating code
        val name = tag.getAttributeValue("class")
        if (name != null) {
          val aClass = findViewValidInXMLByName(facet, name)
          if (aClass != null) {
            registerAttributesForClassAndSuperclasses(
              facet,
              element,
              aClass,
              callback,
              skipAttrNames
            )
          }
        }
      }
      SdkConstants.VIEW_MERGE -> {
        if (tag.parentTag == null) {
          registerToolsAttribute(SdkConstants.ATTR_PARENT_TAG, callback)
        }
        registerAttributesForClassAndSuperclasses(
          facet,
          element,
          findViewValidInXMLByName(facet, SdkConstants.VIEW_MERGE),
          callback,
          skipAttrNames
        )
        val parentTagName =
          tag.getAttributeValue(SdkConstants.ATTR_PARENT_TAG, SdkConstants.TOOLS_URI)
        if (parentTagName != null) {
          registerAttributesForClassAndSuperclasses(
            facet,
            element,
            findViewValidInXMLByName(facet, parentTagName),
            callback,
            skipAttrNames
          )
        }
      }
      else -> {
        val c = findViewValidInXMLByName(facet, tagName)
        registerAttributesForClassAndSuperclasses(facet, element, c, callback, skipAttrNames)
      }
    }
    if (tagName == SdkConstants.VIEW_MERGE) {
      // A <merge> does not have layout attributes.
      // Instead the children of the merge tag are considered the top elements.
      return
    }
    val parentTag = tag.parentTag
    var parentViewClass: PsiClass? = null
    if (parentTag != null) {
      var parentTagName: String? = parentTag.name
      if (SdkConstants.VIEW_MERGE == parentTagName) {
        parentTagName =
          parentTag.getAttributeValue(SdkConstants.ATTR_PARENT_TAG, SdkConstants.TOOLS_URI)
      }
      if (SdkConstants.TAG_LAYOUT == parentTagName) {
        // Data binding: ensure that the children of the <layout> tag
        // pick up layout params from ViewGroup (layout_width and layout_height)
        parentViewClass = findViewClassByName(facet, SdkConstants.CLASS_VIEWGROUP)
      } else if (parentTagName != null) {
        parentViewClass = findViewValidInXMLByName(facet, parentTagName)
      }
      if (parentTagName != null) {
        while (parentViewClass != null) {
          registerAttributesFromSuffixedStyleables(
            facet,
            element,
            parentViewClass,
            callback,
            skipAttrNames
          )
          parentViewClass = getSuperclass(parentViewClass)
        }
        return
      }
    }

    // We don't know what the parent is: include all layout attributes from all layout classes.
    registerAttributesFromSuffixedStyleables(facet, element, callback, skipAttrNames)
  }

  /**
   * Enumerate attributes that are available for the given XML tag, represented by
   * [AndroidDomElement], and "return" them via [AttributeProcessor].
   *
   * Primary user is [AndroidDomExtender], which uses it to provide code completion facilities when
   * editing XML files in text editor.
   *
   * Implementation of the method implements [Styleable] annotation handling and dispatches on tag
   * type using instanceof checks for adding attributes that don't come from styleable definitions
   * with statically known names.
   *
   * @param processAllExistingAttrsFirst whether already existing attributes should be returned
   *   first
   */
  @JvmStatic
  fun processAttributes(
    element: AndroidDomElement,
    facet: AndroidFacet,
    processAllExistingAttrsFirst: Boolean,
    callback: AttributeProcessor
  ) {
    if (getInstance.getInstance(facet.module.project).isDumb) {
      return
    }
    val tag = element.xmlTag!!
    val skippedAttributes =
      if (processAllExistingAttrsFirst) registerExistingAttributes(facet, tag, element, callback)
      else HashSet()
    val descriptor = tag.descriptor
    if (
      descriptor is TagFromClassDescriptor && (descriptor as TagFromClassDescriptor).clazz == null
    ) {
      // Don't register attributes for unresolved classes.
      return
    }
    if (element is ManifestElement) {
      processManifestAttributes(tag, element, callback)
    } else if (element is LayoutElement) {
      processLayoutAttributes(facet, tag, element, skippedAttributes, callback)
    } else if (element is XmlResourceElement) {
      processXmlAttributes(facet, tag, element, skippedAttributes, callback)
    } else if (element is XmlRawResourceElement) {
      processRawAttributes(tag, callback)
    } else if (element is NavDestinationElement) {
      processNavAttributes(facet, tag, element, skippedAttributes, callback)
    }

    // If DOM element is annotated with @Styleable annotation, load a styleable definition
    // from Android framework or a library with the name provided in annotation and register all
    // attributes
    // from it for code highlighting and completion.
    val styleableAnnotation = element.getAnnotation(Styleable::class.java) ?: return
    val isSystem = styleableAnnotation.packageName == SdkConstants.ANDROID_PKG
    val definitions: AttributeDefinitions
    if (isSystem) {
      val manager =
        ModuleResourceManagers.getInstance(facet).getFrameworkResourceManager() ?: return
      definitions = manager.getAttributeDefinitions()
      if (definitions == null) {
        return
      }
    } else {
      definitions =
        ModuleResourceManagers.getInstance(facet).getLocalResourceManager().attributeDefinitions
    }
    if (element is MenuItem) {
      processMenuItemAttributes(facet, element, skippedAttributes, callback)
      return
    }
    for (styleableName in styleableAnnotation.value) {
      val styleable = definitions.getStyleableByName(styleableName)
      if (styleable != null) {
        // TODO(namespaces): if !isSystem and we're namespace-aware we should use the
        // library-specific namespace
        registerStyleableAttributes(
          element,
          styleable,
          if (isSystem) SdkConstants.ANDROID_URI else SdkConstants.AUTO_URI,
          callback,
          skippedAttributes
        )
      } else if (isSystem) {
        // DOM element is annotated with @Styleable annotation, but styleable definition with
        // provided name is not there in Android framework. This is a bug, so logging it as a
        // warning.
        log.warn(
          String.format(
            "@Styleable(%s) annotation doesn't point to existing styleable",
            styleableName
          )
        )
      }
    }

    // Handle interpolator XML tags: they don't have their own DomElement interfaces, and all use
    // InterpolatorElement at the moment.
    // Thus, they can't use @Styleable annotation and there is a mapping from tag name to styleable
    // name that's used below.

    // This snippet doesn't look much different from lines above for handling @Styleable annotations
    // above,
    // but is used to provide customized warning message
    // TODO: figure it out how to make it DRY without introducing new method with lots of arguments
    if (element is InterpolatorElement) {
      val styleableName = InterpolatorDomFileDescription.getInterpolatorStyleableByTagName(tag.name)
      if (styleableName != null) {
        val styleable = definitions.getStyleableByName(styleableName)
        if (styleable == null) {
          log.warn(
            String.format("%s doesn't point to existing styleable for interpolator", styleableName)
          )
        } else {
          registerStyleableAttributes(
            element,
            styleable,
            SdkConstants.ANDROID_URI,
            callback,
            skippedAttributes
          )
        }
      }
    }
  }

  /** Handle attributes for XML elements in raw/ resource folder */
  fun processRawAttributes(tag: XmlTag, callback: AttributeProcessor) {
    // For Resource Shrinking
    if (SdkConstants.TAG_RESOURCES == tag.name) {
      registerToolsAttribute(SdkConstants.ATTR_SHRINK_MODE, callback)
      registerToolsAttribute(SdkConstants.ATTR_KEEP, callback)
      registerToolsAttribute(SdkConstants.ATTR_DISCARD, callback)
    }
  }

  /** Handle attributes for XML elements from AndroidManifest.xml */
  fun processManifestAttributes(
    tag: XmlTag,
    element: AndroidDomElement,
    callback: AttributeProcessor
  ) {
    // Don't register manifest merger attributes for root element
    if (tag.parentTag != null) {
      registerToolsAttribute(ToolsAttributeUtil.ATTR_NODE, callback)
      registerToolsAttribute(ToolsAttributeUtil.ATTR_STRICT, callback)
      registerToolsAttribute(ToolsAttributeUtil.ATTR_REMOVE, callback)
      registerToolsAttribute(ToolsAttributeUtil.ATTR_REPLACE, callback)
    }
    if (element is UsesSdk) {
      registerToolsAttribute(ToolsAttributeUtil.ATTR_OVERRIDE_LIBRARY, callback)
    }
  }

  private fun processMenuItemAttributes(
    facet: AndroidFacet,
    element: DomElement,
    skippedAttributes: MutableCollection<XmlName>,
    callback: AttributeProcessor
  ) {
    val manager = ModuleResourceManagers.getInstance(facet).getFrameworkResourceManager() ?: return
    val styleables = manager.getAttributeDefinitions() ?: return
    val styleable = styleables.getStyleableByName("MenuItem")
    if (styleable == null) {
      log.warn("No StyleableDefinition for MenuItem")
      return
    }
    for (attribute in styleable.getAttributes()) {
      val name = attribute.name

      // android:showAsAction was introduced in API Level 11. Use the app: one if the project
      // depends on appcompat.
      // See com.android.tools.lint.checks.AppCompatResourceDetector.
      if (name == SdkConstants.ATTR_SHOW_AS_ACTION) {
        val hasAppCompat =
          facet.module.dependsOn(GoogleMavenArtifactId.APP_COMPAT_V7) ||
            facet.module.dependsOn(GoogleMavenArtifactId.ANDROIDX_APP_COMPAT_V7)
        if (hasAppCompat) {
          // TODO(namespaces): Replace AUTO_URI with the URI of the correct namespace.
          val xmlName = XmlName(name, SdkConstants.AUTO_URI)
          if (skippedAttributes.add(xmlName)) {
            registerAttribute(attribute, xmlName, "MenuItem", element, callback)
          }
          continue
        }
      }
      val xmlName = XmlName(name, SdkConstants.ANDROID_URI)
      if (skippedAttributes.add(xmlName)) {
        registerAttribute(attribute, xmlName, "MenuItem", element, callback)
      }
    }
  }

  private fun registerToolsAttribute(attributeName: String, callback: AttributeProcessor) {
    val definition = ToolsAttributeUtil.getAttrDefByName(attributeName)
    if (definition != null) {
      val name = XmlName(attributeName, SdkConstants.TOOLS_URI)
      val domExtension = callback.processAttribute(name, definition, null)
      val converter = ToolsAttributeUtil.getConverter(definition)
      if (domExtension != null && converter != null) {
        domExtension.setConverter(converter)
      }
    } else {
      log.warn("No attribute definition for tools attribute $attributeName")
    }
  }

  private fun registerExistingAttributes(
    facet: AndroidFacet,
    tag: XmlTag,
    element: AndroidDomElement,
    callback: AttributeProcessor
  ): MutableSet<XmlName> {
    val result: MutableSet<XmlName> = HashSet()
    val attrs = tag.attributes
    for (attr in attrs) {
      val localName = attr.localName
      if (!localName.endsWith(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED)) {
        if ("xmlns" != attr.namespacePrefix) {
          val attrDef = AndroidDomUtil.getAttributeDefinition(facet, attr)
          if (attrDef != null) {
            val xmlName = getXmlName(attrDef, attr.namespace)
            result.add(xmlName)
            val namespaceUri = attr.namespace
            registerAttribute(attrDef, xmlName, null, element, callback)
          }
        }
      }
    }
    return result
  }

  private fun getXmlName(attrDef: AttributeDefinition, namespaceUri: String?): XmlName {
    val attrReference = attrDef.resourceReference
    val attrNamespaceUri = attrReference.namespace.xmlNamespaceUri
    return XmlName(
      attrReference.name,
      if (SdkConstants.TOOLS_URI == namespaceUri) SdkConstants.TOOLS_URI else attrNamespaceUri
    )
  }

  interface AttributeProcessor {
    fun processAttribute(
      xmlName: XmlName,
      attrDef: AttributeDefinition,
      parentStyleableName: String?
    ): DomExtension?
  }
}
