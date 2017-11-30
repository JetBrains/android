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

import com.android.SdkConstants
import com.android.SdkConstants.*
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.property.editors.NonEditableEditor
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavigationTestCase
import com.android.tools.idea.naveditor.property.NavComponentTypeProperty
import com.android.tools.idea.naveditor.property.NavPropertiesManager
import com.android.tools.idea.naveditor.property.TYPE_EDITOR_PROPERTY_LABEL
import com.android.tools.idea.naveditor.property.editors.ChildDestinationsEditor
import com.android.tools.idea.naveditor.property.editors.SourceGraphEditor
import com.android.tools.idea.naveditor.property.editors.VisibleDestinationsEditor
import org.jetbrains.android.dom.navigation.NavigationSchema
import org.jetbrains.android.dom.navigation.NavigationSchema.*

class NavPropertiesInspectorProvidersTest : NavigationTestCase() {

  private lateinit var model: SyncNlModel
  private lateinit var propertiesManager: NavPropertiesManager

  override fun setUp() {
    super.setUp()
    model = model("nav.xml",
        NavModelBuilderUtil.rootComponent("root").unboundedChildren(
            NavModelBuilderUtil.includeComponent("navigation"),
            NavModelBuilderUtil.fragmentComponent("f1")
                .unboundedChildren(NavModelBuilderUtil.actionComponent("a1").withDestinationAttribute("f2"),
                    NavModelBuilderUtil.actionComponent("a2").withDestinationAttribute("f3")),
            NavModelBuilderUtil.fragmentComponent("f2"),
            NavModelBuilderUtil.navigationComponent("subnav").unboundedChildren(
                NavModelBuilderUtil.fragmentComponent("f3"),
                NavModelBuilderUtil.activityComponent("activity"))))
        .build()

    propertiesManager = NavPropertiesManager(myFacet, model.surface)
  }

  fun testFragmentInspector() {
    val inspectorProvider = NavMainPropertiesInspectorProvider()

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
    assertInstanceOf(inspector.editors.first { it.property == typeProperty }, NonEditableEditor::class.java)
  }

  fun testActivityInspector() {
    val activity = listOf(model.find("activity")!!)

    val dummyProperty = SimpleProperty("foo", activity)
    val typeProperty = NavComponentTypeProperty(activity)
    val idProperty = SimpleProperty(ATTR_ID, activity)
    val nameProperty = SimpleProperty(ATTR_NAME, activity)
    val labelProperty = SimpleProperty(ATTR_LABEL, activity)
    val actionProperty = SimpleProperty(ATTR_ACTION, activity)
    val dataProperty = SimpleProperty(ATTR_DATA, activity)
    val dataPatternProperty = SimpleProperty(ATTR_DATA_PATTERN, activity)
    // TODO: add more properties once they're fully supported

    val properties = listOf(typeProperty, idProperty, nameProperty, labelProperty, dummyProperty, actionProperty, dataProperty,
        dataPatternProperty).associateBy { it.name }

    val inspectorProvider = NavMainPropertiesInspectorProvider()
    assertTrue(inspectorProvider.isApplicable(activity, properties, propertiesManager))
    val inspector = inspectorProvider.createCustomInspector(activity, properties, propertiesManager)

    assertSameElements(inspector.editors.map { it.property }, listOf(idProperty, typeProperty, nameProperty, labelProperty))
    assertInstanceOf(inspector.editors.first { it.property == typeProperty }, NonEditableEditor::class.java)

    val activityInspectorProvider = NavActivityPropertiesInspectorProvider()
    assertTrue(activityInspectorProvider.isApplicable(activity, properties, propertiesManager))
    val activityInspector = activityInspectorProvider.createCustomInspector(activity, properties, propertiesManager)
    assertSameElements(activityInspector.editors.map { it.property }, listOf(actionProperty, dataProperty, dataPatternProperty))
  }

  fun testActionInspector() {

    val a1Only = listOf(model.find("a1")!!)

    val dummyProperty = SimpleProperty("foo", a1Only)
    val typeProperty = SimpleProperty(TYPE_EDITOR_PROPERTY_LABEL, a1Only)
    val idProperty = SimpleProperty(ATTR_ID, a1Only)
    val singleTopProperty = SimpleProperty(ATTR_SINGLE_TOP, a1Only)
    val documentProperty = SimpleProperty(ATTR_DOCUMENT, a1Only)
    val clearTaskProperty = SimpleProperty(ATTR_CLEAR_TASK, a1Only)
    val destinationProperty = SimpleProperty(NavigationSchema.ATTR_DESTINATION, a1Only)
    val popToProperty = SimpleProperty(NavigationSchema.ATTR_POP_UP_TO, a1Only)
    val popToInclusiveProperty = SimpleProperty(NavigationSchema.ATTR_POP_UP_TO_INCLUSIVE, a1Only)
    // TODO: add more properties once they're fully supported

    val properties =
        listOf(typeProperty, idProperty, destinationProperty, clearTaskProperty, singleTopProperty, documentProperty, popToProperty,
            popToInclusiveProperty, dummyProperty)
            .associateBy { it.name }

    val inspectorProvider = NavMainPropertiesInspectorProvider()
    assertTrue(inspectorProvider.isApplicable(a1Only, properties, propertiesManager))
    val inspector = inspectorProvider.createCustomInspector(a1Only, properties, propertiesManager)

    assertSameElements(inspector.editors.map { it.property }, listOf(idProperty, typeProperty, destinationProperty))
    assertInstanceOf(inspector.editors.first { it.property == typeProperty }, NonEditableEditor::class.java)
    assertInstanceOf(inspector.editors.first { it.property == destinationProperty }, VisibleDestinationsEditor::class.java)

    val popInspectorProvider = NavActionPopInspectorProvider()
    assertTrue(popInspectorProvider.isApplicable(a1Only, properties, propertiesManager))
    val popInspector = popInspectorProvider.createCustomInspector(a1Only, properties, propertiesManager)
    assertSameElements(popInspector.editors.map { it.property }, listOf(popToInclusiveProperty, popToProperty))

    val launchOptionsInspectorProvider = NavActionLaunchOptionsInspectorProvider()
    assertTrue(launchOptionsInspectorProvider.isApplicable(a1Only, properties, propertiesManager))
    val launchOptionsInspector = launchOptionsInspectorProvider.createCustomInspector(a1Only, properties, propertiesManager)
    assertSameElements(launchOptionsInspector.editors.map { it.property }, listOf(documentProperty, singleTopProperty, clearTaskProperty))
  }

  fun testRootNavigationInspector() {
    val inspectorProvider = NavMainPropertiesInspectorProvider()

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
    assertInstanceOf(inspector.editors.first { it.property == typeProperty }, NonEditableEditor::class.java)
    assertInstanceOf(inspector.editors.first { it.property == startDestinationProperty }, ChildDestinationsEditor::class.java)
  }

  fun testSubnavNavigationInspector() {
    val inspectorProvider = NavMainPropertiesInspectorProvider()

    val root = listOf(model.find("subnav")!!)

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
        listOf(idProperty, typeProperty, nameProperty, labelProperty))
    assertInstanceOf(inspector.editors.first { it.property == typeProperty }, NonEditableEditor::class.java)
  }

  fun testIncludeNavigationInspector() {
    val inspectorProvider = NavMainPropertiesInspectorProvider()

    val root = listOf(model.find("nav")!!)

    val dummyProperty = SimpleProperty("foo", root)
    val typeProperty = NavComponentTypeProperty(root)
    val graphProperty = SimpleProperty(SdkConstants.ATTR_GRAPH, root)

    val properties = listOf(graphProperty, typeProperty, dummyProperty)
        .associateBy { it.name }

    assertTrue(inspectorProvider.isApplicable(root, properties, propertiesManager))
    val inspector = inspectorProvider.createCustomInspector(root, properties, propertiesManager)

    assertSameElements(inspector.editors.map { it.property },
        listOf(typeProperty, graphProperty))
    assertInstanceOf(inspector.editors.first { it.property == typeProperty }, NonEditableEditor::class.java)
    assertInstanceOf(inspector.editors.first { it.property == graphProperty }, SourceGraphEditor::class.java)
  }
}