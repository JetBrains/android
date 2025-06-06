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
package com.android.tools.idea.projectsystem

import com.android.ide.common.gradle.Dependency
import com.android.ide.common.repository.GoogleMavenArtifactId
import com.android.ide.common.repository.GradleCoordinate
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager
import com.android.tools.idea.gradle.model.IdeAndroidProject
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.gradle.GradleModuleSystem
import com.android.tools.idea.projectsystem.gradle.ProjectBuildModelHandler
import com.android.tools.idea.testing.IdeComponents
import com.google.common.truth.Truth.assertThat
import org.jetbrains.android.AndroidTestCase
import org.mockito.Mockito
import org.mockito.Mockito.times

/**
 * These unit tests use a local test maven repo "project-system-gradle/testData/repoIndex". To see
 * what dependencies are available to test with, go to that folder and look at the group-indices.
 */
class GradleModuleSystemTest : AndroidTestCase() {
  private var _gradleDependencyManager: GradleDependencyManager? = null
  private var _gradleModuleSystem: GradleModuleSystem? = null
  private var androidProject: IdeAndroidProject? = null
  private val gradleDependencyManager get() = _gradleDependencyManager!!
  private val gradleModuleSystem get() = _gradleModuleSystem!!
  private val moduleHierarchyProviderStub = object : ModuleHierarchyProvider {}

  override fun setUp() {
    super.setUp()
    _gradleDependencyManager = IdeComponents(project).mockProjectService(GradleDependencyManager::class.java)
    _gradleModuleSystem = GradleModuleSystem(myModule, ProjectBuildModelHandler(project), moduleHierarchyProviderStub)
    assertThat(gradleModuleSystem.getAndroidLibraryDependencies(DependencyScopeType.MAIN)).isEmpty()
  }

  override fun tearDown() {
    try {
      _gradleDependencyManager = null
      _gradleModuleSystem = null
      androidProject = null
    }
    finally {
      super.tearDown()
    }
  }

  fun testRegisterDependencyCoordinate() {
    val coordinate = GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getCoordinate("+")
    val dependency = GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getDependency("+")
    assertThat(gradleModuleSystem.canRegisterDependency(DependencyType.IMPLEMENTATION)).isTrue()
    gradleModuleSystem.registerDependency(coordinate, DependencyType.IMPLEMENTATION)
    Mockito.verify<GradleDependencyManager>(gradleDependencyManager, times(1))
      .addDependencies(myModule, listOf(dependency), "implementation")
  }

  fun testRegisterDebugDependencyCoordinate() {
    val coordinate = GoogleMavenArtifactId.COMPOSE_TOOLING.getCoordinate("+")
    val dependency = GoogleMavenArtifactId.COMPOSE_TOOLING.getDependency("+")
    assertThat(gradleModuleSystem.canRegisterDependency(DependencyType.DEBUG_IMPLEMENTATION)).isTrue()
    gradleModuleSystem.registerDependency(coordinate, DependencyType.DEBUG_IMPLEMENTATION)
    Mockito.verify<GradleDependencyManager>(gradleDependencyManager, times(1))
      .addDependencies(myModule, listOf(dependency), "debugImplementation")
  }

  fun testRegisterAnnotationProcessorDependencyCoordinate() {
    val coordinate = GoogleMavenArtifactId.ROOM_COMPILER.getCoordinate("+")
    val dependency = GoogleMavenArtifactId.ROOM_COMPILER.getDependency("+")
    assertThat(gradleModuleSystem.canRegisterDependency(DependencyType.ANNOTATION_PROCESSOR)).isTrue()
    gradleModuleSystem.registerDependency(coordinate, DependencyType.ANNOTATION_PROCESSOR)
    Mockito.verify<GradleDependencyManager>(gradleDependencyManager, times(1))
      .addDependencies(myModule, listOf(dependency), "annotationProcessor")
  }

  fun testRegisterDependencyDependency() {
    val dependency = GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getDependency("+")
    assertThat(gradleModuleSystem.canRegisterDependency(DependencyType.IMPLEMENTATION)).isTrue()
    gradleModuleSystem.registerDependency(dependency, DependencyType.IMPLEMENTATION)
    Mockito.verify<GradleDependencyManager>(gradleDependencyManager, times(1))
      .addDependencies(myModule, listOf(dependency), "implementation")
  }

  fun testRegisterDebugDependencyDependency() {
    val dependency = GoogleMavenArtifactId.COMPOSE_TOOLING.getDependency("+")
    assertThat(gradleModuleSystem.canRegisterDependency(DependencyType.DEBUG_IMPLEMENTATION)).isTrue()
    gradleModuleSystem.registerDependency(dependency, DependencyType.DEBUG_IMPLEMENTATION)
    Mockito.verify<GradleDependencyManager>(gradleDependencyManager, times(1))
      .addDependencies(myModule, listOf(dependency), "debugImplementation")
  }

  fun testRegisterAnnotationProcessorDependencyDependency() {
    val dependency = GoogleMavenArtifactId.ROOM_COMPILER.getDependency("+")
    assertThat(gradleModuleSystem.canRegisterDependency(DependencyType.ANNOTATION_PROCESSOR)).isTrue()
    gradleModuleSystem.registerDependency(dependency, DependencyType.ANNOTATION_PROCESSOR)
    Mockito.verify<GradleDependencyManager>(gradleDependencyManager, times(1))
      .addDependencies(myModule, listOf(dependency), "annotationProcessor")
  }

  fun testRegisterDependencyId() {
    val id = gradleModuleSystem.getRegisteredDependencyId(GoogleMavenArtifactId.CONSTRAINT_LAYOUT)
    val dependency = GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getDependency("+")
    assertThat(gradleModuleSystem.canRegisterDependency(DependencyType.IMPLEMENTATION)).isTrue()
    gradleModuleSystem.registerDependency(id, DependencyType.IMPLEMENTATION)
    Mockito.verify<GradleDependencyManager>(gradleDependencyManager, times(1))
      .addDependencies(myModule, listOf(dependency), "implementation")
  }

  fun testRegisterDebugDependencyId() {
    val id = gradleModuleSystem.getRegisteredDependencyId(GoogleMavenArtifactId.COMPOSE_TOOLING)
    val dependency = GoogleMavenArtifactId.COMPOSE_TOOLING.getDependency("+")
    assertThat(gradleModuleSystem.canRegisterDependency(DependencyType.DEBUG_IMPLEMENTATION)).isTrue()
    gradleModuleSystem.registerDependency(id, DependencyType.DEBUG_IMPLEMENTATION)
    Mockito.verify<GradleDependencyManager>(gradleDependencyManager, times(1))
      .addDependencies(myModule, listOf(dependency), "debugImplementation")
  }

  fun testRegisterAnnotationProcessorDependencyId() {
    val id = gradleModuleSystem.getRegisteredDependencyId(GoogleMavenArtifactId.ROOM_COMPILER)
    val dependency = GoogleMavenArtifactId.ROOM_COMPILER.getDependency("+")
    assertThat(gradleModuleSystem.canRegisterDependency(DependencyType.ANNOTATION_PROCESSOR)).isTrue()
    gradleModuleSystem.registerDependency(id, DependencyType.ANNOTATION_PROCESSOR)
    Mockito.verify<GradleDependencyManager>(gradleDependencyManager, times(1))
      .addDependencies(myModule, listOf(dependency), "annotationProcessor")
  }

  fun testRegisterDependencyVersionedId() {
    val dependency = GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getDependency("1.2.3")
    val id = gradleModuleSystem.getRegisteredDependencyId(dependency)
    assertThat(gradleModuleSystem.canRegisterDependency(DependencyType.IMPLEMENTATION)).isTrue()
    gradleModuleSystem.registerDependency(id, DependencyType.IMPLEMENTATION)
    Mockito.verify<GradleDependencyManager>(gradleDependencyManager, times(1))
      .addDependencies(myModule, listOf(dependency), "implementation")
  }

  fun testRegisterDebugDependencyVersionedId() {
    val dependency = GoogleMavenArtifactId.COMPOSE_TOOLING.getDependency("4.5.6")
    val id = gradleModuleSystem.getRegisteredDependencyId(dependency)
    assertThat(gradleModuleSystem.canRegisterDependency(DependencyType.DEBUG_IMPLEMENTATION)).isTrue()
    gradleModuleSystem.registerDependency(id, DependencyType.DEBUG_IMPLEMENTATION)
    Mockito.verify<GradleDependencyManager>(gradleDependencyManager, times(1))
      .addDependencies(myModule, listOf(dependency), "debugImplementation")
  }

  fun testRegisterAnnotationProcessorDependencyVersionedId() {
    val dependency = GoogleMavenArtifactId.ROOM_COMPILER.getDependency("7.8.9")
    val id = gradleModuleSystem.getRegisteredDependencyId(dependency)
    assertThat(gradleModuleSystem.canRegisterDependency(DependencyType.ANNOTATION_PROCESSOR)).isTrue()
    gradleModuleSystem.registerDependency(id, DependencyType.ANNOTATION_PROCESSOR)
    Mockito.verify<GradleDependencyManager>(gradleDependencyManager, times(1))
      .addDependencies(myModule, listOf(dependency), "annotationProcessor")
  }

  fun testNoGradleAndroidModel() {
    // The GradleAndroidModel shouldn't be created when running from an IdeaTestCase.
    assertThat(GradleAndroidModel.get(myModule)).isNull()
    assertThat(gradleModuleSystem.getResolvedDependency(GoogleMavenArtifactId.SUPPORT_APPCOMPAT_V7.getCoordinate("+"))).isNull()
  }

  fun testGetPackageName_noOverrides() {
    val packageName = (myModule.getModuleSystem() as DefaultModuleSystem).getPackageName()
    assertThat(packageName).isEqualTo("p1.p2")
  }
}
