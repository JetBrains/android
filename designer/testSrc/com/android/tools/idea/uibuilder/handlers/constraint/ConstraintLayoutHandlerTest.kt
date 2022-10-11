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
package com.android.tools.idea.uibuilder.handlers.constraint

import com.android.AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_GROUP
import com.android.SdkConstants.CLASS_VIEW
import com.android.AndroidXConstants.CONSTRAINT_LAYOUT
import com.android.AndroidXConstants.CONSTRAINT_LAYOUT_BARRIER
import com.android.AndroidXConstants.RECYCLER_VIEW
import com.android.SdkConstants.TAG_LAYOUT
import com.android.SdkConstants.TEXT_VIEW
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.uibuilder.fixtures.ScreenFixture
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintLayoutHandler.getSelectedIds
import com.android.tools.idea.uibuilder.model.viewGroupHandler
import com.android.tools.idea.uibuilder.scene.SceneTest
import org.mockito.Mockito

class ConstraintLayoutHandlerTest: SceneTest() {

  fun testClearConstraintAttributesWithNoComponents() {
    val nlModel = model("constraint.xml",
                        component(CONSTRAINT_LAYOUT.defaultName())
                          .id("@+id/root")
                          .withBounds(0, 0, 2000, 2000)
                          .width("1000dp")
                          .height("1000dp"))
      .build()

    val handler = nlModel.find("root")!!.viewGroupHandler!!
    assertNoException<IllegalArgumentException>(IllegalArgumentException::class.java) {
        handler.clearAttributes(listOf())
      }
  }

  fun testClearConstraintAttributes() {
    val handler = myModel.find("root")!!.viewGroupHandler!!
    val button1 = myModel.find("button1")!!
    val text1 = myModel.find("text1")!!
    val barrier1 = myModel.find("barrier1")!!
    val group1 = myModel.find("group1")!!
    val recyclerView = myModel.find("recycler_view")!!

    handler.clearAttributes(listOf(button1, text1, recyclerView, barrier1, group1))

    myScreen.get("@+id/button1")
      .expectXml("<TextView\n" +
                 "        android:id=\"@+id/button1\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        tools:layout_editor_absoluteX=\"450dp\"\n" +
                 "        tools:layout_editor_absoluteY=\"490dp\" />")
    myScreen.get("@+id/text1")
      .expectXml("<TextView\n" +
                 "        android:id=\"@+id/text1\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        tools:layout_editor_absoluteX=\"450dp\"\n" +
                 "        tools:layout_editor_absoluteY=\"490dp\" />")
    myScreen.get("@+id/recycler_view")
      .expectXml("<" + RECYCLER_VIEW.newName() + "\n" +
                 "        android:id=\"@+id/recycler_view\"\n" +
                 "        android:layout_width=\"860dp\"\n" +
                 "        android:layout_height=\"860dp\"\n" +
                 "        tools:layout_editor_absoluteX=\"70dp\"\n" +
                 "        tools:layout_editor_absoluteY=\"70dp\" />")
    myScreen.get("@+id/barrier1")
      .expectXml("<android.support.constraint.Barrier\n" +
                 "        android:id=\"@+id/barrier1\"\n" +
                 "        android:layout_width=\"wrap_content\"\n" +
                 "        android:layout_height=\"wrap_content\"\n" +
                 "        app:barrierDirection=\"left\"\n" +
                 "        app:constraint_referenced_ids=\"button1,text1\" />")
    myScreen.get("@+id/group1")
      .expectXml("<android.support.constraint.Group\n" +
                 "        android:id=\"@+id/group1\"\n" +
                 "        android:layout_width=\"wrap_content\"\n" +
                 "        android:layout_height=\"wrap_content\"\n" +
                 "        android:visibility=\"visible\"\n" +
                 "        app:constraint_referenced_ids=\"button1,text1\" />")
  }

  override fun createModel(): ModelBuilder {
    return model("constraint.xml",
                 component(CONSTRAINT_LAYOUT.defaultName())
                   .id("@+id/root")
                   .withBounds(0, 0, 2000, 2000)
                   .width("1000dp")
                   .height("1000dp")
                   .withAttribute("android:padding", "20dp")
                   .children(
                     component(TEXT_VIEW)
                       .id("@+id/button1")
                       .withBounds(900, 980, 200, 40)
                       .width("100dp")
                       .height("20dp")
                       .withAttribute("app:layout_constraintLeft_toLeftOf", "parent")
                       .withAttribute("app:layout_constraintRight_toRightOf", "parent")
                       .withAttribute("app:layout_constraintTop_toTopOf", "parent")
                       .withAttribute("app:layout_constraintBottom_toBottomOf", "parent"),
                     component(TEXT_VIEW)
                       .id("@+id/text1")
                       .withBounds(900, 980, 200, 40)
                       .width("0dp")
                       .height("0dp")
                       .withAttribute("app:layout_constraintLeft_toLeftOf", "@+id/button1")
                       .withAttribute("app:layout_constraintRight_toRightOf", "@+id/button1")
                       .withAttribute("app:layout_constraintTop_toTopOf", "@+id/button1")
                       .withAttribute("app:layout_constraintBottom_toBottomOf", "parent"),
                     component(RECYCLER_VIEW.newName())
                       .id("@+id/recycler_view")
                       .withBounds(140, 140, 1720, 1720)
                       .width("860dp")
                       .height("860dp")
                       .withAttribute("android:layout_marginStart", "50dp")
                       .withAttribute("android:layout_marginTop", "50dp")
                       .withAttribute("android:layout_marginEnd", "50dp")
                       .withAttribute("android:layout_marginBottom", "50dp")
                       .withAttribute("app:layout_constraintStart_toStartOf", "parent")
                       .withAttribute("app:layout_constraintEnd_toEndOf", "parent")
                       .withAttribute("app:layout_constraintTop_toTopOf", "parent")
                       .withAttribute("app:layout_constraintBottom_toBottomOf", "parent"),
                     component(CONSTRAINT_LAYOUT_BARRIER.defaultName())
                       .id("@+id/barrier1")
                       .width("wrap_content")
                       .height("wrap_content")
                       .withAttribute("app:barrierDirection", "left")
                       .withAttribute("app:constraint_referenced_ids", "button1,text1")
                       .withAttribute("tools:layout_editor_absoluteX", "56dp")
                       .withAttribute("tools:layout_editor_absoluteY", "81dp"),
                     component(CLASS_CONSTRAINT_LAYOUT_GROUP.defaultName())
                       .id("@+id/group1")
                       .width("wrap_content")
                       .height("wrap_content")
                       .withAttribute("android:visibility", "visible")
                       .withAttribute("app:constraint_referenced_ids", "button1,text1")
                       .withAttribute("tools:layout_editor_absoluteX", "99dp")
                       .withAttribute("tools:layout_editor_absoluteY", "109dp")
                   ))
  }

  fun testClearAttributesWithDataBinding() {
    val model = createDataBindingModel().build()
    val screen = ScreenFixture(model)
    screen.get("@id/button")
      .expectXml("<TextView\n" +
                 "    android:id=\"@id/button\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"20dp\"\n" +
                 "    app:layout_constraintLeft_toLeftOf=\"parent\"\n" +
                 "    app:layout_constraintRight_toRightOf=\"parent\"\n" +
                 "    app:layout_constraintTop_toTopOf=\"parent\"\n" +
                 "    app:layout_constraintBottom_toBottomOf=\"parent\"/>")
    screen.get("@id/button2")
      .expectXml("<TextView\n" +
                 "    android:id=\"@id/button2\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"20dp\"\n" +
                 "    app:layout_constraintLeft_toLeftOf=\"@+id/button\"\n" +
                 "    app:layout_constraintTop_toBottomOf=\"@+id/button\"\n" +
                 "    android:layout_marginTop=\"16dp\"/>")

    val button = model.find("button")!!
    val button2 = model.find("button2")!!

    val handler = model.find("root")!!.viewGroupHandler!!
    handler.clearAttributes(listOf(button, button2))

    screen.get("@id/button")
      .expectXml("<TextView\n" +
                 "        android:id=\"@id/button\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        tools:layout_editor_absoluteX=\"450dp\"\n" +
                 "        tools:layout_editor_absoluteY=\"490dp\" />")
    screen.get("@id/button2")
      .expectXml("<TextView\n" +
                 "        android:id=\"@id/button2\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        tools:layout_editor_absoluteX=\"450dp\"\n" +
                 "        tools:layout_editor_absoluteY=\"526dp\" />")
  }

  fun testSelectedIdsNull() {
    val list = ArrayList<NlComponent>()
    assertNull(getSelectedIds(list))

    list.add(nonViewMockedComponent("button"))
    list.add(nonViewMockedComponent("button2"))

    assertNull(getSelectedIds(list))
  }

  fun testSelectedIds() {
    val model = createTestModel()
    val list = ArrayList<NlComponent>()
    list.add(model.find("view1")!!)
    list.add(model.find("view2")!!)
    list.add(model.find("non_view1")!!)

    assertEquals("view1,view2", getSelectedIds(list))
  }

  private fun nonViewMockedComponent(id: String): NlComponent {
    val component = Mockito.mock(NlComponent::class.java)
    whenever(component.id).thenReturn(id)
    return component
  }

  private fun createTestModel(): NlModel {
    return model("constraint.xml",
                 component(TAG_LAYOUT)
                   .children(
                     component(CONSTRAINT_LAYOUT.defaultName())
                       .id("@id/root")
                       .children(
                         component(CLASS_VIEW)
                           .id("@id/view1"),
                         component(CLASS_VIEW)
                           .id("@id/view2"),
                         component("Non-view-component")
                           .id("@id/non_view1")
                       )
                   )
    ).build()
  }

  private fun createDataBindingModel(): ModelBuilder {
    return model("constraint.xml",
                 component(TAG_LAYOUT)
                   .withBounds(0, 0, 2000, 2000)
                   .children(
                     component(CONSTRAINT_LAYOUT.defaultName())
                       .id("@id/root")
                       .withBounds(0, 0, 2000, 2000)
                       .width("1000dp")
                       .height("1000dp")
                       .withAttribute("android:padding", "20dp")
                       .children(
                         component(TEXT_VIEW)
                           .id("@id/button")
                           .withBounds(900, 980, 200, 40)
                           .width("100dp")
                           .height("20dp")
                           .withAttribute("app:layout_constraintLeft_toLeftOf", "parent")
                           .withAttribute("app:layout_constraintRight_toRightOf", "parent")
                           .withAttribute("app:layout_constraintTop_toTopOf", "parent")
                           .withAttribute("app:layout_constraintBottom_toBottomOf", "parent"),
                         component(TEXT_VIEW)
                           .id("@id/button2")
                           .withBounds(900, 1052, 200, 40)
                           .width("100dp")
                           .height("20dp")
                           .withAttribute("app:layout_constraintLeft_toLeftOf", "@+id/button")
                           .withAttribute("app:layout_constraintTop_toBottomOf", "@+id/button")
                           .withAttribute("android:layout_marginTop", "16dp")
                       )
                   )
    )
  }
}
