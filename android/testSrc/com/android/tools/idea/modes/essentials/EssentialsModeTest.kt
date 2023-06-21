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
  @Test
  fun `setEnabled turns on and off Essentials Mode`(){
    EssentialsMode.setEnabled(false)
    Truth.assertThat(EssentialsMode.isEnabled()).isFalse()

    EssentialsMode.setEnabled(true)
    Truth.assertThat(EssentialsMode.isEnabled()).isTrue()

    EssentialsMode.setEnabled(false)
    Truth.assertThat(EssentialsMode.isEnabled()).isFalse()
  }

}