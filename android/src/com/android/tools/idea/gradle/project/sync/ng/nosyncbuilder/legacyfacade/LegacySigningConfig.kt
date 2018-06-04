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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.legacyfacade

import com.android.ide.common.gradle.model.UnusedModelMethodException
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.androidproject.SigningConfig
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.OldSigningConfig
import java.io.File

open class LegacySigningConfig(private val signingConfig: SigningConfig) : OldSigningConfig {
  override fun getName(): String = signingConfig.name
  override fun getStoreFile(): File? = signingConfig.storeFile
  override fun getStorePassword(): String? = signingConfig.storePassword
  override fun getKeyAlias(): String? = signingConfig.keyAlias

  override fun isV1SigningEnabled(): Boolean = throw UnusedModelMethodException("isV1SigningEnabled")
  override fun getKeyPassword(): String? = throw UnusedModelMethodException("getKeyPassword")
  override fun getStoreType(): String? = throw UnusedModelMethodException("getStoreType")
  override fun isV2SigningEnabled(): Boolean = throw UnusedModelMethodException("isV2SigningEnabled")
  override fun isSigningReady(): Boolean = throw UnusedModelMethodException("isSigningReady")

  override fun toString(): String = "LegacyBaseArtifact{" +
                                    "name=$name," +
                                    "storeFile=$storeFile," +
                                    "storePassword=$storePassword," +
                                    "keyAlias=$keyAlias" +
                                    "}"
}

