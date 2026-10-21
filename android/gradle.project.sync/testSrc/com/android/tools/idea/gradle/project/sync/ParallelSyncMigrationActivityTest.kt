// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.gradle.project.sync

import com.android.tools.idea.IdeInfo
import com.android.tools.idea.testing.AndroidProjectRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ParallelSyncMigrationActivityTest {

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModel()

  @Test
  fun migrationIsNotApplicableOutsideAndroidStudioEvenInAndroidProject() {
    assertFalse(IdeInfo.getInstance().isAndroidStudio)
    assertFalse(ParallelSyncMigrationActivity.isMigrationApplicable())
  }

  @Test
  fun migrationIsApplicableInAndroidStudio() {
    val ideInfo = mock<IdeInfo>()
    whenever(ideInfo.isAndroidStudio).thenReturn(true)
    projectRule.replaceService(IdeInfo::class.java, ideInfo)

    assertTrue(ParallelSyncMigrationActivity.isMigrationApplicable())
  }
}
