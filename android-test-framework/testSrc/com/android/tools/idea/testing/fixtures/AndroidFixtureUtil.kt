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
@file:JvmName("AndroidFixtureUtil")

package com.android.tools.idea.testing.fixtures

import com.android.projectmodel.*
import com.android.tools.idea.util.toPathString
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.TempDirTestFixture

typealias ModelFactory = (TempDirTestFixture) -> AndroidModel
const val TEST_VARIANT_NAME = "debug"

object CommonModelFactories {
  val SINGLE_APP_MODULE: ModelFactory = singleModuleProject("app", ProjectType.APP)
  val SINGLE_LIB_MODULE: ModelFactory  = singleModuleProject("lib", ProjectType.LIBRARY)

  val APP_AND_LIB: ModelFactory = { fixture ->
    val app = SINGLE_APP_MODULE(fixture).projects.single()
    val lib = SINGLE_LIB_MODULE(fixture).projects.single()

    val appVariant = app.variants.single()
    val newConfig = appVariant.mainArtifact.resolved.copy(
      compileDeps = listOf(
        ArtifactDependency(
          library = ProjectLibrary(
            address = lib.name,
            projectName = lib.name,
            variant = TEST_VARIANT_NAME
          )
        )
      )
    )

    val newApp = app.copy(
      variants = listOf(
        appVariant.copy(
          mainArtifact = appVariant.mainArtifact.copy(
            resolved = newConfig
          )
        )
      )
    )

    AndroidModel(listOf(newApp, lib))
  }
}

private fun singleModuleProject(
  moduleName: String,
  type: ProjectType
): ModelFactory = { fixture: TempDirTestFixture ->
  val root = fixture.findOrCreateDir(moduleName)
  val src = VfsUtil.createDirectoryIfMissing(root, "src/main")

  AndroidModel(
    listOf(
      AndroidProject(
        name= moduleName,
        type = type,
        variants = listOf(
          Variant(
            name = TEST_VARIANT_NAME,
            mainArtifact = Artifact(
              name = ARTIFACT_NAME_MAIN,
              // TODO: how can packageName be in the model, if it changes with the manifest?
              resolved = Config(
                sources = SourceSet(
                  paths = mapOf(
                    AndroidPathType.JAVA to listOf(src.toPathString())
                  )
                )
              )
            )
          )
        )
      )
    )
  )
}

