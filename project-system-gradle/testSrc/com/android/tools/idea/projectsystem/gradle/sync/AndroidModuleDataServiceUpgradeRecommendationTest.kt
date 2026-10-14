// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.projectsystem.gradle.sync

import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_AGP_VERSION_UPDATED
import com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_MODIFIED
import com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_VARIANT_SELECTION_CHANGED_BY_USER
import org.junit.Test

class AndroidModuleDataServiceUpgradeRecommendationTest {

  @Test
  fun `recommends the plugin upgrade after an IntelliJ import that left no sync request`() {
    assertThat(recommendsPluginUpgradeAfterImport(syncTrigger = null, isAndroidStudio = false)).isTrue()
  }

  @Test
  fun `does not recommend the plugin upgrade in Android Studio without a sync request`() {
    assertThat(recommendsPluginUpgradeAfterImport(syncTrigger = null, isAndroidStudio = true)).isFalse()
  }

  @Test
  fun `recommends the plugin upgrade after a requested sync in both IDEs`() {
    assertThat(recommendsPluginUpgradeAfterImport(TRIGGER_PROJECT_MODIFIED, isAndroidStudio = true)).isTrue()
    assertThat(recommendsPluginUpgradeAfterImport(TRIGGER_PROJECT_MODIFIED, isAndroidStudio = false)).isTrue()
  }

  @Test
  fun `does not recommend the plugin upgrade after an AGP upgrade or a variant change`() {
    assertThat(recommendsPluginUpgradeAfterImport(TRIGGER_AGP_VERSION_UPDATED, isAndroidStudio = false)).isFalse()
    assertThat(recommendsPluginUpgradeAfterImport(TRIGGER_VARIANT_SELECTION_CHANGED_BY_USER, isAndroidStudio = false)).isFalse()
  }
}
