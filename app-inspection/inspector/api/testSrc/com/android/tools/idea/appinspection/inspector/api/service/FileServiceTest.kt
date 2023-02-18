package com.android.tools.idea.appinspection.inspector.api.service

import com.google.common.truth.Truth.assertThat
import com.intellij.util.io.isDirectory
import org.junit.Test

class FileServiceTest {
  private val fileService = TestFileService()

  @Test
  fun testDirCreation() {
    val cachePath = fileService.getOrCreateCacheDir("x")
    val tmpPath = fileService.getOrCreateTempDir("x")

    assertThat(cachePath.isDirectory()).isTrue()
    assertThat(tmpPath.isDirectory()).isTrue()

    assertThat(cachePath.fileName).isEqualTo(tmpPath.fileName)
    assertThat(cachePath).isNotEqualTo(tmpPath)
    assertThat(cachePath).isEqualTo(fileService.getOrCreateCacheDir("x"))
    assertThat(cachePath).isNotEqualTo(fileService.getOrCreateCacheDir("y"))
  }
}
