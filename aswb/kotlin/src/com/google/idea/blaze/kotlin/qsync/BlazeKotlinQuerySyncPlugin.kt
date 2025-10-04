/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.kotlin.qsync

import com.google.idea.blaze.base.model.primitives.LanguageClass
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot
import com.google.idea.blaze.base.projectview.ProjectViewSet
import com.google.idea.blaze.base.qsync.BlazeQuerySyncPlugin
import com.google.idea.blaze.base.sync.projectview.LanguageSupport
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.java.projectview.JavaLanguageLevelSection
import com.google.idea.common.experiments.BoolExperiment
import com.intellij.facet.FacetManager
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.unfrozen
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider.Companion.isK2Mode
import org.jetbrains.kotlin.idea.compiler.configuration.Kotlin2JvmCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.facet.KotlinFacet.Companion.get
import org.jetbrains.kotlin.idea.facet.KotlinFacetConfiguration
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import org.jetbrains.kotlin.idea.fir.extensions.KotlinK2BundledCompilerPlugins

/** Supports Kotlin.  */
class BlazeKotlinQuerySyncPlugin : BlazeQuerySyncPlugin {
  override fun updateProjectSettingsForQuerySync(project: Project, context: Context<*>, projectViewSet: ProjectViewSet) {
    if (!isKotlinProject(projectViewSet)) {
      return
    }

    // Set jvm-target from java language level
    val javaLanguageLevel =
      JavaLanguageLevelSection.getLanguageLevel(projectViewSet, LanguageLevel.JDK_21)
    setProjectJvmTarget(project, javaLanguageLevel)
  }

  override fun updateProjectStructureForQuerySync(
    project: Project,
    context: Context<*>,
    models: IdeModifiableModelsProvider,
    workspaceRoot: WorkspaceRoot,
    workspaceModule: Module,
    androidResourceDirectories: Set<String>,
    androidSourcePackages: Set<String>,
    workspaceLanguageSettings: WorkspaceLanguageSettings
  ) {
    val kotlinFacet: KotlinFacet = getOrCreateKotlinFacet(models, workspaceModule)
    // TODO(xinruiy): makes BlazeGoogle3AndroidKotlinPluginOptionsProvider#hasParcelizeDependency
    // compatible with query sync. So that we can
    // collect new plugin options for query sync targets.
    updatePluginOptions(kotlinFacet, listOf())
  }

  companion object {
    private val qsyncDisableCompose = BoolExperiment("qsync.disable.compose", false)

    private fun getOrCreateKotlinFacet(
      models: IdeModifiableModelsProvider, module: Module
    ): KotlinFacet {
      var facet = get(module)
      if (facet != null) {
        return facet
      }
      val facetManager = FacetManager.getInstance(module)
      val modifiableFacetModel = models.getModifiableFacetModel(module)
      facet = facetManager.createFacet(KotlinFacetType.INSTANCE, KotlinFacetType.NAME, null)
      modifiableFacetModel.addFacet(facet)
      return facet
    }

    private fun updatePluginOptions(
      kotlinFacet: KotlinFacet,
      newPluginOptions: List<String>
    ) {
      val facetSettings = kotlinFacet.configuration.settings
      var commonArguments = facetSettings.compilerArguments
      if (commonArguments == null) {
        commonArguments = K2JVMCompilerArguments()
      }

      if (isK2Mode() && !qsyncDisableCompose.value) {
        // Register the bundled directly, as KtCompilerPluginsProviderIdeImpl consistently replaces
        // user's plugin class path with it.
        // Note: This implementation may need updating if the Kotlin plugin alters its provider
        // replacement logic.
        commonArguments.pluginClasspaths = arrayOf(
          KotlinK2BundledCompilerPlugins.COMPOSE_COMPILER_PLUGIN.bundledJarLocation
            .toString(),
        )
      }
      commonArguments.pluginOptions = newPluginOptions.toTypedArray<String>()
      facetSettings.compilerArguments = commonArguments
    }

    private fun setProjectJvmTarget(project: Project, javaLanguageLevel: LanguageLevel) {
      val k2JVMCompilerArguments = Kotlin2JvmCompilerArgumentsHolder.getInstance(project).settings.unfrozen()

      val javaVersion = javaLanguageLevel.toJavaVersion().toString()
      k2JVMCompilerArguments.jvmTarget = javaVersion
      Kotlin2JvmCompilerArgumentsHolder.getInstance(project).settings = k2JVMCompilerArguments
    }

    private fun isKotlinProject(projectViewSet: ProjectViewSet): Boolean {
      val workspaceLanguageSettings =
        LanguageSupport.createWorkspaceLanguageSettings(projectViewSet)
      return workspaceLanguageSettings.isLanguageActive(LanguageClass.KOTLIN)
    }
  }
}
