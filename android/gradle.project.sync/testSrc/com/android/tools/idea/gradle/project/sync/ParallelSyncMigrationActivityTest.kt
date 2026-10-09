// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.gradle.project.sync

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.JavaModuleModelBuilder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ParallelSyncMigrationActivityTest {

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels(JavaModuleModelBuilder.rootModuleBuilder, JavaModuleModelBuilder(":lib"))

  @Test
  fun migrationIsNotApplicableToGradleProjectWithoutAndroidModules() {
    assertFalse(ParallelSyncMigrationActivity.isMigrationApplicable(projectRule.project))
  }
}

class ParallelSyncMigrationActivityAndroidProjectTest {

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModel()

  @Test
  fun migrationIsApplicableToGradleProjectWithAndroidModules() {
    assertTrue(ParallelSyncMigrationActivity.isMigrationApplicable(projectRule.project))
  }
}
