/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.scene;

import com.android.AndroidXConstants;
import com.android.tools.idea.common.command.NlWriteCommandActionUtil;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.scout.Scout;
import com.android.tools.idea.uibuilder.scout.ScoutConnectArrange;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.android.SdkConstants.*;

/**
 * Check that connections to parent if referenced by an id still works, also check the display list sorted result.
 */
public class ScoutConnectArrangeTest1 extends SceneTest {

  public ScoutConnectArrangeTest1() {
    super(false);
  }

  @Override
  @NotNull
  public ModelBuilder createModel() {
    return model("constraint.xml",
                 component(AndroidXConstants.CONSTRAINT_LAYOUT.defaultName())
                   .id("@+id/content_main")
                   .withBounds(0, 0, 2000, 2000)
                   .width("1000dp")
                   .height("1000dp")
                   .children(

                     component(TEXT_VIEW)
                       .id("@+id/textview4")
                       .withBounds(100, 750, 200, 40)
                       .width("100dp")
                       .height("40dp"),
                     component(TEXT_VIEW)
                       .id("@+id/textview2")
                       .withBounds(400, 1050, 200, 30)
                       .width("200dp")
                       .height("30dp"),
                     component(TEXT_VIEW)
                       .id("@+id/textview3")
                       .withBounds(650, 1150, 200, 50)
                       .width("200dp")
                       .height("50dp"),
                     component(AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_BARRIER.defaultName())
                       .id("@+id/barrier")
                       .withBounds(50, 750, 200, 40)
                       .withAttribute("app:barrierDirection", "left")
                       .width("100dp")
                       .height("40dp").children(component(TAG).id("@+id/textview1"))
                   ));
  }

  public void testCannotConnectToComponentItself() {
    myScreen.get("@+id/textview2")
      .expectXml("<TextView\n" +
                 "    android:id=\"@+id/textview2\"\n" +
                 "    android:layout_width=\"200dp\"\n" +
                 "    android:layout_height=\"30dp\"/>");
    List<NlComponent> list = new ArrayList<>(); // testing passing in an empty selection does not crash
    NlComponent textView2 = myScreen.get("@+id/textview2").getComponent();
    list.add(textView2);
    list.add(textView2);

    // cannot connect to itself no matter which connection it is.
    for (Scout.Connect connect : Scout.Connect.values()) {
      assertFalse(ScoutConnectArrange.connectCheck(list, connect,false));
      assertFalse(ScoutConnectArrange.connectCheck(list, connect,true));
    }
  }

  public void testAlignHorizontallyRight2() {
    myScreen.get("@+id/textview2")
      .expectXml("<TextView\n" +
                 "    android:id=\"@+id/textview2\"\n" +
                 "    android:layout_width=\"200dp\"\n" +
                 "    android:layout_height=\"30dp\"/>");

    List<NlComponent> list = new ArrayList<>(); // testing passing in an empty selection does not crash
    list.add(myScreen.get("@+id/textview2").getComponent());
    boolean result;

    result = ScoutConnectArrange.connectCheck(list, Scout.Connect.ConnectToParentTop,false);
    assertTrue(result);
    result = ScoutConnectArrange.connectCheck(list, Scout.Connect.ConnectToParentBottom,false);
    assertTrue(result);
    result = ScoutConnectArrange.connectCheck(list, Scout.Connect.ConnectToParentStart,false);
    assertTrue(result);
    result = ScoutConnectArrange.connectCheck(list, Scout.Connect.ConnectToParentEnd,false);
    assertTrue(result);

    list.add(myScreen.get("@+id/textview3").getComponent());
    result = ScoutConnectArrange.connectCheck(list, Scout.Connect.ConnectTopToTop,false);
    assertTrue(result);
    result = ScoutConnectArrange.connectCheck(list, Scout.Connect.ConnectTopToBottom,false);
    assertTrue(result);
    result = ScoutConnectArrange.connectCheck(list, Scout.Connect.ConnectBottomToBottom,false);
    assertTrue(result);
    result = ScoutConnectArrange.connectCheck(list, Scout.Connect.ConnectBottomToTop,false);
    assertTrue(result);
    result = ScoutConnectArrange.connectCheck(list, Scout.Connect.ConnectStartToStart,false);
    assertTrue(result);
    result = ScoutConnectArrange.connectCheck(list, Scout.Connect.ConnectStartToEnd,false);
    assertTrue(result);
    result = ScoutConnectArrange.connectCheck(list, Scout.Connect.ConnectEndToEnd,false);
    assertTrue(result);
    result = ScoutConnectArrange.connectCheck(list, Scout.Connect.ConnectEndToStart,false);
    assertTrue(result);
    result = ScoutConnectArrange.connectCheck(list, Scout.Connect.ConnectBaseLineToBaseLine,false);
    assertTrue(result);

    myScreen.get("@+id/textview2")
            .expectXml("<TextView\n" +
                       "    android:id=\"@+id/textview2\"\n" +
                       "    android:layout_width=\"200dp\"\n" +
                       "    android:layout_height=\"30dp\"/>");
    myScreen.get("@+id/textview3")
            .expectXml("<TextView\n" +
                       "    android:id=\"@+id/textview3\"\n" +
                       "    android:layout_width=\"200dp\"\n" +
                       "    android:layout_height=\"50dp\"/>");
    ScoutConnectArrange.connect(list, Scout.Connect.ConnectTopToTop,false,false);
    NlWriteCommandActionUtil
    .run(list, Scout.Arrange.ConnectTop.toString(), () -> list.forEach(component -> component.startAttributeTransaction().commit()));
    myScreen.get("@+id/textview2")
            .expectXml("<TextView\n" +
                       "        android:id=\"@+id/textview2\"\n" +
                       "        android:layout_width=\"200dp\"\n" +
                       "        android:layout_height=\"30dp\"\n" +
                       "        app:layout_constraintTop_toTopOf=\"@+id/textview3\"\n" +
                       "        tools:layout_editor_absoluteX=\"200dp\" />");
    myScreen.get("@+id/textview3")
            .expectXml("<TextView\n" +
                       "        android:id=\"@+id/textview3\"\n" +
                       "        android:layout_width=\"200dp\"\n" +
                       "        android:layout_height=\"50dp\"\n" +
                       "        tools:layout_editor_absoluteX=\"325dp\"\n" +
                       "        tools:layout_editor_absoluteY=\"575dp\" />");
    ScoutConnectArrange.connect(list, Scout.Connect.ConnectStartToEnd,false,true);
    NlWriteCommandActionUtil
      .run(list, Scout.Arrange.ConnectTop.toString(), () -> list.forEach(component -> component.startAttributeTransaction().commit()));

    myScreen.get("@+id/textview2")
            .expectXml("<TextView\n" +
                       "        android:id=\"@+id/textview2\"\n" +
                       "        android:layout_width=\"200dp\"\n" +
                       "        android:layout_height=\"30dp\"\n" +
                       "        app:layout_constraintStart_toEndOf=\"@+id/textview3\"\n" +
                       "        app:layout_constraintTop_toTopOf=\"@+id/textview3\" />");
    myScreen.get("@+id/textview3")
            .expectXml("<TextView\n" +
                       "        android:id=\"@+id/textview3\"\n" +
                       "        android:layout_width=\"200dp\"\n" +
                       "        android:layout_height=\"50dp\"\n" +
                       "        tools:layout_editor_absoluteX=\"325dp\"\n" +
                       "        tools:layout_editor_absoluteY=\"575dp\" />");

    result = ScoutConnectArrange.connectCheck(list, Scout.Connect.ConnectTopToTop,false);
    assertTrue(result);
    result = ScoutConnectArrange.connectCheck(list, Scout.Connect.ConnectTopToBottom,false);
    assertTrue(result);
    result = ScoutConnectArrange.connectCheck(list, Scout.Connect.ConnectBottomToBottom,false);
    assertTrue(result);
    result = ScoutConnectArrange.connectCheck(list, Scout.Connect.ConnectBottomToTop,false);
    assertTrue(result);
    result = ScoutConnectArrange.connectCheck(list, Scout.Connect.ConnectStartToStart,false);
    assertTrue(result);
    result = ScoutConnectArrange.connectCheck(list, Scout.Connect.ConnectStartToEnd,false);
    assertTrue(result);
    result = ScoutConnectArrange.connectCheck(list, Scout.Connect.ConnectEndToEnd,false);
    assertTrue(result);
    result = ScoutConnectArrange.connectCheck(list, Scout.Connect.ConnectEndToStart,false);
    assertTrue(result);
    result = ScoutConnectArrange.connectCheck(list, Scout.Connect.ConnectBaseLineToBaseLine,false);
    assertTrue(result);


    result = ScoutConnectArrange.connectCheck(list, Scout.Connect.ConnectTopToTop,true);
    assertFalse(result);
    result = ScoutConnectArrange.connectCheck(list, Scout.Connect.ConnectTopToBottom,true);
    assertFalse(result);
    result = ScoutConnectArrange.connectCheck(list, Scout.Connect.ConnectBottomToBottom,true);
    assertFalse(result);
    result = ScoutConnectArrange.connectCheck(list, Scout.Connect.ConnectBottomToTop,true);
    assertFalse(result);
    result = ScoutConnectArrange.connectCheck(list, Scout.Connect.ConnectStartToStart,true);
    assertFalse(result);
    result = ScoutConnectArrange.connectCheck(list, Scout.Connect.ConnectStartToEnd,true);
    assertFalse(result);
    result = ScoutConnectArrange.connectCheck(list, Scout.Connect.ConnectEndToEnd,true);
    assertFalse(result);
    result = ScoutConnectArrange.connectCheck(list, Scout.Connect.ConnectEndToStart,true);
    assertFalse(result);
    result = ScoutConnectArrange.connectCheck(list, Scout.Connect.ConnectBaseLineToBaseLine,true);
    assertFalse(result);

    ScoutConnectArrange.connect(list, Scout.Connect.ConnectBottomToBottom,false,true);
    NlWriteCommandActionUtil
      .run(list, Scout.Arrange.ConnectTop.toString(), () -> list.forEach(component -> component.startAttributeTransaction().commit()));

    myScreen.get("@+id/textview2")
            .expectXml("<TextView\n" +
                       "        android:id=\"@+id/textview2\"\n" +
                       "        android:layout_width=\"200dp\"\n" +
                       "        android:layout_height=\"30dp\"\n" +
                       "        android:layout_marginBottom=\"60dp\"\n" +
                       "        app:layout_constraintBottom_toBottomOf=\"@+id/textview3\"\n" +
                       "        app:layout_constraintStart_toEndOf=\"@+id/textview3\"\n" +
                       "        app:layout_constraintTop_toTopOf=\"@+id/textview3\" />");
    myScreen.get("@+id/textview3")
            .expectXml("<TextView\n" +
                       "        android:id=\"@+id/textview3\"\n" +
                       "        android:layout_width=\"200dp\"\n" +
                       "        android:layout_height=\"50dp\"\n" +
                       "        tools:layout_editor_absoluteX=\"325dp\"\n" +
                       "        tools:layout_editor_absoluteY=\"575dp\" />");



    ScoutConnectArrange.connect(list, Scout.Connect.ConnectEndToEnd,false,true);
    NlWriteCommandActionUtil
      .run(list, Scout.Arrange.ConnectTop.toString(), () -> list.forEach(component -> component.startAttributeTransaction().commit()));
    myScreen.get("@+id/textview2")
            .expectXml("<TextView\n" +
                       "        android:id=\"@+id/textview2\"\n" +
                       "        android:layout_width=\"200dp\"\n" +
                       "        android:layout_height=\"30dp\"\n" +
                       "        android:layout_marginEnd=\"125dp\"\n" +
                       "        android:layout_marginRight=\"125dp\"\n" +
                       "        android:layout_marginBottom=\"60dp\"\n" +
                       "        app:layout_constraintBottom_toBottomOf=\"@+id/textview3\"\n" +
                       "        app:layout_constraintEnd_toEndOf=\"@+id/textview3\"\n" +
                       "        app:layout_constraintStart_toEndOf=\"@+id/textview3\"\n" +
                       "        app:layout_constraintTop_toTopOf=\"@+id/textview3\" />");
    myScreen.get("@+id/textview3")
            .expectXml("<TextView\n" +
                       "        android:id=\"@+id/textview3\"\n" +
                       "        android:layout_width=\"200dp\"\n" +
                       "        android:layout_height=\"50dp\"\n" +
                       "        tools:layout_editor_absoluteX=\"325dp\"\n" +
                       "        tools:layout_editor_absoluteY=\"575dp\" />");

    List<NlComponent>  list2 = new ArrayList<>(); // testing passing in an empty selection does not crash
    list2.add(myScreen.get("@+id/textview4").getComponent());
    list2.add(myScreen.get("@+id/textview3").getComponent());


    ScoutConnectArrange.connect(list2, Scout.Connect.ConnectTopToBottom,true,true);
    NlWriteCommandActionUtil
      .run(list2, Scout.Arrange.ConnectTop.toString(), () -> list2.forEach(component -> component.startAttributeTransaction().commit()));
    myScreen.get("@+id/textview3")
            .expectXml("<TextView\n" +
                       "        android:id=\"@+id/textview3\"\n" +
                       "        android:layout_width=\"200dp\"\n" +
                       "        android:layout_height=\"50dp\"\n" +
                       "        android:layout_marginTop=\"180dp\"\n" +
                       "        app:layout_constraintTop_toBottomOf=\"@+id/textview4\"\n" +
                       "        tools:layout_editor_absoluteX=\"325dp\" />");


    ScoutConnectArrange.connect(list2, Scout.Connect.ConnectBottomToTop,true,true);
    NlWriteCommandActionUtil
      .run(list2, Scout.Arrange.ConnectTop.toString(), () -> list2.forEach(component -> component.startAttributeTransaction().commit()));
    myScreen.get("@+id/textview3")
            .expectXml("<TextView\n" +
                       "        android:id=\"@+id/textview3\"\n" +
                       "        android:layout_width=\"200dp\"\n" +
                       "        android:layout_height=\"50dp\"\n" +
                       "        android:layout_marginTop=\"180dp\"\n" +
                       "        app:layout_constraintBottom_toTopOf=\"@+id/textview4\"\n" +
                       "        app:layout_constraintTop_toBottomOf=\"@+id/textview4\"\n" +
                       "        tools:layout_editor_absoluteX=\"325dp\" />");
    ScoutConnectArrange.connect(list2, Scout.Connect.ConnectStartToStart,true,true);
    NlWriteCommandActionUtil
      .run(list2, Scout.Arrange.ConnectTop.toString(), () -> list2.forEach(component -> component.startAttributeTransaction().commit()));
    myScreen.get("@+id/textview3")
            .expectXml("<TextView\n" +
                       "        android:id=\"@+id/textview3\"\n" +
                       "        android:layout_width=\"200dp\"\n" +
                       "        android:layout_height=\"50dp\"\n" +
                       "        android:layout_marginStart=\"275dp\"\n" +
                       "        android:layout_marginLeft=\"275dp\"\n" +
                       "        android:layout_marginTop=\"180dp\"\n" +
                       "        app:layout_constraintBottom_toTopOf=\"@+id/textview4\"\n" +
                       "        app:layout_constraintStart_toStartOf=\"@+id/textview4\"\n" +
                       "        app:layout_constraintTop_toBottomOf=\"@+id/textview4\" />");
    ScoutConnectArrange.connect(list2, Scout.Connect.ConnectEndToStart,true,true);
    NlWriteCommandActionUtil
      .run(list2, Scout.Arrange.ConnectTop.toString(), () -> list2.forEach(component -> component.startAttributeTransaction().commit()));
    myScreen.get("@+id/textview3")
            .expectXml("<TextView\n" +
                       "        android:id=\"@+id/textview3\"\n" +
                       "        android:layout_width=\"200dp\"\n" +
                       "        android:layout_height=\"50dp\"\n" +
                       "        android:layout_marginStart=\"275dp\"\n" +
                       "        android:layout_marginLeft=\"275dp\"\n" +
                       "        android:layout_marginTop=\"180dp\"\n" +
                       "        app:layout_constraintBottom_toTopOf=\"@+id/textview4\"\n" +
                       "        app:layout_constraintEnd_toStartOf=\"@+id/textview4\"\n" +
                       "        app:layout_constraintStart_toStartOf=\"@+id/textview4\"\n" +
                       "        app:layout_constraintTop_toBottomOf=\"@+id/textview4\" />");



    buildScene();
  }


}