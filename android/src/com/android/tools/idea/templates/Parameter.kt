/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.templates

import com.android.SdkConstants
import com.android.builder.model.SourceProvider
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.idea.npw.assetstudio.AssetStudioUtils
import com.android.tools.idea.res.*
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidRootUtil
import org.jetbrains.android.facet.IdeaSourceProvider
import org.jetbrains.android.util.AndroidUtils
import org.w3c.dom.Element

import java.io.File
import java.util.*

import com.android.tools.idea.templates.Template.*
import com.google.common.annotations.VisibleForTesting

/**
 * Parameter represents an external input to a template. It consists of an ID used to refer to it within the template, human-readable
 * information to be displayed in the UI, and type and validation specifications that can be used in the UI to assist in data entry.
 */
class Parameter(
  /** The template defining the parameter  */
  @JvmField val template: TemplateMetadata,
  /** The element defining this parameter  */
  @JvmField val element: Element) {
  /** The type of parameter  */
  @JvmField
  val type: Type
  /** The unique id of the parameter (not displayed to the user)  */
  @JvmField
  val id: String?
  /** The display name for this parameter  */
  @JvmField
  val name: String?
  /** The initial value for this parameter (see also [suggest] for more dynamic defaults) */
  @JvmField
  val initial: String?
  /** A template expression using other template parameters for producing a default value based on other edited parameters, if possible. */
  @JvmField
  val suggest: String?
  /** A template expression using other template parameters for dynamically changing the visibility of this parameter to the user. */
  @JvmField
  val visibility: String?
  /** A template expression using other template parameters for dynamically changing whether this parameter is enabled for the user. */
  @JvmField
  val enabled: String?
  /** Help for the parameter, if any  */
  @JvmField
  val help: String?
  /** The constraints applicable for this parameter  */
  @JvmField
  val constraints: EnumSet<Constraint>

  val options: List<Element>
    get() = TemplateUtils.getChildren(element)

  enum class Type {
    STRING,
    BOOLEAN,
    ENUM,
    SEPARATOR;
  }

  /**
   * Constraints that can be applied to a parameter which helps the UI add a validator etc for user input.
   * These are typically combined into a set of constraints via an EnumSet.
   */
  enum class Constraint {
    /**
     * This value must be unique. This constraint usually only makes sense when other constraints are specified, such as [LAYOUT],
     * which means that the parameter should designate a name that does not represent an existing layout resource name.
     */
    UNIQUE,
    /**
     * This value must already exist. This constraint usually only makes sense when other constraints are specified, such as [LAYOUT],
     * which means that the parameter should designate a name that already exists as a resource name.
     */
    EXISTS,
    /** The associated value must not be empty. */
    NONEMPTY,
    /** The associated value should represent a fully qualified activity class name. */
    ACTIVITY,
    /** The associated value should represent an API level. */
    API_LEVEL,
    /** The associated value should represent a valid class name. */
    CLASS,
    /** The associated value should represent a valid package name. */
    PACKAGE,
    /** The associated value should represent a valid Android application package name. */
    APP_PACKAGE,
    /** The associated value should represent a valid Module name. */
    MODULE,
    /** The associated value should represent a valid layout resource name. */
    LAYOUT,
    /** The associated value should represent a valid drawable resource name. */
    DRAWABLE,
    /** The associated value should represent a valid values file name. */
    VALUES,
    /** The associated value should represent a valid id resource name. */
    ID,
    /** The associated value should represent a valid source directory name. */
    SOURCE_SET_FOLDER,
    /** The associated value should represent a valid string resource name. */
    STRING,
    /**  The associated value should represent a valid URI authority. Format: [userinfo@]host[:port] */
    URI_AUTHORITY;

    fun toResourceFolderType(): ResourceFolderType = when (this) {
      DRAWABLE -> ResourceFolderType.DRAWABLE
      STRING, VALUES -> ResourceFolderType.VALUES
      LAYOUT -> ResourceFolderType.LAYOUT
      else -> throw IllegalArgumentException("There is not matching ResourceFolderType for $this constraint")
    }
  }

  init {
    val typeName = element.getAttribute(ATTR_TYPE)
    assert(typeName != null && typeName.isNotEmpty()) { ATTR_TYPE }
    type = Type.valueOf(typeName!!.toUpperCase(Locale.US))
    id = element.getAttribute(ATTR_ID)
    initial = element.getAttribute(ATTR_DEFAULT)
    suggest = element.getAttribute(ATTR_SUGGEST)
    visibility = element.getAttribute(ATTR_VISIBILITY)
    enabled = element.getAttribute(ATTR_ENABLED)
    name = element.getAttribute(ATTR_NAME)
    help = element.getAttribute(ATTR_HELP)
    val constraintString = element.getAttribute(ATTR_CONSTRAINTS)
    constraints = if (constraintString != null && constraintString.isNotEmpty()) {
      EnumSet.copyOf(constraintString.split("|").map { Constraint.valueOf(it.toUpperCase(Locale.US)) })
    }
    else {
      EnumSet.noneOf(Constraint::class.java)
    }
  }

  /**
   * Validate the given value for this parameter and list any reasons why the given value is invalid.
   * @return An error message detailing why the given value is invalid.
   */
  fun validate(project: Project?, module: Module?, provider: SourceProvider?,
               packageName: String?, value: Any?, relatedValues: Set<Any>): String? = when (type) {
    Type.STRING -> {
      val v = value?.toString() ?: ""
      val violations = validateStringType(project, module, provider, packageName, v, relatedValues)
      violations.mapNotNull { getErrorMessageForViolatedConstraint(it, v) }.firstOrNull()
    }
    Type.BOOLEAN, Type.ENUM, Type.SEPARATOR -> null
  }

  private fun getErrorMessageForViolatedConstraint(c: Constraint, value: String): String? = when (c) {
    Constraint.NONEMPTY -> "Please specify " + name!!
    Constraint.ACTIVITY -> name!! + " is not set to a valid activity name"
    Constraint.CLASS -> name!! + " is not set to a valid class name"
    Constraint.PACKAGE -> name!! + " is not set to a valid package name"
    Constraint.MODULE -> name!! + " is not set to a valid module name"
    Constraint.ID -> name!! + " is not set to a valid id."
    Constraint.DRAWABLE, Constraint.STRING, Constraint.LAYOUT -> {
      val rft = c.toResourceFolderType()
      val resourceNameError = IdeResourceNameValidator.forFilename(rft).getErrorText(value)
      if (resourceNameError == null) {
        "Unknown resource name error (name: $name). Constraint $c is violated"
      }
      else {
        "$name is not set to a valid resource name: $resourceNameError"
      }
    }
    Constraint.APP_PACKAGE -> AndroidUtils.validateAndroidPackageName(value)
                              ?: throw IllegalArgumentException("Given constraint $c is not violated by $value")
    Constraint.UNIQUE -> name!! + " must be unique"
    Constraint.EXISTS -> name!! + " must already exist"
    Constraint.URI_AUTHORITY -> name!! + " must be a valid URI authority"
    Constraint.API_LEVEL -> TODO("validity check")
    Constraint.VALUES -> TODO()
    Constraint.SOURCE_SET_FOLDER -> TODO()
  }

  /**
   * Validate the given value for this parameter and list the constraints that the given value violates.
   * @return All constraints of this parameter that are violated by the proposed value.
   */
  @VisibleForTesting
  fun validateStringType(project: Project?,
                         module: Module?,
                         provider: SourceProvider?,
                         packageName: String?,
                         value: String?,
                         relatedValues: Set<Any> = setOf()): Collection<Constraint> {
    if (value == null || value.isEmpty()) {
      return if (Constraint.NONEMPTY in constraints) listOf(Constraint.NONEMPTY) else listOf()
    }

    val searchScope = if (module != null) GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)
    else GlobalSearchScope.EMPTY_SCOPE

    val qualifier = if (packageName != null && !value.contains('.')) "$packageName." else ""
    val fqName = qualifier + value

    fun validateConstraint(c: Constraint): Boolean = when (c) {
      Constraint.NONEMPTY -> value.isEmpty()
      Constraint.URI_AUTHORITY -> !value.matches("$URI_AUTHORITY_REGEX(;$URI_AUTHORITY_REGEX)*".toRegex())
      Constraint.ACTIVITY, Constraint.CLASS, Constraint.PACKAGE -> !isValidFullyQualifiedJavaIdentifier(fqName)
      Constraint.APP_PACKAGE -> AndroidUtils.validateAndroidPackageName(value) != null
      Constraint.DRAWABLE, Constraint.STRING, Constraint.LAYOUT, Constraint.VALUES -> {
        val rft = c.toResourceFolderType()
        IdeResourceNameValidator.forFilename(rft).getErrorText(value) != null
      }
      Constraint.SOURCE_SET_FOLDER, Constraint.MODULE -> false // may only violate uniqueness
      Constraint.UNIQUE, Constraint.EXISTS -> false // not applicable
      Constraint.API_LEVEL, Constraint.ID -> TODO()
    }

    fun checkExistence(c: Constraint): Boolean {
      return when (c) {
        Constraint.ACTIVITY -> {
          project ?: return false
          val aClass = JavaPsiFacade.getInstance(project).findClass(fqName, searchScope)
          val activityClass = JavaPsiFacade.getInstance(project).findClass(SdkConstants.CLASS_ACTIVITY, GlobalSearchScope.allScope(project))
          aClass != null && activityClass != null && aClass.isInheritor(activityClass, true)
        }
        Constraint.CLASS -> project != null && existsClassFile(project, searchScope, provider, fqName)
        Constraint.PACKAGE, Constraint.APP_PACKAGE -> project != null && existsPackage(project, provider, value)
        Constraint.MODULE -> project != null && ModuleManager.getInstance(project).findModuleByName(value) != null
        Constraint.LAYOUT -> {
          if (provider != null)
            existsResourceFile(provider, module, ResourceFolderType.LAYOUT, ResourceType.LAYOUT, value)
          else
            existsResourceFile(module, ResourceType.LAYOUT, value)
        }
        Constraint.DRAWABLE -> {
          if (provider != null)
            existsResourceFile(provider, module, ResourceFolderType.DRAWABLE, ResourceType.DRAWABLE, value)
          else
            existsResourceFile(module, ResourceType.DRAWABLE, value)
        }
        Constraint.VALUES -> provider?.resDirectories?.any { existsResourceFile(it, ResourceFolderType.VALUES, value) } ?: false
        Constraint.SOURCE_SET_FOLDER -> {
          module ?: return false
          val facet = AndroidFacet.getInstance(module) ?: return false
          val modulePath = AndroidRootUtil.getModuleDirPath(module) ?: return false
          val file = File(FileUtil.toSystemDependentName(modulePath), value)
          val vFile = VfsUtil.findFileByIoFile(file, true)
          IdeaSourceProvider.getSourceProvidersForFile(facet, vFile, null).isNotEmpty()
        }
        Constraint.NONEMPTY, Constraint.ID, Constraint.STRING, Constraint.URI_AUTHORITY, Constraint.API_LEVEL -> false
        Constraint.UNIQUE, Constraint.EXISTS -> false // not applicable
      }
    }

    val exists = constraints.any { checkExistence(it) } || value in relatedValues
    val violations = constraints.filter { validateConstraint(it) }

    if (Constraint.UNIQUE in constraints && exists) {
      return violations + listOf(Constraint.UNIQUE)
    }
    if (Constraint.EXISTS in constraints && !exists) {
      return violations + listOf(Constraint.EXISTS)
    }
    return violations
  }


  /**
   * Returns true if the given stringType is non-unique when it should be.
   */
  fun uniquenessSatisfied(project: Project?, module: Module?, provider: SourceProvider?,
                          packageName: String?, value: String?, relatedValues: Set<Any>): Boolean =
    !validateStringType(project, module, provider, packageName, value, relatedValues).contains(Constraint.UNIQUE)

  fun isRelated(p: Parameter): Boolean = TYPE_CONSTRAINTS.intersect(constraints).intersect(p.constraints).isNotEmpty()

  override fun toString(): String = "(parameter id: $id)"

  companion object {
    private const val URI_AUTHORITY_REGEX = "[a-zA-Z][a-zA-Z0-9-_.]*(:\\d+)?"

    val TYPE_CONSTRAINTS: EnumSet<Constraint> = EnumSet
      .of(Constraint.ACTIVITY, Constraint.API_LEVEL, Constraint.CLASS, Constraint.PACKAGE, Constraint.APP_PACKAGE, Constraint.MODULE,
          Constraint.LAYOUT, Constraint.DRAWABLE, Constraint.ID, Constraint.SOURCE_SET_FOLDER, Constraint.STRING, Constraint.URI_AUTHORITY)

    private fun isValidFullyQualifiedJavaIdentifier(value: String) = AndroidUtils.isValidJavaPackageName(value) && value.contains('.')

    fun existsResourceFile(module: Module?, resourceType: ResourceType, name: String?): Boolean {
      if (name == null || name.isEmpty() || module == null) {
        return false
      }
      val facet = AndroidFacet.getInstance(module) ?: return false
      return AssetStudioUtils.resourceExists(facet, resourceType, name)
    }

    fun existsResourceFile(sourceProvider: SourceProvider?, module: Module?,
                           resourceFolderType: ResourceFolderType, resourceType: ResourceType, name: String?): Boolean {
      if (name == null || name.isEmpty() || sourceProvider == null) {
        return false
      }
      val facet = if (module != null) AndroidFacet.getInstance(module) else null

      return sourceProvider.resDirectories.any { resDir ->
        if (facet != null) {
          val virtualResDir = VfsUtil.findFileByIoFile(resDir, false) ?: return@any false
          val folderRepository = ResourceFolderRegistry.getInstance(facet.module.project).get(facet, virtualResDir)
          val resourceItemList = folderRepository.getResources(ResourceNamespace.TODO(), resourceType, name)
          resourceItemList.isNotEmpty()
        }
        else {
          existsResourceFile(resDir, resourceFolderType, name)
        }
      }
    }

    fun existsResourceFile(resDir: File, resourceType: ResourceFolderType, name: String): Boolean {
      val resTypes = resDir.listFiles() ?: return false
      return resTypes.filter { it.isDirectory && resourceType == ResourceFolderType.getFolderType(it.name) }.any {
        it.listFiles()?.any { f -> getNameWithoutExtensions(f).equals(name, ignoreCase = true) } ?: false
      }
    }

    private fun getNameWithoutExtensions(f: File): String = f.name.dropLastWhile { it != '.' }.removeSuffix(".")

    fun existsClassFile(project: Project?, searchScope: GlobalSearchScope,
                        sourceProvider: SourceProvider?, fullyQualifiedClassName: String): Boolean {
      if (project == null) {
        return false
      }
      if (sourceProvider == null) {
        return searchScope !== GlobalSearchScope.EMPTY_SCOPE &&
               JavaPsiFacade.getInstance(project).findClass(fullyQualifiedClassName, searchScope) != null
      }
      val base = fullyQualifiedClassName.replace('.', File.separatorChar)
      return sourceProvider.javaDirectories.any { javaDir ->
        val javaFile = File(javaDir, base + SdkConstants.DOT_JAVA)
        val ktFile = File(javaDir, base + SdkConstants.DOT_KT)
        javaFile.exists() || ktFile.exists()
      }
    }

    private fun existsPackage(project: Project?, sourceProvider: SourceProvider?, packageName: String): Boolean {
      if (project == null) {
        return false
      }
      if (sourceProvider == null) {
        return JavaPsiFacade.getInstance(project).findPackage(packageName) != null
      }
      return sourceProvider.javaDirectories.any {
        val classFile = File(it, packageName.replace('.', File.separatorChar))
        classFile.exists() && classFile.isDirectory
      }
    }
  }
}
