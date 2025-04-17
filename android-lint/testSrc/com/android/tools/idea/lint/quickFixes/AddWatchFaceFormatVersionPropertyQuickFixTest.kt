/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.lint.quickFixes

import com.android.SdkConstants.ANDROID_MANIFEST_XML
import com.android.tools.idea.lint.AbstractAndroidLintTest
import com.android.tools.idea.lint.inspections.AndroidLintWatchFaceFormatMissingVersionInspection
import com.android.tools.idea.testing.addFileToProjectAndInvalidate
import com.android.tools.idea.testing.moveCaret
import com.android.tools.lint.checks.infrastructure.TestLintResult
import com.google.common.truth.Truth.assertThat
import org.intellij.lang.annotations.Language

class AddWatchFaceFormatVersionPropertyQuickFixTest : AbstractAndroidLintTest() {

  fun `test property is added to the application element`() {
    check(
      manifestContent =
        """
      <manifest xmlns:android="http://schemas.android.com/apk/res/android"
          package="test.pkg">
          <uses-sdk android:minSdkVersion="33" />
          <uses-feature android:name="android.hardware.type.watch" />
          <application
              android:icon="@mipmap/ic_launcher"
              android:label="@string/app_name"
              android:hasCode="false">
          </application>
      </manifest>
    """
          .trimIndent(),
      expectedDiff =
        """
        @@ -9 +9
                  android:hasCode="false">
        +         <property
        +             android:name="com.google.wear.watchface.format.version"
        +             android:value="1" />
              </application>
    """
          .trimIndent(),
    )
  }

  fun `test property is added after other property elements`() {
    check(
      manifestContent =
        """
      <manifest xmlns:android="http://schemas.android.com/apk/res/android"
          package="test.pkg">
          <uses-sdk android:minSdkVersion="33" />
          <uses-feature android:name="android.hardware.type.watch" />
          <application
              android:icon="@mipmap/ic_launcher"
              android:label="@string/app_name"
              android:hasCode="false">
              <property android:name="some other property" android:value="some value" />
          </application>
      </manifest>
    """
          .trimIndent(),
      expectedDiff =
        """
        @@ -10 +10
                  <property android:name="some other property" android:value="some value" />
        +         <property
        +             android:name="com.google.wear.watchface.format.version"
        +             android:value="1" />
              </application>
    """
          .trimIndent(),
    )
  }

  private fun check(@Language("XML") manifestContent: String, expectedDiff: String) {
    myFixture.addFileToProject("res/raw/watch_face.xml", "<WatchFace />")
    val manifestFile =
      myFixture.addFileToProjectAndInvalidate(ANDROID_MANIFEST_XML, manifestContent)
    myFixture.enableInspections(AndroidLintWatchFaceFormatMissingVersionInspection())
    myFixture.configureFromExistingVirtualFile(manifestFile.virtualFile)

    myFixture.moveCaret("<application|")
    val quickFix =
      myFixture.findSingleIntention(
        "Add 'com.google.wear.watchface.format.version' property element"
      )

    myFixture.launchAction(quickFix)

    val fixed = manifestFile.text
    val diff = TestLintResult.getDiff(manifestContent, fixed, 1)
    assertThat(diff.trim()).isEqualTo(expectedDiff)
  }
}
