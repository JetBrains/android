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
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.actions.DropDownAction;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.utils.Pair;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.psi.PsiClass;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.ui.components.panels.VerticalLayout;
import icons.StudioIcons;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jetbrains.android.dom.navigation.NavigationSchema.DestinationType.FRAGMENT;
import static org.jetbrains.android.dom.navigation.NavigationSchema.TAG_INCLUDE;

/**
 * "Add" popup menu in the navigation editor.
 */
@VisibleForTesting
public class CreateDestinationMenu extends DropDownAction {

  private static final JLabel RENDERER_COMPONENT = new JLabel();
  private final NavDesignSurface mySurface;
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
  JTextField mySourceField;
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
  private String myDefaultId;
  private String myDefaultLabel;

  CreateDestinationMenu(@NotNull NavDesignSurface surface) {
    super("", "Add Destination", StudioIcons.NavEditor.Toolbar.ADD_DESTINATION);
    mySurface = surface;
    mySchema = mySurface.getSchema();
  }

  @Nullable
  @Override
  protected JPanel createCustomComponentPopup() {
    if (myMainPanel == null) {
      myMainPanel = createNewPanel();
    }
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
    PsiClass psiClass = selected.getSecond();
    NlComponent parent = mySurface.getCurrentNavigation();
    // Should only be true for "include"
    if (psiClass == null) {
      new Destination.IncludeDestination(mySourceField.getText(), parent).addToGraph();
      // TODO: actually create the file
    }
    else {
      NavigationSchema.DestinationType type = mySchema.getTypeForNavigatorClass(psiClass);
      //noinspection ConstantConditions  At this point we know that there's a tag associated with this type
      new Destination.RegularDestination(parent, mySchema.getDefaultTag(type), myLabelField.getText(), null, null, myIdField.getText())
        .addToGraph();
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
    mySourceField = new JTextField("", 40);
    selectionGrid.add(mySourceField, new TabularLayout.Constraint(3, 2));

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
      /*
      TODO: enable creating new graphs with the "include" option.
      if (classes.isEmpty()) {
        // Dummy value, like <include>. Add it with a null class.
        myKindPopup.addItem(Pair.of(label, null));
      }
      */
    }

    myKindPopup.addItemListener(itemEvent -> {
      //noinspection unchecked
      if (NavigationSchema.INCLUDE_GRAPH_LABEL.equals(((Pair<String, PsiClass>)itemEvent.getItem()).getFirst())) {
        myIdField.setVisible(false);
        myLabelField.setVisible(false);
        myIdLabel.setVisible(false);
        myLabelLabel.setVisible(false);
        mySourceLabel.setVisible(true);
        mySourceField.setVisible(true);
      }
      else {
        mySourceLabel.setVisible(false);
        mySourceField.setVisible(false);
        myLabelLabel.setVisible(true);
        myLabelField.setVisible(true);
        myIdLabel.setVisible(true);
        myIdField.setVisible(true);
      }
      updateDefaultIdAndLabel();
    });

    myKindPopup.setSelectedItem(defaultSelection);
  }

  @Override
  protected boolean updateActions() {
    return true;
  }
}
