/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.model

import com.android.builder.model.AndroidLibrary
import com.android.builder.model.JavaLibrary
import com.android.builder.model.Library
import com.android.builder.model.MavenCoordinates
import com.android.tools.idea.gradle.model.impl.BuildFolderPaths
import com.android.tools.idea.gradle.model.impl.IdeJavaLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeModuleLibraryImpl
import com.android.tools.idea.gradle.model.stubs.AndroidLibraryStub
import com.android.tools.idea.gradle.model.stubs.JavaLibraryStub
import com.android.tools.idea.gradle.model.stubs.LibraryStub
import com.android.tools.idea.gradle.model.stubs.MavenCoordinatesStub
import com.android.tools.idea.gradle.project.sync.idea.svs.ModelCache
import com.android.tools.idea.gradle.project.sync.idea.svs.ModelCacheTesting
import com.google.common.truth.Truth
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.File

/** Tests for [IdeLibraryFactory].  */
class IdeLibraryFactoryTest {
  lateinit var modelCache: ModelCacheTesting

  @Before
  fun setUp() {
    modelCache = ModelCache.createForTesting();
  }

  @Test
  fun createFromJavaLibrary() {
    // Verify JavaLibrary of module dependency returns instance of IdeModuleLibrary.
    val moduleLibrary = modelCache.libraryFrom(JavaLibraryStub())
    Truth.assertThat(moduleLibrary).isInstanceOf(IdeModuleLibraryImpl::class.java)

    // Verify JavaLibrary of jar dependency returns instance of IdeJavaLibrary.
    val javaLibrary: JavaLibrary = object : JavaLibraryStub() {
      override fun getProject(): String? {
        return null
      }
    }
    Truth.assertThat(modelCache.libraryFrom(javaLibrary)).isInstanceOf(IdeJavaLibraryImpl::class.java)
  }

  @Test
  fun createFromString() {
    Truth.assertThat(modelCache.libraryFrom("lib", ":lib@@:", "/rootDir/lib"))
      .isInstanceOf(IdeModuleLibraryImpl::class.java)
  }

  @Test
  fun computeMavenAddress() {
    val library: Library = object : LibraryStub() {
      override fun getResolvedCoordinates(): MavenCoordinates {
        return MavenCoordinatesStub("com.android.tools", "test", "2.1", "aar")
      }
    }
    Truth.assertThat(modelCache.computeAddress(library)).isEqualTo("com.android.tools:test:2.1@aar")
  }

  @Test
  fun computeMavenAddressWithModuleLibrary() {
    val library: Library = object : AndroidLibraryStub() {
      override fun getProject(): String? {
        return ":androidLib"
      }

      override fun getProjectVariant(): String? {
        return "release"
      }
    }
    Truth.assertThat(modelCache.computeAddress(library)).isEqualTo(":androidLib::release")
  }

  @Test
  fun computeMavenAddressWithModuleLibraryWithBuildId() {
    val library: Library = object : AndroidLibraryStub() {
      override fun getProject(): String? {
        return ":androidLib"
      }

      override fun getBuildId(): String? {
        return "/project/root"
      }

      override fun getProjectVariant(): String? {
        return "release"
      }
    }
    Truth.assertThat(modelCache.computeAddress(library)).isEqualTo("/project/root:androidLib::release")
  }

  @Test
  fun computeMavenAddressWithNestedModuleLibrary() {
    val library: Library = object : LibraryStub() {
      override fun getResolvedCoordinates(): MavenCoordinates {
        return MavenCoordinatesStub(
          "myGroup", ":androidLib:subModule", "undefined", "aar"
        )
      }
    }
    Truth.assertThat(modelCache.computeAddress(library))
      .isEqualTo("myGroup:androidLib.subModule:undefined@aar")
  }

  @Test
  fun checkIsLocalAarModule() {
    val localAarLibrary: AndroidLibrary = object : AndroidLibraryStub() {
      override fun getProject(): String {
        return ":aarModule"
      }

      override fun getBundle(): File {
        return File("/ProjectRoot/aarModule/aarModule.aar")
      }
    }
    val moduleLibrary: AndroidLibrary = object : AndroidLibraryStub() {
      override fun getProject(): String {
        return ":androidLib"
      }

      override fun getBundle(): File {
        return File("/ProjectRoot/androidLib/build/androidLib.aar")
      }
    }
    val externalLibrary: AndroidLibrary = object : AndroidLibraryStub() {
      override fun getProject(): String? {
        return null
      }
    }
    val buildFoldersPath = BuildFolderPaths()
    buildFoldersPath.rootBuildId = "project"
    buildFoldersPath.addBuildFolderMapping(
      "project", ":aarModule", File("/ProjectRoot/aarModule/build/")
    )
    buildFoldersPath.addBuildFolderMapping(
      "project", ":androidLib", File("/ProjectRoot/androidLib/build/")
    )
    val modelCache = ModelCache.createForTesting(buildFoldersPath)
    Assert.assertTrue(modelCache.isLocalAarModule(localAarLibrary))
    Assert.assertFalse(modelCache.isLocalAarModule(moduleLibrary))
    Assert.assertFalse(modelCache.isLocalAarModule(externalLibrary))
  }

  @Test
  fun checkIsLocalAarModuleWithCompositeBuild() {
    // simulate project structure:
    // project(root)     - aarModule
    // project(root)     - androidLib
    //      project1     - aarModule
    //      project1     - androidLib
    val localAarLibraryInRootProject: AndroidLibrary = object : AndroidLibraryStub() {
      override fun getProject(): String {
        return ":aarModule"
      }

      override fun getBundle(): File {
        return File("/Project/aarModule/aarModule.aar")
      }

      override fun getBuildId(): String? {
        return "Project"
      }
    }
    val localAarLibraryInProject1: AndroidLibrary = object : AndroidLibraryStub() {
      override fun getProject(): String {
        return ":aarModule"
      }

      override fun getBundle(): File {
        return File("/Project1/aarModule/aarModule.aar")
      }

      override fun getBuildId(): String? {
        return "Project1"
      }
    }
    val moduleLibraryInRootProject: AndroidLibrary = object : AndroidLibraryStub() {
      override fun getProject(): String {
        return ":androidLib"
      }

      override fun getBundle(): File {
        return File("/Project/androidLib/build/androidLib.aar")
      }

      override fun getBuildId(): String? {
        return "Project"
      }
    }
    val moduleLibraryInProject1: AndroidLibrary = object : AndroidLibraryStub() {
      override fun getProject(): String {
        return ":androidLib"
      }

      override fun getBundle(): File {
        return File("/Project1/androidLib/build/androidLib.aar")
      }

      override fun getBuildId(): String? {
        return "Project1"
      }
    }
    val externalLibrary: AndroidLibrary = object : AndroidLibraryStub() {
      override fun getProject(): String? {
        return null
      }
    }
    val buildFolderPaths = BuildFolderPaths()
    buildFolderPaths.rootBuildId = "Project"
    buildFolderPaths.addBuildFolderMapping(
      "Project", ":aarModule", File("/Project/aarModule/build/")
    )
    buildFolderPaths.addBuildFolderMapping(
      "Project", ":androidLib", File("/Project/androidLib/build/")
    )
    buildFolderPaths.addBuildFolderMapping(
      "Project1", ":aarModule", File("/Project1/aarModule/build/")
    )
    buildFolderPaths.addBuildFolderMapping(
      "Project1", ":androidLib", File("/Project1/androidLib/build/")
    )
    val modelCache = ModelCache.createForTesting(buildFolderPaths)
    Assert.assertTrue(modelCache.isLocalAarModule(localAarLibraryInRootProject))
    Assert.assertTrue(modelCache.isLocalAarModule(localAarLibraryInProject1))
    Assert.assertFalse(modelCache.isLocalAarModule(moduleLibraryInRootProject))
    Assert.assertFalse(modelCache.isLocalAarModule(moduleLibraryInProject1))
    Assert.assertFalse(modelCache.isLocalAarModule(externalLibrary))
  }
}
