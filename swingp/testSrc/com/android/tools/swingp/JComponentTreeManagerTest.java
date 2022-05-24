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
package com.android.tools.swingp;

import static com.android.tools.swingp.JComponentTreeManager.clear;
import static com.android.tools.swingp.JComponentTreeManager.popJComponent;
import static com.android.tools.swingp.JComponentTreeManager.pushJComponent;
import static com.android.tools.swingp.JComponentTreeManager.setEnabled;

import javax.swing.JComponent;
import javax.swing.JPanel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class JComponentTreeManagerTest {
  @Before
  public void setup() {
    setEnabled(true);
  }

  @After
  public void teardown() {
    clear();
    setEnabled(false);
  }

  @Test(expected = RuntimeException.class)
  public void testPopOnEmpty() {
    JComponent component = new JPanel();
    popJComponent(component);
  }

  @Test(expected = RuntimeException.class)
  public void testDoublePopToEmpty() {
    JComponent component = new JPanel();
    pushJComponent(component);
    popJComponent(component);
    popJComponent(component);
  }

  @Test(expected = RuntimeException.class)
  public void testDoublePop() {
    JComponent component = new JPanel();
    JComponent childComponent = new JPanel();
    component.add(childComponent);

    pushJComponent(component);
    pushJComponent(childComponent);
    popJComponent(childComponent);
    popJComponent(childComponent);
  }

  @Test(expected = RuntimeException.class)
  public void testMissingPop() {
    JComponent component = new JPanel();
    JComponent childComponent = new JPanel();
    component.add(childComponent);

    pushJComponent(component);
    pushJComponent(childComponent);
    popJComponent(component);
  }

  @Test
  public void testCorrectBehavior() {
    JComponent component = new JPanel();
    JComponent childComponent = new JPanel();
    component.add(childComponent);

    pushJComponent(component);
    popJComponent(component);

    pushJComponent(component);
    pushJComponent(childComponent);
    popJComponent(childComponent);
    popJComponent(component);
  }
}
