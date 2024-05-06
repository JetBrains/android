package com.android.tools.idea.gradle.structure.configurables.ui.modules

import com.android.tools.idea.gradle.structure.configurables.ui.modules.SigningConfigsPanel.Companion.isNonDebugSelected
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.ui.NamedConfigurable
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class SigningConfigsPanelTest {
  @Test
  fun `Rename and remove are not enabled for debug`() {
    val configurable = mock(NamedConfigurable::class.java)
    `when`(configurable.displayName).thenReturn("debug")
    assertThat(isNonDebugSelected(configurable)).isFalse()
  }

  @Test
  fun `Rename and remove are not enabled for non selected configurable`() {
    assertThat(isNonDebugSelected(null)).isFalse()
  }

  @Test
  fun `Rename and remove are enabled for non debug`() {
    val configurable = mock(NamedConfigurable::class.java)
    `when`(configurable.displayName).thenReturn("name")
    assertThat(isNonDebugSelected(configurable)).isTrue()
  }
}