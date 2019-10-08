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
package com.android.tools.idea.startup

import com.google.common.truth.Truth.assertThat
import com.intellij.ide.ui.customization.ActionUrl
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.testFramework.ApplicationRule
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class HideProgrammaticallyAddedActionUsingSchemaTest {

  @JvmField @Rule val appRule = ApplicationRule()
  private lateinit var schema: CustomActionsSchema
  private lateinit var shallowCopyOfSchemaActions: ArrayList<ActionUrl>

  @Before
  fun setUp() {
    schema = CustomActionsSchema()
    shallowCopyOfSchemaActions = ArrayList(schema.actions)
  }

  @After
  fun tearDown() {
    schema.actions = shallowCopyOfSchemaActions
    assertThat(schema.actions).hasSize(shallowCopyOfSchemaActions.size)
  }

  @Test
  fun mutateSchemaToHideAction() {
    Actions.hideProgrammaticallyAddedAction(
      schema, "Action", 1, "root", "child", "grandchild")
    assertThat(schema.actions).hasSize(1)

    val hiddenActionUrl = Actions.createActionUrl(
      "Action", null, ActionUrl.DELETED, 1, arrayOf("root", "child", "grandchild"))
    assertThat(schema.actions.contains(hiddenActionUrl)).isTrue()

    val anotherActionUrl = Actions.createActionUrl(
      "Action", null, ActionUrl.DELETED, 2, arrayOf("root", "child", "grandchild"))
    assertThat(schema.actions.contains(anotherActionUrl)).isFalse()
  }

}