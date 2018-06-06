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
package com.android.tools.idea.gradle.structure.model

import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.project.model.JavaModuleModel
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.PsCollectionBase
import com.android.tools.idea.gradle.structure.model.java.PsJavaModule
import com.android.tools.idea.gradle.util.GradleUtil.getGradlePath
import com.intellij.openapi.module.ModuleManager

enum class ModuleKind { ANDROID, JAVA }
data class ModuleKey(val kind: ModuleKind, val name: String)

class PsModuleCollection(parent: PsProject) : PsCollectionBase<PsModule, ModuleKey, PsProject>(parent) {
  override fun getKeys(from: PsProject): Set<ModuleKey> {
    val result = mutableSetOf<ModuleKey>()
    val projectParsedModel = from.parsedModel
    for (resolvedModel in ModuleManager.getInstance(from.resolvedModel).modules) {
      val gradlePath = getGradlePath(resolvedModel)
      val moduleParsedModel = projectParsedModel.getModuleBuildModel(resolvedModel)
      if (gradlePath != null && moduleParsedModel != null) {
        val gradleModel = AndroidModuleModel.get(resolvedModel)
        if (gradleModel != null) {
          result.add(ModuleKey(ModuleKind.ANDROID, resolvedModel.name))
        }
        else {
          val javaModuleModel = JavaModuleModel.get(resolvedModel)
          if (javaModuleModel != null && javaModuleModel.isBuildable) {
            result.add(ModuleKey(ModuleKind.JAVA, resolvedModel.name))
          }
        }

      }
    }
    return result
  }

  override fun create(key: ModuleKey): PsModule {
    val projectParsedModel = parent.parsedModel
    val moduleManager = ModuleManager.getInstance(parent.resolvedModel)
    val module = moduleManager.findModuleByName(key.name)!!
    val gradlePath = getGradlePath(module)!!
    val moduleParsedModel = projectParsedModel.getModuleBuildModel(module)!!
    return when (key.kind) {
      ModuleKind.ANDROID -> {
        val moduleModel = AndroidModuleModel.get(module)!!
        PsAndroidModule(parent, module.name, moduleModel, gradlePath, moduleParsedModel)
      }
      ModuleKind.JAVA -> PsJavaModule(parent, module.name, gradlePath, JavaModuleModel.get(module)!!, moduleParsedModel)
    }
  }

  // TODO(solodkyy): Support module refreshing.
  override fun update(key: ModuleKey, model: PsModule) = Unit
}