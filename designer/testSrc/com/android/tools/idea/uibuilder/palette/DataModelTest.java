/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.AndroidXConstants.APP_BAR_LAYOUT;
import static com.android.SdkConstants.BOTTOM_APP_BAR;
import static com.android.AndroidXConstants.BOTTOM_NAVIGATION_VIEW;
import static com.android.SdkConstants.BUTTON;
import static com.android.SdkConstants.CHIP;
import static com.android.SdkConstants.CHIP_GROUP;
import static com.android.AndroidXConstants.FLOATING_ACTION_BUTTON;
import static com.android.SdkConstants.FRAGMENT_CONTAINER_VIEW;
import static com.android.SdkConstants.IMAGE_VIEW;
import static com.android.SdkConstants.MATERIAL_TOOLBAR;
import static com.android.AndroidXConstants.NAVIGATION_VIEW;
import static com.android.AndroidXConstants.RECYCLER_VIEW;
import static com.android.SdkConstants.SCROLL_VIEW;
import static com.android.SdkConstants.SWITCH;
import static com.android.AndroidXConstants.TAB_ITEM;
import static com.android.AndroidXConstants.TAB_LAYOUT;
import static com.android.AndroidXConstants.TEXT_INPUT_LAYOUT;
import static com.android.SdkConstants.TEXT_VIEW;
import static com.android.tools.idea.uibuilder.palette.DataModel.FAVORITE_ITEMS;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.SdkConstants.PreferenceTags;
import com.android.tools.adtui.workbench.PropertiesComponentMock;
import com.android.tools.idea.concurrency.FutureUtils;
import com.android.tools.idea.uibuilder.type.LayoutEditorFileType;
import com.android.tools.idea.uibuilder.type.LayoutFileType;
import com.android.tools.idea.uibuilder.type.MenuFileType;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.PlatformTestUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.ListModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.stubbing.Answer;

public class DataModelTest extends AndroidTestCase {
  private DataModel myDataModel;
  private CategoryListModel myCategoryListModel;
  private ItemListModel myItemListModel;
  private DependencyManager myDependencyManager;
  private boolean myUseAndroidxDependencies = true;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myDependencyManager = mock(DependencyManager.class);
      when(myDependencyManager.useAndroidXDependencies()).thenAnswer((Answer<Boolean>)invocation -> myUseAndroidxDependencies);
    myDataModel = new DataModel(getTestRootDisposable(), myDependencyManager);
    myCategoryListModel = myDataModel.getCategoryListModel();
    myItemListModel = myDataModel.getItemListModel();
    registerApplicationService(PropertiesComponent.class, new PropertiesComponentMock());
  }

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
    myDataModel = null;
    myCategoryListModel = null;
    myItemListModel = null;
    myDependencyManager = null;
    myUseAndroidxDependencies = false;
  }

  /**
   * Sets the given layout type and waits for the updates to propagate in the model.
   *
   * There are several transitions from the UI thread to/from a pooled thread. The current code
   * does not provide a Future that ensures the CategoryListModel is properly updated.
   * Instead use this hack and listen for 2 updates: 1 from the original palette and another
   * update when the custom views have been analyzed.
   */
  private void setLayoutTypeAndWait(@NotNull DataModel dataModel, @NotNull LayoutEditorFileType type) {
    CountDownLatch latch = new CountDownLatch(2);
    CategoryListModel categoryListModel = dataModel.getCategoryListModel();
    ListDataListener listener = new ListDataListener() {
      @Override
      public void intervalAdded(@NotNull ListDataEvent event) {
      }

      @Override
      public void intervalRemoved(@NotNull ListDataEvent event) {
      }

      @Override
      public void contentsChanged(@NotNull ListDataEvent event) {
        latch.countDown();
      }
    };
    categoryListModel.addListDataListener(listener);
    dataModel.setLayoutTypeAsync(myFacet, type);
    try {
      // setLayoutTypeAsync requires some operations to be executed on the UI thread so let the events execute until it completes
      if (!FutureUtils.pumpEventsAndWaitForFuture(
        ApplicationManager.getApplication().executeOnPooledThread(() -> latch.await(10, TimeUnit.SECONDS)), 10, TimeUnit.SECONDS)) {
        fail("Category list not updated as expected");
      }
    }
    catch (Exception e) {
      fail(e.getMessage());
    }
    finally {
      categoryListModel.removeListDataListener(listener);
    }

    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
  }

  public void testEmptyModelHoldsUsableListModels() {
    assertThat(myCategoryListModel.getSize()).isEqualTo(0);
    assertThat(myItemListModel.getSize()).isEqualTo(0);
  }

  public void testCommonLayoutGroup() {
    setLayoutTypeAndWait(myDataModel, LayoutFileType.INSTANCE);
    assertThat(myCategoryListModel.getSize()).isEqualTo(9);
    assertThat(myCategoryListModel.getElementAt(0)).isEqualTo(DataModel.COMMON);
    assertThat(getElementsAsStrings(myItemListModel)).isEmpty();
    myDataModel.categorySelectionChanged(DataModel.COMMON);
    assertThat(getElementsAsStrings(myItemListModel))
      .containsExactly("TextView", "Button", "ImageView", "RecyclerView", "FragmentContainerView", "ScrollView", "Switch").inOrder();
  }

  public void testAddFavorite() {
    setLayoutTypeAndWait(myDataModel, LayoutFileType.INSTANCE);
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(2));
    myDataModel.addFavoriteItem(myDataModel.getPalette().getItemById(FLOATING_ACTION_BUTTON.newName()));
    assertThat(PropertiesComponent.getInstance().getList(FAVORITE_ITEMS))
      .containsExactly(TEXT_VIEW, BUTTON, IMAGE_VIEW, RECYCLER_VIEW.oldName(), RECYCLER_VIEW.newName(), FRAGMENT_CONTAINER_VIEW,
                       SCROLL_VIEW, SWITCH, PreferenceTags.CHECK_BOX_PREFERENCE, PreferenceTags.EDIT_TEXT_PREFERENCE,
                       PreferenceTags.SWITCH_PREFERENCE, PreferenceTags.PREFERENCE_CATEGORY, FLOATING_ACTION_BUTTON.newName()).inOrder();
    myDataModel.categorySelectionChanged(DataModel.COMMON);
    assertThat(getElementsAsStrings(myItemListModel))
      .containsExactly("TextView", "Button", "ImageView", "RecyclerView", "FragmentContainerView", "ScrollView", "Switch",
                       "FloatingActionButton")
      .inOrder();
  }

  public void testRemoveFavorite() {
    setLayoutTypeAndWait(myDataModel, LayoutFileType.INSTANCE);
    myDataModel.categorySelectionChanged(DataModel.COMMON);
    myDataModel.removeFavoriteItem(myDataModel.getPalette().getItemById("Button"));
    assertThat(PropertiesComponent.getInstance().getList(FAVORITE_ITEMS))
      .containsExactly(TEXT_VIEW, IMAGE_VIEW, RECYCLER_VIEW.oldName(), RECYCLER_VIEW.newName(), FRAGMENT_CONTAINER_VIEW,
                       SCROLL_VIEW, SWITCH, PreferenceTags.CHECK_BOX_PREFERENCE, PreferenceTags.EDIT_TEXT_PREFERENCE,
                       PreferenceTags.SWITCH_PREFERENCE, PreferenceTags.PREFERENCE_CATEGORY).inOrder();
    assertThat(getElementsAsStrings(myItemListModel))
      .containsExactly("TextView", "ImageView", "RecyclerView", "FragmentContainerView", "ScrollView", "Switch").inOrder();
  }

  public void testButtonsGroup() {
    setLayoutTypeAndWait(myDataModel, LayoutFileType.INSTANCE);
    assertThat(myCategoryListModel.getSize()).isEqualTo(9);
    assertThat(myCategoryListModel.getElementAt(2).getName()).isEqualTo("Buttons");
    assertThat(myCategoryListModel.hasMatchCounts()).isFalse();
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(2));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly(
      "Button", "ImageButton", "ChipGroup", "Chip", "CheckBox", "RadioGroup", "RadioButton", "ToggleButton", "Switch",
      "FloatingActionButton").inOrder();
  }

  public void testHelpersGroup() {
    setLayoutTypeAndWait(myDataModel, LayoutFileType.INSTANCE);
    assertThat(myCategoryListModel.getSize()).isEqualTo(9);
    assertThat(myCategoryListModel.getElementAt(6).getName()).isEqualTo("Helpers");
    assertThat(myCategoryListModel.hasMatchCounts()).isFalse();
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(6));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly(
      "Group", "Barrier (Horizontal)", "Barrier (Vertical)", "Flow", "Guideline (Horizontal)", "Guideline (Vertical)",
      "Layer", "ImageFilterView", "ImageFilterButton", "MockView").inOrder();
  }

  public void testContainersGroup() {
    setLayoutTypeAndWait(myDataModel, LayoutFileType.INSTANCE);
    assertThat(myCategoryListModel.getSize()).isEqualTo(9);
    assertThat(myCategoryListModel.getElementAt(5).getName()).isEqualTo("Containers");
    assertThat(myCategoryListModel.hasMatchCounts()).isFalse();
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(5));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly(
        "Spinner", "RecyclerView", "ScrollView", "HorizontalScrollView", "NestedScrollView", "ViewPager2", "CardView",
        "AppBarLayout", "BottomAppBar", "NavigationView", "BottomNavigationView", "Toolbar", "MaterialToolbar", "TabLayout", "TabItem",
        "ViewStub", "ViewAnimator", "ViewSwitcher", "<include>", "FragmentContainerView", "NavHostFragment", "<view>", "<requestFocus>")
      .inOrder();
  }

  public void testSearch() {
    setLayoutTypeAndWait(myDataModel, LayoutFileType.INSTANCE);
    myDataModel.setFilterPattern("ima");
    assertThat(getElementsAsStrings(myCategoryListModel))
      .containsExactly(DataModel.RESULTS.getName(), "Text", "Buttons", "Widgets", "Containers", "Helpers").inOrder();
    assertThat(getMatchCounts()).containsExactly(6, 1, 1, 1, 1, 2).inOrder();
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(0));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly("Number (Decimal)", "ImageButton", "ImageView",
                                                                      "ViewAnimator", "ImageFilterView", "ImageFilterButton").inOrder();
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(1));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly("Number (Decimal)");
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(2));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly("ImageButton");
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(3));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly("ImageView");
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(4));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly("ViewAnimator");
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(5));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly("ImageFilterView", "ImageFilterButton");
    myDataModel.setFilterPattern("Floating");
    assertThat(getElementsAsStrings(myCategoryListModel))
      .containsExactly(DataModel.RESULTS.getName(), "Buttons").inOrder();
    assertThat(getMatchCounts()).containsExactly(1, 1).inOrder();
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(0));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly("FloatingActionButton").inOrder();
    assertThat(myItemListModel.getElementAt(0).getTagName()).isEqualTo(FLOATING_ACTION_BUTTON.newName());

    myUseAndroidxDependencies = false;
    myDataModel.setFilterPattern("Floating");
    assertThat(getMatchCounts()).containsExactly(1, 1).inOrder();
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(0));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly("FloatingActionButton").inOrder();
    assertThat(myItemListModel.getElementAt(0).getTagName()).isEqualTo(FLOATING_ACTION_BUTTON.oldName());
  }

  public void testMetaSearch() {
    setLayoutTypeAndWait(myDataModel, LayoutFileType.INSTANCE);
    myDataModel.setFilterPattern("material");
    assertThat(getElementsAsStrings(myCategoryListModel))
      .containsExactly(DataModel.RESULTS.getName(), "Text", "Buttons", "Containers").inOrder();
    assertThat(getMatchCounts()).containsExactly(11, 1, 3, 7).inOrder();
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(0));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly(
      "TextInputLayout", "ChipGroup", "Chip", "FloatingActionButton", "AppBarLayout", "BottomAppBar", "NavigationView",
      "BottomNavigationView", "MaterialToolbar", "TabLayout", "TabItem").inOrder();
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(1));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly("TextInputLayout");
    assertThat(getElementsAsTagNames(myItemListModel)).containsExactly(TEXT_INPUT_LAYOUT.newName());
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(2));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly("ChipGroup", "Chip", "FloatingActionButton").inOrder();
    assertThat(getElementsAsTagNames(myItemListModel)).containsExactly(CHIP_GROUP, CHIP, FLOATING_ACTION_BUTTON.newName());
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(3));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly(
      "AppBarLayout", "BottomAppBar", "NavigationView", "BottomNavigationView", "MaterialToolbar", "TabLayout", "TabItem").inOrder();
    assertThat(getElementsAsTagNames(myItemListModel)).containsExactly(
      APP_BAR_LAYOUT.newName(), BOTTOM_APP_BAR, NAVIGATION_VIEW.newName(), BOTTOM_NAVIGATION_VIEW.newName(), MATERIAL_TOOLBAR,
      TAB_LAYOUT.newName(), TAB_ITEM.newName()).inOrder();
  }

  public void testMenuType() {
    setLayoutTypeAndWait(myDataModel, MenuFileType.INSTANCE);
    assertThat(myCategoryListModel.getSize()).isEqualTo(1);
    myDataModel.categorySelectionChanged(DataModel.COMMON);
    assertThat(getElementsAsStrings(myItemListModel))
      .containsExactly("Cast Button", "Menu Item", "Search Item", "Switch Item", "Menu", "Group").inOrder();
  }

  public void testUsingAndroidxDependencies() {
    setLayoutTypeAndWait(myDataModel, LayoutFileType.INSTANCE);
    myUseAndroidxDependencies = true;
    myDataModel.setFilterPattern("Floating");
    assertThat(getMatchCounts()).containsExactly(1, 1).inOrder();
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(0));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly("FloatingActionButton").inOrder();
    assertThat(myItemListModel.getElementAt(0).getTagName()).isEqualTo(FLOATING_ACTION_BUTTON.newName());
    // Check meta-search
    myDataModel.setFilterPattern("material");
    assertThat(getElementsAsStrings(myCategoryListModel))
      .containsExactly(DataModel.RESULTS.getName(), "Text", "Buttons", "Containers").inOrder();
    assertThat(getMatchCounts()).containsExactly(11, 1, 3, 7).inOrder();
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(1));
    assertThat(myItemListModel.getElementAt(0).getTagName()).isEqualTo(TEXT_INPUT_LAYOUT.newName());
  }

  public void testUsingOldDependencies() {
    myUseAndroidxDependencies = false;

    setLayoutTypeAndWait(myDataModel, LayoutFileType.INSTANCE);
    myDataModel.setFilterPattern("Floating");
    assertThat(getMatchCounts()).containsExactly(1, 1).inOrder();
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(0));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly("FloatingActionButton").inOrder();
    assertThat(myItemListModel.getElementAt(0).getTagName()).isEqualTo(FLOATING_ACTION_BUTTON.oldName());
    // Check meta-search
    myDataModel.setFilterPattern("material");
    assertThat(getElementsAsStrings(myCategoryListModel))
      .containsExactly(DataModel.RESULTS.getName(), "Text", "Buttons", "Containers").inOrder();
    assertThat(getMatchCounts()).containsExactly(7, 1, 1, 5).inOrder();
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(1));
    assertThat(myItemListModel.getElementAt(0).getTagName()).isEqualTo(TEXT_INPUT_LAYOUT.oldName());
  }

  public void testListenerIsRemovedAtDispose() {
    NlPaletteModel paletteModel = NlPaletteModel.get(myFacet);
    assertThat(paletteModel.getUpdateListeners()).isEmpty();

    Disposable disposable = Disposer.newDisposable();
    DataModel dataModel2 = new DataModel(disposable, myDependencyManager);
    setLayoutTypeAndWait(dataModel2, LayoutFileType.INSTANCE);
    assertThat(paletteModel.getUpdateListeners()).hasSize(1);

    Disposer.dispose(disposable);
    assertThat(paletteModel.getUpdateListeners()).isEmpty();
  }

  public void testCustomComponent() {
    myFixture.addClass(MY_TEXT_VIEW);
    setLayoutTypeAndWait(myDataModel, LayoutFileType.INSTANCE);

    assertThat(myCategoryListModel.getSize()).isEqualTo(10);
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(9));
    assertThat(myItemListModel.getSize()).isEqualTo(1);
    assertThat(myItemListModel.getElementAt(0).getTagName()).isEqualTo("com.example.MyTextView");
  }

  @NotNull
  private static List<String> getElementsAsStrings(@NotNull ListModel<?> model) {
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

    List<String> elements = new ArrayList<>();
    for (int index = 0; index < model.getSize(); index++) {
      elements.add(model.getElementAt(index).toString());
    }
    return elements;
  }

  @NotNull
  private static List<String> getElementsAsTagNames(@NotNull ListModel<Palette.Item> model) {
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

    List<String> elements = new ArrayList<>();
    for (int index = 0; index < model.getSize(); index++) {
      elements.add(model.getElementAt(index).getTagName());
    }
    return elements;
  }

  @NotNull
  private List<Integer> getMatchCounts() {
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

    List<Integer> matchCounts = new ArrayList<>();
    for (int index = 0; index < myCategoryListModel.getSize(); index++) {
      matchCounts.add(myCategoryListModel.getMatchCountAt(index));
    }
    return matchCounts;
  }

  @Language("JAVA")
  private static final String MY_TEXT_VIEW =
    "package com.example;\n" +
    "\n" +
    "import android.content.Context;\n" +
    "import android.widget.TextView;\n" +
    "\n" +
    "public class MyTextView extends TextView {\n" +
    "    public MyTextView(Context context) {\n" +
    "        super(context, null);\n" +
    "    }\n" +
    "}\n";
}
