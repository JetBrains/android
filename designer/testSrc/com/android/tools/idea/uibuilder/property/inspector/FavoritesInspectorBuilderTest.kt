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
package com.android.tools.idea.uibuilder.property.inspector

import com.android.AndroidXConstants.CONSTRAINT_LAYOUT
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ALPHA
import com.android.SdkConstants.ATTR_GRAVITY
import com.android.SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF
import com.android.SdkConstants.ATTR_LAYOUT_CONSTRAINT_TAG
import com.android.SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF
import com.android.SdkConstants.ATTR_TEXT
import com.android.SdkConstants.ATTR_VISIBILITY
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.TEXT_VIEW
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.testutils.MockitoKt.mock
import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.addManifest
import com.android.tools.idea.uibuilder.property.NlNewPropertyItem
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.idea.uibuilder.property.NlPropertyType
import com.android.tools.idea.uibuilder.property.testutils.InspectorTestUtil
import com.android.tools.property.panel.api.EnumSupport
import com.android.tools.property.panel.api.EnumSupportProvider
import com.android.tools.property.ptable.PTable
import com.google.common.truth.Truth.assertThat
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.runReadAction
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.StringSelection
import javax.swing.JTable
import javax.swing.TransferHandler
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FavoritesInspectorBuilderTest {
  @get:Rule val projectRule = AndroidProjectRule.withSdk()

  @Before
  fun setUp() {
    addManifest(projectRule.fixture)
    projectRule.replaceService(PropertiesComponent::class.java, PropertiesComponentMock())
    InspectorSection.FAVORITES.visible = true
  }

  private val enumSupportProvider =
    object : EnumSupportProvider<NlPropertyItem> {
      override fun invoke(property: NlPropertyItem): EnumSupport? {
        return null
      }
    }

  @Test
  fun testEmptyFavorites() {
    val util = InspectorTestUtil(projectRule, TEXT_VIEW)
    val builder = FavoritesInspectorBuilder(util.model, enumSupportProvider)
    assertThat(builder.loadFavoritePropertiesIfNeeded()).isEmpty()
  }

  @Test
  fun testFavoritesParser() {
    PropertiesComponent.getInstance()
      .setValue(FAVORITES_PROPERTY, ":alpha;tools:gravity;:visibility;app:layout_constraintTag", "")
    val util = InspectorTestUtil(projectRule, TEXT_VIEW)
    val builder = FavoritesInspectorBuilder(util.model, enumSupportProvider)
    assertThat(builder.loadFavoritePropertiesIfNeeded())
      .containsExactly(
        ResourceReference.attr(ResourceNamespace.ANDROID, ATTR_ALPHA),
        ResourceReference.attr(ResourceNamespace.TOOLS, ATTR_GRAVITY),
        ResourceReference.attr(ResourceNamespace.ANDROID, ATTR_VISIBILITY),
        ResourceReference.attr(ResourceNamespace.RES_AUTO, ATTR_LAYOUT_CONSTRAINT_TAG),
      )
      .inOrder()
  }

  @Test
  fun testTextView() {
    PropertiesComponent.getInstance()
      .setValue(FAVORITES_PROPERTY, ":alpha;tools:gravity;:visibility;:non_exist;", "")
    val util = InspectorTestUtil(projectRule, TEXT_VIEW)
    val builder = FavoritesInspectorBuilder(util.model, enumSupportProvider)
    util.loadProperties()
    builder.attachToInspector(util.inspector, util.properties)
    util.checkTitle(0, InspectorSection.FAVORITES.title)
    val lineModel = util.checkTable(1)
    lineModel.checkItem(0, ANDROID_URI, ATTR_ALPHA)
    lineModel.checkItem(1, ANDROID_URI, ATTR_VISIBILITY)
    lineModel.checkItemCount(2)
  }

  @Test
  fun testAddFavorite() {
    PropertiesComponent.getInstance()
      .setValue(FAVORITES_PROPERTY, ":alpha;tools:gravity;:visibility;:non_exist;", "")
    val util = InspectorTestUtil(projectRule, TEXT_VIEW)
    val builder = FavoritesInspectorBuilder(util.model, enumSupportProvider)
    util.loadProperties()
    builder.attachToInspector(util.inspector, util.properties)
    util.performAction(0, 0, AllIcons.General.Add)

    // Check that there are 3 attributes (one placeholder)
    val lineModel = util.checkTable(1)
    lineModel.checkItem(0, ANDROID_URI, ATTR_ALPHA)
    lineModel.checkItem(1, ANDROID_URI, ATTR_VISIBILITY)
    lineModel.checkItem(2, "", "") // placeholder
    lineModel.checkItemCount(3)
  }

  @Test
  fun testAddFavoriteAndSelectProperty() {
    PropertiesComponent.getInstance()
      .setValue(FAVORITES_PROPERTY, ":alpha;tools:gravity;:visibility;", "")
    val util = InspectorTestUtil(projectRule, TEXT_VIEW)
    val builder = FavoritesInspectorBuilder(util.model, enumSupportProvider)
    util.loadProperties()
    builder.attachToInspector(util.inspector, util.properties)
    util.performAction(0, 0, AllIcons.General.Add)
    val newItem = util.checkTable(1).tableModel.items[2] as NlNewPropertyItem

    // Select a new favorite property:
    newItem.name = "android:text"
    val lineModel = util.checkTable(1)
    lineModel.checkItem(0, ANDROID_URI, ATTR_ALPHA)
    lineModel.checkItem(1, ANDROID_URI, ATTR_TEXT)
    lineModel.checkItem(2, ANDROID_URI, ATTR_VISIBILITY)
    lineModel.checkItem(3, "", "") // placeholder
    lineModel.checkItemCount(4)
    assertThat(PropertiesComponent.getInstance().getValue(FAVORITES_PROPERTY))
      .isEqualTo(":alpha;tools:gravity;:visibility;:text;")
  }

  @Test
  fun testAddFavoriteAndSelectApplicationProperty() {
    PropertiesComponent.getInstance()
      .setValue(FAVORITES_PROPERTY, ":alpha;tools:gravity;:visibility;", "")
    val util = InspectorTestUtil(projectRule, TEXT_VIEW, parentTag = CONSTRAINT_LAYOUT.newName())
    val builder = FavoritesInspectorBuilder(util.model, enumSupportProvider)
    util.loadProperties()
    util.addProperty(AUTO_URI, ATTR_LAYOUT_BOTTOM_TO_TOP_OF, NlPropertyType.ID)
    util.addProperty(AUTO_URI, ATTR_LAYOUT_TOP_TO_TOP_OF, NlPropertyType.ID)
    builder.attachToInspector(util.inspector, util.properties)
    util.performAction(0, 0, AllIcons.General.Add)
    val newItem = util.checkTable(1).tableModel.items[2] as NlNewPropertyItem

    // Select a new favorite property:
    newItem.name = "app:layout_constraintBottom_toTopOf"
    val lineModel = util.checkTable(1)
    lineModel.checkItem(0, AUTO_URI, ATTR_LAYOUT_BOTTOM_TO_TOP_OF)
    lineModel.checkItem(1, ANDROID_URI, ATTR_ALPHA)
    lineModel.checkItem(2, ANDROID_URI, ATTR_VISIBILITY)
    lineModel.checkItem(3, "", "") // placeholder
    lineModel.checkItemCount(4)
    assertThat(PropertiesComponent.getInstance().getValue(FAVORITES_PROPERTY))
      .isEqualTo(":alpha;tools:gravity;:visibility;app:layout_constraintBottom_toTopOf;")
  }

  @Test
  fun testRemoveFavorite() {
    PropertiesComponent.getInstance()
      .setValue(FAVORITES_PROPERTY, ":alpha;tools:gravity;:visibility;", "")
    val util = InspectorTestUtil(projectRule, TEXT_VIEW)
    val builder = FavoritesInspectorBuilder(util.model, enumSupportProvider)
    util.loadProperties()
    builder.attachToInspector(util.inspector, util.properties)
    val lineModel = util.checkTable(1)
    lineModel.selectedItem = lineModel.tableModel.items[0] // select ATTR_ALPHA
    util.performAction(0, 1, AllIcons.General.Remove)
    lineModel.checkItem(0, ANDROID_URI, ATTR_VISIBILITY)
    lineModel.checkItemCount(1)
    assertThat(PropertiesComponent.getInstance().getValue(FAVORITES_PROPERTY))
      .isEqualTo("tools:gravity;:visibility;")
  }

  @Test
  fun testPasteFavoriteFromClipboard() {
    PropertiesComponent.getInstance().setValue(FAVORITES_PROPERTY, "tools:gravity;:visibility;", "")
    val util = InspectorTestUtil(projectRule, TEXT_VIEW)
    val builder = FavoritesInspectorBuilder(util.model, enumSupportProvider)
    util.loadProperties()
    builder.attachToInspector(util.inspector, util.properties)
    val lineModel = util.checkTable(1)
    val table = PTable.create(lineModel.tableModel).component
    val transferHandler = table.transferHandler
    runReadAction { transferHandler.importData(table, StringSelection("backgroundTint\t#22FF22")) }
    assertThat(PropertiesComponent.getInstance().getValue(FAVORITES_PROPERTY))
      .isEqualTo("tools:gravity;:visibility;:backgroundTint;")
  }

  @Test
  fun testCutFavoriteViaClipboard() {
    PropertiesComponent.getInstance()
      .setValue(FAVORITES_PROPERTY, ":alpha;tools:gravity;:visibility;", "")
    val util = InspectorTestUtil(projectRule, TEXT_VIEW)
    val builder = FavoritesInspectorBuilder(util.model, enumSupportProvider)
    util.loadProperties()
    builder.attachToInspector(util.inspector, util.properties)
    val lineModel = util.checkTable(1)
    val table = PTable.create(lineModel.tableModel).component as JTable
    val transferHandler = table.transferHandler
    assertThat(transferHandler.getSourceActions(table)).isEqualTo(TransferHandler.COPY_OR_MOVE)
    runReadAction { table.setRowSelectionInterval(0, 0) }
    val clipboard: Clipboard = mock()
    runReadAction { transferHandler.exportToClipboard(table, clipboard, TransferHandler.MOVE) }
    assertThat(PropertiesComponent.getInstance().getValue(FAVORITES_PROPERTY))
      .isEqualTo("tools:gravity;:visibility;")
  }
}
