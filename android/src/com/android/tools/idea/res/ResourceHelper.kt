/*
 * Copyright (C) 2016 The Android Open Source Project
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
@file:JvmName("ResourceHelper")
package com.android.tools.idea.res

import com.android.SdkConstants.*
import com.android.ide.common.rendering.api.RenderResources
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.repository.ResourceVisibilityLookup
import com.android.ide.common.resources.AbstractResourceRepository
import com.android.ide.common.resources.AbstractResourceRepository.MAX_RESOURCE_INDIRECTION
import com.android.ide.common.resources.DataFile
import com.android.ide.common.resources.ResourceFile
import com.android.ide.common.resources.ResourceItem.*
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.xml.AndroidManifestParser
import com.android.io.FileWrapper
import com.android.resources.FolderTypeRelationship
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.databinding.DataBindingUtil
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.lint.detector.api.LintUtils
import com.google.common.collect.Lists
import com.google.common.collect.Sets
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.xml.*
import com.intellij.ui.ColorUtil
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.ResourceFolderManager
import org.jetbrains.android.sdk.AndroidPlatform
import org.jetbrains.annotations.Contract
import java.awt.Color
import java.io.File
import java.util.*

const val ALPHA_FLOATING_ERROR_FORMAT = "The alpha attribute in %1\$s/%2\$s does not resolve to a floating point number"
private val LOG = Logger.getInstance("#com.android.tools.idea.res.ResourceHelper")

/**
 * Package prefixes used in [isViewPackageNeeded]
 */
private val NO_PREFIX_PACKAGES = arrayOf(ANDROID_WIDGET_PREFIX, ANDROID_VIEW_PKG, ANDROID_WEBKIT_PKG)

/**
 * Returns true if the given style represents a project theme
 *
 * @param styleResourceUrl a theme style resource url
 * @return true if the style string represents a project theme, as opposed
 * to a framework theme
 */
fun isProjectStyle(styleResourceUrl: String): Boolean {
  return !styleResourceUrl.startsWith(ANDROID_STYLE_RESOURCE_PREFIX)
}

/**
 * Returns the theme name to be shown for theme styles, e.g. for "@style/Theme" it
 * returns "Theme"
 *
 * @param style a theme style string
 * @return the user visible theme name
 */
fun styleToTheme(style: String): String {
  return when {
    style.startsWith(STYLE_RESOURCE_PREFIX) -> style.substring(STYLE_RESOURCE_PREFIX.length)
    style.startsWith(ANDROID_STYLE_RESOURCE_PREFIX) -> style.substring(ANDROID_STYLE_RESOURCE_PREFIX.length)
    style.startsWith(PREFIX_RESOURCE_REF) -> {
      // @package:style/foo
      val index = style.indexOf('/')
      if (index != -1) style.substring(index + 1) else style
    }
    else -> style
  }
}

/**
 * Is this a resource that can be defined in any file within the "values" folder?
 *
 *
 * Some resource types can be defined **both** as a separate XML file as well
 * as defined within a value XML file. This method will return true for these types
 * as well. In other words, a ResourceType can return true for both
 * [.isValueBasedResourceType] and [.isFileBasedResourceType].
 *
 * @param type the resource type to check
 * @return true if the given resource type can be represented as a value under the
 * values/ folder
 */
fun isValueBasedResourceType(type: ResourceType): Boolean {
  return FolderTypeRelationship.getRelatedFolders(type).contains(ResourceFolderType.VALUES)
}

/**
 * Is this a resource that is defined in a file named by the resource plus the extension?
 *
 *
 * Some resource types can be defined **both** as a separate XML file as well as
 * defined within a value XML file along with other properties. This method will
 * return true for these resource types as well. In other words, a ResourceType can
 * return true for both [.isValueBasedResourceType] and
 * [.isFileBasedResourceType].
 *
 * @param type the resource type to check
 * @return true if the given resource type is stored in a file named by the resource
 */
fun isFileBasedResourceType(type: ResourceType): Boolean {
  if (type == ResourceType.ID) {
    // The folder types for ID is not only VALUES but also
    // LAYOUT and MENU. However, unlike resources, they are only defined
    // inline there so for the purposes of isFileBasedResourceType
    // (where the intent is to figure out files that are uniquely identified
    // by a resource's name) this method should return false anyway.
    return false
  }

  return FolderTypeRelationship.getRelatedFolders(type).firstOrNull { it != ResourceFolderType.VALUES } != null
}

/**
 * Returns the resource name of the given file.
 *
 *
 * For example, `getResourceName(</res/layout-land/foo.xml, false) = "foo"`.
 *
 * @param file the file to compute a resource name for
 * @return the resource name
 */
fun getResourceName(file: VirtualFile): String {
  // Note that we use getBaseName here rather than {@link VirtualFile#getNameWithoutExtension}
  // because that method uses lastIndexOf('.') rather than indexOf('.') -- which means that
  // for a nine patch drawable it would include ".9" in the resource name
  return LintUtils.getBaseName(file.name)
}

/**
 * Returns the resource name of the given file.
 *
 * For example, `getResourceName(</res/layout-land/foo.xml, false) = "foo"`.
 *
 * @param file the file to compute a resource name for
 * @return the resource name
 */
fun getResourceName(file: PsiFile): String {
  // See getResourceName(VirtualFile)
  // We're replicating that code here rather than just calling
  // getResourceName(file.getVirtualFile());
  // since file.getVirtualFile can return null
  return LintUtils.getBaseName(file.name)
}

fun getFolderType(file: PsiFile?): ResourceFolderType? {
  if (file == null) return null
  if (!ApplicationManager.getApplication().isReadAccessAllowed) return runReadAction { getFolderType(file) }
  if (!file.isValid) return getFolderType(file.virtualFile)
  return file.parent?.let { ResourceFolderType.getFolderType(it.name) }
}

fun getFolderType(file: VirtualFile?): ResourceFolderType? = file?.parent?.let { ResourceFolderType.getFolderType(it.name) }

fun getFolderType(file: ResourceFile): ResourceFolderType? = file.file.parentFile?.let { ResourceFolderType.getFolderType(it.name) }

fun getFolderConfiguration(file: PsiFile?): FolderConfiguration? {
  if (file == null) return null
  if (!ApplicationManager.getApplication().isReadAccessAllowed) return runReadAction { getFolderConfiguration(file) }
  if (!file.isValid) return getFolderConfiguration(file.virtualFile)
  return file.parent?.let {FolderConfiguration.getConfigForFolder(it.name) }
}

fun getFolderConfiguration(file: VirtualFile?): FolderConfiguration? = file?.parent?.let { FolderConfiguration.getConfigForFolder(it.name) }

/**
 * Returns all resource variations for the given file
 *
 * @param file resource file, which should be an XML file in one of the
 * various resource folders, e.g. res/layout, res/values-xlarge, etc.
 * @param includeSelf if true, include the file itself in the list,
 * otherwise exclude it
 * @return a list of all the resource variations
 */
fun getResourceVariations(file: VirtualFile?, includeSelf: Boolean): List<VirtualFile> {
  if (file == null) {
    return emptyList()
  }

  // Compute the set of layout files defining this layout resource
  val variations = ArrayList<VirtualFile>()
  val name = file.name
  val resFolder = file.parent?.parent ?: return variations
  var parentName = file.parent.name
  var prefix = parentName
  val qualifiers = prefix.indexOf('-')

  if (qualifiers != -1) {
    parentName = prefix.substring(0, qualifiers)
    prefix = prefix.substring(0, qualifiers + 1)
  } else {
    prefix += '-'
  }
  for (resource in resFolder.children) {
    val n = resource.name
    if ((n.startsWith(prefix) || n == parentName) && resource.isDirectory) {
      val variation = resource.findChild(name)
      if (variation != null) {
        if (!includeSelf && file == variation) {
          continue
        }
        variations.add(variation)
      }
    }
  }

  return variations
}

/**
 * Returns true if views with the given fully qualified class name need to include
 * their package in the layout XML tag. Package prefixes that allow class name to be
 * unqualified are specified in [.NO_PREFIX_PACKAGES] and should reflect a list
 * of prefixes from framework's LayoutInflater and PhoneLayoutInflater.
 *
 * @param qualifiedName the fully qualified class name, such as android.widget.Button
 * @param apiLevel The API level for the calling context. This is the max of the
 * project's minSdkVersion and the layout file's version qualifier, if any.
 * You can pass -1 if this is not known, which will force fully qualified
 * names on some packages which recently no longer require it.
 * @return true if the full package path should be included in the layout XML element
 * tag
 */
fun isViewPackageNeeded(qualifiedName: String, apiLevel: Int): Boolean {
  for (noPrefixPackage in NO_PREFIX_PACKAGES) {
    // We need to check not only if prefix is a "whitelisted" package, but if the class
    // is stored in that package directly, as opposed to be stored in a subpackage.
    // For example, view with FQCN android.view.MyView can be abbreviated to "MyView",
    // but android.view.custom.MyView can not.
    if (isDirectlyInPackage(qualifiedName, noPrefixPackage)) {
      return false
    }
  }

  return if (apiLevel >= 20) {
    // Special case: starting from API level 20, classes from "android.app" also inflated
    // without fully qualified names
    !isDirectlyInPackage(qualifiedName, ANDROID_APP_PKG)
  } else true
}

/**
 * XML tags associated with classes usually can come either with fully-qualified names, which can be shortened
 * in case of common packages, which is handled by various inflaters in Android framework. This method checks
 * whether a class with given qualified name can be shortened to a simple name, or is required to have
 * a package qualifier.
 *
 *
 * Accesses JavaPsiFacade, and thus should be run inside read action.
 *
 * @see .isViewPackageNeeded
 */
fun isClassPackageNeeded(qualifiedName: String, baseClass: PsiClass, apiLevel: Int): Boolean {
  val viewClass = JavaPsiFacade.getInstance(baseClass.project).findClass(CLASS_VIEW, GlobalSearchScope.allScope(baseClass.project))

  return if (viewClass != null && baseClass.isInheritor(viewClass, true)) {
    isViewPackageNeeded(qualifiedName, apiLevel)
  } else if (CLASS_PREFERENCE == baseClass.qualifiedName) {
    // Handled by PreferenceInflater in Android framework
    !isDirectlyInPackage(qualifiedName, "android.preference.")
  } else {
    // TODO: removing that makes some of unit tests fail, but leaving it as it is can introduce buggy XML validation
    // Issue with further information: http://b.android.com/186559
    !qualifiedName.startsWith(ANDROID_PKG_PREFIX)
  }
}

/**
 * Returns whether a class with given qualified name resides directly in a package with
 * given prefix (as opposed to reside in a subpackage).
 *
 *
 * For example,
 *
 *  * isDirectlyInPackage("android.view.View", "android.view.") -> true
 *  * isDirectlyInPackage("android.view.internal.View", "android.view.") -> false
 *
 */
fun isDirectlyInPackage(qualifiedName: String, packagePrefix: String): Boolean {
  return qualifiedName.startsWith(packagePrefix) && qualifiedName.indexOf('.', packagePrefix.length + 1) == -1
}

/**
 * Tries to resolve the given resource value to an actual RGB color. For state lists
 * it will pick the simplest/fallback color.
 *
 * @param colorValue the color to resolve
 * @param project the current project
 * @return the corresponding [Color] color, or null
 */
// TODO(namespaces): require more information here as context for namespaced lookup
fun RenderResources.resolveColor(colorValue: ResourceValue?, project: Project): Color? {
  return resolveColor(colorValue, project, 0)
}

// TODO(namespaces): require more information here as context for namespaced lookup
private fun RenderResources.resolveColor(colorValue: ResourceValue?, project: Project, depth: Int): Color? {
  if (depth >= MAX_RESOURCE_INDIRECTION) {
    LOG.warn("too deep " + colorValue)
    return null
  }
  val result = resolveNullableResValue(colorValue) ?: return null

  val stateList = resolveStateList(result, project) ?: return parseColor(result.value)
  val states = stateList.states

  if (states.isEmpty()) {
    // In the case of an empty selector, we don't want to crash.
    return null
  }

  // Getting the last color of the state list, because it's supposed to be the simplest / fallback one
  val state = states[states.size - 1]

  val stateColor = parseColor(state.value) ?: resolveColor(findResValue(state.value, false), project, depth + 1) ?: return null
  try {
    return makeColorWithAlpha(stateColor, state.alpha)
  } catch (e: NumberFormatException) {
    // If the alpha value is not valid, Android uses 1.0
    LOG.warn(
        String.format(
            "The alpha attribute in %s/%s does not resolve to a floating point number", stateList.dirName,
            stateList.fileName
        )
    )
    return stateColor
  }
}

/**
 * Tries to resolve colors from given resource value. When state list is encountered all
 * possibilities are explored.
 */
fun RenderResources.resolveMultipleColors(value: ResourceValue?, project: Project): List<Color> {
  return resolveMultipleColors(value, project, 0)
}

/**
 * Tries to resolve colors from given resource value. When state list is encountered all
 * possibilities are explored.
 */
// TODO(namespaces): require more information here as context for namespaced lookup
private fun RenderResources.resolveMultipleColors(value: ResourceValue?, project: Project, depth: Int): List<Color> {
  if (depth >= MAX_RESOURCE_INDIRECTION) {
    LOG.warn("too deep " + value)
    return emptyList()
  }

  val resolvedValue = resolveNullableResValue(value) ?: return emptyList()

  val result = ArrayList<Color>()

  val stateList = resolveStateList(resolvedValue, project)
  if (stateList != null) {
    for (state in stateList.states) {
      val stateColors: List<Color>
      val resolvedStateResource = findResValue(state.value, false)
      stateColors = if (resolvedStateResource != null) {
        resolveMultipleColors(resolvedStateResource, project, depth + 1)
      } else {
        listOfNotNull(parseColor(state.value))
      }
      for (color in stateColors) {
        try {
          result.add(makeColorWithAlpha(color, state.alpha))
        } catch (e: NumberFormatException) {
          // If the alpha value is not valid, Android uses 1.0 so nothing more needs to be done, we simply take color as it is
          result.add(color)
          LOG.warn(String.format(ALPHA_FLOATING_ERROR_FORMAT, stateList.dirName, stateList.fileName))
        }

      }
    }
  } else {
    val color = parseColor(resolvedValue.value)
    if (color != null) {
      result.add(color)
    }
  }
  return result
}

// TODO(namespaces): require more information here as context for namespaced lookup
fun RenderResources.resolveStringValue(value: String): String {
  val resValue = findResValue(value, false) ?: return value
  return resolveNullableResValue(resValue)?.value ?: value
}

/**
 * When annotating Java files, we need to find an associated layout file to pick the resource
 * resolver from (e.g. to for example have a theme association which will drive how colors are
 * resolved). This picks one of the open layout files, and if not found, the first layout
 * file found in the resources (if any).
 */
fun pickAnyLayoutFile(module: Module, facet: AndroidFacet): VirtualFile? {
  val openFiles = FileEditorManager.getInstance(module.project).openFiles
  for (file in openFiles) {
    if (file.name.endsWith(DOT_XML) && file.parent != null &&
        file.parent.name.startsWith(FD_RES_LAYOUT)) {
      return file
    }
  }

  // Pick among actual files in the project
  for (resourceDir in ResourceFolderManager.getInstance(facet).folders) {
    for (folder in resourceDir.children) {
      if (folder.name.startsWith(FD_RES_LAYOUT) && folder.isDirectory) {
        for (file in folder.children) {
          if (file.name.endsWith(DOT_XML) && file.parent != null &&
              file.parent.name.startsWith(FD_RES_LAYOUT)) {
            return file
          }
        }
      }
    }
  }

  return null
}

fun RenderResources.resolve(resourceUrl: ResourceUrl, tag: XmlTag): ResourceValue? {
  val facet = AndroidFacet.getInstance(tag) ?: return null
  val resourceRepositoryManager = ResourceRepositoryManager.getOrCreateInstance(facet)
  val namespaceResolver = getNamespaceResolver(tag)
  val resourceReference = resourceUrl.resolve(resourceRepositoryManager.namespace, namespaceResolver) ?: return null
  return getUnresolvedResource(resourceReference)
}

@Throws(NumberFormatException::class)
fun RenderResources.makeColorWithAlpha(color: Color, alphaValue: String?): Color {
  val alpha = if (alphaValue != null) resolveStringValue(alphaValue).toFloat() else 1.0f
  val combinedAlpha = (color.alpha * alpha).toInt()
  return ColorUtil.toAlpha(color, clamp(combinedAlpha, 0, 255))
}

/**
 * Returns a [StateList] description of the state list value, or null if value is not a state list.
 */
fun RenderResources.resolveStateList(value: ResourceValue, project: Project): StateList? = resolveStateList(value, project, 0)

// TODO(namespaces): require more information here as context for namespaced lookup
private fun RenderResources.resolveStateList(resourceValue: ResourceValue, project: Project, depth: Int): StateList? {
  if (depth >= MAX_RESOURCE_INDIRECTION) {
    LOG.warn("too deep " + resourceValue)
    return null
  }

  // Not all ResourceValue instances have values (e.g. StyleResourceValue)
  val value = resourceValue.value ?: return null

  if (value.startsWith(PREFIX_RESOURCE_REF)) {
    val resValue = findResValue(value, resourceValue.isFramework)
    if (resValue != null) return resolveStateList(resValue, project, depth + 1)
  } else {
    val virtualFile = LocalFileSystem.getInstance().findFileByPath(value) ?: return null
    val psiFile = (AndroidPsiUtils.getPsiFileSafely(project, virtualFile) as? XmlFile) ?: return null
    val rootTag = psiFile.rootTag ?: return null
    if (TAG_SELECTOR == rootTag.name) {
      val stateList = StateList(psiFile.name, psiFile.containingDirectory.name)
      for (subTag in rootTag.findSubTags(TAG_ITEM)) {
        val stateListState = createStateListState(subTag, resourceValue.isFramework) ?: return null
        stateList.addState(stateListState)
      }
      return stateList
    }
  }
  return null
}

/**
 * Try to parse a state in the "item" tag. Only handles those items that have
 * either "android:color" or "android:drawable" attributes in "item" tag.
 *
 * @return [StateListState] representing the state in tag, null if parse is unsuccessful
 */
private fun createStateListState(tag: XmlTag, isFramework: Boolean): StateListState? {
  var stateValue: String? = null
  var alphaValue: String? = null
  val stateAttributes = HashMap<String, Boolean>()
  val attributes = tag.attributes
  for (attr in attributes) {
    val name = attr.localName
    val value = attr.value ?: continue
    when {
      ATTR_COLOR == name || ATTR_DRAWABLE == name -> {
        val url = ResourceUrl.parse(value, isFramework)
        stateValue = url?.toString() ?: value
      }
      "alpha" == name -> {
        val url = ResourceUrl.parse(value, isFramework)
        alphaValue = url?.toString() ?: value
      }
      name.startsWith(STATE_NAME_PREFIX) -> stateAttributes[name] = value.toBoolean()
    }
  }
  return stateValue?.let { StateListState(stateValue, stateAttributes, alphaValue) }
}

/**
 * Converts the supported color formats (#rgb, #argb, #rrggbb, #aarrggbb to a Color
 * http://developer.android.com/guide/topics/resources/more-resources.html#Color
 */
fun parseColor(s: String?): Color? {
  val trimmed = s?.trim() ?: return null
  if (trimmed.isEmpty()) {
    return null
  }

  if (trimmed[0] == '#') {
    var longColor = trimmed.substring(1).toLongOrNull(16) ?: return null

    if (trimmed.length == 4 || trimmed.length == 5) {
      val a = if (trimmed.length == 4) 0xff else extend(longColor and 0xf000 shr 12)
      val r = extend(longColor and 0xf00 shr 8)
      val g = extend(longColor and 0x0f0 shr 4)
      val b = extend(longColor and 0x00f)
      longColor = a shl 24 or (r shl 16) or (g shl 8) or b
      return Color(longColor.toInt(), true)
    }

    if (trimmed.length == 7) {
      longColor = longColor or -0x1000000
    } else if (trimmed.length != 9) {
      return null
    }
    return Color(longColor.toInt(), true)
  }

  return null
}

/**
 * Converts a color to hex-string representation, including alpha channel.
 * If alpha is FF then the output is #RRGGBB with no alpha component.
 */
fun colorToString(color: Color): String {
  var longColor = (color.red shl 16 or (color.green shl 8) or color.blue).toLong()
  if (color.alpha != 0xFF) {
    longColor = longColor or (color.alpha.toLong() shl 24)
    return String.format("#%08x", longColor)
  }
  return String.format("#%06x", longColor)
}

private fun extend(nibble: Long): Long {
  return nibble or (nibble shl 4)
}

/**
 * Tries to resolve the given resource value to an actual drawable bitmap file. For state lists
 * it will pick the simplest/fallback drawable.
 *
 * @param drawable the drawable to resolve
 * @param project the current project
 * @return the corresponding [File], or null
 */
fun RenderResources.resolveDrawable(drawable: ResourceValue?, project: Project): File? {
  val resolvedDrawable = resolveNullableResValue(drawable) ?: return null

  var result = resolvedDrawable.value

  val stateList = resolveStateList(resolvedDrawable, project)
  if (stateList != null) {
    val states = stateList.states
    if (!states.isEmpty()) {
      val state = states[states.size - 1]
      result = state.value
    }
  }

  if (result == null) {
    return null
  }

  val file = File(result)
  return if (file.isFile) file else null
}

/**
 * Tries to resolve the given resource value to an actual layout file.
 *
 * @param layout the layout to resolve
 * @return the corresponding [File], or null
 */
// TODO(namespaces): require more information here as context for namespaced lookup
fun RenderResources.resolveLayout(layout: ResourceValue?): File? {
  var resolvedLayout = resolveNullableResValue(layout) ?: return null
  var value = resolvedLayout.value

  var depth = 0
  while (value != null && depth < MAX_RESOURCE_INDIRECTION) {
    if (DataBindingUtil.isBindingExpression(value)) {
      value = DataBindingUtil.getBindingExprDefault(value) ?: return null
    }
    if (value.startsWith(PREFIX_RESOURCE_REF)) {
      resolvedLayout = findResValue(value, resolvedLayout.isFramework) ?: break
      value = resolvedLayout.value
    } else {
      val file = File(value)
      return if (file.exists()) file else null
    }

    depth++
  }

  return null
}

/**
 * Returns the given resource name, and possibly prepends a project-configured prefix to the name
 * if set on the Gradle module (but only if it does not already start with the prefix).
 *
 * @param module the corresponding module
 * @param name the resource name
 * @return the resource name, possibly with a new prefix at the beginning of it
 */
@Contract("_, !null, _ -> !null")
fun prependResourcePrefix(module: Module?, name: String?, folderType: ResourceFolderType?): String? {
  if (module == null) {
    return name
  }
  val facet = AndroidFacet.getInstance(module) ?: return name
  val androidModel = AndroidModuleModel.get(facet) ?: return name
  val resourcePrefix = LintUtils.computeResourcePrefix(androidModel.androidProject) ?: return name
  return if (name != null) {
    if (name.startsWith(resourcePrefix)) name else LintUtils.computeResourceName(resourcePrefix, name, folderType)
  } else {
    resourcePrefix
  }
}

fun clamp(i: Int, min: Int, max: Int): Int {
  return Math.max(min, Math.min(i, max))
}

/**
 * Returns the list of all resource names that can be used as a value for one of the [ResourceType] in completionTypes,
 * optionally sorting/not sorting the results.
 */
@JvmOverloads
fun getCompletionFromTypes(
  facet: AndroidFacet, completionTypes: EnumSet<ResourceType>,
  sort: Boolean = true
): List<String> {
  val types = Sets.newEnumSet(completionTypes, ResourceType::class.java)

  // Use drawables for mipmaps
  if (types.contains(ResourceType.MIPMAP)) {
    types.add(ResourceType.DRAWABLE)
  } else if (types.contains(ResourceType.DRAWABLE)) {
    types.add(ResourceType.MIPMAP)
  }

  val completionTypesContainsColor = types.contains(ResourceType.COLOR)
  if (types.contains(ResourceType.DRAWABLE)) {
    // The Drawable type accepts colors as value but not color state lists.
    types.add(ResourceType.COLOR)
  }

  val repository = AppResourceRepository.getOrCreateInstance(facet)
  val lookup = repository.getResourceVisibility(facet)
  val androidPlatform = AndroidPlatform.getInstance(facet.module)
  var frameworkResources: AbstractResourceRepository? = null
  if (androidPlatform != null) {
    val targetData = androidPlatform.sdkData.getTargetData(androidPlatform.target)
    frameworkResources = targetData.getFrameworkResources(true)
  }

  val resources = Lists.newArrayListWithCapacity<String>(500)
  for (type in types) {
    // If type == ResourceType.COLOR, we want to include file resources (i.e. color state lists) only in the case where
    // color was present in completionTypes, and not if we added it because of the presence of ResourceType.DRAWABLES.
    // For any other ResourceType, we always include file resources.
    val includeFileResources = type != ResourceType.COLOR || completionTypesContainsColor
    if (frameworkResources != null) {
      addFrameworkItems(resources, type, includeFileResources, frameworkResources)
    }
    addProjectItems(resources, type, includeFileResources, repository, lookup)
  }

  if (sort) {
    Collections.sort(resources) { resource1, resource2 -> compareResourceReferences(resource1, resource2) }
  }

  return resources
}

/**
 * Return all the IDs in a XML file.
 */
fun findIdsInFile(file: PsiFile): Set<String> {
  val ids = HashSet<String>()
  file.accept(object : PsiRecursiveElementVisitor() {
    override fun visitElement(element: PsiElement) {
      super.visitElement(element)
      if (element is XmlTag) {
        val id = LintUtils.stripIdPrefix(element.getAttributeValue(ATTR_ID, ANDROID_URI))
        if (!id.isEmpty()) {
          ids.add(id)
        }
      }
    }
  })
  return ids
}

/**
 * Comparator function for resource references (e.g. `@foo/bar`.
 * Sorts project resources higher than framework resources.
 */
fun compareResourceReferences(resource1: String, resource2: String): Int {
  val framework1 = if (resource1.startsWith(ANDROID_PREFIX)) 1 else 0
  val framework2 = if (resource2.startsWith(ANDROID_PREFIX)) 1 else 0
  val delta = framework1 - framework2
  return if (delta != 0) delta else resource1.compareTo(resource2, ignoreCase = true)
}

private fun addFrameworkItems(
  destination: MutableList<String>,
  type: ResourceType,
  includeFileResources: Boolean,
  frameworkResources: AbstractResourceRepository
) {
  val items = frameworkResources.getPublicResourcesOfType(type)
  for (item in items) {
    if (!includeFileResources) {
      val sourceFile = item.source
      if (sourceFile != null && !sourceFile.file.parent.startsWith(FD_RES_VALUES)) {
        continue
      }
    }

    destination.add(PREFIX_RESOURCE_REF + ANDROID_NS_NAME_PREFIX + type.getName() + '/'.toString() + item.name)
  }
}

// TODO(namespaces): require more information here as context for namespaced lookup
private fun addProjectItems(
  destination: MutableList<String>,
  type: ResourceType,
  includeFileResources: Boolean,
  repository: AppResourceRepository,
  lookup: ResourceVisibilityLookup?
) {
  for (resourceName in repository.getItemsOfType(type)) {
    if (lookup != null && lookup.isPrivate(type, resourceName)) {
      continue
    }
    val items = repository.getResourceItem(type, resourceName) ?: continue
    if (!includeFileResources) {
      if (items[0].sourceType != DataFile.FileType.XML_VALUES) {
        continue
      }
    }

    destination.add(PREFIX_RESOURCE_REF + type.getName() + '/'.toString() + resourceName)
  }
}

/**
 * Returns a [ResourceNamespace.Resolver] for the specified tag.
 */
fun getNamespaceResolver(tag: XmlTag): ResourceNamespace.Resolver {
  // TODO(b/72688160, namespaces): precompute this to avoid the read lock.
  return object : ResourceNamespace.Resolver {
    override fun uriToPrefix(namespaceUri: String): String? {
      return ReadAction.compute<String, RuntimeException> {
        if (!tag.isValid) null else StringUtil.nullize(tag.getPrefixByNamespace(namespaceUri))
      }
    }

    override fun prefixToUri(namespacePrefix: String): String? {
      return ReadAction.compute<String, RuntimeException> {
        if (!tag.isValid) null else StringUtil.nullize(tag.getNamespaceByPrefix(namespacePrefix))
      }
    }
  }
}

/**
 * Returns the text content of a given tag
 */
fun getTextContent(tag: XmlTag): String {
  // We can't just use tag.getValue().getTrimmedText() here because we need to remove
  // intermediate elements such as <xliff> text:
  // TODO: Make sure I correct handle HTML content for XML items in <string> nodes!
  // For example, for the following string we want to compute "Share with %s":
  // <string name="share">Share with <xliff:g id="application_name" example="Bluetooth">%s</xliff:g></string>
  val subTags = tag.subTags
  val textElements = tag.value.textElements
  if (subTags.isEmpty()) {
    if (textElements.size == 1) {
      return getXmlTextValue(textElements[0])
    } else if (textElements.isEmpty()) {
      return ""
    }
  }
  val sb = StringBuilder(40)
  appendText(sb, tag)
  return sb.toString()
}

private fun getXmlTextValue(element: XmlText): String {
  var current = element.firstChild ?: return element.text
  if (current.nextSibling != null) {
    val sb = StringBuilder()
    while (true) {
      val type = current.node.elementType
      if (type === XmlElementType.XML_CDATA) {
        val children = current.children
        if (children.size == 3) { // XML_CDATA_START, XML_DATA_CHARACTERS, XML_CDATA_END
          assert(children[1].node.elementType === XmlTokenType.XML_DATA_CHARACTERS)
          sb.append(children[1].text)
        }
        current = current.nextSibling ?: break
        continue
      }
      sb.append(current.text)
      current = current.nextSibling ?: break
    }
    return sb.toString()
  } else if (current.node.elementType === XmlElementType.XML_CDATA) {
    val children = current.children
    if (children.size == 3) { // XML_CDATA_START, XML_DATA_CHARACTERS, XML_CDATA_END
      assert(children[1].node.elementType === XmlTokenType.XML_DATA_CHARACTERS)
      return children[1].text
    }
  }

  return element.text
}

private fun appendText(sb: StringBuilder, tag: XmlTag) {
  val children = tag.children
  for (child in children) {
    if (child is XmlText) {
      sb.append(getXmlTextValue(child))
    }
    else if (child is XmlTag) {
      // xliff support
      if (XLIFF_G_TAG == child.localName && child.namespace.startsWith(XLIFF_NAMESPACE_PREFIX)) {
        val example = child.getAttributeValue(ATTR_EXAMPLE)
        if (example != null) {
          // <xliff:g id="number" example="7">%d</xliff:g> minutes => "(7) minutes"
          sb.append('(').append(example).append(')')
          continue
        } else {
          val id = child.getAttributeValue(ATTR_ID)
          if (id != null) {
            // Step <xliff:g id="step_number">%1$d</xliff:g> => Step ${step_number}
            sb.append('$').append('{').append(id).append('}')
            continue
          }
        }
      }
      appendText(sb, child)
    }
  }
}

@Contract("null -> null")
private fun RenderResources.resolveNullableResValue(res: ResourceValue?): ResourceValue? {
  if (res == null) {
    return null
  }
  return resolveResValue(res)
}

fun buildResourceId(packageId: Byte, typeId: Byte, entryId: Short) =
  (packageId.toInt() shl 24) or (typeId.toInt() shl 16) or (entryId.toInt() and 0xffff)

fun getAarPackageName(aarDir: File): String? {
  val manifest = File(aarDir, ANDROID_MANIFEST_XML)
  return if (manifest.exists()) {
    try {
      AndroidManifestParser.parse(FileWrapper(manifest)).`package`
    } catch (e: Exception) {
      null
    }
  } else {
    null
  }
}

