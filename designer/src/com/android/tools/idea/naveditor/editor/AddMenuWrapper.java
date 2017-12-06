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
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.utils.Pair;
import com.google.common.base.Function;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiClass;
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
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
  public ComboBox<Pair<String, PsiClass>> myKindPopup;
  @VisibleForTesting
  JTextField myLabelField;
  @VisibleForTesting
  JLabel myLabelLabel;
  JTextField myIdField;
  @VisibleForTesting
  JLabel myIdLabel;
  @VisibleForTesting
  JLabel mySourceLabel;
  @VisibleForTesting
  ComboBox<String> mySourcePopup;
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
  private String myDefaultId;
  private String myDefaultLabel;

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
  void addElement(@NotNull NavActionManager.Destination destination,
                  @NotNull NavDesignSurface surface,
                  @Nullable String id,
                  @Nullable String label) {
    String tagName = destination.getTag();
    Consumer<NlComponent> extraActions = component -> {
      XmlFile layout = destination.getLayoutFile();
      if (layout != null) {
        // TODO: do this the right way
        String layoutId = "@" + ResourceType.LAYOUT.getName() + "/" + FileUtil.getNameWithoutExtension(layout.getName());
        component.setAttribute(TOOLS_URI, ATTR_LAYOUT, layoutId);
      }
    };
    addElement(surface, tagName, id != null ? id : destination.getClassName(), destination.getQualifiedName(), label, extraActions);
  }

  @VisibleForTesting
  void addElement(@NotNull NavDesignSurface surface,
                  @NotNull String tagName,
                  @NotNull String idBase,
                  @Nullable String className,
                  @Nullable String label,
                  @Nullable Consumer<NlComponent> extraActions) {
    new WriteCommandAction(surface.getProject(), "Create " + className, surface.getModel().getFile()) {
      @Override
      protected void run(@NotNull Result result) {
        NlComponent parent = surface.getCurrentNavigation();
        XmlTag tag = parent.getTag().createChildTag(tagName, null, null, true);
        NlComponent newComponent = surface.getModel().createComponent(tag, parent, null);
        surface.getSelectionModel().setSelection(ImmutableList.of(newComponent));
        newComponent.assignId(idBase);
        if (className != null && !className.isEmpty()) {
          newComponent.setAndroidAttribute(ATTR_NAME, className);
        }
        if (label != null && !label.isEmpty()) {
          newComponent.setAndroidAttribute(ATTR_LABEL, label);
        }
        if (extraActions != null) {
          extraActions.accept(newComponent);
        }
      }
    }.execute();
  }

  @Nullable
  @Override
  protected JPanel createCustomComponentPopup() {
    if (myMainPanel == null) {
      myMainPanel = new JPanel(new JBCardLayout());

      myMainPanel.add(createSelectionPanel(), SELECTION_PANEL_NAME);
      myMainPanel.add(createNewPanel(), NEW_PANEL_NAME);
    }
    ((JBCardLayout)myMainPanel.getLayout()).show(myMainPanel, SELECTION_PANEL_NAME);
    myDefaultLabel = null;
    myDefaultId = null;
    updateDefaultIdAndLabel();
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
    @SuppressWarnings("unchecked") Pair<String, PsiClass> selected = (Pair<String, PsiClass>)myKindPopup.getSelectedItem();

    if (selected == null) {
      return;
    }
    // Should only be true for "include"
    PsiClass psiClass = selected.getSecond();
    if (psiClass == null) {
      String filename;
      if (mySourcePopup.getSelectedItem() == null) {
        // TODO: implement "new graph"
        filename = "dummy.xml";
      }
      else {
        filename = (String)mySourcePopup.getSelectedItem();
      }
      String shortFileName = FileUtil.getNameWithoutExtension(new File(filename).getName());
      String resource = "@" + ResourceType.NAVIGATION.getName() + "/" + shortFileName;
      addElement(mySurface, TAG_INCLUDE, shortFileName, null, myLabelField.getText(),
                 component -> component.setAttribute(AUTO_URI, "graph", resource));
      closePopup();
      return;
    }
    NavigationSchema.DestinationType type = mySchema.getTypeForNavigatorClass(psiClass);
    if (type == ACTIVITY || type == FRAGMENT) {
      //noinspection ConstantConditions  At this point we know that there's a tag associated with this type
      NavActionManager.Destination dest =
        new NavActionManager.Destination(null, "", "",
                                         mySchema.getDefaultTag(type), null);
      addElement(dest, mySurface, myIdField.getText(), myLabelField.getText());
    }
    else if (type == NAVIGATION) {
      addElement(mySurface, mySchema.getTag(psiClass), myIdField.getText(), null, myLabelField.getText(), null);
    }
    closePopup();
  }

  @VisibleForTesting
  boolean validate() {
    String error = null;
    if (myLabelField.getText().isEmpty()) {
      error = "Label must be set";
    }
    else if (myIdField.getText().isEmpty()) {
      error = "ID must be set";
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

    myLabelLabel = new JLabel("Label");
    selectionGrid.add(myLabelLabel, new TabularLayout.Constraint(1, 0));
    myIdField = new JTextField("", 40);
    selectionGrid.add(myIdField, new TabularLayout.Constraint(2, 2));
    myLabelField = new JTextField("", 40);
    selectionGrid.add(myLabelField, new TabularLayout.Constraint(1, 2));
    myIdLabel = new JLabel("ID");
    selectionGrid.add(myIdLabel, new TabularLayout.Constraint(2, 0));

    mySourceLabel = new JLabel("Source");
    selectionGrid.add(mySourceLabel, new TabularLayout.Constraint(3, 0));
    createSourcePopup();

    selectionGrid.add(mySourcePopup, new TabularLayout.Constraint(3, 2));

    createKindPopup();
    selectionGrid.add(myKindPopup, new TabularLayout.Constraint(0, 2));

    myKindPopup.setSelectedItem(mySchema.getDefaultTag(FRAGMENT));
    updateDefaultIdAndLabel();
    return selectionGrid;
  }

  private void updateDefaultIdAndLabel() {
    @SuppressWarnings("unchecked") Pair<String, PsiClass> item = (Pair<String, PsiClass>)myKindPopup.getSelectedItem();
    if (item == null) {
      return;
    }
    PsiClass navigatorClass = item.getSecond();
    String tag = navigatorClass == null ? TAG_INCLUDE : mySchema.getTag(navigatorClass);
    // If we haven't changed from the default, update the value
    if (myDefaultId == null || myIdField.getText().equals(myDefaultId)) {
      NlModel model = mySurface.getModel();
      myDefaultId = NlComponent.generateId(tag, model.getIds(), ResourceFolderType.NAVIGATION,
                                           model.getModule());
      myIdField.setText(myDefaultId);
    }
    if (myDefaultLabel == null || myLabelField.getText().equals(myDefaultLabel)) {
      Matcher m = Pattern.compile("\\d*$").matcher(myDefaultId);
      if (m.find()) {
        String n = m.group();
        myDefaultLabel = mySchema.getTagLabel(tag) + (n.isEmpty() ? "" : " " + n);
        myLabelField.setText(myDefaultLabel);
      }
    }
  }

  private void createSourcePopup() {
    mySourcePopup = new ComboBox<>();
    mySourcePopup.addItem(null);
    ResourceManager resourceManager = LocalResourceManager.getInstance(mySurface.getModel().getModule());
    //noinspection ConstantConditions  We can't get in here without a facet, which is the only reason resourceManager would be null.
    for (PsiFile navPsi : resourceManager.findResourceFiles(ResourceFolderType.NAVIGATION)) {
      if (mySurface.getModel().getFile().equals(navPsi)) {
        continue;
      }
      mySourcePopup.addItem(navPsi.getName());
    }
    mySourcePopup.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
      if (value == null) {
        RENDERER_COMPONENT.setText("New...");
      }
      else {
        RENDERER_COMPONENT.setText(value);
      }
      return RENDERER_COMPONENT;
    });
  }

  private void createKindPopup() {
    myKindPopup = new ComboBox<>();

    myKindPopup.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
      String text = value.getFirst();
      RENDERER_COMPONENT.setText(text);
      return RENDERER_COMPONENT;
    });
    Multimap<String, PsiClass> tagToClass = HashMultimap.create();
    mySchema.getNavigatorClassTagMap().forEach((psiClass, tag) -> tagToClass.put(tag, psiClass));
    Pair<String, PsiClass> defaultSelection = null;
    String defaultTag = mySchema.getDefaultTag(FRAGMENT);
    // Iterate over tagTypeMap since it includes <include>
    for (String tag : mySchema.getTagTypeMap().keySet()) {
      Collection<PsiClass> classes = tagToClass.get(tag);
      String label = mySchema.getTagLabel(tag);
      for (PsiClass psiClass : classes) {
        if (classes.size() > 1) {
          label += " " + psiClass.getName();
        }
        Pair<String, PsiClass> item = Pair.of(label, psiClass);
        if (tag.equals(defaultTag)) {
          defaultSelection = item;
        }
        myKindPopup.addItem(item);
      }
      if (classes.isEmpty()) {
        // Dummy value, like <include>. Add it with a null class.
        myKindPopup.addItem(Pair.of(label, null));
      }
    }

    myKindPopup.addItemListener(itemEvent -> {
      //noinspection unchecked
      if (NavigationSchema.INCLUDE_GRAPH_LABEL.equals(((Pair<String, PsiClass>)itemEvent.getItem()).getFirst())) {
        myIdField.setVisible(false);
        myLabelField.setVisible(false);
        myIdLabel.setVisible(false);
        myLabelLabel.setVisible(false);
        mySourceLabel.setVisible(true);
        mySourcePopup.setVisible(true);
      }
      else {
        mySourceLabel.setVisible(false);
        mySourcePopup.setVisible(false);
        myLabelLabel.setVisible(true);
        myLabelField.setVisible(true);
        myIdLabel.setVisible(true);
        myIdField.setVisible(true);
      }
      updateDefaultIdAndLabel();
    });

    myKindPopup.setSelectedItem(defaultSelection);
  }

  @NotNull
  private JPanel createSelectionPanel() {
    CollectionListModel<NavActionManager.Destination> listModel = new CollectionListModel<>(myDestinations);
    Function<NavActionManager.Destination, String> getUiName = destination -> {
      if (destination.getLayoutFile() != null) {
        return FileUtil.getNameWithoutExtension(destination.getLayoutFile().getName());
      }
      return destination.getClassName();
    };
    // Don't want to show an exact number of rows, since then it's not obvious there's another row available.
    myDestinationsGallery = new ASGallery<NavActionManager.Destination>(
      listModel, d -> null, getUiName, new Dimension(96, 96), null) {
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
    myNewDestinationButton
      .addActionListener(event -> ((JBCardLayout)myMainPanel.getLayout()).swipe(myMainPanel, "new", JBCardLayout.SwipeDirection.FORWARD));
    myDestinationsGallery.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(@NotNull MouseEvent event) {
        NavActionManager.Destination element = myDestinationsGallery.getSelectedElement();
        if (element != null) {
          addElement(element, mySurface, null, null);
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
