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
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.project.build.invoker.AssembleInvocationResult
import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult
import com.android.tools.idea.gradle.project.build.invoker.GradleMultiInvocationResult
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.run.OutputBuildAction.PostBuildModuleModels
import com.android.tools.idea.gradle.run.OutputBuildAction.PostBuildProjectModels
import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.projectsystem.gradle.getGradleProjectPath
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.android.tools.idea.testing.findAppModule
import com.android.tools.idea.testing.setupTestProjectFromAndroidModel
import com.google.common.collect.ImmutableList
import com.google.common.collect.Lists
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.HeavyPlatformTestCase
import org.mockito.Mockito
import java.io.File

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
    initTestProject("3.5.0", IdeAndroidProjectType.PROJECT_TYPE_APP)
    val output = File("path/to/apk")
    val androidModel = GradleAndroidModel.get(myModule)
    val buildVariant = androidModel!!.selectedVariant.name
    val buildsAndBundlePaths = myTask.getBuildsToPaths(
      createPostBuildModel(setOf(output), buildVariant).toTestAssembleResult(),
      ImmutableList.of(),
      setOf(myModule),
      false
    )
    assertThat(buildsAndBundlePaths.keys).containsExactly(myModule.name)
    assertThat(buildsAndBundlePaths[myModule.name]).isEqualTo(output)
  }

  fun testMultipleOutputsFromPostBuildModel() {
    initTestProject("3.5.0", IdeAndroidProjectType.PROJECT_TYPE_APP)
    val output1 = File("path/to/apk1")
    val output2 = File("path/to/apk2")
    assertThat(output2.parentFile).isEqualTo(output1.parentFile)
    val androidModel = GradleAndroidModel.get(myModule)
    val buildVariant = androidModel!!.selectedVariant.name
    val buildsAndBundlePaths = myTask.getBuildsToPaths(
      createPostBuildModel(Lists.newArrayList(output1, output2), buildVariant).toTestAssembleResult(),
      ImmutableList.of(),
      setOf(myModule),
      false
    )
    assertThat(buildsAndBundlePaths.keys).containsExactly(myModule.name)
    assertThat(buildsAndBundlePaths[myModule.name]).isEqualTo(output1.parentFile)
  }

  fun testSingleOutputFromPostBuildModelForSignedApk() {
    initTestProject("3.5.0", IdeAndroidProjectType.PROJECT_TYPE_APP)
    val output = File("path/to/apk")
    val buildsAndBundlePaths = myTask.getBuildsToPaths(
      createPostBuildModel(setOf(output), buildVariant).toTestAssembleResult(),
      ImmutableList.of(buildVariant),
      setOf(myModule),
      false
    )
    assertThat(buildsAndBundlePaths.keys).containsExactly(buildVariant)
    assertThat(buildsAndBundlePaths[buildVariant]).isEqualTo(output)
  }

  fun testMultipleOutputFromPostBuildModelForSignedApk() {
    initTestProject("3.5.0", IdeAndroidProjectType.PROJECT_TYPE_APP)
    val output1 = File("path/to/apk1")
    val output2 = File("path/to/apk2")
    assertThat(output2.parentFile).isEqualTo(output1.parentFile)
    val buildsAndBundlePaths = myTask.getBuildsToPaths(
      createPostBuildModel(Lists.newArrayList(output1, output2),
                           buildVariant).toTestAssembleResult(),
      ImmutableList.of(buildVariant),
      setOf(myModule),
      false
    )
    assertThat(buildsAndBundlePaths.keys).containsExactly(buildVariant)
    assertThat(buildsAndBundlePaths[buildVariant]).isEqualTo(output1.parentFile)
  }

  fun testSingleOutputFromInstantAppPostBuildModel() {
    initTestProject("3.5.0", IdeAndroidProjectType.PROJECT_TYPE_INSTANTAPP)
    val output = File("path/to/bundle")
    val androidModel = GradleAndroidModel.get(myModule)
    val buildVariant = androidModel!!.selectedVariant.name
    val buildsAndBundlePaths = myTask.getBuildsToPaths(
      createInstantAppPostBuildModel(output, buildVariant).toTestAssembleResult(),
      emptyList(),
      setOf(myModule),
      false
    )
    assertThat(buildsAndBundlePaths.keys).containsExactly(myModule.name)
    assertThat(buildsAndBundlePaths[myModule.name]).isEqualTo(output)
  }

  fun testSingleOutputFromInstantAppPostBuildModelForSignedApk() {
    initTestProject("3.5.0", IdeAndroidProjectType.PROJECT_TYPE_INSTANTAPP)
    val output = File("path/to/bundle")
    val buildsAndBundlePaths = myTask.getBuildsToPaths(
      createInstantAppPostBuildModel(output, buildVariant).toTestAssembleResult(),
      ImmutableList.of(buildVariant),
      setOf(myModule),
      false
    )
    assertThat(buildsAndBundlePaths.keys).containsExactly(buildVariant)
    assertThat(buildsAndBundlePaths[buildVariant]).isEqualTo(output)
  }

  fun testSingleOutputFromPostBuildModelForBundle() {
    initTestProject("3.5.0", IdeAndroidProjectType.PROJECT_TYPE_APP)
    val output = File("path/to/bundle")
    val buildsAndBundlePaths = myTask.getBuildsToPaths(
      createAppBundleBuildModel(output, GradleAndroidModel.get(myModule)!!.selectedVariant.name).toTestAssembleResult(),
      emptyList(),
      setOf(myModule),
      true
    )

    assertThat(buildsAndBundlePaths.keys).containsExactly(myModule.name)
    assertThat(buildsAndBundlePaths[myModule.name]).isEqualTo(output)
  }

  fun testSingleOutputFromPostBuildModelForSignedBundle() {
    initTestProject("3.5.0", IdeAndroidProjectType.PROJECT_TYPE_APP)
    val output = File("path/to/bundle")
    val buildsAndBundlePaths = myTask.getBuildsToPaths(
      createAppBundleBuildModel(output, buildVariant).toTestAssembleResult(),
      ImmutableList.of(buildVariant),
      setOf(myModule),
      true
    )
    assertThat(buildsAndBundlePaths.keys).containsExactly(buildVariant)
    assertThat(buildsAndBundlePaths[buildVariant]).isEqualTo(output)
  }

  private fun createInstantAppPostBuildModel(output: File, buildVariant: String): PostBuildProjectModels {
    val instantAppProjectBuildOutput = createInstantAppProjectBuildOutputMock(
      buildVariant, output)
    val postBuildModuleModels = PostBuildModuleModelsMockBuilder().setInstantAppProjectBuildOutput(
      instantAppProjectBuildOutput).build()
    return PostBuildProjectModelsMockBuilder().setPostBuildModuleModels(
      myModule.getGradleProjectPath()!!.path, postBuildModuleModels).build()
  }

  private fun createPostBuildModel(outputs: Collection<File>,
                                   buildVariant: String): PostBuildProjectModels {
    val projectBuildOutput = createProjectBuildOutputMock(buildVariant, outputs)
    val postBuildModuleModels = PostBuildModuleModelsMockBuilder().setProjectBuildOutput(
      projectBuildOutput).build()
    return PostBuildProjectModelsMockBuilder().setPostBuildModuleModels(
      myModule.getGradleProjectPath()!!.path, postBuildModuleModels).build()
  }

  private fun createAppBundleBuildModel(output: File,
                                        buildVariant: String): PostBuildProjectModels {
    val projectBuildOutput = createAppBundleOutputMock(buildVariant, output)
    val postBuildModuleModels = PostBuildModuleModelsMockBuilder().setAppBundleProjectBuildOutput(
      projectBuildOutput).build()
    return PostBuildProjectModelsMockBuilder().setPostBuildModuleModels(
      myModule.getGradleProjectPath()!!.path, postBuildModuleModels).build()
  }

  private class PostBuildModuleModelsMockBuilder {
    private val myPostBuildModuleModels: PostBuildModuleModels = Mockito.mock(PostBuildModuleModels::class.java)
    fun setProjectBuildOutput(projectBuildOutput: ProjectBuildOutput): PostBuildModuleModelsMockBuilder {
      whenever(
        myPostBuildModuleModels.findModel(Mockito.eq(
          ProjectBuildOutput::class.java))).thenReturn(projectBuildOutput)
      return this
    }

    fun setAppBundleProjectBuildOutput(appBundleOutput: AppBundleProjectBuildOutput): PostBuildModuleModelsMockBuilder {
      whenever(
        myPostBuildModuleModels.findModel(Mockito.eq(
          AppBundleProjectBuildOutput::class.java))).thenReturn(appBundleOutput)
      return this
    }

    fun setInstantAppProjectBuildOutput(instantAppProjectBuildOutput: InstantAppProjectBuildOutput): PostBuildModuleModelsMockBuilder {
      whenever(
        myPostBuildModuleModels.findModel(Mockito.eq(
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
      whenever(myPostBuildProjectModels.getModels(Mockito.eq(gradlePath))).thenReturn(
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
      whenever(
        projectBuildOutput.instantAppVariantsBuildOutput).thenReturn(setOf(variantBuildOutput))
      whenever(variantBuildOutput.name).thenReturn(variant)
      whenever(variantBuildOutput.output).thenReturn(outputFile)
      whenever(outputFile.outputFile).thenReturn(file)
      return projectBuildOutput
    }

    private fun createProjectBuildOutputMock(variant: String,
                                             files: Collection<File>): ProjectBuildOutput {
      val projectBuildOutput = Mockito.mock(ProjectBuildOutput::class.java)
      val variantBuildOutput = Mockito.mock(VariantBuildOutput::class.java)
      val outputFiles: MutableList<OutputFile> = ArrayList()
      for (file in files) {
        val outputFile = Mockito.mock(OutputFile::class.java)
        whenever(outputFile.outputFile).thenReturn(file)
        outputFiles.add(outputFile)
      }
      whenever(projectBuildOutput.variantsBuildOutput).thenReturn(
        setOf(variantBuildOutput))
      whenever(variantBuildOutput.name).thenReturn(variant)
      whenever(variantBuildOutput.outputs).thenReturn(outputFiles)
      return projectBuildOutput
    }

    private fun createAppBundleOutputMock(variant: String,
                                          file: File): AppBundleProjectBuildOutput {
      val projectBuildOutput = Mockito.mock(AppBundleProjectBuildOutput::class.java)
      val variantBuildOutput = Mockito.mock(AppBundleVariantBuildOutput::class.java)

      whenever(projectBuildOutput.appBundleVariantsBuildOutput).thenReturn(
        setOf(variantBuildOutput))
      whenever(variantBuildOutput.name).thenReturn(variant)
      whenever(variantBuildOutput.bundleFile).thenReturn(file)
      return projectBuildOutput
    }
  }
}

private fun PostBuildProjectModels.toTestAssembleResult() =
  AssembleInvocationResult(
    GradleMultiInvocationResult(
      listOf(
        GradleInvocationResult(
          rootProjectPath = File("/not-expected-to-matter"),
          tasks = listOf("not-expected-to-matter"),
          buildError = null,
          model = this
        )
      )
    ),
    BuildMode.ASSEMBLE
  )