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
package com.android.tools.idea.gradle.dsl.model

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.dsl.TestFileName
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.BOOLEAN_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.INTEGER_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.BOOLEAN
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.INTEGER
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.INTERPOLATED
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.STRING
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.dsl.parser.files.GradleBuildFile
import com.android.tools.idea.gradle.dsl.parser.files.GradlePropertiesFile
import com.android.tools.idea.gradle.dsl.parser.files.GradleSettingsFile
import com.android.tools.idea.gradle.dsl.parser.files.GradleVersionCatalogFile
import com.android.tools.idea.gradle.dsl.parser.semantics.AndroidGradlePluginVersion
import com.intellij.openapi.vfs.VirtualFile
import junit.framework.Assert
import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.MatcherAssert.assertThat
import org.jetbrains.annotations.SystemDependent
import org.junit.Test
import java.io.File
import java.io.IOException

class ProjectBuildModelTest : GradleFileModelTestCase() {
  @Test
  fun testAppliedFilesShared() {
    val b = writeToNewProjectFile("b", TestFile.APPLIED_FILES_SHARED_APPLIED)
    writeToBuildFile(TestFile.APPLIED_FILES_SHARED)
    writeToSubModuleBuildFile(TestFile.APPLIED_FILES_SHARED_SUB)
    writeToSettingsFile(subModuleSettingsText)

    val projectModel = projectBuildModel
    val parentBuildModel = projectModel.projectBuildModel!!
    val childBuildModel = projectModel.getModuleBuildModel(mySubModule)!!

    run {
      val parentProperty = parentBuildModel.ext().findProperty("property")
      verifyPropertyModel(parentProperty.resolve(), STRING_TYPE, "hello", INTERPOLATED, REGULAR, 1, "property")
      val childProperty = childBuildModel.ext().findProperty("childProperty")
      verifyPropertyModel(childProperty.resolve(), STRING_TYPE, "hello", STRING, REGULAR, 1, "childProperty")
      val appliedProperty = childProperty.dependencies[0]
      verifyPropertyModel(appliedProperty.resolve(), STRING_TYPE, "hello", STRING, REGULAR, 0, "greeting")

      // Alter the value of the applied file variable
      appliedProperty.setValue("goodbye")
      childProperty.rename("dodgy")

      verifyPropertyModel(appliedProperty.resolve(), STRING_TYPE, "goodbye", STRING, REGULAR, 0, "greeting")
      verifyPropertyModel(parentProperty.resolve(), STRING_TYPE, "goodbye", INTERPOLATED, REGULAR, 1, "property")
      verifyPropertyModel(childProperty.resolve(), STRING_TYPE, "goodbye", STRING, REGULAR, 1, "dodgy")
    }

    assertFalse(parentBuildModel.isModified)
    assertTrue(childBuildModel.isModified)

    applyChangesAndReparse(projectModel)
    verifyFileContents(myBuildFile, TestFile.APPLIED_FILES_SHARED)
    verifyFileContents(mySubModuleBuildFile, TestFile.APPLIED_FILES_SHARED_SUB_EXPECTED)
    verifyFileContents(myProjectBasePath.findChild(b)!!, TestFile.APPLIED_FILES_SHARED_APPLIED_EXPECTED)

    assertFalse(parentBuildModel.isModified)
    assertFalse(childBuildModel.isModified)

    run {
      val parentProperty = parentBuildModel.ext().findProperty("property")
      val childProperty = childBuildModel.ext().findProperty("dodgy")
      val appliedProperty = childProperty.dependencies[0]
      verifyPropertyModel(appliedProperty.resolve(), STRING_TYPE, "goodbye", STRING, REGULAR, 0, "greeting")
      verifyPropertyModel(parentProperty.resolve(), STRING_TYPE, "goodbye", INTERPOLATED, REGULAR, 1, "property")
      verifyPropertyModel(childProperty.resolve(), STRING_TYPE, "goodbye", STRING, REGULAR, 1, "dodgy")
    }
  }

  @Test
  fun testApplyNoRootBuildFile() {
    writeToSubModuleBuildFile(TestFile.APPLY_NO_ROOT_BUILD_FILE)
    writeToSettingsFile(subModuleSettingsText)

    // Delete the main build file
    runWriteAction<Unit, IOException> { myBuildFile.delete(this) }

    val pbm = projectBuildModel
    assertNull(pbm.projectBuildModel)

    val buildModel = pbm.getModuleBuildModel(mySubModule)!!
    verifyPropertyModel(buildModel.ext().findProperty("prop"), INTEGER_TYPE, 1, INTEGER, REGULAR, 0)

    // Make a change
    buildModel.ext().findProperty("prop").setValue(5)

    // Make sure that applying the changes still affects the submodule build file
    applyChangesAndReparse(pbm)

    verifyFileContents(mySubModuleBuildFile, TestFile.APPLY_NO_ROOT_BUILD_FILE_EXPECTED)
  }

  @Test
  fun testMultipleModelsPersistChanges() {
    writeToBuildFile(TestFile.MULTIPLE_MODELS_PERSIST_CHANGES)
    writeToSubModuleBuildFile(TestFile.MULTIPLE_MODELS_PERSIST_CHANGES_SUB)

    val projectModel = projectBuildModel
    val childModelOne = projectModel.getModuleBuildModel(mySubModule)!!
    val childModelTwo = projectModel.getModuleBuildModel(mySubModule)!!
    val parentModelOne = projectModel.projectBuildModel!!
    val parentModelTwo = projectModel.projectBuildModel!!

    // Edit the properties in one of the models.
    run {
      val parentPropertyModel = parentModelTwo.ext().findProperty("prop")
      verifyPropertyModel(parentPropertyModel.resolve(), STRING_TYPE, "Hello i am true!", INTERPOLATED, REGULAR, 1)
      val childPropertyModel = childModelOne.ext().findProperty("prop1")
      verifyPropertyModel(childPropertyModel.resolve(), STRING_TYPE, "boo", STRING, REGULAR, 0)

      // Change values on each file.
      parentPropertyModel.dependencies[0].setValue(false)
      childPropertyModel.setValue("ood")

      // Check that the properties have been updated in the original models
      verifyPropertyModel(parentPropertyModel.resolve(), STRING_TYPE, "Hello i am false!", INTERPOLATED, REGULAR, 1)
      verifyPropertyModel(childPropertyModel.resolve(), STRING_TYPE, "ood", STRING, REGULAR, 0)
      // Check that the properties have been updated in the other models
      val otherParentPropertyModel = parentModelOne.ext().findProperty("prop")
      val otherChildPropertyModel = childModelTwo.ext().findProperty("prop1")
      verifyPropertyModel(otherParentPropertyModel.resolve(), STRING_TYPE, "Hello i am false!", INTERPOLATED, REGULAR, 1)
      verifyPropertyModel(otherChildPropertyModel.resolve(), STRING_TYPE, "ood", STRING, REGULAR, 0)
    }

    applyChangesAndReparse(projectModel)
    verifyFileContents(myBuildFile, TestFile.MULTIPLE_MODELS_PERSIST_CHANGES_EXPECTED)
    verifyFileContents(mySubModuleBuildFile, TestFile.MULTIPLE_MODELS_PERSIST_CHANGES_SUB_EXPECTED)

    run {
      val parentPropertyModel = parentModelTwo.ext().findProperty("prop")
      val childPropertyModel = childModelOne.ext().findProperty("prop1")
      // Check that the properties have been updated in the original models
      verifyPropertyModel(parentPropertyModel.resolve(), STRING_TYPE, "Hello i am false!", INTERPOLATED, REGULAR, 1)
      verifyPropertyModel(childPropertyModel.resolve(), STRING_TYPE, "ood", STRING, REGULAR, 0)
      // Check that the properties have been updated in the other models
      val otherParentPropertyModel = parentModelOne.ext().findProperty("prop")
      val otherChildPropertyModel = childModelTwo.ext().findProperty("prop1")
      verifyPropertyModel(otherParentPropertyModel.resolve(), STRING_TYPE, "Hello i am false!", INTERPOLATED, REGULAR, 1)
      verifyPropertyModel(otherChildPropertyModel.resolve(), STRING_TYPE, "ood", STRING, REGULAR, 0)
    }
  }

  @Test
  fun testApplyResolvesCorrectFile() {
    // The sub-module applies a sub-module Gradle file which in turn applies a Gradle file from the root project directory.
    writeToBuildFile(TestFile.RESOLVES_CORRECT_FILE)
    writeToNewProjectFile("applied", TestFile.RESOLVES_CORRECT_FILE_APPLIED)
    writeToSubModuleBuildFile(TestFile.RESOLVES_CORRECT_FILE_SUB)
    writeToNewSubModuleFile("applied", TestFile.RESOLVES_CORRECT_FILE_APPLIED_SUB)
    writeToSettingsFile(subModuleSettingsText)

    // This should correctly resolve the variable
    val projectModel = projectBuildModel
    val buildModel = projectModel.getModuleBuildModel(mySubModule)!!

    val prop = buildModel.ext().findProperty("prop")
    verifyPropertyModel(prop, STRING_TYPE, "value", STRING, REGULAR, 0)
  }

  @Test
  fun testSettingsFileUpdatesCorrectly() {
    writeToBuildFile(TestFile.SETTINGS_FILE_UPDATES_CORRECTLY)
    writeToSubModuleBuildFile(TestFile.SETTINGS_FILE_UPDATES_CORRECTLY_SUB)
    writeToSettingsFile(subModuleSettingsText)
    val newModule = writeToNewSubModule("lib", TestFile.SETTINGS_FILE_UPDATES_CORRECTLY_OTHER_SUB, "")

    val projectModel = projectBuildModel
    val parentBuildModel = projectModel.projectBuildModel!!
    val childBuildModel = projectModel.getModuleBuildModel(File(mySubModule.moduleFilePath).parentFile)!!
    val otherChildBuildModel = projectModel.getModuleBuildModel(newModule)!!
    val settingsModel = projectModel.projectSettingsModel!!

    run {
      // Check the child build models are correct.
      val childPropertyModel = childBuildModel.ext().findProperty("moduleProp")
      verifyPropertyModel(childPropertyModel, STRING_TYPE, "one", STRING, REGULAR, 0, "moduleProp")
      val otherChildPropertyModel = otherChildBuildModel.ext().findProperty("otherModuleProp")
      verifyPropertyModel(otherChildPropertyModel, STRING_TYPE, "two", STRING, REGULAR, 0, "otherModuleProp")
      // Change the module paths are correct.
      val paths = settingsModel.modulePaths()
      assertThat(paths, hasItems(":", ":${SUB_MODULE_NAME}"))
      val parentBuildModelTwo = settingsModel.moduleModel(":")!!
      // Check that this model has the same view as one we obtained from the project model.
      val propertyModel = parentBuildModelTwo.ext().findProperty("parentProp")
      verifyPropertyModel(propertyModel.resolve(), STRING_TYPE, "zero", STRING, REGULAR, 0, "parentProp")
      val oldPropertyModel = parentBuildModel.ext().findProperty("parentProp")
      verifyPropertyModel(oldPropertyModel.resolve(), STRING_TYPE, "zero", STRING, REGULAR, 0, "parentProp")
      propertyModel.setValue(true)
      verifyPropertyModel(propertyModel.resolve(), BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 0, "parentProp")
      verifyPropertyModel(oldPropertyModel.resolve(), BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 0, "parentProp")

      // Add the new path to the settings model.
      settingsModel.addModulePath(":lib")
      val newPaths = settingsModel.modulePaths()
      assertThat(newPaths, hasItems(":", ":${SUB_MODULE_NAME}", ":lib"))
    }

    applyChangesAndReparse(projectModel)
    verifyFileContents(myBuildFile, TestFile.SETTINGS_FILE_UPDATES_CORRECTLY_EXPECTED)
    verifyFileContents(mySettingsFile, TestFile.SETTINGS_FILE_UPDATES_CORRECTLY_SETTINGS_EXPECTED)

    run {
      val paths = settingsModel.modulePaths()
      assertThat(paths, hasItems(":", ":${SUB_MODULE_NAME}", ":lib"))
    }
  }

  @Test
  fun testProjectModelSavesFiles() {
    writeToSubModuleBuildFile(TestFile.PROJECT_MODELS_SAVES_FILES_SUB)
    writeToBuildFile(TestFile.PROJECT_MODELS_SAVES_FILES)
    writeToSettingsFile(subModuleSettingsText)
    var pbm = projectBuildModel
    var buildModel = pbm.getModuleBuildModel(mySubModule)
    var optionsModel = buildModel!!.android().defaultConfig().externalNativeBuild().cmake()
    optionsModel.arguments().addListValue()!!.setValue("-DCMAKE_MAKE_PROGRAM=////")
    verifyListProperty(optionsModel.arguments(), listOf("-DCMAKE_MAKE_PROGRAM=////"))

    applyChangesAndReparse(pbm)

    pbm = projectBuildModel
    buildModel = pbm.getModuleBuildModel(mySubModule)
    optionsModel = buildModel!!.android().defaultConfig().externalNativeBuild().cmake()
    verifyListProperty(optionsModel.arguments(), listOf("-DCMAKE_MAKE_PROGRAM=////"))

    verifyFileContents(mySubModuleBuildFile, TestFile.PROJECT_MODELS_SAVES_FILES_EXPECTED)
  }

  @Test
  fun testGetModelFromVirtualFile() {
    writeToBuildFile(TestFile.GET_MODEL_FROM_VIRTUAL_FILE)

    val pbm = projectBuildModel
    val buildModel = pbm.getModuleBuildModel(myBuildFile)
    assertNotNull(buildModel)
    verifyPropertyModel(buildModel.android().compileSdkVersion(), INTEGER_TYPE, 28, INTEGER, REGULAR, 0)
  }

  @Test
  fun testEnsureParsingAppliedFileInSubmoduleFolder() {
    writeToSubModuleBuildFile(TestFile.ENSURE_PARSING_APPLIED_FILE_IN_SUBMODULE_FOLDER_SUB)
    writeToBuildFile(TestFile.ENSURE_PARSING_APPLIED_FILE_IN_SUBMODULE_FOLDER)
    writeToSettingsFile(subModuleSettingsText)
    writeToNewSubModuleFile("a", TestFile.ENSURE_PARSING_APPLIED_FILE_IN_SUBMODULE_FOLDER_APPLIED)

    val pbm = projectBuildModel
    val buildModel = pbm.getModuleBuildModel(myModule)

    val pluginModel = buildModel!!.buildscript().dependencies().artifacts()[0].completeModel()
    assertEquals("com.android.tools.build:gradle:1.2.3", pluginModel.forceString())
  }

  @Test
  fun testProjectModelGetFile() {
    // We reuse a build file here since we just need any file for this test.
    writeToSubModuleBuildFile(TestFile.ENSURE_PARSING_APPLIED_FILE_IN_SUBMODULE_FOLDER_SUB)
    writeToBuildFile(TestFile.ENSURE_PARSING_APPLIED_FILE_IN_SUBMODULE_FOLDER)
    writeToSettingsFile(subModuleSettingsText)
    writeToNewSubModuleFile("a", TestFile.ENSURE_PARSING_APPLIED_FILE_IN_SUBMODULE_FOLDER_APPLIED)

    val pbm = projectBuildModel
    val mainBuildModel = pbm.getModuleBuildModel(myModule)!!
    val subBuildModel = pbm.getModuleBuildModel(mySubModule)!!
    val settingModel = pbm.projectSettingsModel!!

    val mainPsiFile = mainBuildModel.psiFile!!
    val subPsiFile = subBuildModel.psiFile!!
    val settingFile = settingModel.psiFile!!
    assertEquals(mainPsiFile.virtualFile, mainBuildModel.virtualFile)
    assertEquals(subPsiFile.virtualFile, subBuildModel.virtualFile)
    assertEquals(settingFile.virtualFile, settingModel.virtualFile)
  }

  @Test
  fun testReparseThenChange() {
    writeToBuildFile("")
    val pbm = projectBuildModel
    val gbm = pbm.projectBuildModel!!
    gbm.ext().findProperty("foo").setValue("bar")

    applyChanges(pbm)
    pbm.reparse()
    verifyFileContents(myBuildFile, TestFile.REPARSE_THEN_CHANGE_EXPECTED_ONE)

    verifyPropertyModel("foo1", gbm.ext().findProperty("foo"), "bar")

    val gbm2 = pbm.projectBuildModel!!
    gbm2.ext().findProperty("foo").setValue("baz")

    applyChanges(pbm)
    pbm.reparse()
    verifyFileContents(myBuildFile, TestFile.REPARSE_THEN_CHANGE_EXPECTED_TWO)
    verifyPropertyModel("foo1 again", gbm.ext().findProperty("foo"), "baz")
    verifyPropertyModel("foo2", gbm2.ext().findProperty("foo"), "baz")
  }

  @Test
  fun testBuildSrcModel() {
    writeToBuildFile("")
    writeToBuildSrcBuildFile(TestFile.BUILD_SRC_ANDROID_GRADLE_PLUGIN_DEPENDENCY)
    val pbm = projectBuildModel
    val gbm = pbm.getModuleBuildModel(myBuildSrcBuildFile)

    val dependencies = gbm.dependencies().artifacts()
    assertSize(1, dependencies)
    val dependency = dependencies[0]
    assertEquals("com.android.tools.build", dependency.group().toString())
    assertEquals("gradle", dependency.name().toString())
    assertEquals("4.1.0-rc01", dependency.version().toString())
    dependency.version().setValue("4.1.0")

    applyChanges(pbm)
    verifyFileContents(myBuildSrcBuildFile, TestFile.BUILD_SRC_ANDROID_GRADLE_PLUGIN_DEPENDENCY_EXPECTED)
  }

  @Test
  fun testGetAllIncludedBuildModels() {
    // We reuse build files here since we just need any sufficiently rich project for this test.
    writeToSubModuleBuildFile(TestFile.ENSURE_PARSING_APPLIED_FILE_IN_SUBMODULE_FOLDER_SUB)
    writeToBuildFile(TestFile.ENSURE_PARSING_APPLIED_FILE_IN_SUBMODULE_FOLDER)
    writeToSettingsFile(subModuleSettingsText)
    writeToNewSubModuleFile("a", TestFile.ENSURE_PARSING_APPLIED_FILE_IN_SUBMODULE_FOLDER_APPLIED)

    val pbm = projectBuildModel
    val args = mutableListOf<Pair<Int, Int?>>()
    pbm.getAllIncludedBuildModels { n, total -> args.add(n to total) }
    assertEquals(listOf(1 to null, 2 to null, 3 to 4, 4 to 4), args)
  }

  @Test
  fun testGetContext() {
    val pbm = projectBuildModel
    val settingsModel = pbm.projectSettingsModel!!
    val buildModel = pbm.projectBuildModel!!

    assertEquals(pbm.context, settingsModel.context)
    assertEquals(pbm.context, buildModel.context)
    buildModel.involvedFiles.forEach {
      assertEquals(pbm.context, it.context)
    }
  }

  @Test
  fun testContextOrdering() {
    writeToBuildFile("")
    writeToSubModuleBuildFile("")
    writeToNewSubModule("a", "", "")
    writeToNewSubModule("b", "", "")
    writeToVersionCatalogFile("")
    writeToSettingsFile(subModuleSettingsText + getSubModuleSettingsText("a") + getSubModuleSettingsText("b"))

    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)

    try {
      val pbm = projectBuildModel
      pbm.getAllIncludedBuildModels()
      val context = pbm.context
      val allRequestedFiles = context.allRequestedFiles
      assertSize(11, allRequestedFiles)
      assertInstanceOf(allRequestedFiles[0], GradleSettingsFile::class.java)
      assertInstanceOf(allRequestedFiles[1], GradlePropertiesFile::class.java)
      assertInstanceOf(allRequestedFiles[2], GradleVersionCatalogFile::class.java)
      assertInstanceOf(allRequestedFiles[3], GradleBuildFile::class.java)
      assertEquals(":", (allRequestedFiles[3] as GradleBuildFile).name)
      // buildSrc
      assertInstanceOf(allRequestedFiles[4], GradleBuildFile::class.java)
      // TODO(b/239564531)
      //assertEquals(":buildSrc", (allRequestedFiles[4] as GradleBuildFile).name)
      // gradleModelTest
      assertInstanceOf(allRequestedFiles[5], GradlePropertiesFile::class.java)
      assertInstanceOf(allRequestedFiles[6], GradleBuildFile::class.java)
      // TODO(b/239564531)
      //assertEquals(":gradleModelTest", (allRequestedFiles[5] as GradleBuildFile).name)
      // a
      assertInstanceOf(allRequestedFiles[7], GradlePropertiesFile::class.java)
      assertInstanceOf(allRequestedFiles[8], GradleBuildFile::class.java)
      // TODO(b/239564531)
      //assertEquals(":a", (allRequestedFiles[5] as GradleBuildFile).name)
      // b
      assertInstanceOf(allRequestedFiles[9], GradlePropertiesFile::class.java)
      assertInstanceOf(allRequestedFiles[10], GradleBuildFile::class.java)
      // TODO(b/239564531)
      //assertEquals(":b", (allRequestedFiles[5] as GradleBuildFile).name)
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testContextAgpVersion() {
    writeToBuildFile(TestFile.CONTEXT_AGP_VERSION)
    writeToSettingsFile("")

    val pbm = projectBuildModel
    val settingsModel = pbm.projectSettingsModel!!
    val buildModel = pbm.projectBuildModel!!

    // The test here is not only that the initial version for agpVersion is null, but also that that null
    // is unaffected by the presence of an AGP declaration in a build file.  Any version-dependent behaviour
    // needs to be explicitly requested by client code, by setting a non-null agpVersion in the context.
    assertEquals(null, pbm.context.agpVersion)
    assertEquals(null, buildModel.context.agpVersion)
    assertEquals(null, settingsModel.context.agpVersion)
    buildModel.involvedFiles.forEach {
      assertEquals(null, it.context.agpVersion)
    }
  }

  @Test
  fun testContextAgpVersionSetExplicitly() {
    writeToBuildFile(TestFile.CONTEXT_AGP_VERSION)
    writeToSettingsFile("")

    val pbm = projectBuildModel
    val settingsModel = pbm.projectSettingsModel!!
    val buildModel = pbm.projectBuildModel!!

    val version = AndroidGradlePluginVersion.parse("3.5.0")
    pbm.context.agpVersion = version
    assertEquals(version, pbm.context.agpVersion)
    assertEquals(version, buildModel.context.agpVersion)
    assertEquals(version, settingsModel.context.agpVersion)
    buildModel.involvedFiles.forEach {
      assertEquals(version, it.context.agpVersion)
    }
  }

  @Test
  fun testGetGradlePropertiesModel() {
    writeToBuildFile("")
    writeToSettingsFile("")
    writeToPropertiesFile("abc.foo=bar")
    val pbm = projectBuildModel
    val propertiesModel = pbm.projectBuildModel?.propertiesModel!!
    assertSize(1, propertiesModel.declaredProperties)
    assertEquals("abc.foo", propertiesModel.declaredProperties[0].name)
    assertEquals("abc\\.foo", propertiesModel.declaredProperties[0].fullyQualifiedName)
    assertEquals("bar", propertiesModel.declaredProperties[0].getValue(STRING_TYPE))
  }

  @Test
  fun testNoVersionCatalogResolutionIfSettingIsOff() {
    GradleDslModelExperimentalSettings.getInstance().isVersionCatalogEnabled = false
    try {
      writeToBuildFile(TestFile.VERSION_CATALOG_BUILD_FILE)
      writeToVersionCatalogFile(TestFile.VERSION_CATALOG_COMPACT_NOTATION)

      val pbm = projectBuildModel
      val buildModel = pbm.projectBuildModel!!
      val dependencies = buildModel.dependencies()
      val artifacts = dependencies.artifacts()
      assertSize(0, artifacts)
    }
    finally {
      GradleDslModelExperimentalSettings.getInstance().isVersionCatalogEnabled = true
    }
  }

  @Test
  fun testVersionCatalogCompactNotationVariableResolution() {
    writeToBuildFile(TestFile.VERSION_CATALOG_BUILD_FILE)
    writeToVersionCatalogFile(TestFile.VERSION_CATALOG_COMPACT_NOTATION)

    val pbm = projectBuildModel
    val buildModel = pbm.projectBuildModel!!
    val dependencies = buildModel.dependencies()
    val artifacts = dependencies.artifacts()
    assertSize(1, artifacts)
    assertEquals("com.example:example:1.2.3", artifacts[0].compactNotation())
  }

  @Test
  fun testVersionCatalogGroupCompactNotationVariableResolution() {
    writeToBuildFile(TestFile.VERSION_CATALOG_ALIAS_MAPPING_BUILD_FILE)
    writeToVersionCatalogFile(TestFile.VERSION_CATALOG_GROUP_COMPACT_NOTATION)

    val pbm = projectBuildModel
    val buildModel = pbm.projectBuildModel!!
    val dependencies = buildModel.dependencies()
    val artifacts = dependencies.artifacts()
    assertSize(1, artifacts)
    assertEquals("com.example:example:1.2.3", artifacts[0].compactNotation())
  }

  @Test
  fun testVersionCatalogMapNotationVariableResolution() {
    writeToBuildFile(TestFile.VERSION_CATALOG_BUILD_FILE)
    writeToVersionCatalogFile(TestFile.VERSION_CATALOG_MAP_NOTATION)

    val pbm = projectBuildModel
    val buildModel = pbm.projectBuildModel!!
    val dependencies = buildModel.dependencies()
    val artifacts = dependencies.artifacts()
    assertSize(1, artifacts)
    assertEquals("com.example:example:1.2.3", artifacts[0].compactNotation())
  }

  @Test
  fun testVersionCatalogModuleNotationVariableResolution() {
    writeToBuildFile(TestFile.VERSION_CATALOG_BUILD_FILE)
    writeToVersionCatalogFile(TestFile.VERSION_CATALOG_MODULE_NOTATION)

    val pbm = projectBuildModel
    val buildModel = pbm.projectBuildModel!!
    val dependencies = buildModel.dependencies()
    val artifacts = dependencies.artifacts()
    assertSize(1, artifacts)
    assertEquals("com.example:example:1.2.3", artifacts[0].compactNotation())
  }

  @Test
  fun testVersionCatalogMapVersionRefNotationVariableResolution() {
    writeToBuildFile(TestFile.VERSION_CATALOG_BUILD_FILE)
    writeToVersionCatalogFile(TestFile.VERSION_CATALOG_MAP_VERSION_REF_NOTATION)

    val pbm = projectBuildModel
    val buildModel = pbm.projectBuildModel!!
    val dependencies = buildModel.dependencies()
    val artifacts = dependencies.artifacts()
    assertSize(1, artifacts)
    assertEquals("com.example:example:1.2.3", artifacts[0].compactNotation())
  }

  @Test
  fun testVersionCatalogModuleVersionRefNotationVariableResolution() {
    writeToBuildFile(TestFile.VERSION_CATALOG_BUILD_FILE)
    writeToVersionCatalogFile(TestFile.VERSION_CATALOG_MODULE_VERSION_REF_NOTATION)

    val pbm = projectBuildModel
    val buildModel = pbm.projectBuildModel!!
    val dependencies = buildModel.dependencies()
    val artifacts = dependencies.artifacts()
    assertSize(1, artifacts)
    assertEquals("com.example:example:1.2.3", artifacts[0].compactNotation())
  }

  @Test
  fun testVersionCatalogPluginsDsl() {
    writeToBuildFile(TestFile.VERSION_CATALOG_PLUGINS_DSL_BUILD_FILE)
    writeToVersionCatalogFile(TestFile.VERSION_CATALOG_PLUGINS_NOTATION)

    val pbm = projectBuildModel
    val buildModel = pbm.projectBuildModel!!
    val appliedPlugins = buildModel.appliedPlugins()
    assertSize(0, appliedPlugins)
    val plugins = buildModel.plugins()
    assertSize(3, plugins)
    assertEquals("com.android.application", plugins[0].name().toString())
    assertEquals("7.1.0", plugins[0].version().toString())

    assertEquals("com.android.library", plugins[1].name().toString())
    assertEquals("7.1.0", plugins[1].version().toString())

    assertEquals("com.android.dynamic-feature", plugins[2].name().toString())
    assertEquals("7.1.0", plugins[2].version().toString())
  }

  @Test
  fun testVersionCatalogPluginsDslSetVersions() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {
      writeToBuildFile(TestFile.VERSION_CATALOG_PLUGINS_DSL_BUILD_FILE)
      writeToVersionCatalogFile(TestFile.VERSION_CATALOG_PLUGINS_NOTATION)

      val pbm = projectBuildModel
      val buildModel = pbm.projectBuildModel!!
      val plugins = buildModel.plugins()
      // This way of setting versions should not be seen "in the wild", but exposed an issue in the PluginAliasTransform
      plugins[0].version().setValue("7.1.1")
      plugins[1].version().setValue("7.1.2")
      plugins[2].version().setValue("7.1.3")
      // We do not check file contents here because the end result of this is not clear.  For a clearer version, see
      // testVersionCatalogPluginsDslSetResultModelVersions() below.
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testVersionCatalogPluginsDslSetResultModelVersions() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {
      writeToBuildFile(TestFile.VERSION_CATALOG_PLUGINS_DSL_BUILD_FILE)
      writeToVersionCatalogFile(TestFile.VERSION_CATALOG_PLUGINS_NOTATION)

      val pbm = projectBuildModel
      val buildModel = pbm.projectBuildModel!!
      val plugins = buildModel.plugins()
      plugins[0].version().resultModel.setValue("7.1.1")
      plugins[1].version().resultModel.setValue("7.1.2")
      plugins[2].version().resultModel.setValue("7.1.3")
      applyChangesAndReparse(pbm)
      verifyFileContents(myBuildFile, TestFile.VERSION_CATALOG_PLUGINS_DSL_BUILD_FILE)
      verifyVersionCatalogFileContents(myVersionCatalogFile, TestFile.VERSION_CATALOG_PLUGINS_NOTATION_EXPECTED)
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testVersionCatalogBundlesDsl() {
    writeToBuildFile(TestFile.VERSION_CATALOG_BUNDLE_BUILD_FILE)
    writeToVersionCatalogFile(TestFile.VERSION_CATALOG_BUNDLES_COMPACT_NOTATION)

    val pbm = projectBuildModel
    val buildModel = pbm.projectBuildModel!!
    val dependencies = buildModel.dependencies()
    val artifacts = dependencies.artifacts()
    assertSize(2, artifacts)
    assertEquals("com.example:foo:1.2.3", artifacts[0].compactNotation())
    assertEquals("com.example:bar:1.2.3", artifacts[1].compactNotation())
  }

  @Test
  fun testWriteVersionCatalogMapNotation() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {
      writeToBuildFile(TestFile.VERSION_CATALOG_BUILD_FILE)
      writeToVersionCatalogFile(TestFile.VERSION_CATALOG_MAP_NOTATION)

      val pbm = projectBuildModel
      let {
        val buildModel = pbm.projectBuildModel!!
        val dependencies = buildModel.dependencies()
        val artifacts = dependencies.artifacts()
        assertSize(1, artifacts)
        artifacts[0].version().resultModel.setValue("2.3.4")
        applyChangesAndReparse(pbm)
      }

      val buildModel = pbm.projectBuildModel!!
      val dependencies = buildModel.dependencies()
      val artifacts = dependencies.artifacts()
      assertSize(1, artifacts)
      assertEquals("com.example:example:2.3.4", artifacts[0].compactNotation())
      verifyFileContents(myBuildFile, TestFile.VERSION_CATALOG_BUILD_FILE)
      verifyVersionCatalogFileContents(myVersionCatalogFile, TestFile.VERSION_CATALOG_MAP_NOTATION_EXPECTED)
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testWriteVersionCatalogMapVersionRefNotation() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {
      writeToBuildFile(TestFile.VERSION_CATALOG_BUILD_FILE)
      writeToVersionCatalogFile(TestFile.VERSION_CATALOG_MAP_VERSION_REF_NOTATION)

      val pbm = projectBuildModel
      let {
        val buildModel = pbm.projectBuildModel!!
        val dependencies = buildModel.dependencies()
        val artifacts = dependencies.artifacts()
        assertSize(1, artifacts)
        artifacts[0].version().resultModel.setValue("2.3.4")
        applyChangesAndReparse(pbm)
      }

      val buildModel = pbm.projectBuildModel!!
      val dependencies = buildModel.dependencies()
      val artifacts = dependencies.artifacts()
      assertSize(1, artifacts)
      assertEquals("com.example:example:2.3.4", artifacts[0].compactNotation())
      verifyFileContents(myBuildFile, TestFile.VERSION_CATALOG_BUILD_FILE)
      verifyVersionCatalogFileContents(myVersionCatalogFile, TestFile.VERSION_CATALOG_MAP_VERSION_REF_NOTATION_EXPECTED)
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testWriteVersionCatalogCompactOverBundle() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {
      writeToBuildFile(TestFile.VERSION_CATALOG_BUNDLE_BUILD_FILE)
      writeToVersionCatalogFile(TestFile.VERSION_CATALOG_BUNDLES_COMPACT_NOTATION)
      val pbm = projectBuildModel
      let {
        val buildModel = pbm.projectBuildModel!!
        val dependencies = buildModel.dependencies()
        val artifacts = dependencies.artifacts()
        assertSize(2, artifacts)
        artifacts[0].version().resultModel.setValue("2.3.4")
        applyChangesAndReparse(pbm)
      }

      val artifacts = pbm.projectBuildModel!!.dependencies().artifacts()
      assertSize(2, artifacts)
      assertEquals("com.example:foo:2.3.4", artifacts[0].compactNotation())
      verifyFileContents(myBuildFile, TestFile.VERSION_CATALOG_BUNDLE_BUILD_FILE)
      verifyVersionCatalogFileContents(myVersionCatalogFile, TestFile.VERSION_CATALOG_BUNDLES_COMPACT_NOTATION_EXPECTED)
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testWriteVersionCatalogMapOverBundle() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {
      writeToBuildFile(TestFile.VERSION_CATALOG_BUNDLE_BUILD_FILE)
      writeToVersionCatalogFile(TestFile.VERSION_CATALOG_BUNDLES_MAP_NOTATION)
      val pbm = projectBuildModel
      let {
        val buildModel = pbm.projectBuildModel!!
        val dependencies = buildModel.dependencies()
        val artifacts = dependencies.artifacts()
        assertSize(2, artifacts)
        artifacts[0].version().resultModel.setValue("2.3.4")
        applyChangesAndReparse(pbm)
      }

      val artifacts = pbm.projectBuildModel!!.dependencies().artifacts()
      assertSize(2, artifacts)
      assertEquals("com.example:foo:2.3.4", artifacts[0].compactNotation())
      verifyFileContents(myBuildFile, TestFile.VERSION_CATALOG_BUNDLE_BUILD_FILE)
      verifyVersionCatalogFileContents(myVersionCatalogFile, TestFile.VERSION_CATALOG_BUNDLES_MAP_NOTATION_EXPECTED)
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testWriteVersionCatalogMapVersionRefOverBundle() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {
      writeToBuildFile(TestFile.VERSION_CATALOG_BUNDLE_BUILD_FILE)
      writeToVersionCatalogFile(TestFile.VERSION_CATALOG_BUNDLES_MAP_VERSION_REF_NOTATION)
      val pbm = projectBuildModel
      let {
        val buildModel = pbm.projectBuildModel!!
        val dependencies = buildModel.dependencies()
        val artifacts = dependencies.artifacts()
        assertSize(2, artifacts)
        artifacts[0].version().resultModel.setValue("2.3.4")
        applyChangesAndReparse(pbm)
      }

      val artifacts = pbm.projectBuildModel!!.dependencies().artifacts()
      assertSize(2, artifacts)
      assertEquals("com.example:foo:2.3.4", artifacts[0].compactNotation())
      verifyFileContents(myBuildFile, TestFile.VERSION_CATALOG_BUNDLE_BUILD_FILE)
      verifyVersionCatalogFileContents(myVersionCatalogFile, TestFile.VERSION_CATALOG_BUNDLES_MAP_VERSION_REF_NOTATION_EXPECTED)
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testVersionCatalogsModelIfSettingIsOff() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    GradleDslModelExperimentalSettings.getInstance().isVersionCatalogEnabled = false
    try {
      writeToBuildFile("")
      writeToVersionCatalogFile("")

      val pbm = projectBuildModel
      val vcModel = pbm.versionCatalogsModel
      assertNotNull(vcModel)
      assertEmpty(vcModel.catalogNames())
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
      GradleDslModelExperimentalSettings.getInstance().isVersionCatalogEnabled = true
    }
  }

  @Test
  fun testVersionCatalogDisabled() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {
      writeToBuildFile("")
      removeVersionCatalogFile()

      val pbm = projectBuildModel
      val vcModel = pbm.versionCatalogsModel
      assertNotNull(vcModel)
      assertEmpty(vcModel.catalogNames())
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testVersionCatalogCreateVersionProperty() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {
      writeToBuildFile("")
      writeToVersionCatalogFile("")

      val pbm = projectBuildModel
      val vcModel = pbm.versionCatalogsModel
      val versions = vcModel.versions("libs")!!
      versions.findProperty("foo").setValue("1.2.3")
      applyChanges(pbm)

      verifyFileContents(myBuildFile, "")
      verifyVersionCatalogFileContents(myVersionCatalogFile, TestFile.VERSION_CATALOG_CREATE_VERSION_PROPERTY_EXPECTED)
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testVersionPropertyWithGetVersionCatalogModel() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {
      writeToBuildFile("")
      writeToVersionCatalogFile("""
        [versions]
        foo = "1.1.1"
      """.trimIndent())

      val pbm = projectBuildModel
      val vcModel = pbm.versionCatalogsModel
      val versions = vcModel.getVersionCatalogModel("libs").versions()!!
      val foo = versions.findProperty("foo")
      assertEquals("1.1.1", foo.toString())
      foo.setValue("1.2.3")
      versions.findProperty("bar").setValue("2.3.4")
      applyChanges(pbm)

      verifyFileContents(myBuildFile, "")
      verifyFileContents(myVersionCatalogFile, """
        [versions]
        foo = "1.2.3"
        bar = "2.3.4"
      """.trimIndent())
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testVersionCatalogDeleteVersionProperty() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {
      writeToBuildFile("")
      writeToVersionCatalogFile("""
        [versions]
        foo = "1.2.3"
      """.trimIndent())

      val pbm = projectBuildModel
      val vcModel = pbm.versionCatalogsModel
      val versions = vcModel.versions("libs")!!
      val foo = versions.findProperty("foo")
      assertEquals("1.2.3", foo.toString())
      foo.delete()
      applyChanges(pbm)
      verifyVersionCatalogFileContents(myVersionCatalogFile, """
        [versions]
      """.trimIndent())
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testUpdateFromMultipleVCModels() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {
      writeToBuildFile("")
      writeToVersionCatalogFile("""
        [libraries]
        foo = { version = "1.2.3", group = "com.example", name = "foo" }
      """.trimIndent())

      val pbm = projectBuildModel
      val vcModel = pbm.versionCatalogsModel
      val lib1 = vcModel.libraries("libs")!!
      val lib2 = vcModel.libraries("libs")!!
      val foo1 = lib1.findProperty("foo")
      val foo2 = lib2.findProperty("foo")
      foo1.getMapValue("group")!!.delete()
      // models are not in sync
      Assert.assertNotNull(foo2.getMapValue ("group"))
      foo2.getMapValue("version")!!.delete()
      applyChanges(pbm)
      verifyVersionCatalogFileContents(myVersionCatalogFile, """
         [libraries]
        foo = { name = "foo" }
      """.trimIndent())
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testVersionCatalogDeleteMapOnlyElement() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {
      writeToBuildFile("")
      writeToVersionCatalogFile("""
        [libraries]
        foo = { arbitrary = "abc" }
      """.trimIndent())

      val pbm = projectBuildModel
      val vcModel = pbm.versionCatalogsModel
      val libraries = vcModel.libraries("libs")!!
      val foo = libraries.findProperty("foo")
      assertEquals("abc", foo.getMapValue("arbitrary")!!.toString())
      foo.getMapValue("arbitrary")!!.delete()
      applyChanges(pbm)
      verifyVersionCatalogFileContents(myVersionCatalogFile, """
        [libraries]
        foo = { }
      """.trimIndent())
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testVersionCatalogDeleteMapElementOne() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {
      writeToBuildFile("")
      writeToVersionCatalogFile("""
        [libraries]
        foo = { version = "1.2.3", group = "com.example", name = "foo" }
      """.trimIndent())

      val pbm = projectBuildModel
      val vcModel = pbm.versionCatalogsModel
      val libraries = vcModel.libraries("libs")!!
      val foo = libraries.findProperty("foo")
      assertEquals("1.2.3", foo.getMapValue("version")!!.toString())
      assertEquals("com.example", foo.getMapValue("group")!!.toString())
      assertEquals("foo", foo.getMapValue("name")!!.toString())
      foo.getMapValue("version")!!.delete()
      applyChanges(pbm)
      verifyVersionCatalogFileContents(myVersionCatalogFile, """
        [libraries]
        foo = { group = "com.example", name = "foo" }
      """.trimIndent())
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testVersionCatalogDeleteMapElementTwo() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {
      writeToBuildFile("")
      writeToVersionCatalogFile("""
        [libraries]
        foo = { version = "1.2.3", group = "com.example", name = "foo" }
      """.trimIndent())

      val pbm = projectBuildModel
      val vcModel = pbm.versionCatalogsModel
      val libraries = vcModel.libraries("libs")!!
      val foo = libraries.findProperty("foo")
      assertEquals("1.2.3", foo.getMapValue("version")!!.toString())
      assertEquals("com.example", foo.getMapValue("group")!!.toString())
      assertEquals("foo", foo.getMapValue("name")!!.toString())
      foo.getMapValue("group")!!.delete()
      applyChanges(pbm)
      verifyVersionCatalogFileContents(myVersionCatalogFile, """
        [libraries]
        foo = { version = "1.2.3", name = "foo" }
      """.trimIndent())
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testVersionCatalogDeleteMapElementThree() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {
      writeToBuildFile("")
      writeToVersionCatalogFile("""
        [libraries]
        foo = { version = "1.2.3", group = "com.example", name = "foo" }
      """.trimIndent())

      val pbm = projectBuildModel
      val vcModel = pbm.versionCatalogsModel
      val libraries = vcModel.libraries("libs")!!
      val foo = libraries.findProperty("foo")
      assertEquals("1.2.3", foo.getMapValue("version")!!.toString())
      assertEquals("com.example", foo.getMapValue("group")!!.toString())
      assertEquals("foo", foo.getMapValue("name")!!.toString())
      foo.getMapValue("name")!!.delete()
      applyChanges(pbm)
      verifyVersionCatalogFileContents(myVersionCatalogFile, """
        [libraries]
        foo = { version = "1.2.3", group = "com.example" }
      """.trimIndent())
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testLibraryMapVersionToVersion() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {
      writeToBuildFile("")
      writeToVersionCatalogFile("""
        [libraries]
        foo = { version = "1.2.3", group = "com.example", name = "foo" }
      """.trimIndent())

      val pbm = projectBuildModel
      val vcModel = pbm.versionCatalogsModel
      val libraries = vcModel.libraries("libs")!!
      val foo = libraries.findProperty("foo")
      assertEquals("1.2.3", foo.getMapValue("version")!!.toString())
      foo.getMapValue("version")!!.setValue("2.3.4")
      applyChanges(pbm)
      verifyVersionCatalogFileContents(myVersionCatalogFile, """
        [libraries]
        foo = { version = "2.3.4", group = "com.example", name = "foo" }
      """.trimIndent())
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testTwoTomlFilesVisibility() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {
      val gradlePath = myProjectBasePath.findChild("gradle")!!
      var myVersionCatalogFile: VirtualFile? = null
        runWriteAction<Unit, IOException> { myVersionCatalogFile = gradlePath.createChildData(this, "testLibs.versions.toml") }
      saveFileUnderWrite(myVersionCatalogFile!!, """
      [libraries]
        fooTest = { version = "2.3.4", group = "com.example", name = "fooTest" }
      """.trimIndent())
      writeToSettingsFile("""
        dependencyResolutionManagement {
          versionCatalogs {
             testLibs {
              from(files("gradle/testLibs.versions.toml"))
            }
          }
        }
      """.trimIndent())
      writeToBuildFile("")
      writeToVersionCatalogFile("""
        [libraries]
        foo = { version = "1.2.3", group = "com.example", name = "foo" }
      """.trimIndent())

      val pbm = projectBuildModel
      val vcModel = pbm.versionCatalogsModel
      assertContainsElements(vcModel.catalogNames(), "libs", "testLibs")
      val libraries = vcModel.libraries("libs")!!
      val foo = libraries.findProperty("foo")
      assertEquals("1.2.3", foo.getMapValue("version")!!.toString())

      val testLibs = vcModel.libraries("testLibs")!!
      val fooTest = testLibs.findProperty("fooTest")
      assertEquals("2.3.4", fooTest.getMapValue("version")!!.toString())

      fooTest.getMapValue("version")!!.setValue("3.3.3")
      applyChanges(pbm)
      verifyFileContents(myVersionCatalogFile!!, """
        [libraries]
        fooTest = { version = "3.3.3", group = "com.example", name = "fooTest" }
      """.trimIndent())
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testLibraryMapVersionRefToVersionRef() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {
      writeToBuildFile("")
      writeToVersionCatalogFile("""
        [versions]
        fooVersion = "1.2.3"
        otherFooVersion = "2.3.4"

        [libraries]
        foo = { version.ref = "fooVersion", group = "com.example", name = "foo" }
      """.trimIndent())

      val pbm = projectBuildModel
      val vcModel = pbm.versionCatalogsModel
      val libraries = vcModel.libraries("libs")!!
      val foo = libraries.findProperty("foo")
      assertEquals("\"fooVersion\"", foo.getMapValue("version")!!.toString())
      foo.getMapValue("version")!!.setValue(ReferenceTo(vcModel.versions("libs")!!.findProperty("otherFooVersion")))
      applyChanges(pbm)
      verifyVersionCatalogFileContents(myVersionCatalogFile, """
        [versions]
        fooVersion = "1.2.3"
        otherFooVersion = "2.3.4"

        [libraries]
        foo = { version.ref = "otherFooVersion", group = "com.example", name = "foo" }
      """.trimIndent())
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testLibraryMapVersionMapRefToVersionRef() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {
      writeToBuildFile("")
      writeToVersionCatalogFile("""
        [versions]
        fooVersion = "1.2.3"
        otherFooVersion = "2.3.4"

        [libraries]
        foo = { version = { ref = "fooVersion" }, group = "com.example", name = "foo" }
      """.trimIndent())

      val pbm = projectBuildModel
      val vcModel = pbm.versionCatalogsModel
      val libraries = vcModel.libraries("libs")!!
      val foo = libraries.findProperty("foo")
      assertEquals("\"fooVersion\"", foo.getMapValue("version")!!.toString())
      foo.getMapValue("version")!!.setValue(ReferenceTo(vcModel.versions("libs")!!.findProperty("otherFooVersion")))
      applyChanges(pbm)
      verifyVersionCatalogFileContents(myVersionCatalogFile, """
        [versions]
        fooVersion = "1.2.3"
        otherFooVersion = "2.3.4"

        [libraries]
        foo = { version = { ref = "otherFooVersion" }, group = "com.example", name = "foo" }
      """.trimIndent())
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testLibraryMapVersionToVersionRef() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {
      writeToBuildFile("")
      writeToVersionCatalogFile("""
        [versions]
        fooVersion = "2.3.4"

        [libraries]
        foo = { version = "1.2.3", group = "com.example", name = "foo" }
      """.trimIndent())

      val pbm = projectBuildModel
      val vcModel = pbm.versionCatalogsModel
      val libraries = vcModel.libraries("libs")!!
      val foo = libraries.findProperty("foo")
      assertEquals("1.2.3", foo.getMapValue("version")!!.toString())
      foo.getMapValue("version")!!.setValue(ReferenceTo(vcModel.versions("libs")!!.findProperty("fooVersion")))
      applyChanges(pbm)
      verifyVersionCatalogFileContents(myVersionCatalogFile, """
        [versions]
        fooVersion = "2.3.4"

        [libraries]
        foo = { version.ref = "fooVersion", group = "com.example", name = "foo" }
      """.trimIndent())
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testLibraryMapVersionRefToVersion() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {
      writeToBuildFile("")
      writeToVersionCatalogFile("""
        [versions]
        fooVersion = "1.2.3"

        [libraries]
        foo = { version.ref = "fooVersion", group = "com.example", name = "foo" }
      """.trimIndent())

      val pbm = projectBuildModel
      val vcModel = pbm.versionCatalogsModel
      val libraries = vcModel.libraries("libs")!!
      val foo = libraries.findProperty("foo")
      assertEquals("\"fooVersion\"", foo.getMapValue("version")!!.toString())
      foo.getMapValue("version")!!.setValue("2.3.4")
      applyChanges(pbm)
      verifyVersionCatalogFileContents(myVersionCatalogFile, """
        [versions]
        fooVersion = "1.2.3"

        [libraries]
        foo = { version = "2.3.4", group = "com.example", name = "foo" }
      """.trimIndent())
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testLibraryMapVersionMapRefToVersion() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {
      writeToBuildFile("")
      writeToVersionCatalogFile("""
        [versions]
        fooVersion = "1.2.3"

        [libraries]
        foo = { version = { ref = "fooVersion" }, group = "com.example", name = "foo" }
      """.trimIndent())

      val pbm = projectBuildModel
      val vcModel = pbm.versionCatalogsModel
      val libraries = vcModel.libraries("libs")!!
      val foo = libraries.findProperty("foo")
      assertEquals("\"fooVersion\"", foo.getMapValue("version")!!.toString())
      foo.getMapValue("version")!!.setValue("2.3.4")
      applyChanges(pbm)
      verifyVersionCatalogFileContents(myVersionCatalogFile, """
        [versions]
        fooVersion = "1.2.3"

        [libraries]
        foo = { version = "2.3.4", group = "com.example", name = "foo" }
      """.trimIndent())
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testLibraryCreateVersion() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {
      writeToBuildFile("")
      writeToVersionCatalogFile("""
        [versions]
        fooVersion = "1.2.3"

        [libraries]
        foo = { group = "com.example", name = "foo" }
      """.trimIndent())

      val pbm = projectBuildModel
      val vcModel = pbm.versionCatalogsModel
      val libraries = vcModel.libraries("libs")!!
      val foo = libraries.findProperty("foo")
      foo.getMapValue("version")!!.setValue("2.3.4")
      applyChanges(pbm)
      verifyVersionCatalogFileContents(myVersionCatalogFile, """
        [versions]
        fooVersion = "1.2.3"

        [libraries]
        foo = { group = "com.example", name = "foo", version = "2.3.4" }
      """.trimIndent())
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testLibraryCreateVersionRef() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {
      writeToBuildFile("")
      writeToVersionCatalogFile("""
        [versions]
        fooVersion = "1.2.3"

        [libraries]
        foo = { group = "com.example", name = "foo" }
      """.trimIndent())

      val pbm = projectBuildModel
      val vcModel = pbm.versionCatalogsModel
      val libraries = vcModel.libraries("libs")!!
      val foo = libraries.findProperty("foo")
      foo.getMapValue("version")!!.setValue(ReferenceTo(vcModel.versions("libs")!!.findProperty("fooVersion")))
      applyChanges(pbm)
      verifyVersionCatalogFileContents(myVersionCatalogFile, """
        [versions]
        fooVersion = "1.2.3"

        [libraries]
        foo = { group = "com.example", name = "foo", version.ref = "fooVersion" }
      """.trimIndent())
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testLibraryCreateMapWithVersion() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {
      writeToBuildFile("")
      writeToVersionCatalogFile("""
        [versions]
        fooVersion = "1.2.3"

        [libraries]
      """.trimIndent())

      val pbm = projectBuildModel
      val vcModel = pbm.versionCatalogsModel
      val libraries = vcModel.libraries("libs")!!
      val foo = libraries.findProperty("foo")
      foo.getMapValue("version")!!.setValue("2.3.4")
      foo.getMapValue("module")!!.setValue("com.example:foo")
      applyChanges(pbm)
      verifyVersionCatalogFileContents(myVersionCatalogFile, """
        [versions]
        fooVersion = "1.2.3"

        [libraries]
        foo = { version = "2.3.4", module = "com.example:foo" }
      """.trimIndent())
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testLibraryCreateMapWithVersionRef() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {
      writeToBuildFile("")
      writeToVersionCatalogFile("""
        [versions]
        fooVersion = "1.2.3"

        [libraries]
      """.trimIndent())

      val pbm = projectBuildModel
      val vcModel = pbm.versionCatalogsModel
      val libraries = vcModel.libraries("libs")!!
      val foo = libraries.findProperty("foo")
      foo.getMapValue("version")!!.setValue(ReferenceTo(vcModel.versions("libs")!!.findProperty("fooVersion")))
      foo.getMapValue("module")!!.setValue("com.example:foo")
      applyChanges(pbm)
      verifyVersionCatalogFileContents(myVersionCatalogFile, """
        [versions]
        fooVersion = "1.2.3"

        [libraries]
        foo = { version.ref = "fooVersion", module = "com.example:foo" }
      """.trimIndent())
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testPluginCreateVersion() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {
      writeToBuildFile("")
      writeToVersionCatalogFile("""
        [versions]
        fooVersion = "1.2.3"

        [plugins]
        foo = { id = "com.example.foo" }
      """.trimIndent())

      val pbm = projectBuildModel
      val vcModel = pbm.versionCatalogsModel
      val plugins = vcModel.plugins("libs")!!
      val foo = plugins.findProperty("foo")
      foo.getMapValue("version")!!.setValue("2.3.4")
      applyChanges(pbm)
      verifyVersionCatalogFileContents(myVersionCatalogFile, """
        [versions]
        fooVersion = "1.2.3"

        [plugins]
        foo = { id = "com.example.foo", version = "2.3.4" }
      """.trimIndent())
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testPluginCreateVersionRef() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {
      writeToBuildFile("")
      writeToVersionCatalogFile("""
        [versions]
        fooVersion = "1.2.3"

        [plugins]
        foo = { id = "com.example.foo" }
      """.trimIndent())

      val pbm = projectBuildModel
      val vcModel = pbm.versionCatalogsModel
      val plugins = vcModel.plugins("libs")!!
      val foo = plugins.findProperty("foo")
      foo.getMapValue("version")!!.setValue(ReferenceTo(vcModel.versions("libs")!!.findProperty("fooVersion")))
      applyChanges(pbm)
      verifyVersionCatalogFileContents(myVersionCatalogFile, """
        [versions]
        fooVersion = "1.2.3"

        [plugins]
        foo = { id = "com.example.foo", version.ref = "fooVersion" }
      """.trimIndent())
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testPluginCreateMapWithVersion() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {
      writeToBuildFile("")
      writeToVersionCatalogFile("""
        [versions]
        fooVersion = "1.2.3"

        [plugins]
      """.trimIndent())

      val pbm = projectBuildModel
      val vcModel = pbm.versionCatalogsModel
      val plugins = vcModel.plugins("libs")!!
      val foo = plugins.findProperty("foo")
      foo.getMapValue("version")!!.setValue("2.3.4")
      foo.getMapValue("id")!!.setValue("com.example.foo")
      applyChanges(pbm)
      verifyVersionCatalogFileContents(myVersionCatalogFile, """
        [versions]
        fooVersion = "1.2.3"

        [plugins]
        foo = { version = "2.3.4", id = "com.example.foo" }
      """.trimIndent())
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testPluginCreateMapWithVersionRef() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {
      writeToBuildFile("")
      writeToVersionCatalogFile("""
        [versions]
        fooVersion = "1.2.3"

        [plugins]
      """.trimIndent())

      val pbm = projectBuildModel
      val vcModel = pbm.versionCatalogsModel
      val plugins = vcModel.plugins("libs")!!
      val foo = plugins.findProperty("foo")
      foo.getMapValue("version")!!.setValue(ReferenceTo(vcModel.versions("libs")!!.findProperty("fooVersion")))
      foo.getMapValue("id")!!.setValue("com.example.foo")
      applyChanges(pbm)
      verifyVersionCatalogFileContents(myVersionCatalogFile, """
        [versions]
        fooVersion = "1.2.3"

        [plugins]
        foo = { version.ref = "fooVersion", id = "com.example.foo" }
      """.trimIndent())
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testSetVersionToReferenceByText() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {
      writeToBuildFile("")
      writeToVersionCatalogFile("""
        [versions]
        fooVersion = "2.3.4"

        [libraries]
        foo = { module = "com.example:foo", version = "1.2.3" }
      """.trimIndent())
      val pbm = projectBuildModel
      val vcModel = pbm.versionCatalogsModel
      val libraries = vcModel.libraries("libs")!!
      val foo = libraries.findProperty("foo")
      val ref = ReferenceTo.createReferenceFromText("versions.fooVersion", foo)!!
      foo.getMapValue("version")!!.setValue(ref)
      applyChanges(pbm)
      verifyVersionCatalogFileContents(myVersionCatalogFile, """
        [versions]
        fooVersion = "2.3.4"

        [libraries]
        foo = { module = "com.example:foo", version.ref = "fooVersion" }
      """.trimIndent())


    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testPluginAliasInvalidSyntax() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {
      writeToBuildFile(TestFile.VERSION_CATALOG_BUILD_FILE_INVALID_ALIAS)
      writeToVersionCatalogFile(TestFile.VERSION_CATALOG_MAP_NOTATION)
      val pbm = projectBuildModel
      val buildModel = pbm.projectBuildModel!!
      val appliedPlugins = buildModel.appliedPlugins()
      assertSize(0, appliedPlugins)
      val plugins = buildModel.plugins()
      assertSize(0, plugins)
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testSimpleBundle() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {
      writeToBuildFile("")
      writeToVersionCatalogFile("""
        [libraries]
        foo = { group = "com.example", name = "foo", version = "1.2.3" }
        bar = { group = "com.example", name = "bar", version = "1.2.3" }

        [bundles]
        core = [ "foo", "bar" ]
      """.trimIndent())

      val vcModel = projectBuildModel.versionCatalogsModel
      val bundles = vcModel.bundles("libs")!!
      val libraries = vcModel.libraries("libs")!!
      val refs = bundles.findProperty("core").toList()!!

      //Check that libraries.foo is the same DSL element that is referred from bundles.code[0] (foo)
      assertTrue(libraries.findProperty("foo").rawElement == refs[0].dependencies[0].rawElement)
      assertTrue(libraries.findProperty("bar").rawElement == refs[1].dependencies[0].rawElement)

      assertEquals(refs.map { it.rawElement!!.name }, listOf("foo", "bar"))
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testBundleCreateMapWithLibs() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {
      writeToBuildFile("")
      writeToVersionCatalogFile("""
        [libraries]
        foo = { group = "com.example", name = "foo", version = "1.2.3" }
        bar = { group = "com.example", name = "bar", version = "1.2.3" }
      """.trimIndent())

      val pbm = projectBuildModel
      val vcModel = pbm.versionCatalogsModel
      val bundles = vcModel.bundles("libs")!!
      val core = bundles.findProperty("core")

      val libraries = vcModel.libraries("libs")!!
      core.addListValue()!!.setValue(ReferenceTo(libraries.findProperty("bar")))
      core.addListValueAt(0)!!.setValue(ReferenceTo(libraries.findProperty("foo")))
      applyChanges(pbm)
      verifyVersionCatalogFileContents(myVersionCatalogFile, """
        [libraries]
        foo = { group = "com.example", name = "foo", version = "1.2.3" }
        bar = { group = "com.example", name = "bar", version = "1.2.3" }
        [bundles]
        core = [ "foo", "bar" ]
      """.trimIndent())
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testBundleAppendLib() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {
      writeToBuildFile("")
      writeToVersionCatalogFile("""
        [libraries]
        foo = { group = "com.example", name = "foo", version = "1.2.3" }
        bar = { group = "com.example", name = "bar", version = "1.2.3" }

        [bundles]
        core = [ "foo" ]
      """.trimIndent())

      val pbm = projectBuildModel
      val vcModel = pbm.versionCatalogsModel
      val bundles = vcModel.bundles("libs")!!
      val core = bundles.findProperty("core")

      val libraries = vcModel.libraries("libs")!!
      core.addListValue()!!.setValue(ReferenceTo(libraries.findProperty("bar")))
      applyChanges(pbm)
      verifyVersionCatalogFileContents(myVersionCatalogFile, """
        [libraries]
        foo = { group = "com.example", name = "foo", version = "1.2.3" }
        bar = { group = "com.example", name = "bar", version = "1.2.3" }

        [bundles]
        core = [ "foo", "bar" ]
      """.trimIndent())
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testDeleteLibFromBundle() {
    StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.override(true)
    try {
      writeToBuildFile("")
      writeToVersionCatalogFile("""
        [libraries]
        foo = { group = "com.example", name = "foo", version = "1.2.3" }
        bar = { group = "com.example", name = "bar", version = "1.2.3" }

        [bundles]
        core = [ "foo", "bar" ]
      """.trimIndent())

      val pbm = projectBuildModel
      val vcModel = pbm.versionCatalogsModel
      val core = vcModel.bundles("libs")!!.findProperty("core")

      core.toList()!![0].delete()
      applyChanges(pbm)
      verifyVersionCatalogFileContents(myVersionCatalogFile, """
        [libraries]
        foo = { group = "com.example", name = "foo", version = "1.2.3" }
        bar = { group = "com.example", name = "bar", version = "1.2.3" }

        [bundles]
        core = [ "bar" ]
      """.trimIndent())
    }
    finally {
      StudioFlags.GRADLE_DSL_TOML_WRITE_SUPPORT.clearOverride()
    }
  }

  enum class TestFile(val path: @SystemDependent String): TestFileName {
    APPLIED_FILES_SHARED("appliedFilesShared"),
    APPLIED_FILES_SHARED_APPLIED("appliedFilesSharedApplied"),
    APPLIED_FILES_SHARED_APPLIED_EXPECTED("appliedFilesSharedAppliedExpected"),
    APPLIED_FILES_SHARED_SUB("appliedFilesShared_sub"),
    APPLIED_FILES_SHARED_SUB_EXPECTED("appliedFilesShared_subExpected"),
    APPLY_NO_ROOT_BUILD_FILE("applyNoRootBuildFile"),
    APPLY_NO_ROOT_BUILD_FILE_EXPECTED("applyNoRootBuildFileExpected"),
    MULTIPLE_MODELS_PERSIST_CHANGES("multipleModelsPersistChanges"),
    MULTIPLE_MODELS_PERSIST_CHANGES_SUB("multipleModelsPersistChanges_sub"),
    MULTIPLE_MODELS_PERSIST_CHANGES_EXPECTED("multipleModelsPersistChangesExpected"),
    MULTIPLE_MODELS_PERSIST_CHANGES_SUB_EXPECTED("multipleModelsPersistChanges_subExpected"),
    SETTINGS_FILE_UPDATES_CORRECTLY("settingsFileUpdatesCorrectly"),
    SETTINGS_FILE_UPDATES_CORRECTLY_EXPECTED("settingsFileUpdatesCorrectlyExpected"),
    SETTINGS_FILE_UPDATES_CORRECTLY_SUB("settingsFileUpdatesCorrectly_sub"),
    SETTINGS_FILE_UPDATES_CORRECTLY_OTHER_SUB("settingsFileUpdatesCorrectlyOther_sub"),
    SETTINGS_FILE_UPDATES_CORRECTLY_SETTINGS_EXPECTED("settingsFileUpdatesCorrectlySettingsExpected"),
    PROJECT_MODELS_SAVES_FILES("projectModelSavesFiles"),
    PROJECT_MODELS_SAVES_FILES_SUB("projectModelSavesFiles_sub"),
    PROJECT_MODELS_SAVES_FILES_EXPECTED("projectModelSavesFilesExpected"),
    GET_MODEL_FROM_VIRTUAL_FILE("getModelFromVirtualFile"),
    ENSURE_PARSING_APPLIED_FILE_IN_SUBMODULE_FOLDER("ensureParsingAppliedFileInSubmoduleFolder"),
    ENSURE_PARSING_APPLIED_FILE_IN_SUBMODULE_FOLDER_SUB("ensureParsingAppliedFileInSubmoduleFolder_sub"),
    ENSURE_PARSING_APPLIED_FILE_IN_SUBMODULE_FOLDER_APPLIED("ensureParsingAppliedFileInSubmoduleFolderApplied"),
    RESOLVES_CORRECT_FILE("applyResolvesCorrectFile"),
    RESOLVES_CORRECT_FILE_APPLIED("applyResolvesCorrectFileApplied"),
    RESOLVES_CORRECT_FILE_APPLIED_SUB("applyResolvesCorrectFileApplied_sub"),
    RESOLVES_CORRECT_FILE_SUB("applyResolvesCorrectFile_sub"),
    REPARSE_THEN_CHANGE_EXPECTED_ONE("reparseThenChangeExpectedOne"),
    REPARSE_THEN_CHANGE_EXPECTED_TWO("reparseThenChangeExpectedTwo"),
    BUILD_SRC_ANDROID_GRADLE_PLUGIN_DEPENDENCY("buildSrcAndroidGradlePluginDependency"),
    BUILD_SRC_ANDROID_GRADLE_PLUGIN_DEPENDENCY_EXPECTED("buildSrcAndroidGradlePluginDependencyExpected"),
    CONTEXT_AGP_VERSION("contextAgpVersion"),
    VERSION_CATALOG_BUILD_FILE("versionCatalogBuildFile"),
    VERSION_CATALOG_ALIAS_MAPPING_BUILD_FILE("versionCatalogAliasMappingBuildFile"),
    VERSION_CATALOG_PLUGINS_DSL_BUILD_FILE("versionCatalogPluginsDslBuildFile"),
    VERSION_CATALOG_BUNDLE_BUILD_FILE("versionCatalogBundleBuildFile"),
    VERSION_CATALOG_BUILD_FILE_INVALID_ALIAS("versionCatalogBuildFileInvalidAlias"),
    VERSION_CATALOG_COMPACT_NOTATION("versionCatalogCompactNotation.toml"),
    VERSION_CATALOG_GROUP_COMPACT_NOTATION("versionCatalogGroupCompactNotation.toml"),
    VERSION_CATALOG_MAP_NOTATION("versionCatalogMapNotation.toml"),
    VERSION_CATALOG_MAP_NOTATION_EXPECTED("versionCatalogMapNotationExpected.toml"),
    VERSION_CATALOG_MODULE_NOTATION("versionCatalogModuleNotation.toml"),
    VERSION_CATALOG_BUNDLES_COMPACT_NOTATION("versionCatalogBundlesCompactNotation.toml"),
    VERSION_CATALOG_BUNDLES_COMPACT_NOTATION_EXPECTED("versionCatalogBundlesCompactNotationExpected.toml"),
    VERSION_CATALOG_BUNDLES_MAP_NOTATION("versionCatalogBundlesMapNotation.toml"),
    VERSION_CATALOG_BUNDLES_MAP_NOTATION_EXPECTED("versionCatalogBundlesMapNotationExpected.toml"),
    VERSION_CATALOG_BUNDLES_MAP_VERSION_REF_NOTATION("versionCatalogBundlesMapVersionRefNotation.toml"),
    VERSION_CATALOG_BUNDLES_MAP_VERSION_REF_NOTATION_EXPECTED("versionCatalogBundlesMapVersionRefNotationExpected.toml"),
    VERSION_CATALOG_MAP_VERSION_REF_NOTATION("versionCatalogMapVersionRefNotation.toml"),
    VERSION_CATALOG_MAP_VERSION_REF_NOTATION_EXPECTED("versionCatalogMapVersionRefNotationExpected.toml"),
    VERSION_CATALOG_MODULE_VERSION_REF_NOTATION("versionCatalogModuleVersionRefNotation.toml"),
    VERSION_CATALOG_PLUGINS_NOTATION("versionCatalogPluginsNotation.toml"),
    VERSION_CATALOG_PLUGINS_NOTATION_EXPECTED("versionCatalogPluginsNotationExpected.toml"),
    VERSION_CATALOG_CREATE_VERSION_PROPERTY_EXPECTED("versionCatalogCreateVersionPropertyExpected.toml"),
    ;

    override fun toFile(basePath: @SystemDependent String, extension: String): File {
      return super.toFile("$basePath/projectBuildModel/$path", extension)
    }
  }

}
