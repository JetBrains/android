/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.registerExtension
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.idea.gradleTooling.KotlinGradleModel
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModel
import org.jetbrains.kotlin.idea.gradleTooling.model.kapt.KaptGradleModel
import org.jetbrains.plugins.gradle.model.ExternalProject
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension

sealed class CapturePlatformModelsProjectResolverExtension(val mode: TestGradleModelProviderMode) : AbstractProjectResolverExtension() {
  class IdeModels : CapturePlatformModelsProjectResolverExtension(TestGradleModelProviderMode.IDE_MODELS)
  class TestGradleModels : CapturePlatformModelsProjectResolverExtension(TestGradleModelProviderMode.TEST_GRADLE_MODELS)
  class TestExceptionModels : CapturePlatformModelsProjectResolverExtension(TestGradleModelProviderMode.TEST_EXCEPTION_MODELS)

  companion object {
    private val kotlinModels = mutableMapOf<String, KotlinGradleModel>()
    private val kaptModels = mutableMapOf<String, KaptGradleModel>()
    private val mppModels = mutableMapOf<String, KotlinMPPGradleModel>()
    private val externalProjectModels = mutableMapOf<String, ExternalProject>()
    private val testGradleModels = mutableMapOf<String, TestGradleModel>()
    private val testParameterizedGradleModels = mutableMapOf<String, TestParameterizedGradleModel>()
    private val testExceptionModels = mutableMapOf<String, TestExceptionModel>()

    fun getKotlinModel(module: Module): KotlinGradleModel? = kotlinModels[getGradleProjectPath(module)]
    fun getKaptModel(module: Module): KaptGradleModel? = kaptModels[getGradleProjectPath(module)]
    fun getMppModel(module: Module): KotlinMPPGradleModel? = mppModels[getGradleProjectPath(module)]
    fun getExternalProjectModel(module: Module): ExternalProject? = externalProjectModels[getGradleProjectPath(module)]
    fun getTestGradleModel(module: Module): TestGradleModel? = testGradleModels[getGradleProjectPath(module)]
    fun getTestParameterizedGradleModel(module: Module): TestParameterizedGradleModel? =
      testParameterizedGradleModels[getGradleProjectPath(module)]

    fun getTestExceptionModel(module: Module): TestExceptionModel? = testExceptionModels[getGradleProjectPath(module)]

    private fun getGradleProjectPath(module: Module): String? {
      return ExternalSystemApiUtil.getExternalProjectPath(module)
        .takeIf { ExternalSystemApiUtil.getExternalModuleType(module) == null } // Return models for holder modules only.
    }

    fun reset() {
      kotlinModels.clear()
      kaptModels.clear()
      mppModels.clear()
      externalProjectModels.clear()
      testGradleModels.clear()
      testParameterizedGradleModels.clear()
      testExceptionModels.clear()
    }

    fun registerTestHelperProjectResolver(prototypeInstance: CapturePlatformModelsProjectResolverExtension, disposable: Disposable) {
      ApplicationManager.getApplication().registerExtension(
        @Suppress("UnstableApiUsage")
        EP_NAME,
        prototypeInstance, // Note: a new instance is created by the external system.
        disposable
      )
      Disposer.register(disposable, object : Disposable {
        override fun dispose() {
          reset()
        }
      })
    }
  }

  override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
    val gradleProjectPath = ideModule.data.linkedExternalProjectPath
    resolverCtx.getExtraProject(gradleModule, KotlinGradleModel::class.java)?.let { kotlinModels[gradleProjectPath] = it }
    // The KaptGradleModel is present for Java modules
    resolverCtx.getExtraProject(gradleModule, KaptGradleModel::class.java)?.let {
      kaptModels[gradleProjectPath] = it
    }
    resolverCtx.getExtraProject(gradleModule, KotlinMPPGradleModel::class.java)?.let {
      mppModels[gradleProjectPath] = it
    }
    // For Android modules it is contained within the IdeAndroidModels class
    resolverCtx.getExtraProject(gradleModule, IdeAndroidModels::class.java)?.let {
      it.kaptGradleModel?.let { kaptModel ->
        kaptModels[gradleProjectPath] = kaptModel
      }
    }
    resolverCtx.getExtraProject(gradleModule, ExternalProject::class.java)?.let {
      externalProjectModels[gradleProjectPath] = it
    }
    resolverCtx.getExtraProject(gradleModule, TestGradleModel::class.java)?.let {
      testGradleModels[gradleProjectPath] = it
    }
    resolverCtx.getExtraProject(gradleModule, TestParameterizedGradleModel::class.java)?.let {
      testParameterizedGradleModels[gradleProjectPath] = it
    }
    resolverCtx.getExtraProject(gradleModule, TestExceptionModel::class.java)?.let {
      testExceptionModels[gradleProjectPath] = it
    }
    super.populateModuleExtraModels(gradleModule, ideModule)
  }

  override fun getToolingExtensionsClasses(): Set<Class<*>> {
    return setOf(TestModelBuilderService::class.java)
  }

  override fun getModelProvider(): ProjectImportModelProvider {
    return TestGradleModelProvider("EHLO", mode)
  }

  override fun getExtraProjectModelClasses(): Set<Class<*>> {
    error("Not expected to be called when `getModelProvider` is overridden")
  }
}

fun KotlinGradleModel.testSourceSetNames(): Collection<String> = cachedCompilerArgumentsBySourceSet.keys
fun KaptGradleModel.testSourceSetNames(): Collection<String> = sourceSets.map { it.sourceSetName }
