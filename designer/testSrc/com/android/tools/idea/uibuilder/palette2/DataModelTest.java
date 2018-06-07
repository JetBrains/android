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
package com.android.tools.idea.uibuilder.palette2;

import com.android.tools.adtui.workbench.PropertiesComponentMock;
import com.android.tools.idea.common.model.NlLayoutType;
import com.intellij.ide.util.PropertiesComponent;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.palette2.DataModel.FAVORITE_ITEMS;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

public class DataModelTest extends AndroidTestCase {
  private DataModel myDataModel;
  private CategoryListModel myCategoryListModel;
  private ItemListModel myItemListModel;
  private DependencyManager myDependencyManager;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myDependencyManager = mock(DependencyManager.class);
    myDataModel = new DataModel(myDependencyManager);
    myCategoryListModel = myDataModel.getCategoryListModel();
    myItemListModel = myDataModel.getItemListModel();
    registerApplicationComponent(PropertiesComponent.class, new PropertiesComponentMock());
  }

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
    myDataModel = null;
    myCategoryListModel = null;
    myItemListModel = null;
    myDependencyManager = null;
  }

  public void testEmptyModelHoldsUsableListModels() {
    assertThat(myCategoryListModel.getSize()).isEqualTo(0);
    assertThat(myItemListModel.getSize()).isEqualTo(0);
  }

  public void testCommonLayoutGroup() {
    myDataModel.setLayoutType(myFacet, NlLayoutType.LAYOUT);
    assertThat(myCategoryListModel.getSize()).isEqualTo(8);
    assertThat(myCategoryListModel.getElementAt(0)).isEqualTo(DataModel.COMMON);
    assertThat(getElementsAsStrings(myItemListModel)).isEmpty();

    myDataModel.categorySelectionChanged(DataModel.COMMON);
    assertThat(getElementsAsStrings(myItemListModel))
      .containsExactly("TextView", "Button", "ImageView", "RecyclerView", "<fragment>", "ScrollView", "Switch").inOrder();
  }

  public void testAddFavorite() {
    myDataModel.setLayoutType(myFacet, NlLayoutType.LAYOUT);
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(2));
    myDataModel.addFavoriteItem(myDataModel.getPalette().getItemById(FLOATING_ACTION_BUTTON));

    assertThat(PropertiesComponent.getInstance().getValues(FAVORITE_ITEMS)).asList()
      .containsExactly(TEXT_VIEW, BUTTON, IMAGE_VIEW, RECYCLER_VIEW, VIEW_FRAGMENT, SCROLL_VIEW, SWITCH, FLOATING_ACTION_BUTTON).inOrder();

    myDataModel.categorySelectionChanged(DataModel.COMMON);
    assertThat(getElementsAsStrings(myItemListModel))
      .containsExactly("TextView", "Button", "ImageView", "RecyclerView", "<fragment>", "ScrollView", "Switch", "FloatingActionButton").inOrder();
  }

  public void testRemoveFavorite() {
    myDataModel.setLayoutType(myFacet, NlLayoutType.LAYOUT);

    myDataModel.categorySelectionChanged(DataModel.COMMON);
    myDataModel.removeFavoriteItem(myDataModel.getPalette().getItemById("Button"));

    assertThat(PropertiesComponent.getInstance().getValues(FAVORITE_ITEMS)).asList()
      .containsExactly(TEXT_VIEW, IMAGE_VIEW, RECYCLER_VIEW, VIEW_FRAGMENT, SCROLL_VIEW, SWITCH).inOrder();

    assertThat(getElementsAsStrings(myItemListModel))
      .containsExactly("TextView", "ImageView", "RecyclerView", "<fragment>", "ScrollView", "Switch").inOrder();
  }

  public void testButtonsGroup() {
    myDataModel.setLayoutType(myFacet, NlLayoutType.LAYOUT);
    assertThat(myCategoryListModel.getSize()).isEqualTo(8);
    assertThat(myCategoryListModel.getElementAt(2).getName()).isEqualTo("Buttons");
    assertThat(myCategoryListModel.hasMatchCounts()).isFalse();

    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(2));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly(
      "Button", "ImageButton", "CheckBox", "RadioGroup", "RadioButton", "ToggleButton", "Switch", "FloatingActionButton").inOrder();
  }

  public void testSearch() {
    myDataModel.setLayoutType(myFacet, NlLayoutType.LAYOUT);
    myDataModel.setFilterPattern("ima");

    assertThat(getElementsAsStrings(myCategoryListModel))
      .containsExactly(DataModel.RESULTS.getName(), "Text", "Buttons", "Widgets").inOrder();
    assertThat(getMatchCounts()).containsExactly(3, 1, 1, 1).inOrder();

    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(0));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly("Number (Decimal)", "ImageButton", "ImageView").inOrder();

    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(1));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly("Number (Decimal)");

    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(2));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly("ImageButton");

    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(3));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly("ImageView");
  }

  public void testMetaSearch() {
    myDataModel.setLayoutType(myFacet, NlLayoutType.LAYOUT);
    myDataModel.setFilterPattern("material");

    assertThat(getElementsAsStrings(myCategoryListModel))
      .containsExactly(DataModel.RESULTS.getName(), "Text", "Buttons", "Containers").inOrder();
    assertThat(getMatchCounts()).containsExactly(4, 1, 1, 2).inOrder();

    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(0));
    assertThat(getElementsAsStrings(myItemListModel))
      .containsExactly("TextInputLayout", "FloatingActionButton", "TabLayout", "TabItem").inOrder();

    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(1));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly("TextInputLayout");

    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(2));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly("FloatingActionButton");

    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(3));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly("TabLayout", "TabItem").inOrder();
  }

  public void testMenuType() {
    myDataModel.setLayoutType(myFacet, NlLayoutType.MENU);

    assertThat(myCategoryListModel.getSize()).isEqualTo(1);
    myDataModel.categorySelectionChanged(DataModel.COMMON);
    assertThat(getElementsAsStrings(myItemListModel))
      .containsExactly("Cast Button", "Menu Item", "Search Item", "Switch Item", "Menu", "Group").inOrder();
  }

  @NotNull
  private static List<String> getElementsAsStrings(@NotNull ListModel<?> model) {
    List<String> elements = new ArrayList<>();
    for (int index = 0; index < model.getSize(); index++) {
      elements.add(model.getElementAt(index).toString());
    }
    return elements;
  }

  @NotNull
  private List<Integer> getMatchCounts() {
    List<Integer> matchCounts = new ArrayList<>();
    for (int index = 0; index < myCategoryListModel.getSize(); index++) {
      matchCounts.add(myCategoryListModel.getMatchCountAt(index));
    }
    return matchCounts;
  }
}
