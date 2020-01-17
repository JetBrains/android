/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:JvmName("AndroidResourceUtil")

package org.jetbrains.android.util

import com.android.SdkConstants
import com.android.builder.model.AaptOptions.Namespacing
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.ValueXmlHelper
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.idea.apk.viewer.ApkFileSystem
import com.android.tools.idea.kotlin.getPreviousInQualifiedChain
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.res.AndroidInternalRClassFinder
import com.android.tools.idea.res.AndroidRClassBase
import com.android.tools.idea.res.PsiResourceItem
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.res.StateList
import com.android.tools.idea.res.getFolderType
import com.android.tools.idea.res.getSourceAsVirtualFile
import com.android.tools.idea.res.isInlineIdDeclaration
import com.android.tools.idea.res.psi.ResourceReferencePsiElement
import com.android.tools.idea.res.psi.ResourceReferencePsiElement.Companion.create
import com.android.tools.lint.detector.api.stripIdPrefix
import com.android.utils.SdkUtils
import com.google.common.base.Joiner
import com.google.common.collect.Lists
import com.google.common.collect.Sets
import com.intellij.ide.actions.CreateElementActionBase
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.FileTemplateUtil
import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction.writeCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
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
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.ResolveResult
import com.intellij.psi.XmlElementFactory
import com.intellij.psi.impl.PsiModificationTrackerImpl
import com.intellij.psi.search.PsiElementProcessor.FindFilteredElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.Processor
import com.intellij.util.xml.DomManager
import org.jetbrains.android.AndroidFileTemplateProvider
import org.jetbrains.android.actions.CreateTypedResourceFileAction
import org.jetbrains.android.augment.ManifestClass
import org.jetbrains.android.augment.StyleableAttrLightField
import org.jetbrains.android.dom.AndroidDomElement
import org.jetbrains.android.dom.color.ColorSelector
import org.jetbrains.android.dom.converters.ResourceReferenceConverter
import org.jetbrains.android.dom.drawable.DrawableSelector
import org.jetbrains.android.dom.manifest.Manifest
import org.jetbrains.android.dom.resources.ResourceElement
import org.jetbrains.android.dom.resources.Resources
import org.jetbrains.android.dom.wrappers.LazyValueResourceElementWrapper
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.ResourceFolderManager.Companion.getInstance
import org.jetbrains.android.resourceManagers.ModuleResourceManagers
import org.jetbrains.android.sdk.AndroidPlatform
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import java.io.File
import java.io.IOException
import java.util.ArrayList
import java.util.Comparator
import java.util.EnumSet
import java.util.HashMap
import java.util.Properties
import java.util.function.Function

private val LOG: Logger = logger(::LOG)

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

const val ROOT_TAG_PROPERTY = "ROOT_TAG"
const val LAYOUT_WIDTH_PROPERTY = "LAYOUT_WIDTH"
const val LAYOUT_HEIGHT_PROPERTY = "LAYOUT_HEIGHT"
private const val RESOURCE_CLASS_SUFFIX = "." + AndroidUtils.R_CLASS_NAME

/**
 * Comparator which orders [PsiElement] items into a priority order most suitable for presentation
 * to the user; for example, it prefers base resource folders such as `values/` over resource
 * folders such as `values-en-rUS`
 */
@JvmField
val RESOURCE_ELEMENT_COMPARATOR = Comparator { e1: PsiElement, e2: PsiElement ->
  if (e1 is LazyValueResourceElementWrapper && e2 is LazyValueResourceElementWrapper) {
    return@Comparator e1.compareTo(e2)
  }
  val delta = compareResourceFiles(e1.containingFile, e2.containingFile)
  if (delta != 0) delta else e1.textOffset - e2.textOffset
}

/**
 * Comparator for [ResolveResult] using [RESOURCE_ELEMENT_COMPARATOR] on the result PSI element.
 */
@JvmField
val RESOLVE_RESULT_COMPARATOR: Comparator<ResolveResult> = Comparator.nullsLast(
  Comparator.comparing(
    Function { obj: ResolveResult -> obj.element },
    RESOURCE_ELEMENT_COMPARATOR
  )
)

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
  return ValueXmlHelper.escapeResourceString(value, false)
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
 *
 */
fun scheduleNewResolutionAndHighlighting(psiManager: PsiManager) {
  ApplicationManager.getApplication().invokeLater {
    psiManager.dropResolveCaches()
    (psiManager.modificationTracker as PsiModificationTrackerImpl).incCounter()
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
 * @return
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
  val resourceType = (if (fileResType == ResourceFolderType.VALUES) getResourceTypeForResourceTag(
    tag)
  else null)
                     ?: return PsiField.EMPTY_ARRAY
  val name = tag.getAttributeValue(SdkConstants.ATTR_NAME) ?: return PsiField.EMPTY_ARRAY
  return findResourceFields(facet, resourceType.getName(), name, onlyInOwnPackages)
}

fun findStyleableAttributeFields(tag: XmlTag, onlyInOwnPackages: Boolean): Array<PsiField> {
  val tagName = tag.name
  if (SdkConstants.TAG_DECLARE_STYLEABLE == tagName) {
    val styleableName = tag.getAttributeValue(SdkConstants.ATTR_NAME) ?: return PsiField.EMPTY_ARRAY
    val facet = AndroidFacet.getInstance(tag) ?: return PsiField.EMPTY_ARRAY
    val names: MutableSet<String> = Sets.newHashSet()
    for (attr in tag.subTags) {
      if (SdkConstants.TAG_ATTR == attr.name) {
        val attrName = attr.getAttributeValue(SdkConstants.ATTR_NAME)
        if (attrName != null) {
          names.add(styleableName + '_' + attrName)
        }
      }
    }
    if (!names.isEmpty()) {
      return findResourceFields(facet, ResourceType.STYLEABLE.getName(), names,
                                onlyInOwnPackages)
    }
  }
  else if (SdkConstants.TAG_ATTR == tagName) {
    val parentTag = tag.parentTag
    if (parentTag != null && SdkConstants.TAG_DECLARE_STYLEABLE == parentTag.name) {
      val styleName = parentTag.getAttributeValue(SdkConstants.ATTR_NAME)
      val attributeName = tag.getAttributeValue(SdkConstants.ATTR_NAME)
      val facet = AndroidFacet.getInstance(tag)
      if (facet != null && styleName != null && attributeName != null) {
        return findResourceFields(
          facet,
          ResourceType.STYLEABLE.getName(),
          styleName + '_' + attributeName,
          onlyInOwnPackages
        )
      }
    }
  }
  return PsiField.EMPTY_ARRAY
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

// result contains XmlAttributeValue or PsiFile

fun findResourcesByField(field: PsiField): List<PsiElement> {
  val facet = AndroidFacet.getInstance(field)
  return if (facet != null) ModuleResourceManagers.getInstance(facet).localResourceManager.findResourcesByField(field)
  else emptyList()
}

/**
 * Distinguishes whether a reference to a resource in an XML file is a resource declaration or a usage.
 */
fun isResourceDeclaration(resourceElement: PsiElement, targetElement: ResourceReferencePsiElement): Boolean {
  return when (resourceElement) {
    is XmlFile -> { // If the ReferencePsiElement created from the resourceElement matches the targetElement, then it must be a declaration of the
      // targetElement.
      val referencePsiElement = create(resourceElement)
      referencePsiElement != null && referencePsiElement.equals(targetElement)
    }
    is XmlAttributeValue -> {
      if (isIdDeclaration(
          resourceElement)) { // Layout and Navigation graph files can do inline id declaration.
        return true
      }
      if (ResourceFolderType.VALUES == getFolderType(resourceElement.getContainingFile())) {
        val attribute = PsiTreeUtil.getParentOfType(resourceElement,
                                                    XmlAttribute::class.java)
        if (attribute == null || attribute.nameElement.text != SdkConstants.ATTR_NAME) {
          return false
        }
        val tag = PsiTreeUtil.getParentOfType(resourceElement, XmlTag::class.java) ?: return false
        return when (getResourceTypeForResourceTag(tag)) {
          null -> {
            // Null means no resource type so this is not a resource declaration
            false
          }
          ResourceType.ATTR -> tag.getAttribute(SdkConstants.ATTR_FORMAT) != null
          ResourceType.STYLE -> {
            // Styles can have references to other styles in their name, this checks that the full name is the reference we're looking for.
            targetElement == create(resourceElement)
          }
          else -> {
            // For all other ResourceType, this is a declaration
            true
          }
        }
      }
      false
    }
    else -> false
  }
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

fun findIdFields(value: XmlAttributeValue): Array<PsiField> {
  return if (value.parent is XmlAttribute) {
    findIdFields(value.parent as XmlAttribute)
  }
  else PsiField.EMPTY_ARRAY
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
    val xmlAttribute = parent
    val nsURI = xmlAttribute.namespace
    val nsPrefix = xmlAttribute.namespacePrefix
    val key = xmlAttribute.localName
    return isConstraintReferencedIds(nsURI, nsPrefix, key)
  }
  return false
}

fun findIdFields(attribute: XmlAttribute): Array<PsiField> {
  val valueElement = attribute.valueElement
  val value = attribute.value
  if (valueElement != null && value != null && isIdDeclaration(valueElement)) {
    val id = getResourceNameByReferenceText(value)
    if (id != null) {
      val facet = AndroidFacet.getInstance(attribute)
      if (facet != null) {
        return findResourceFields(facet, ResourceType.ID.getName(), id, false)
      }
    }
  }
  return PsiField.EMPTY_ARRAY
}

/**
 * Generate an extension-less file name based on a passed string, that should pass
 * validation as a resource file name by Gradle plugin.
 *
 * For names validation in the Gradle plugin, see [FileResourceNameValidator]
 */
fun getValidResourceFileName(base: String): String {
  return StringUtil.toLowerCase(base.replace('-', '_').replace(' ', '_'))
}

fun getResourceNameByReferenceText(text: String): String? {
  val i = text.indexOf('/')
  return if (i < text.length - 1) {
    text.substring(i + 1)
  }
  else null
}

fun addValueResource(
  resType: ResourceType,
  resources: Resources,
  value: String?
): ResourceElement {
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
      if (value != null && value.indexOf('.') > 0) { // Deals with dimension values in the form of floating-point numbers, e.g. "0.24"
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

fun getResourceSubdirs(
  resourceType: ResourceFolderType,
  resourceDirs: Collection<VirtualFile?>
): List<VirtualFile> {
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
  if (ResourceType.PLURALS == type || ResourceType.STRING == type) {
    return "strings.xml"
  }
  if (VALUE_RESOURCE_TYPES.contains(type)) {
    return if (type == ResourceType.LAYOUT // Lots of unit tests assume drawable aliases are written in "drawables.xml" but going
               // forward lets combine both layouts and drawables in refs.xml as is done in the templates:
               || type == ResourceType.DRAWABLE && !ApplicationManager.getApplication().isUnitTestMode) {
      "refs.xml"
    }
    else type.getName() + "s.xml"
  }
  return if (ResourceType.ATTR == type ||
             ResourceType.STYLEABLE == type) {
    "attrs.xml"
  }
  else null
}

fun getValueResourcesFromElement(resourceType: ResourceType,
                                 resources: Resources): List<ResourceElement> {
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

fun isInResourceSubdirectory(file: PsiFile, resourceType: String? = null): Boolean {
  var file = file
  file = file.originalFile
  val dir = file.containingDirectory ?: return false
  return isResourceSubdirectory(dir, resourceType)
}

fun isResourceSubdirectory(directory: PsiDirectory, resourceType: String? = null): Boolean {
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
  return dir != null && isResourceDirectory(dir)
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

fun isResourceDirectory(directory: PsiDirectory): Boolean {
  var dir: PsiDirectory? = directory
  // check facet settings
  val vf = dir!!.virtualFile
  if (isLocalResourceDirectory(vf, dir.project)) {
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
    dirNames,
    Processor { element: ResourceElement ->
      if (!value.isEmpty()) {
        val s = if (resourceType == ResourceType.STRING) normalizeXmlResourceValue(
          value)
        else value
        element.setStringValue(s)
      }
      else if (resourceType == ResourceType.STYLEABLE || resourceType == ResourceType.STYLE) {
        element.setStringValue("value")
        element.xmlTag!!.value.setText(
          "")
      }
      outTags?.add(element)
      true
    }
  )
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
    val resources: Resources? = AndroidUtils.loadDomElement<Resources>(project, resFiles[i]!!, Resources::class.java)
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
        element.name.setValue(resourceName)
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
 * @param useGlobalCommand if true, the undo will be registered globally. This allows the command to be undone from anywhere in the IDE
 * and not only the XML editor
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
  if (resFieldName == null || resFieldName.isEmpty()) {
    return null
  }
  var qExp: PsiExpression = exp.qualifierExpression as? PsiReferenceExpression ?: return null
  val resClassReference = qExp as PsiReferenceExpression
  val resClassName = resClassReference.referenceName
  if (resClassName == null || resClassName.isEmpty() || className != null && className != resClassName) {
    return null
  }
  qExp = resClassReference.qualifierExpression ?: return null
  if (qExp !is PsiReferenceExpression) {
    return null
  }
  val resolvedElement = qExp.resolve() as? PsiClass ?: return null
  val resolvedModule = ModuleUtilCore.findModuleForPsiElement(resolvedElement)
  val aClass = resolvedElement
  val classShortName = aClass.name!!
  val fromManifest = AndroidUtils.MANIFEST_CLASS_NAME == classShortName
  if (!fromManifest && AndroidUtils.R_CLASS_NAME != classShortName) {
    return null
  }
  val qName = aClass.qualifiedName ?: return null
  if (!localOnly) {
    if (SdkConstants.CLASS_R == qName || AndroidInternalRClassFinder.INTERNAL_R_CLASS_QNAME == qName) {
      return ReferredResourceFieldInfo(resClassName, resFieldName, resolvedModule, ResourceNamespace.ANDROID, false)
    }
  }
  return if (if (fromManifest) !isManifestClass(aClass) else !isRJavaClass(aClass)) {
    null
  }
  else ReferredResourceFieldInfo(resClassName, resFieldName, resolvedModule, getRClassNamespace(facet, qName),
                                 fromManifest)
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
  return if (file1 != null && file1 == file2 || file1 === file2) {
    0
  }
  else if (file1 != null && file2 != null) {
    val xml1 = file1.fileType === StdFileTypes.XML
    val xml2 = file2.fileType === StdFileTypes.XML
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

/**
 * Utility method suitable for Comparator implementations which order resource files,
 * which will sort files by base folder followed by alphabetical configurations. Prioritizes
 * XML files higher than non-XML files. (Resource file folders are sorted by folder configuration
 * order.)
 */

fun compareResourceFiles(file1: PsiFile?, file2: PsiFile?): Int {
  return if (file1 === file2) {
    0
  }
  else if (file1 != null && file2 != null) {
    val xml1 = file1.fileType === StdFileTypes.XML
    val xml2 = file2.fileType === StdFileTypes.XML
    if (xml1 != xml2) {
      return if (xml1) -1 else 1
    }
    val parent1 = file1.parent
    val parent2 = file2.parent
    if (parent1 != null && parent2 != null && parent1 !== parent2) {
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
    file1.name.compareTo(file2.name)
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
fun getResourceDirectoriesForFacets(facets: List<AndroidFacet?>): Map<VirtualFile, AndroidFacet?> {
  val resDirectories: MutableMap<VirtualFile, AndroidFacet?> = HashMap()
  for (facet in facets) {
    for (resourceDir in getInstance(facet!!).folders) {
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
  val psiFile = getItemPsiFile(project, idResource) as? XmlFile ?: return null
  val resourceName = idResource.name
  // TODO(b/113646219): find the right one, if there are multiple, not the first one.
  val processor = FindFilteredElement<XmlAttribute> { element ->
    if (element !is XmlAttribute) {
      false
    } else {
      val attrValue = element.value
      if (isIdDeclaration(attrValue)) {
        val resourceUrl = ResourceUrl.parse(attrValue!!)
        resourceUrl != null && resourceUrl.name == resourceName
      }
      else {
        false
      }
    }
  }
  PsiTreeUtil.processElements(psiFile, processor)
  return processor.foundElement as XmlAttribute?
}

/**
 * Returns the [XmlAttributeValue] defining the given resource item. This is only defined for resource items which are not file
 * based.
 *
 *
 * [org.jetbrains.android.AndroidFindUsagesHandlerFactory.createFindUsagesHandler] assumes references to value resources
 * resolve to the "name" [XmlAttributeValue], that's how they are found when looking for usages of a resource.
 *
 * TODO(b/113646219): store enough information in [ResourceItem] to find the attribute and get the tag from there, not the other
 * way around.
 *
 * @see ResourceItem.isFileBased
 * @see org.jetbrains.android.AndroidFindUsagesHandlerFactory.createFindUsagesHandler
 */
fun getDeclaringAttributeValue(project: Project, item: ResourceItem): XmlAttributeValue? {
  if (item.isFileBased) {
    return null
  }
  val attribute: XmlAttribute?
  attribute = if (item.isInlineIdDeclaration()) {
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
            SdkConstants.ATTR_FORMAT) != null || attr.subTags.size > 0)) {
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
    if (valuesResource != null && VALUE_RESOURCE_TYPES.contains(
        getResourceTypeForResourceTag(valuesResource))) {
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
    val psiItem = item
    val tag = psiItem.tag
    val id = item.getName()
    if (tag != null && tag.isValid // Make sure that the id attribute we're searching for is actually
        // defined for this tag, not just referenced from this tag.
        // For example, we could have
        //    <Button a:alignLeft="@+id/target" a:id="@+id/something ...>
        // and this should *not* return "Button" as the view tag for
        // @+id/target!
        && id == stripIdPrefix(tag.getAttributeValue(SdkConstants.ATTR_ID, SdkConstants.ANDROID_URI))) {
      return tag.name
    }
    val file = psiItem.psiFile
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
  val id = tag.getAttributeValue(SdkConstants.ATTR_ID, SdkConstants.ANDROID_URI)
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
    return if (AndroidUtils.TAG_LINEAR_LAYOUT == rootTagName) AndroidFileTemplateProvider.LAYOUT_RESOURCE_VERTICAL_FILE_TEMPLATE else AndroidFileTemplateProvider.LAYOUT_RESOURCE_FILE_TEMPLATE
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
 * If some of the directories do not contain a file with that name, creates such a resource file.
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
  val files: MutableList<VirtualFile> = Lists.newArrayListWithCapacity(
    dirNames.size)
  val foundFiles = writeCommandAction(project).withName("Find statelists files").compute<Boolean, Exception> {
    try {
      var fileName = stateListName
      if (!stateListName.endsWith(SdkConstants.DOT_XML)) {
        fileName += SdkConstants.DOT_XML
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
          throw IOException(
            "cannot find " + Joiner.on(File.separatorChar).join(resDir, dirPath, fileName))
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

fun updateStateList(project: Project, stateList: StateList, files: List<VirtualFile>) {
  if (!ensureFilesWritable(project, files)) {
    return
  }
  val psiFiles: MutableList<PsiFile> = Lists.newArrayListWithCapacity(files.size)
  val manager = PsiManager.getInstance(project)
  for (file in files) {
    val psiFile = manager.findFile(file)
    if (psiFile != null) {
      psiFiles.add(psiFile)
    }
  }
  val selectors: MutableList<AndroidDomElement> = Lists.newArrayListWithCapacity(files.size)
  val selectorClass: Class<out AndroidDomElement> = if (stateList.folderType == ResourceFolderType.COLOR) {
    ColorSelector::class.java
  }
  else {
    DrawableSelector::class.java
  }
  for (file in files) {
    val selector = AndroidUtils.loadDomElement(project, file, selectorClass)
    if (selector == null) {
      AndroidUtils.reportError(project, file.name + " is not a statelist file")
      return
    }
    selectors.add(selector)
  }

  return writeCommandAction(project, *psiFiles.toTypedArray()).withName("Change State List").run<Exception> {
    for (selector in selectors) {
      val tag = selector.xmlTag
      for (subtag in tag!!.subTags) {
        subtag.delete()
      }
      for (state in stateList.states) {
        var child = tag.createChildTag(SdkConstants.TAG_ITEM, tag.namespace, null, false)
        child = tag.addSubTag(child, false)
        val attributes = state.attributes
        for (attributeName in attributes.keys) {
          child.setAttribute(attributeName, SdkConstants.ANDROID_URI, attributes[attributeName].toString())
        }
        if (!StringUtil.isEmpty(state.alpha)) {
          child.setAttribute("alpha", SdkConstants.ANDROID_URI, state.alpha)
        }
        if (selector is ColorSelector) {
          child.setAttribute(SdkConstants.ATTR_COLOR, SdkConstants.ANDROID_URI, state.value)
        }
        else if (selector is DrawableSelector) {
          child.setAttribute(SdkConstants.ATTR_DRAWABLE, SdkConstants.ANDROID_URI, state.value)
        }
      }
    }
    // The following is necessary since layoutlib will look on disk for the color state list file.
    // So as soon as a color state list is modified, the change needs to be saved on disk
    // for the correct values to be used in the theme editor preview.
    // TODO: Remove this once layoutlib can get color state lists from PSI instead of disk
    FileDocumentManager.getInstance().saveAllDocuments()
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

  prefix = suggestedPrefix ?: when(namespaceUri) {
    SdkConstants.TOOLS_URI -> SdkConstants.TOOLS_PREFIX
    SdkConstants.ANDROID_URI -> SdkConstants.ANDROID_NS_NAME
    SdkConstants.AAPT_URI -> SdkConstants.AAPT_PREFIX
    else -> SdkConstants.APP_PREFIX
  }

  if (rootTag.getAttribute(SdkConstants.XMLNS_PREFIX + prefix) != null) {
    val base: String = prefix
    var i = 2
    while (true) {
      prefix = base + Integer.toString(i)
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
    if (!attributeName.startsWith(SdkConstants.XMLNS_PREFIX) || attributeName.compareTo(name) > 0) {
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
