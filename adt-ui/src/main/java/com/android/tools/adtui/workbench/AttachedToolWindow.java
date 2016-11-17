/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.adtui.workbench;

import com.android.annotations.VisibleForTesting;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.impl.AnchoredButton;
import com.intellij.openapi.wm.impl.InternalDecorator;
import com.intellij.openapi.wm.impl.StripeButtonUI;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.UIBundle;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.actionSystem.ActionToolbar.NAVBAR_MINIMUM_BUTTON_SIZE;

/**
 * AttachedToolWindow is a tool window that can be attached to a {@link WorkBench}.
 * This implementation is inspired by {@link com.intellij.designer.LightToolWindow}.
 *
 * @param <T> the type of data that is being edited by the associated {@link WorkBench}
 */
class AttachedToolWindow<T> implements Disposable {
  static final String TOOL_WINDOW_PROPERTY_PREFIX = "ATTACHED_TOOL_WINDOW.";
  static final String TOOL_WINDOW_TOOLBAR_PLACE = "TOOL_WINDOW_TOOLBAR";

  enum PropertyType {MINIMIZED, LEFT, SPLIT, AUTO_HIDE, FLOATING}

  private final String myWorkBenchName;
  private final ToolWindowDefinition<T> myDefinition;
  private final PropertiesComponent myPropertiesComponent;
  private final SideModel<T> myModel;
  private final JPanel myPanel;
  private final List<UpdatableActionButton> myActionButtons;
  private final AbstractButton myMinimizedButton;

  @Nullable
  private ToolContent<T> myContent;
  private boolean myAutoHideOpen;

  public AttachedToolWindow(@NotNull ToolWindowDefinition<T> definition,
                            @NotNull String workBenchName,
                            @NotNull SideModel<T> model) {
    this(definition, workBenchName, model, PropertiesComponent.getInstance(), DumbService.getInstance(model.getProject()));
  }

  @VisibleForTesting
  public AttachedToolWindow(@NotNull ToolWindowDefinition<T> definition,
                            @NotNull String workBenchName,
                            @NotNull SideModel<T> model,
                            @NotNull PropertiesComponent propertiesComponent,
                            @NotNull DumbService dumbService) {
    myWorkBenchName = workBenchName;
    myDefinition = definition;
    myPropertiesComponent = propertiesComponent;
    myModel = model;
    myPanel = new JPanel(new BorderLayout());
    myActionButtons = new ArrayList<>(4);
    myMinimizedButton = new MyMinimizedButton(definition.getTitle(), definition.getIcon());
    setDefaultProperty(PropertyType.LEFT, definition.getSide().isLeft());
    setDefaultProperty(PropertyType.SPLIT, definition.getSplit().isBottom());
    setDefaultProperty(PropertyType.AUTO_HIDE, definition.getAutoHide().isAutoHide());
    updateContent();
    dumbService.smartInvokeLater(this::updateActions);
  }

  @Override
  public void dispose() {
    if (myContent != null) {
      Disposer.dispose(myContent);
      myContent = null;
    }
  }

  @NotNull
  public String getToolName() {
    return myDefinition.getName();
  }

  @NotNull
  public JComponent getComponent() {
    return myPanel;
  }

  @NotNull
  public AbstractButton getMinimizedButton() {
    return myMinimizedButton;
  }

  @Nullable
  public T getContext() {
    return myModel.getContext();
  }

  public ToolWindowDefinition<T> getDefinition() {
    return myDefinition;
  }

  public boolean isMinimized() {
    return getProperty(PropertyType.MINIMIZED);
  }

  public void setMinimized(boolean value) {
    setProperty(PropertyType.MINIMIZED, value);
  }

  public boolean isLeft() {
    return getProperty(PropertyType.LEFT);
  }

  public void setLeft(boolean value) {
    setProperty(PropertyType.LEFT, value);
  }

  public boolean isSplit() {
    return getProperty(PropertyType.SPLIT);
  }

  public void setSplit(boolean value) {
    setProperty(PropertyType.SPLIT, value);
  }

  public boolean isAutoHide() {
    return getProperty(PropertyType.AUTO_HIDE);
  }

  public void setAutoHide(boolean value) {
    setProperty(PropertyType.AUTO_HIDE, value);
  }

  public boolean isFloating() {
    return getProperty(PropertyType.FLOATING);
  }

  public void setFloating(boolean value) {
    setProperty(PropertyType.FLOATING, value);
  }

  public boolean getProperty(@NotNull PropertyType property) {
    if (property == PropertyType.MINIMIZED && isAutoHide()) {
      return !myAutoHideOpen;
    }
    return myPropertiesComponent.getBoolean(getPropertyName(property));
  }

  public void setProperty(@NotNull PropertyType property, boolean value) {
    if (property == PropertyType.MINIMIZED && isAutoHide()) {
      myAutoHideOpen = !value;
    }
    else {
      myPropertiesComponent.setValue(getPropertyName(property), value);
    }
    if (myMinimizedButton != null) {
      myMinimizedButton.setSelected(!isMinimized());
    }
  }

  public void setDefaultProperty(@NotNull PropertyType property, boolean defaultValue) {
    if (!myPropertiesComponent.isValueSet(getPropertyName(property))) {
      setProperty(property, defaultValue);
    }
  }

  private String getPropertyName(@NotNull PropertyType property) {
    return TOOL_WINDOW_PROPERTY_PREFIX + myWorkBenchName + "." + myDefinition.getName() + "." + property.name();
  }

  public void setPropertyAndUpdate(@NotNull PropertyType property, boolean value) {
    setProperty(property, value);
    updateContent();
    updateActions();
    myModel.update(this, property);
    if (property == PropertyType.MINIMIZED && !value && myContent != null) {
      myContent.getFocusedComponent().requestFocus();
    }
  }

  public void setContext(T context) {
    if (myContent != null) {
      myContent.setToolContext(context);
    }
  }

  private void updateContent() {
    if (isFloating() && myContent != null) {
      myPanel.removeAll();
      myContent.setToolContext(null);
      Disposer.dispose(myContent);
      myContent = null;
    }
    else if (!isFloating() && myContent == null) {
      myContent = myDefinition.getFactory().create();
      assert myContent != null;
      myContent.setToolContext(myModel.getContext());
      myContent.registerCloseAutoHideWindow(this::closeAutoHideWindow);
      myPanel.add(createHeader(myContent.getAdditionalActions()), BorderLayout.NORTH);
      myPanel.add(myContent.getComponent(), BorderLayout.CENTER);
    }
  }

  private void closeAutoHideWindow() {
    if (!isFloating() && isAutoHide() && !isMinimized()) {
      setPropertyAndUpdate(PropertyType.MINIMIZED, true);
    }
  }

  @NotNull
  private JComponent createHeader(@NotNull List<AnAction> additionalActions) {
    JPanel header = new JPanel(new BorderLayout());
    header.add(createTitlePanel(myDefinition.getTitle()), BorderLayout.WEST);
    header.add(createActionPanel(additionalActions), BorderLayout.EAST);
    header.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
    return header;
  }

  @NotNull
  private static JComponent createTitlePanel(@NotNull String title) {
    JLabel titleLabel = new JBLabel(title);
    titleLabel.setBorder(IdeBorderFactory.createEmptyBorder(2, 5, 2, 10));
    titleLabel.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
    return titleLabel;
  }

  @NotNull
  private JComponent createActionPanel(@NotNull List<AnAction> additionalActions) {
    Dimension buttonSize = myDefinition.getButtonSize();
    int border = buttonSize.equals(NAVBAR_MINIMUM_BUTTON_SIZE) ? 2 : 0;
    JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 3, 0));
    actionPanel.setOpaque(false);
    actionPanel.setBorder(IdeBorderFactory.createEmptyBorder(border, 0, border, 0));
    if (!additionalActions.isEmpty()) {
      additionalActions.forEach(action -> actionPanel.add(createActionButton(action, buttonSize)));
      actionPanel.add(new JLabel(AllIcons.General.Divider));
    }
    actionPanel.add(createActionButton(new GearAction(), buttonSize));
    actionPanel.add(createActionButton(new HideAction(), buttonSize));
    return actionPanel;
  }

  @NotNull
  private ActionButton createActionButton(@NotNull AnAction action, @NotNull Dimension buttonSize) {
    UpdatableActionButton button = new UpdatableActionButton(action, buttonSize);
    myActionButtons.add(button);
    return button;
  }

  private void updateActions() {
    myActionButtons.forEach(UpdatableActionButton::update);
  }

  private void showGearPopup(@NotNull Component component, int x, int y) {
    DefaultActionGroup group = new DefaultActionGroup();
    addGearPopupActions(group);

    ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ToolWindowContentUi.POPUP_PLACE, group);
    popupMenu.getComponent().show(component, x, y);
  }

  private void addGearPopupActions(@NotNull DefaultActionGroup group) {
    if (myContent != null) {
      List<AnAction> myExtraGearActions = myContent.getGearActions();
      if (!myExtraGearActions.isEmpty()) {
        group.addAll(myExtraGearActions);
        group.addSeparator();
      }
    }
    DefaultActionGroup attachedSide = new DefaultActionGroup("Attached Side", true);
    attachedSide.add(new TogglePropertyTypeAction(PropertyType.LEFT, "Left"));
    attachedSide.add(new ToggleOppositePropertyTypeAction(PropertyType.LEFT, "Right"));
    attachedSide.add(new SwapAction());
    group.add(attachedSide);
    ActionManager manager = ActionManager.getInstance();
    group.add(new ToggleOppositePropertyTypeAction(PropertyType.AUTO_HIDE, manager.getAction(InternalDecorator.TOGGLE_DOCK_MODE_ACTION_ID)));
    group.add(new TogglePropertyTypeAction(PropertyType.FLOATING, manager.getAction(InternalDecorator.TOGGLE_FLOATING_MODE_ACTION_ID)));
    group.add(new TogglePropertyTypeAction(PropertyType.SPLIT, manager.getAction(InternalDecorator.TOGGLE_SIDE_MODE_ACTION_ID)));
  }

  private class MyMinimizedButton extends AnchoredButton {

    private MyMinimizedButton(@NotNull String title, @NotNull Icon icon) {
      super(title, icon);
      addActionListener(event -> {
        setSelected(false);
        setPropertyAndUpdate(PropertyType.MINIMIZED, !isMinimized());
      });
      setBorder(BorderFactory.createEmptyBorder(5, 5, 0, 5));
      setFocusable(false);
      setRolloverEnabled(true);
      setOpaque(true);
      setSelected(!isMinimized());
    }

    @Override
    public void updateUI() {
      setUI(StripeButtonUI.createUI(this));
      setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
    }

    /**
     * The {@link StripeButtonUI} is drawing the button slightly to the left for buttons on
     * the left side. Counteract this by translating the graphics 1 pixel to the right.
     */
    @Override
    public void paint(Graphics graphics) {
      if (!isLeft()) {
        super.paint(graphics);
        return;
      }
      Graphics graphics2 = graphics.create();
      try {
        graphics2.translate(JBUI.scale(1), 0);
        super.paint(graphics2);
      }
      finally {
        graphics2.dispose();
      }
    }

    @Override
    public int getMnemonic2() {
      return 0;
    }

    @Override
    public ToolWindowAnchor getAnchor() {
      return isLeft() ? ToolWindowAnchor.LEFT : ToolWindowAnchor.RIGHT;
    }
  }

  private static class UpdatableActionButton extends ActionButton {
    private UpdatableActionButton(@NotNull AnAction action, @NotNull Dimension buttonSize) {
      super(action, action.getTemplatePresentation().clone(), TOOL_WINDOW_TOOLBAR_PLACE, buttonSize);
    }

    public void update() {
      AnActionEvent event = new AnActionEvent(null, getDataContext(), myPlace, myPresentation, ActionManager.getInstance(), 0);
      ActionUtil.performDumbAwareUpdate(myAction, event, false);
    }
  }

  private class GearAction extends AnAction {
    public GearAction() {
      super("Gear");
      Presentation presentation = getTemplatePresentation();
      presentation.setIcon(AllIcons.General.Gear);
      presentation.setHoveredIcon(AllIcons.General.GearHover);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      int x = 0;
      int y = 0;
      InputEvent inputEvent = e.getInputEvent();
      if (inputEvent instanceof MouseEvent) {
        x = ((MouseEvent)inputEvent).getX();
        y = ((MouseEvent)inputEvent).getY();
      }

      showGearPopup(inputEvent.getComponent(), x, y);
    }
  }

  private class HideAction extends AnAction {
    public HideAction() {
      super(UIBundle.message("tool.window.hide.action.name"));
      update(getTemplatePresentation());
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
      update(event.getPresentation());
    }

    private void update(@NotNull Presentation presentation) {
      if (isLeft()) {
        presentation.setIcon(AllIcons.General.HideLeftPart);
        presentation.setHoveredIcon(AllIcons.General.HideLeftPartHover);
      }
      else {
        presentation.setIcon(AllIcons.General.HideRightPart);
        presentation.setHoveredIcon(AllIcons.General.HideRightPartHover);
      }
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
      setPropertyAndUpdate(PropertyType.MINIMIZED, true);
    }
  }

  private class TogglePropertyTypeAction extends ToggleAction {
    private final PropertyType myProperty;

    public TogglePropertyTypeAction(@NotNull PropertyType property, @NotNull String text) {
      super(text);
      myProperty = property;
    }

    public TogglePropertyTypeAction(@NotNull PropertyType property, @NotNull AnAction action) {
      myProperty = property;
      copyFrom(action);
    }

    @Override
    public boolean isSelected(AnActionEvent event) {
      return getProperty(myProperty);
    }

    @Override
    public void setSelected(@NotNull AnActionEvent event, boolean state) {
      setPropertyAndUpdate(myProperty, state);
    }
  }

  private class ToggleOppositePropertyTypeAction extends TogglePropertyTypeAction {
    public ToggleOppositePropertyTypeAction(@NotNull PropertyType property, @NotNull String text) {
      super(property, text);
    }

    public ToggleOppositePropertyTypeAction(@NotNull PropertyType property, @NotNull AnAction action) {
      super(property, action);
    }

    @Override
    public boolean isSelected(AnActionEvent event) {
      return !super.isSelected(event);
    }

    @Override
    public void setSelected(@NotNull AnActionEvent event, boolean state) {
      super.setSelected(event, !state);
    }
  }

  private class SwapAction extends AnAction {
    public SwapAction() {
      super("Swap");
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
      myModel.swap();
      updateActions();
    }
  }
}
