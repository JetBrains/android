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
package com.android.tools.idea.naveditor.editor;

import com.android.annotations.VisibleForTesting;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.adtui.ASGallery;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.actions.DropDownAction;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.tools.idea.common.model.NlComponent;
import com.google.common.collect.ImmutableList;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.JBCardLayout;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.IconUtil;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;

import static com.android.SdkConstants.*;
import static com.android.dvlib.DeviceSchema.ATTR_NAME;
import static org.jetbrains.android.dom.navigation.NavigationSchema.DestinationType.*;
import static org.jetbrains.android.dom.navigation.NavigationSchema.TAG_INCLUDE;

/**
 * "Add" popup menu in the navigation editor.
 */
@VisibleForTesting
public class AddMenuWrapper extends DropDownAction {

  private static final JLabel RENDERER_COMPONENT = new JLabel();
  private static final String NEW_PANEL_NAME = "new";
  private static final String SELECTION_PANEL_NAME = "selection";
  private final NavDesignSurface mySurface;
  @VisibleForTesting
  public final List<NavActionManager.Destination> myDestinations;
  @VisibleForTesting
  public ComboBox<String> myKindPopup;
  @VisibleForTesting
  JTextField myLabelField;
  @VisibleForTesting
  JTextField myIdField;
  @VisibleForTesting
  JLabel myClassLabel;
  @VisibleForTesting
  ComboBox<String> myClassPopup;
  @VisibleForTesting
  JLabel mySourceLabel;
  @VisibleForTesting
  ComboBox<PsiFile> mySourcePopup;
  @VisibleForTesting
  JLabel myValidationLabel;
  private final NavigationSchema mySchema;
  private JPanel myMainPanel;
  @VisibleForTesting
  public JButton myNewDestinationButton;
  @VisibleForTesting
  public JButton myBackButton;
  @VisibleForTesting
  public JButton myCreateButton;
  @VisibleForTesting
  public ASGallery<NavActionManager.Destination> myDestinationsGallery;

  private MediaTracker myMediaTracker;
  @VisibleForTesting
  JBLoadingPanel myLoadingPanel;

  AddMenuWrapper(@NotNull NavDesignSurface surface, @NotNull List<NavActionManager.Destination> destinations) {
    super("", "Add Destination", IconUtil.getAddIcon());
    mySurface = surface;
    mySchema = mySurface.getSchema();
    myDestinations = destinations;
  }

  @VisibleForTesting
  void addElement(@NotNull NavActionManager.Destination destination, @NotNull NavDesignSurface surface) {
    String tagName = destination.getTag();
    String qName = destination.getQualifiedName();
    Consumer<NlComponent> extraActions = component -> {
      XmlFile layout = destination.getLayoutFile();
      if (layout != null) {
        // TODO: do this the right way
        String layoutId = "@" + ResourceType.LAYOUT.getName() + "/" + FileUtil.getNameWithoutExtension(layout.getName());
        component.setAttribute(TOOLS_URI, ATTR_LAYOUT, layoutId);
      }
    };
    addElement(surface, tagName, qName, qName, extraActions);
  }

  @VisibleForTesting
  void addElement(@NotNull NavDesignSurface surface,
                         @NotNull String tagName,
                         @NotNull String idBase,
                         @NotNull String name,
                         @Nullable Consumer<NlComponent> extraActions) {
    new WriteCommandAction(surface.getProject(), "Create " + name, surface.getModel().getFile()) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        NlComponent parent = surface.getCurrentNavigation();
        XmlTag tag = parent.getTag().createChildTag(tagName, null, null, true);
        NlComponent newComponent = surface.getModel().createComponent(tag, parent, null);
        surface.getSelectionModel().setSelection(ImmutableList.of(newComponent));
        newComponent.assignId(idBase);
        newComponent.setAttribute(ANDROID_URI, ATTR_NAME, name);
        if (extraActions != null) {
          extraActions.accept(newComponent);
        }
      }
    }.execute();
  }

  @Nullable
  @Override
  protected JPanel createCustomComponentPopup() {
    myMainPanel = new JPanel(new JBCardLayout());

    myMainPanel.add(createSelectionPanel(), SELECTION_PANEL_NAME);
    myMainPanel.add(createNewPanel(), NEW_PANEL_NAME);

    return myMainPanel;
  }

  @VisibleForTesting
  @NotNull
  public JPanel getMainPanel() {
    return myMainPanel;
  }

  @NotNull
  private JPanel createNewPanel() {
    JPanel newPanel = new JPanel(new VerticalLayout(10));
    JLabel destinationLabel = new JLabel("New Destination");
    newPanel.add(destinationLabel);
    newPanel.add(new JSeparator());
    JPanel selectionGrid = createAddControls();
    newPanel.add(selectionGrid);

    myValidationLabel = new JLabel("", AllIcons.General.Error, SwingConstants.LEFT);
    myValidationLabel.setVisible(false);
    newPanel.add(myValidationLabel, VerticalLayout.BOTTOM);

    JPanel buttons = new JPanel(new HorizontalLayout(2, SwingConstants.CENTER));
    myBackButton = new JButton("Back");
    myBackButton.addActionListener(
      event -> ((JBCardLayout)myMainPanel.getLayout()).swipe(myMainPanel, SELECTION_PANEL_NAME, JBCardLayout.SwipeDirection.BACKWARD));
    buttons.add(myBackButton);

    myCreateButton = new JButton("Create");
    myCreateButton.addActionListener(event -> {
      if (validate()) {
        createDestination();
      }
    });
    buttons.add(myCreateButton);
    JPanel bottomRow = new JPanel();
    bottomRow.add(buttons);
    newPanel.add(bottomRow, VerticalLayout.BOTTOM);
    newPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    return newPanel;
  }

  @VisibleForTesting
  void createDestination() {
    if (TAG_INCLUDE.equals(myKindPopup.getSelectedItem())) {
      String filename;
      if (mySourcePopup.getSelectedItem() == null) {
        // TODO: implement "new graph"
        filename = "dummy.xml";
      }
      else {
        filename = ((PsiFile)mySourcePopup.getSelectedItem()).getName();
      }
      addElement(mySurface, TAG_INCLUDE, myIdField.getText(), myLabelField.getText(),
                 component -> component.setAttribute(AUTO_URI, "graph", filename));
      return;
    }
    NavigationSchema.DestinationType type = mySchema.getDestinationType((String)myKindPopup.getSelectedItem());
    if (type == ACTIVITY || type == FRAGMENT) {
      String className = (String)myClassPopup.getSelectedItem();
      NavActionManager.Destination dest =
        new NavActionManager.Destination(null, className.substring(className.lastIndexOf('.') + 1), className,
                                         (String)myKindPopup.getSelectedItem(), null);
      addElement(dest, mySurface);
    }
    else if (type == NAVIGATION) {
      addElement(mySurface, (String)myKindPopup.getSelectedItem(), myIdField.getText(), myLabelField.getText(), null);
    }
    closePopup();
  }

  @VisibleForTesting
  boolean validate() {
    String error = null;
    String kind = (String)myKindPopup.getSelectedItem();
    NavigationSchema.DestinationType type = mySchema.getDestinationType(kind);
    if (myLabelField.getText().isEmpty()) {
      error = "Label must be set";
    }
    else if (myIdField.getText().isEmpty()) {
      error = "ID must be set";
    }
    else if (myClassPopup.getSelectedItem() == null && (type == ACTIVITY || type == FRAGMENT)) {
      // TODO: validate class?
      error = "Class must be set";
    }
    if (error != null) {
      myValidationLabel.setText(error);
      myValidationLabel.setVisible(true);
      return false;
    }
    else {
      myValidationLabel.setVisible(false);
      return true;
    }
  }

  @NotNull
  private JPanel createAddControls() {
    TabularLayout layout = new TabularLayout("Fit,20px,*");
    layout.setVGap(3);
    JPanel selectionGrid = new JPanel(layout);
    selectionGrid.add(new JLabel("Kind"), new TabularLayout.Constraint(0, 0));

    selectionGrid.add(new JLabel("Label"), new TabularLayout.Constraint(1, 0));
    // TODO: uniqification
    myLabelField = new JTextField("Dest 0", 40);
    selectionGrid.add(myLabelField, new TabularLayout.Constraint(1, 2));
    selectionGrid.add(new JLabel("ID"), new TabularLayout.Constraint(2, 0));
    // TODO: uniqification
    myIdField = new JTextField("dest_0", 40);
    selectionGrid.add(myIdField, new TabularLayout.Constraint(2, 2));

    myClassLabel = new JLabel("Class");
    selectionGrid.add(myClassLabel, new TabularLayout.Constraint(3, 0));
    createClassPopup();
    selectionGrid.add(myClassPopup, new TabularLayout.Constraint(3, 2));

    mySourceLabel = new JLabel("Source");
    selectionGrid.add(mySourceLabel, new TabularLayout.Constraint(3, 0));
    createSourcePopup();

    selectionGrid.add(mySourcePopup, new TabularLayout.Constraint(3, 2));

    createKindPopup();
    selectionGrid.add(myKindPopup, new TabularLayout.Constraint(0, 2));

    myKindPopup.setSelectedItem(mySchema.getTag(FRAGMENT));
    return selectionGrid;
  }

  private void createClassPopup() {
    // TODO: not clear what should be in here yet...
    myClassPopup = new ComboBox<>();
    myClassPopup.setEditable(true);
  }

  private void createSourcePopup() {
    // TODO: add tests for this when the nav type is created
    mySourcePopup = new ComboBox<>();
    mySourcePopup.addItem(null);
    ResourceManager resourceManager = LocalResourceManager.getInstance(mySurface.getModel().getModule());
    for (PsiFile navPsi : resourceManager.findResourceFiles(ResourceFolderType.NAVIGATION)) {
      if (mySurface.getModel().getFile().equals(navPsi)) {
        continue;
      }
      mySourcePopup.addItem(navPsi);
    }
    mySourcePopup.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
      if (value == null) {
        RENDERER_COMPONENT.setText("New...");
      }
      else {
        RENDERER_COMPONENT.setText(value.getName());
      }
      return RENDERER_COMPONENT;
    });
  }

  private void createKindPopup() {
    myKindPopup = new ComboBox<>();

    myKindPopup.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
      // TODO: print different text for different (custom) navigators?
      NavigationSchema.DestinationType type = mySchema.getDestinationType(value);
      String text;
      if (type == NAVIGATION) {
        text = "Nested Graph";
      }
      else if (type == FRAGMENT) {
        text = "Fragment (default)";
      }
      else if (type == ACTIVITY) {
        text = "Activity";
      }
      else if (TAG_INCLUDE.equals(value)) {
        text = "Include Graph";
      }
      else {
        text = value;
      }
      RENDERER_COMPONENT.setText(text);
      return RENDERER_COMPONENT;
    });
    for (NavigationSchema.DestinationType type : values()) {
      for (String tag : mySchema.getDestinationClassByTagMap(type).keySet()) {
        if (tag != null) {
          myKindPopup.addItem(tag);
        }
      }
    }
    myKindPopup.addItem(TAG_INCLUDE);

    myKindPopup.addItemListener(itemEvent -> {
      myClassLabel.setVisible(false);
      myClassPopup.setVisible(false);
      mySourceLabel.setVisible(false);
      mySourcePopup.setVisible(false);
      String item = (String)itemEvent.getItem();
      NavigationSchema.DestinationType type = mySchema.getDestinationType(item);
      if (type == ACTIVITY || type == FRAGMENT) {
        myClassLabel.setVisible(true);
        myClassPopup.setVisible(true);
      }
      else if (TAG_INCLUDE.equals(item)) {
        mySourceLabel.setVisible(true);
        mySourcePopup.setVisible(true);
      }
    });
  }

  @NotNull
  private JPanel createSelectionPanel() {
    CollectionListModel<NavActionManager.Destination> listModel = new CollectionListModel<>(myDestinations);
    // Don't want to show an exact number of rows, since then it's not obvious there's another row available.
    myDestinationsGallery = new ASGallery<NavActionManager.Destination>(
      listModel, d->null, NavActionManager.Destination::getName, new Dimension(96, 96), null) {
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
            myDestinationsGallery.setImageProvider(NavActionManager.Destination::getThumbnail);
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
      myDestinationsGallery.setImageProvider(NavActionManager.Destination::getThumbnail);
      selectionPanel.add(scrollPane);
    }
    myNewDestinationButton = new JButton("New Destination");
    JPanel createButtonPanel = new JPanel();
    createButtonPanel.add(myNewDestinationButton);
    selectionPanel.add(createButtonPanel);
    myNewDestinationButton.addActionListener(event -> {
      ((JBCardLayout)myMainPanel.getLayout()).swipe(myMainPanel, "new", JBCardLayout.SwipeDirection.FORWARD);
    });
    myDestinationsGallery.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(@NotNull MouseEvent event) {
        if (myDestinationsGallery.getSelectedIndex() != -1) {
          addElement(myDestinationsGallery.getSelectedElement(), mySurface);
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
