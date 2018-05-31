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
package com.android.tools.idea.gradle.structure.model.android

import com.android.builder.model.SigningConfig
import com.android.tools.idea.gradle.structure.model.PsModelCollection
import java.util.function.Consumer

class PsSigningConfigCollection internal constructor(private val parent: PsAndroidModule) : PsModelCollection<PsSigningConfig> {
  private val signingConfigsByName = mutableMapOf<String, PsSigningConfig>()

  init {
    val signingConfigsFromGradle = mutableMapOf<String, SigningConfig>()
    for (signingConfig in parent.gradleModel.androidProject.signingConfigs) {
      signingConfigsFromGradle[signingConfig.name] = signingConfig
    }

    val parsedModel = parent.parsedModel
    if (parsedModel != null) {
      val android = parsedModel.android()
      if (android != null) {
        val parsedSigningConfigs = android.signingConfigs()
        for (parsedSigningConfig in parsedSigningConfigs) {
          val name = parsedSigningConfig.name()
          val fromGradle = signingConfigsFromGradle.remove(name)

          val model = PsSigningConfig(parent, fromGradle, parsedSigningConfig)
          signingConfigsByName[name] = model
        }
      }
    }

    if (!signingConfigsFromGradle.isEmpty()) {
      for (signingConfig in signingConfigsFromGradle.values) {
        val model = PsSigningConfig(parent, signingConfig, null)
        signingConfigsByName[signingConfig.name] = model
      }
    }
  }

  fun findElement(name: String): PsSigningConfig? {
    return signingConfigsByName[name]
  }

  override fun forEach(consumer: Consumer<PsSigningConfig>) {
    signingConfigsByName.values.forEach(consumer)
  }

  fun addNew(name: String): PsSigningConfig {
    assert(parent.parsedModel != null)
    val androidModel = parent.parsedModel!!.android()!!

    androidModel.addSigningConfig(name)
    val signingConfigs = androidModel.signingConfigs()
    val model = PsSigningConfig(parent, null, signingConfigs.single { it -> it.name() == name })
    signingConfigsByName[name] = model
    parent.isModified = true
    return model
  }

  fun remove(name: String) {
    assert(parent.parsedModel != null)
    val androidModel = parent.parsedModel!!.android()!!
    androidModel.removeSigningConfig(name)
    signingConfigsByName.remove(name)
    parent.isModified = true
  }
}
