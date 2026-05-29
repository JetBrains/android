/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.projectview.parser

import com.google.common.truth.Truth.assertThat
import com.google.idea.blaze.base.BlazeTestCase
import com.google.idea.blaze.base.projectview.ProjectViewStorageManager
import com.google.idea.blaze.base.projectview.section.sections.EnableWorkspaceSwitcherSection
import com.google.idea.blaze.base.scope.BlazeContext
import java.io.File
import java.util.Optional
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class EnableWorkspaceSwitcherSectionTest : BlazeTestCase() {

  private lateinit var projectViewParser: ProjectViewParser
  private lateinit var context: BlazeContext
  private lateinit var projectViewStorageManager: ProjectViewParserTest.MockProjectViewStorageManager

  override fun initTest(applicationServices: Container, projectServices: Container) {
    super.initTest(applicationServices, projectServices)
    context = BlazeContext.create()
    projectViewParser = ProjectViewParser(context, null)
    projectViewStorageManager = ProjectViewParserTest.MockProjectViewStorageManager()
    applicationServices.register(ProjectViewStorageManager::class.java, projectViewStorageManager)
  }

  @Test
  fun testParseTrue() {
    projectViewStorageManager.add(".blazeproject", "enable_workspace_switcher: true")
    projectViewParser.parseProjectViewFile(File(".blazeproject"), listOf(EnableWorkspaceSwitcherSection.PARSER))

    val projectViewSet = projectViewParser.result
    assertThat(projectViewSet.getScalarValue(EnableWorkspaceSwitcherSection.KEY)).isEqualTo(Optional.of(true))
  }

  @Test
  fun testParseFalse() {
    projectViewStorageManager.add(".blazeproject", "enable_workspace_switcher: false")
    projectViewParser.parseProjectViewFile(File(".blazeproject"), listOf(EnableWorkspaceSwitcherSection.PARSER))

    val projectViewSet = projectViewParser.result
    assertThat(projectViewSet.getScalarValue(EnableWorkspaceSwitcherSection.KEY)).isEqualTo(Optional.of(false))
  }

  @Test
  fun testParseInvalidReturnsEmpty() {
    projectViewStorageManager.add(".blazeproject", "enable_workspace_switcher: invalid")
    projectViewParser.parseProjectViewFile(File(".blazeproject"), listOf(EnableWorkspaceSwitcherSection.PARSER))

    val projectViewSet = projectViewParser.result
    assertThat(projectViewSet.getScalarValue(EnableWorkspaceSwitcherSection.KEY)).isEqualTo(Optional.empty<Boolean>())
  }
}
