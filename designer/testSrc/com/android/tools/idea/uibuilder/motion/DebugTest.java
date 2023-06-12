/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.motion;

import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.utils.Debug;
import com.android.tools.idea.uibuilder.motion.adapters.BaseMotionEditorTest;
import com.android.tools.idea.uibuilder.motion.adapters.MTagImp;
import com.android.tools.idea.uibuilder.motion.adapters.samples.layout_16_xml;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;

public class DebugTest extends BaseMotionEditorTest {

  private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
  private final PrintStream originalOut = System.out;

  public void testGetLocation() {
    String location = Debug.getLocation();
    assertEquals( ".(DebugTest.java:33)", location);
  }

  @Override
  public void setUp() {
    System.setOut(new PrintStream(outContent));
  }

  @Override
  public void tearDown() {
    System.setOut(originalOut);
  }

  public void testDebugLog() {
    Debug.log("Hello World");
    assertEquals(".(DebugTest.java:48)Hello World\n", outContent.toString());
  }

  public void testDebugLogStack() {
    Debug.logStack("Hello World", 5);
    assertTrue( outContent.toString().length() > 5);
  }

  public void testDebugSerialization() {
    assertEquals(" null", Debug.toString(null));
    InputStream layout_stream = layout_16_xml.asStream();
    String layoutStr = convert(layout_stream);
    MTag layout = MTagImp.parse(layoutStr);
    assertEquals(" [number,dial_pad,dialtitle,button1,button2,button3,button4,button5,button6," +
                 "button7,button8,button9,button10,button11,button12,people_pad,people_title,people1," +
                 "people2,people3,people4,people5,people6,people7,people8]", Debug.toString(layout.getChildTags()));
    layout.getChildren().clear();
    assertEquals(" []", Debug.toString(layout.getChildTags()));
  }

}
