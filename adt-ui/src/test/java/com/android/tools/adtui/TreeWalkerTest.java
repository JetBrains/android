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
package com.android.tools.adtui;

import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertThat;

@SuppressWarnings("OptionalGetWithoutIsPresent") // Don't care in tests
public class TreeWalkerTest {
  @Test
  public void canStreamDescendants() throws Exception {
    JPanel panel1 = new JPanel();
    JPanel panel11 = new JPanel();
    JPanel panel12 = new JPanel();
    JPanel panel123 = new JPanel();
    JPanel panel13 = new JPanel();

    panel1.add(panel11);
    panel1.add(panel12);
    panel12.add(panel123);
    panel1.add(panel13);

    JLabel label = new JLabel();
    JButton button = new JButton();
    JTextField textField = new JTextField();
    panel11.add(label);
    panel123.add(button);
    panel13.add(textField);

    TreeWalker treeWalker = new TreeWalker(panel1);
    assertThat(treeWalker.descendantStream().filter(c -> c instanceof JLabel).findFirst().get()).isEqualTo(label);
    assertThat(treeWalker.descendantStream().filter(c -> c instanceof JButton).findFirst().get()).isEqualTo(button);
    assertThat(treeWalker.descendantStream().filter(c -> c instanceof JTextField).findFirst().get()).isEqualTo(textField);
    assertThat(treeWalker.descendantStream().anyMatch(c -> c instanceof JTable)).isFalse();
  }

  @Test
  public void canIterateDescendants() throws Exception {
    JPanel panel1 = new JPanel();
    JPanel panel11 = new JPanel();
    JPanel panel12 = new JPanel();
    JPanel panel123 = new JPanel();
    JPanel panel13 = new JPanel();

    panel1.add(panel11);
    panel1.add(panel12);
    panel12.add(panel123);
    panel1.add(panel13);

    int count = 0;
    TreeWalker treeWalker = new TreeWalker(panel1);
    for (Component c : treeWalker.descendants()) {
      count++;
    }
    assertThat(count).isEqualTo(5);
  }

  @Test
  public void descendantsIncludesSelf() throws Exception {
    JPanel panel1 = new JPanel();
    JPanel panel11 = new JPanel();
    JPanel panel12 = new JPanel();

    panel1.add(panel11);
    panel1.add(panel12);

    TreeWalker treeWalker = new TreeWalker(panel1);
    assertThat(treeWalker.descendantStream().anyMatch(c -> c == panel1)).isTrue();
  }

  @Test
  public void descendantsAreReturnedInBreadthFirstSearchOrder() throws Exception {
    JPanel panelRoot = new JPanel();
    JPanel panel1 = new JPanel();
    JPanel panel11 = new JPanel();
    JPanel panel111 = new JPanel();
    JPanel panel112 = new JPanel();
    JPanel panel2 = new JPanel();
    JPanel panel3 = new JPanel();
    JPanel panel31 = new JPanel();
    JPanel panel32 = new JPanel();

    panelRoot.add(panel1);
    panel1.add(panel11);
    panel11.add(panel111);
    panel11.add(panel112);
    panelRoot.add(panel2);
    panelRoot.add(panel3);
    panel3.add(panel31);
    panel3.add(panel32);

    TreeWalker treeWalker = new TreeWalker(panelRoot);
    assertThat(treeWalker.descendantStream().collect(Collectors.toList())).
      containsExactly(panelRoot, panel1, panel2, panel3, panel11, panel31, panel32, panel111, panel112).inOrder();
  }

  @Test
  public void descendantsCanBeReturnedInDepthFirstSearchOrder() throws Exception {
    JPanel panelRoot = new JPanel();
    JPanel panel1 = new JPanel();
    JPanel panel11 = new JPanel();
    JPanel panel111 = new JPanel();
    JPanel panel112 = new JPanel();
    JPanel panel2 = new JPanel();
    JPanel panel3 = new JPanel();
    JPanel panel31 = new JPanel();
    JPanel panel32 = new JPanel();

    panelRoot.add(panel1);
    panel1.add(panel11);
    panel11.add(panel111);
    panel11.add(panel112);
    panelRoot.add(panel2);
    panelRoot.add(panel3);
    panel3.add(panel31);
    panel3.add(panel32);

    TreeWalker treeWalker = new TreeWalker(panelRoot);
    assertThat(treeWalker.descendantStream(TreeWalker.DescendantOrder.DEPTH_FIRST).collect(Collectors.toList())).
      containsExactly(panelRoot, panel1, panel11, panel111, panel112, panel2, panel3, panel31, panel32).inOrder();
  }

  @Test
  public void canStreamAncestors() throws Exception {
    JPanel panel1 = new JPanel();
    JPanel panel11 = new JPanel();
    JPanel panel12 = new JPanel();
    JPanel panel123 = new JPanel();
    JPanel panel13 = new JPanel();

    panel1.add(panel11);
    panel1.add(panel12);
    panel12.add(panel123);
    panel1.add(panel13);

    TreeWalker treeWalker = new TreeWalker(panel123);
    assertThat(treeWalker.ancestorStream().anyMatch(c -> c == panel12)).isTrue();
    assertThat(treeWalker.ancestorStream().anyMatch(c -> c == panel1)).isTrue();
    assertThat(treeWalker.ancestorStream().anyMatch(c -> c == panel11)).isFalse();
    assertThat(treeWalker.ancestorStream().anyMatch(c -> c == panel13)).isFalse();
  }

  @Test
  public void canIterateAncestors() throws Exception {
    JPanel panel1 = new JPanel();
    JPanel panel11 = new JPanel();
    JPanel panel12 = new JPanel();
    JPanel panel123 = new JPanel();
    JPanel panel13 = new JPanel();

    panel1.add(panel11);
    panel1.add(panel12);
    panel12.add(panel123);
    panel1.add(panel13);

    int count = 0;
    TreeWalker treeWalker = new TreeWalker(panel123);
    for (Component c : treeWalker.ancestors()) {
      count++;
    }

    assertThat(count).isEqualTo(3); // panel123, panel12, panel1
  }

  @Test
  public void findAncestorsIncludesSelf() throws Exception {
    JPanel panel1 = new JPanel();
    JPanel panel11 = new JPanel();
    JPanel panel12 = new JPanel();

    panel1.add(panel11);
    panel1.add(panel12);

    TreeWalker treeWalker = new TreeWalker(panel12);
    assertThat(treeWalker.ancestorStream().anyMatch(c -> c == panel12)).isTrue();
  }

  @Test
  public void ancestorsAreReturnedInOrder() throws Exception {
    JPanel panel1 = new JPanel();
    JPanel panel2 = new JPanel();
    JPanel panel3 = new JPanel();

    panel1.add(panel2);
    panel2.add(panel3);

    TreeWalker treeWalker = new TreeWalker(panel3);
    assertThat(treeWalker.ancestorStream().collect(Collectors.toList())).containsExactly(panel3, panel2, panel1).inOrder();
  }

  @Test
  public void isAncestorWorks() throws Exception {
    JPanel panel1 = new JPanel();
    JPanel panel11 = new JPanel();
    JPanel panel12 = new JPanel();
    JPanel panel123 = new JPanel();
    JPanel panel13 = new JPanel();

    panel1.add(panel11);
    panel1.add(panel12);
    panel12.add(panel123);
    panel1.add(panel13);

    assertThat(TreeWalker.isAncestor(panel1, panel123)).isTrue();
    assertThat(TreeWalker.isAncestor(panel123, panel1)).isFalse();
    assertThat(TreeWalker.isAncestor(panel1, panel1)).isTrue();
    assertThat(TreeWalker.isAncestor(panel11, panel123)).isFalse();
    assertThat(TreeWalker.isAncestor(panel12, panel123)).isTrue();
  }
}