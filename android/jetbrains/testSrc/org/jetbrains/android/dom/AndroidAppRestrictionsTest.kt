/*
 * Copyright (C) 2021 The Android Open Source Project
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
package org.jetbrains.android.dom

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.tools.idea.res.psi.ResourceReferencePsiElement
import com.google.common.truth.Truth.assertThat
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.dom.inspections.AndroidDomInspection
import org.jetbrains.android.dom.inspections.AndroidElementNotAllowedInspection
import org.jetbrains.android.dom.inspections.AndroidUnknownAttributeInspection
import org.jetbrains.android.dom.xml.AppRestrictionsDomFileDescription
import org.jetbrains.android.dom.xml.Restrictions

/**
 * Tests for [AppRestrictionsDomFileDescription] and [Restrictions]
 */
class AndroidAppRestrictionsTest : AndroidTestCase() {

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(AndroidDomInspection::class.java,
                                AndroidUnknownAttributeInspection::class.java,
                                AndroidElementNotAllowedInspection::class.java)
    myFixture.addFileToProject("res/values/values.xml", """
      <resources>
          <string name="test">foobar</string>
          <string name="foo">foobar</string>
          <string-array name="stringarray">foobar</string-array>
      </resources>
    """.trimIndent())
  }

  fun testHighlighting() {
    val restrictions = myFixture.addFileToProject(
      "res/xml/app_restrictions.xml",
      """
        <?xml version="1.0" encoding="utf-8"?>
        <restrictions xmlns:android="http://schemas.android.com/apk/res/android">
            <restriction
                android:defaultValue="false"
                android:description="@string/test"
                android:entryValues="@array/stringarray"
                android:key="asdf"
                android:restrictionType="bool"
                android:title="@string/test"/>
            <restriction />
        </restrictions>
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(restrictions.virtualFile)
    myFixture.checkHighlighting()
  }

  fun testResourceElement() {
    val restrictions = myFixture.addFileToProject(
      "res/xml/app_restrictions.xml",
      """
        <?xml version="1.0" encoding="utf-8"?>
        <restrictions xmlns:android="http://schemas.android.com/apk/res/android">
            <restriction
                android:defaultValue="false"
                android:description="@string/<caret>test"
                android:entryValues="@array/stringarray"
                android:key="asdf"
                android:restrictionType="bool"
                android:title="@string/test"/>
            <restriction />
        </restrictions>
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(restrictions.virtualFile)
    val elementAtCaret = myFixture.elementAtCaret as ResourceReferencePsiElement
    assertThat(elementAtCaret.resourceReference).isEqualTo(ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.STRING, "test"))

    myFixture.completeBasic()
    val elementStrings = myFixture.lookupElementStrings
    assertThat(elementStrings).containsAllOf("@string/foo", "@string/test")
  }
}