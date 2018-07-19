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
package com.android.tools.idea.gradle.structure.configurables.ui

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import com.android.tools.idea.gradle.structure.model.PsModel
import com.android.tools.idea.gradle.structure.model.PsParsedDependencies
import com.android.tools.idea.gradle.structure.model.PsVariables
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.asParsed
import com.android.tools.idea.gradle.structure.model.meta.DslText
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.repositories.search.FoundArtifact
import org.hamcrest.CoreMatchers
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Test

class ArtifactRepositorySearchFormKtTest : GradleFileModelTestCase() {

  private val foundArtifact = FoundArtifact(
    "repository", "org.example.group.id", "artifact-name", listOf(GradleVersion(1, 0), GradleVersion(1, 1), GradleVersion(2, 0)))

  @Test
  fun testVersionToLibrary() {
    assertThat(
      versionToLibrary(foundArtifact, ParsedValue.Set.Parsed(GradleVersion(1, 0), DslText.Literal)),
      equalTo("org.example.group.id:artifact-name:1.0".asParsed()))
    // The actual list of versions does not matter.
    assertThat(
      versionToLibrary(foundArtifact, ParsedValue.Set.Parsed(GradleVersion(1, 1, 1), DslText.Literal)),
      equalTo("org.example.group.id:artifact-name:1.1.1".asParsed()))
    // References.
    assertThat<ParsedValue<String>>(
      versionToLibrary(foundArtifact, ParsedValue.Set.Parsed(GradleVersion(1, 0), DslText.Reference("artifactVer"))),
      equalTo(ParsedValue.Set.Parsed(
        "org.example.group.id:artifact-name:1.0",
        DslText.InterpolatedString("org.example.group.id:artifact-name:\${artifactVer}"))))
  }

  private val stubModel = object : PsModel {
    override val parent: PsModel? = null
    override var isModified: Boolean = true
    override val name: String = "model"
    override val isDeclared: Boolean = true
  }

  @Test
  fun testPrepareArtifactVersionChoices() {
    writeToBuildFile(
      """
        ext {
          ver10 = "1.0"
          ver20 = "2.0"
          otherVer = "3.0
          nonVer = "something"
          nonString = 123
          inTheMap = [
            "itemVer10": "1.0",
            "itemVer11": "1.1",
            "other": true
          ]
        }"""
    )

    val variables = PsVariables(stubModel, "variables", gradleBuildModel.ext(), null)
    val choices = prepareArtifactVersionChoices(foundArtifact, variables)
    assertThat(choices, equalTo(listOf(
      GradleVersion(2, 0).asParsed(),
      GradleVersion(2, 0) asVariable "ver20",
      GradleVersion(1, 1).asParsed(),
      GradleVersion(1, 1) asVariable "inTheMap.itemVer11",
      GradleVersion(1, 0).asParsed(),
      GradleVersion(1, 0) asVariable "inTheMap.itemVer10",
      GradleVersion(1, 0) asVariable "ver10"
    )))
  }
}

private infix fun <T : Any> T.asVariable(variable: String) = ParsedValue.Set.Parsed(dslText = DslText.Reference(variable), value = this)
