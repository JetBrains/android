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
package com.android.tools.idea.assistant

import com.android.SdkConstants
import com.android.tools.idea.templates.FreemarkerUtils.TemplateProcessingException
import com.android.tools.idea.templates.TemplateUtils.openEditors
import com.android.tools.idea.templates.recipe.Recipe
import com.android.tools.idea.templates.recipe.RenderingContext
import com.google.common.collect.ImmutableList
import com.google.common.collect.LinkedHashMultimap
import com.google.common.collect.SetMultimap
import com.intellij.openapi.command.WriteCommandAction.writeCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.android.facet.AndroidRootUtil
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import javax.xml.parsers.SAXParserFactory

private val log: Logger get() = logger<RecipeUtils>()

/**
 * A collection of utility methods for interacting with an `Recipe`.
 */
object RecipeUtils {
  private val recipeMetadataCache: MutableMap<Pair<Recipe, Project>, List<RecipeMetadata>> = hashMapOf()

  @JvmStatic
  fun getRecipeMetadata(recipe: Recipe, module: Module): RecipeMetadata {
    val key = Pair(recipe, module.project)
    if (recipeMetadataCache.containsKey(key)) {
      val metadata = recipeMetadataCache[key]!!.find { it.recipe == recipe }
      if (metadata != null) {
        return metadata
      }
    }
    val dependencies: SetMultimap<String, String> = LinkedHashMultimap.create()
    val classpathEntries: Set<String> = hashSetOf()
    val plugins: Set<String> = hashSetOf()
    val sourceFiles: List<File> = mutableListOf()
    val targetFiles: List<File> = mutableListOf()
    val metadata = RecipeMetadata(recipe, module)
    var context: RenderingContext? = null

    try {
      val moduleRoot = AndroidRootUtil.findModuleRootFolderPath(module)!!
      // TODO: do we care about this path?
      val rootPath = File(FileUtil.generateRandomTemporaryPath(), "unused")
      rootPath.deleteOnExit()
      context = RenderingContext.Builder
        .newContext(rootPath, module.project)
        .withOutputRoot(moduleRoot)
        .withModuleRoot(moduleRoot)
        .withFindOnlyReferences(true)
        .intoDependencies(dependencies)
        .intoClasspathEntries(classpathEntries)
        .intoPlugins(plugins)
        .intoSourceFiles(sourceFiles)
        .intoTargetFiles(targetFiles)
        .build()
      val recipeExecutor = context.recipeExecutor
      recipe.execute(recipeExecutor)
    }
    catch (e: TemplateProcessingException) {
      // TODO(b/31039466): Extra logging to track down a rare issue.
      log.warn("Template processing exception with context in the following state: $context")
      throw RuntimeException(e)
    }

    val manifests = sourceFiles.filter { it.name == SdkConstants.FN_ANDROID_MANIFEST_XML }

    // Ignore test configurations here.
    dependencies[SdkConstants.GRADLE_COMPILE_CONFIGURATION].forEach { metadata.dependencies.add(it!!) }
    classpathEntries.forEach { metadata.classpathEntries.add(it) }
    manifests.forEach { parseManifestForPermissions(it, metadata) }
    plugins.forEach { metadata.plugins.add(it) }
    targetFiles.forEach { metadata.modifiedFiles.add(it) }
    return metadata
  }

  @JvmStatic
  fun getRecipeMetadata(recipe: Recipe, project: Project): List<RecipeMetadata> {
    val key = Pair(recipe, project)
    if (!recipeMetadataCache.containsKey(key)) {
      val cache = ImmutableList.builder<RecipeMetadata>()
      for (module in AssistActionStateManager.getAndroidModules(project)) {
        cache.add(getRecipeMetadata(recipe, module))
      }
      recipeMetadataCache[key] = cache.build()
    }
    return recipeMetadataCache[key]!!
  }

  @JvmStatic
  fun execute(recipe: Recipe, module: Module) {
    val filesToOpen: List<File> = mutableListOf()
    val moduleRoot = AndroidRootUtil.findModuleRootFolderPath(module)!!
    val rootPath: File = File(FileUtil.generateRandomTemporaryPath(), "unused")
    val context = RenderingContext.Builder
      .newContext(rootPath, module.project)
      .withOutputRoot(moduleRoot)
      .withModuleRoot(moduleRoot)
      .intoOpenFiles(filesToOpen)
      .build()

    writeCommandAction(module.project).withName("Executing recipe instructions").run<Exception> {
      recipe.execute(context.recipeExecutor)
    }

    openEditors(module.project, filesToOpen, true)
  }

  private fun parseManifestForPermissions(f: File, metadata: RecipeMetadata) = try {
    val factory = SAXParserFactory.newInstance()
    val saxParser = factory.newSAXParser()
    saxParser.parse(f, object : DefaultHandler() {
      override fun startElement(uri: String, localName: String, tagName: String, attributes: Attributes) {
        if (tagName == SdkConstants.TAG_USES_PERMISSION || tagName == SdkConstants.TAG_USES_PERMISSION_SDK_23 || tagName == SdkConstants.TAG_USES_PERMISSION_SDK_M) {
          // Most permissions are "android.permission.XXX", so for readability, just remove the prefix if present
          val permission = attributes.getValue(SdkConstants.ANDROID_NS_NAME_PREFIX + SdkConstants.ATTR_NAME)
            .replace(SdkConstants.ANDROID_PKG_PREFIX + SdkConstants.ATTR_PERMISSION + ".", "")
          metadata.permissions.add(permission)
        }
      }
    })
  }
  catch (e: Exception) {
    // This method shouldn't crash the user for any reason, as showing permissions is just informational,
    // but log a warning so developers can see if they make a mistake when creating their service.
    log.warn("Failed to read permissions from AndroidManifest.xml", e)
  }
}