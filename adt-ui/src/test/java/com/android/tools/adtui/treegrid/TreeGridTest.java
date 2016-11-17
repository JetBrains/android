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
package com.android.tools.adtui.treegrid;

import com.intellij.ide.DataManager;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static java.awt.event.KeyEvent.*;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

@RunWith(JUnit4.class)
public class TreeGridTest {
  private Disposable myDisposable;
  private TreeGrid<String> myGrid;
  private JList<String> myGroup1;
  private JList<String> myGroup2;
  private JList<String> myGroup3;

  @Before
  public void setUp() {
    myDisposable = Disposer.newDisposable();
    Application application = mock(Application.class);
    DataManager dataManager = mock(DataManager.class);
    DataContext dataContext = mock(DataContext.class);
    ApplicationManager.setApplication(application, myDisposable);
    when(application.getAnyModalityState()).thenReturn(ModalityState.NON_MODAL);
    when(application.getComponent(eq(DataManager.class))).thenReturn(dataManager);
    when(dataManager.getDataContext(any(Component.class))).thenReturn(dataContext);
    myGrid = new TreeGrid<>();
    myGrid.setSize(140, 800);
    myGrid.setModel(createTree());
    myGrid.setLayoutOrientation(JList.HORIZONTAL_WRAP);
    myGrid.setFixedCellWidth(40);
    myGrid.setFixedCellHeight(40);
    myGrid.doLayout();
    BufferedImage image = UIUtil.createImage(1000, 1000, TYPE_INT_ARGB);
    myGrid.paint(image.getGraphics());

    List<JList<String>> lists = myGrid.getLists();
    myGroup1 = lists.get(0);
    myGroup2 = lists.get(1);
    myGroup3 = lists.get(2);
  }

  @After
  public void tearDown() {
    Disposer.dispose(myDisposable);
  }

  @Test
  public void testIsFiltered() {
    assertThat(myGrid.isFiltered()).isFalse();
    myGrid.setFilter(s -> s.equals("b2"));
    assertThat(myGrid.isFiltered()).isTrue();
  }

  @Test
  public void testSelectIfUnique() {
    myGrid.selectIfUnique();
    assertThat(myGrid.getSelectedElement()).isNull();
  }

  @Test
  public void testSelectIfUniqueWithUniqueFilter() {
    myGrid.setFilter(s -> s.equals("b2"));
    myGrid.selectIfUnique();
    assertThat(myGrid.getSelectedElement()).isEqualTo("b2");
  }

  @Test
  public void testSelectIfUniqueWithNonUniqueFilter() {
    myGrid.setFilter(s -> s.startsWith("b"));
    myGrid.selectIfUnique();
    assertThat(myGrid.getSelectedElement()).isNull();
  }

  @Test
  public void testSetVisibleSection() {
    myGrid.setVisibleSection("g2");
    assertThat(myGroup1.isVisible()).isFalse();
    assertThat(myGroup2.isVisible()).isTrue();
    assertThat(myGroup3.isVisible()).isFalse();

    myGrid.setVisibleSection(null);
    assertThat(myGroup1.isVisible()).isTrue();
    assertThat(myGroup2.isVisible()).isTrue();
    assertThat(myGroup3.isVisible()).isTrue();
  }

  @Test
  public void testSelectFirst() {
    myGrid.selectFirst();
    assertThat(myGrid.getSelectedElement()).isEqualTo("a1");
  }

  @Test
  public void testSelectNext() {
    myGrid.selectFirst();
    key(VK_RIGHT);
    assertThat(myGrid.getSelectedElement()).isEqualTo("b1");
  }

  @Test
  public void testSelectNextGoesToNextGroup() {
    myGrid.setSelectedElement("d1");
    key(VK_RIGHT);
    assertThat(myGrid.getSelectedElement()).isEqualTo("a2");
  }

  @Test
  public void testSelectNextStopsWithLastElement() {
    myGrid.setSelectedElement("d3");
    key(VK_RIGHT);
    assertThat(myGrid.getSelectedElement()).isEqualTo("d3");
  }

  @Test
  public void testSelectPrev() {
    myGrid.setSelectedElement("c1");
    key(VK_LEFT);
    assertThat(myGrid.getSelectedElement()).isEqualTo("b1");
  }

  @Test
  public void testSelectPrevGoesToPrevGroup() {
    myGrid.setSelectedElement("a3");
    key(VK_LEFT);
    assertThat(myGrid.getSelectedElement()).isEqualTo("b2");
  }

  @Test
  public void testSelectPrevStopsWithFirstElement() {
    myGrid.setSelectedElement("a1");
    key(VK_LEFT);
    assertThat(myGrid.getSelectedElement()).isEqualTo("a1");
  }

  @Test
  public void testSelectDown() {
    myGrid.setSelectedElement("a1");
    key(VK_DOWN);
    assertThat(myGrid.getSelectedElement()).isEqualTo("d1");
  }

  @Test
  public void testSelectDownRemembersColumn() {
    myGrid.setSelectedElement("b1");
    key(VK_DOWN);
    assertThat(myGrid.getSelectedElement()).isEqualTo("d1");
    key(VK_DOWN);
    assertThat(myGrid.getSelectedElement()).isEqualTo("b2");
  }

  @Test
  public void testSelectDownStopsAtLastRow() {
    myGrid.setSelectedElement("a3");
    key(VK_DOWN);
    assertThat(myGrid.getSelectedElement()).isEqualTo("d3");
    key(VK_DOWN);
    assertThat(myGrid.getSelectedElement()).isEqualTo("d3");
  }

  @Test
  public void testSelectUp() {
    myGrid.setSelectedElement("a2");
    key(VK_UP);
    assertThat(myGrid.getSelectedElement()).isEqualTo("d1");
  }

  @Test
  public void testSelectUpRemembersColumn() {
    myGrid.setSelectedElement("b2");
    key(VK_UP);
    assertThat(myGrid.getSelectedElement()).isEqualTo("d1");
    key(VK_UP);
    assertThat(myGrid.getSelectedElement()).isEqualTo("b1");
    key(VK_UP);
    assertThat(myGrid.getSelectedElement()).isEqualTo("b1");
  }

  @Test
  public void testNoNavigationWithoutSelection() {
    myGrid.selectFirst();
    JComponent component = myGrid.getSelectedComponent();
    myGrid.setSelectedElement(null);

    key(VK_UP, component, component);
    key(VK_DOWN, component, component);
    key(VK_LEFT, component, component);
    key(VK_RIGHT, component, component);
    assertThat(myGrid.getSelectedElement()).isNull();
  }

  private void key(@MagicConstant(flagsFromClass = KeyEvent.class) int keyCode) {
    JComponent component = myGrid.getSelectedComponent();
    key(keyCode, component, component);
  }

  private static void key(@MagicConstant(flagsFromClass = KeyEvent.class) int keyCode,
                          @NotNull JComponent source,
                          @NotNull JComponent target) {
    KeyEvent event = new KeyEvent(source, KEY_PRESSED, System.currentTimeMillis(), 0, keyCode, '\0');
    for (KeyListener listener : target.getKeyListeners()) {
      listener.keyPressed(event);
    }
  }

  private static AbstractTreeStructure createTree() {
    return new AbstractTreeStructure() {
      @Override
      public Object getRootElement() {
        return "root";
      }

      @Override
      public Object[] getChildElements(Object element) {
        switch ((String)element) {
          case "root":
            return new Object[]{"g1", "g2", "g3"};
          case "g1":
            return new Object[]{"a1", "b1", "c1", "d1"};
          case "g2":
            return new Object[]{"a2", "b2"};
          case "g3":
            return new Object[]{"a3", "b3", "c3", "d3"};
          default:
            return ArrayUtil.EMPTY_OBJECT_ARRAY;
        }
      }

      @Nullable
      @Override
      public Object getParentElement(Object element) {
        switch ((String)element) {
          case "c":
            return "a";
          case "a":
          case "b":
            return "root";
          default:
            return null;
        }
      }

      @NotNull
      @Override
      public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
        return new NodeDescriptor(null, parentDescriptor) {

          @Override
          public boolean update() {
            return false;
          }

          @Override
          public Object getElement() {
            return element;
          }
        };
      }

      @Override
      public void commit() {
      }

      @Override
      public boolean hasSomethingToCommit() {
        return false;
      }
    };
  }
}
