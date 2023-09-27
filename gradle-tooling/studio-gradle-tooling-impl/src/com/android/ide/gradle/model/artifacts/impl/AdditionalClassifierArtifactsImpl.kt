/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.ide.gradle.model.artifacts.impl

import com.android.ide.gradle.model.ArtifactIdentifier
import com.android.ide.gradle.model.artifacts.AdditionalClassifierArtifacts
import java.io.File
import java.io.Serializable

data class AdditionalClassifierArtifactsImpl(
  private val id: ArtifactIdentifier,
  private val sources: List<File>,
  private val javadoc: File?,
  private val mavenPom: File?,
) : AdditionalClassifierArtifacts, Serializable {
  override fun getId() = id
  override fun getSources() = sources
  override fun getJavadoc() = javadoc
  override fun getMavenPom() = mavenPom
}