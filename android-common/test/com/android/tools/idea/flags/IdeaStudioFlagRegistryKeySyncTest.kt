// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.flags

import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.ProjectRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertNotNull

class IdeaStudioFlagRegistryKeySyncTest {
  @get:Rule
  val projectRule = ProjectRule()

  @Test
  fun `test - 'StudioFlags#GRADLE_DECLARATIVE_IDE_SUPPORT' ID is in sync with RegistryKey 'Gradle Declarative Ide Support' ID`() {
    val studioFlagId = StudioFlags.GRADLE_DECLARATIVE_IDE_SUPPORT.id

    assertNotNull(
      Registry.getAll().firstOrNull { it.key == "android.$studioFlagId" },
      "StudioFlags#GRADLE_DECLARATIVE_IDE_SUPPORT id has changed and RegistryKey(key=\"android.gradle.ide.gradle.declarative.ide.support\") has to be updated in Android plugin plugin.xml configuration file."
    )
  }
}