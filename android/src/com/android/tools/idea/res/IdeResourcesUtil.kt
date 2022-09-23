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

/**
 * Utilities related to Android resources that are specific to IntelliJ APIs.
 *
 * See also `ResourcesUtil` in sdk-common.
 */
@file:JvmName("IdeResourcesUtil")

package com.android.tools.idea.res

import com.android.AndroidXConstants.PreferenceAndroidX.CLASS_PREFERENCE_ANDROIDX
import com.android.SdkConstants
import com.android.SdkConstants.ANDROID_APP_PKG
import com.android.SdkConstants.ANDROID_PKG_PREFIX
import com.android.SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX
import com.android.SdkConstants.ANDROID_VIEW_PKG
import com.android.SdkConstants.ANDROID_WEBKIT_PKG
import com.android.SdkConstants.ANDROID_WIDGET_PREFIX
import com.android.SdkConstants.ATTR_COLOR
import com.android.SdkConstants.ATTR_DRAWABLE
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.CLASS_VIEW
import com.android.SdkConstants.CLASS_VIEWGROUP
import com.android.SdkConstants.DOT_XML
import com.android.SdkConstants.FD_RES_LAYOUT
import com.android.SdkConstants.PREFIX_RESOURCE_REF
import com.android.SdkConstants.PreferenceClasses.CLASS_PREFERENCE
import com.android.SdkConstants.STYLE_RESOURCE_PREFIX
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_SELECTOR
import com.android.ide.common.rendering.api.RenderResources
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.rendering.api.ResourceValueImpl
import com.android.ide.common.resources.ResourceFile
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.ResourceItem.ATTR_EXAMPLE
import com.android.ide.common.resources.ResourceItem.XLIFF_G_TAG
import com.android.ide.common.resources.ResourceItem.XLIFF_NAMESPACE_PREFIX
import com.android.ide.common.resources.ResourceItemWithVisibility
import com.android.ide.common.resources.ResourceRepository
import com.android.ide.common.resources.ResourceResolver.MAX_RESOURCE_INDIRECTION
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.resources.escape.string.StringResourceEscaper
import com.android.ide.common.resources.toFileResourcePathString
import com.android.ide.common.util.PathString
import com.android.resources.FolderTypeRelationship
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.resources.ResourceVisibility
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.apk.viewer.ApkFileSystem
import com.android.tools.idea.databinding.util.DataBindingUtil
import com.android.tools.idea.kotlin.getPreviousInQualifiedChain
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.model.Namespacing
import com.android.tools.idea.projectsystem.SourceProviders
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.rendering.GutterIconCache
import com.android.tools.idea.res.psi.ResourceReferencePsiElement
import com.android.tools.idea.ui.MaterialColorUtils
import com.android.tools.idea.util.toVirtualFile
import com.android.tools.lint.detector.api.computeResourceName
import com.android.tools.lint.detector.api.stripIdPrefix
import com.android.utils.SdkUtils
import com.google.common.base.CharMatcher
import com.google.common.base.Joiner
import com.google.common.base.Preconditions
import com.google.common.collect.Lists
import com.intellij.ide.actions.CreateElementActionBase
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.FileTemplateUtil
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction.writeCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.JdkOrderEntry
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.XmlElementFactory
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlElement
import com.intellij.psi.xml.XmlElementType
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlText
import com.intellij.psi.xml.XmlTokenType
import com.intellij.ui.ColorUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.Processor
import com.intellij.util.text.nullize
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.ColorsIcon
import com.intellij.util.xml.DomManager
import org.jetbrains.android.AndroidAnnotatorUtil
import org.jetbrains.android.AndroidFileTemplateProvider
import org.jetbrains.android.actions.CreateTypedResourceFileAction
import org.jetbrains.android.augment.ManifestClass
import org.jetbrains.android.augment.StyleableAttrLightField
import org.jetbrains.android.dom.converters.ResourceReferenceConverter
import org.jetbrains.android.dom.manifest.Manifest
import org.jetbrains.android.dom.resources.ResourceElement
import org.jetbrains.android.dom.resources.Resources
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidRootUtil
import org.jetbrains.android.facet.ResourceFolderManager
import org.jetbrains.android.resourceManagers.ModuleResourceManagers
import org.jetbrains.android.sdk.AndroidPlatform
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.android.util.AndroidUtils
import org.jetbrains.annotations.Contract
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly
import org.jetbrains.kotlin.util.collectionUtils.filterIsInstanceAnd
import java.awt.Color
import java.io.File
import java.io.IOException
import java.util.EnumSet
import java.util.Properties
import javax.swing.Icon

private const val RESOURCE_CLASS_SUFFIX = "." + AndroidUtils.R_CLASS_NAME
private const val ROOT_TAG_PROPERTY = "ROOT_TAG"
private const val LAYOUT_WIDTH_PROPERTY = "LAYOUT_WIDTH"
private const val LAYOUT_HEIGHT_PROPERTY = "LAYOUT_HEIGHT"
private val LOG: Logger = Logger.getInstance("IdeResourcesUtil.kt")
const val RESOURCE_ICON_SIZE = 16
const val ALPHA_FLOATING_ERROR_FORMAT = "The alpha attribute in %1\$s/%2\$s does not resolve to a floating point number"
const val DEFAULT_STRING_RESOURCE_FILE_NAME = "strings.xml"

/** Matches characters that are not allowed in a resource name. */
private val RESOURCE_NAME_DISALLOWED_CHARS = CharMatcher.inRange('a', 'z')
  .or(CharMatcher.inRange('A', 'Z'))
  .or(CharMatcher.inRange('0', '9'))
  .negate()

@JvmField
val VALUE_RESOURCE_TYPES: EnumSet<ResourceType> = EnumSet.of(
  ResourceType.DRAWABLE, ResourceType.COLOR, ResourceType.DIMEN,
  ResourceType.STRING, ResourceType.STYLE, ResourceType.ARRAY,
  ResourceType.PLURALS, ResourceType.ID, ResourceType.BOOL,
  ResourceType.INTEGER, ResourceType.FRACTION, ResourceType.LAYOUT
)

@JvmField
val ALL_VALUE_RESOURCE_TYPES: EnumSet<ResourceType> = EnumSet.copyOf(VALUE_RESOURCE_TYPES).apply {
  add(ResourceType.ATTR)
  add(ResourceType.STYLEABLE)
}

/**
 * Returns the theme name to be shown for theme styles, e.g. for "@style/Theme" it returns "Theme".
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
 * Checks if this is a resource that can be defined in any file within the "values" folder.
 *
 * Some resource types can be defined **both** as a separate XML file as well
 * as defined within a value XML file. This method will return true for these types
 * as well. In other words, a ResourceType can return true for both
 * [ResourceType.isValueBased] and [ResourceType.isFileBased].
 *
 * @return true if the given resource type can be represented as a value under the
 * values/ folder
 */
fun ResourceType.isValueBased(): Boolean {
  return FolderTypeRelationship.getRelatedFolders(this).contains(ResourceFolderType.VALUES)
}

/**
 * Checks if this a resource that is defined in a file named by the resource plus the extension.
 *
 * Some resource types can be defined **both** as a separate XML file as well as
 * defined within a value XML file along with other properties. This method will
 * return true for these resource types as well. In other words, a ResourceType can
 * return true for both [ResourceType.isValueBased] and [ResourceType.isFileBased].
 *
 * @return true if the given resource type is stored in a file named by the resource
 */
fun ResourceType.isFileBased(): Boolean {
  if (this == ResourceType.ID) {
    // The folder types for ID is not only VALUES but also
    // LAYOUT and MENU. However, unlike resources, they are only defined
    // inline there so for the purposes of isFileBased
    // (where the intent is to figure out files that are uniquely identified
    // by a resource's name) this method should return false anyway.
    return false
  }

  return FolderTypeRelationship.getRelatedFolders(this).firstOrNull { it != ResourceFolderType.VALUES } != null
}

fun getFolderType(file: PsiFile?): ResourceFolderType? {
  return when {
    file == null -> null
    !ApplicationManager.getApplication().isReadAccessAllowed -> runReadAction { getFolderType(file) }
    !file.isValid -> getFolderType(file.virtualFile)
    else -> {
      var folderType = file.parent?.let { ResourceFolderType.getFolderType(it.name) }
      if (folderType == null) {
        folderType = file.virtualFile?.let { getFolderType(it) }
      }

      return folderType
    }
  }
}

fun getFolderType(file: VirtualFile?): ResourceFolderType? = file?.parent?.let { ResourceFolderType.getFolderType(it.name) }

fun getFolderType(file: ResourceFile): ResourceFolderType? = file.file.parentFile?.let { ResourceFolderType.getFolderType(it.name) }

fun getFolderConfiguration(file: PsiFile?): FolderConfiguration? {
  return when {
    file == null -> null
    !ApplicationManager.getApplication().isReadAccessAllowed -> runReadAction { getFolderConfiguration(file) }
    !file.isValid -> getFolderConfiguration(file.virtualFile)
    else -> file.parent?.let { FolderConfiguration.getConfigForFolder(it.name) }
  }
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
  }
  else {
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
 * Returns the [VirtualFile] representing the source of the given resource item, or null
 * if the source of the resource item is unknown or there is no VirtualFile for it.
 */
fun ResourceItem.getSourceAsVirtualFile(): VirtualFile? = runReadAction {
  if (this is PsiResourceItem) psiFile?.virtualFile else originalSource?.toVirtualFile()
}

/**
 * Package prefixes used in [isViewPackageNeeded]
 */
val NO_PREFIX_PACKAGES_FOR_VIEW = arrayOf(ANDROID_WIDGET_PREFIX, ANDROID_VIEW_PKG, ANDROID_WEBKIT_PKG)

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
 * @return true if the full package path should be included in the layout XML element tag
 */
fun isViewPackageNeeded(qualifiedName: String, apiLevel: Int): Boolean {
  for (noPrefixPackage in NO_PREFIX_PACKAGES_FOR_VIEW) {
    // We need to check not only if prefix is in an allowed package, but if the class
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
  }
  else {
    true
  }
}

/**
 * XML tags associated with classes usually can come either with fully-qualified names, which can be shortened
 * in case of common packages, which is handled by various inflaters in Android framework. This method checks
 * whether a class with given qualified name can be shortened to a simple name, or is required to have
 * a package qualifier.
 *
 * Accesses JavaPsiFacade, and thus should be run inside read action.
 *
 * @param parentClassQualifiedName Optional. Optimisation that can be used if you already know what the baseclass inherits from. If matching
 *                                 qualified name is found, then inheritance check is avoided.
 *
 * @see [isViewPackageNeeded]
 */
fun isClassPackageNeeded(qualifiedName: String, baseClass: PsiClass, apiLevel: Int, parentClassQualifiedName: String?): Boolean {
  return when {
    parentClassQualifiedName == CLASS_VIEW -> isViewPackageNeeded(qualifiedName, apiLevel)
    parentClassQualifiedName == CLASS_VIEWGROUP -> isViewPackageNeeded(qualifiedName, apiLevel)
    parentClassQualifiedName == CLASS_PREFERENCE -> !isDirectlyInPackage(qualifiedName, "android.preference")
    parentClassQualifiedName == CLASS_PREFERENCE_ANDROIDX.newName() ->
      !isDirectlyInPackage(qualifiedName, "androidx.preference")
    parentClassQualifiedName == CLASS_PREFERENCE_ANDROIDX.oldName() ->
      !isDirectlyInPackage(qualifiedName, "android.support.v7.preference") &&
      !isDirectlyInPackage(qualifiedName, "android.support.v14.preference")
    InheritanceUtil.isInheritor(baseClass, CLASS_VIEW) -> isViewPackageNeeded(qualifiedName, apiLevel)
    InheritanceUtil.isInheritor(baseClass, CLASS_PREFERENCE) -> !isDirectlyInPackage(qualifiedName, "android.preference")
    InheritanceUtil.isInheritor(baseClass, CLASS_PREFERENCE_ANDROIDX.newName()) ->
      !isDirectlyInPackage(qualifiedName, "androidx.preference")
    InheritanceUtil.isInheritor(baseClass, CLASS_PREFERENCE_ANDROIDX.oldName()) ->
      !isDirectlyInPackage(qualifiedName, "android.support.v7.preference") &&
      !isDirectlyInPackage(qualifiedName, "android.support.v14.preference")
    else -> // TODO: removing that makes some of unit tests fail, but leaving it as it is can introduce buggy XML validation
      // Issue with further information: http://b.android.com/186559
      !qualifiedName.startsWith(ANDROID_PKG_PREFIX)
  }
}

/**
 * Returns whether a class with given qualified name resides directly in a package with
 * given prefix (as opposed to reside in a subpackage).
 *
 * For example:
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
    LOG.warn("too deep $colorValue")
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
  return try {
    makeColorWithAlpha(stateColor, state.alpha)
  }
  catch (e: NumberFormatException) {
    // If the alpha value is not valid, Android uses 1.0
    LOG.warn(
      String.format(
        "The alpha attribute in %s/%s does not resolve to a floating point number", stateList.dirName,
        stateList.fileName
      )
    )
    stateColor
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
 * Tries to resolve a given resource value as a square Icon of max 16x16 (scaled size).
 * <ul>
 *   <li> A single color is represented as a [ColorIcon] </li>
 *   <li> A color state list is represented as a [ColorsIcon] with 2 of the possible colors in the list </li>
 *   <li> A drawable is shown as a scaled image if reasonable small version of the drawable exists </li>
 *   <li> Otherwise a null is returned. </li>
 * </ul>
 */
fun RenderResources.resolveAsIcon(value: ResourceValue?, project: Project, facet: AndroidFacet): Icon? {
  return resolveAsColorIcon(value, RESOURCE_ICON_SIZE, project) ?: resolveAsDrawable(value, project, facet)
}

private fun RenderResources.resolveAsColorIcon(value: ResourceValue?, size: Int, project: Project): Icon? {
  val colors = resolveMultipleColors(value, project)
  return when (colors.size) {
    0 -> null
    1 -> JBUIScale.scaleIcon(ColorIcon(size, colors.first(), false))
    else -> JBUIScale.scaleIcon(ColorsIcon(size, colors.last(), findContrastingOtherColor(colors, colors.last())))
  }
}

private fun findContrastingOtherColor(colors: List<Color>, color: Color): Color {
  return colors.maxByOrNull { MaterialColorUtils.colorDistance(it, color) } ?: colors.first()
}

private fun RenderResources.resolveAsDrawable(value: ResourceValue?, project: Project, facet: AndroidFacet): Icon? {
  val bitmap = AndroidAnnotatorUtil.pickSmallestDpiFile(resolveDrawable(value, project)) ?: return null
  return GutterIconCache.getInstance().getIcon(bitmap, this, facet)
}

/**
 * Tries to resolve colors from given resource value. When state list is encountered all
 * possibilities are explored.
 */
// TODO(namespaces): require more information here as context for namespaced lookup
private fun RenderResources.resolveMultipleColors(value: ResourceValue?, project: Project, depth: Int): List<Color> {
  if (depth >= MAX_RESOURCE_INDIRECTION) {
    LOG.warn("too deep $value")
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
      }
      else {
        listOfNotNull(parseColor(state.value))
      }
      for (color in stateColors) {
        try {
          result.add(makeColorWithAlpha(color, state.alpha))
        }
        catch (e: NumberFormatException) {
          // If the alpha value is not valid, Android uses 1.0 so nothing more needs to be done, we simply take color as it is
          result.add(color)
          LOG.warn(String.format(ALPHA_FLOATING_ERROR_FORMAT, stateList.dirName, stateList.fileName))
        }

      }
    }
  }
  else {
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
fun pickAnyLayoutFile(facet: AndroidFacet): VirtualFile? {
  val openFiles = FileEditorManager.getInstance(facet.module.project).openFiles
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

/**
 * Returns the {@link ResourceNamespace} for the given PSI element (including elements from the SDK or AARs), or null if the project is
 * misconfigured.
 *
 * Has to be called inside a read action.
 */
val PsiElement.resourceNamespace: ResourceNamespace?
  get() {
    val projectFileIndex = ProjectFileIndex.getInstance(project)

    // There may be no virtual file for light R and Manifest classes.
    val vFile: VirtualFile? = containingFile.originalFile.virtualFile

    // First, we need to figure out if this file belongs to the project. For PsiFile, ModuleUtil.findModuleForPsiElement will "find" modules
    // that use a given SDK file, which is not what we need. On the other hand, isInSource if false for the manifest (at least in the
    // standard AGP structure) because it doesn't live under src/main/java nor src/main/res, but `getModuleForFile` finds the module by
    // walking up the file system.
    return if (getUserData(ModuleUtilCore.KEY_MODULE) != null
               || (vFile != null && (projectFileIndex.isInSource(vFile) || projectFileIndex.getModuleForFile(vFile) != null))) {
      AndroidFacet.getInstance(this)
        ?.let { ResourceRepositoryManager.getInstance(it) }
        ?.namespace
    }
    else {
      val orderEntries = projectFileIndex.getOrderEntriesForFile(vFile ?: return null)
      when {
        orderEntries.any { it is JdkOrderEntry } -> ResourceNamespace.ANDROID
        // TODO(b/110082720): Handle sources for namespaced libraries and return the correct namespace here.
        orderEntries.any { it is LibraryOrderEntry } -> ResourceNamespace.RES_AUTO
        else -> null
      }
    }
  }

/** A pair of the current ("context") [ResourceNamespace] and a [ResourceNamespace.Resolver] for dealing with prefixes. */
data class ResourceNamespaceContext(val currentNs: ResourceNamespace, val resolver: ResourceNamespace.Resolver)

/** Constructs the right [ResourceNamespaceContext] for a given [XmlElement]. */
fun getNamespacesContext(element: XmlElement): ResourceNamespaceContext? {
  return ResourceNamespaceContext(element.resourceNamespace ?: return null, getNamespaceResolver(element))
}

/** Resolves a given [ResourceUrl] in the context of the given [XmlElement]. */
fun RenderResources.resolve(resourceUrl: ResourceUrl, element: XmlElement): ResourceValue? {
  val (namespace, namespaceResolver) = getNamespacesContext(element) ?: return null
  val resourceReference = resourceUrl.resolve(namespace, namespaceResolver) ?: return null
  return getUnresolvedResource(resourceReference)
}

/** Resolves a given namespace prefix in the context of the [XmlElement]. */
fun XmlElement.resolveResourceNamespace(prefix: String?): ResourceNamespace? {
  val (namespace, namespaceResolver) = getNamespacesContext(this) ?: return null
  return ResourceNamespace.fromNamespacePrefix(prefix, namespace, namespaceResolver)
}

/** Resolves the given [ResourceUrl] in the context of the [XmlElement]. */
fun ResourceUrl.resolve(element: XmlElement): ResourceReference? {
  val (namespace, namespaceResolver) = getNamespacesContext(element) ?: return null
  return resolve(namespace, namespaceResolver)
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
    LOG.warn("too deep $resourceValue")
    return null
  }

  // Not all ResourceValue instances have values (e.g. StyleResourceValue)
  val value = resourceValue.value ?: return null

  if (value.startsWith(PREFIX_RESOURCE_REF)) {
    val resValue = findResValue(value, resourceValue.isFramework) ?: return null
    return resolveStateList(resValue, project, depth + 1)
  }
  else {
    val virtualFile = toFileResourcePathString(value)?.toVirtualFile() ?: return null
    val psiFile = (AndroidPsiUtils.getPsiFileSafely(project, virtualFile) as? XmlFile) ?: return null
    return runReadAction {
      val rootTag = psiFile.rootTag
      if (TAG_SELECTOR == rootTag?.name) {
        val stateList = StateList(psiFile.name, psiFile.containingDirectory.name)
        for (subTag in rootTag.findSubTags(TAG_ITEM)) {
          createStateListState(subTag, resourceValue.isFramework)?.let { stateListState ->
            stateList.addState(stateListState)
          }
        }
        stateList
      }
      else null
    }
  }
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
    }
    else if (trimmed.length != 9) {
      return null
    }
    return Color(longColor.toInt(), true)
  }

  return null
}

/**
 * Converts a color to hex-string representation: #AARRGGBB, including alpha channel.
 * If alpha is FF then the output is #RRGGBB with no alpha component.
 */
fun colorToString(color: Color): String {
  var longColor = (color.red shl 16 or (color.green shl 8) or color.blue).toLong()
  if (color.alpha != 0xFF) {
    longColor = longColor or (color.alpha.toLong() shl 24)
    return String.format("#%08X", longColor)
  }
  return String.format("#%06X", longColor)
}

/**
 * Converts a color to Java/Kotlin hex-string representation: 0xAARRGGBB, including alpha channel.
 *
 * The alpha channel is always included for this format.
 */
fun colorToStringWithAlpha(color: Color): String {
  return String.format("0x%08X", (color.red shl 16 or (color.green shl 8) or color.blue).toLong() or (color.alpha.toLong() shl 24))
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
 * @return the corresponding [VirtualFile], or null
 */
fun RenderResources.resolveDrawable(drawable: ResourceValue?, project: Project): VirtualFile? {
  val resolvedDrawable = resolveNullableResValue(drawable) ?: return null

  var result = resolvedDrawable.value

  // For a StateListDrawable, look up the last state, which is typically the default.
  val stateList = resolveStateList(resolvedDrawable, project)
  if (stateList != null) {
    val states = stateList.states
    if (states.isNotEmpty()) {
      val state = states[states.size - 1]
      // If the state refers to another drawable with a ResourceUrl, we need to resolve it first to a file resource path.
      val resourceValue = ResourceValueImpl(resolvedDrawable.asReference(), state.value)
      result = resolveResValue(resourceValue)?.value
    }
  }

  if (result == null) {
    return null
  }

  return toFileResourcePathString(result)?.toVirtualFile()
}

/**
 * Tries to resolve the given resource value to an actual layout file.
 *
 * @param layout the layout to resolve
 * @return the corresponding [PathString], or null
 */
// TODO(namespaces): require more information here as context for namespaced lookup
fun RenderResources.resolveLayout(layout: ResourceValue?): VirtualFile? {
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
    }
    else {
      return toFileResourcePathString(value)?.toVirtualFile()
    }

    depth++
  }

  return null
}

/**
 * Checks if the given path points to a file resource. The resource path can point
 * to either file on disk, or a ZIP file entry. If the candidate path contains
 * "file:" or "apk:" scheme prefix, the method returns true without doing any I/O.
 * Otherwise, the local file system is checked for existence of the file.
 */
fun isFileResource(candidatePath: String): Boolean =
  candidatePath.startsWith("file:") || candidatePath.startsWith("apk:") || candidatePath.startsWith("jar:") ||
  File(candidatePath).isFile

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
  val androidModel = AndroidModel.get(facet) ?: return name
  val resourcePrefix = androidModel.resourcePrefix ?: return name
  return if (name != null) {
    if (name.startsWith(resourcePrefix)) name else computeResourceName(resourcePrefix, name, folderType)
  }
  else {
    resourcePrefix
  }
}

fun clamp(i: Int, min: Int, max: Int): Int {
  return i.coerceIn(min, max)
}

/**
 * Return all ID URLs in an XML file.
 */
fun findIdUrlsInFile(file: PsiFile): Set<ResourceUrl> {
  val ids = HashSet<ResourceUrl>()
  file.accept(object : PsiRecursiveElementVisitor() {
    override fun visitElement(element: PsiElement) {
      super.visitElement(element)
      if (element is XmlTag) {
        val attrValue = element.getAttributeValue(ATTR_ID, SdkConstants.ANDROID_URI) ?: return
        val url = ResourceUrl.parse(attrValue) ?: return
        if (url.hasValidName()) {
          ids.add((url))
        }
      }
    }
  })
  return ids
}

/**
 * Returns a [ResourceNamespace.Resolver] for the specified tag.
 */
fun getNamespaceResolver(element: XmlElement): ResourceNamespace.Resolver {
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

  val repositoryManager = ResourceRepositoryManager.getInstance(element) ?: return ResourceNamespace.Resolver.EMPTY_RESOLVER

  return if (repositoryManager.namespacing == Namespacing.DISABLED) {
    // In non-namespaced projects, framework is the only namespace, but the resource merger messes with namespaces at build time, so you
    // have to use "android" as the prefix, which is equivalent not to defining a prefix at all (since "android" is the package name of the
    // framework). We also need to keep in mind we recognize "tools" even without the xmlns definition in non-namespaced projects.
    ResourceNamespace.Resolver.TOOLS_ONLY
  }
  else {
    // TODO(b/72688160, namespaces): precompute this to avoid the read lock.
    object : ResourceNamespace.Resolver {
      override fun uriToPrefix(namespaceUri: String): String? = withTag { tag -> tag.getPrefixByNamespace(namespaceUri) }
      override fun prefixToUri(namespacePrefix: String): String? = withTag { tag -> tag.getNamespaceByPrefix(namespacePrefix).nullize() }
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
    }
    else if (textElements.isEmpty()) {
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
  }
  else if (current.node.elementType === XmlElementType.XML_CDATA) {
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
        }
        else {
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

/**
 * Returns the names of [ResourceItem]s with the given namespace, type and visibility in the repository.
 *
 * Intended for code completion.
 */
fun ResourceRepository.getResourceItems(
  namespace: ResourceNamespace,
  type: ResourceType,
  minVisibility: ResourceVisibility
): Collection<String> {
  Preconditions.checkArgument(minVisibility != ResourceVisibility.UNDEFINED)

  val items = getResources(namespace, type) { item ->
    when {
      minVisibility == ResourceVisibility.values()[0] -> true
      item is ResourceItemWithVisibility && item.visibility != ResourceVisibility.UNDEFINED -> item.visibility >= minVisibility
      // Only project resources may not implement ResourceItemWithVisibility.
      else -> true // TODO(b/74324283): Distinguish between PRIVATE and PRIVATE_XML_ONLY for project resources.
    }
  }
  return items.mapTo(HashSet(items.size), ResourceItem::getName)
}

/** Checks if the given [ResourceItem] is available in XML resources in the given [AndroidFacet]. */
fun ResourceItem.isAccessibleInXml(facet: AndroidFacet): Boolean {
  return isAccessible(namespace, type, name, facet)
}

/** Checks if the given [ResourceValue] is available in XML resources in the given [AndroidFacet]. */
fun ResourceValue.isAccessibleInXml(facet: AndroidFacet): Boolean {
  return isAccessible(namespace, resourceType, name, facet)
}

/** Checks if the given [ResourceItem] is available in Java or Kotlin code in the given [AndroidFacet]. */
fun ResourceItem.isAccessibleInCode(facet: AndroidFacet): Boolean {
  return isAccessibleInXml(facet) // TODO(b/74324283): implement the third visibility level.
}

/** Checks if the given [ResourceValue] is available in Java or Kotlin code in the given [AndroidFacet]. */
fun ResourceValue.isAccessibleInCode(facet: AndroidFacet): Boolean {
  return isAccessibleInXml(facet) // TODO(b/74324283): implement the third visibility level.
}

/**
 * Temporary implementation of the accessibility checks, which ignores the "call site" and assumes
 * that only public resources can be accessed.
 */
// TODO(b/74324283): Build the concept of visibility level and scope (private to a given library/module)
//                   into repositories, items and values.
fun isAccessible(namespace: ResourceNamespace, type: ResourceType, name: String, facet: AndroidFacet): Boolean {
  val repositoryManager = ResourceRepositoryManager.getInstance(facet)
  val repository = repositoryManager.getResourcesForNamespace(namespace)
  // For some unclear reason nonexistent resources in the application workspace are treated differently from the framework ones.
  // This non-intuitive behavior is required for the DerivedStyleFinderTest to pass.
  val resource = repository?.getResources(namespace, type, name)?.firstOrNull() ?: return namespace == repositoryManager.namespace
  if (namespace == repositoryManager.namespace && resource.libraryName == null) {
    return true // Project resource.
  }
  if (resource is ResourceItemWithVisibility) {
    return resource.visibility == ResourceVisibility.PUBLIC
  }
  throw AssertionError("Library resource $type/$name of type ${resource.javaClass}" +
                       " doesn't implement ResourceItemWithVisibility")
}

/**
 * Checks if this [ResourceItem] came from an inline id declaration (`@+id`) in an "id generating" file.
 */
fun ResourceItem.isInlineIdDeclaration(): Boolean {
  if (type != ResourceType.ID) return false
  val parentFolderName = source?.parentFileName ?: return false
  return when (val resourceFolderType = ResourceFolderType.getFolderType(parentFolderName)) {
    null, ResourceFolderType.VALUES -> false
    else -> FolderTypeRelationship.isIdGeneratingFolderType(resourceFolderType)
  }
}

/**
 * Ensures that the given namespace is imported in the given XML document.
 */
fun ensureNamespaceImported(file: XmlFile, namespaceUri: String, suggestedPrefix: String? = null): String {
  val rootTag = file.rootTag!!
  val elementFactory = XmlElementFactory.getInstance(file.project)
  if (StringUtil.isEmpty(namespaceUri)) { // The style attribute has an empty namespaceUri:
    return ""
  }
  var prefix = rootTag.getPrefixByNamespace(namespaceUri)
  if (prefix != null) {
    return prefix
  }

  ApplicationManager.getApplication().assertWriteAccessAllowed()

  prefix = suggestedPrefix ?: when (namespaceUri) {
    SdkConstants.TOOLS_URI -> SdkConstants.TOOLS_PREFIX
    SdkConstants.ANDROID_URI -> SdkConstants.ANDROID_NS_NAME
    SdkConstants.AAPT_URI -> SdkConstants.AAPT_PREFIX
    else -> SdkConstants.APP_PREFIX
  }

  if (rootTag.getAttribute(SdkConstants.XMLNS_PREFIX + prefix) != null) {
    val base: String = prefix
    var i = 2
    while (true) {
      prefix = base + i.toString()
      if (rootTag.getAttribute(SdkConstants.XMLNS_PREFIX + prefix) == null) {
        break
      }
      i++
    }
  }
  val name = SdkConstants.XMLNS_PREFIX + prefix
  val xmlnsAttr = elementFactory.createXmlAttribute(name, namespaceUri)
  val attributes = rootTag.attributes
  var next = if (attributes.isNotEmpty()) attributes[0] else null
  for (attribute in attributes) {
    val attributeName = attribute.name
    if (!attributeName.startsWith(SdkConstants.XMLNS_PREFIX) || attributeName > name) {
      next = attribute
      break
    }
  }
  if (next != null) {
    rootTag.addBefore(xmlnsAttr, next)
  }
  else {
    rootTag.add(xmlnsAttr)
  }
  return prefix!!
}

fun requiresDynamicFeatureModuleResources(context: PsiElement): Boolean {
  if (context.language !== XMLLanguage.INSTANCE) {
    return false
  }
  val attribute = PsiTreeUtil.getParentOfType(context, XmlAttribute::class.java) ?: return false
  val domElement = DomManager.getDomManager(context.project).getDomElement(attribute) ?: return false
  val domElementConverter = domElement.converter
  return if (domElementConverter !is ResourceReferenceConverter) {
    false
  }
  else domElementConverter.includeDynamicFeatures
}

fun normalizeXmlResourceValue(value: String): String {
  return StringResourceEscaper.escape(value, false)
}

fun packageToRClass(packageName: String): String {
  return packageName + RESOURCE_CLASS_SUFFIX
}

fun findResourceFields(
  facet: AndroidFacet,
  resClassName: String,
  resourceName: String,
  onlyInOwnPackages: Boolean
): Array<PsiField> {
  return findResourceFields(facet, resClassName, setOf(resourceName), onlyInOwnPackages)
}

/**
 * Like [.findResourceFields] but
 * can match than more than a single field name
 */
fun findResourceFields(
  facet: AndroidFacet,
  resClassName: String,
  resourceNames: Collection<String>,
  onlyInOwnPackages: Boolean
): Array<PsiField> {
  val result: MutableList<PsiField> = ArrayList()
  for (rClass in findRJavaClasses(facet)) {
    findResourceFieldsFromClass(rClass, resClassName, resourceNames, result)
  }
  return result.toTypedArray()
}

fun findStyleableAttrFieldsForAttr(facet: AndroidFacet, attrName: String): Array<PsiField> {
  val result: MutableList<PsiField> = ArrayList()
  for (rClass in findRJavaClasses(facet)) {
    val styleableClass = rClass.findInnerClassByName(ResourceType.STYLEABLE.getName(), false) ?: continue
    for (field in styleableClass.fields) {
      if (field is StyleableAttrLightField) {
        if (field.styleableAttrFieldUrl.attr.name == attrName) {
          result.add(field)
        }
      }
    }
  }
  return result.toTypedArray()
}

fun findStyleableAttrFieldsForStyleable(facet: AndroidFacet, styleableName: String): Array<PsiField> {
  val result: MutableList<PsiField> = ArrayList()
  for (rClass in findRJavaClasses(facet)) {
    val styleableClass = rClass.findInnerClassByName(ResourceType.STYLEABLE.getName(), false) ?: continue
    for (field in styleableClass.fields) {
      if (field is StyleableAttrLightField) {
        if (field.styleableAttrFieldUrl.styleable.name == styleableName) {
          result.add(field)
        }
      }
    }
  }
  return result.toTypedArray()
}

/**
 * Clears the reference resolution cache and triggers the highlighting in the project.
 *
 * This is necessary after a complex Android Resource refactor where the ResourceFolderRepository needs to rescan files to stay up to
 * date. This must be called after the ResourceFolderRepository has scheduled the scan (at the end of the refactor) so that the caches
 * are dropped after the repository is updated.
 */
fun scheduleNewResolutionAndHighlighting(psiManager: PsiManager) {
  ApplicationManager.getApplication().invokeLater {
    psiManager.dropResolveCaches()
    psiManager.dropPsiCaches()
  }
}

private fun findResourceFieldsFromClass(
  rClass: PsiClass,
  resClassName: String, resourceNames: Collection<String>,
  result: MutableList<PsiField>
) {
  val resourceTypeClass = rClass.findInnerClassByName(resClassName, false)
  if (resourceTypeClass != null) {
    for (resourceName in resourceNames) {
      val fieldName = getRJavaFieldName(resourceName)
      val field = resourceTypeClass.findFieldByName(fieldName, false)
      if (field != null) {
        result.add(field)
      }
    }
  }
}

/**
 * Finds all R classes that contain fields for resources from the given module.
 *
 * @param facet [AndroidFacet] of the module to find classes for
 */
private fun findRJavaClasses(facet: AndroidFacet): Collection<PsiClass> {
  val module = facet.module
  if (Manifest.getMainManifest(facet) == null) {
    return emptySet()
  }
  val resourceClassService = facet.module.project.getProjectSystem().getLightResourceClassService()
  return resourceClassService.getLightRClassesContainingModuleResources(module)
}

fun findResourceFieldsForFileResource(file: PsiFile, onlyInOwnPackages: Boolean): Array<PsiField> {
  val facet = AndroidFacet.getInstance(file) ?: return PsiField.EMPTY_ARRAY
  val resourceType = ModuleResourceManagers.getInstance(facet).localResourceManager.getFileResourceType(file)
                     ?: return PsiField.EMPTY_ARRAY
  val resourceName = SdkUtils.fileNameToResourceName(file.name)
  return findResourceFields(facet, resourceType, resourceName, onlyInOwnPackages)
}

fun findResourceFieldsForValueResource(tag: XmlTag, onlyInOwnPackages: Boolean): Array<PsiField> {
  val facet = AndroidFacet.getInstance(tag) ?: return PsiField.EMPTY_ARRAY
  val fileResType = getFolderType(tag.containingFile)
  val resourceType = (if (fileResType == ResourceFolderType.VALUES) getResourceTypeForResourceTag(tag) else null)
                     ?: return PsiField.EMPTY_ARRAY
  val name = tag.getAttributeValue(SdkConstants.ATTR_NAME) ?: return PsiField.EMPTY_ARRAY
  return findResourceFields(facet, resourceType.getName(), name, onlyInOwnPackages)
}

fun getRJavaFieldName(resourceName: String): String {
  if (resourceName.indexOf('.') == -1) {
    return resourceName
  }
  val identifiers = resourceName.split("\\.").toTypedArray()
  val result = StringBuilder(resourceName.length)
  var i = 0
  val n = identifiers.size
  while (i < n) {
    result.append(identifiers[i])
    if (i < n - 1) {
      result.append('_')
    }
    i++
  }
  return result.toString()
}

fun isCorrectAndroidResourceName(resourceName: String): Boolean {
  // TODO: No, we need to check per resource folder type here. There is a validator for this!
  if (resourceName.isEmpty()) {
    return false
  }
  if (resourceName.startsWith(".") || resourceName.endsWith(".")) {
    return false
  }
  val identifiers = resourceName.split("\\.").toTypedArray()
  for (identifier in identifiers) {
    if (!StringUtil.isJavaIdentifier(identifier)) {
      return false
    }
  }
  return true
}

fun getResourceTypeForResourceTag(tag: XmlTag): ResourceType? {
  return ResourceType.fromXmlTag(tag, { obj: XmlTag -> obj.name }, { obj: XmlTag, qname: String? -> obj.getAttributeValue(qname) })
}

fun getResourceClassName(field: PsiField): String? {
  val resourceClass = field.containingClass
  if (resourceClass != null) {
    val parentClass = resourceClass.containingClass
    if (parentClass != null && AndroidUtils.R_CLASS_NAME == parentClass.name && parentClass.containingClass == null) {
      return resourceClass.name
    }
  }
  return null
}

/**
 * Distinguishes whether a reference to a resource in an XML file is a resource declaration or a usage.
 */
fun isResourceDeclaration(resourceElement: PsiElement, targetElement: ResourceReferencePsiElement): Boolean {
  return when (resourceElement) {
    is XmlFile -> targetElement.isEquivalentTo(resourceElement)
    is XmlAttributeValue -> isResourceDeclaration(resourceElement, targetElement)
    else -> false
  }
}

fun isResourceDeclaration(resourceElement: XmlAttributeValue, targetElement: ResourceReferencePsiElement): Boolean {
  if (isIdDeclaration(resourceElement)) { // Layout and Navigation graph files can do inline id declaration.
    return true
  }
  if (ResourceFolderType.VALUES == getFolderType(resourceElement.containingFile)) {
    val attribute = PsiTreeUtil.getParentOfType(resourceElement, XmlAttribute::class.java)
    if (attribute == null || attribute.nameElement.text != SdkConstants.ATTR_NAME) {
      return false
    }
    val tag = PsiTreeUtil.getParentOfType(resourceElement, XmlTag::class.java) ?: return false
    return when (getResourceTypeForResourceTag(tag)) {
      null -> false // Null means no resource type, so this is not a resource declaration.
      ResourceType.ATTR -> tag.getAttribute(SdkConstants.ATTR_FORMAT) != null
      // Styles may have references to other styles in their name, this checks that the full name is the reference we're looking for.
      ResourceType.STYLE -> targetElement.isEquivalentTo(resourceElement)
      else -> true // For all other resource types, this is a declaration.
    }
  }
  return false
}

fun isResourceField(field: PsiField): Boolean {
  var rClass: PsiClass? = field.containingClass ?: return false
  rClass = rClass?.containingClass ?: return false
  if (AndroidUtils.R_CLASS_NAME == rClass.name) {
    val facet = AndroidFacet.getInstance(field)
    if (facet != null) {
      if (isRJavaClass(rClass)) {
        return true
      }
    }
  }
  return false
}

fun isStringResource(tag: XmlTag): Boolean {
  return tag.name == SdkConstants.TAG_STRING && tag.getAttribute(SdkConstants.ATTR_NAME) != null
}

fun isIdDeclaration(attrValue: String?): Boolean {
  return attrValue != null && attrValue.startsWith(SdkConstants.NEW_ID_PREFIX)
}

fun isIdReference(attrValue: String?): Boolean {
  return attrValue != null && attrValue.startsWith(SdkConstants.ID_PREFIX)
}

fun isIdDeclaration(value: XmlAttributeValue): Boolean {
  return isIdDeclaration(value.value)
}

fun isConstraintReferencedIds(nsURI: String?, nsPrefix: String?, key: String?): Boolean {
  return SdkConstants.AUTO_URI == nsURI && SdkConstants.APP_PREFIX == nsPrefix && SdkConstants.CONSTRAINT_REFERENCED_IDS == key
}

fun isConstraintReferencedIds(value: XmlAttributeValue): Boolean {
  val parent = value.parent
  if (parent is XmlAttribute) {
    val nsURI = parent.namespace
    val nsPrefix = parent.namespacePrefix
    val key = parent.localName
    return isConstraintReferencedIds(nsURI, nsPrefix, key)
  }
  return false
}

fun getResourceNameByReferenceText(text: String): String? {
  val i = text.indexOf('/')
  return if (i < text.length - 1) {
    text.substring(i + 1)
  }
  else null
}

fun addValueResource(resType: ResourceType, resources: Resources, value: String?): ResourceElement {
  return when (resType) {
    ResourceType.STRING -> resources.addString()
    ResourceType.PLURALS -> resources.addPlurals()
    ResourceType.DIMEN -> {
      if (value != null && value.trim { it <= ' ' }.endsWith(
          "%")) { // Deals with dimension values in the form of percentages, e.g. "65%"
        val item = resources.addItem()
        item.type.stringValue = ResourceType.DIMEN.getName()
        return item
      }
      if (value != null && value.matches(Regex("[-+]?(\\d+\\.\\d*|\\d*\\.\\d+)"))) {
        // Dimension value is in the form of floating-point number, e.g. "0.24".
        val item = resources.addItem()
        item.type.stringValue = ResourceType.DIMEN.getName()
        item.format.stringValue = "float"
        return item
      }
      resources.addDimen()
    }
    ResourceType.COLOR -> resources.addColor()
    ResourceType.DRAWABLE -> resources.addDrawable()
    ResourceType.STYLE -> resources.addStyle()
    ResourceType.ARRAY ->  // todo: choose among string-array, integer-array and array
      resources.addStringArray()
    ResourceType.INTEGER -> resources.addInteger()
    ResourceType.FRACTION -> resources.addFraction()
    ResourceType.BOOL -> resources.addBool()
    ResourceType.ID -> {
      val item = resources.addItem()
      item.type.value = ResourceType.ID.getName()
      item
    }
    ResourceType.STYLEABLE -> resources.addDeclareStyleable()
    else -> throw IllegalArgumentException("Incorrect resource type")
  }
}

fun getResourceSubdirs(resourceType: ResourceFolderType, resourceDirs: Iterable<VirtualFile?>): List<VirtualFile> {
  val dirs: MutableList<VirtualFile> = ArrayList()
  for (resourcesDir in resourceDirs) {
    if (resourcesDir == null || !resourcesDir.isValid) {
      continue
    }
    for (child in resourcesDir.children) {
      val type = ResourceFolderType.getFolderType(child.name)
      if (resourceType == type) dirs.add(child)
    }
  }
  return dirs
}

fun getDefaultResourceFileName(type: ResourceType): String? {
  return when (type) {
    ResourceType.STRING, ResourceType.PLURALS -> DEFAULT_STRING_RESOURCE_FILE_NAME
    ResourceType.ATTR, ResourceType.STYLEABLE -> "attrs.xml"
    // Lots of unit tests assume drawable aliases are written in "drawables.xml" but going
    // forward let's combine both layouts and drawables in refs.xml as is done in the templates:
    ResourceType.LAYOUT, ResourceType.DRAWABLE ->
      if (ApplicationManager.getApplication().isUnitTestMode) (type.getName() + "s.xml") else "refs.xml"
    in VALUE_RESOURCE_TYPES -> type.getName() + "s.xml"
    else -> null
  }
}

fun getValueResourcesFromElement(resourceType: ResourceType, resources: Resources): List<ResourceElement> {
  val result: MutableList<ResourceElement> = ArrayList()
  when (resourceType) {
    ResourceType.STRING -> result.addAll(resources.strings)
    ResourceType.PLURALS -> result.addAll(resources.pluralses)
    ResourceType.DRAWABLE -> result.addAll(resources.drawables)
    ResourceType.COLOR -> result.addAll(resources.colors)
    ResourceType.DIMEN -> result.addAll(resources.dimens)
    ResourceType.STYLE -> result.addAll(resources.styles)
    ResourceType.ARRAY -> {
      result.addAll(resources.stringArrays)
      result.addAll(resources.integerArrays)
      result.addAll(resources.arrays)
    }
    ResourceType.INTEGER -> result.addAll(resources.integers)
    ResourceType.FRACTION -> result.addAll(resources.fractions)
    ResourceType.BOOL -> result.addAll(resources.bools)
    else -> {
    }
  }
  for (item in resources.items) {
    val type = item.type.value
    if (resourceType.getName() == type) {
      result.add(item)
    }
  }
  return result
}

private fun isLocalResourceDirectoryInAnyVariant(dir: PsiDirectory): Boolean {
  val vf = dir.virtualFile
  val module = ModuleUtilCore.findModuleForFile(vf, dir.project) ?: return false
  val facet = AndroidFacet.getInstance(module) ?: return false
  for (provider in SourceProviders.getInstance(facet).currentAndSomeFrequentlyUsedInactiveSourceProviders) {
    for (resDir in provider.resDirectories)
      if (vf == resDir) {
        return true
      }
  }
  for (resDir in AndroidRootUtil.getResourceOverlayDirs(facet)) {
    if (vf == resDir) {
      return true
    }
  }
  return false
}

fun isInResourceSubdirectoryInAnyVariant(file: PsiFile, resourceType: String? = null): Boolean {
  val dir = file.originalFile.containingDirectory ?: return false
  return isResourceSubdirectory(dir, resourceType, searchInAllVariants = true)
}

fun isInResourceSubdirectory(file: PsiFile, resourceType: String? = null): Boolean {
  val dir = file.originalFile.containingDirectory ?: return false
  return isResourceSubdirectory(dir, resourceType, searchInAllVariants = false)
}

fun isResourceSubdirectory(directory: PsiDirectory, resourceType: String? = null, searchInAllVariants: Boolean = false): Boolean {
  var dir: PsiDirectory? = directory
  val dirName = dir!!.name
  if (resourceType != null) {
    val typeLength = resourceType.length
    val dirLength = dirName.length
    if (dirLength < typeLength || !dirName.startsWith(resourceType) || dirLength > typeLength && dirName[typeLength] != '-') {
      return false
    }
  }
  dir = dir.parent
  if (dir == null) {
    return false
  }
  if ("default" == dir.name) {
    dir = dir.parentDirectory
  }
  return dir != null && isResourceDirectory(dir, searchInAllVariants)
}

fun isLocalResourceDirectory(dir: VirtualFile, project: Project): Boolean {
  val module = ModuleUtilCore.findModuleForFile(dir, project)
  if (module != null) {
    val facet = AndroidFacet.getInstance(module)
    return facet != null && ModuleResourceManagers.getInstance(facet).localResourceManager.isResourceDir(dir)
  }
  return false
}

fun isResourceFile(file: VirtualFile, facet: AndroidFacet): Boolean {
  val parent = file.parent
  val resDir = parent?.parent
  return resDir != null && ModuleResourceManagers.getInstance(facet).localResourceManager.isResourceDir(resDir)
}

fun isResourceDirectory(directory: PsiDirectory, searchInAllVariants: Boolean = false): Boolean {
  var dir: PsiDirectory? = directory
  // check facet settings
  val vf = dir!!.virtualFile
  if (searchInAllVariants && isLocalResourceDirectoryInAnyVariant(directory)) {
    return true
  }
  if (!searchInAllVariants && isLocalResourceDirectory(vf, dir.project)) {
    return true
  }
  if (SdkConstants.FD_RES != dir.name) return false
  dir = dir.parent
  if (dir != null) {
    val protocol = vf.fileSystem.protocol
    // TODO: Figure out a better way to check if a directory belongs to proto AAR resources.
    if (protocol == JarFileSystem.PROTOCOL || protocol == ApkFileSystem.PROTOCOL) {
      return true // The file belongs either to res.apk or a source attachment JAR of a library.
    }
    if (dir.findFile(SdkConstants.FN_ANDROID_MANIFEST_XML) != null) {
      return true
    }
    // The method can be invoked for a framework resource directory, so we should check it.
    dir = dir.parent
    if (dir != null) {
      if (containsAndroidJar(dir)) return true
      dir = dir.parent
      if (dir != null) {
        return containsAndroidJar(dir)
      }
    }
  }
  return false
}

private fun containsAndroidJar(psiDirectory: PsiDirectory): Boolean {
  return psiDirectory.findFile(SdkConstants.FN_FRAMEWORK_LIBRARY) != null
}

fun isRJavaClass(psiClass: PsiClass): Boolean {
  return psiClass is AndroidRClassBase
}

fun isManifestClass(psiClass: PsiClass): Boolean {
  return psiClass is ManifestClass
}

fun createValueResource(
  project: Project,
  resDir: VirtualFile,
  resourceName: String,
  resourceValue: String?,
  resourceType: ResourceType,
  fileName: String,
  dirNames: List<String>,
  afterAddedProcessor: Processor<ResourceElement>
): Boolean {
  return try {
    addValueResource(
      project,
      resDir,
      resourceName,
      resourceType,
      fileName,
      dirNames,
      resourceValue,
      afterAddedProcessor
    )
  }
  catch (e: Exception) {
    val message = CreateElementActionBase.filterMessage(e.message)
    if (message == null || message.isEmpty()) {
      LOG.error(e)
    }
    else {
      LOG.info(e)
      AndroidUtils.reportError(project, message)
    }
    false
  }
}

@JvmOverloads
fun createValueResource(
  project: Project,
  resDir: VirtualFile,
  resourceName: String,
  resourceType: ResourceType,
  fileName: String,
  dirNames: List<String>,
  value: String,
  outTags: MutableList<ResourceElement?>? = null
): Boolean {
  return createValueResource(
    project,
    resDir,
    resourceName,
    value,
    resourceType,
    fileName,
    dirNames) { element: ResourceElement ->
    if (value.isNotEmpty()) {
      val s = if (resourceType == ResourceType.STRING) normalizeXmlResourceValue(value) else value
      element.stringValue = s
    }
    else if (resourceType == ResourceType.STYLEABLE || resourceType == ResourceType.STYLE) {
      element.stringValue = "value"
      element.xmlTag!!.value.text = ""
    }
    outTags?.add(element)
    true
  }
}

private fun addValueResource(
  project: Project,
  resDir: VirtualFile,
  resourceName: String,
  resourceType: ResourceType,
  fileName: String,
  dirNames: List<String>,
  resourceValue: String?,
  afterAddedProcessor: Processor<ResourceElement>
): Boolean {
  if (dirNames.isEmpty()) {
    return false
  }
  val resFiles = arrayOfNulls<VirtualFile>(dirNames.size)
  run {
    var i = 0
    val n = dirNames.size
    while (i < n) {
      val dirName = dirNames[i]
      resFiles[i] = WriteAction.compute<VirtualFile?, Exception> { findOrCreateResourceFile(project, resDir, fileName, dirName) }
      if (resFiles[i] == null) {
        return false
      }
      i++
    }
  }
  if (!ReadonlyStatusHandler.ensureFilesWritable(project, *resFiles)) {
    return false
  }
  val resourcesElements = arrayOfNulls<Resources>(resFiles.size)
  for (i in resFiles.indices) {
    val resources: Resources? = AndroidUtils.loadDomElement(project, resFiles[i]!!, Resources::class.java)
    if (resources == null) {
      AndroidUtils.reportError(project, AndroidBundle.message("not.resource.file.error", fileName))
      return false
    }
    resourcesElements[i] = resources
  }
  val psiFiles: MutableList<PsiFile> = Lists.newArrayListWithExpectedSize(resFiles.size)
  val manager = PsiManager.getInstance(project)
  for (file in resFiles) {
    val psiFile = manager.findFile(file!!)
    if (psiFile != null) {
      psiFiles.add(psiFile)
    }
  }
  writeCommandAction(project, *psiFiles.toTypedArray()).withName("Add Resource").run<RuntimeException> {
    for (resources in resourcesElements) {
      if (resourceType == ResourceType.ATTR) {
        resources!!.addAttr().name.setValue(
          ResourceReference.attr(
            ResourceNamespace.TODO(), resourceName))
      }
      else {
        val element = addValueResource(
          resourceType, resources!!, resourceValue)
        element.name.value = resourceName
        afterAddedProcessor.process(element)
      }
    }
  }
  return true
}

/**
 * Sets a new value for a resource.
 *
 * @param project the project containing the resource
 * @param resDir the res/ directory containing the resource
 * @param name the name of the resource to be modified
 * @param newValue the new resource value
 * @param fileName the resource values file name
 * @param dirNames list of values directories where the resource should be changed
 * @param useGlobalCommand if true, the undo operation will be registered globally. This allows
 *     the command to be undone from anywhere in the IDE and not only the XML editor
 * @return true if the resource value was changed
 */
fun changeValueResource(
  project: Project,
  resDir: VirtualFile,
  name: String,
  resourceType: ResourceType,
  newValue: String,
  fileName: String,
  dirNames: List<String>,
  useGlobalCommand: Boolean
): Boolean {
  if (dirNames.isEmpty()) {
    return false
  }
  val resFiles = Lists.newArrayListWithExpectedSize<VirtualFile>(
    dirNames.size)
  for (dirName in dirNames) {
    val resFile = findResourceFile(resDir, fileName, dirName)
    if (resFile != null) {
      resFiles.add(resFile)
    }
  }
  if (!ensureFilesWritable(project, resFiles)) {
    return false
  }
  val resourcesElements = arrayOfNulls<Resources>(
    resFiles.size)
  for (i in resFiles.indices) {
    val resources = AndroidUtils.loadDomElement(
      project, resFiles[i], Resources::class.java)
    if (resources == null) {
      AndroidUtils.reportError(project, AndroidBundle.message("not.resource.file.error", fileName))
      return false
    }
    resourcesElements[i] = resources
  }
  val psiFiles: MutableList<PsiFile> = Lists.newArrayListWithExpectedSize(resFiles.size)
  val manager = PsiManager.getInstance(project)
  for (file in resFiles) {
    val psiFile = manager.findFile(file!!)
    if (psiFile != null) {
      psiFiles.add(psiFile)
    }
  }

  return writeCommandAction(project, *psiFiles.toTypedArray())
    .withName("Change " + resourceType.getName() + " Resource")
    .compute<Boolean, Exception> {
      if (useGlobalCommand) {
        CommandProcessor.getInstance().markCurrentCommandAsGlobal(project)
      }
      var result = false
      for (resources in resourcesElements) {
        for (element in getValueResourcesFromElement(resourceType, resources!!)) {
          val value = element.name.stringValue
          if (name == value) {
            element.stringValue = newValue
            result = true
          }
        }
      }
      result
    }
}

private fun findResourceFile(
  resDir: VirtualFile,
  fileName: String,
  dirName: String
): VirtualFile? = resDir.findChild(dirName)?.findChild(fileName)

private fun findOrCreateResourceFile(
  project: Project,
  resDir: VirtualFile,
  fileName: String,
  dirName: String
): VirtualFile? {
  val dir = AndroidUtils.createChildDirectoryIfNotExist(project, resDir, dirName)
  val dirPath = FileUtil.toSystemDependentName(resDir.path + '/' + dirName)
  if (dir == null) {
    AndroidUtils.reportError(project, AndroidBundle.message("android.cannot.create.dir.error", dirPath))
    return null
  }
  val file = dir.findChild(fileName)
  if (file != null) {
    return file
  }
  AndroidFileTemplateProvider
    .createFromTemplate(project, dir, AndroidFileTemplateProvider.VALUE_RESOURCE_FILE_TEMPLATE, fileName)
  val result = dir.findChild(fileName)
  if (result == null) {
    AndroidUtils.reportError(project, AndroidBundle.message("android.cannot.create.file.error",
                                                            dirPath + File.separatorChar + fileName))
  }
  return result
}

fun getReferredResourceOrManifestField(
  facet: AndroidFacet,
  exp: KtSimpleNameExpression,
  className: String?,
  localOnly: Boolean
): ReferredResourceFieldInfo? {
  val resFieldName = exp.getReferencedName()
  if (resFieldName.isEmpty()) {
    return null
  }
  val resClassReference = exp.getPreviousInQualifiedChain() as? KtSimpleNameExpression ?: return null
  val resClassName = resClassReference.getReferencedName()
  if (resClassName.isEmpty() || className != null && className != resClassName) {
    return null
  }
  val rClassReference = resClassReference.getPreviousInQualifiedChain() as? KtSimpleNameExpression ?: return null
  val resolvedElement: PsiElement = rClassReference.mainReference.resolve() as? PsiClass ?: return null
  val aClass = resolvedElement as PsiClass
  val classShortName = aClass.name!!
  val fromManifest = AndroidUtils.MANIFEST_CLASS_NAME == classShortName
  if (!fromManifest && !isRJavaClass(aClass)) {
    return null
  }
  val qName = aClass.qualifiedName ?: return null
  val resolvedModule = ModuleUtilCore.findModuleForPsiElement(resolvedElement)
  if (!localOnly) {
    if (SdkConstants.CLASS_R == qName || AndroidInternalRClassFinder.INTERNAL_R_CLASS_QNAME == qName) {
      return ReferredResourceFieldInfo(resClassName, resFieldName, resolvedModule, ResourceNamespace.ANDROID, false)
    }
  }
  return if (if (fromManifest) !isManifestClass(aClass) else !isRJavaClass(aClass)) {
    null
  }
  else {
    ReferredResourceFieldInfo(
      resClassName,
      resFieldName,
      resolvedModule,
      getRClassNamespace(facet, qName),
      fromManifest
    )
  }
}

@JvmOverloads
fun getReferredResourceOrManifestField(
  facet: AndroidFacet,
  exp: PsiReferenceExpression,
  className: String? = null,
  localOnly: Boolean
): ReferredResourceFieldInfo? {
  val resFieldName = exp.referenceName
  if (resFieldName.isNullOrEmpty()) {
    return null
  }
  var qExp: PsiExpression = exp.qualifierExpression as? PsiReferenceExpression ?: return null
  val resClassReference = qExp as PsiReferenceExpression
  val resClassName = resClassReference.referenceName
  if (resClassName.isNullOrEmpty() || className != null && className != resClassName) {
    return null
  }
  qExp = resClassReference.qualifierExpression ?: return null
  if (qExp !is PsiReferenceExpression) {
    return null
  }
  val psiClass = qExp.resolve() as? PsiClass ?: return null
  val resolvedModule = ModuleUtilCore.findModuleForPsiElement(psiClass)
  val classShortName = psiClass.name!!
  val fromManifest = AndroidUtils.MANIFEST_CLASS_NAME == classShortName
  if (!fromManifest && AndroidUtils.R_CLASS_NAME != classShortName) {
    return null
  }
  val qName = psiClass.qualifiedName ?: return null
  if (!localOnly) {
    if (SdkConstants.CLASS_R == qName || AndroidInternalRClassFinder.INTERNAL_R_CLASS_QNAME == qName) {
      return ReferredResourceFieldInfo(resClassName, resFieldName, resolvedModule, ResourceNamespace.ANDROID, false)
    }
  }
  return if (if (fromManifest) !isManifestClass(psiClass) else !isRJavaClass(psiClass)) {
    null
  }
  else ReferredResourceFieldInfo(resClassName, resFieldName, resolvedModule, getRClassNamespace(facet, qName), fromManifest)
}

fun getRClassNamespace(facet: AndroidFacet, qName: String?): ResourceNamespace {
  return if (ResourceRepositoryManager.getInstance(facet).namespacing == Namespacing.DISABLED) {
    ResourceNamespace.RES_AUTO
  }
  else {
    ResourceNamespace.fromPackageName(StringUtil.getPackageName(qName!!))
  }
}

/**
 * Utility method suitable for Comparator implementations which order resource files,
 * which will sort files by base folder followed by alphabetical configurations. Prioritizes
 * XML files higher than non-XML files.
 */
fun compareResourceFiles(file1: VirtualFile?, file2: VirtualFile?): Int {
  return if (file1 == file2) {
    0
  }
  else if (file1 != null && file2 != null) {
    val xml1 = file1.fileType === XmlFileType.INSTANCE
    val xml2 = file2.fileType === XmlFileType.INSTANCE
    if (xml1 != xml2) {
      return if (xml1) -1 else 1
    }
    val parent1 = file1.parent
    val parent2 = file2.parent
    if (parent1 != null && parent2 != null && parent1 != parent2) {
      val parentName1 = parent1.name
      val parentName2 = parent2.name
      val qualifier1 = parentName1.indexOf('-') != -1
      val qualifier2 = parentName2.indexOf('-') != -1
      if (qualifier1 != qualifier2) {
        return if (qualifier1) 1 else -1
      }
      if (qualifier1) { // Sort in FolderConfiguration order
        val config1 = FolderConfiguration.getConfigForFolder(parentName1)
        val config2 = FolderConfiguration.getConfigForFolder(parentName2)
        if (config1 != null && config2 != null) {
          return config1.compareTo(config2)
        }
        else if (config1 != null) {
          return -1
        }
        else if (config2 != null) {
          return 1
        }
        val delta = parentName1.compareTo(parentName2)
        if (delta != 0) {
          return delta
        }
      }
    }
    file1.path.compareTo(file2.path)
  }
  else if (file1 != null) {
    -1
  }
  else {
    1
  }
}

fun ensureFilesWritable(project: Project, files: Collection<VirtualFile>): Boolean {
  return !ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(files).hasReadonlyFiles()
}

/**
 * Grabs resource directories from the given facets and pairs the directory with an arbitrary
 * AndroidFacet which happens to depend on the directory.
 *
 * @param facets set of facets which may have resource directories
 */
fun getResourceDirectoriesForFacets(facets: List<AndroidFacet>): Map<VirtualFile, AndroidFacet> {
  val resDirectories = HashMap<VirtualFile, AndroidFacet>()
  for (facet in facets) {
    for (resourceDir in ResourceFolderManager.getInstance(facet).folders) {
      if (!resDirectories.containsKey(resourceDir)) {
        resDirectories[resourceDir] = facet
      }
    }
  }
  return resDirectories
}

/** Returns the [PsiFile] corresponding to the source of the given resource item, if possible.  */
fun getItemPsiFile(project: Project, item: ResourceItem): PsiFile? {
  if (project.isDisposed) {
    return null
  }
  if (item is PsiResourceItem) {
    return item.psiFile
  }
  val virtualFile = item.getSourceAsVirtualFile()
  if (virtualFile != null) {
    val psiManager = PsiManager.getInstance(project)
    return psiManager.findFile(virtualFile)
  }
  return null
}

/**
 * Returns the XML attribute containing declaration of the given ID resource.
 *
 * @param project the project containing the resource
 * @param idResource the ID resource
 * @return
 */
fun getIdDeclarationAttribute(project: Project, idResource: ResourceItem): XmlAttribute? {
  assert(idResource.type == ResourceType.ID)
  val resourceName = idResource.name
  val predicate = { attribute: XmlAttribute ->
    val attrValue = attribute.value
    isIdDeclaration(attrValue) && ResourceUrl.parse(attrValue!!)?.name == resourceName
  }
  // TODO: Consider storing XmlAttribute instead of XmlTag in PsiResourceItem.
  val scope = (idResource as? PsiResourceItem)?.tag ?: getItemPsiFile(project, idResource)
  val attributes: Collection<XmlAttribute> = SyntaxTraverser.psiTraverser(scope).traverse().filterIsInstanceAnd(predicate)
  return attributes.firstOrNull { it.name == "android:id"} ?: attributes.firstOrNull()
}

/**
 * Checks if the ID resource is a definition of that ID.
 */
fun ResourceItem.isIdDefinition(project: Project): Boolean {
  assert(type == ResourceType.ID)
  return !isInlineIdDeclaration() || getIdDeclarationAttribute(project, this)?.name == "android:id"
}

/**
 * Returns the [XmlAttributeValue] defining the given resource item. This is only defined for resource items which are not file
 * based.
 *
 * @see ResourceItem.isFileBased
 */
fun getDeclaringAttributeValue(project: Project, item: ResourceItem): XmlAttributeValue? {
  if (item.isFileBased) {
    return null
  }
  val attribute: XmlAttribute? = if (item.isInlineIdDeclaration()) {
    getIdDeclarationAttribute(project, item)
  }
  else {
    val tag = getItemTag(project, item)
    tag?.getAttribute(SdkConstants.ATTR_NAME)
  }
  return attribute?.valueElement
}

/**
 * Returns the [XmlTag] corresponding to the given resource item. This is only defined for resource items in value files.
 *
 * @see .getDeclaringAttributeValue
 */
fun getItemTag(project: Project, item: ResourceItem): XmlTag? {
  ApplicationManager.getApplication().assertReadAccessAllowed()
  if (item.isFileBased) {
    return null
  }
  if (item is PsiResourceItem) {
    return item.tag
  }
  val psiFile = getItemPsiFile(project, item) as? XmlFile ?: return null
  val rootTag = psiFile.rootTag
  if (rootTag == null || !rootTag.isValid || rootTag.name != SdkConstants.TAG_RESOURCES) {
    return null
  }
  for (tag in rootTag.subTags) {
    ProgressManager.checkCanceled()
    if (!tag.isValid) {
      continue
    }
    val tagResourceType = getResourceTypeForResourceTag(tag)
    if (item.type == tagResourceType && item.name == tag.getAttributeValue(SdkConstants.ATTR_NAME)) {
      return tag
    }
    // Consider children of declare-styleable.
    if (item.type == ResourceType.ATTR && tagResourceType == ResourceType.STYLEABLE) {
      val attrs = tag.subTags
      for (attr in attrs) {
        if (!attr.isValid) {
          continue
        }
        if (item.name == attr.getAttributeValue(SdkConstants.ATTR_NAME) && (attr.getAttribute(
            SdkConstants.ATTR_FORMAT) != null || attr.subTags.isNotEmpty())) {
          return attr
        }
      }
    }
  }
  return null
}

fun getResourceElementFromSurroundingValuesTag(element: PsiElement): ResourceReferencePsiElement? {
  val file = element.containingFile
  if (file != null && isInResourceSubdirectory(file, ResourceFolderType.VALUES.getName()) &&
      (element.text == null || ResourceUrl.parse(element.text) == null)) {
    val valuesResource = PsiTreeUtil.getParentOfType(element, XmlTag::class.java)
    if (valuesResource != null && VALUE_RESOURCE_TYPES.contains(getResourceTypeForResourceTag(valuesResource))) {
      val attribute = valuesResource.getAttribute(SdkConstants.ATTR_NAME) ?: return null
      val valueElement = attribute.valueElement ?: return null
      val elementReference = valueElement.reference ?: return null
      val resolvedElement = elementReference.resolve()
      return if (resolvedElement is ResourceReferencePsiElement) resolvedElement else null
    }
  }
  return null
}

fun getViewTag(item: ResourceItem): String? {
  if (item is PsiResourceItem) {
    val tag = item.tag
    val id = item.getName()
    if (tag != null && tag.isValid // Make sure that the id attribute we're searching for is actually
        // defined for this tag, not just referenced from this tag.
        // For example, we could have
        //    <Button a:alignLeft="@+id/target" a:id="@+id/something ...>
        // and this should *not* return "Button" as the view tag for
        // @+id/target!
        && id == stripIdPrefix(tag.getAttributeValue(ATTR_ID, SdkConstants.ANDROID_URI))) {
      return tag.name
    }
    val file = item.psiFile
    if (file is XmlFile && file.isValid()) {
      val rootTag = file.rootTag
      if (rootTag != null && rootTag.isValid) {
        return findViewTag(rootTag, id)
      }
    }
  }
  return null
}

private fun findViewTag(tag: XmlTag, target: String): String? {
  val id = tag.getAttributeValue(ATTR_ID, SdkConstants.ANDROID_URI)
  if (id != null && id.endsWith(target) && target == stripIdPrefix(id)) {
    return tag.name
  }
  for (sub in tag.subTags) {
    if (sub.isValid) {
      val found = findViewTag(sub, target)
      if (found != null) {
        return found
      }
    }
  }
  return null
}

fun createFileResource(
  fileName: String,
  resSubdir: PsiDirectory,
  rootTagName: String,
  resourceType: String,
  valuesResourceFile: Boolean
): XmlFile {
  val manager = FileTemplateManager.getInstance(resSubdir.project)
  val templateName = getTemplateName(resourceType, valuesResourceFile, rootTagName)
  val template = manager.getJ2eeTemplate(templateName)
  val properties = Properties()
  if (!valuesResourceFile) {
    properties.setProperty(ROOT_TAG_PROPERTY, rootTagName)
  }
  if (ResourceType.LAYOUT.getName() == resourceType) {
    val module = ModuleUtilCore.findModuleForPsiElement(resSubdir)
    val platform = if (module != null) AndroidPlatform.getInstance(
      module)
    else null
    val apiLevel = platform?.apiLevel ?: -1
    val value = if (apiLevel == -1 || apiLevel >= 8) "match_parent" else "fill_parent"
    properties.setProperty(LAYOUT_WIDTH_PROPERTY, value)
    properties.setProperty(LAYOUT_HEIGHT_PROPERTY, value)
  }
  val createdElement = FileTemplateUtil.createFromTemplate(template, fileName, properties, resSubdir)
  assert(createdElement is XmlFile)
  return createdElement as XmlFile
}

private fun getTemplateName(resourceType: String, valuesResourceFile: Boolean, rootTagName: String): String {
  if (valuesResourceFile) {
    return AndroidFileTemplateProvider.VALUE_RESOURCE_FILE_TEMPLATE
  }
  if (ResourceType.LAYOUT.getName() == resourceType && SdkConstants.TAG_LAYOUT != rootTagName && SdkConstants.VIEW_MERGE != rootTagName) {
    return if (AndroidUtils.TAG_LINEAR_LAYOUT == rootTagName) AndroidFileTemplateProvider.LAYOUT_RESOURCE_VERTICAL_FILE_TEMPLATE
           else AndroidFileTemplateProvider.LAYOUT_RESOURCE_FILE_TEMPLATE
  }
  return if (ResourceType.NAVIGATION.getName() == resourceType) {
    AndroidFileTemplateProvider.NAVIGATION_RESOURCE_FILE_TEMPLATE
  }
  else AndroidFileTemplateProvider.RESOURCE_FILE_TEMPLATE
}

fun getFieldNameByResourceName(styleName: String): String {
  var i = 0
  val n = styleName.length
  while (i < n) {
    val c = styleName[i]
    if (c == '.' || c == '-' || c == ':') {
      return styleName.replace('.', '_').replace('-', '_').replace(':', '_')
    }
    i++
  }
  return styleName
}

/**
 * Finds and returns the resource files named stateListName in the directories listed in dirNames.
 * If some directories do not contain a file with that name, creates such a resource file.
 *
 * @param project the project
 * @param resDir the res/ dir containing the directories under investigation
 * @param folderType Type of the directories under investigation
 * @param resourceType Type of the resource file to create if necessary
 * @param stateListName Name of the resource files to be returned
 * @param dirNames List of directory names to look into
 * @return List of found and created files
 */
fun findOrCreateStateListFiles(
  project: Project,
  resDir: VirtualFile,
  folderType: ResourceFolderType,
  resourceType: ResourceType,
  stateListName: String,
  dirNames: List<String>
): List<VirtualFile>? {
  val manager = PsiManager.getInstance(project)
  val files: MutableList<VirtualFile> = Lists.newArrayListWithCapacity(dirNames.size)
  val foundFiles = writeCommandAction(project).withName("Find statelists files").compute<Boolean, Exception> {
    try {
      var fileName = stateListName
      if (!stateListName.endsWith(DOT_XML)) {
        fileName += DOT_XML
      }
      for (dirName in dirNames) {
        val dirPath = FileUtil.toSystemDependentName(resDir.path + '/' + dirName)
        val dir: VirtualFile = AndroidUtils.createChildDirectoryIfNotExist(project, resDir, dirName)
                               ?: throw IOException("cannot make " + resDir + File.separatorChar + dirName)
        var file = dir.findChild(fileName)
        if (file != null) {
          files.add(file)
          continue
        }
        val directory = manager.findDirectory(dir) ?: throw IOException("cannot find " + resDir + File.separatorChar + dirName)
        createFileResource(
          fileName,
          directory,
          CreateTypedResourceFileAction.getDefaultRootTagByResourceType(folderType),
          resourceType.getName(),
          false
        )
        file = dir.findChild(fileName)
        if (file == null) {
          throw IOException("cannot find " + Joiner.on(File.separatorChar).join(resDir, dirPath, fileName))
        }
        files.add(file)
      }
      true
    }
    catch (e: Exception) {
      LOG.error(e.message)
      false
    }
  }

  return if (foundFiles) files else null
}

fun buildResourceNameFromStringValue(resourceValue: String): String? {
  val result = RESOURCE_NAME_DISALLOWED_CHARS.trimAndCollapseFrom(resourceValue, '_').toLowerCaseAsciiOnly()

  if (result.isEmpty()) return null
  if (CharMatcher.inRange('0', '9').matches(result[0])) return "_$result"
  return result
}

/**
 * Data gathered from a reference to field of an aapt-generated class: R or Manifest.
 */
data class ReferredResourceFieldInfo(
  val className: String,
  val fieldName: String,
  val resolvedModule: Module?,
  val namespace: ResourceNamespace,
  val isFromManifest: Boolean
)
