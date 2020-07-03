/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.cuj

import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.ScreenshotsDuringTest
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.CreateResourceFileDialogFixture
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture
import com.android.tools.idea.tests.gui.framework.fixture.MoveFilesOrDirectoriesDialogFixture
import com.android.tools.idea.tests.gui.framework.fixture.ResourceExplorerFixture
import com.android.tools.idea.tests.gui.framework.fixture.ResourcePickerDialogFixture
import com.android.tools.idea.tests.gui.framework.fixture.assetstudio.AssetStudioWizardFixture
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.openPsd
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.selectDependenciesConfigurable
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.fest.swing.core.MouseButton
import org.intellij.lang.annotations.Language
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * UI test for "Adding new QR scanning feature to existing app" CUJ
 */
@RunWith(GuiTestRemoteRunner::class)
class QrScanningCujTest {

  @Rule
  @JvmField
  val guiTest: GuiTestRule = GuiTestRule().withTimeout(8, TimeUnit.MINUTES)

  @Rule
  @JvmField
  val screenshotsRule = ScreenshotsDuringTest()

  @Test
  @RunIn(TestGroup.UNRELIABLE)
  @Throws(IOException::class)
  fun qrScanningCuj() {
    // Import VotingApp project
    val ide = guiTest
      .importProjectAndWaitForProjectSyncToFinish("VotingApp")
      .invokeAndWaitForBuildAction("Build", "Make Project")

    // Add constraint layout dependency from the PSD
    ide.run {
      openPsd()
      .run {
        selectDependenciesConfigurable().run {
          findModuleSelector().selectModule("app")
          findDependenciesPanel().clickAddLibraryDependency().run {
            findSearchQueryTextBox().setText("constraint-layout")
            findSearchButton().click()
            findVersionsView(true).run {
              cell("1.1.3").click()
            }
            clickOk()
          }
        }
        clickOk()
      }
    }

    // Create new layout file
    ide.run {
      projectView
        .selectAndroidPane()
        .clickPath(MouseButton.RIGHT_BUTTON, "app", "res", "layout")
        .openFromContextualMenu({ CreateResourceFileDialogFixture.find(it) }, arrayOf("New", "Layout Resource File"))
        .setFilename("activity_main_qr_scan")
        .setRootElement("android.support.constraint.ConstraintLayout")
        .clickOk()
      invokeMenuPath("Build", "Make Project")
    }
      .closeBuildPanel()
      .closeProjectPanel()

    // Build layout from UI in the layout editor
    ide.editor
      .getLayoutEditor()
      .showOnlyDesignView().run {
        dragComponentToSurface("Common", "TextView")
          .findView("TextView", 0)
          .createConstraintFromTopToTopOfLayout()
          .createConstraintFromLeftToLeftOfLayout()
          .createConstraintFromRightToRightOfLayout()
        dragComponentToSurface("Common", "Button")
          .findView("Button", 0)
          .createConstraintFromBottomToBottomOfLayout()
          .createConstraintFromLeftToLeftOfLayout()
          .createConstraintFromRightToRightOfLayout()
      }

    // Import SVG resource using the Resource Explorer
    ide.openResourceManager()
      .run {
        ResourceExplorerFixture.find(robot())
          .clickAddButton()
        openFromContextualMenu({ AssetStudioWizardFixture.find(it) }, arrayOf("Vector Asset"))
          .useLocalFile(GuiTests.getTestDataDir()!!.toString() + "/VotingApp/ic_qr_code.svg")
          .setName("ic_qr_code")
          .setWidth(100)
          .clickNext()
          .clickFinish()
      }
      .closeResourceManager()

    // Add an image view to the layout
    ide.editor
      .getLayoutEditor().run {
        dragComponentToSurface("Common", "ImageView")
        ResourcePickerDialogFixture.find(robot()).run {
          resourceExplorer.searchField.setText("ic_qr")
          resourceExplorer.selectResource("ic_qr_code")
          clickOk()
        }
        waitForRenderToFinish()
        findView("ImageView", 0)
          .createConstraintFromLeftToLeftOfLayout()
          .createConstraintFromRightToRightOfLayout()
        configToolbar.chooseDevice("Pixel 3 XL")
          .chooseLayoutVariant("Landscape")
          .chooseDevice("Pixel 2")
          .chooseLayoutVariant("Portrait")
      }

    // Edit MainActivity.java
    ide.editor
      .open("app/src/main/java/com/src/adux/votingapp/MainActivity.java")
      .select("(activity_main_edit_text)")
      .typeText("activity_main_qr")
      .autoCompleteWindow
      .item("activity_main_qr_scan")
      .doubleClick()

    // Check that the project compiles
    ide.invokeAndWaitForBuildAction("Build", "Rebuild Project")

    // Add dependency by typing it in build.gradle file
    ide.editor
      // play-services-vision uses AndroidX, so we need to add the following property (see bug 130286699). Eventually, all the dependencies
      // used in this test project should be upgraded to AndroidX.
      .open("gradle.properties")
      .enterText("android.useAndroidX=true\n")
      .open("app/build.gradle")
      .moveBetween("dependencies {\n", "")
      .typeText("    implementation 'com.google.android.gms:play-services-vision:+'\n")
      .ideFrame
      .requestProjectSyncAndWaitForSyncToFinish()
      .closeBuildPanel()

    // Create a new layout file from the File > New > Android Resource File menu
    ide.openFromMenu({ CreateResourceFileDialogFixture.find(it) }, arrayOf("File", "New", "Android Resource File"))
      .setType("layout")
      .setRootElement("LinearLayout")
      .setFilename("actions_main")
      .clickOk()

    // Create a new vector drawable from an icon available in the Vector Asset Wizard
    ide.openResourceManager()
      .run {
        ResourceExplorerFixture.find(robot())
          .clickAddButton()
        openFromContextualMenu({ AssetStudioWizardFixture.find(it) }, arrayOf("Vector Asset"))
          .switchToClipArt()
          .chooseIcon()
          .filterByNameAndSelect("flash on")
          .clickOk()
          .setName("ic_baseline_flash_on_24")
          .setColor("FFFFFF")
          .clickNext()
          .clickFinish()
      }.closeResourceManager()

    // Add an ImageView by dragging it from the palette in the preview, and select ic_flash_on_white_24dp as the source
    ide.editor
      .getLayoutEditor().run {
        dragComponentToSurface("Common", "ImageView")
        ResourcePickerDialogFixture.find(robot()).run {
          resourceExplorer.searchField.setText("ic_baseline_flash_on")
          resourceExplorer.selectResource("ic_baseline_flash_on_24")
          clickOk()
        }
      }

    // Move qrcodelib code to the app module
    ide.projectView
      .selectProjectPane().run {
        clickPath(MouseButton.RIGHT_BUTTON, "VotingApp", "qrcodelib")
          .invokeMenuPath("Cut")
        clickPath(MouseButton.RIGHT_BUTTON, "VotingApp", "app", "src", "main", "java")
          .openFromContextualMenu({ MoveFilesOrDirectoriesDialogFixture.find(it.robot()) }, arrayOf("Paste"))
          .clickOk()
      }

    // Create a new layout file from contextual menu
    ide.projectView
      .selectAndroidPane()
      .clickPath(MouseButton.RIGHT_BUTTON, "app", "res", "layout")
      .openFromContextualMenu({ CreateResourceFileDialogFixture.find(it) }, arrayOf("New", "Layout Resource File"))
      .setFilename("barcode_capture")
      .setRootElement("FrameLayout")
      .clickOk()

    @Language("XML")
    val layoutText = """
      <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
          xmlns:tools="http://schemas.android.com/tools"
          android:id="@+id/topLayout"
          android:layout_width="match_parent"
          android:layout_height="match_parent"
          android:keepScreenOn="true"
          android:orientation="vertical">

          <qrcodelib.CameraSourcePreview
              android:id="@+id/preview"
              android:layout_width="match_parent"
              android:layout_height="match_parent">

      <!--   TODO: Need to fix the qrcodelib.CameraSourcePreview customView so that it appears correctly in the layout editor-->

              <qrcodelib.GraphicOverlay
                  android:id="@+id/graphicOverlay"
                  android:layout_width="match_parent"
                  android:layout_height="match_parent" />

          </qrcodelib.CameraSourcePreview>

          <RelativeLayout
              android:layout_width="match_parent"
              android:layout_height="150dp"
              android:layout_gravity="center_horizontal|bottom"
              android:visibility="gone">

              <TextView
                  android:id="@+id/textView"
                  android:layout_width="wrap_content"
                  android:layout_height="wrap_content"
                  android:layout_centerHorizontal="true"
                  android:layout_centerVertical="true"
                  android:layout_gravity="center_horizontal|bottom"
                  android:text=""
                  android:textAppearance="?android:attr/textAppearanceLarge" />

          </RelativeLayout>

          <TextView
              android:layout_width="wrap_content"
              android:layout_height="wrap_content"
              android:textAppearance="?android:attr/textAppearanceMedium"
              android:shadowDx="1"
              android:shadowDy="1"
              android:shadowRadius="2"
              android:layout_marginTop="64dp"
              android:layout_marginLeft="16dp"
              android:layout_marginRight="16dp"
              android:textColor="@android:color/white"
              android:textStyle="bold"
              android:id="@+id/topText"
              tools:text="Scanning..."
              android:layout_gravity="center_horizontal|top" />

          <include layout="@layout/actions_main"/>

          <ImageView
              android:id="@+id/barcode_square"
              android:layout_width="match_parent"
              android:layout_height="wrap_content"
              android:layout_gravity="center"
              android:layout_margin="38dp"
              android:adjustViewBounds="true"
              android:alpha="0.5"
              tools:srcCompat="@drawable/material_barcode_square_512" />

      </FrameLayout>""".trimIndent()

    // Copy text into layout file
    ide.editor
      .selectEditorTab(EditorFixture.Tab.EDITOR)
      .select("(<FrameLayout[\\s\\S]*</FrameLayout>)")
      .pasteText(layoutText)

    // Edit AndroidManifest.xml
    ide.editor
      .open("app/src/main/AndroidManifest.xml")
      .moveBetween("<uses-permission android:name=\"android.permission.INTERNET\"/>", "")
      .enterText("\n    <uses-permission android:name=\"android.permission.CAMERA\" />")

    // Edit MainActivity.java by pasting and typing text
    ide.editor
      .open("app/src/main/java/com/src/adux/votingapp/MainActivity.java")
      .moveBetween("", "private void sendCode() {")
      .pasteText("""
        private void startScan() {
            Log.d("Scanning","again");
            final MaterialBarcodeScanner materialBarcodeScanner = new MaterialBarcodeScannerBuilder()
                .withActivity(this)
                .withEnableAutoFocus(true)
                .withBleepEnabled(true)
                .withBackfacingCamera()
                .withCenterTracker()
                .withText("Scanning QR code...")
                .withResultListener(new MaterialBarcodeScanner.OnResultListener() {
                    @Override
                    public void onResult(Barcode barcode) throws IOException {
                        Log.d("Scanning", barcode.rawValue);
                        mQRcodeResult = barcode;
                        type = false;
                        Thread thread = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Uri data = Uri.parse(mQRcodeResult.rawValue);
                                    String link = data.getLastPathSegment();
                                    mQRResult = link;
                                    System.out.println("&&&&&&&& "+link);
                                    //getJson(SERVER_URL + "/question?id=" + mQRcodeResult.rawValue);
                                    getJson(SERVER_URL + "/question?id=" + link);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                        thread.start();
                        //}
                    }
                })
                .build();
            materialBarcodeScanner.startScan();
        }

      """.trimIndent())
      .select("(Edit Text Code Screen \\*\\*\\*\\*\\*\\*\n" +
              "                sendCode\\(\\);)")
      .typeText("QR Code Screen ******\nstartScan();")
  }
}
