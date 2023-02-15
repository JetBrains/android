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
import com.android.tools.idea.adb.AdbFileProvider
import com.android.tools.idea.sqlite.cli.SqliteCliProvider.Companion.SQLITE3_PATH_ENV
import com.android.tools.idea.sqlite.cli.SqliteCliProvider.Companion.SQLITE3_PATH_PROPERTY
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.Strings
import java.io.File
import java.nio.file.Path
import org.jetbrains.annotations.TestOnly

/** Service locating an instance of the `sqlite3` CLI tool (from Android platform-tools) */
interface SqliteCliProvider {
  companion object {
    /** System property allowing for a local override of the `sqlite3` tool location */
    const val SQLITE3_PATH_PROPERTY = "android.sqlite3.path"
    /** System env-variable allowing for a local override of the `sqlite3` tool location */
    const val SQLITE3_PATH_ENV = "ANDROID_SQLITE3_PATH"
  }

  /**
   * Searches for `sqlite3` CLI tool (part of platform-tools). Respects overriding the path through
   * [SQLITE3_PATH_PROPERTY] system property and [SQLITE3_PATH_ENV] environment variable.
   * @return path to the tool if able to find it. Otherwise `null`.
   */
  fun getSqliteCli(): Path?
}

/** Locates an instance of the `sqlite3` CLI tool (from Android platform-tools) */
class SqliteCliProviderImpl(private val project: Project) : SqliteCliProvider {
  private val logger = logger<SqliteCliProviderImpl>()

  override fun getSqliteCli(): Path? {
    return getSqliteCli({ key -> System.getProperty(key) }, { key -> System.getenv(key) })
  }

  /**
   * Overload targeted for testing (allows for injecting system env which otherwise is not possible
   * from runtime)
   */
  @TestOnly
  fun getSqliteCli(
    systemPropertyResolver: (key: String) -> String?,
    systemEnvResolver: (key: String) -> String?
  ): Path? {
    // check system property/env overrides
    val overrideFile =
      listOf(systemPropertyResolver(SQLITE3_PATH_PROPERTY), systemEnvResolver(SQLITE3_PATH_ENV))
        .filter { Strings.isNotEmpty(it) }
        .map { File(it!!) }
        .firstOrNull {
          logCheckingSqlite3(it.toPath())
          it.exists()
        }
    if (overrideFile != null) return overrideFile.toPath().also { logFoundSqlite3(it) }

    // check adb location - sqlite3 is its sibling in platform-tools
    val adbFile = AdbFileProvider.fromProject(project).get()
    if (adbFile == null) {
      logUnableToFind("adb")
      return null
    }
    logAny("Adb location: ${adbFile.toPath()}")
    logAny("Adb neighbours: ${adbFile.parentFile.listFiles()?.map { it.toPath() }}")
    val adbSibling = adbFile.toPath().parent.resolve(SdkConstants.FN_SQLITE3).toFile()

    logCheckingSqlite3(adbSibling.toPath())
    return if (adbSibling.exists()) {
      adbSibling.toPath().also { logFoundSqlite3(it) }
    } else {
      logAny("Adb location: ${adbFile.toPath()}")
      logAny("Adb neighbours: ${adbFile.parentFile.listFiles()?.map { it.toPath() }}")
      logUnableToFind("sqlite3")
      null
    }
  }

  private fun logCheckingSqlite3(path: Path) {
    val s = "Checking ${path.toAbsolutePath()} for sqlite3"
    logger.info(s)
  }

  private fun logFoundSqlite3(path: Path) {
    val s = "Located sqlite3 under ${path.toAbsolutePath()}"
    logger.info(s)
  }

  private fun logUnableToFind(what: String) {
    val s = "Unable to locate $what file"
    logger.error(s)
  }

  private fun logAny(s: String) {
    logger.debug(s)
  }
}
