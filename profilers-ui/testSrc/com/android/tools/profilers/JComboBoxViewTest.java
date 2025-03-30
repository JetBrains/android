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
package com.android.tools.profilers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.android.tools.adtui.model.AspectModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.ComboBoxModel;
import javax.swing.JComboBox;
import org.junit.Before;
import org.junit.Test;

public class JComboBoxViewTest {
  private List<String> myList;
  private String mySelected;

  private JComboBox<String> myComboBox = new JComboBox<>();
  private JComboBoxView myComboBoxView;
  private AspectModel<TestAspect> myAspectModel = new AspectModel<>();

  @Before
  public void setUp() {
    myList = new ArrayList<>(Arrays.asList("A", "B", "C"));
    mySelected = "A";
    myComboBoxView = new JComboBoxView<>(myComboBox, myAspectModel, TestAspect.CHANGED,
                                         () -> myList,
                                         () -> mySelected,
                                         s -> mySelected = s);
    myComboBoxView.bind();
  }

  @Test
  public void bind() {
    check();
  }

  @Test
  public void changedList() {
    myList.add("D");
    myAspectModel.changed(TestAspect.CHANGED);
    check();
  }

  @Test
  public void changedSelected() {
    mySelected = "B";
    myAspectModel.changed(TestAspect.CHANGED);
    check();
  }

  @Test
  public void changedListAndSelected() {
    myList.remove(0);
    mySelected = "C";
    myAspectModel.changed(TestAspect.CHANGED);
    check();
  }

  @Test
  public void listEmpty() {
    myList.clear();
    myAspectModel.changed(TestAspect.CHANGED);
    ComboBoxModel model = myComboBox.getModel();
    assertEquals(1, model.getSize());
    assertNull(model.getElementAt(0));
  }

  @Test
  public void selectedFromComboBox() {
    myComboBox.setSelectedItem("C");
    assertEquals("C", mySelected);
  }

  @Test
  public void testResetting() {
    myComboBoxView = new JComboBoxView<>(myComboBox, myAspectModel, TestAspect.CHANGED,
                                         () -> myList,
                                         () -> mySelected,
                                         s -> mySelected = s,
                                         () -> reset(mySelected));
    myComboBoxView.bind();
    mySelected = "A1";
    myAspectModel.changed(TestAspect.CHANGED);
    assertEquals(reset(mySelected), myComboBox.getModel().getSelectedItem());
  }

  private static String reset(String item) {
    return item.substring(0, 1);
  }

  /**
   * Check that list of items and selected item in myComboBox is corresponds to myList
   * and mySelected.
   */
  private void check() {
    checkWithSelected(mySelected);
  }

  private void checkWithSelected(String selected) {
    ComboBoxModel model = myComboBox.getModel();
    assertEquals(selected, model.getSelectedItem());

    assertEquals(myList.size(), model.getSize());
    for (int i = 0; i < myList.size(); ++i) {
      assertEquals(myList.get(i), model.getElementAt(i));
    }
  }

  private enum TestAspect {
    CHANGED
  }
}