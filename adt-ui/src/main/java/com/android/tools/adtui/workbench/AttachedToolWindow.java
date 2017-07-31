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
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.impl.AnchoredButton;
import com.intellij.openapi.wm.impl.InternalDecorator;
import com.intellij.openapi.wm.impl.StripeButtonUI;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBImageIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.MouseInputAdapter;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
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
  static final String LABEL_HEADER = "LABEL";
  static final String SEARCH_HEADER = "SEARCH";

  enum PropertyType {AUTO_HIDE, MINIMIZED, LEFT, SPLIT, DETACHED, FLOATING}

  private final String myWorkBenchName;
  private final ToolWindowDefinition<T> myDefinition;
  private final PropertiesComponent myPropertiesComponent;
  private final SideModel<T> myModel;
  private final JPanel myPanel;
  private final List<UpdatableActionButton> myActionButtons;
  private final AbstractButton myMinimizedButton;
  private final MySearchField mySearchField;
  private final ActionButton mySearchActionButton;
  private ButtonDragListener<T> myDragListener;

  @Nullable
  private ToolContent<T> myContent;
  private boolean myAutoHideOpen;
  private int myToolOrder;

  public AttachedToolWindow(@NotNull ToolWindowDefinition<T> definition,
                            @NotNull ButtonDragListener<T> dragListener,
                            @NotNull String workBenchName,
                            @NotNull SideModel<T> model) {
    myWorkBenchName = workBenchName;
    myDefinition = definition;
    myDragListener = dragListener;
    myPropertiesComponent = PropertiesComponent.getInstance();
    myModel = model;
    myPanel = new JPanel(new BorderLayout());
    myPanel.setFocusCycleRoot(true);
    myPanel.setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());
    myActionButtons = new ArrayList<>(4);
    myMinimizedButton = new MinimizedButton(definition.getTitle(), definition.getIcon(), this);
    mySearchField = new MySearchField(TOOL_WINDOW_PROPERTY_PREFIX + workBenchName + ".TEXT_SEARCH_HISTORY");
    mySearchActionButton = createActionButton(new SearchAction(), myDefinition.getButtonSize());
    setDefaultProperty(PropertyType.LEFT, definition.getSide().isLeft());
    setDefaultProperty(PropertyType.SPLIT, definition.getSplit().isBottom());
    setDefaultProperty(PropertyType.AUTO_HIDE, definition.getAutoHide().isAutoHide());
    updateContent();
    DumbService.getInstance(model.getProject()).smartInvokeLater(this::updateActions);
  }

  @Override
  public void dispose() {
    if (myContent != null) {
      Disposer.dispose(myContent);
      myContent = null;
      myDragListener = null;
      myPanel.removeAll();
    }
  }

  @NotNull
  public String getToolName() {
    return myDefinition.getName();
  }

  public int getToolOrder() {
    return myToolOrder;
  }

  public void setToolOrder(int order) {
    myToolOrder = order;
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

  public boolean isDetached() {
    return getProperty(PropertyType.DETACHED);
  }

  public void setDetached(boolean value) {
    setProperty(PropertyType.DETACHED, value);
  }

  public boolean getProperty(@NotNull PropertyType property) {
    if (property == PropertyType.MINIMIZED && isAutoHide()) {
      return !myAutoHideOpen;
    }
    return getLayoutProperty(Layout.CURRENT, property);
  }

  public void setProperty(@NotNull PropertyType property, boolean value) {
    if (property == PropertyType.MINIMIZED && isAutoHide()) {
      myAutoHideOpen = !value;
    }
    else {
      setLayoutProperty(Layout.CURRENT, property, value);
    }
    if (myMinimizedButton != null) {
      myMinimizedButton.setSelected(!isMinimized());
    }
  }

  private boolean getLayoutProperty(@NotNull Layout layout, @NotNull PropertyType property) {
    return myPropertiesComponent.getBoolean(getPropertyName(layout, property));
  }

  private void setLayoutProperty(@NotNull Layout layout, @NotNull PropertyType property, boolean value) {
    myPropertiesComponent.setValue(getPropertyName(layout, property), value);
  }

  public void setDefaultProperty(@NotNull PropertyType property, boolean defaultValue) {
    if (!myPropertiesComponent.isValueSet(getPropertyName(Layout.DEFAULT, property))) {
      // Force write of all default values:
      myPropertiesComponent.setValue(getPropertyName(Layout.DEFAULT, property), defaultValue, !defaultValue);
      setLayoutProperty(Layout.CURRENT, property, defaultValue);
    }
  }

  private String getPropertyName(@NotNull Layout layout, @NotNull PropertyType property) {
    return TOOL_WINDOW_PROPERTY_PREFIX + layout.getPrefix() + myWorkBenchName + "." + myDefinition.getName() + "." + property.name();
  }

  public void setPropertyAndUpdate(@NotNull PropertyType property, boolean value) {
    setProperty(property, value);
    if (property == PropertyType.FLOATING && value) {
      property = PropertyType.DETACHED;
      setProperty(property, true);
    }
    updateContent();
    updateActions();
    myModel.update(this, property);
    if (property == PropertyType.MINIMIZED && !value && myContent != null) {
      myContent.getFocusedComponent().requestFocus();
    }
  }

  public void storeDefaultLayout() {
    for (PropertyType property : PropertyType.values()) {
      setLayoutProperty(Layout.DEFAULT, property, getLayoutProperty(Layout.CURRENT, property));
    }
  }

  public void restoreDefaultLayout() {
    for (PropertyType property : PropertyType.values()) {
      setProperty(property, getLayoutProperty(Layout.DEFAULT, property));
    }
  }

  public void setContext(T context) {
    if (myContent != null) {
      myContent.setToolContext(context);
    }
  }

  @VisibleForTesting
  @Nullable
  ToolContent<T> getContent() {
    return myContent;
  }

  private void updateContent() {
    if (isDetached() && myContent != null) {
      myPanel.removeAll();
      myContent.setToolContext(null);
      Disposer.dispose(myContent);
      myContent = null;
    }
    else if (!isDetached() && myContent == null) {
      myContent = myDefinition.getFactory().create();
      assert myContent != null;
      myContent.setToolContext(myModel.getContext());
      myContent.setCloseAutoHideWindow(this::closeAutoHideWindow);
      myContent.setRestoreToolWindow(this::restore);
      myContent.setStartFiltering(this::startFiltering);
      myPanel.add(createHeader(myContent.supportsFiltering(), myContent.getAdditionalActions()), BorderLayout.NORTH);
      myPanel.add(myContent.getComponent(), BorderLayout.CENTER);
    }
  }

  private void restore() {
    if (!isDetached() && isMinimized()) {
      setPropertyAndUpdate(PropertyType.MINIMIZED, false);
    }
  }

  private void closeAutoHideWindow() {
    if (!isDetached() && isAutoHide() && !isMinimized()) {
      setPropertyAndUpdate(PropertyType.MINIMIZED, true);
    }
  }

  @NotNull
  private JComponent createHeader(boolean includeSearchButton, @NotNull List<AnAction> additionalActions) {
    JPanel header = new JPanel(new BorderLayout());
    header.add(createTitlePanel(myDefinition.getTitle(), includeSearchButton, mySearchField), BorderLayout.CENTER);
    header.add(createActionPanel(includeSearchButton, additionalActions), BorderLayout.EAST);
    header.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
    return header;
  }

  @NotNull
  private static JPanel createTitlePanel(@NotNull String title, boolean includeSearchField, @NotNull SearchTextField searchField) {
    CardLayout layout = new CardLayout();
    JPanel titlePanel = new JPanel(layout);
    JLabel titleLabel = new JBLabel(title);
    titleLabel.setBorder(JBUI.Borders.empty(2, 5, 2, 10));
    titleLabel.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
    titlePanel.add(titleLabel, LABEL_HEADER);
    if (includeSearchField) {
      // Override the preferred height of the search field in order to align all tool window headers
      searchField.setPreferredSize(new Dimension(searchField.getPreferredSize().width, titlePanel.getPreferredSize().height));
      titlePanel.add(searchField, SEARCH_HEADER);
    }
    layout.show(titlePanel, LABEL_HEADER);
    return titlePanel;
  }

  private void showSearchField(boolean show) {
    Container parent = mySearchField.getParent();
    CardLayout layout = (CardLayout)parent.getLayout();
    if (show) {
      layout.show(parent, SEARCH_HEADER);
      mySearchField.requestFocus();
    }
    else {
      layout.show(parent, LABEL_HEADER);
    }
    mySearchActionButton.setVisible(!show);
  }

  private void startFiltering(char character) {
    if (myContent == null || !myContent.supportsFiltering()) {
      return;
    }
    mySearchField.setText(String.valueOf(character));
    showSearchField(true);
  }

  @NotNull
  private JComponent createActionPanel(boolean includeSearchButton, @NotNull List<AnAction> additionalActions) {
    Dimension buttonSize = myDefinition.getButtonSize();
    int border = buttonSize.equals(NAVBAR_MINIMUM_BUTTON_SIZE) ? 2 : 0;
    JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
    actionPanel.setOpaque(false);
    actionPanel.setBorder(JBUI.Borders.empty(border, 0));
    if (includeSearchButton) {
      actionPanel.add(mySearchActionButton);
      mySearchActionButton.setVisible(true);
    }
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
    button.setFocusable(true);
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
    attachedSide.add(new TogglePropertyTypeAction(PropertyType.DETACHED, "None"));
    group.add(attachedSide);
    ActionManager manager = ActionManager.getInstance();
    group.add(new ToggleOppositePropertyTypeAction(PropertyType.AUTO_HIDE, manager.getAction(InternalDecorator.TOGGLE_DOCK_MODE_ACTION_ID)));
    group.add(new TogglePropertyTypeAction(PropertyType.FLOATING, manager.getAction(InternalDecorator.TOGGLE_FLOATING_MODE_ACTION_ID)));
    group.add(new TogglePropertyTypeAction(PropertyType.SPLIT, manager.getAction(InternalDecorator.TOGGLE_SIDE_MODE_ACTION_ID)));
  }

  static class DragEvent {
    private final MouseEvent myMouseEvent;
    private final Component myDragImage;
    private final Point myDragPoint;

    public DragEvent(@NotNull MouseEvent mouseEvent, @NotNull Component dragImage, @NotNull Point dragPoint) {
      myMouseEvent = mouseEvent;
      myDragImage = dragImage;
      myDragPoint = dragPoint;
    }

    @NotNull
    public Point getMousePoint() {
      return myMouseEvent.getPoint();
    }

    @NotNull
    public Component getDragImage() {
      return myDragImage;
    }

    @NotNull
    public Point getDragPoint() {
      return myDragPoint;
    }
  }

  interface ButtonDragListener<T> {
    void buttonDragged(@NotNull AttachedToolWindow<T> toolWindow, @NotNull DragEvent event);
    void buttonDropped(@NotNull AttachedToolWindow<T> toolWindow, @NotNull DragEvent event);
  }

  @VisibleForTesting
  void fireButtonDragged(@NotNull DragEvent event) {
    myDragListener.buttonDragged(this, event);
  }

  @VisibleForTesting
  void fireButtonDropped(@NotNull DragEvent event) {
    myDragListener.buttonDropped(this, event);
  }

  private static class MinimizedButton extends AnchoredButton {
    private final AttachedToolWindow myToolWindow;
    private JLabel myDragImage;
    private Point myStartDragPosition;

    public MinimizedButton(@NotNull String title, @NotNull Icon icon, @NotNull AttachedToolWindow toolWindow) {
      super(title, icon);
      myToolWindow = toolWindow;
      setBorder(BorderFactory.createEmptyBorder(5, 5, 0, 5));
      setFocusable(false);
      setRolloverEnabled(true);
      setSelected(!toolWindow.isMinimized());

      // This is needed on Linux otherwise the button shows
      // the Metal L&F gradient even though opaque is false
      setOpaque(false);
      setBackground(null);

      MouseInputAdapter listener = new MouseInputAdapter() {
        @Override
        public void mouseDragged(@NotNull MouseEvent event) {
          handleDragging(event);
        }

        @Override
        public void mouseReleased(@NotNull MouseEvent event) {
          stopDragging(event);
        }

        @Override
        public void mouseClicked(@NotNull MouseEvent event) {
          if (event.getButton() <= MouseEvent.BUTTON1) {
            setSelected(false);
            myToolWindow.setPropertyAndUpdate(AttachedToolWindow.PropertyType.MINIMIZED, !myToolWindow.isMinimized());
          }
          else {
            myToolWindow.showGearPopup(MinimizedButton.this, event.getX(), event.getY());
          }
        }
      };
      addMouseListener(listener);
      addMouseMotionListener(listener);
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
    public void paint(@NotNull Graphics graphics) {
      if (isDragging()) {
        return;
      }
      if (!myToolWindow.isLeft()) {
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
      return myToolWindow.isLeft() ? ToolWindowAnchor.LEFT : ToolWindowAnchor.RIGHT;
    }

    private boolean isDragging() {
      return myDragImage != null;
    }

    private void handleDragging(@NotNull MouseEvent event) {
      if (!isDragging()) {
        startDragging(event);
      }
      myToolWindow.fireButtonDragged(new DragEvent(event, myDragImage, myStartDragPosition));
    }

    private void stopDragging(@NotNull MouseEvent event) {
      if (isDragging()) {
        myToolWindow.fireButtonDropped(new DragEvent(event, myDragImage, myStartDragPosition));
        myDragImage = null;
        myStartDragPosition = null;
      }
    }

    private void startDragging(@NotNull MouseEvent event) {
      BufferedImage image = UIUtil.createImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
      Graphics graphics = image.getGraphics();
      paint(graphics);
      graphics.dispose();

      myDragImage = new JBLabel(new JBImageIcon(image));
      myStartDragPosition = event.getPoint();
    }
  }

  private static class UpdatableActionButton extends ActionButton {

    private UpdatableActionButton(@NotNull AnAction action, @NotNull Dimension buttonSize) {
      super(action, action.getTemplatePresentation().clone(), TOOL_WINDOW_TOOLBAR_PLACE, buttonSize);
    }

    public void update() {
      AnActionEvent event = new AnActionEvent(null, getDataContext(), myPlace, myPresentation, ActionManager.getInstance(), 0);
      ActionUtil.performDumbAwareUpdate(false, myAction, event, false);
    }

    @Override
    protected void presentationPropertyChanded(@NotNull PropertyChangeEvent event) {
      super.presentationPropertyChanded(event);
      update();
    }
  }

  private class SearchAction extends AnAction {
    public SearchAction() {
      super("Search");
      Presentation presentation = getTemplatePresentation();
      presentation.setIcon(AllIcons.Actions.FindPlain);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
      showSearchField(true);
    }
  }

  private class GearAction extends AnAction {
    public GearAction() {
      super("More Options");
      Presentation presentation = getTemplatePresentation();
      presentation.setIcon(AllIcons.General.Gear);
      presentation.setHoveredIcon(AllIcons.General.GearHover);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
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

  private class MySearchField extends SearchTextFieldWithStoredHistory implements KeyListener {
    private MySearchField(@NotNull String propertyName) {
      super(propertyName);
      addKeyboardListener(this);
      addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(DocumentEvent e) {
          if (myContent != null) {
            myContent.setFilter(getText().trim());
          }
        }
      });
    }

    @Override
    protected void onFocusLost() {
      Component focusedDescendent = IdeFocusManager.getGlobalInstance().getFocusedDescendantFor(this);
      if (focusedDescendent == null && getText().trim().isEmpty()) {
        showSearchField(false);
      }
    }

    @Override
    public void keyTyped(@NotNull KeyEvent event) {
      if (myContent != null && myContent.getFilterKeyListener() != null) {
        myContent.getFilterKeyListener().keyTyped(event);
        if (event.isConsumed()) {
          addCurrentTextToHistory();
        }
      }
    }

    @Override
    public void keyPressed(@NotNull KeyEvent event) {
      if (myContent != null && myContent.getFilterKeyListener() != null) {
        myContent.getFilterKeyListener().keyPressed(event);
        if (event.isConsumed()) {
          addCurrentTextToHistory();
        }
      }
      if (event.getKeyCode() == KeyEvent.VK_ESCAPE && getText().isEmpty()) {
        showSearchField(false);
      }
    }

    @Override
    public void keyReleased(@NotNull KeyEvent event) {
      if (myContent != null && myContent.getFilterKeyListener() != null) {
        myContent.getFilterKeyListener().keyReleased(event);
        if (event.isConsumed()) {
          addCurrentTextToHistory();
        }
      }
    }
  }
}
