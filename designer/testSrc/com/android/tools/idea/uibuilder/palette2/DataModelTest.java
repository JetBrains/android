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

import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.workbench.PropertiesComponentMock;
import com.android.tools.idea.common.model.NlLayoutType;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId;
import com.android.tools.idea.uibuilder.palette.Palette;
import com.intellij.ide.util.PropertiesComponent;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.ArgumentMatchers;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.palette2.DataModel.FAVORITE_ITEMS;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DataModelTest extends AndroidTestCase {
  private DataModel myDataModel;
  private CategoryListModel myCategoryListModel;
  private ItemListModel myItemListModel;
  private DependencyManager myDependencyManager;
  private List<GoogleMavenArtifactId> myDependencies;
  private boolean myHasAndroidxDeps;
  private AndroidVersion myVersion27 = new AndroidVersion(27);
  private AndroidVersion myVersion28 = new AndroidVersion(28);

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myDependencies = new ArrayList<>();
    myDependencyManager = mock(DependencyManager.class);
    when(myDependencyManager.useAndroidxDependencies()).thenAnswer(new Answer<Boolean>() {
      @Override
      public Boolean answer(@NotNull InvocationOnMock invocation) {
        return myHasAndroidxDeps;
      }
    });
    when(myDependencyManager.dependsOn(ArgumentMatchers.any())).thenAnswer(new Answer<Boolean>() {
      @Override
      public Boolean answer(@NotNull InvocationOnMock invocation) {
        GoogleMavenArtifactId artifactId = invocation.getArgument(0);
        return myDependencies.contains(artifactId);
      }
    });
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
    myHasAndroidxDeps = false;
    myDependencies = null;
  }

  public void testEmptyModelHoldsUsableListModels() {
    assertThat(myCategoryListModel.getSize()).isEqualTo(0);
    assertThat(myItemListModel.getSize()).isEqualTo(0);
  }

  public void testCommonLayoutGroup() {
    myDataModel.setLayoutType(myFacet, NlLayoutType.LAYOUT, myVersion28);
    assertThat(myCategoryListModel.getSize()).isEqualTo(8);
    assertThat(myCategoryListModel.getElementAt(0)).isEqualTo(DataModel.COMMON);
    assertThat(getElementsAsStrings(myItemListModel)).isEmpty();

    myDataModel.categorySelectionChanged(DataModel.COMMON);
    assertThat(getElementsAsStrings(myItemListModel))
      .containsExactly("TextView", "Button", "ImageView", "RecyclerView", "<fragment>", "ScrollView", "Switch").inOrder();
  }

  public void testAddFavorite() {
    myDataModel.setLayoutType(myFacet, NlLayoutType.LAYOUT, myVersion28);
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(2));
    myDataModel.addFavoriteItem(myDataModel.getPalette().getItemById(FLOATING_ACTION_BUTTON.newName()));

    assertThat(PropertiesComponent.getInstance().getValues(FAVORITE_ITEMS)).asList()
      .containsExactly(TEXT_VIEW, BUTTON, IMAGE_VIEW, RECYCLER_VIEW.oldName(), RECYCLER_VIEW.newName(), VIEW_FRAGMENT, SCROLL_VIEW, SWITCH,
                       FLOATING_ACTION_BUTTON.newName()).inOrder();

    myDataModel.categorySelectionChanged(DataModel.COMMON);
    assertThat(getElementsAsStrings(myItemListModel))
      .containsExactly("TextView", "Button", "ImageView", "RecyclerView", "<fragment>", "ScrollView", "Switch", "FloatingActionButton")
      .inOrder();
  }

  public void testRemoveFavorite() {
    myDataModel.setLayoutType(myFacet, NlLayoutType.LAYOUT, myVersion28);

    myDataModel.categorySelectionChanged(DataModel.COMMON);
    myDataModel.removeFavoriteItem(myDataModel.getPalette().getItemById("Button"));

    assertThat(PropertiesComponent.getInstance().getValues(FAVORITE_ITEMS)).asList()
      .containsExactly(TEXT_VIEW, IMAGE_VIEW, RECYCLER_VIEW.oldName(), RECYCLER_VIEW.newName(), VIEW_FRAGMENT, SCROLL_VIEW, SWITCH).inOrder();

    assertThat(getElementsAsStrings(myItemListModel))
      .containsExactly("TextView", "ImageView", "RecyclerView", "<fragment>", "ScrollView", "Switch").inOrder();
  }

  public void testButtonsGroup() {
    myDataModel.setLayoutType(myFacet, NlLayoutType.LAYOUT, myVersion28);
    assertThat(myCategoryListModel.getSize()).isEqualTo(8);
    assertThat(myCategoryListModel.getElementAt(2).getName()).isEqualTo("Buttons");
    assertThat(myCategoryListModel.hasMatchCounts()).isFalse();

    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(2));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly(
      "Button", "ImageButton", "ChipGroup", "Chip", "CheckBox", "RadioGroup", "RadioButton", "ToggleButton", "Switch",
      "FloatingActionButton").inOrder();
  }

  public void testContainersGroup() {
    myDataModel.setLayoutType(myFacet, NlLayoutType.LAYOUT, myVersion28);
    assertThat(myCategoryListModel.getSize()).isEqualTo(8);
    assertThat(myCategoryListModel.getElementAt(5).getName()).isEqualTo("Containers");
    assertThat(myCategoryListModel.hasMatchCounts()).isFalse();

    StudioFlags.ENABLE_NAV_EDITOR.override(true);
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(5));
    assertThat(getElementsAsStrings(myItemListModel)).contains("NavHostFragment");

    StudioFlags.ENABLE_NAV_EDITOR.override(false);
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(5));
    assertThat(getElementsAsStrings(myItemListModel)).doesNotContain("NavHostFragment");
    StudioFlags.ENABLE_NAV_EDITOR.clearOverride();
  }

  public void testSearch() {
    myDataModel.setLayoutType(myFacet, NlLayoutType.LAYOUT, myVersion28);
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

    myDataModel.setFilterPattern("Floating");
    assertThat(getElementsAsStrings(myCategoryListModel))
      .containsExactly(DataModel.RESULTS.getName(), "Buttons").inOrder();
    assertThat(getMatchCounts()).containsExactly(1, 1).inOrder();
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(0));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly("FloatingActionButton").inOrder();
    assertThat(myItemListModel.getElementAt(0).getTagName()).isEqualTo(FLOATING_ACTION_BUTTON.newName());

    myDependencies.add(GoogleMavenArtifactId.DESIGN);
    myDataModel.setFilterPattern("Floating");
    assertThat(getMatchCounts()).containsExactly(1, 1).inOrder();
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(0));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly("FloatingActionButton").inOrder();
    assertThat(myItemListModel.getElementAt(0).getTagName()).isEqualTo(FLOATING_ACTION_BUTTON.oldName());
  }

  public void testMetaSearch() {
    myDataModel.setLayoutType(myFacet, NlLayoutType.LAYOUT, myVersion28);
    myDataModel.setFilterPattern("material");

    assertThat(getElementsAsStrings(myCategoryListModel))
      .containsExactly(DataModel.RESULTS.getName(), "Text", "Buttons", "Containers").inOrder();
    assertThat(getMatchCounts()).containsExactly(10, 1, 3, 6).inOrder();

    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(0));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly(
      "TextInputLayout", "ChipGroup", "Chip", "FloatingActionButton", "AppBarLayout", "BottomAppBar", "NavigationView",
      "BottomNavigationView", "TabLayout", "TabItem").inOrder();

    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(1));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly("TextInputLayout");
    assertThat(getElementsAsTagNames(myItemListModel)).containsExactly(TEXT_INPUT_LAYOUT.newName());

    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(2));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly("ChipGroup", "Chip", "FloatingActionButton").inOrder();
    assertThat(getElementsAsTagNames(myItemListModel)).containsExactly(CHIP_GROUP, CHIP, FLOATING_ACTION_BUTTON.newName());

    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(3));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly(
      "AppBarLayout", "BottomAppBar", "NavigationView", "BottomNavigationView", "TabLayout", "TabItem").inOrder();
    assertThat(getElementsAsTagNames(myItemListModel)).containsExactly(
      APP_BAR_LAYOUT.newName(), BOTTOM_APP_BAR, NAVIGATION_VIEW.newName(), BOTTOM_NAVIGATION_VIEW.newName(), TAB_LAYOUT.newName(),
      TAB_ITEM.newName()).inOrder();
  }

  public void testMetaSearchWithMaterial1() {
    myDependencies.add(GoogleMavenArtifactId.DESIGN);

    myDataModel.setLayoutType(myFacet, NlLayoutType.LAYOUT, myVersion28);
    myDataModel.setFilterPattern("material");

    assertThat(getElementsAsStrings(myCategoryListModel))
      .containsExactly(DataModel.RESULTS.getName(), "Text", "Buttons", "Containers").inOrder();
    assertThat(getMatchCounts()).containsExactly(10, 1, 3, 6).inOrder();

    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(0));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly(
      "TextInputLayout", "ChipGroup", "Chip", "FloatingActionButton", "AppBarLayout", "BottomAppBar", "NavigationView",
      "BottomNavigationView", "TabLayout", "TabItem").inOrder();

    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(1));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly("TextInputLayout");
    assertThat(getElementsAsTagNames(myItemListModel)).containsExactly(TEXT_INPUT_LAYOUT.oldName());

    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(2));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly("ChipGroup", "Chip", "FloatingActionButton").inOrder();
    assertThat(getElementsAsTagNames(myItemListModel)).containsExactly(CHIP_GROUP, CHIP, FLOATING_ACTION_BUTTON.oldName());

    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(3));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly(
      "AppBarLayout", "BottomAppBar", "NavigationView", "BottomNavigationView", "TabLayout", "TabItem").inOrder();
    assertThat(getElementsAsTagNames(myItemListModel)).containsExactly(
      APP_BAR_LAYOUT.oldName(), BOTTOM_APP_BAR, NAVIGATION_VIEW.oldName(), BOTTOM_NAVIGATION_VIEW.oldName(), TAB_LAYOUT.oldName(),
      TAB_ITEM.oldName()).inOrder();
  }

  public void testMetaSearchWithMaterial1WhenCompileSdkIsLessThan28() {
    myDependencies.add(GoogleMavenArtifactId.DESIGN);

    myDataModel.setLayoutType(myFacet, NlLayoutType.LAYOUT, myVersion27);
    myDataModel.setFilterPattern("material");

    assertThat(getElementsAsStrings(myCategoryListModel))
      .containsExactly(DataModel.RESULTS.getName(), "Text", "Buttons", "Containers").inOrder();
    assertThat(getMatchCounts()).containsExactly(7, 1, 1, 5).inOrder();

    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(0));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly(
      "TextInputLayout", "FloatingActionButton", "AppBarLayout", "NavigationView",
      "BottomNavigationView", "TabLayout", "TabItem").inOrder();

    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(1));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly("TextInputLayout");
    assertThat(getElementsAsTagNames(myItemListModel)).containsExactly(TEXT_INPUT_LAYOUT.oldName());

    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(2));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly("FloatingActionButton").inOrder();
    assertThat(getElementsAsTagNames(myItemListModel)).containsExactly(FLOATING_ACTION_BUTTON.oldName());

    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(3));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly(
      "AppBarLayout", "NavigationView", "BottomNavigationView", "TabLayout", "TabItem").inOrder();
    assertThat(getElementsAsTagNames(myItemListModel)).containsExactly(
      APP_BAR_LAYOUT.oldName(), NAVIGATION_VIEW.oldName(), BOTTOM_NAVIGATION_VIEW.oldName(), TAB_LAYOUT.oldName(),
      TAB_ITEM.oldName()).inOrder();
  }

  public void testMenuType() {
    myDataModel.setLayoutType(myFacet, NlLayoutType.MENU, myVersion28);

    assertThat(myCategoryListModel.getSize()).isEqualTo(1);
    myDataModel.categorySelectionChanged(DataModel.COMMON);
    assertThat(getElementsAsStrings(myItemListModel))
      .containsExactly("Cast Button", "Menu Item", "Search Item", "Switch Item", "Menu", "Group").inOrder();
  }

  public void testUsingAndroidxDependencies() {
    myDataModel.setLayoutType(myFacet, NlLayoutType.LAYOUT, myVersion28);

    myHasAndroidxDeps = true;
    myDataModel.setFilterPattern("Floating");
    assertThat(getMatchCounts()).containsExactly(1, 1).inOrder();
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(0));
    assertThat(getElementsAsStrings(myItemListModel)).containsExactly("FloatingActionButton").inOrder();
    assertThat(myItemListModel.getElementAt(0).getTagName()).isEqualTo("com.google.android.material.floatingactionbutton.FloatingActionButton");

    // Check meta-search
    myDataModel.setFilterPattern("material");
    assertThat(getElementsAsStrings(myCategoryListModel))
      .containsExactly(DataModel.RESULTS.getName(), "Text", "Buttons", "Containers").inOrder();
    assertThat(getMatchCounts()).containsExactly(10, 1, 3, 6).inOrder();
    myDataModel.categorySelectionChanged(myCategoryListModel.getElementAt(1));
    assertThat(myItemListModel.getElementAt(0).getTagName()).isEqualTo("com.google.android.material.textfield.TextInputLayout");
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
  private static List<String> getElementsAsTagNames(@NotNull ListModel<Palette.Item> model) {
    List<String> elements = new ArrayList<>();
    for (int index = 0; index < model.getSize(); index++) {
      elements.add(model.getElementAt(index).getTagName());
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
