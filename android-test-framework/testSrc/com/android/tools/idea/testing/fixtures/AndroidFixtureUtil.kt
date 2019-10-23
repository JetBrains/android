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

import com.android.projectmodel.ARTIFACT_NAME_MAIN
import com.android.projectmodel.AndroidModel
import com.android.projectmodel.AndroidPathType
import com.android.projectmodel.AndroidSubmodule
import com.android.projectmodel.ArtifactDependency
import com.android.projectmodel.Config
import com.android.projectmodel.ProjectLibrary
import com.android.projectmodel.ProjectType
import com.android.projectmodel.SourceSet
import com.android.projectmodel.configTableWith
import com.android.projectmodel.emptySubmodulePath
import com.android.projectmodel.submodulePathOf
import com.android.tools.idea.util.toPathString
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.TempDirTestFixture

typealias ModelFactory = (TempDirTestFixture) -> AndroidModel

object CommonModelFactories {
  val SINGLE_APP_MODULE: ModelFactory = singleModuleProject("app", ProjectType.APP)
  val SINGLE_LIB_MODULE: ModelFactory  = singleModuleProject("lib", ProjectType.LIBRARY)

  val APP_AND_LIB: ModelFactory = { fixture ->
    val app = SINGLE_APP_MODULE(fixture).submodules.single()
    val lib = SINGLE_LIB_MODULE(fixture).submodules.single()

    val mainArtifactPath = submodulePathOf(ARTIFACT_NAME_MAIN)
    val mainArtifact = app.getArtifact(mainArtifactPath) ?: error("Main artifact missing")
    val newConfig = mainArtifact.resolved.copy(
      compileDeps = listOf(
        ArtifactDependency(
          library = ProjectLibrary(
            address = lib.name,
            projectName = lib.name,
            variant = emptySubmodulePath.simpleName
          )
        )
      )
    )

    val newApp = app.copy(
      artifacts = app.artifacts + (mainArtifactPath to mainArtifact.copy(resolved = newConfig))
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
      AndroidSubmodule(
        name = moduleName,
        type = type
      ).withVariantsGeneratedBy(configTableWith(Config(
        sources = SourceSet(
          paths = mapOf(
            AndroidPathType.JAVA to listOf(src.toPathString())
          )
        )
      )))
    )
  )
}

