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
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture
import com.android.tools.idea.tests.gui.framework.fixture.designer.SplitEditorFixture
import com.android.tools.idea.tests.gui.framework.fixture.designer.getSplitEditorFixture
import com.android.tools.idea.tests.gui.framework.fixture.properties.PTableFixture
import com.android.tools.idea.tests.gui.framework.fixture.properties.SectionFixture
import com.google.common.truth.Truth.*
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.fest.swing.core.MouseButton
import org.fest.swing.data.TableCell
import org.fest.swing.timing.Wait
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.awt.event.KeyEvent
import java.util.concurrent.TimeUnit

@RunWith(GuiTestRemoteRunner::class)
class DownloadableFontTest {
  @JvmField
  @Rule
  val guiTest = GuiTestRule().withTimeout(15, TimeUnit.MINUTES)

  @JvmField
  @Rule
  val renderTaskLeakCheckRule = RenderTaskLeakCheckRule()
  private lateinit var myNlEditorFixture: NlEditorFixture
  private lateinit var declaredAttributesSection: SectionFixture
  private lateinit var declaredAttributesTable: PTableFixture

  /**
   * To verify downloadable font added as a file to project
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: ac7e1d3e-49b5-48d4-8fc1-0c757e94ce71
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Launch Android Studio.
   *   2. Open an existing project, SimpleApplication
   *   3. Open the "activity_my.xml" file in editor mode and select "TextView"
   *   4. Change file layout to "Design" mode.
   *   5. Go to Attributes => Declared Properties
   *   6. Add Property "fontFamily"
   *   7. Select "More Fonts" for value
   *   8. Select "Aclonica" and click "Ok" (Verify 1)
   *   Verify:
   *   1. Font should apply to selected text view
   *   1.1) A new font folder should be created with selected font
   *   1.2) TTF font file added to font folder
   *   </pre>
   * <p>
   */

  @Test
  @Throws(Exception::class)
  fun downloadableFontTest() {
    val myEditorFixture = guiTest
      .importProjectAndWaitForProjectSyncToFinish("SimpleApplication", Wait.seconds(180))
      .editor

    myEditorFixture.open("app/src/main/res/layout/activity_my.xml")
    myEditorFixture.waitForFileToActivate()
    val splitEditorFixture: SplitEditorFixture = myEditorFixture.getSplitEditorFixture()
    splitEditorFixture.setCodeMode()
    guiTest.waitForAllBackgroundTasksToBeCompleted()
    myEditorFixture.select("(TextView)")

    myNlEditorFixture = myEditorFixture.layoutEditor
      .waitForSurfaceToLoad()
      .waitForRenderToFinish()
    assertThat(myNlEditorFixture.canInteractWithSurface()).isTrue()

    guiTest.waitForAllBackgroundTasksToBeCompleted()
    refreshDeclaredAttributesTable("textview")
    declaredAttributesSection.expand()    //Expand declared attributes section.
    declaredAttributesSection.clickAddAttributeActionButton()//Adding new attribute
    refreshDeclaredAttributesTable("textview")

    declaredAttributesSection
      .title!!
      .addProperty()
    guiTest.robot().typeText("fontFamily")
    guiTest.waitForAllBackgroundTasksToBeCompleted()
    guiTest.robot().pressAndReleaseKey(KeyEvent.VK_ENTER)
    selectFontFamily(2) // Open the dropdown and select "Fonts" for a textview
    selectFontFamily(15) // Open the dropdown and select "More Fonts..."

    MoreFontsDialogFixture.find(guiTest.robot())
      .selectFont("Aclonica")
    guiTest.robot().pressAndReleaseKey(KeyEvent.VK_ENTER)
    guiTest.ideFrame().requestFocusIfLost()
    refreshDeclaredAttributesTable("textview")

    guiTest.waitForAllBackgroundTasksToBeCompleted()
    myEditorFixture.switchToTab("Text")
    // Verify changes
    val activityFileContent = myEditorFixture.currentFileContents
    Assert.assertTrue(activityFileContent.contains("android:fontFamily=\"@font/aclonica\""))

    val fontContent = myEditorFixture.open("app/src/main/res/font/aclonica.xml").currentFileContents
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

    val preloadedFontContent = myEditorFixture.open("app/src/main/res/values/preloaded_fonts.xml").currentFileContents
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

    val fontCertsContent = myEditorFixture.open("app/src/main/res/values/font_certs.xml").currentFileContents
    assertThat(fontCertsContent).isNotEmpty()
  }

  private fun refreshDeclaredAttributesTable(attributeID: String) {
    //Method to constantly get the latest Table info in declared Attributes
    declaredAttributesSection = myNlEditorFixture
      .attributesPanel
      .waitForId(attributeID)
      .findSectionByName("Declared Attributes")!!

    //Updating the Table information.
    declaredAttributesTable = declaredAttributesSection.components.first() as PTableFixture
  }

  private fun selectFontFamily(goDownClick: Int) {
    guiTest.waitForAllBackgroundTasksToBeCompleted()
    refreshDeclaredAttributesTable("textview")
    val row = declaredAttributesTable.findRowOf("fontFamily")
    declaredAttributesTable.click(TableCell.row(row).column(1), MouseButton.LEFT_BUTTON)
    declaredAttributesTable.click(TableCell.row(row).column(1), MouseButton.LEFT_BUTTON) //To reduce flakiness
    // Open the dropdown and select "font"
    repeat(goDownClick) { guiTest.robot().pressAndReleaseKey(KeyEvent.VK_DOWN) }
    guiTest.robot().pressAndReleaseKey(KeyEvent.VK_ENTER)
    refreshDeclaredAttributesTable("textview")
    guiTest.waitForAllBackgroundTasksToBeCompleted()
  }
}