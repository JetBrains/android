/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.nav.safeargs.kotlin.k2

import com.android.tools.idea.nav.safeargs.SafeArgsMode
import com.android.tools.idea.nav.safeargs.SafeArgsRule
import com.android.tools.idea.nav.safeargs.safeArgsMode
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncReason
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult
import com.android.tools.idea.projectsystem.getSyncManager
import com.android.tools.idea.testing.KotlinPluginRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.messages.Topic
import com.intellij.util.ui.EDT
import org.jetbrains.kotlin.analysis.api.platform.analysisMessageBus
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModificationTopics.GLOBAL_SOURCE_OUT_OF_BLOCK_MODIFICATION
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModificationTopics.MODULE_STATE_MODIFICATION
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModuleStateModificationKind
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.util.toKaModulesForModificationEvents
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class ChangeListenerProjectServiceTest {
  private val safeArgsRule = SafeArgsRule(SafeArgsMode.KOTLIN)

  @get:Rule
  val ruleChain = RuleChain.outerRule(KotlinPluginRule(KotlinPluginMode.K2)).around(safeArgsRule)

  private inline fun <reified T : Any> withAnalysisBusListener(
    topic: Topic<T>,
    block: (T) -> Unit,
  ) {
    val disposable = Disposer.newDisposable()
    try {
      val listener = mock<T>()
      safeArgsRule.project.analysisMessageBus.connect(disposable).subscribe(topic, listener)
      block(listener)
    } finally {
      Disposer.dispose(disposable)
    }
  }

  @Before
  fun setUp() {
    ChangeListenerProjectService.ensureListening(safeArgsRule.project)
  }

  @Test
  fun `fires module OOB for module SafeArgs mode change`() =
    withAnalysisBusListener(MODULE_STATE_MODIFICATION) { listener ->
      safeArgsRule.androidFacet.safeArgsMode = SafeArgsMode.JAVA
      runInEdtAndWait { EDT.dispatchAllInvocationEvents() }
      safeArgsRule.module.toKaModulesForModificationEvents().forEach {
        verify(listener).onModification(it, KotlinModuleStateModificationKind.UPDATE)
      }
    }

  @Test
  fun `fires global source change for completed project sync`() =
    withAnalysisBusListener(GLOBAL_SOURCE_OUT_OF_BLOCK_MODIFICATION) { listener ->
      val future = safeArgsRule.project.getSyncManager().syncProject(SyncReason.USER_REQUEST)
      val result = future.get()
      assertThat(result).isNoneOf(SyncResult.FAILURE, SyncResult.CANCELLED, SyncResult.UNKNOWN)
      runInEdtAndWait { EDT.dispatchAllInvocationEvents() }
      verify(listener).onModification()
    }
}
