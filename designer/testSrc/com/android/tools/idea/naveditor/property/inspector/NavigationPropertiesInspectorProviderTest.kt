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
package com.android.tools.idea.naveditor.property.inspector

import com.android.SdkConstants.*
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.property.editors.NonEditableEditor
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavigationTestCase
import com.android.tools.idea.naveditor.property.NavComponentTypeProperty
import com.android.tools.idea.naveditor.property.NavPropertiesManager
import com.android.tools.idea.naveditor.property.TYPE_EDITOR_PROPERTY_LABEL
import com.android.tools.idea.naveditor.property.editors.ChildDestinationsEditor
import com.android.tools.idea.naveditor.property.editors.VisibleDestinationsEditor
import org.jetbrains.android.dom.navigation.NavigationSchema
import org.jetbrains.android.dom.navigation.NavigationSchema.*

class NavigationPropertiesInspectorProviderTest : NavigationTestCase() {

  private lateinit var model : SyncNlModel
  private lateinit var propertiesManager : NavPropertiesManager

  override fun setUp() {
    super.setUp()
    model = model("nav.xml",
        NavModelBuilderUtil.rootComponent("root").unboundedChildren(
            NavModelBuilderUtil.fragmentComponent("f1")
                .unboundedChildren(NavModelBuilderUtil.actionComponent("a1").withDestinationAttribute("f2"),
                    NavModelBuilderUtil.actionComponent("a2").withDestinationAttribute("f3")),
            NavModelBuilderUtil.fragmentComponent("f2"),
            NavModelBuilderUtil.fragmentComponent("f3"),
            NavModelBuilderUtil.activityComponent("activity")))
        .build()

    propertiesManager = NavPropertiesManager(myFacet, model.surface)
  }

  fun testFragmentInspector() {
    val inspectorProvider = NavigationPropertiesInspectorProvider()

    val f1Only = listOf(model.find("f1")!!)

    val dummyProperty = SimpleProperty("foo", f1Only)
    val typeProperty = NavComponentTypeProperty(f1Only)
    val idProperty = SimpleProperty(ATTR_ID, f1Only)
    val nameProperty = SimpleProperty(ATTR_NAME, f1Only)
    val labelProperty = SimpleProperty(ATTR_LABEL, f1Only)
    // TODO: add more properties once they're fully supported

    val properties = listOf(typeProperty, idProperty, nameProperty, labelProperty, dummyProperty).associateBy { it.name }

    assertTrue(inspectorProvider.isApplicable(f1Only, properties, propertiesManager))
    val inspector = inspectorProvider.createCustomInspector(f1Only, properties, propertiesManager)

    assertSameElements(inspector.editors.map { it.property }, listOf(idProperty, typeProperty, nameProperty, labelProperty))
    assertInstanceOf(inspector.editors.first {it.property == typeProperty}, NonEditableEditor::class.java)
  }

  fun testActionInspector() {
    val inspectorProvider = NavigationPropertiesInspectorProvider()

    val a1Only = listOf(model.find("a1")!!)

    val dummyProperty = SimpleProperty("foo", a1Only)
    val typeProperty = SimpleProperty(TYPE_EDITOR_PROPERTY_LABEL, a1Only)
    val idProperty = SimpleProperty(ATTR_ID, a1Only)
    val singleTopProperty = SimpleProperty(ATTR_SINGLE_TOP, a1Only)
    val documentProperty = SimpleProperty(ATTR_DOCUMENT, a1Only)
    val clearTaskProperty = SimpleProperty(ATTR_CLEAR_TASK, a1Only)
    val destinationProperty = SimpleProperty(NavigationSchema.ATTR_DESTINATION, a1Only)
    // TODO: add more properties once they're fully supported

    val properties =
        listOf(typeProperty, idProperty, destinationProperty, clearTaskProperty, singleTopProperty, documentProperty, dummyProperty)
            .associateBy { it.name }

    assertTrue(inspectorProvider.isApplicable(a1Only, properties, propertiesManager))
    val inspector = inspectorProvider.createCustomInspector(a1Only, properties, propertiesManager)

    assertSameElements(inspector.editors.map { it.property },
        listOf(idProperty, typeProperty, documentProperty, singleTopProperty, clearTaskProperty, destinationProperty))
    assertInstanceOf(inspector.editors.first {it.property == typeProperty}, NonEditableEditor::class.java)
    assertInstanceOf(inspector.editors.first {it.property == destinationProperty}, VisibleDestinationsEditor::class.java)
  }

  fun testNavigationInspector() {
    val inspectorProvider = NavigationPropertiesInspectorProvider()

    val root = listOf(model.find("root")!!)

    val dummyProperty = SimpleProperty("foo", root)
    val typeProperty = NavComponentTypeProperty(root)
    val idProperty = SimpleProperty(ATTR_ID, root)
    val nameProperty = SimpleProperty(ATTR_NAME, root)
    val labelProperty = SimpleProperty(ATTR_LABEL, root)
    val startDestinationProperty = SimpleProperty(NavigationSchema.ATTR_START_DESTINATION, root)

    val properties = listOf(typeProperty, idProperty, nameProperty, labelProperty, startDestinationProperty, dummyProperty)
        .associateBy { it.name }

    assertTrue(inspectorProvider.isApplicable(root, properties, propertiesManager))
    val inspector = inspectorProvider.createCustomInspector(root, properties, propertiesManager)

    assertSameElements(inspector.editors.map { it.property },
        listOf(idProperty, typeProperty, nameProperty, labelProperty, startDestinationProperty))
    assertInstanceOf(inspector.editors.first {it.property == typeProperty}, NonEditableEditor::class.java)
    assertInstanceOf(inspector.editors.first {it.property == startDestinationProperty}, ChildDestinationsEditor::class.java)
  }

}