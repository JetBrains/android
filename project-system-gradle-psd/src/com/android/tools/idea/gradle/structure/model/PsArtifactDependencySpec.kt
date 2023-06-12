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
package com.android.tools.idea.gradle.structure.model

import com.android.SdkConstants.GRADLE_PATH_SEPARATOR
import com.android.ide.common.repository.GradleCoordinate
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import com.intellij.openapi.externalSystem.model.project.dependencies.ArtifactDependencyNode
import com.intellij.openapi.util.text.StringUtil.isNotEmpty
import com.intellij.util.text.nullize
import org.gradle.tooling.model.GradleModuleVersion
import java.util.regex.Pattern

/**
 * Similar to [com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec], with the difference that for the
 * 'Project Structure Dialog' (PSD) we don't care about a dependency's classifier and extension because the PSD matches/merges
 * dependencies obtained from different models (e.g. Gradle model, 'parsed' model and POM model,) and those dependencies can be expressed
 * differently on each model.
 */
interface PsArtifactDependencySpec : Comparable<PsArtifactDependencySpec> {
  val group: String?
  val name: String
  val version: String?

  fun compactNotation(): String = listOfNotNull(group, name, version).joinToString(GRADLE_PATH_SEPARATOR)

  fun getDisplayText(uiSettings: PsUISettings): String = getDisplayText(uiSettings.DECLARED_DEPENDENCIES_SHOW_GROUP_ID, true)

  fun getDisplayText(showGroupId: Boolean, showVersion: Boolean): String =
    buildString {
      if (showGroupId && isNotEmpty(group)) {
        append(group)
        append(GRADLE_PATH_SEPARATOR)
      }
      append(name)
      if (showVersion && isNotEmpty(version)) {
        append(GRADLE_PATH_SEPARATOR)
        append(version)
      }
    }

  // Hiding as a private class to ensure empty string to null conversion.
  private data class Impl(override val group: String?,
                          override val name: String,
                          override val version: String?) : PsArtifactDependencySpec {

    override fun compareTo(other: PsArtifactDependencySpec): Int =
      compareValuesBy(this, other, { it.group }, { it.name })
        .takeUnless { it == 0 }
      ?: GradleCoordinate.COMPARE_PLUS_LOWER.compare(
        GradleCoordinate.parseVersionOnly(this.version.orEmpty()),
        GradleCoordinate.parseVersionOnly(other.version.orEmpty()))


    override fun toString(): String = compactNotation()
  }

  companion object {

    // Regex covering the format group:name:version:classifier@extension. only name group and version are captured, and only name is required.
    // To avoid ambiguity name must not start with a digit and version must start with a digit (otherwise a:b could be parsed as group:name or
    // name:version). This requirement does not seem to be documented anywhere but is assumed elsewhere in the code and is true for all
    // examples of this format that I have seen.
    private val ourPattern = Pattern.compile("^(?:([^:@]*):)?([^\\d+:@][^:@]*)(?::([^:@]*))?(?::[^@]*)?(?:@.*)?$")

    fun create(group: String?, name: String, version: String?): PsArtifactDependencySpec = Impl(group?.nullize(), name, version?.nullize())

    fun create(notation: String): PsArtifactDependencySpec? {
      // Example: org.gradle.test.classifiers:service:1.0 where
      //   group: org.gradle.test.classifiers
      //   name: service
      //   version: 1.0
      val matcher = ourPattern.matcher(notation)
      return if (!matcher.matches()) {
        null
      }
      else create(matcher.group(1), matcher.group(2), matcher.group(3))
    }

    fun create(dependency: ArtifactDependencyModel): PsArtifactDependencySpec =
      create(dependency.group().toString(), dependency.name().forceString(), dependency.version().toString())

    fun create(coordinates: GradleCoordinate): PsArtifactDependencySpec =
      create(coordinates.groupId, coordinates.artifactId, coordinates.revision)

    fun create(moduleVersion: GradleModuleVersion): PsArtifactDependencySpec =
      create(moduleVersion.group, moduleVersion.name, moduleVersion.version)

    fun create(id: ArtifactDependencyNode): PsArtifactDependencySpec =
      create(id.group, id.module, id.version)
  }
}

