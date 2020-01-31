/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.ide.gradle.model.sources.impl

import com.android.ide.gradle.model.sources.SourcesAndJavadocArtifact
import com.android.ide.gradle.model.sources.SourcesAndJavadocArtifactIdentifier
import java.io.File
import java.io.Serializable

data class SourcesAndJavadocArtifactImpl(
  private val id: SourcesAndJavadocArtifactIdentifier,
  private val sources: File?,
  private val javadoc: File?
) : SourcesAndJavadocArtifact, Serializable {
  override fun getId() = id
  override fun getSources() = sources
  override fun getJavadoc() = javadoc
}