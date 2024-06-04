// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.aar

import com.android.resources.aar.AarSourceResourceRepository
import com.android.test.testutils.TestUtils
import java.nio.file.Path


object AarTestUtils {
  private const val AAR_LIBRARY_NAME = "com.test:test-library:1.0.0"
  private const val TEST_DATA_DIR = "tools/base/resource-repository/test/resources/aar"

  @JvmOverloads
  @JvmStatic
  fun getTestAarRepositoryFromExplodedAar(libraryDirName: String = "my_aar_lib"): AarSourceResourceRepository {
    return AarSourceResourceRepository.create(
      TestUtils.resolveWorkspacePath("$TEST_DATA_DIR/$libraryDirName/res"),
      AAR_LIBRARY_NAME
    )
  }

  @JvmStatic
  fun resolveAarTestData(relativePath: String): Path {
    val normalizedRelativePath = relativePath.removePrefix("/")
    return TestUtils.resolveWorkspacePath("$TEST_DATA_DIR/$normalizedRelativePath")
  }
}
