// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.naveditor.editor;

import com.android.annotations.VisibleForTesting;
import com.android.tools.adtui.ASGallery;
import com.android.tools.adtui.actions.DropDownAction;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.VerticalLayout;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * "Add" popup menu in the navigation editor.
 */
@VisibleForTesting
public class AddExistingDestinationMenu extends DropDownAction {

  private final NavDesignSurface mySurface;
  @VisibleForTesting
  public final List<Destination> myDestinations;
  private JPanel myMainPanel;
  @VisibleForTesting
  public ASGallery<Destination> myDestinationsGallery;

  private MediaTracker myMediaTracker;
  @VisibleForTesting
  JBLoadingPanel myLoadingPanel;

  AddExistingDestinationMenu(@NotNull NavDesignSurface surface, @NotNull List<Destination> destinations) {
    super("", "Add Destination", StudioIcons.NavEditor.Toolbar.ADD_EXISTING);
    mySurface = surface;
    myDestinations = destinations;
  }

  @Nullable
  @Override
  protected JPanel createCustomComponentPopup() {
    if (myMainPanel == null) {
      myMainPanel = createSelectionPanel();
    }
    return myMainPanel;
  }

  @VisibleForTesting
  @NotNull
  public JPanel getMainPanel() {
    return myMainPanel;
  }

  @NotNull
  private JPanel createSelectionPanel() {
    CollectionListModel<Destination> listModel = new CollectionListModel<>(myDestinations);
    // Don't want to show an exact number of rows, since then it's not obvious there's another row available.
    myDestinationsGallery = new ASGallery<Destination>(
      listModel, d -> null, Destination::getLabel, new Dimension(73, 94), null) {
      @Override
      @NotNull
      public Dimension getPreferredScrollableViewportSize() {
        Dimension cellSize = computeCellSize();
        int heightInsets = getInsets().top + getInsets().bottom;
        int widthInsets = getInsets().left + getInsets().right;
        // Don't want to show an exact number of rows, since then it's not obvious there's another row available.
        return new Dimension(cellSize.width * 3 + widthInsets, (int)(cellSize.height * 2.2) + heightInsets);
      }

      @Override
      public int locationToIndex(@NotNull Point location) {
        int index = super.locationToIndex(location);
        if (index != -1 && !getCellBounds(index, index).contains(location)) {
          return -1;
        }
        else {
          return index;
        }
      }
    };

    myDestinationsGallery.setBackground(null);
    myDestinationsGallery.addMouseMotionListener(new MouseAdapter() {
      @Override
      public void mouseMoved(@NotNull MouseEvent event) {
        int index = myDestinationsGallery.locationToIndex(event.getPoint());
        if (index != -1) {
          myDestinationsGallery.setSelectedIndex(index);
          myDestinationsGallery.requestFocusInWindow();
        }
        else {
          myDestinationsGallery.clearSelection();
        }
      }
    });

    JPanel selectionPanel = new JPanel(new VerticalLayout(5));
    // TODO: hook up search
    selectionPanel.add(new SearchTextField());

    JBScrollPane scrollPane = new JBScrollPane(myDestinationsGallery);
    scrollPane.setBorder(BorderFactory.createEmptyBorder());

    myMediaTracker = new MediaTracker(myDestinationsGallery);

    myDestinations.forEach(destination -> myMediaTracker.addImage(destination.getThumbnail(), 0));
    if (!myMediaTracker.checkAll()) {
      myLoadingPanel = new JBLoadingPanel(new BorderLayout(), mySurface);
      myLoadingPanel.add(scrollPane, BorderLayout.CENTER);
      myLoadingPanel.startLoading();

      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        try {
          myMediaTracker.waitForAll();
          ApplicationManager.getApplication().invokeLater(() -> {
            myDestinationsGallery.setImageProvider(Destination::getThumbnail);
            myLoadingPanel.stopLoading();
          });
        }
        catch (Exception e) {
          myLoadingPanel.setLoadingText("Failed to load thumbnails");
        }
      });

      selectionPanel.add(myLoadingPanel);
    }
    else {
      myDestinationsGallery.setImageProvider(Destination::getThumbnail);
      selectionPanel.add(scrollPane);
    }
    myDestinationsGallery.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(@NotNull MouseEvent event) {
        Destination element = myDestinationsGallery.getSelectedElement();
        if (element != null) {
          element.addToGraph();
          // explicitly update so the new SceneComponent is created
          mySurface.getSceneManager().update();
          NlComponent component = element.getComponent();
          mySurface.getSelectionModel().setSelection(ImmutableList.of(component));
          mySurface.scrollToCenter(ImmutableList.of(component));
          closePopup();
        }
      }
    });
    return selectionPanel;
  }

  @Override
  protected boolean updateActions() {
    return true;
  }
}
