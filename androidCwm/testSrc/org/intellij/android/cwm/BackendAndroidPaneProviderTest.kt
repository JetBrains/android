// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.android.cwm

import com.android.tools.idea.navigator.AndroidProjectViewPane
import org.junit.Assert
import org.junit.Test

class BackendAndroidPaneProviderTest {
  @Test
  fun testPanelId() {
    Assert.assertEquals("BackendAndroidPaneProvider.panelId should match AndroidProjectViewPane.ID",
                        AndroidProjectViewPane.ID, BackendAndroidPaneProvider.panelId)
  }
}