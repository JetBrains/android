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
package com.android.tools.idea.testing.fixtures

import com.android.projectmodel.ARTIFACT_NAME_MAIN
import com.android.projectmodel.AndroidModel
import com.android.projectmodel.AndroidPathType
import com.android.projectmodel.AndroidSubmodule
import com.android.projectmodel.Artifact
import com.android.projectmodel.ProjectLibrary
import com.android.projectmodel.submodulePathOf
import com.android.projectmodel.visitEach
import com.android.tools.idea.util.toVirtualFile
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.idea.IdeaTestApplication
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleTypeId
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modifyModules
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import org.jetbrains.android.AndroidTestCase

object ModelToTestProjectConverter {

  enum class Mode {
    REUSE_PROJECT,
    TEMP_FS,
    DISK
  }

  fun convert(
    modelFactory: (TempDirTestFixture) -> AndroidModel,
    mode: Mode,
    projectName: String
  ): JavaCodeInsightTestFixture {
    val ideaFixtureFactory = IdeaTestFixtureFactory.getFixtureFactory()
    ideaFixtureFactory.registerFixtureBuilder(
      AndroidTestCase.AndroidModuleFixtureBuilder::class.java,
      AndroidTestCase.AndroidModuleFixtureBuilderImpl::class.java
    )

    // TempDirTestFixture decides where to store project files.
    val tempDirFixture: TempDirTestFixture = when (mode) {
      Mode.REUSE_PROJECT -> LightTempDirTestFixtureImpl(true)
      Mode.TEMP_FS -> LightTempDirTestFixtureImpl()
      Mode.DISK -> TempDirTestFixtureImpl()
    }

    IdeaTestApplication.getInstance() // Initialize the application, so that tempDirFixture can run.
    val model = runWriteAction {
      modelFactory(tempDirFixture)
    }

    // IdeaProjectTestFixture manages the project etc.
    val projectFixture: IdeaProjectTestFixture = when (mode) {
      Mode.REUSE_PROJECT -> ideaFixtureFactory.createLightFixtureBuilder(ModelBasedProjectDescriptor(model)).fixture
      Mode.TEMP_FS, Mode.DISK -> ideaFixtureFactory.createFixtureBuilder(projectName).fixture
    }

    // JavaCodeInsightTestFixture manages an editor ("code insight") and provides a JavaFacade.
    val codeInsightFixture: JavaCodeInsightTestFixture =
      JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectFixture, tempDirFixture)
    codeInsightFixture.testDataPath = AndroidTestCase.getTestDataPath()
    codeInsightFixture.setUp()

    if (mode != Mode.REUSE_PROJECT) {
      runWriteAction { tempDirFixture.findOrCreateDir("_imls") }
      setUpProjectFromModel(model, projectFixture.project) { p -> pickImlPath(p.name, mode, tempDirFixture) }
    }

    return codeInsightFixture
  }

  private fun setUpProjectFromModel(
    model: AndroidModel,
    project: Project,
    pickPath: (AndroidSubmodule) -> String
  ) {
    val modules = mutableMapOf<String, Module>()
    for (modelModule in model.submodules) {
      val ideaModule = project.modifyModules {
        val module = newModule(pickPath(modelModule), ModuleTypeId.JAVA_MODULE)
        val rootModel = ModuleRootManager.getInstance(module).modifiableModel

        // TODO: Handle generated sources.
        // TODO: Handle test sources.
        for (pathString in modelModule.mainArtifact.resolved.sources[AndroidPathType.JAVA]) {
          val vf = pathString.toVirtualFile(refresh = true) ?: error("Failed to find $pathString.")
          val contentEntry = rootModel.addContentEntry(vf)
          contentEntry.addSourceFolder(vf, false)
        }
        runWriteAction { rootModel.commit() }
        module
      }

      AndroidTestCase.addAndroidFacet(ideaModule)
      // TODO: set module type (app/lib) in the facet.
      // TODO: store the model in the facet

      modules[ideaModule.name] = ideaModule
    }

    // TODO: create libraries

    // Now set up dependencies:
    for (ideaModule in modules.values) {
      val modelModule = model.getSubmodule(ideaModule.name)!!
      modelModule.mainArtifact.compileDeps.visitEach().forEach { dependency ->
        val library = dependency.library
        when (library) {
          is ProjectLibrary -> {
            val dependencyModule = modules[library.projectName] ?: error("Unknown project ${library.projectName}")
            ModuleRootModificationUtil.addDependency(ideaModule, dependencyModule)
          }
          else -> TODO("support other dependencies")
        }
      }
    }
  }

  private val AndroidSubmodule.mainArtifact: Artifact
    get() = getArtifact(submodulePathOf(ARTIFACT_NAME_MAIN)) ?: error("Only single-variant modules are supported")

  private fun pickImlPath(moduleName: String, mode: Mode, tempDirFixture: TempDirTestFixture): String = when (mode) {
    Mode.DISK -> "${tempDirFixture.tempDirPath}/_imls/${moduleName}.${ModuleFileType.DEFAULT_EXTENSION}"
    Mode.TEMP_FS -> TODO() // LightProjectDescriptor puts them on disk using FileUtil.getTempDirectory
    Mode.REUSE_PROJECT -> error("Light test cases manage modules themselves.")
  }

  /**
   * A [LightProjectDescriptor] that equal only to another descriptor for an equal [AndroidModel].
   */
  private data class ModelBasedProjectDescriptor(val model: AndroidModel) : LightProjectDescriptor() {
    init {
      require(model.submodules.size == 1) { "${Mode.REUSE_PROJECT} is only available for single-module projects." }
    }

    override fun getSdk(): Sdk? {
      TODO("Set up the SDK like AndroidFacetProjectDescriptor.")
    }

    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      TODO("Add the facet like AndroidFacetProjectDescriptor.")
      TODO("Register the model with the facet/module.")
    }
  }
}
