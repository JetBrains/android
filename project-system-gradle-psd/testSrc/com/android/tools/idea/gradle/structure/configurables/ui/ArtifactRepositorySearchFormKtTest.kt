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

import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.dsl.api.ext.ExtModel
import com.android.tools.idea.gradle.structure.ARTIFACT_REPOSITORY_SEARCH_FORM_KT_PREPARE_ARTIFACT_VERSION_CHOICES
import com.android.tools.idea.gradle.structure.PsdGradleFileModelTestCase
import com.android.tools.idea.gradle.structure.model.PsModel
import com.android.tools.idea.gradle.structure.model.PsVariables
import com.android.tools.idea.gradle.structure.model.android.asParsed
import com.android.tools.idea.gradle.structure.model.meta.DslText
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.annotateWithError
import com.android.tools.idea.gradle.structure.model.meta.annotated
import com.android.tools.idea.gradle.repositories.search.FoundArtifact
import com.intellij.testFramework.RunsInEdt
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Test

@RunsInEdt
class ArtifactRepositorySearchFormKtTest : PsdGradleFileModelTestCase() {

  private val foundArtifact = FoundArtifact(
    "repository", "org.example.group.id", "artifact-name", listOf(GradleVersion(1, 0), GradleVersion(1, 1), GradleVersion(2, 0)))

  private val notExactQuery = ArtifactSearchQuery("group", "name", "9.99", gradleCoordinates = null)
  private val exactMatchingQuery =
    ArtifactSearchQuery(
      groupId = "org.example.group.id",
      artifactName = "artifact-name",
      version = "9.99",
      gradleCoordinates = GradleCoordinate.parseCoordinateString("org.example.group.id:artifact-name:9.99"))

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
    writeToBuildFile(ARTIFACT_REPOSITORY_SEARCH_FORM_KT_PREPARE_ARTIFACT_VERSION_CHOICES)

    val variables = object: PsVariables(stubModel, "variables", "variables", null) {
      override fun getContainer(from: PsModel): ExtModel? = gradleBuildModel.ext()
    }
    val choices = prepareArtifactVersionChoices(notExactQuery, foundArtifact, variables)
    assertThat(choices, equalTo(listOf(
      GradleVersion(2, 0).asParsed().annotated(),
      (GradleVersion(2, 0) asVariable "ver20").annotated(),
      GradleVersion(1, 1).asParsed().annotated(),
      (GradleVersion(1, 1) asVariable "inTheMap.itemVer11").annotated(),
      GradleVersion(1, 0).asParsed().annotated(),
      (GradleVersion(1, 0) asVariable "inTheMap.itemVer10").annotated(),
      (GradleVersion(1, 0) asVariable "ver10").annotated()
    )))

    val choicesWithNotFound = prepareArtifactVersionChoices(exactMatchingQuery, foundArtifact, variables)
    assertThat(choicesWithNotFound, equalTo(listOf(
      GradleVersion(9, 99).asParsed().annotateWithError("not found"),
      GradleVersion(2, 0).asParsed().annotated(),
      (GradleVersion(2, 0) asVariable "ver20").annotated(),
      GradleVersion(1, 1).asParsed().annotated(),
      (GradleVersion(1, 1) asVariable "inTheMap.itemVer11").annotated(),
      GradleVersion(1, 0).asParsed().annotated(),
      (GradleVersion(1, 0) asVariable "inTheMap.itemVer10").annotated(),
      (GradleVersion(1, 0) asVariable "ver10").annotated()
    )))
  }

}

class ArtifactRepositorySearchFormKtLightTest {
  @Test
  fun testParseArtifactSearchQuery_fullyQualified() {
    assertThat("com.google.guava:guava:26.0".parseArtifactSearchQuery(),
               equalTo(ArtifactSearchQuery(
                 "com.google.guava", "guava", "26.0", GradleCoordinate.parseCoordinateString("com.google.guava:guava:26.0"))))
  }

  @Test
  fun testParseArtifactSearchQuery_groupAndName() {
    assertThat("com.google.guava:guava".parseArtifactSearchQuery(), equalTo(ArtifactSearchQuery("com.google.guava", "guava")))
  }

  @Test
  fun testParseArtifactSearchQuery_groupOnly() {
    assertThat("com.google.guava".parseArtifactSearchQuery(), equalTo(ArtifactSearchQuery(groupId = "com.google.guava")))
  }

  @Test
  fun testParseArtifactSearchQuery_nameOnly() {
    assertThat("guava".parseArtifactSearchQuery(), equalTo(ArtifactSearchQuery(artifactName = "guava")))
  }

  @Test
  fun testParseArtifactSearchQuery_groupAndColon() {
    assertThat("short:".parseArtifactSearchQuery(), equalTo(ArtifactSearchQuery(groupId = "short")))
  }

  @Test
  fun testParseArtifactSearchQuery_colonAndName() {
    assertThat(":guava".parseArtifactSearchQuery(), equalTo(ArtifactSearchQuery(artifactName = "guava")))
  }

  @Test
  fun testParseArtifactSearchQuery_wildcards() {
    assertThat("com.*:gu*".parseArtifactSearchQuery(), equalTo(ArtifactSearchQuery(groupId = "com.*", artifactName = "gu*")))
  }
}

private infix fun <T : Any> T.asVariable(variable: String) = ParsedValue.Set.Parsed(dslText = DslText.Reference(variable), value = this)
