/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.dcl.lang.ide

import com.android.test.testutils.TestUtils
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@org.junit.Ignore("b/349894866")
@RunWith(JUnit4::class)
class DeclarativeServiceTest {
  @get:Rule
  val projectRule = AndroidProjectRule.onDisk()
  private val fixture by lazy {
    projectRule.fixture.apply {
      testDataPath = TestUtils.resolveWorkspacePath("tools/adt/idea/project-system-gradle/testData/declarative").toString()
    }
  }

  @Before
  fun onBefore(){
    DeclarativeIdeSupport.override(true)
  }

  @After
  fun onAfter(){
    DeclarativeIdeSupport.clearOverride()
  }

  @Test
  fun `read random schema files from predefined folder`() {
    fixture.copyFileToProject("newFormatSchemas/project.dcl.schema", ".gradle/declarative-schema/random.dcl.schema")
    val service = DeclarativeService(projectRule.project)
    val schema = service.getSchema()
    Truth.assertThat(schema).isNotNull()
  }

  @Test
  fun doNotFailIfNoSchemas() {
    val service = DeclarativeService(projectRule.project)
    val schema = service.getSchema()
    Truth.assertThat(schema).isNull()
  }

  @Test
  fun returnSchemaWithFlagIfAnySchemaIsBad() {
    fixture.copyFileToProject("settingsSchemas/settings.dcl.schema", ".gradle/declarative-schema/settings.dcl.schema")
    fixture.copyFileToProject("oldSchema/project.dcl.schema", ".gradle/declarative-schema/project.dcl.schema")

    val service = DeclarativeService(projectRule.project)
    val schema = service.getSchema()
    Truth.assertThat(schema).isNotNull()
    Truth.assertThat(schema!!.failureHappened).isTrue()
  }

}