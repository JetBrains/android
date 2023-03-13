/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.compose.code.completion

import com.android.ide.common.vectordrawable.VdIcon
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.MaterialVdIconsProvider
import com.android.tools.idea.material.icons.MaterialVdIcons
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.invocation.InvocationOnMock
import java.awt.image.BufferedImage

class ComposeMaterialIconServiceTest {

  private val iconLoader: IconLoader = mock()
  private val service: ComposeMaterialIconService = ComposeMaterialIconService(iconLoader)

  private var callback: ((MaterialVdIcons, MaterialVdIconsProvider.Status) -> Unit)? = null

  @Before
  fun setup() {
    whenever(iconLoader.invoke(any(), any())).then(this::storeCallback)
  }

  private fun storeCallback(invocation: InvocationOnMock) {
    this.callback = invocation.arguments[0] as (MaterialVdIcons, MaterialVdIconsProvider.Status) -> Unit
  }

  @Test
  fun ensureIconsLoaded_loadIsInvokedOnce() {
    service.ensureIconsLoaded()
    service.ensureIconsLoaded()
    service.ensureIconsLoaded()
    service.ensureIconsLoaded()
    service.ensureIconsLoaded()

    verify(iconLoader, times(1)).invoke(any(), any())
  }

  @Test
  fun getIcon_invokesLoadOnFirstCall() {
    assertThat(service.getIcon("someFileName")).isNull()
    verify(iconLoader, times(1)).invoke(any(), any())
  }

  @Test
  fun getIcon_doesNotInvokeLoadOnSecondCall() {
    assertThat(service.getIcon("someFileName")).isNull()
    verify(iconLoader, times(1)).invoke(any(), any())
  }

  @Test
  fun getIcon_returnsIconAfterCallback() {
    val mockMaterialVdIcons: MaterialVdIcons = mock()
    val mockStyle1VdIcon: VdIcon = mock()
    val mockStyle1BufferedImage: BufferedImage = mock()

    whenever(mockMaterialVdIcons.styles).thenReturn(arrayOf("style1"))
    whenever(mockMaterialVdIcons.getAllIcons("style1")).thenReturn(arrayOf(mockStyle1VdIcon))
    whenever(mockStyle1VdIcon.name).thenReturn("mockStyle1VdIcon.xml")
    whenever(mockStyle1VdIcon.renderIcon(16, 16)).thenReturn(mockStyle1BufferedImage)

    service.ensureIconsLoaded()

    // Simulate callbacks
    callback!!.invoke(mockMaterialVdIcons, MaterialVdIconsProvider.Status.FINISHED)

    // We should now get the icon back, but it should only be rendered once.
    assertThat(service.getIcon("mockStyle1VdIcon.xml")).isNotNull()
    assertThat(service.getIcon("mockStyle1VdIcon.xml")).isNotNull()
    assertThat(service.getIcon("mockStyle1VdIcon.xml")).isNotNull()

    verify(mockStyle1VdIcon, times(1)).renderIcon(anyInt(), anyInt())
  }

  @Test
  fun getIcon_iconsUpdateAfterMultipleCallbacks() {
    val mockMaterialVdIconsBatch1: MaterialVdIcons = mock()
    val mockStyle1VdIcon: VdIcon = mock()
    val mockStyle1BufferedImage: BufferedImage = mock()

    whenever(mockMaterialVdIconsBatch1.styles).thenReturn(arrayOf("style1", "style2"))
    whenever(mockMaterialVdIconsBatch1.getAllIcons("style1")).thenReturn(arrayOf(mockStyle1VdIcon))
    whenever(mockMaterialVdIconsBatch1.getAllIcons("style2")).thenReturn(arrayOf())
    whenever(mockStyle1VdIcon.name).thenReturn("mockStyle1VdIcon.xml")
    whenever(mockStyle1VdIcon.renderIcon(16, 16)).thenReturn(mockStyle1BufferedImage)

    val mockMaterialVdIconsBatch2: MaterialVdIcons = mock()
    val mockStyle2VdIcon: VdIcon = mock()
    val mockStyle2BufferedImage: BufferedImage = mock()

    whenever(mockMaterialVdIconsBatch2.styles).thenReturn(arrayOf("style1", "style2"))
    whenever(mockMaterialVdIconsBatch2.getAllIcons("style1")).thenReturn(arrayOf(mockStyle1VdIcon))
    whenever(mockMaterialVdIconsBatch2.getAllIcons("style2")).thenReturn(arrayOf(mockStyle2VdIcon))
    whenever(mockStyle2VdIcon.name).thenReturn("mockStyle2VdIcon.xml")
    whenever(mockStyle2VdIcon.renderIcon(16, 16)).thenReturn(mockStyle2BufferedImage)

    service.ensureIconsLoaded()

    // Simulate callback with batch 1
    callback!!.invoke(mockMaterialVdIconsBatch1, MaterialVdIconsProvider.Status.LOADING)

    assertThat(service.getIcon("mockStyle1VdIcon.xml")).isNotNull()
    assertThat(service.getIcon("mockStyle2VdIcon.xml")).isNull()

    // Simulate callback with batch 2
    callback!!.invoke(mockMaterialVdIconsBatch2, MaterialVdIconsProvider.Status.FINISHED)

    assertThat(service.getIcon("mockStyle1VdIcon.xml")).isNotNull()
    assertThat(service.getIcon("mockStyle2VdIcon.xml")).isNotNull()
  }
}
