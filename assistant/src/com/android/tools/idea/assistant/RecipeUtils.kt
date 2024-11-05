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
import com.android.tools.idea.npw.model.actuallyRender
import com.android.tools.idea.npw.model.findReferences
import com.android.tools.idea.npw.template.getExistingModuleTemplateDataBuilder
import com.android.tools.idea.templates.TemplateUtils.openEditors
import com.android.tools.idea.templates.recipe.RenderingContext
import com.android.tools.idea.wizard.template.Recipe
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import com.intellij.openapi.command.WriteCommandAction.writeCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import javax.xml.parsers.SAXParserFactory
import org.jetbrains.android.AndroidPluginDisposable
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidRootUtil
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

private val log: Logger
  get() = logger<RecipeUtils>()

/** A collection of utility methods for interacting with an `Recipe`. */
object RecipeUtils {
  // TODO(qumeric): remove this cache. It is not needed anymore, everything is fast without it.
  private val recipeMetadataCache: MutableMap<Pair<Recipe, Project>, List<RecipeMetadata>> = hashMapOf()

  private fun getRecipeMetadata(recipe: Recipe, module: Module): RecipeMetadata {
    val key = Pair(recipe, module.project)
    if (recipeMetadataCache.containsKey(key)) {
      val metadata = recipeMetadataCache[key]!!.find { it.recipe == recipe }
      if (metadata != null) {
        return metadata
      }
    }
    val metadata = RecipeMetadata(recipe, module)

    val moduleRoot = AndroidRootUtil.findModuleRootFolderPath(module)!!
    // TODO: do we care about this path?
    val rootPath = File(FileUtil.generateRandomTemporaryPath(), "unused")
    rootPath.deleteOnExit()

    val context =
      RenderingContext(
        project = module.project,
        module = module,
        commandName = "Unnamed",
        templateData = getExistingModuleTemplateDataBuilder(module).build(),
        outputRoot = rootPath,
        moduleRoot = moduleRoot,
        dryRun = false,
        showErrors = true,
      )

    // TODO(b/149085696): create logging events for Firebase?
    recipe.findReferences(context)

    setUpMetadata(context, metadata)
    return metadata
  }

  @VisibleForTesting
  fun setUpMetadata(context: RenderingContext, metadata: RecipeMetadata) {
    // TODO(qumeric): consider using RenderingContext instead of custom RecipeMetadata class
    with(context) {
      val manifests = mutableListOf<File>()
      // FIXME(qumeric): sourceFiles.filter { it.name == SdkConstants.FN_ANDROID_MANIFEST_XML }

      // Ignore test configurations here.
      dependencies[SdkConstants.GRADLE_IMPLEMENTATION_CONFIGURATION].forEach {
        metadata.dependencies.add(it!!)
      }
      dependencies[SdkConstants.GRADLE_API_CONFIGURATION].forEach {
        metadata.dependencies.add(it!!)
      }
      classpathEntries.forEach { metadata.classpathEntries.add(it) }
      manifests.forEach { parseManifestForPermissions(it, metadata) }
      plugins.forEach { metadata.plugins.add(it) }
      targetFiles.forEach { metadata.modifiedFiles.add(it) }
    }
  }

  @JvmStatic
  fun getRecipeMetadata(recipe: Recipe, project: Project): List<RecipeMetadata> {
    val key = Pair(recipe, project)

    val cached = recipeMetadataCache[key]
    if (cached != null) {
      return cached
    }

    val metadataBuilder = ImmutableList.builder<RecipeMetadata>()
    for (module in getAndroidModules(project)) {
      metadataBuilder.add(getRecipeMetadata(recipe, module))
    }

    val metadata = metadataBuilder.build()
    recipeMetadataCache[key] = metadata

    Disposer.register(AndroidPluginDisposable.getProjectInstance(project)) {
      recipeMetadataCache.remove(key)
    }

    return metadata
  }

  @JvmStatic
  fun execute(recipe: Recipe, module: Module) {
    val moduleRoot = AndroidRootUtil.findModuleRootFolderPath(module)!!
    val rootPath = File(FileUtil.generateRandomTemporaryPath(), "unused")

    val context =
      RenderingContext(
        project = module.project,
        module = module,
        commandName = "Unnamed",
        templateData = getExistingModuleTemplateDataBuilder(module).build(),
        outputRoot = rootPath,
        moduleRoot = moduleRoot,
        dryRun = false,
        showErrors = true,
      )

    writeCommandAction(module.project).withName("Executing recipe instructions").run<Exception> {
      // TODO(b/149085696): create logging events for Firebase?
      recipe.actuallyRender(context)
    }

    openEditors(module.project, context.filesToOpen, true)
  }

  @VisibleForTesting
  fun getRecipeMetadataCacheSize(): Int {
    return recipeMetadataCache.size
  }

  private fun parseManifestForPermissions(f: File, metadata: RecipeMetadata) =
    try {
      val factory = SAXParserFactory.newInstance()
      val saxParser = factory.newSAXParser()
      saxParser.parse(
        f,
        object : DefaultHandler() {
          override fun startElement(
            uri: String,
            localName: String,
            tagName: String,
            attributes: Attributes,
          ) {
            if (
              tagName == SdkConstants.TAG_USES_PERMISSION ||
                tagName == SdkConstants.TAG_USES_PERMISSION_SDK_23 ||
                tagName == SdkConstants.TAG_USES_PERMISSION_SDK_M
            ) {
              // Most permissions are "android.permission.XXX", so for readability, just remove the
              // prefix if present
              val permission =
                attributes
                  .getValue(SdkConstants.ANDROID_NS_NAME_PREFIX + SdkConstants.ATTR_NAME)
                  .replace(SdkConstants.ANDROID_PKG_PREFIX + SdkConstants.ATTR_PERMISSION + ".", "")
              metadata.permissions.add(permission)
            }
          }
        },
      )
    } catch (e: Exception) {
      // This method shouldn't crash the user for any reason, as showing permissions is just
      // informational,
      // but log a warning so developers can see if they make a mistake when creating their service.
      log.warn("Failed to read permissions from AndroidManifest.xml", e)
    }

  private fun getAndroidModules(project: Project) =
    ModuleManager.getInstance(project)
      .modules
      .filter { module -> AndroidFacet.getInstance(module) != null }
      .toList()
}
