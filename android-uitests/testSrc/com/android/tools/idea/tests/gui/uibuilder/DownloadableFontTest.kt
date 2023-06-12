/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.uibuilder

import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture
import com.android.tools.idea.tests.gui.framework.fixture.MoreFontsDialogFixture
import com.android.tools.idea.tests.gui.framework.fixture.properties.PTableFixture
import com.google.common.truth.Truth.*
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.fest.swing.core.MouseButton
import org.fest.swing.data.TableCell
import org.fest.swing.timing.Wait
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.awt.event.KeyEvent
import java.util.concurrent.TimeUnit

@RunWith(GuiTestRemoteRunner::class)
class DownloadableFontTest {
  @JvmField
  @Rule
  val guiTest = GuiTestRule().withTimeout(5, TimeUnit.MINUTES)

  @JvmField
  @Rule
  val renderTaskLeakCheckRule = RenderTaskLeakCheckRule()

  @Test
  @Throws(Exception::class)
  fun downloadableFontTest() {
    val frame = guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleApplication", Wait.seconds(120))
    val editorFixture = frame.editor.open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN)
      .layoutEditor
      .waitForSurfaceToLoad()
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    assertThat(editorFixture.canInteractWithSurface()).isTrue()

    editorFixture.componentTree.clickPath("RelativeLayout/TextView")

    val declaredAttributesSection = editorFixture
      .attributesPanel
      .waitForId("textview")
      .findSectionByName("Declared Attributes")!!

    declaredAttributesSection
      .title!!
      .addProperty()
    guiTest.robot().typeText("fontFamily")
    guiTest.robot().pressAndReleaseKey(KeyEvent.VK_ENTER)

    val declaredAttributesTable = declaredAttributesSection.components.first() as PTableFixture
    val row = declaredAttributesTable.findRowOf("fontFamily")
    declaredAttributesTable.click(TableCell.row(row).column(1), MouseButton.LEFT_BUTTON)
    // Open the dropdown and select "More Fonts..."
    repeat(16) { guiTest.robot().pressAndReleaseKey(KeyEvent.VK_DOWN) }
    guiTest.robot().pressAndReleaseKey(KeyEvent.VK_ENTER)

    MoreFontsDialogFixture.find(guiTest.robot())
      .selectFont("Aclonica")
      .clickOk()

    val fontContent = frame.editor.open("app/src/main/res/font/aclonica.xml").currentFileContents
    assertThat(fontContent.trimIndent()).isEqualTo(
      """
        <?xml version="1.0" encoding="utf-8"?>
        <font-family xmlns:app="http://schemas.android.com/apk/res-auto"
                app:fontProviderAuthority="com.google.android.gms.fonts"
                app:fontProviderPackage="com.google.android.gms"
                app:fontProviderQuery="Aclonica"
                app:fontProviderCerts="@array/com_google_android_gms_fonts_certs">
        </font-family>
      """.trimIndent()
    )

    val preloadedFontContent = frame.editor.open("app/src/main/res/values/preloaded_fonts.xml").currentFileContents
    assertThat(preloadedFontContent.trimIndent()).isEqualTo(
      """
        <?xml version="1.0" encoding="utf-8"?>
        <resources>
            <array name="preloaded_fonts" translatable="false">
                <item>@font/aclonica</item>
            </array>
        </resources>
      """.trimIndent()
    )

    val fontCertsContent = frame.editor.open("app/src/main/res/values/font_certs.xml").currentFileContents
    assertThat(fontCertsContent).isNotEmpty()
  }
}