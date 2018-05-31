/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model.android

import com.android.builder.model.BuildType
import com.android.tools.idea.gradle.structure.model.PsModelCollection
import java.util.function.Consumer

class PsBuildTypeCollection internal constructor(private val parent: PsAndroidModule) : PsModelCollection<PsBuildType> {
  private val buildTypesByName = mutableMapOf<String, PsBuildType>()

  init {
    val buildTypesFromGradle = mutableMapOf<String, BuildType>()
    for (container in parent.gradleModel.androidProject.buildTypes) {
      val buildType = container.buildType
      buildTypesFromGradle[buildType.name] = buildType
    }

    val parsedModel = parent.parsedModel
    if (parsedModel != null) {
      val android = parsedModel.android()
      if (android != null) {
        val parsedBuildTypes = android.buildTypes()
        for (parsedBuildType in parsedBuildTypes) {
          val name = parsedBuildType.name()
          val fromGradle = buildTypesFromGradle.remove(name)

          val model = PsBuildType(parent, fromGradle, parsedBuildType)
          buildTypesByName[name] = model
        }
      }
    }

    if (!buildTypesFromGradle.isEmpty()) {
      for (buildType in buildTypesFromGradle.values) {
        val model = PsBuildType(parent, buildType, null)
        buildTypesByName[buildType.name] = model
      }
    }
  }

  fun findElement(name: String): PsBuildType? {
    return buildTypesByName[name]
  }

  override fun forEach(consumer: Consumer<PsBuildType>) {
    buildTypesByName.values.forEach(consumer)
  }

  fun addNew(name: String): PsBuildType {
    assert(parent.parsedModel != null)
    val androidModel = parent.parsedModel!!.android()!!
    androidModel.addBuildType(name)
    val buildTypes = androidModel.buildTypes()
    val model = PsBuildType(parent, null, buildTypes.single { it.name() == name })
    buildTypesByName[name] = model
    parent.isModified = true
    return model
  }

  fun remove(name: String) {
    assert(parent.parsedModel != null)
    val androidModel = parent.parsedModel!!.android()!!
    androidModel.removeBuildType(name)
    buildTypesByName.remove(name)
    parent.isModified = true
  }
}
