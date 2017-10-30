/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.relative;

import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.common.util.NlTreeDumper;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.model.TestAndroidModel;
import com.android.tools.idea.refactoring.rtl.RtlSupportProcessor;
import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.fixtures.ScreenFixture;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.LayoutTestUtilities.mockViewWithBaseline;
import static com.android.tools.idea.uibuilder.model.SegmentType.*;
import static java.awt.event.InputEvent.SHIFT_MASK;

public class RelativeLayoutHandlerTest extends LayoutTestCase {

  @Override
  protected void setUp() throws Exception {
    StudioFlags.NELE_TARGET_RELATIVE.override(false);
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      StudioFlags.NELE_TARGET_RELATIVE.clearOverride();
    }
    finally {
      super.tearDown();
    }
  }

  public void testResizeToNowhereWithModifierKeepsPosition() {
    screen(createModel())
      .get("@id/checkbox")
      .resize(TOP, LEFT)
      .drag(0, 0)
      .modifiers(SHIFT_MASK)
      .release()
      .expectXml("<CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_marginLeft=\"50dp\"\n" +
                 "        android:layout_marginTop=\"50dp\"\n" +
                 "        android:layout_toRightOf=\"@+id/button\"\n" +
                 "        android:layout_below=\"@+id/button\" />");
  }

  public void testResizeTopRemovesVerticalConstraint() {
    screen(createModel())
      .get("@id/checkbox")
      .resize(TOP)
      .drag(10, 10)
      .release()
      .expectXml("<CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_marginLeft=\"100dp\"\n" +
                 "        android:layout_toRightOf=\"@id/button\" />");
  }

  public void testResizeLeftRemovesHorizontalConstraint() {
    screen(createModel())
      .get("@id/checkbox")
      .resize(LEFT)
      .drag(10, 10)
      .release()
      .expectXml("<CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_marginTop=\"100dp\"\n" +
                 "        android:layout_below=\"@id/button\" />");
  }

  public void testResizeTopLeftSnapToLeftOfButton() {
    screen(createModel())
      .get("@id/checkbox")
      .resize(TOP, LEFT)
      .drag(-195, 10)
      .release()
      .expectXml("<CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_alignLeft=\"@+id/button\" />");
  }

  // Resize left, top: snap to top edge of button
  public void testResizeTopLeftSnapToTopOfButton() {
    screen(createModel())
      .get("@id/checkbox")
      .resize(TOP, LEFT)
      .drag(100, -195)
      .release()
      .expectXml("<CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_alignTop=\"@+id/button\" />");
  }

  public void testResizeTopLeftWithModifierSnapToBottomLeftOfButton() {
    screen(createModel())
      .get("@id/checkbox")
      .resize(TOP, LEFT)
      .modifiers(SHIFT_MASK)
      .drag(-195, -100)
      .release()
      .expectXml("<CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_below=\"@+id/button\"\n" +
                 "        android:layout_alignLeft=\"@+id/button\" />");
  }

  public void testResizeTopLeftWithModifierCloseToBottomLeftOfButton() {
    screen(createModel())
      .get("@id/checkbox")
      .resize(TOP, LEFT)
      .modifiers(SHIFT_MASK)
      .drag(-175, -78)
      .release()
      .expectXml("<CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_marginLeft=\"13dp\"\n" +
                 "        android:layout_marginTop=\"11dp\"\n" +
                 "        android:layout_below=\"@+id/button\"\n" +
                 "        android:layout_alignLeft=\"@+id/button\" />");
  }

  public void testResizeBottomRightWithModifier() {
    screen(createModel())
      .get("@id/checkbox")
      .resize(BOTTOM, RIGHT)
      .modifiers(SHIFT_MASK)
      .drag(10, 10)
      .release()
      .expectXml("<CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_marginLeft=\"100dp\"\n" +
                 "        android:layout_marginTop=\"100dp\"\n" +
                 "        android:layout_marginRight=\"335dp\"\n" +
                 "        android:layout_marginBottom=\"335dp\"\n" +
                 "        android:layout_toRightOf=\"@id/button\"\n" +
                 "        android:layout_below=\"@id/button\"\n" +
                 "        android:layout_alignParentRight=\"true\"\n" +
                 "        android:layout_alignParentBottom=\"true\" />");
  }

  public void testResizeBottomRightWithModifierSnapToBottomOfLayout() {
    screen(createModel())
      .get("@id/checkbox")
      .resize(BOTTOM, RIGHT)
      .modifiers(SHIFT_MASK)
      .drag(670, 670)
      .release()
      .expectXml("<CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_marginLeft=\"100dp\"\n" +
                 "        android:layout_marginTop=\"100dp\"\n" +
                 "        android:layout_toRightOf=\"@id/button\"\n" +
                 "        android:layout_below=\"@id/button\"\n" +
                 "        android:layout_alignParentRight=\"true\"\n" +
                 "        android:layout_alignParentBottom=\"true\" />");
  }

  public void testResizeBottomRightWithModifierToBottomOfLayout() {
    screen(createModel())
      .get("@id/checkbox")
      .resize(BOTTOM, RIGHT)
      .modifiers(SHIFT_MASK)
      .drag(580, 580)
      .release()
      .expectXml("<CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_marginLeft=\"100dp\"\n" +
                 "        android:layout_marginTop=\"100dp\"\n" +
                 "        android:layout_marginRight=\"50dp\"\n" +
                 "        android:layout_marginBottom=\"50dp\"\n" +
                 "        android:layout_toRightOf=\"@id/button\"\n" +
                 "        android:layout_below=\"@id/button\"\n" +
                 "        android:layout_alignParentRight=\"true\"\n" +
                 "        android:layout_alignParentBottom=\"true\" />");
  }

  public void testMoveToNowhere() {
    screen(createModel())
      .get("@id/checkbox")
      .drag()
      .drag(0, 0)
      .release()
      .primary()
      .expectXml("<CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_marginLeft=\"50dp\"\n" +
                 "        android:layout_marginTop=\"50dp\"\n" +
                 "        android:layout_toRightOf=\"@+id/button\"\n" +
                 "        android:layout_below=\"@+id/button\" />");
  }

  public void testMoveSnapToTopOfButton() {
    screen(createModel())
      .get("@id/checkbox")
      .drag()
      .drag(30, -200)
      .release()
      .primary()
      .expectXml("<CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_marginLeft=\"65dp\"\n" +
                 "        android:layout_toRightOf=\"@+id/button\"\n" +
                 "        android:layout_alignTop=\"@+id/button\" />");
  }

  public void testMoveCloseToTopOfButton() {
    screen(createModel())
      .get("@id/checkbox")
      .drag()
      .drag(30, -179)
      .release()
      .primary()
      .expectXml("<CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_marginLeft=\"65dp\"\n" +
                 "        android:layout_marginTop=\"11dp\"\n" +
                 "        android:layout_toRightOf=\"@+id/button\"\n" +
                 "        android:layout_alignTop=\"@+id/button\" />");
  }

  public void testMoveSnapToBottomOfButton() {
    screen(createModel())
      .get("@id/checkbox")
      .drag()
      .drag(150, -120)
      .release()
      .primary()
      .expectXml("<CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_marginLeft=\"125dp\"\n" +
                 "        android:layout_toRightOf=\"@+id/button\"\n" +
                 "        android:layout_alignBottom=\"@+id/button\" />");
  }

  public void testMoveCloseToBottomOfButton() {
    screen(createModel())
      .get("@id/checkbox")
      .drag()
      .drag(150, -150)
      .release()
      .primary()
      .expectXml("<CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_marginLeft=\"125dp\"\n" +
                 "        android:layout_marginBottom=\"15dp\"\n" +
                 "        android:layout_toRightOf=\"@+id/button\"\n" +
                 "        android:layout_alignBottom=\"@+id/button\" />");
  }

  public void testMoveSnapToMiddleOfLayout() {
    screen(createModel())
      .get("@id/checkbox")
      .drag()
      .drag(200, 200)
      .release()
      .primary()
      .expectXml("<CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_centerHorizontal=\"true\"\n" +
                 "        android:layout_centerVertical=\"true\" />");
  }

  public void testMoveLeft() {
    // This should have both start and left attributes
    setAndroidModel(RtlSupportProcessor.RTL_TARGET_SDK_START - 1, RtlSupportProcessor.RTL_TARGET_SDK_START);
    screen(createRtlLeftModel())
      .get("@id/textView")
      .drag()
      .drag(-70, -100)
      .release()
      .primary()
      .expectXml("<TextView\n" +
                 "        android:id=\"@id/textView\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"100dp\"\n" +
                 "        android:layout_marginStart=\"15dp\"\n" +
                 "        android:layout_marginLeft=\"15dp\"\n" +
                 "        android:layout_alignParentStart=\"true\"\n" +
                 "        android:layout_alignParentLeft=\"true\"\n" +
                 "        android:layout_alignParentTop=\"true\" />");

    // This should have start attributes and no left attributes
    setAndroidModel(RtlSupportProcessor.RTL_TARGET_SDK_START, RtlSupportProcessor.RTL_TARGET_SDK_START);
    screen(createRtlLeftModel())
      .get("@id/textView")
      .drag()
      .drag(-70, -100)
      .release()
      .primary()
      .expectXml("<TextView\n" +
                 "        android:id=\"@id/textView\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"100dp\"\n" +
                 "        android:layout_marginStart=\"15dp\"\n" +
                 "        android:layout_alignParentStart=\"true\"\n" +
                 "        android:layout_alignParentTop=\"true\" />");
  }

  public void testMoveRight() {
    // This should have both start and left attributes
    setAndroidModel(RtlSupportProcessor.RTL_TARGET_SDK_START - 1, RtlSupportProcessor.RTL_TARGET_SDK_START);
    screen(createRtlRightModel())
      .get("@id/textView")
      .drag()
      .drag(770, -100)
      .release()
      .primary()
      .expectXml("<TextView\n" +
                 "        android:id=\"@id/textView\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"100dp\"\n" +
                 "        android:layout_marginEnd=\"15dp\"\n" +
                 "        android:layout_marginRight=\"15dp\"\n" +
                 "        android:layout_alignParentTop=\"true\"\n" +
                 "        android:layout_alignParentEnd=\"true\"\n" +
                 "        android:layout_alignParentRight=\"true\" />");

    // This should have start attributes and no left attributes
    setAndroidModel(RtlSupportProcessor.RTL_TARGET_SDK_START, RtlSupportProcessor.RTL_TARGET_SDK_START);
    screen(createRtlRightModel())
      .get("@id/textView")
      .drag()
      .drag(770, -100)
      .release()
      .primary()
      .expectXml("<TextView\n" +
                 "        android:id=\"@id/textView\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"100dp\"\n" +
                 "        android:layout_marginEnd=\"15dp\"\n" +
                 "        android:layout_alignParentTop=\"true\"\n" +
                 "        android:layout_alignParentEnd=\"true\" />");
  }

  public void testSnapCheckBoxToTopLeftOfLayoutThenMoveButtonToSnapWithCheckBox() {
    ScreenFixture screen = screen(createModel());
    screen
      .get("@id/checkbox")
      .drag()
      .dragTo(0, 0)
      .release()
      .primary()
      .expectXml("<CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_alignParentLeft=\"true\"\n" +
                 "        android:layout_alignParentTop=\"true\" />");
    screen
      .get("@id/button")
      .drag()
      .drag(-150, -150)
      .release()
      .primary()
      .expectXml("<Button\n" +
                 "        android:id=\"@id/button\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"100dp\"\n" +
                 "        android:layout_marginStart=\"100dp\"\n" +
                 "        android:layout_toRightOf=\"@+id/checkbox\"\n" +
                 "        android:layout_below=\"@+id/checkbox\" />");
  }

  public void testMoveWithModifier() {
    screen(createModel())
      .get("@id/checkbox")
      .drag()
      .modifiers(SHIFT_MASK)
      .drag(30, 30)
      .release()
      .primary()
      .expectXml("<CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_toRightOf=\"@+id/button\"\n" +
                 "        android:layout_below=\"@+id/button\" />");
  }

  public void testMoveSnapToBaseline() {
    screen(createModel())
      .get("@id/textView")
      .drag()
      .drag(0, -153)
      .release()
      .primary()
      .expectXml("<TextView\n" +
                 "        android:id=\"@id/textView\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"100dp\"\n" +
                 "        android:layout_marginLeft=\"40dp\"\n" +
                 "        android:layout_toRightOf=\"@+id/checkbox\"\n" +
                 "        android:layout_alignBaseline=\"@+id/checkbox\"\n" +
                 "        android:layout_alignBottom=\"@+id/checkbox\" />");
  }

  public void testMoveDoesNotReorderComponents() throws Exception {
    //noinspection XmlUnusedNamespaceDeclaration
    screen(createModel())
      .get("@id/checkbox")
      .drag()
      .drag(10, 10)
      .release()
      .primary()
      .parent()
      .expectXml("<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "    android:layout_width=\"match_parent\"\n" +
                 "    android:layout_height=\"match_parent\">\n" +
                 "\n" +
                 "    <Button\n" +
                 "        android:id=\"@id/button\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"100dp\"\n" +
                 "        android:layout_alignParentTop=\"true\"\n" +
                 "        android:layout_alignParentLeft=\"true\"\n" +
                 "        android:layout_alignParentStart=\"true\"\n" +
                 "        android:layout_marginTop=\"100dp\"\n" +
                 "        android:layout_marginLeft=\"100dp\"\n" +
                 "        android:layout_marginStart=\"100dp\" />\n" +
                 "\n" +
                 "    <CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_marginLeft=\"55dp\"\n" +
                 "        android:layout_marginTop=\"55dp\"\n" +
                 "        android:layout_toRightOf=\"@+id/button\"\n" +
                 "        android:layout_below=\"@+id/button\" />\n" +
                 "\n" +
                 "    <TextView\n" +
                 "        android:id=\"@id/textView\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"100dp\"\n" +
                 "        android:layout_below=\"@id/checkbox\"\n" +
                 "        android:layout_toRightOf=\"@id/checkbox\"\n" +
                 "        android:layout_marginLeft=\"80dp\"\n" +
                 "        android:layout_marginTop=\"80dp\" />\n" +
                 "\n" +
                 "</RelativeLayout>");
  }

  private void setAndroidModel(int minSdk, int targetSdk) {
    myFacet.getConfiguration().setModel(new TestAndroidModel("com.example.test",
                                                             new AndroidVersion(minSdk),
                                                             new AndroidVersion(targetSdk)));
  }

  @NotNull
  private SyncNlModel createModel() {
    ModelBuilder builder = model("relative.xml",
                                 component(RELATIVE_LAYOUT)
                                   .withBounds(0, 0, 1000, 1000)
                                   .matchParentWidth()
                                   .matchParentHeight()
                                   .children(
                                     component(BUTTON)
                                       .withBounds(100, 100, 100, 100)
                                       .id("@id/button")
                                       .width("100dp")
                                       .height("100dp")
                                       .withAttribute("android:layout_alignParentTop", "true")
                                       .withAttribute("android:layout_alignParentLeft", "true")
                                       .withAttribute("android:layout_alignParentStart", "true")
                                       .withAttribute("android:layout_marginTop", "100dp")
                                       .withAttribute("android:layout_marginLeft", "100dp")
                                       .withAttribute("android:layout_marginStart", "100dp"),

                                     component(CHECK_BOX)
                                       .withBounds(300, 300, 20, 20)
                                       .viewObject(mockViewWithBaseline(17))
                                       .id("@id/checkbox")
                                       .width("20dp")
                                       .height("20dp")
                                       .withAttribute("android:layout_below", "@id/button")
                                       .withAttribute("android:layout_toRightOf", "@id/button")
                                       .withAttribute("android:layout_marginLeft", "100dp")
                                       .withAttribute("android:layout_marginTop", "100dp"),

                                     component(TEXT_VIEW)
                                       .withBounds(400, 400, 100, 100)
                                       .viewObject(mockViewWithBaseline(70))
                                       .id("@id/textView")
                                       .width("100dp")
                                       .height("100dp")
                                       .withAttribute("android:layout_below", "@id/checkbox")
                                       .withAttribute("android:layout_toRightOf", "@id/checkbox")
                                       .withAttribute("android:layout_marginLeft", "80dp")
                                       .withAttribute("android:layout_marginTop", "80dp")
                                   ));
    SyncNlModel model = builder.build();
    assertEquals(1, model.getComponents().size());
    assertEquals("NlComponent{tag=<RelativeLayout>, bounds=[0,0:1000x1000}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,100:100x100}\n" +
                 "    NlComponent{tag=<CheckBox>, bounds=[300,300:20x20}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[400,400:100x100}",
                 NlTreeDumper.dumpTree(model.getComponents()));

    format(model.getFile());
    assertEquals("<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "    android:layout_width=\"match_parent\"\n" +
                 "    android:layout_height=\"match_parent\">\n" +
                 "\n" +
                 "    <Button\n" +
                 "        android:id=\"@id/button\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"100dp\"\n" +
                 "        android:layout_alignParentTop=\"true\"\n" +
                 "        android:layout_alignParentLeft=\"true\"\n" +
                 "        android:layout_alignParentStart=\"true\"\n" +
                 "        android:layout_marginTop=\"100dp\"\n" +
                 "        android:layout_marginLeft=\"100dp\"\n" +
                 "        android:layout_marginStart=\"100dp\" />\n" +
                 "\n" +
                 "    <CheckBox\n" +
                 "        android:id=\"@id/checkbox\"\n" +
                 "        android:layout_width=\"20dp\"\n" +
                 "        android:layout_height=\"20dp\"\n" +
                 "        android:layout_below=\"@id/button\"\n" +
                 "        android:layout_toRightOf=\"@id/button\"\n" +
                 "        android:layout_marginLeft=\"100dp\"\n" +
                 "        android:layout_marginTop=\"100dp\" />\n" +
                 "\n" +
                 "    <TextView\n" +
                 "        android:id=\"@id/textView\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"100dp\"\n" +
                 "        android:layout_below=\"@id/checkbox\"\n" +
                 "        android:layout_toRightOf=\"@id/checkbox\"\n" +
                 "        android:layout_marginLeft=\"80dp\"\n" +
                 "        android:layout_marginTop=\"80dp\" />\n" +
                 "\n" +
                 "</RelativeLayout>\n", model.getFile().getText());
    return model;
  }

  @NotNull
  private SyncNlModel createRtlLeftModel() {
    ModelBuilder builder = model("relative.xml",
                                 component(RELATIVE_LAYOUT)
                                   .withBounds(0, 0, 1000, 1000)
                                   .matchParentWidth()
                                   .matchParentHeight()
                                   .children(
                                     component(TEXT_VIEW)
                                       .withBounds(100, 100, 100, 100)
                                       .id("@id/textView")
                                       .width("100dp")
                                       .height("100dp")
                                       .withAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_LEFT, "30dp")
                                       .withAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_START, "30dp")
                                   ));
    SyncNlModel model = builder.build();
    assertEquals(1, model.getComponents().size());
    assertEquals("NlComponent{tag=<RelativeLayout>, bounds=[0,0:1000x1000}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100}",
                 NlTreeDumper.dumpTree(model.getComponents()));

    format(model.getFile());
    assertEquals("<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "    android:layout_width=\"match_parent\"\n" +
                 "    android:layout_height=\"match_parent\">\n" +
                 "\n" +
                 "    <TextView\n" +
                 "        android:id=\"@id/textView\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"100dp\"\n" +
                 "        android:layout_marginLeft=\"30dp\"\n" +
                 "        android:layout_marginStart=\"30dp\" />\n" +
                 "\n" +
                 "</RelativeLayout>\n", model.getFile().getText());
    return model;
  }

  @NotNull
  private SyncNlModel createRtlRightModel() {
    ModelBuilder builder = model("relative.xml",
                                 component(RELATIVE_LAYOUT)
                                   .withBounds(0, 0, 1000, 1000)
                                   .matchParentWidth()
                                   .matchParentHeight()
                                   .children(
                                     component(TEXT_VIEW)
                                       .withBounds(100, 100, 100, 100)
                                       .id("@id/textView")
                                       .width("100dp")
                                       .height("100dp")
                                       .withAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_RIGHT, "30dp")
                                       .withAttribute(ANDROID_URI, ATTR_LAYOUT_MARGIN_END, "30dp")
                                   ));
    SyncNlModel model = builder.build();
    assertEquals(1, model.getComponents().size());
    assertEquals("NlComponent{tag=<RelativeLayout>, bounds=[0,0:1000x1000}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100}",
                 NlTreeDumper.dumpTree(model.getComponents()));

    format(model.getFile());
    assertEquals("<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "    android:layout_width=\"match_parent\"\n" +
                 "    android:layout_height=\"match_parent\">\n" +
                 "\n" +
                 "    <TextView\n" +
                 "        android:id=\"@id/textView\"\n" +
                 "        android:layout_width=\"100dp\"\n" +
                 "        android:layout_height=\"100dp\"\n" +
                 "        android:layout_marginRight=\"30dp\"\n" +
                 "        android:layout_marginEnd=\"30dp\" />\n" +
                 "\n" +
                 "</RelativeLayout>\n", model.getFile().getText());
    return model;
  }
}
