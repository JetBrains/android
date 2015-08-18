/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.editors.navigation.model;

import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;

public class NavigationReaderTest extends TestCase {
  private static State createState(String className) {
    return new ActivityState(className);
  }

  private static Object fromString(String output) {
    return new XMLReader(new ByteArrayInputStream(output.getBytes())).read();
  }

  private static String toString(Object model) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    XMLWriter writer = new XMLWriter(out);
    writer.write(model);
    return out.toString();
  }

  private static Transition transition(String type, State source, State destination) {
    return new Transition(type, Locator.of(source), Locator.of(destination));
  }

  public void test0() throws FileNotFoundException {
    NavigationModel model = new NavigationModel();
    State s0 = createState("com.acme.MasterController");
    State s1 = createState("com.acme.SlaveController");
    Transition t1 = transition("click", s0, s1);
    Transition t2 = transition("swipe", s1, s0);
    model.add(t1);
    model.add(t2);

    String output = toString(model);

    Object model2 = fromString(output);
    String output2 = toString(model2);

    assertEquals(output, output2);
  }
}
