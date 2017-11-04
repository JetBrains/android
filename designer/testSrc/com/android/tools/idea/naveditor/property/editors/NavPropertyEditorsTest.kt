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

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.property.editors.NonEditableEditor
import com.android.tools.idea.naveditor.property.TYPE_EDITOR_PROPERTY_LABEL
import com.android.tools.idea.naveditor.property.inspector.SimpleProperty
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.dom.navigation.NavigationSchema
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class NavPropertyEditorsTest : AndroidTestCase() {

  fun testCreate() {
    val model = mock(NlModel::class.java)
    `when`(model.project).thenReturn(project)
    val component = mock(NlComponent::class.java)
    `when`(component.model).thenReturn(model)

    val navPropertyEditors = NavPropertyEditors.Factory.getInstance(project)
    var editor = navPropertyEditors.create(SimpleProperty(TYPE_EDITOR_PROPERTY_LABEL, listOf(component)))
    assertInstanceOf(editor, NonEditableEditor::class.java)

    editor = navPropertyEditors.create(SimpleProperty(NavigationSchema.ATTR_DESTINATION, listOf(component)))
    assertInstanceOf(editor, VisibleDestinationsEditor::class.java)

    // Try something else just to make sure it doesn't blow up
    editor = navPropertyEditors.create(SimpleProperty("foo", listOf(component)))
    assertNotNull(editor)
  }
}