/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.ide.common.repository.GradleCoordinate
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import junit.framework.TestCase
import org.gradle.tooling.model.GradleModuleVersion
import org.mockito.Mockito.mock

/**
 * Tests for [PsArtifactDependencySpec].
 */
class PsArtifactDependencySpecTest : TestCase() {

  fun testCreate_empty() {
    val spec = PsArtifactDependencySpec.create("")
    assertNull(spec)
  }

  fun testCreate_incomplete() {
    val spec = PsArtifactDependencySpec.create(":")
    assertNull(spec)
  }

  fun testCreate_nameOnly() {
    val spec = PsArtifactDependencySpec.create("name")
    assertNotNull(spec)
    assertNull(spec!!.group)
    assertEquals("name", spec.name)
    assertNull(spec.version)
  }

  fun testCreate_nameAndVersion() {
    val spec = PsArtifactDependencySpec.create("name:1.0")
    assertNotNull(spec)
    assertNull(spec!!.group)
    assertEquals("name", spec.name)
    assertEquals("1.0", spec.version)
  }

  fun testCreate_nameAndPlus() {
    val spec = PsArtifactDependencySpec.create("name:+")
    assertNotNull(spec)
    assertNull(spec!!.group)
    assertEquals("name", spec.name)
    assertEquals("+", spec.version)
  }

  fun testCreate_groupAndName() {
    val spec = PsArtifactDependencySpec.create("group:name")
    assertNotNull(spec)
    assertEquals("group", spec!!.group)
    assertEquals("name", spec.name)
    assertNull(spec.version)
  }

  fun testCreate_groupNameAndVersion() {
    val spec = PsArtifactDependencySpec.create("group:name:1.0")
    assertNotNull(spec)
    assertEquals("group", spec!!.group)
    assertEquals("name", spec.name)
    assertEquals("1.0", spec.version)
  }

  fun testCreate_groupNameAndDynamicVersion() {
    val spec = PsArtifactDependencySpec.create("group:name:1.+")
    assertNotNull(spec)
    assertEquals("group", spec!!.group)
    assertEquals("name", spec.name)
    assertEquals("1.+", spec.version)
  }

  fun testCreate_groupNameVersionAndClassifier() {
    val spec = PsArtifactDependencySpec.create("group:name:1.0:jdk")
    assertNotNull(spec)
    assertEquals("group", spec!!.group)
    assertEquals("name", spec.name)
    assertEquals("1.0", spec.version)
  }

  fun testCreate_groupNameVersionClassifierAndPackage() {
    val spec = PsArtifactDependencySpec.create("group:name:1.0:jdk15@jar")
    assertNotNull(spec)
    assertEquals("group", spec!!.group)
    assertEquals("name", spec.name)
    assertEquals("1.0", spec.version)
  }

  fun testCreate_groupNameVersionAndPackage() {
    val spec = PsArtifactDependencySpec.create("group:name:1.0@jar")
    assertNotNull(spec)
    assertEquals("group", spec!!.group)
    assertEquals("name", spec.name)
    assertEquals("1.0", spec.version)
  }

  fun testCreate_groupNameAndPackage() {
    val spec = PsArtifactDependencySpec.create("group:name@jar")
    assertNotNull(spec)
    assertEquals("group", spec!!.group)
    assertEquals("name", spec.name)
    assertNull(spec.version)
  }

  fun testCreate_nameAndPackage() {
    val spec = PsArtifactDependencySpec.create("name@jar")
    assertNotNull(spec)
    assertNull(spec!!.group)
    assertEquals("name", spec.name)
    assertNull(spec.version)
  }

  fun testCreate_complexSpecification() {
    val spec = PsArtifactDependencySpec.create("a.b.c..323.d:name2:1.0-res.+:jdk15@jar")
    assertNotNull(spec)
    assertEquals("a.b.c..323.d", spec!!.group)
    assertEquals("name2", spec.name)
    assertEquals("1.0-res.+", spec.version)
  }

  fun testCreate_artifactDependencyModel() {
    val model = mock(ArtifactDependencyModel::class.java)
    fun propertyModelFunc(value: String): ResolvedPropertyModel? {
      val propertyModel = mock(ResolvedPropertyModel::class.java)
      whenever(propertyModel.toString()).thenReturn(value)
      whenever(propertyModel.forceString()).thenReturn(value)
      return propertyModel
    }
    val groupModel = propertyModelFunc("group")
    val nameModel = propertyModelFunc("name")
    val versionModel = propertyModelFunc("version")
    whenever(model.group()).thenReturn(groupModel)
    whenever(model.name()).thenReturn(nameModel)
    whenever(model.version()).thenReturn(versionModel)
    val spec = PsArtifactDependencySpec.create(model)
    assertNotNull(spec)
    assertEquals("group", spec.group)
    assertEquals("name", spec.name)
    assertEquals("version", spec.version)
  }

  fun testCreate_mavenCoordinates() {
    val coordinates = GradleCoordinate.parseCoordinateString("group:name:version@aar")
    val spec = PsArtifactDependencySpec.create(coordinates!!)
    assertNotNull(spec)
    assertEquals("group", spec.group)
    assertEquals("name", spec.name)
    assertEquals("version", spec.version)
  }

  fun testCreate_gradleModuleVersion() {
    val version = mock(GradleModuleVersion::class.java)
    whenever(version.group).thenReturn("group")
    whenever(version.name).thenReturn("name")
    whenever(version.version).thenReturn("version")
    val spec = PsArtifactDependencySpec.create(version)
    assertNotNull(spec)
    assertEquals("group", spec.group)
    assertEquals("name", spec.name)
    assertEquals("version", spec.version)
  }

  fun testGetDisplayText_fullySpecifiedWithGroup() {
    val spec = PsArtifactDependencySpec.create("group:name:1.0")
    assertNotNull(spec)

    val uiSettings = PsUISettings()
    uiSettings.DECLARED_DEPENDENCIES_SHOW_GROUP_ID = true
    assertEquals("group:name:1.0", spec!!.getDisplayText(uiSettings))
  }

  fun testGetDisplayText_fullySpecifiedWithoutGroup() {
    val spec = PsArtifactDependencySpec.create("group:name:1.0")
    assertNotNull(spec)

    val uiSettings = PsUISettings()
    uiSettings.DECLARED_DEPENDENCIES_SHOW_GROUP_ID = false
    assertEquals("name:1.0", spec!!.getDisplayText(uiSettings))
  }

  fun testGetDisplayText_noVersionWithGroup() {
    val spec = PsArtifactDependencySpec.create("group:name")
    assertNotNull(spec)

    val uiSettings = PsUISettings()
    uiSettings.DECLARED_DEPENDENCIES_SHOW_GROUP_ID = true
    assertEquals("group:name", spec!!.getDisplayText(uiSettings))
  }

  fun testGetDisplayText_noVersionWithoutGroup() {
    val spec = PsArtifactDependencySpec.create("group:name")
    assertNotNull(spec)

    val uiSettings = PsUISettings()
    uiSettings.DECLARED_DEPENDENCIES_SHOW_GROUP_ID = false
    assertEquals("name", spec!!.getDisplayText(uiSettings))
  }

  fun testGetDisplayText_doNotShowVersion() {
    val spec = PsArtifactDependencySpec.create("group:name:1.1")
    assertNotNull(spec)

    assertEquals("group:name", spec!!.getDisplayText(true, false))
  }

  fun testGetDisplayText_doNotShowGroupAndVersion() {
    val spec = PsArtifactDependencySpec.create("group:name:1.1")
    assertNotNull(spec)

    assertEquals("name", spec!!.getDisplayText(false, false))
  }

  fun testGetDisplayText_doNotShowGroup() {
    val spec = PsArtifactDependencySpec.create("group:name:1.1")
    assertNotNull(spec)

    assertEquals("name:1.1", spec!!.getDisplayText(false, true))
  }

  fun testGetDisplayText_showAll() {
    val spec = PsArtifactDependencySpec.create("group:name:1.1")
    assertNotNull(spec)

    assertEquals("group:name:1.1", spec!!.getDisplayText(true, true))
  }

  fun testCompactNotation() {
    val spec = PsArtifactDependencySpec.create("group:name:1.0")
    assertNotNull(spec)

    assertEquals("group:name:1.0", spec!!.compactNotation())
  }

  fun testCompare() {
    fun create(text: String) = PsArtifactDependencySpec.create(text)!!
    assertTrue(create("group2:name:1.0") > create("group1:name:1.0"))
    assertTrue(create("group:name2:1.0") > create("group:name1:1.0"))
    assertTrue(create("group:name:2.0") > create("group:name:1.0"))
    assertTrue(create("group:name:1.1") > create("group:name:1.0"))
    assertTrue(create("group:name:1.0") > create("group:name:1.+"))
    assertTrue(create("group:name:1.0") > create("group:name:+"))
    assertTrue(create("group:name:1.0") < create("group:name:2.+"))

    assertFalse(create("group:name:1.0") < create("group:name:1.+"))
    assertFalse(create("group:name:1.0") < create("group:name:+"))
  }
}
