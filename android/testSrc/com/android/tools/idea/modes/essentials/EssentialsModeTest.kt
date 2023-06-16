package com.android.tools.idea.modes.essentials

import com.android.tools.idea.flags.StudioFlags
import com.google.common.truth.Truth
import com.intellij.ide.EssentialHighlightingMode
import com.intellij.testFramework.LightPlatform4TestCase
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class EssentialsModeTest : LightPlatform4TestCase() {

  @Before
  fun setup() {
    EssentialsMode.setEnabled(false)
    EssentialHighlightingMode.setEnabled(false)
  }


  @Test
  fun `setEnabled true enables Essentials Mode with Essential Highlighting`() {
    StudioFlags.ESSENTIALS_HIGHLIGHTING_MODE.override(true)
    Truth.assertThat(EssentialsMode.isEnabled()).isFalse()
    Truth.assertThat(EssentialHighlightingMode.isEnabled()).isFalse()

    EssentialsMode.setEnabled(true)

    Truth.assertThat(EssentialHighlightingMode.isEnabled()).isTrue()
    Truth.assertThat(EssentialsMode.isEnabled()).isTrue()
  }

  @Test
  fun `setEnabled true enables Essentials Mode without Essential Highlighting`() {
    StudioFlags.ESSENTIALS_HIGHLIGHTING_MODE.override(false)
    Truth.assertThat(EssentialsMode.isEnabled()).isFalse()
    Truth.assertThat(EssentialHighlightingMode.isEnabled()).isFalse()

    EssentialsMode.setEnabled(true)

    Truth.assertThat(EssentialHighlightingMode.isEnabled()).isFalse()
    Truth.assertThat(EssentialsMode.isEnabled()).isTrue()
  }

  @Test
  fun `setEnabled false turn off Essentials Mode and highlighting without flag`() {
    EssentialHighlightingMode.setEnabled(true)
    StudioFlags.ESSENTIALS_HIGHLIGHTING_MODE.override(false)

    Truth.assertThat(EssentialsMode.isEnabled()).isFalse()
    Truth.assertThat(EssentialHighlightingMode.isEnabled()).isTrue()

    EssentialsMode.setEnabled(false)

    Truth.assertThat(EssentialHighlightingMode.isEnabled()).isFalse()
    Truth.assertThat(EssentialsMode.isEnabled()).isFalse()
  }
}