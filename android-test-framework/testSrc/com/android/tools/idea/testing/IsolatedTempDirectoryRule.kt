// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.testing

import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.io.sanitizeFileName
import org.junit.rules.ExternalResource
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.nio.file.Paths

/**
 * JUnit rule that manages temporary directory cache for tests.
 *
 * This rule resets the canonical temp path cache to a test-specific subdirectory
 * before the test runs and restores the original cache after the test completes.
 * This is useful when tests need isolated temporary directories or when working
 * with [FileUtilRt] functions that rely on canonical temp paths.
 *
 * Usage example:
 * ```
 * @Rule
 * val tempDirCache = TempDirectoryCacheRule()
 * ```
 */
class IsolatedTempDirectoryRule : ExternalResource() {
  private var originalTempDirectory: String? = null
  private var testClassName: String? = null

  override fun apply(base: Statement, description: Description): Statement {
    testClassName = sanitizeFileName(description.className.substringAfterLast('.'))
    return super.apply(base, description)
  }

  override fun before() {
    originalTempDirectory = FileUtilRt.getTempDirectory()
    val testSpecificTempDir = Paths.get(originalTempDirectory!!).resolve(testClassName!!)
    FileUtilRt.resetCanonicalTempPathCache(testSpecificTempDir.toString())
  }

  override fun after() {
    originalTempDirectory?.let { FileUtilRt.resetCanonicalTempPathCache(it) }
    originalTempDirectory = null
  }
}