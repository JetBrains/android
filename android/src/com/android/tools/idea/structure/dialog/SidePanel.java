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
package com.android.tools.idea.structure.dialog;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.roots.ui.configuration.SidePanelCountLabel;
import com.intellij.openapi.roots.ui.configuration.SidePanelSeparator;
import com.intellij.openapi.ui.popup.ListItemDescriptor;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.SeparatorWithText;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import com.intellij.ui.navigation.Place.Navigator;
import com.intellij.ui.popup.list.GroupedItemsListRenderer;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.intellij.ui.ScrollPaneFactory.createScrollPane;
import static com.intellij.util.ui.UIUtil.SIDE_PANEL_BACKGROUND;
import static javax.swing.ListSelectionModel.SINGLE_SELECTION;

public class SidePanel extends JPanel {
  @NotNull private final JBList myList;
  @NotNull private final DefaultListModel<Place> myListModel;
  @NotNull private final Navigator myNavigator;

  @NotNull private final List<Place> myPlaces = Lists.newArrayList();
  @NotNull private final Map<Integer, String> mySeparatorByIndex = Maps.newHashMap();
  @NotNull private final Map<Place, Presentation> myPresentationByPlace = Maps.newHashMap();

  @NotNull private final History myHistory;

  public SidePanel(@NotNull Navigator navigator, @NotNull History history) {
    super(new BorderLayout());
    myHistory = history;
    myNavigator = navigator;

    myListModel = new DefaultListModel<>();
    myList = new JBList(myListModel);
    myList.setBackground(SIDE_PANEL_BACKGROUND);
    myList.setBorder(new EmptyBorder(5, 0, 0, 0));

    ListItemDescriptor<Place> descriptor = new ListItemDescriptor<Place>() {
      @Override
      public String getTextFor(Place place) {
        return myPresentationByPlace.get(place).getText();
      }

      @Override
      public String getTooltipFor(Place place) {
        return getTextFor(place);
      }

      @Override
      public Icon getIconFor(Place place) {
        return EmptyIcon.create(16, 20);
      }

      @Override
      public boolean hasSeparatorAboveOf(Place value) {
        return getSeparatorAbove(value) != null;
      }

      @Override
      public String getCaptionAboveOf(Place value) {
        return getSeparatorAbove(value);
      }
    };

    myList.setCellRenderer(new GroupedItemsListRenderer(descriptor) {
      JPanel myExtraPanel;
      SidePanelCountLabel myCountLabel;
      CellRendererPane myValidationParent = new CellRendererPane();
      {
        mySeparatorComponent.setCaptionCentered(false);
        myList.add(myValidationParent);
      }

      @Override
      protected Color getForeground() {
        return new JBColor(Gray._60, Gray._140);
      }

      @Override
      protected SeparatorWithText createSeparator() {
        return new SidePanelSeparator();
      }

      @Override
      protected void layout() {
        myRendererComponent.add(mySeparatorComponent, BorderLayout.NORTH);
        myExtraPanel.add(myComponent, BorderLayout.CENTER);
        myExtraPanel.add(myCountLabel, BorderLayout.EAST);
        myRendererComponent.add(myExtraPanel, BorderLayout.CENTER);
      }

      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        layout();
        myCountLabel.setText("");
        Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof Place) {
          Place place = (Place)value;
          Object category = place.getPath("category");
          if (category instanceof CounterDisplayConfigurable) {
            CounterDisplayConfigurable configurable = (CounterDisplayConfigurable)category;
            int count = configurable.getCount();
            if (count > 0) {
              myCountLabel.setSelected(isSelected);
              myCountLabel.setText(count > 100 ? "100+" : String.valueOf(count));
            }
          }
        }
        return component;
      }

      @Override
      protected JComponent createItemComponent() {
        myExtraPanel = new NonOpaquePanel(new BorderLayout());
        myCountLabel = new SidePanelCountLabel();
        JComponent component = super.createItemComponent();

        myTextLabel.setForeground(Gray._240);
        myTextLabel.setOpaque(true);

        return component;
      }

      @Override
      protected Color getBackground() {
        return SIDE_PANEL_BACKGROUND;
      }
    });

    add(createScrollPane(myList, true), BorderLayout.CENTER);

    myList.setSelectionMode(SINGLE_SELECTION);
    myList.addListSelectionListener(e -> {
      if (e.getValueIsAdjusting()) return;
      Object value = myList.getSelectedValue();
      if (value != null) {
        myNavigator.navigateTo(((Place)value), false);
      }
    });
  }

  @NotNull
  public JBList getList() {
    return myList;
  }

  public void addPlace(@NotNull Place place, @NotNull Presentation presentation) {
    myListModel.addElement(place);
    myPlaces.add(place);
    myPresentationByPlace.put(place, presentation);
    revalidate();
    repaint();
  }

  public void clear() {
    myListModel.clear();
    myPlaces.clear();
    myPresentationByPlace.clear();
    mySeparatorByIndex.clear();
  }

  public void addSeparator(@NotNull String text) {
    mySeparatorByIndex.put(myPlaces.size(), text);
  }

  @Nullable
  public String getSeparatorAbove(@NotNull Place place) {
    return mySeparatorByIndex.get(myPlaces.indexOf(place));
  }

  @NotNull
  public Collection<Place> getPlaces() {
    return myPlaces;
  }

  public void select(@NotNull Place place) {
    myList.setSelectedValue(place, true);
  }
}
