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
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.util.io.exists
import com.intellij.util.io.isFile

class SqliteCliProviderTest : LightPlatformTestCase() {
  private lateinit var tempDirTestFixture: TempDirTestFixture

  override fun setUp() {
    super.setUp()
    tempDirTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture()
    initAdbFileProvider(project)
    tempDirTestFixture.setUp()
  }

  override fun tearDown() {
    System.clearProperty(SQLITE3_PATH_PROPERTY)
    System.clearProperty(SQLITE3_PATH_ENV)
    tempDirTestFixture.tearDown()
    super.tearDown()
  }

  fun testRealSdk() {
    val actual = SqliteCliProviderImpl(project).getSqliteCli()
    assertThat(actual!!.exists()).isTrue()
    assertThat(actual.isFile()).isTrue()
    assertThat(actual.fileName.toString()).isEqualTo(SdkConstants.FN_SQLITE3)
    assertThat(actual.parent.toFile().name).isEqualTo("platform-tools")
  }

  fun testSystemOverride() {
    val fakeSqlite3Env = tempDirTestFixture.createFile("fake-sqlite3-env").toNioPath().toFile()
    val fakeSqlite3Property =
      tempDirTestFixture.createFile("fake-sqlite3-property").toNioPath().toFile()

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

  fun testExtension() {
    val actual = SqliteCliProviderImpl(project).getSqliteCli()
    val expectedExtension = if (OsType.getHostOs() == OsType.WINDOWS) "exe" else ""
    assertThat(actual!!.toFile().extension).isEqualTo(expectedExtension)
  }
}
