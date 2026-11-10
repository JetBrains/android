// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.npw.startup

import com.android.tools.idea.wizard.template.FormFactor
import com.google.common.truth.Truth.assertThat
import com.intellij.util.xmlb.XmlSerializer
import org.junit.Test

class PromotionTemplateStateServiceTest {
  @Test
  fun serializedRequestReopensOnceAndPreservesTheSelection() {
    val service = PromotionTemplateStateService()
    service.requestNpwReopenOnNextStartup("com.jetbrains.kmm", "Kotlin Multiplatform", FormFactor.Generic)
    assertThat(service.consumeNpwReopenRequestAfterRestart()).isFalse()
    assertThat(service.consumePromotedTemplate()).isNull()
    val restarted = reload(service)
    restarted.markRestartedIfNpwReopenPending()

    assertThat(restarted.consumeNpwReopenRequestAfterRestart()).isTrue()
    assertThat(restarted.consumeNpwReopenRequestAfterRestart()).isFalse()
    assertThat(restarted.consumePromotedTemplate())
      .isEqualTo(PromotedTemplate("Kotlin Multiplatform", FormFactor.Generic, "com.jetbrains.kmm"))
    assertThat(restarted.consumePromotedTemplate()).isNull()

    val nextSession = reload(restarted)
    nextSession.markRestartedIfNpwReopenPending()
    assertThat(nextSession.consumeNpwReopenRequestAfterRestart()).isFalse()
    assertThat(nextSession.consumePromotedTemplate()).isNull()
  }

  private fun reload(service: PromotionTemplateStateService): PromotionTemplateStateService =
    PromotionTemplateStateService().apply {
      loadState(XmlSerializer.deserialize(XmlSerializer.serialize(service.state), PromotionTemplateStateService.State::class.java))
    }
}
