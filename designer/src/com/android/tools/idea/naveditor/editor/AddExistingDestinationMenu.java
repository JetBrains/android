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
import com.android.tools.adtui.common.AdtSecondaryPanel;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.ui.speedSearch.FilteringListModel;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * "Add" popup menu in the navigation editor.
 */
@VisibleForTesting
public class AddExistingDestinationMenu extends NavToolbarMenu {

  @VisibleForTesting
  public final List<Destination> myDestinations;
  private JPanel myMainPanel;
  @VisibleForTesting
  public JBList<Destination> myDestinationsList;

  private MediaTracker myMediaTracker;
  @VisibleForTesting
  JBLoadingPanel myLoadingPanel;

  private FilteringListModel<Destination> myListModel;
  @VisibleForTesting
  SearchTextField mySearchField = new SearchTextField();

  private static final JPanel RENDERER = new AdtSecondaryPanel(new BorderLayout());
  private static final JLabel THUMBNAIL_RENDERER = new JBLabel();
  private static final JLabel PRIMARY_TEXT_RENDERER = new JBLabel();
  private static final JLabel SECONDARY_TEXT_RENDERER = new JBLabel();

  static {
    RENDERER.add(THUMBNAIL_RENDERER, BorderLayout.WEST);
    JPanel leftPanel = new JPanel(new VerticalLayout(8));
    leftPanel.setBorder(BorderFactory.createEmptyBorder(12, 6, 0, 0));
    leftPanel.add(PRIMARY_TEXT_RENDERER, VerticalLayout.CENTER);
    leftPanel.add(SECONDARY_TEXT_RENDERER, VerticalLayout.CENTER);
    RENDERER.add(leftPanel, BorderLayout.CENTER);
  }

  AddExistingDestinationMenu(@NotNull NavDesignSurface surface, @NotNull List<Destination> destinations) {
    super(surface, "Add Destination", StudioIcons.NavEditor.Toolbar.ADD_EXISTING);
    myDestinations = destinations;
    SECONDARY_TEXT_RENDERER.setForeground(surface.getCurrentSceneView().getColorSet().getSubduedText());
  }

  @Override
  @VisibleForTesting
  @NotNull
  public JPanel getMainPanel() {
    if (myMainPanel == null) {
      myMainPanel = createSelectionPanel();
    }
    return myMainPanel;
  }

  @NotNull
  private JPanel createSelectionPanel() {
    myListModel = new FilteringListModel<>(new CollectionListModel<>(myDestinations));
    myListModel.setFilter(destination -> destination.getLabel().toLowerCase().contains(mySearchField.getText().toLowerCase()));
    // Don't want to show an exact number of rows, since then it's not obvious there's another row available.
    myDestinationsList = new JBList<Destination>(myListModel) {
      @Override
      @NotNull
      public Dimension getPreferredScrollableViewportSize() {
        return new Dimension(252, 300);
      }
    };

    myDestinationsList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
      THUMBNAIL_RENDERER.setIcon(new ImageIcon(value.getThumbnail().getScaledInstance(50, 64, Image.SCALE_SMOOTH)));
      PRIMARY_TEXT_RENDERER.setText(value.getLabel());
      SECONDARY_TEXT_RENDERER.setText(value.getTypeLabel());
      return RENDERER;
    });

    myDestinationsList.setBackground(null);
    myDestinationsList.addMouseMotionListener(new MouseAdapter() {
      @Override
      public void mouseMoved(@NotNull MouseEvent event) {
        int index = myDestinationsList.locationToIndex(event.getPoint());
        if (index != -1) {
          myDestinationsList.setSelectedIndex(index);
          myDestinationsList.requestFocusInWindow();
        }
        else {
          myDestinationsList.clearSelection();
        }
      }
    });

    JPanel selectionPanel = new AdtSecondaryPanel(new VerticalLayout(5));
    myDestinationsList.setBackground(selectionPanel.getBackground());
    selectionPanel.add(mySearchField);
    mySearchField.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        myListModel.refilter();
      }
    });

    JBScrollPane scrollPane = new JBScrollPane(myDestinationsList);
    scrollPane.setBorder(BorderFactory.createEmptyBorder());

    myMediaTracker = new MediaTracker(myDestinationsList);

    myDestinations.forEach(destination -> myMediaTracker.addImage(destination.getThumbnail(), 0));
    if (!myMediaTracker.checkAll()) {
      myLoadingPanel = new JBLoadingPanel(new BorderLayout(), getSurface());
      myLoadingPanel.add(scrollPane, BorderLayout.CENTER);
      myLoadingPanel.startLoading();

      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        try {
          myMediaTracker.waitForAll();
          ApplicationManager.getApplication().invokeLater(() -> {
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
      selectionPanel.add(scrollPane);
    }
    myDestinationsList.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(@NotNull MouseEvent event) {
        AnAction action = new AnAction() {
          @Override
          public void actionPerformed(AnActionEvent e) {
            Destination element = myDestinationsList.getSelectedValue();
            if (element != null) {
              element.addToGraph();
              // explicitly update so the new SceneComponent is created
              getSurface().getSceneManager().update();
              NlComponent component = element.getComponent();
              getSurface().getSelectionModel().setSelection(ImmutableList.of(component));
              getSurface().scrollToCenter(ImmutableList.of(component));
            }
          }
        };
        ActionManager.getInstance().tryToExecute(action, event, event.getComponent(), ActionPlaces.TOOLBAR, true);
      }
    });
    return selectionPanel;
  }
}
