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
package com.android.tools.idea.uibuilder.palette;

import com.android.SdkConstants;
import com.android.tools.adtui.treegrid.TreeGrid;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.uibuilder.model.NlLayoutType;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.intellij.psi.PsiFile;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.xml.ws.Holder;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.android.tools.idea.uibuilder.palette.PaletteTestCase.findItem;
import static com.google.common.truth.Truth.assertThat;
import static java.awt.event.InputEvent.BUTTON1_DOWN_MASK;
import static java.awt.event.InputEvent.BUTTON1_MASK;
import static java.awt.event.MouseEvent.*;
import static org.mockito.Mockito.*;

public class NlPaletteTreeGridTest extends AndroidTestCase {
  private DesignSurface mySurface;
  private DependencyManager myDependencyManager;
  private NlPaletteTreeGrid myPanel;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myDependencyManager = mock(DependencyManager.class);
    mySurface = mock(DesignSurface.class);
    Runnable closeToolWindowCallback = mock(Runnable.class);
    myPanel = new NlPaletteTreeGrid(getProject(), myDependencyManager, closeToolWindowCallback);
    PsiFile file = myFixture.configureByText("res/layout/mine.xml", "<LinearLayout/>");
    Configuration configuration = myFacet.getConfigurationManager().getConfiguration(file.getVirtualFile());
    when(mySurface.getConfiguration()).thenReturn(configuration);
  }

  public void testLayoutGroups() {
    checkPaletteGroupsAndItems(NlLayoutType.LAYOUT);
  }

  public void testMenuGroups() {
    checkPaletteGroupsAndItems(NlLayoutType.MENU);
  }

  public void testPreferenceGroups() {
    checkPaletteGroupsAndItems(NlLayoutType.PREFERENCE_SCREEN);
  }

  public void testIconAndName() {
    Palette palette = NlPaletteModel.get(getProject()).getPalette(NlLayoutType.LAYOUT);
    myPanel.setMode(PaletteMode.ICON_AND_NAME);
    myPanel.populateUiModel(palette, mySurface);
    TreeGrid<Palette.Item> grid = myPanel.getComponentTree();
    grid.getLists().forEach(list -> {
      assertThat(list.getLayoutOrientation()).isEqualTo(JList.VERTICAL);
      assertThat(list.getFixedCellWidth()).isEqualTo(-1);
      assertThat(list.getFixedCellHeight()).isEqualTo(-1);
    });
    assertThat(myPanel.getMode()).isEqualTo(PaletteMode.ICON_AND_NAME);
  }

  public void testLargeIcons() {
    Palette palette = NlPaletteModel.get(getProject()).getPalette(NlLayoutType.LAYOUT);
    myPanel.setMode(PaletteMode.LARGE_ICONS);
    myPanel.populateUiModel(palette, mySurface);
    TreeGrid<Palette.Item> grid = myPanel.getComponentTree();
    grid.getLists().forEach(list -> {
      assertThat(list.getLayoutOrientation()).isEqualTo(JList.HORIZONTAL_WRAP);
      assertThat(list.getFixedCellWidth()).isEqualTo(36);
      assertThat(list.getFixedCellHeight()).isEqualTo(36);
    });
    assertThat(myPanel.getMode()).isEqualTo(PaletteMode.LARGE_ICONS);
  }

  public void testSmallIcons() {
    Palette palette = NlPaletteModel.get(getProject()).getPalette(NlLayoutType.LAYOUT);
    myPanel.setMode(PaletteMode.SMALL_ICONS);
    myPanel.populateUiModel(palette, mySurface);
    TreeGrid<Palette.Item> grid = myPanel.getComponentTree();
    grid.getLists().forEach(list -> {
      assertThat(list.getLayoutOrientation()).isEqualTo(JList.HORIZONTAL_WRAP);
      assertThat(list.getFixedCellWidth()).isEqualTo(24);
      assertThat(list.getFixedCellHeight()).isEqualTo(24);
    });
    assertThat(myPanel.getMode()).isEqualTo(PaletteMode.SMALL_ICONS);
  }

  public void testSelectionChangeNotifications() {
    Palette palette = NlPaletteModel.get(getProject()).getPalette(NlLayoutType.LAYOUT);
    myPanel.populateUiModel(palette, mySurface);
    Holder<Palette.Item> lastSelectedItem = new Holder<>();
    myPanel.setSelectionListener(item -> lastSelectedItem.value = item);

    clickOnItem(2, 3);
    assertThat(lastSelectedItem.value.toString()).isEqualTo("LinearLayout");
  }

  public void testClickOnItemMissingFromProject() {
    Palette palette = NlPaletteModel.get(getProject()).getPalette(NlLayoutType.LAYOUT);
    Palette.Item coordinatorLayout = findItem(palette, SdkConstants.COORDINATOR_LAYOUT);

    myPanel.populateUiModel(palette, mySurface);
    when(myDependencyManager.needsLibraryLoad(eq(coordinatorLayout))).thenReturn(true);
    when(myDependencyManager.ensureLibraryIsIncluded(coordinatorLayout)).thenReturn(true);

    Holder<Palette.Item> lastSelectedItem = new Holder<>();
    myPanel.setSelectionListener(item -> lastSelectedItem.value = item);

    clickOnItem(9, 0);

    assertThat(lastSelectedItem.value.getTagName()).isEqualTo(SdkConstants.COORDINATOR_LAYOUT);
    verify(myDependencyManager).ensureLibraryIsIncluded(eq(coordinatorLayout));
  }

  public void testFocusTraversalPolicy() {
    Palette palette = NlPaletteModel.get(getProject()).getPalette(NlLayoutType.LAYOUT);
    myPanel.populateUiModel(palette, mySurface);
    JList<Palette.Item> list = myPanel.getComponentTree().getLists().get(3);

    FocusTraversalPolicy policy = myPanel.getFocusTraversalPolicy();
    assertThat(classify(policy.getFirstComponent(myPanel))).isEqualTo(FocusComponent.LIST_IN_COMPONENT_TREE);
    assertThat(classify(policy.getLastComponent(myPanel))).isEqualTo(FocusComponent.CATEGORY_LIST);
    assertThat(classify(policy.getComponentAfter(myPanel, list))).isEqualTo(FocusComponent.CATEGORY_LIST);
    assertThat(classify(policy.getComponentBefore(myPanel, list))).isEqualTo(FocusComponent.CATEGORY_LIST);
    assertThat(classify(policy.getComponentAfter(myPanel, myPanel.getCategoryList()))).isEqualTo(FocusComponent.LIST_IN_COMPONENT_TREE);
    assertThat(classify(policy.getComponentBefore(myPanel, myPanel.getCategoryList()))).isEqualTo(FocusComponent.LIST_IN_COMPONENT_TREE);
    assertThat(classify(policy.getDefaultComponent(myPanel))).isEqualTo(FocusComponent.LIST_IN_COMPONENT_TREE);
    assertThat(classify(policy.getInitialComponent(mock(Window.class)))).isEqualTo(FocusComponent.LIST_IN_COMPONENT_TREE);
  }

  private enum FocusComponent {
    LIST_IN_COMPONENT_TREE,
    CATEGORY_LIST,
    OTHER
  }

  @NotNull
  private FocusComponent classify(@Nullable Component component) {
    if (component == null) {
      return FocusComponent.OTHER;
    }
    boolean isCategory = component == myPanel.getCategoryList();
    boolean isList = component instanceof JList && SwingUtilities.isDescendingFrom(component, myPanel.getComponentTree());
    if (isCategory && !isList) {
      return FocusComponent.CATEGORY_LIST;
    }
    if (isList && !isCategory) {
      return FocusComponent.LIST_IN_COMPONENT_TREE;
    }
    return FocusComponent.OTHER;
  }

  private void clickOnItem(int listIndex, int itemIndex) {
    TreeGrid<Palette.Item> tree = myPanel.getComponentTree();
    JList<Palette.Item> list = tree.getLists().get(listIndex);
    Rectangle bounds = list.getCellBounds(itemIndex, itemIndex);
    int x = bounds.x + bounds.width / 2;
    int y = bounds.y + bounds.height / 2;
    List<MouseListener> listeners = Arrays.stream(list.getMouseListeners())
      .filter(this::isInnerClassOfPanel)
      .collect(Collectors.toList());
    list.setSelectedIndex(itemIndex);
    MouseEvent event1 = new MouseEvent(list, MOUSE_PRESSED, System.currentTimeMillis(), BUTTON1_MASK | BUTTON1_DOWN_MASK, x, y, 1, false);
    for (MouseListener listener : listeners) {
      listener.mousePressed(event1);
    }
    MouseEvent event2 = new MouseEvent(list, MOUSE_RELEASED, System.currentTimeMillis(), BUTTON1_MASK | BUTTON1_DOWN_MASK, x, y, 1, false);
    for (MouseListener listener : listeners) {
      listener.mouseReleased(event2);
    }
    MouseEvent event3 = new MouseEvent(list, MOUSE_CLICKED, System.currentTimeMillis(), BUTTON1_MASK | BUTTON1_DOWN_MASK, x, y, 1, false);
    for (MouseListener listener : listeners) {
      listener.mouseClicked(event3);
    }
  }

  // This filtering is required on Linux and Windows, but not on Mac.
  private boolean isInnerClassOfPanel(@NotNull MouseListener listener) {
    Class<?> enclosingClass = listener.getClass().getEnclosingClass();
    return enclosingClass != null && enclosingClass.isInstance(myPanel);
  }

  private void checkPaletteGroupsAndItems(@NotNull NlLayoutType type) {
    Palette palette = NlPaletteModel.get(getProject()).getPalette(type);
    myPanel.populateUiModel(palette, mySurface);

    JList<Palette.Group> categoryList = myPanel.getCategoryList();
    ListModel<Palette.Group> model = categoryList.getModel();

    TreeGrid<Palette.Item> tree = myPanel.getComponentTree();
    List<JList<Palette.Item>> lists = tree.getLists();

    assertThat(model.getElementAt(0).toString()).isEqualTo("All");

    List<Palette.BaseItem> items = palette.getItems();
    assertThat(items.size()).isGreaterThan(1);
    if (items.get(0) instanceof Palette.Group) {
      for (int groupIndex = 0; groupIndex < items.size(); groupIndex++) {
        Palette.BaseItem item = items.get(groupIndex);
        assertThat(item).isInstanceOf(Palette.Group.class);
        assertThat(item).isSameAs(model.getElementAt(groupIndex + 1));
        Palette.Group group = (Palette.Group)item;

        checkItems(group.getItems(), lists.get(groupIndex));
      }
      assertThat(model.getSize()).isEqualTo(1 + items.size());
      assertThat(model.getSize()).isGreaterThan(1);
    }
    else {
      assertThat(lists.size()).isEqualTo(1);
      checkItems(palette.getItems(), lists.get(0));
      assertThat(model.getSize()).isEqualTo(1);
    }
  }

  private static void checkItems(@NotNull List<Palette.BaseItem> items, @NotNull JList<Palette.Item> list) {
    ListModel<Palette.Item> model = list.getModel();
    for (int index = 0; index < items.size(); index++) {
      Palette.BaseItem item = items.get(index);
      assertThat(item).isInstanceOf(Palette.Item.class);
      assertThat(item).isSameAs(model.getElementAt(index));
    }
    assertThat(model.getSize()).isEqualTo(items.size());
  }
}
