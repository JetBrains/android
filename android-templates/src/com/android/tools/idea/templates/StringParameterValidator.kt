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
package com.android.tools.idea.templates

import com.android.SdkConstants
import com.android.builder.model.SourceProvider
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.idea.projectsystem.AndroidModulePaths
import com.android.tools.idea.projectsystem.getForFile
import com.android.tools.idea.projectsystem.sourceProviders
import com.android.tools.idea.res.IdeResourceNameValidator
import com.android.tools.idea.res.ResourceFolderRegistry
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.util.androidFacet
import com.android.tools.idea.wizard.template.Constraint.*
import com.android.tools.idea.wizard.template.Constraint
import com.android.tools.idea.wizard.template.StringParameter
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.Iterables
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScope.EMPTY_SCOPE
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidRootUtil
import org.jetbrains.android.util.AndroidUtils
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.projectStructure.productionSourceInfo
import org.jetbrains.kotlin.idea.core.KotlinIndicesHelper
import java.io.File

/**
 * Validate the given value for this parameter and list any reasons why the given value is invalid.
 * @return An error message detailing why the given value is invalid.
 */
fun StringParameter.validate(
  project: Project?, module: Module?, provider: SourceProvider?, packageName: String?, value: Any?, relatedValues: Set<Any>
): String? {
  val v = value?.toString().orEmpty()
  val violations = validateStringType(project, module, provider, packageName, v, relatedValues)
  return violations.mapNotNull { getErrorMessageForViolatedConstraint(it, v) }.firstOrNull()
}

private fun StringParameter.getErrorMessageForViolatedConstraint(c: Constraint, value: String): String? = when (c) {
  NONEMPTY -> "Please specify $name"
  ACTIVITY -> "$name is not set to a valid activity name"
  CLASS -> "$name is not set to a valid class name"
  PACKAGE -> "$name is not set to a valid package name"
  MODULE -> "$name is not set to a valid module name"
  KOTLIN_FUNCTION -> "$name is not set to a valid function name"
  DRAWABLE, NAVIGATION, STRING, LAYOUT -> {
    val rft = c.toResourceFolderType()
    val resourceNameError = IdeResourceNameValidator.forFilename(rft).getErrorText(value)
    if (resourceNameError == null)
      "Unknown resource name error (name: $name). Constraint $c is violated"
    else
      "$name is not set to a valid resource name: $resourceNameError"
  }
  APP_PACKAGE -> AndroidUtils.validateAndroidPackageName(value)
                 ?: throw IllegalArgumentException("Given constraint $c is not violated by $value")
  UNIQUE -> "$name must be unique"
  EXISTS -> "$name must already exist"
  URI_AUTHORITY -> "$name must be a valid URI authority"
  VALUES -> "$name must be a valid 'values' file name"
  SOURCE_SET_FOLDER -> "$name must be a valid source directory name"
}

/**
 * Validate the given value for this parameter and list the constraints that the given value violates.
 * @return All constraints of this parameter that are violated by the proposed value.
 */
@VisibleForTesting
fun StringParameter.validateStringType(
  project: Project?, module: Module?, provider: SourceProvider?, packageName: String?, value: String?, relatedValues: Set<Any> = setOf()
): Collection<Constraint> {
  if (value.isNullOrEmpty()) {
    return if (NONEMPTY in constraints) listOf(NONEMPTY)
    else listOf()
  }
  val searchScope = if (module != null) GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module) else EMPTY_SCOPE
  val qualifier = if (packageName != null && !value.contains('.')) "$packageName." else ""
  val fqName = qualifier + value

  fun validateConstraint(c: Constraint): Boolean = when (c) {
    NONEMPTY -> value.isEmpty()
    URI_AUTHORITY -> !value.matches("$URI_AUTHORITY_REGEX(;$URI_AUTHORITY_REGEX)*".toRegex())
    ACTIVITY, CLASS, PACKAGE, KOTLIN_FUNCTION -> !isValidFullyQualifiedJavaIdentifier(fqName)
    APP_PACKAGE -> AndroidUtils.validateAndroidPackageName(value) != null
    DRAWABLE, NAVIGATION, STRING, LAYOUT, VALUES -> {
      val rft = c.toResourceFolderType()
      IdeResourceNameValidator.forFilename(rft).getErrorText(value) != null
    }
    SOURCE_SET_FOLDER, MODULE -> false // may only violate uniqueness
    UNIQUE, EXISTS -> false // not applicable
  }

  fun checkExistence(c: Constraint): Boolean {
  return when (c) {
      ACTIVITY -> {
        project ?: return false
        val aClass = JavaPsiFacade.getInstance(project).findClass(fqName, searchScope)
        val activityClass = JavaPsiFacade.getInstance(project).findClass(SdkConstants.CLASS_ACTIVITY, GlobalSearchScope.allScope(project))
        aClass != null && activityClass != null && aClass.isInheritor(activityClass, true)
      }
    KOTLIN_FUNCTION -> {
      project ?: return false
      module ?: return false
      val moduleInfo = module.productionSourceInfo!!
      val platform = module.platform
      val facade = KotlinCacheService.getInstance(project).getResolutionFacadeByModuleInfo(moduleInfo, platform)!!
      val helper = KotlinIndicesHelper(facade, searchScope, { true })
      helper.getTopLevelCallablesByName(value).isNotEmpty()
    }
    CLASS -> project != null && existsClassFile(project, searchScope, provider, fqName)
      PACKAGE, APP_PACKAGE -> project != null && existsPackage(project, provider, value)
      MODULE -> project != null && ModuleManager.getInstance(project).findModuleByName(value) != null
      LAYOUT -> {
        if (provider != null)
          existsResourceFile(provider, module, ResourceFolderType.LAYOUT, ResourceType.LAYOUT, value)
        else
          existsResourceFile(module, ResourceType.LAYOUT, value)
      }
      DRAWABLE -> {
        if (provider != null)
          existsResourceFile(provider, module, ResourceFolderType.DRAWABLE, ResourceType.DRAWABLE, value)
        else
          existsResourceFile(module, ResourceType.DRAWABLE, value)
      }
      NAVIGATION -> {
        if (provider != null)
          existsResourceFile(provider, module, ResourceFolderType.NAVIGATION, ResourceType.NAVIGATION, value)
        else
          existsResourceFile(module, ResourceType.NAVIGATION, value)
      }
      VALUES -> provider?.resDirectories?.any {
        existsResourceFile(it, ResourceFolderType.VALUES, value)
      } ?: false
      SOURCE_SET_FOLDER -> {
        module ?: return false
        val facet = AndroidFacet.getInstance(module) ?: return false
        val modulePath: @SystemIndependent String = AndroidRootUtil.getModuleDirPath(module) ?: return false
        val file = File(FileUtil.toSystemDependentName(modulePath), value)
        val vFile = VfsUtil.findFileByIoFile(file, true)
        facet.sourceProviders.getForFile(vFile) != null
      }
      NONEMPTY, STRING, URI_AUTHORITY -> false
      UNIQUE, EXISTS -> false // not applicable
    }
  }

  val exists = constraints.any { checkExistence(it) } || value in relatedValues
  val violations = constraints.filter { validateConstraint(it) }
  if (UNIQUE in constraints && exists) {
    return violations + listOf(UNIQUE)
  }
  if (EXISTS in constraints && !exists) {
    return violations + listOf(EXISTS)
  }
  return violations
}

/**
 * Returns true if the given stringType is non-unique when it should be.
 */
fun StringParameter.uniquenessSatisfied(
  project: Project?, module: Module?, provider: SourceProvider?, packageName: String?, value: String?, relatedValues: Set<Any>
): Boolean = !validateStringType(project, module, provider, packageName, value, relatedValues).contains(UNIQUE)

private const val URI_AUTHORITY_REGEX = "[a-zA-Z][a-zA-Z0-9-_.]*(:\\d+)?"

fun existsResourceFile(module: Module?, resourceType: ResourceType, name: String?): Boolean {
  if (name.isNullOrEmpty() || module == null) {
    return false
  }
  val facet = module.androidFacet ?: return false
  return resourceExists(facet, resourceType, name)
}

fun existsResourceFile(
  sourceProvider: SourceProvider?, module: Module?, resourceFolderType: ResourceFolderType, resourceType: ResourceType, name: String?
): Boolean {
  if (name.isNullOrEmpty() || sourceProvider == null) {
    return false
  }
  val facet = module?.androidFacet
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

fun existsResourceFile(resDir: File, resourceType: ResourceFolderType, name: String): Boolean =
  resDir.listFiles()
    ?.filter { it.isDirectory && resourceType == ResourceFolderType.getFolderType(it.name) }
    ?.any { it.listFiles()?.any { f -> getNameWithoutExtensions(f).equals(name, ignoreCase = true) } ?: false }
  ?: false

private fun getNameWithoutExtensions(f: File): String = f.name.dropLastWhile { it != '.' }.removeSuffix(".")

fun existsClassFile(
  project: Project?, searchScope: GlobalSearchScope, sourceProvider: SourceProvider?, fullyQualifiedClassName: String
): Boolean {
  if (project == null) {
    return false
  }
  if (sourceProvider == null) {
    return searchScope != EMPTY_SCOPE && JavaPsiFacade.getInstance(project).findClass(fullyQualifiedClassName, searchScope) != null
  }
  val base = fullyQualifiedClassName.replace('.', File.separatorChar)
  return sourceProvider.javaDirectories.any { javaDir ->
    val javaFile = File(javaDir, base + SdkConstants.DOT_JAVA)
    val ktFile = File(javaDir, base + SdkConstants.DOT_KT)
    javaFile.exists() || ktFile.exists()
  }
}

fun Constraint.toResourceFolderType(): ResourceFolderType = when (this) {
  DRAWABLE -> ResourceFolderType.DRAWABLE
  STRING, VALUES -> ResourceFolderType.VALUES
  LAYOUT -> ResourceFolderType.LAYOUT
  NAVIGATION -> ResourceFolderType.NAVIGATION
  else -> throw IllegalArgumentException("There is no matching ResourceFolderType for $this constraint")
}

private fun isValidFullyQualifiedJavaIdentifier(value: String) = AndroidUtils.isValidJavaPackageName(value) && value.contains('.')

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

/**
 * Returns true if a resource with the same name is already found at a location implied by the input parameters.
 */
private fun resourceExists(paths: AndroidModulePaths, resourceType: ResourceFolderType, name: String): Boolean {
  val resDir = Iterables.getFirst(paths.resDirectories, null) ?: return false
  val resTypes = resDir.listFiles() ?: return false

  // The path of a resource looks something like:
  //
  // path/to/res/
  //   drawable/name
  //   drawable-hdpi-v9/name
  //   drawable-hdpi-v11/name
  //   drawable-mdpi-v9/name
  //   ...
  //
  // We don't really care about the "drawable" directory here; we just want to search all folders
  // in res/ and look for the first match in any of them.
  return resTypes
    .filter { it.isDirectory && resourceType == ResourceFolderType.getFolderType(it.name) }
    .flatMap { it.listFiles().orEmpty().toList()  }
    .any { FileUtil.getNameWithoutExtension(it).equals(name, ignoreCase = true) }
}

/**
 * Like [resourceExists] but a useful fallback if information about the current paths is not known.
 */
private fun resourceExists(facet: AndroidFacet, resourceType: ResourceType, name: String): Boolean {
  val repository = ResourceRepositoryManager.getAppResources(facet)
  return repository.hasResources(ResourceNamespace.TODO(), resourceType, name)
}