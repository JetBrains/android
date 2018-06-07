/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.newEditor.SettingsDialog;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.treeStructure.CachingSimpleNode;
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure;
import org.fest.swing.cell.JTreeCellReader;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.exception.ComponentLookupException;
import org.fest.swing.exception.LocationUnavailableException;
import org.fest.swing.fixture.JCheckBoxFixture;
import org.fest.swing.fixture.JTreeFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.util.Collection;
import java.util.List;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickButtonWhenEnabled;
import static com.google.common.truth.Truth.assertThat;
import static org.fest.reflect.core.Reflection.field;

public class IdeSettingsDialogFixture extends IdeaDialogFixture<SettingsDialog> {
  @NotNull
  public static IdeSettingsDialogFixture find(@NotNull Robot robot) {
    return new IdeSettingsDialogFixture(robot, find(robot, SettingsDialog.class, new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog component) {
        // Title is different for mac and if it's global/project settings
        return component.getTitle().matches("(Default )?(Preferences|Settings)");
      }
    }));
  }

  private IdeSettingsDialogFixture(@NotNull Robot robot, @NotNull DialogAndWrapper<SettingsDialog> dialogAndWrapper) {
    super(robot, dialogAndWrapper);
  }

  @NotNull
  public List<String> getProjectSettingsNames() {
    List<String> names = Lists.newArrayList();
    JPanel optionsEditor = field("myEditor").ofType(JPanel.class).in(getDialogWrapper()).get();

    List<JComponent> trees = findComponentsOfType(optionsEditor, "com.intellij.openapi.options.newEditor.SettingsTreeView");
    assertThat(trees).hasSize(1);
    JComponent tree = trees.get(0);

    CachingSimpleNode root = field("myRoot").ofType(CachingSimpleNode.class).in(tree).get();

    ConfigurableGroup[] groups = field("myGroups").ofType(ConfigurableGroup[].class).in(root).get();
    for (ConfigurableGroup current : groups) {
      Configurable[] configurables = current.getConfigurables();
      for (Configurable configurable : configurables) {
        names.add(configurable.getDisplayName());
      }
    }
    return names;
  }

  @NotNull
  public IdeSettingsDialogFixture selectSdkPage() {
    JPanel optionsEditor = field("myEditor").ofType(JPanel.class).in(getDialogWrapper()).get();
    List<JComponent> trees = findComponentsOfType(optionsEditor, "com.intellij.openapi.options.newEditor.SettingsTreeView");
    JComponent tree = Iterables.getOnlyElement(trees);

    JTree jTree = field("myTree").ofType(JTree.class).in(tree).get();
    JTreeFixture jTreeFixture = new JTreeFixture(robot(), jTree);
    jTreeFixture.replaceCellReader(TREE_NODE_CELL_READER);
    // It takes a few seconds to load the whole tree.
    Wait.seconds(5).expecting("The desired path is loaded").until(() -> {
      try {
        jTreeFixture.selectPath("Appearance & Behavior/System Settings/Android SDK");
        return true;
      } catch (LocationUnavailableException e) {
        return false;
      }
    });
    return this;
  }

  public void clickOK() {
    findAndClickButtonWhenEnabled(this, "OK");
  }

  public void selectShowPackageDetails() {
    Collection<JCheckBox> allFound = robot().finder().findAll(
      target(),
      Matchers.byText(JCheckBox.class, "Show Package Details"));

    for (JCheckBox jCheckBox : allFound) {
      if (jCheckBox.isShowing()) {
        new JCheckBoxFixture(robot(), jCheckBox).select();
        return;
      }
    }

    throw new ComponentLookupException("Show Package Details checkbox is not found", allFound);
  }

  private static final JTreeCellReader TREE_NODE_CELL_READER = (jTree, modelValue) -> {
    Object userObject = ((DefaultMutableTreeNode)modelValue).getUserObject();
    if (userObject instanceof String) { // It is a String ("loading...") if the cell is not loaded yet.
      return (String)userObject;
    } else {
      return field("myDisplayName").ofType(String.class)
        .in(((FilteringTreeStructure.FilteringNode)userObject).getDelegate()).get();
    }
  };

  @NotNull
  private static List<JComponent> findComponentsOfType(@NotNull JComponent parent, @NotNull String typeName) {
    List<JComponent> result = Lists.newArrayList();
    findComponentsOfType(typeName, result, parent);
    return result;
  }

  private static void findComponentsOfType(@NotNull String typeName, @NotNull List<JComponent> result, @Nullable JComponent parent) {
    if (parent == null) {
      return;
    }
    if (parent.getClass().getName().equals(typeName)) {
      result.add(parent);
    }
    for (Component c : parent.getComponents()) {
      if (c instanceof JComponent) {
        findComponentsOfType(typeName, result, (JComponent)c);
      }
    }
  }
}