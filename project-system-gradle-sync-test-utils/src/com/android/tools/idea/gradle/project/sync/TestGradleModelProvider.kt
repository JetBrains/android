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
package com.android.tools.idea.gradle.project.sync

import org.gradle.api.Project
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider.GradleModelConsumer
import org.jetbrains.plugins.gradle.tooling.Message
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService
import java.io.Serializable

enum class TestGradleModelProviderMode {
  IDE_MODELS,
  TEST_GRADLE_MODELS,
  TEST_EXCEPTION_MODELS
}

class TestGradleModelProvider(private val paramValue: String, val mode: TestGradleModelProviderMode) : ProjectImportModelProvider {

  override fun populateProjectModels(
    controller: BuildController,
    projectModel: BasicGradleProject,
    modelConsumer: GradleModelConsumer
  ) {
    when (mode) {
      TestGradleModelProviderMode.IDE_MODELS -> Unit
      TestGradleModelProviderMode.TEST_GRADLE_MODELS -> {
        val testGradleModel = controller.findModel(projectModel, TestGradleModel::class.java)
        if (testGradleModel != null) {
          modelConsumer.consumeProjectModel(projectModel, testGradleModel, TestGradleModel::class.java)
        }

        val testParameterizedGradleModel =
          controller.findModel(
            projectModel,
            TestParameterizedGradleModel::class.java,
            ModelBuilderService.Parameter::class.java
          ) { parameter ->
            parameter.value = paramValue
          }
        if (testParameterizedGradleModel != null) {
          modelConsumer.consumeProjectModel(projectModel, testParameterizedGradleModel, TestParameterizedGradleModel::class.java)
        }
      }

      TestGradleModelProviderMode.TEST_EXCEPTION_MODELS -> {
        val testExceptionModel = controller.findModel(projectModel, TestExceptionModel::class.java)
        if (testExceptionModel != null) {
          modelConsumer.consumeProjectModel(projectModel, testExceptionModel, TestExceptionModel::class.java)
        }
      }
    }
  }
}

interface TestGradleModel {
  val message: String
}

interface TestParameterizedGradleModel {
  val message: String
}

interface TestExceptionModel {
  val exception: Throwable
}

data class TestGradleModelImpl(override val message: String) : TestGradleModel, Serializable
data class TestParameterizedGradleModelImpl(override val message: String) : TestParameterizedGradleModel, Serializable
data class TestExceptionModelImpl(override val exception: Throwable) : TestExceptionModel, Serializable

class TestModelBuilderService : ModelBuilderService {
  override fun canBuild(modelName: String?): Boolean {
    return modelName == TestGradleModel::class.java.name ||
           modelName == TestExceptionModel::class.java.name
  }

  override fun buildAll(modelName: String?, project: Project?): Any {
    return when (modelName) {
      TestGradleModel::class.java.name -> TestGradleModelImpl("Hello, ${project?.buildDir}")
      TestExceptionModel::class.java.name -> TestExceptionModelImpl(kotlin.runCatching { error("expected error") }.exceptionOrNull()!!)
      else -> error("Unexpected model name: $modelName")
    }
  }

  override fun reportErrorMessage(modelName: String, project: Project, context: ModelBuilderContext, exception: Exception) {
    context.messageReporter.createMessage()
      .withGroup(this)
      .withKind(Message.Kind.WARNING)
      .withTitle("Gradle import errors")
      .withException(exception)
      .reportMessage(project)
  }
}

class TestParameterizedModelBuilderService : ModelBuilderService.ParameterizedModelBuilderService {
  override fun canBuild(modelName: String?): Boolean {
    return modelName == TestParameterizedGradleModel::class.java.name
  }

  override fun buildAll(
    modelName: String?,
    project: Project?,
    context: ModelBuilderContext,
    parameter: ModelBuilderService.Parameter?
  ): Any {
    return TestParameterizedGradleModelImpl("Parameter: ${parameter?.value} BuildDir: ${project?.buildDir}")
  }

  override fun buildAll(modelName: String?, project: Project?): Any {
    return TestParameterizedGradleModelImpl("Hello, ${project?.buildDir}")
  }

  override fun reportErrorMessage(modelName: String, project: Project, context: ModelBuilderContext, exception: Exception) {
    context.messageReporter.createMessage()
      .withGroup(this)
      .withKind(Message.Kind.WARNING)
      .withTitle("Gradle import errors")
      .withException(exception)
      .reportMessage(project)
  }
}