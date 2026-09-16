/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.run

import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
import com.google.idea.blaze.base.BlazeTestCase
import com.google.idea.blaze.base.dependencies.TargetInfo
import com.google.idea.blaze.base.model.primitives.Kind
import com.google.idea.blaze.base.model.primitives.Label
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandlerProvider
import com.google.idea.blaze.base.run.targetfinder.TargetFinder
import com.google.idea.blaze.base.settings.BlazeImportSettings
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager
import com.google.idea.blaze.base.settings.BuildSystemName
import com.google.idea.common.experiments.ExperimentService
import com.google.idea.common.experiments.MockExperimentService
import com.intellij.openapi.project.Project
import java.util.concurrent.Future
import org.jdom.Element
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Tests for [BlazeCommandRunConfiguration]. */
@RunWith(JUnit4::class)
class BlazeCommandRunConfigurationTest : BlazeTestCase() {
  private val type = BlazeCommandRunConfigurationType()
  private lateinit var configuration: BlazeCommandRunConfiguration

  override fun initTest(applicationServices: Container, projectServices: Container) {
    super.initTest(applicationServices, projectServices)

    projectServices.register(BlazeImportSettingsManager::class.java, BlazeImportSettingsManager(project))
    BlazeImportSettingsManager.getInstance(project).importSettings = DUMMY_IMPORT_SETTINGS

    applicationServices.register(ExperimentService::class.java, MockExperimentService())
    registerExtensionPoint(Kind.Provider.EP_NAME, Kind.Provider::class.java)
    applicationServices.register(Kind.ApplicationState::class.java, Kind.ApplicationState())

    val targetFinderEp = registerExtensionPoint(TargetFinder.EP_NAME, TargetFinder::class.java)
    targetFinderEp.registerExtension(MockTargetFinder())

    val handlerProviderEp =
      registerExtensionPoint(BlazeCommandRunConfigurationHandlerProvider.EP_NAME, BlazeCommandRunConfigurationHandlerProvider::class.java)
    handlerProviderEp.registerExtension(MockBlazeCommandRunConfigurationHandlerProvider())

    this.configuration = this.type.factory.createTemplateConfiguration(project) as BlazeCommandRunConfiguration
  }

  @Test
  fun readAndWriteShouldMatch() {
    val label = Label.create("//package:rule")
    configuration.setTargetPattern(label.toString())

    val element = Element("test")
    configuration.writeExternal(element)
    val readConfiguration = type.factory.createTemplateConfiguration(project) as BlazeCommandRunConfiguration
    readConfiguration.readExternal(element)

    assertThat(readConfiguration.targetPatterns).containsExactly(label.toString())
  }

  @Test
  fun readAndWriteShouldHandleNulls() {
    val element = Element("test")
    configuration.writeExternal(element)
    val readConfiguration = type.factory.createTemplateConfiguration(project) as BlazeCommandRunConfiguration
    readConfiguration.readExternal(element)

    assertThat(readConfiguration.targetPatterns).isEqualTo(configuration.targetPatterns)
  }

  private class MockTargetFinder : TargetFinder {
    override fun findTarget(project: Project, label: com.google.idea.blaze.common.Label): Future<TargetInfo?> {
      return Futures.immediateFuture(null)
    }
  }

  companion object {
    private val DUMMY_IMPORT_SETTINGS = BlazeImportSettings("", "", "", "", "", BuildSystemName.Blaze)
  }
}
