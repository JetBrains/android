/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.android.configure

import com.android.tools.idea.gradle.project.sync.idea.svs.IdeAndroidModels
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.externalSystem.util.Order
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.idea.gradle.configuration.KotlinAndroidSourceSetData
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModelBuilder
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinMPPGradleProjectResolver
import org.jetbrains.kotlin.idea.gradleJava.configuration.getMppModel
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModel
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
import org.jetbrains.kotlin.idea.projectModel.KotlinTarget
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension

@Order(ExternalSystemConstants.UNORDERED - 1)
class KotlinAndroidMPPGradleProjectResolver : AbstractProjectResolverExtension() {
    private val isAndroidProject by lazy {
      resolverCtx.hasModulesWithModel(IdeAndroidModels::class.java)
    }

    override fun getToolingExtensionsClasses(): Set<Class<out Any>> {
        return setOf(KotlinMPPGradleModelBuilder::class.java, KotlinTarget::class.java, Unit::class.java)
    }

    override fun getExtraProjectModelClasses(): Set<Class<out Any>> {
        return setOf(KotlinMPPGradleModel::class.java, KotlinTarget::class.java)
    }

    override fun createModule(gradleModule: IdeaModule, projectDataNode: DataNode<ProjectData>): DataNode<ModuleData> {
        return super.createModule(gradleModule, projectDataNode)!!.also {
            initializeModuleData(gradleModule, it)
        }
    }

    private fun initializeModuleData(
        gradleModule: IdeaModule,
        mainModuleData: DataNode<ModuleData>
    ) {
        if (!isAndroidProject) return

        val mppModel = resolverCtx.getMppModel(gradleModule) ?: return

        val androidSourceSets = mppModel
            .targets
            .asSequence()
            .flatMap { it.compilations.asSequence() }
            .filter { it.platform == KotlinPlatform.ANDROID }
            .mapNotNull { KotlinMPPGradleProjectResolver.createSourceSetInfo(it, gradleModule, resolverCtx) }
            .toList()
        mainModuleData.createChild(KotlinAndroidSourceSetData.KEY, KotlinAndroidSourceSetData(androidSourceSets))
    }
}