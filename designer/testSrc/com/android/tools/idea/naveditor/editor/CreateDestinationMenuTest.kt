// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.naveditor.editor

import com.android.SdkConstants.ATTR_LABEL
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.naveditor.NavModelBuilderUtil.*
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.android.utils.Pair
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.intellij.psi.PsiClass
import org.jetbrains.android.dom.navigation.NavigationSchema
import org.mockito.Mockito
import java.util.*
import javax.swing.JLabel

// TODO: testing with custom navigators
class CreateDestinationMenuTest : NavTestCase() {

  private var _model: SyncNlModel? = null
  private val model
      get() = _model!!
  
  private var _surface: NavDesignSurface? = null
  private val surface
    get() = _surface!!
  
  private var _menu: CreateDestinationMenu? = null
  private val menu
    get() = _menu!!

  private val myItemsByType = HashMap<NavigationSchema.DestinationType?, Pair<String, PsiClass>>()

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    _model = model("nav.xml",
        rootComponent("navigation").unboundedChildren(
            fragmentComponent("fragment1"),
            navigationComponent("subnav")
                .unboundedChildren(fragmentComponent("fragment2"))))
        .build()
    _surface = NavDesignSurface(project, myRootDisposable)
    surface.setSize(1000, 1000)
    surface.model = model
    _menu = CreateDestinationMenu(surface)
    menu.createCustomComponentPopup()

    val schema = NavigationSchema.get(myFacet)
    val model = menu.myKindPopup.model
    for (i in 0 until model.size) {
      val entry = model.getElementAt(i)
      val psiClass = entry.second
      myItemsByType.put(if (psiClass == null) null else schema.getTypeForNavigatorClass(psiClass), entry)
    }
  }

  @Throws(Exception::class)
  override fun tearDown() {
    _model = null
    _menu = null
    _surface = null
    super.tearDown()
  }

  fun testFragmentValidation() {
    menu.myKindPopup.selectedItem = myItemsByType[NavigationSchema.DestinationType.FRAGMENT]
    menu.myIdField.text = "myId"
    menu.myLabelField.text = "myLabel"
    assertTrue(menu.validate())
    assertFalse(menu.myValidationLabel.isVisible)

    menu.myIdField.text = ""
    assertFalse(menu.validate())
    assertTrue(menu.myValidationLabel.isVisible)
    menu.myIdField.text = "myId"

    assertTrue(menu.validate())
    assertFalse(menu.myValidationLabel.isVisible)

    menu.myLabelField.text = ""
    assertFalse(menu.validate())
    assertTrue(menu.myValidationLabel.isVisible)
  }

  fun testActivityValidation() {
    menu.myKindPopup.selectedItem = myItemsByType[NavigationSchema.DestinationType.ACTIVITY]
    menu.myIdField.text = "myId"
    menu.myLabelField.text = "myLabel"
    assertTrue(menu.validate())
    assertFalse(menu.myValidationLabel.isVisible)

    menu.myIdField.text = ""
    assertFalse(menu.validate())
    assertTrue(menu.myValidationLabel.isVisible)
    menu.myIdField.text = "myId"

    assertTrue(menu.validate())
    assertFalse(menu.myValidationLabel.isVisible)

    menu.myLabelField.text = ""
    assertFalse(menu.validate())
    assertTrue(menu.myValidationLabel.isVisible)
  }

  fun testNestedValidation() {
    menu.myKindPopup.selectedItem = myItemsByType[NavigationSchema.DestinationType.NAVIGATION]
    menu.myIdField.text = "myId"
    menu.myLabelField.text = "myLabel"
    assertTrue(menu.validate())
    assertFalse(menu.myValidationLabel.isVisible)

    menu.myIdField.text = ""
    assertFalse(menu.validate())
    assertTrue(menu.myValidationLabel.isVisible)
    menu.myIdField.text = "myId"

    assertTrue(menu.validate())
    assertFalse(menu.myValidationLabel.isVisible)

    menu.myLabelField.text = ""
    assertFalse(menu.validate())
    assertTrue(menu.myValidationLabel.isVisible)
  }

  fun testIncludeValidation() {
    // TODO: implement create new included graph
    /*
    menu.myKindPopup.setSelectedItem(myItemsByType.get(null));
    menu.myIdField.setText("myId");
    menu.myLabelField.setText("myLabel");
    // TODO: validate "source"

    assertTrue(menu.validate());
    assertFalse(menu.myValidationLabel.isVisible());

    menu.myIdField.setText("");
    assertFalse(menu.validate());
    assertTrue(menu.myValidationLabel.isVisible());
    menu.myIdField.setText("myId");

    assertTrue(menu.validate());
    assertFalse(menu.myValidationLabel.isVisible());

    menu.myLabelField.setText("");
    assertFalse(menu.validate());
    assertTrue(menu.myValidationLabel.isVisible());
    */
  }

  fun testVisibleComponents() {
    menu.myKindPopup.selectedItem = myItemsByType[NavigationSchema.DestinationType.ACTIVITY]
    assertFalse(menu.mySourceField.isVisible)
    assertFalse(menu.mySourceLabel.isVisible)
    assertTrue(menu.myIdField.isVisible)
    assertTrue(menu.myLabelField.isVisible)
    assertTrue(menu.myLabelLabel.isVisible)
    assertTrue(menu.myIdLabel.isVisible)

    menu.myKindPopup.selectedItem = myItemsByType[NavigationSchema.DestinationType.FRAGMENT]
    assertFalse(menu.mySourceField.isVisible)
    assertFalse(menu.mySourceLabel.isVisible)
    assertTrue(menu.myIdField.isVisible)
    assertTrue(menu.myLabelField.isVisible)
    assertTrue(menu.myLabelLabel.isVisible)
    assertTrue(menu.myIdLabel.isVisible)

    menu.myKindPopup.selectedItem = myItemsByType[NavigationSchema.DestinationType.NAVIGATION]
    assertFalse(menu.mySourceField.isVisible)
    assertFalse(menu.mySourceLabel.isVisible)
    assertTrue(menu.myIdField.isVisible)
    assertTrue(menu.myLabelField.isVisible)
    assertTrue(menu.myLabelLabel.isVisible)
    assertTrue(menu.myIdLabel.isVisible)

    // TODO: implement create "include"
    /*
    menu.myKindPopup.setSelectedItem(myItemsByType.get(null));
    assertTrue(menu.mySourceField.isVisible());
    assertTrue(menu.mySourceLabel.isVisible());
    assertFalse(menu.myIdField.isVisible());
    assertFalse(menu.myLabelField.isVisible());
    assertFalse(menu.myLabelLabel.isVisible());
    assertFalse(menu.myIdLabel.isVisible());
    */
  }

  fun testCreateNested() {
    menu.myKindPopup.selectedItem = myItemsByType[NavigationSchema.DestinationType.NAVIGATION]
    menu.myIdField.text = "myId"
    menu.myLabelField.text = "myLabel"

    menu.createDestination()

    val added = model.find("myId")!!
    assertEquals(TAG_NAVIGATION, added.tagName)
    assertEquals("myLabel", added.getAndroidAttribute(ATTR_LABEL))
  }

  fun testCreateFragment() {
    val menu = Mockito.spy(menu)

    menu.myKindPopup.selectedItem = myItemsByType[NavigationSchema.DestinationType.FRAGMENT]
    menu.myIdField.text = "myId"
    menu.myLabelField.text = "myLabel"

    menu.createDestination()

    val added = model.find("myId")!!
    assertEquals("fragment", added.tagName)
    assertEquals("myLabel", added.getAndroidAttribute(ATTR_LABEL))
  }

  fun testCreateActivity() {
    val menu = Mockito.spy(menu)

    menu.myKindPopup.selectedItem = myItemsByType[NavigationSchema.DestinationType.ACTIVITY]
    menu.myIdField.text = "myId"
    menu.myLabelField.text = "myLabel"

    menu.createDestination()

    val added = model.find("myId")!!
    assertEquals("activity", added.tagName)
    assertEquals("myLabel", added.getAndroidAttribute(ATTR_LABEL))
    assertEquals(ImmutableList.of(added), surface.selectionModel.selection)
  }

  fun testCreateInclude() {
    // TODO: implement create new included graph
    /*
    AddMenuWrapper menu = Mockito.spy(menu);

    menu.myKindPopup.setSelectedItem(myItemsByType.get(null));
    menu.myIdField.setText("myId");
    menu.myLabelField.setText("myLabel");
    menu.mySourceField.setSelectedItem("navigation.xml");

    menu.createDestination();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Consumer<NlComponent>> consumerArg = ArgumentCaptor.forClass(Consumer.class);
    Mockito.verify(menu)
      .addElement(eq(surface), eq("include"), eq("navigation"), isNull(), eq("myLabel"), consumerArg.capture());

    NlComponent component = Mockito.mock(NlComponent.class);
    consumerArg.getValue().accept(component);
    Mockito.verify(component).setAttribute(AUTO_URI, "graph", "@navigation/navigation");
    */
  }

  fun testUniqueId() {
    menu.myKindPopup.selectedItem = myItemsByType[NavigationSchema.DestinationType.FRAGMENT]
    assertEquals("fragment", menu.myIdField.text)
    menu.createDestination()
    val menu2 = CreateDestinationMenu(surface)
    menu2.createCustomComponentPopup()
    menu2.myKindPopup.selectedItem = myItemsByType[NavigationSchema.DestinationType.FRAGMENT]
    assertEquals("fragment2", menu2.myIdField.text)
  }

  fun testKindPopup() {
    val popup = menu.myKindPopup
    val renderer = popup.renderer
    val result = HashSet<String>()
    for (i in 0 until popup.itemCount) {
      result.add((renderer.getListCellRendererComponent(null, popup.getItemAt(i), i, false, false) as JLabel).text)
    }
    assertEquals(ImmutableSet.of(/*"Include Graph",*/"Nested Graph", "Fragment", "Activity"), result)
    assertEquals(myItemsByType[NavigationSchema.DestinationType.FRAGMENT], popup.selectedItem)
  }

  fun testSourcePopup() {
    // TODO: implement create new included graph
    /*
    @Language("XML")
    String source = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<navigation xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                    "  android:id=\"@+id/nav2\">\n" +
                    "</navigation>\n";
    myFixture.addFileToProject("res/navigation/nav2.xml", source);
    ComboBox<String> popup = menu.mySourceField;
    ListCellRenderer<? super String> renderer = popup.getRenderer();
    Set<String> result = new HashSet<>();
    for (int i = 0; i < popup.getItemCount(); i++) {
      result.add(((JLabel)renderer.getListCellRendererComponent(null, popup.getItemAt(i), i, false, false)).getText());
    }
    assertEquals(ImmutableSet.of("navigation.xml", "New..."), result);*/
  }
}
