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
package com.android.tools.idea.gradle.actions

import com.android.build.OutputFile
import com.android.builder.model.AppBundleProjectBuildOutput
import com.android.builder.model.AppBundleVariantBuildOutput
import com.android.builder.model.InstantAppProjectBuildOutput
import com.android.builder.model.InstantAppVariantBuildOutput
import com.android.builder.model.ProjectBuildOutput
import com.android.builder.model.VariantBuildOutput
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.run.OutputBuildAction.PostBuildModuleModels
import com.android.tools.idea.gradle.run.OutputBuildAction.PostBuildProjectModels
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.android.tools.idea.testing.findAppModule
import com.android.tools.idea.testing.setupTestProjectFromAndroidModel
import com.google.common.collect.ImmutableList
import com.google.common.collect.Lists
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import java.io.File
import java.util.ArrayList

/**
 * Tests for [BuildsToPathsMapper].
 */
class BuildsToPathsMapperTest : HeavyPlatformTestCase() {
  private lateinit var myTask: BuildsToPathsMapper

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    myTask = BuildsToPathsMapper.getInstance(project)
  }

  private fun initTestProject(agpVersion: String, type: IdeAndroidProjectType) {
    setupTestProjectFromAndroidModel(
      project,
      File(project.basePath!!),
      JavaModuleModelBuilder(":"),
      AndroidModuleModelBuilder(
        gradlePath = ":app",
        agpVersion = agpVersion,
        selectedBuildVariant = "debug",
        projectBuilder = AndroidProjectBuilder(
          projectType = { type }
        )
      )
    )
    myModule = project.findAppModule()
  }

  fun testSingleOutputFromPostBuildModelForApk() {
    initTestProject("3.5", IdeAndroidProjectType.PROJECT_TYPE_APP)
    val output = File("path/to/apk")
    val androidModel = AndroidModuleModel.get(myModule)
    val buildVariant = androidModel!!.selectedVariant.name
    val buildsAndBundlePaths = myTask.getBuildsToPaths(
      createPostBuildModel(setOf(output), buildVariant),
      ImmutableList.of(),
      setOf(myModule),
      false,
      null)
    UsefulTestCase.assertSameElements(buildsAndBundlePaths.keys, myModule.name)
    TestCase.assertEquals(output, buildsAndBundlePaths[myModule.name])
  }

  fun testMultipleOutputsFromPostBuildModel() {
    initTestProject("3.5", IdeAndroidProjectType.PROJECT_TYPE_APP)
    val output1 = File("path/to/apk1")
    val output2 = File("path/to/apk2")
    TestCase.assertEquals(output1.parentFile, output2.parentFile)
    val androidModel = AndroidModuleModel.get(myModule)
    val buildVariant = androidModel!!.selectedVariant.name
    val buildsAndBundlePaths = myTask.getBuildsToPaths(
      createPostBuildModel(Lists.newArrayList(output1, output2), buildVariant),
      ImmutableList.of(),
      setOf(myModule),
      false,
      null)
    UsefulTestCase.assertSameElements(buildsAndBundlePaths.keys, myModule.name)
    TestCase.assertEquals(output1.parentFile, buildsAndBundlePaths[myModule.name])
  }

  fun testSingleOutputFromPreBuildModel() {
    initTestProject("2.3", IdeAndroidProjectType.PROJECT_TYPE_APP)
    val buildsAndBundlePaths = myTask.getBuildsToPaths(null,
                                                       ImmutableList.of(),
                                                       setOf(myModule), false, null)
    // TODO find some way to create module with meaningful output files.
    TestCase.assertTrue(buildsAndBundlePaths.isEmpty())
  }

  fun testSingleOutputFromPostBuildModelForSignedApk() {
    initTestProject("3.5", IdeAndroidProjectType.PROJECT_TYPE_APP)
    val output = File("path/to/apk")
    val buildsAndBundlePaths = myTask.getBuildsToPaths(
      createPostBuildModel(setOf(output), buildVariant),
      ImmutableList.of(buildVariant),
      setOf(myModule),
      false,
      "")
    UsefulTestCase.assertSameElements(buildsAndBundlePaths.keys, buildVariant)
    TestCase.assertEquals(output, buildsAndBundlePaths[buildVariant])
  }

  fun testMultipleOutputFromPostBuildModelForSignedApk() {
    initTestProject("3.5", IdeAndroidProjectType.PROJECT_TYPE_APP)
    val output1 = File("path/to/apk1")
    val output2 = File("path/to/apk2")
    TestCase.assertEquals(output1.parentFile, output2.parentFile)
    val buildsAndBundlePaths = myTask.getBuildsToPaths(
      createPostBuildModel(Lists.newArrayList(output1, output2),
                           buildVariant),
      ImmutableList.of(buildVariant),
      setOf(myModule),
      false,
      "")
    UsefulTestCase.assertSameElements(buildsAndBundlePaths.keys, buildVariant)
    TestCase.assertEquals(output1.parentFile,
                          buildsAndBundlePaths[buildVariant])
  }

  fun testSingleOutputFromInstantAppPostBuildModel() {
    initTestProject("3.5", IdeAndroidProjectType.PROJECT_TYPE_INSTANTAPP)
    val output = File("path/to/bundle")
    val androidModel = AndroidModuleModel.get(myModule)
    val buildVariant = androidModel!!.selectedVariant.name
    val buildsAndBundlePaths = myTask.getBuildsToPaths(
      createInstantAppPostBuildModel(output, buildVariant),
      emptyList(),
      setOf(myModule),
      false,
      null)
    UsefulTestCase.assertSameElements(buildsAndBundlePaths.keys, myModule.name)
    TestCase.assertEquals(output, buildsAndBundlePaths[myModule.name])
  }

  fun testSingleOutputFromInstantAppPostBuildModelForSignedApk() {
    initTestProject("3.5", IdeAndroidProjectType.PROJECT_TYPE_INSTANTAPP)
    val output = File("path/to/bundle")
    val buildsAndBundlePaths = myTask.getBuildsToPaths(
      createInstantAppPostBuildModel(output, buildVariant),
      ImmutableList.of(buildVariant),
      setOf(myModule),
      false,
      "")
    UsefulTestCase.assertSameElements(buildsAndBundlePaths.keys, buildVariant)
    TestCase.assertEquals(output, buildsAndBundlePaths[buildVariant])
  }

  fun testSingleOutputFromPostBuildModelForBundle() {
    initTestProject("3.5", IdeAndroidProjectType.PROJECT_TYPE_APP)
    val output = File("path/to/bundle")
    val buildsAndBundlePaths = myTask.getBuildsToPaths(
      createAppBundleBuildModel(output, AndroidModuleModel.get(myModule)!!.selectedVariant.name),
      emptyList(),
      setOf(myModule),
      true,
      null)

    UsefulTestCase.assertSameElements(buildsAndBundlePaths.keys, myModule.name)
    TestCase.assertEquals(output, buildsAndBundlePaths[myModule.name])
  }

  fun testSingleOutputFromPostBuildModelForSignedBundle() {
    initTestProject("3.5", IdeAndroidProjectType.PROJECT_TYPE_APP)
    val output = File("path/to/bundle")
    val buildsAndBundlePaths = myTask.getBuildsToPaths(
      createAppBundleBuildModel(output, buildVariant),
      ImmutableList.of(buildVariant),
      setOf(myModule),
      true,
      "")
    UsefulTestCase.assertSameElements(buildsAndBundlePaths.keys, buildVariant)
    TestCase.assertEquals(output, buildsAndBundlePaths[buildVariant])
  }

  private fun createInstantAppPostBuildModel(output: File, buildVariant: String): PostBuildProjectModels {
    val instantAppProjectBuildOutput = createInstantAppProjectBuildOutputMock(
      buildVariant, output)
    val postBuildModuleModels = PostBuildModuleModelsMockBuilder().setInstantAppProjectBuildOutput(
      instantAppProjectBuildOutput).build()
    return PostBuildProjectModelsMockBuilder().setPostBuildModuleModels(
      GradleUtil.getGradlePath(myModule)!!, postBuildModuleModels).build()
  }

  private fun createPostBuildModel(outputs: Collection<File>,
                                   buildVariant: String): PostBuildProjectModels {
    val projectBuildOutput = createProjectBuildOutputMock(buildVariant, outputs)
    val postBuildModuleModels = PostBuildModuleModelsMockBuilder().setProjectBuildOutput(
      projectBuildOutput).build()
    return PostBuildProjectModelsMockBuilder().setPostBuildModuleModels(
      GradleUtil.getGradlePath(myModule)!!, postBuildModuleModels).build()
  }

  private fun createAppBundleBuildModel(output: File,
                                        buildVariant: String): PostBuildProjectModels {
    val projectBuildOutput = createAppBundleOutputMock(buildVariant, output)
    val postBuildModuleModels = PostBuildModuleModelsMockBuilder().setAppBundleProjectBuildOutput(
      projectBuildOutput).build()
    return PostBuildProjectModelsMockBuilder().setPostBuildModuleModels(
      GradleUtil.getGradlePath(myModule)!!, postBuildModuleModels).build()
  }

  private class PostBuildModuleModelsMockBuilder {
    private val myPostBuildModuleModels: PostBuildModuleModels = Mockito.mock(PostBuildModuleModels::class.java)
    fun setProjectBuildOutput(projectBuildOutput: ProjectBuildOutput): PostBuildModuleModelsMockBuilder {
      Mockito.`when`(
        myPostBuildModuleModels.findModel(ArgumentMatchers.eq(
          ProjectBuildOutput::class.java))).thenReturn(projectBuildOutput)
      return this
    }

    fun setAppBundleProjectBuildOutput(appBundleOutput: AppBundleProjectBuildOutput): PostBuildModuleModelsMockBuilder {
      Mockito.`when`(
        myPostBuildModuleModels.findModel(ArgumentMatchers.eq(
          AppBundleProjectBuildOutput::class.java))).thenReturn(appBundleOutput)
      return this
    }

    fun setInstantAppProjectBuildOutput(instantAppProjectBuildOutput: InstantAppProjectBuildOutput): PostBuildModuleModelsMockBuilder {
      Mockito.`when`(
        myPostBuildModuleModels.findModel(ArgumentMatchers.eq(
          InstantAppProjectBuildOutput::class.java))).thenReturn(instantAppProjectBuildOutput)
      return this
    }

    fun build(): PostBuildModuleModels {
      return myPostBuildModuleModels
    }
  }

  private class PostBuildProjectModelsMockBuilder {
    private val myPostBuildProjectModels: PostBuildProjectModels = Mockito.mock(PostBuildProjectModels::class.java)
    fun setPostBuildModuleModels(gradlePath: String,
                                 postBuildModuleModels: PostBuildModuleModels): PostBuildProjectModelsMockBuilder {
      Mockito.`when`(myPostBuildProjectModels.getModels(ArgumentMatchers.eq(gradlePath))).thenReturn(
        postBuildModuleModels)
      return this
    }

    fun build(): PostBuildProjectModels {
      return myPostBuildProjectModels
    }
  }

  companion object {
    private const val buildVariant = "FreeDebug"
    private fun createInstantAppProjectBuildOutputMock(variant: String, file: File): InstantAppProjectBuildOutput {
      val projectBuildOutput = Mockito.mock(
        InstantAppProjectBuildOutput::class.java)
      val variantBuildOutput = Mockito.mock(
        InstantAppVariantBuildOutput::class.java)
      val outputFile = Mockito.mock(OutputFile::class.java)
      Mockito.`when`(
        projectBuildOutput.instantAppVariantsBuildOutput).thenReturn(setOf(variantBuildOutput))
      Mockito.`when`(variantBuildOutput.name).thenReturn(variant)
      Mockito.`when`(variantBuildOutput.output).thenReturn(outputFile)
      Mockito.`when`(outputFile.outputFile).thenReturn(file)
      return projectBuildOutput
    }

    private fun createProjectBuildOutputMock(variant: String,
                                             files: Collection<File>): ProjectBuildOutput {
      val projectBuildOutput = Mockito.mock(ProjectBuildOutput::class.java)
      val variantBuildOutput = Mockito.mock(VariantBuildOutput::class.java)
      val outputFiles: MutableList<OutputFile> = ArrayList()
      for (file in files) {
        val outputFile = Mockito.mock(OutputFile::class.java)
        Mockito.`when`(outputFile.outputFile).thenReturn(file)
        outputFiles.add(outputFile)
      }
      Mockito.`when`(projectBuildOutput.variantsBuildOutput).thenReturn(
        setOf(variantBuildOutput))
      Mockito.`when`(variantBuildOutput.name).thenReturn(variant)
      Mockito.`when`(variantBuildOutput.outputs).thenReturn(outputFiles)
      return projectBuildOutput
    }

    private fun createAppBundleOutputMock(variant: String,
                                          file: File): AppBundleProjectBuildOutput {
      val projectBuildOutput = Mockito.mock(AppBundleProjectBuildOutput::class.java)
      val variantBuildOutput = Mockito.mock(AppBundleVariantBuildOutput::class.java)

      Mockito.`when`(projectBuildOutput.appBundleVariantsBuildOutput).thenReturn(
        setOf(variantBuildOutput))
      Mockito.`when`(variantBuildOutput.name).thenReturn(variant)
      Mockito.`when`(variantBuildOutput.bundleFile).thenReturn(file)
      return projectBuildOutput
    }
  }
}