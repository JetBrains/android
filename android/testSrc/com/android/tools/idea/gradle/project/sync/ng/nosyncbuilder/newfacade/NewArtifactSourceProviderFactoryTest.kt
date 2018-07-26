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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade

import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.essentiallyEquals
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.*
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.variant.NewArtifactSourceProviderFactory
import com.android.tools.idea.gradle.stubs.FileStructure
import com.android.tools.idea.gradle.stubs.android.BuildTypeContainerStub
import com.android.tools.idea.gradle.stubs.android.ProductFlavorContainerStub
import com.android.tools.idea.gradle.stubs.android.SourceProviderStub
import junit.framework.TestCase
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.File

class NewArtifactSourceProviderFactoryTest : TestCase() {
  @Throws(Exception::class)
  fun testNewArtifactSourceProviderFactory() {
    val mockAndroidProject = mock(OldAndroidProject::class.java)
    val fs = FileStructure("/") // does not influence any tested logic
    `when`(mockAndroidProject.buildTypes).thenReturn(listOf(BuildTypeContainerStub("buildType", fs)))
    `when`(mockAndroidProject.productFlavors).thenReturn(listOf(
      ProductFlavorContainerStub("lollipop", fs, "api"),
      ProductFlavorContainerStub("oreo", fs, "api"),
      ProductFlavorContainerStub("demo", fs, "type")
    ))
    `when`(mockAndroidProject.defaultConfig).thenReturn(ProductFlavorContainerStub("default", fs, ""))

    val mockVariant = mock(OldVariant::class.java)
    `when`(mockVariant.productFlavors).thenReturn(listOf("oreo", "demo"))
    `when`(mockVariant.buildType).thenReturn("buildType")

    val variantSourceProviderStub = SourceProviderStub(fs).apply {
      name = "variant"
      addResDirectory("variantResDir")
    }
    val multiFlavorSourceProviderStub = SourceProviderStub(fs).apply {
      name = "multiFlavor"
      addCppDirectory("multiFlavorCppDir")
    }

    val mockBaseArtifact = mock(OldBaseArtifact::class.java)
    `when`(mockBaseArtifact.variantSourceProvider).thenReturn(variantSourceProviderStub)
    `when`(mockBaseArtifact.multiFlavorSourceProvider).thenReturn(multiFlavorSourceProviderStub)
    `when`(mockBaseArtifact.classesFolder).thenReturn(File("classesFolder"))
    `when`(mockBaseArtifact.additionalClassesFolders).thenReturn(setOf<File>())
    `when`(mockBaseArtifact.javaResourcesFolder).thenReturn(File("javaResourcesFolder"))
    `when`(mockBaseArtifact.generatedSourceFolders).thenReturn(setOf<File>())

    val artifactSourceProviderFactory = NewArtifactSourceProviderFactory(mockAndroidProject, mockVariant)
    val artifactSourceProvider = artifactSourceProviderFactory.build(mockBaseArtifact)

    assertTrue(mockBaseArtifact.variantSourceProvider!! essentiallyEquals
                 artifactSourceProvider.variantSourceSet!!.toSourceProvider())
    assertTrue(mockAndroidProject.buildTypes.first().sourceProvider essentiallyEquals
                 artifactSourceProvider.buildTypeSourceSet.toSourceProvider())
    assertTrue(mockBaseArtifact.multiFlavorSourceProvider!! essentiallyEquals
                 artifactSourceProvider.multiFlavorSourceSet!!.toSourceProvider())
    assertEquals(mockVariant.productFlavors.size,
                 artifactSourceProvider.singleFlavorSourceSets.size)
    assertTrue(mockAndroidProject.defaultConfig.sourceProvider essentiallyEquals
                 artifactSourceProvider.defaultSourceSet.toSourceProvider())
  }
}
