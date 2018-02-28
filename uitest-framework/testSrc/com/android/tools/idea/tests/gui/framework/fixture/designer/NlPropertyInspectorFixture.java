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
package com.android.tools.idea.tests.gui.framework.fixture.designer;

import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.layout.ConstraintLayoutViewInspectorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.layout.NlPropertyFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.layout.NlPropertyTableFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.idea.uibuilder.handlers.constraint.WidgetConstraintPanel;
import com.android.tools.idea.uibuilder.property.NlPropertiesPanel;
import com.android.tools.idea.uibuilder.property.ToggleXmlPropertyEditor;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.components.JBLabel;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.exception.WaitTimedOutError;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.FocusManager;
import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilFound;
import static com.google.common.truth.Truth.assertThat;

/**
 * Fixture wrapping the component inspector
 */
public class NlPropertyInspectorFixture extends ComponentFixture<NlPropertyInspectorFixture, Component> {
  private final NlPropertiesPanel myPanel;
  private final ToggleXmlPropertyEditor myXmlPropertyToggleAction;

  public NlPropertyInspectorFixture(@NotNull Robot robot, @NotNull NlPropertiesPanel panel) {
    super(NlPropertyInspectorFixture.class, robot, panel);
    myPanel = panel;
    myXmlPropertyToggleAction = new ToggleXmlPropertyEditor(myPanel.getPropertiesManager());
  }

  public static NlPropertiesPanel create(@NotNull Robot robot) {
    return waitUntilFound(robot, null, Matchers.byType(NlPropertiesPanel.class));
  }

  @NotNull
  public NlPropertyInspectorFixture openAsInspector() {
    if (myPanel.isAllPropertiesPanelVisible()) {
      myPanel.setAllPropertiesPanelVisible(false);
    }
    return this;
  }

  @NotNull
  public NlPropertyTableFixture openAsTable() {
    if (!myPanel.isAllPropertiesPanelVisible()) {
      myPanel.setAllPropertiesPanelVisible(true);
    }
    if (isSliceEditorActive()) {
      toggleShowSliceEditor();
    }
    return NlPropertyTableFixture.create(robot());
  }

  @NotNull
  @SuppressWarnings("UnusedReturnValue")
  public NlPropertyTableFixture openAsSliceEditor() {
    if (!myPanel.isAllPropertiesPanelVisible()) {
      myPanel.setAllPropertiesPanelVisible(true);
    }
    if (!isSliceEditorActive()) {
      toggleShowSliceEditor();
    }
    return NlPropertyTableFixture.create(robot());
  }

  @NotNull
  public ConstraintLayoutViewInspectorFixture getConstraintLayoutViewInspector() {
    Robot robot = robot();
    Container target = waitUntilFound(robot, myPanel, Matchers.byType(WidgetConstraintPanel.class));

    return new ConstraintLayoutViewInspectorFixture(robot, target);
  }

  @Nullable
  public NlPropertyFixture findProperty(@NotNull String name) {
    Component component = findPropertyComponent(name, null);
    return component != null ? new NlPropertyFixture(robot(), (JPanel)component) : null;
  }

  /**
   * Sets the frame height such that we see only the requested number of properties
   * in the property inspector.
   *
   * @param frameFixture the frame to set the size of
   * @param visiblePropertyCount the wanted number of visible properties in the inspector
   * @param propertyName use this property to estimate the height of each property
   * @return this fixture
   */
  @NotNull
  public NlPropertyInspectorFixture adjustIdeFrameHeightFor(@NotNull IdeFrameFixture frameFixture,
                                                            int visiblePropertyCount,
                                                            @NotNull String propertyName) {
    Component component = findPropertyComponent(propertyName, null);
    assertThat(component).isNotNull();
    int height = component.getHeight();
    Container parent = SwingUtilities.getAncestorOfClass(JScrollPane.class, component);
    int adjustment = visiblePropertyCount * height - parent.getHeight();

    Dimension size = frameFixture.getIdeFrameSize();
    Dimension newSize = new Dimension(size.width, size.height + adjustment);
    frameFixture.setIdeFrameSize(newSize);
    return this;
  }

  @NotNull
  public NlPropertyInspectorFixture focusAndWaitForFocusGainInProperty(@NotNull String name, @Nullable Icon icon) {
    Component component = findFocusablePropertyComponent(name, icon);
    assertThat(component).isNotNull();
    driver().focusAndWaitForFocusGain(component);
    return this;
  }

  @NotNull
  public NlPropertyInspectorFixture waitForPanelLoading() {
    Wait.seconds(5).expecting("NlPropertiesPanel Loading").until(() -> !myPanel.getPropertiesManager().isLoading());
    return this;
  }

  @NotNull
  public NlPropertyInspectorFixture tab() {
    robot().pressAndReleaseKey(KeyEvent.VK_TAB);
    return this;
  }

  @NotNull
  public NlPropertyInspectorFixture tabBack() {
    robot().pressAndReleaseKey(KeyEvent.VK_TAB, InputEvent.SHIFT_MASK);
    return this;
  }

  @NotNull
  @SuppressWarnings("UnusedReturnValue")
  public NlPropertyInspectorFixture pressKeyInUnknownProperty(int keyCode, int... modifiers) {
    Component component = FocusManager.getCurrentManager().getFocusOwner();
    assertThat(component).isNotNull();
    assertThat(SwingUtilities.isDescendingFrom(component, myPanel)).isTrue();
    //noinspection unchecked
    driver().pressAndReleaseKey(component, keyCode, modifiers);
    IdeFocusManager.findInstance().doWhenFocusSettlesDown(() -> {
    });
    return this;
  }

  @NotNull
  public NlPropertyInspectorFixture assertPropertyShowing(@NotNull String name, @Nullable Icon icon) {
    assertThat(isPropertyShowing(name, icon)).named("Property is Visible to user: " + name).isTrue();
    return this;
  }

  @NotNull
  public NlPropertyInspectorFixture assertPropertyNotShowing(@NotNull String name, @Nullable Icon icon) {
    assertThat(isPropertyShowing(name, icon)).named("Property is Visible to user: " + name).isFalse();
    return this;
  }

  @NotNull
  public NlPropertyInspectorFixture assertFocusInProperty(@NotNull String name, @Nullable Icon icon) {
    Component propertyComponent = findFocusablePropertyComponent(name, icon);
    Component focusComponent = FocusManager.getCurrentManager().getFocusOwner();
    assertThat(propertyComponent != null).named("property: " + name + " found").isTrue();
    assertThat(SwingUtilities.isDescendingFrom(focusComponent, propertyComponent)).named("property: " + name + " has focus").isTrue();
    return this;
  }

  private boolean isPropertyShowing(@NotNull String name, @Nullable Icon icon) {
    Component component = findPropertyComponent(name, icon);
    if (component == null) {
      return false;
    }
    Rectangle rect = component.getBounds();
    Container parent = component.getParent();
    while (parent != null) {
      Rectangle bounds = parent.getBounds();
      if (rect.y > bounds.height || rect.y + rect.height < 0 ||
          rect.x > bounds.width || rect.x + rect.width < 0) {
        return false;
      }
      rect.x += bounds.x;
      rect.y += bounds.y;
      parent = parent.getParent();
    }
    return true;
  }

  @Nullable
  private Component findPropertyComponent(@NotNull String name, @Nullable Icon icon) {
    try {
      JBLabel label = waitUntilFound(robot(), myPanel,
                                     Matchers.byText(JBLabel.class, "<html>" + name + "</html>").and(Matchers.byIcon(JBLabel.class, icon)));

      Container parent = label.getParent();
      Component[] components = parent.getComponents();
      for (int i = 0; i < components.length; i++) {
        if (label == components[i]) {
          return components[i + 1];
        }
      }
      return null;
    }
    catch (WaitTimedOutError ex) {
      return null;
    }
  }

  @Nullable
  private Component findFocusablePropertyComponent(@NotNull String name, @Nullable Icon icon) {
    Component component = findPropertyComponent(name, icon);
    if (component == null) {
      return null;
    }
    if (!component.isFocusable() && component instanceof Container) {
      for (Component inner : ((Container)component).getComponents()) {
        if (inner.isFocusable()) {
          component = inner;
          break;
        }
      }
    }
    if (component instanceof EditorTextField) {
      // If this is a TextField editor the underlying editor component is expected to receive focus (eventually)
      component = ((EditorTextField)component).getEditor().getContentComponent();
    }
    return component;
  }

  private boolean isSliceEditorActive() {
    AnActionEvent event = AnActionEvent.createFromAnAction(myXmlPropertyToggleAction, null, "", DataContext.EMPTY_CONTEXT);
    myXmlPropertyToggleAction.update(event);
    // The slice editor is shown if the action is allowing the user to go back to the all proeprties table:
    return event.getPresentation().getText().contains("All attributes table");
  }

  private void toggleShowSliceEditor() {
    GuiActionRunner.execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        AnActionEvent event = AnActionEvent.createFromAnAction(myXmlPropertyToggleAction, null, "", DataContext.EMPTY_CONTEXT);
        myXmlPropertyToggleAction.actionPerformed(event);
      }
    });
  }
}
