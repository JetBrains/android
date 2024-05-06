/*
 * Copyright 2010-2017 JetBrains s.r.o.
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

package org.jetbrains.kotlin.android.synthetic.idea

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.android.synthetic.AndroidCommandLineProcessor.Companion.ANDROID_COMPILER_PLUGIN_ID
import org.jetbrains.kotlin.android.synthetic.AndroidCommandLineProcessor.Companion.DEFAULT_CACHE_IMPL_OPTION
import org.jetbrains.kotlin.android.synthetic.AndroidCommandLineProcessor.Companion.ENABLED_OPTION
import org.jetbrains.kotlin.android.synthetic.AndroidCommandLineProcessor.Companion.EXPERIMENTAL_OPTION
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.gradleJava.configuration.GradleProjectImportHandler
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension

class AndroidExtensionProperties {
  var hasAndroidExtensionsPlugin: Boolean = false
  var isExperimental: Boolean = false
  var defaultCacheImplementation: String = ""
}

val ANDROID_EXTENSION_PROPERTIES = Key.create(AndroidExtensionProperties::class.java, 1)

@Suppress("unused")
class AndroidExtensionsProjectResolverExtension : AbstractProjectResolverExtension() {
    override fun getExtraProjectModelClasses() = setOf(AndroidExtensionsGradleModel::class.java)
    override fun getToolingExtensionsClasses() = setOf(AndroidExtensionsModelBuilderService::class.java)

    override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
        val androidExtensionsModel = resolverCtx.getExtraProject(gradleModule, AndroidExtensionsGradleModel::class.java)
        if (androidExtensionsModel != null) {
            ideModule.createChild(ANDROID_EXTENSION_PROPERTIES, AndroidExtensionProperties().apply {
                hasAndroidExtensionsPlugin = androidExtensionsModel.hasAndroidExtensionsPlugin
                isExperimental = androidExtensionsModel.isExperimental
                defaultCacheImplementation = androidExtensionsModel.defaultCacheImplementation
            })
        }
        super.populateModuleExtraModels(gradleModule, ideModule)
    }
}

class AndroidExtensionsGradleImportHandler : GradleProjectImportHandler {
    override fun importBySourceSet(facet: KotlinFacet, sourceSetNode: DataNode<GradleSourceSetData>) {
        val module = ExternalSystemApiUtil.findParent(sourceSetNode, ProjectKeys.MODULE) ?: return
        importByModule(facet, module)
    }

    override fun importByModule(facet: KotlinFacet, moduleNode: DataNode<ModuleData>) {
        val facetSettings = facet.configuration.settings
        val commonArguments = facetSettings.compilerArguments ?: CommonCompilerArguments.DummyImpl()

        fun makePluginOption(key: String, value: String) = "plugin:$ANDROID_COMPILER_PLUGIN_ID:$key=$value"

        val newPluginOptions = (commonArguments.pluginOptions ?: emptyArray())
                .filterTo(mutableListOf()) { !it.startsWith("plugin:$ANDROID_COMPILER_PLUGIN_ID:") } // Filter out old options

        val propertiesNode = ExternalSystemApiUtil.find(moduleNode, ANDROID_EXTENSION_PROPERTIES)
        val propertiesData = propertiesNode?.data
        if (propertiesData?.hasAndroidExtensionsPlugin == true) {
            newPluginOptions += makePluginOption(EXPERIMENTAL_OPTION.optionName, propertiesData.isExperimental.toString())
            newPluginOptions += makePluginOption(ENABLED_OPTION.optionName, propertiesData.hasAndroidExtensionsPlugin.toString())
            newPluginOptions += makePluginOption(DEFAULT_CACHE_IMPL_OPTION.optionName, propertiesData.defaultCacheImplementation)
        }

        commonArguments.pluginOptions = newPluginOptions.toTypedArray()
        facetSettings.compilerArguments = commonArguments
    }
}
