/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.java.model.impl

import com.android.java.model.ArtifactModel
import java.io.File
import java.io.Serializable

/**
 * Implementation of the [ArtifactModel] object.
 */
data class ArtifactModelImpl(
  private val myName: String,
  private val myArtifactsByConfiguration: Map<String, Set<File>>) : ArtifactModel, Serializable {

  override fun getName(): String {
    return myName
  }

  override fun getArtifactsByConfiguration(): Map<String, Set<File>> {
    return myArtifactsByConfiguration
  }

  companion object {
    private const val serialVersionUID = 1L
  }
}
