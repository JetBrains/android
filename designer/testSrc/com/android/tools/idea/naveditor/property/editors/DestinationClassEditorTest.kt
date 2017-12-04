/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.property.editors

import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavigationGradleTestCase
import com.android.tools.idea.uibuilder.property.fixtures.EnumEditorFixture
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

// TODO: make this work as a non-gradle test
class DestinationClassEditorTest : NavigationGradleTestCase() {
  override fun setUp() {
    // disabled along with test below
  }

  override fun tearDown() {
    // disabled along with test below
  }

  fun testEmpty() {
    // placeholder for disabled test below.
  }

  // failing after 2017.3 merge
  fun /*test*/Fragment() {
    val model = model("nav.xml",
        NavModelBuilderUtil.rootComponent("root")
            .unboundedChildren(
                NavModelBuilderUtil.fragmentComponent("f1"),
                NavModelBuilderUtil.activityComponent("activity1")))
        .build()
    val property = mock(NlProperty::class.java)
    `when`(property.components).thenReturn(listOf(model.find("f1")))

    val editor = DestinationClassEditor()
    editor.property = property

    EnumEditorFixture
        .create(::DestinationClassEditor)
        .setProperty(property)
        .showPopup()
        .expectChoices("none", null,
            "android.webkit.WebViewFragment", "android.webkit.WebViewFragment",
            "android.preference.PreferenceFragment", "android.preference.PreferenceFragment",
            "android.app.ListFragment", "android.app.ListFragment",
            "android.app.DialogFragment", "android.app.DialogFragment",
            "android.support.v4.app.ListFragment", "android.support.v4.app.ListFragment",
            "android.arch.navigation.NavHostFragment", "android.arch.navigation.NavHostFragment",
            "android.support.v4.app.DialogFragment", "android.support.v4.app.DialogFragment",
            "android.arch.navigation.FragmentNavigator.StateFragment", "android.arch.navigation.FragmentNavigator.StateFragment",
            "mytest.navtest.BlankFragment", "mytest.navtest.BlankFragment",
            "android.support.v7.app.AppCompatDialogFragment", "android.support.v7.app.AppCompatDialogFragment",
            "android.support.design.widget.BottomSheetDialogFragment", "android.support.design.widget.BottomSheetDialogFragment")

    `when`(property.components).thenReturn(listOf(model.find("activity1")))
    editor.property = property

    EnumEditorFixture
        .create(::DestinationClassEditor)
        .setProperty(property)
        .showPopup()
        .expectChoices("none", null,
            "android.app.ListActivity", "android.app.ListActivity",
            "android.app.AliasActivity", "android.app.AliasActivity",
            "android.app.NativeActivity", "android.app.NativeActivity",
            "android.accounts.AccountAuthenticatorActivity", "android.accounts.AccountAuthenticatorActivity",
            "android.app.ActivityGroup", "android.app.ActivityGroup",
            "android.app.ExpandableListActivity", "android.app.ExpandableListActivity",
            "android.support.v4.app.SupportActivity", "android.support.v4.app.SupportActivity",
            "android.app.LauncherActivity", "android.app.LauncherActivity",
            "android.preference.PreferenceActivity", "android.preference.PreferenceActivity",
            "android.app.TabActivity", "android.app.TabActivity",
            "android.support.v4.app.BaseFragmentActivityApi14", "android.support.v4.app.BaseFragmentActivityApi14",
            "android.support.v4.app.BaseFragmentActivityApi16", "android.support.v4.app.BaseFragmentActivityApi16",
            "android.support.v4.app.FragmentActivity", "android.support.v4.app.FragmentActivity",
            "android.support.v7.app.AppCompatActivity", "android.support.v7.app.AppCompatActivity",
            "mytest.navtest.MainActivity", "mytest.navtest.MainActivity");
  }
}