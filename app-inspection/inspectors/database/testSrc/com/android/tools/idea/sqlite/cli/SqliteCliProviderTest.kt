/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.sqlite.cli

import com.android.SdkConstants
import com.android.testutils.OsType
import com.android.tools.idea.sqlite.cli.SqliteCliProvider.Companion.SQLITE3_PATH_ENV
import com.android.tools.idea.sqlite.cli.SqliteCliProvider.Companion.SQLITE3_PATH_PROPERTY
import com.android.tools.idea.sqlite.utils.initAdbFileProvider
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class SqliteCliProviderTest {
  private val projectRule = ProjectRule()

  private val project
    get() = projectRule.project

  private val temporaryFolder = TemporaryFolder()
  @get:Rule val rule = RuleChain(projectRule, temporaryFolder)

  @Before
  fun setUp() {
    initAdbFileProvider(project)
  }

  @After
  fun tearDown() {
    System.clearProperty(SQLITE3_PATH_PROPERTY)
    System.clearProperty(SQLITE3_PATH_ENV)
  }

  @Test
  fun testRealSdk() {
    val actual = SqliteCliProviderImpl(project).getSqliteCli()
    assertThat(actual!!.exists()).isTrue()
    assertThat(actual.isRegularFile()).isTrue()
    assertThat(actual.fileName.toString()).isEqualTo(SdkConstants.FN_SQLITE3)
    assertThat(actual.parent.toFile().name).isEqualTo("platform-tools")
  }

  @Test
  fun testSystemOverride() {
    val fakeSqlite3Env = temporaryFolder.newFile("fake-sqlite3-env").toPath().toFile()
    val fakeSqlite3Property = temporaryFolder.newFile("fake-sqlite3-property").toPath().toFile()

    val propertyResolver: (key: String) -> String? = { key ->
      if (key == SQLITE3_PATH_PROPERTY) fakeSqlite3Property.path else ""
    }
    val envResolver: (key: String) -> String? = { key ->
      if (key == SQLITE3_PATH_ENV) fakeSqlite3Env.path else ""
    }
    val nullResolver: (key: String) -> String? = { null }

    // test env
    val actual1 = SqliteCliProviderImpl(project).getSqliteCli(nullResolver, envResolver)
    assertThat(actual1!!.toFile().canonicalPath).isEqualTo(fakeSqlite3Env.canonicalPath)

    // test property (and override precedence)
    val actual2 = SqliteCliProviderImpl(project).getSqliteCli(propertyResolver, envResolver)
    assertThat(actual2!!.toFile().canonicalPath).isEqualTo(fakeSqlite3Property.canonicalPath)
  }

  @Test
  fun testExtension() {
    val actual = SqliteCliProviderImpl(project).getSqliteCli()
    val expectedExtension = if (OsType.getHostOs() == OsType.WINDOWS) "exe" else ""
    assertThat(actual!!.toFile().extension).isEqualTo(expectedExtension)
  }
}
