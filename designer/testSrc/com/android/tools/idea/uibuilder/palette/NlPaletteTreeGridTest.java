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

import com.android.tools.adtui.treegrid.TreeGrid;
import com.android.tools.adtui.workbench.PropertiesComponentMock;
import com.android.tools.adtui.workbench.StartFilteringListener;
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.analytics.NlUsageTracker;
import com.android.tools.idea.common.model.NlLayoutType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiFile;
import com.intellij.util.ui.JBUI;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.xml.ws.Holder;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.event.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.LayoutTestUtilities.*;
import static com.android.tools.idea.uibuilder.palette.NlPaletteTreeGrid.DEFAULT_CATEGORY_WIDTH;
import static com.android.tools.idea.uibuilder.palette.NlPaletteTreeGrid.PALETTE_CATEGORY_WIDTH;
import static com.google.common.truth.Truth.assertThat;
import static java.awt.dnd.DnDConstants.ACTION_MOVE;
import static java.awt.event.InputEvent.BUTTON1_DOWN_MASK;
import static java.awt.event.InputEvent.BUTTON1_MASK;
import static java.awt.event.MouseEvent.*;
import static org.mockito.Mockito.*;

public class NlPaletteTreeGridTest extends LayoutTestCase {
  private NlDesignSurface mySurface;
  private DependencyManager myDependencyManager;
  private Runnable myCloseToolWindowCallback;
  private NlPaletteTreeGrid myPanel;
  private IconPreviewFactory myIconPreviewFactory;
  private BrowserLauncher myBrowserLauncher;
  private NlUsageTracker myUsageTracker;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myDependencyManager = mock(DependencyManager.class);
    SyncNlModel model = createModel();
    ScreenView screenView = createScreen(model);
    mySurface = (NlDesignSurface)screenView.getSurface();
    myBrowserLauncher = mock(BrowserLauncher.class);
    registerApplicationComponent(BrowserLauncher.class, myBrowserLauncher);
    registerApplicationComponent(PropertiesComponent.class, new PropertiesComponentMock());
    myCloseToolWindowCallback = mock(Runnable.class);
    myIconPreviewFactory = new IconPreviewFactory();
    PsiFile file = myFixture.configureByText("res/layout/mine.xml", "<LinearLayout/>");
    Configuration configuration = ConfigurationManager.getOrCreateInstance(myFacet).getConfiguration(file.getVirtualFile());
    when(mySurface.getConfiguration()).thenReturn(configuration);
    myUsageTracker = mockNlUsageTracker(mySurface);
    myPanel = new NlPaletteTreeGrid(
      getProject(), PaletteMode.ICON_AND_NAME, myDependencyManager, myCloseToolWindowCallback, mySurface, myIconPreviewFactory);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      cleanUsageTrackerAfterTesting(mySurface);
      Disposer.dispose(myPanel);
      Disposer.dispose(myIconPreviewFactory);
    } finally {
      super.tearDown();
    }
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
    Palette palette = NlPaletteModel.get(myFacet).getPalette(NlLayoutType.LAYOUT);
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
    Palette palette = NlPaletteModel.get(myFacet).getPalette(NlLayoutType.LAYOUT);
    myPanel.setMode(PaletteMode.LARGE_ICONS);
    myPanel.populateUiModel(palette, mySurface);
    TreeGrid<Palette.Item> grid = myPanel.getComponentTree();
    grid.getLists().forEach(list -> {
      assertThat(list.getLayoutOrientation()).isEqualTo(JList.HORIZONTAL_WRAP);
      assertThat(list.getFixedCellWidth()).isEqualTo(JBUI.scale(32));
      assertThat(list.getFixedCellHeight()).isEqualTo(JBUI.scale(32));
    });
    assertThat(myPanel.getMode()).isEqualTo(PaletteMode.LARGE_ICONS);
  }

  public void testSmallIcons() {
    Palette palette = NlPaletteModel.get(myFacet).getPalette(NlLayoutType.LAYOUT);
    myPanel.setMode(PaletteMode.SMALL_ICONS);
    myPanel.populateUiModel(palette, mySurface);
    TreeGrid<Palette.Item> grid = myPanel.getComponentTree();
    grid.getLists().forEach(list -> {
      assertThat(list.getLayoutOrientation()).isEqualTo(JList.HORIZONTAL_WRAP);
      assertThat(list.getFixedCellWidth()).isEqualTo(JBUI.scale(22));
      assertThat(list.getFixedCellHeight()).isEqualTo(JBUI.scale(22));
    });
    assertThat(myPanel.getMode()).isEqualTo(PaletteMode.SMALL_ICONS);
  }

  public void testSelectionChangeNotifications() {
    Palette palette = NlPaletteModel.get(myFacet).getPalette(NlLayoutType.LAYOUT);
    myPanel.populateUiModel(palette, mySurface);
    Holder<Palette.Item> lastSelectedItem = new Holder<>();
    myPanel.setSelectionListener(item -> lastSelectedItem.value = item);

    clickOnItem(3, 2);
    assertThat(lastSelectedItem.value.getTagName()).isEqualTo(LINEAR_LAYOUT);
  }

  public void testClickOnItemMissingFromProject() {
    Palette palette = NlPaletteModel.get(myFacet).getPalette(NlLayoutType.LAYOUT);
    Palette.Item coordinatorLayout = findItem(palette, FLOATING_ACTION_BUTTON);

    myPanel.populateUiModel(palette, mySurface);
    when(myDependencyManager.needsLibraryLoad(eq(coordinatorLayout))).thenReturn(true);

    Holder<Palette.Item> lastSelectedItem = new Holder<>();
    myPanel.setSelectionListener(item -> lastSelectedItem.value = item);

    clickOnItem(1, 7);

    assertThat(lastSelectedItem.value.getTagName()).isEqualTo(FLOATING_ACTION_BUTTON);
  }

  public void testSetFilter() {
    Palette palette = NlPaletteModel.get(myFacet).getPalette(NlLayoutType.LAYOUT);
    myPanel.populateUiModel(palette, mySurface);
    assertThat(myPanel.getComponentTree().getLists()).hasSize(7);
    assertThat(getVisibleItems().size()).isGreaterThan(30);

    myPanel.setFilter("utt");
    assertThat(myPanel.getComponentTree().getLists()).hasSize(1);
    assertThat(getVisibleTitles())
      .containsExactly("Button", "ImageButton", "RadioButton", "ToggleButton", "FloatingActionButton").inOrder();
  }

  public void testSelectCategoryWithExistingFilter() {
    Palette palette = NlPaletteModel.get(myFacet).getPalette(NlLayoutType.LAYOUT);
    myPanel.populateUiModel(palette, mySurface);
    myPanel.setFilter("utt");

    myPanel.getCategoryList().setSelectedValue(getGroup(palette, "Widgets"), false);
    assertThat(myPanel.getComponentTree().getLists()).hasSize(1);
    assertThat(getVisibleTitles())
      .containsExactly("Button", "ImageButton", "RadioButton", "ToggleButton", "FloatingActionButton").inOrder();
  }

  public void testSetFilterWithExistingSelectedCategory() {
    Palette palette = NlPaletteModel.get(myFacet).getPalette(NlLayoutType.LAYOUT);
    myPanel.populateUiModel(palette, mySurface);
    myPanel.getCategoryList().setSelectedValue(getGroup(palette, "Buttons"), false);
    myPanel.setFilter("utt");

    assertThat(myPanel.getComponentTree().getLists()).hasSize(1);
    assertThat(getVisibleTitles()).containsExactly("Button", "ToggleButton", "RadioButton", "ImageButton", "FloatingActionButton");
  }

  public void testRemoveFilterWithSelectedCategory() {
    Palette palette = NlPaletteModel.get(myFacet).getPalette(NlLayoutType.LAYOUT);
    myPanel.populateUiModel(palette, mySurface);
    myPanel.setFilter("utt");
    myPanel.getCategoryList().setSelectedValue(getGroup(palette, "Buttons"), false);

    myPanel.setFilter("");
    assertThat(myPanel.getComponentTree().getLists()).hasSize(7);
    assertThat(getVisibleTitles()).containsExactly("Button", "ImageButton", "CheckBox", "RadioGroup", "RadioButton", "ToggleButton", "Switch", "FloatingActionButton").inOrder();
  }

  public void testTypingInTreeStartsFiltering() {
    StartFiltering filtering = new StartFiltering();
    myPanel.setStartFiltering(filtering);
    JComponent tree = myPanel.getComponentTree();
    for (KeyListener listener : tree.getKeyListeners()) {
      listener.keyTyped(new KeyEvent(tree, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, 'u'));
    }
    assertThat(filtering.getChar()).isEqualTo('u');
  }

  public void testSelectAllCategoriesWithExistingFilter() {
    Palette palette = NlPaletteModel.get(myFacet).getPalette(NlLayoutType.LAYOUT);
    myPanel.populateUiModel(palette, mySurface);
    myPanel.getCategoryList().setSelectedValue(getGroup(palette, "Buttons"), false);
    myPanel.setFilter("utt");
    myPanel.getCategoryList().clearSelection();

    assertThat(myPanel.getComponentTree().getLists()).hasSize(1);
    assertThat(getVisibleTitles())
      .containsExactly("Button", "ImageButton", "RadioButton", "ToggleButton", "FloatingActionButton").inOrder();
  }

  public void testRemoveFilterAfterClearingCategorySelection() {
    Palette palette = NlPaletteModel.get(myFacet).getPalette(NlLayoutType.LAYOUT);
    myPanel.populateUiModel(palette, mySurface);
    myPanel.getCategoryList().setSelectedValue(getGroup(palette, "Buttons"), false);
    myPanel.setFilter("utt");
    myPanel.getCategoryList().clearSelection();
    myPanel.setFilter("");

    assertThat(myPanel.getComponentTree().getLists()).hasSize(7);
    assertThat(getVisibleItems().size()).isGreaterThan(30);
  }

  public void testClearCategoryAfterRemovingFilter() {
    Palette palette = NlPaletteModel.get(myFacet).getPalette(NlLayoutType.LAYOUT);
    myPanel.populateUiModel(palette, mySurface);
    myPanel.setFilter("utt");
    myPanel.getCategoryList().setSelectedValue(getGroup(palette, "Buttons"), false);
    myPanel.setFilter("");
    myPanel.getCategoryList().clearSelection();

    assertThat(myPanel.getComponentTree().getLists()).hasSize(7);
    assertThat(getVisibleItems().size()).isGreaterThan(30);
  }

  public void testShiftHelpOnPaletteItem() throws Exception {
    Palette palette = NlPaletteModel.get(myFacet).getPalette(NlLayoutType.LAYOUT);
    myPanel.populateUiModel(palette, mySurface);
    clickOnItem(0, 0);  // Select TextView
    AnAction action = findActionForKey(myPanel.getComponentTree(), KeyEvent.VK_F1, InputEvent.SHIFT_MASK);
    assertThat(action).isNotNull();

    DataContext context = mock(DataContext.class);
    AnActionEvent event = mock(AnActionEvent.class);
    when(event.getDataContext()).thenReturn(context);

    action.actionPerformed(event);
    verify(myBrowserLauncher).browse(eq("https://developer.android.com/reference/android/widget/TextView.html"), isNull(), isNull());
  }

  public void testDefaultInitialCategoryWidth() {
    assertThat(getCategoryWidth()).isEqualTo(JBUI.scale(DEFAULT_CATEGORY_WIDTH));
  }

  public void testInitialCategoryWidthIsReadFromOptions() {
    PropertiesComponent.getInstance().setValue(PALETTE_CATEGORY_WIDTH, "2017");
    Disposer.dispose(myPanel);
    myPanel = new NlPaletteTreeGrid(
      getProject(), PaletteMode.ICON_AND_NAME, myDependencyManager, myCloseToolWindowCallback, mySurface, myIconPreviewFactory);
    assertThat(getCategoryWidth()).isEqualTo(JBUI.scale(2017));
  }

  public void testInitialCategoryWidthFromMalformedOptionValueIsIgnored() {
    PropertiesComponent.getInstance().setValue(PALETTE_CATEGORY_WIDTH, "malformed");
    Disposer.dispose(myPanel);
    myPanel = new NlPaletteTreeGrid(
      getProject(), PaletteMode.ICON_AND_NAME, myDependencyManager, myCloseToolWindowCallback, mySurface, myIconPreviewFactory);
    assertThat(getCategoryWidth()).isEqualTo(JBUI.scale(DEFAULT_CATEGORY_WIDTH));
  }

  public void testInitialCategoryWidthIsSavedToOptions() {
    assertThat(PropertiesComponent.getInstance().getValue(PALETTE_CATEGORY_WIDTH)).isNull();
    setCategoryWidth(JBUI.scale(1971));
    fireComponentResize(myPanel.getCategoryList());
    assertThat(PropertiesComponent.getInstance().getValue(PALETTE_CATEGORY_WIDTH)).isEqualTo("1971");
  }

  public void testDragAndDrop() throws Exception {
    @Language("XML")
    String representation = "<Space\n" +
                            "    android:layout_width=\"wrap_content\"\n" +
                            "    android:layout_height=\"wrap_content\" />\n";

    Palette palette = NlPaletteModel.get(myFacet).getPalette(NlLayoutType.LAYOUT);
    myPanel.populateUiModel(palette, mySurface);
    clickOnItem(3, 7);  // Select Space (to avoid preview)

    JList<Palette.Item> list = myPanel.getComponentTree().getSelectedList();
    assertThat(list).isNotNull();
    MouseEvent event = mock(MouseEvent.class);
    when(event.getPoint()).thenReturn(new Point(50, 50));
    TransferHandler handler = list.getTransferHandler();
    imitateDrop(handler, list);

    verify(myUsageTracker).logDropFromPalette(SPACE, representation, PaletteMode.ICON_AND_NAME, "All", -1);
  }

  private static void imitateDrop(@NotNull TransferHandler handler, @NotNull JComponent component) throws Exception {
    Method createTransferable = handler.getClass().getDeclaredMethod("createTransferable", JComponent.class);
    createTransferable.setAccessible(true);
    Transferable transferable = (Transferable)createTransferable.invoke(handler, component);

    Method exportDone = handler.getClass().getDeclaredMethod("exportDone", JComponent.class, Transferable.class, int.class);
    exportDone.setAccessible(true);
    exportDone.invoke(handler, component, transferable, ACTION_MOVE);
  }

  private List<String> getVisibleTitles() {
    return getVisibleItems().stream().map(Palette.Item::getTitle).collect(Collectors.toList());
  }

  private List<Palette.Item> getVisibleItems() {
    List<Palette.Item> items = new ArrayList<>();
    for (JList<Palette.Item> list : myPanel.getComponentTree().getLists()) {
      if (list.isVisible()) {
        addItemsFromList(list, items);
      }
    }
    return items;
  }

  private static void addItemsFromList(@NotNull JList<Palette.Item> list, @NotNull List<Palette.Item> items) {
    ListModel<Palette.Item> model = list.getModel();
    for (int index = 0; index < model.getSize(); index++) {
      items.add(model.getElementAt(index));
    }
  }

  private static Palette.Group getGroup(@NotNull Palette palette, @NotNull String groupName) {
    for (Palette.BaseItem item : palette.getItems()) {
      if (item instanceof Palette.Group) {
        Palette.Group group = (Palette.Group)item;
        if (group.getName().equals(groupName)) {
          return group;
        }
      }
    }
    throw new RuntimeException("Group not found:" + groupName);
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
    Palette palette = NlPaletteModel.get(myFacet).getPalette(type);
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

  private static void fireComponentResize(@NotNull JComponent component) {
    ComponentEvent event = mock(ComponentEvent.class);
    for (ComponentListener listener : component.getComponentListeners()) {
      listener.componentResized(event);
    }
  }

  private int getCategoryWidth() {
    return myPanel.getSplitter().getFirstSize();
  }

  private void setCategoryWidth(@SuppressWarnings("SameParameterValue") int width) {
    myPanel.getSplitter().setFirstSize(width);
  }

  @NotNull
  private SyncNlModel createModel() {
    return model("linear.xml",
                 component(LINEAR_LAYOUT)
                   .withBounds(0, 0, 1000, 1500)
                   .id("@id/linear")
                   .matchParentWidth()
                   .matchParentHeight()).build();
  }

  @NotNull
  private static Palette.Item findItem(@NotNull Palette palette, @NotNull String tagName) {
    Holder<Palette.Item> found = new Holder<>();
    palette.accept(item -> {
      if (item.getTagName().equals(tagName)) {
        found.value = item;
      }
    });
    if (found.value == null) {
      throw new RuntimeException("The item: " + tagName + " was not found on the palette.");
    }
    return found.value;
  }

  private static class StartFiltering implements StartFilteringListener {
    private char myChar;

    @Override
    public void startFiltering(char character) {
      myChar = character;
    }

    public char getChar() {
      return myChar;
    }
  }
}
