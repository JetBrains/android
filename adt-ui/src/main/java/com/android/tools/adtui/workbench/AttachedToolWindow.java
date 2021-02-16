// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.adtui.workbench;

import static com.intellij.openapi.actionSystem.ActionToolbar.NAVBAR_MINIMUM_BUTTON_SIZE;
import static com.intellij.openapi.actionSystem.IdeActions.ACTION_FIND;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.impl.AnchoredButton;
import com.intellij.openapi.wm.impl.StripeButtonUI;
import com.intellij.openapi.wm.impl.InternalDecorator;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.SideBorder;
import com.intellij.ui.UIBundle;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBImageIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.MouseInputAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * AttachedToolWindow is a tool window that can be attached to a {@link WorkBench}.
 * This implementation is inspired by {@link com.intellij.designer.LightToolWindow}.
 *
 * @param <T> the type of data that is being edited by the associated {@link WorkBench}
 */
class AttachedToolWindow<T> implements ToolWindowCallback, Disposable {
  static final String TOOL_WINDOW_PROPERTY_PREFIX = "ATTACHED_TOOL_WINDOW.";
  static final String TOOL_WINDOW_TOOLBAR_PLACE = "TOOL_WINDOW_TOOLBAR";
  static final String LABEL_HEADER = "LABEL";
  static final String SEARCH_HEADER = "SEARCH";

  enum PropertyType {AUTO_HIDE, MINIMIZED, LEFT, SPLIT, DETACHED, FLOATING}

  private final WorkBench<T> myWorkBench;
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

  AttachedToolWindow(@NotNull ToolWindowDefinition<T> definition,
                     @NotNull ButtonDragListener<T> dragListener,
                     @NotNull WorkBench<T> workBench,
                     @NotNull SideModel<T> model,
                     boolean minimizedByDefault) {
    myWorkBench = workBench;
    myDefinition = definition;
    myDragListener = dragListener;
    myPropertiesComponent = PropertiesComponent.getInstance();
    myModel = model;
    myPanel = new JPanel(new BorderLayout());
    myPanel.setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());
    myActionButtons = new ArrayList<>(4);
    myMinimizedButton = new MinimizedButton(definition.getTitle(), definition.getIcon(), this);
    mySearchField = new MySearchField(TOOL_WINDOW_PROPERTY_PREFIX + workBench.getName() + ".TEXT_SEARCH_HISTORY");
    mySearchActionButton = createActionButton(new SearchAction(), myDefinition.getButtonSize());
    setDefaultProperty(PropertyType.LEFT, definition.getSide().isLeft());
    setDefaultProperty(PropertyType.SPLIT, definition.getSplit().isBottom());
    setDefaultProperty(PropertyType.AUTO_HIDE, definition.getAutoHide().isAutoHide());
    setDefaultProperty(PropertyType.MINIMIZED, minimizedByDefault);
    updateContent();
    DumbService.getInstance(model.getProject()).smartInvokeLater(this::updateActions);
    AnAction globalFindAction = ActionManager.getInstance().getAction(ACTION_FIND);
    if (globalFindAction != null) {
      new FindAction().registerCustomShortcutSet(globalFindAction.getShortcutSet(), myPanel, this);
    }
  }

  @Override
  public void dispose() {
    if (myContent != null) {
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
    String context = myWorkBench.getContext();
    return String.format("%s%s%s.%s.%s%s%s", TOOL_WINDOW_PROPERTY_PREFIX, layout.getPrefix(), myWorkBench.getName(), myDefinition.getName(),
                         context, StringUtil.isEmpty(context) ? "" : ".", property.name());
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
    updateContent();
    updateActions();
    myModel.update(this, PropertyType.DETACHED);
  }

  public void setContext(T context) {
    if (myContent != null) {
      myContent.setToolContext(context);
    }
  }

  @VisibleForTesting
  public SearchTextField getSearchField() {
    return mySearchField;
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
      myContent = myDefinition.getFactory().apply(this);
      assert myContent != null;
      myContent.setToolContext(myModel.getContext());
      myContent.registerCallbacks(this);
      myPanel.add(createHeader(myContent.supportsFiltering(), myContent.getAdditionalActions()), BorderLayout.NORTH);
      myPanel.add(myContent.getComponent(), BorderLayout.CENTER);
    }
    myPanel.putClientProperty(ToolContent.TOOL_CONTENT_KEY, myContent);
  }

  @Override
  public void restore() {
    if (!isDetached() && isMinimized()) {
      setPropertyAndUpdate(PropertyType.MINIMIZED, false);
    }
  }

  @Override
  public void autoHide() {
    if (!isDetached() && isAutoHide() && !isMinimized()) {
      setPropertyAndUpdate(PropertyType.MINIMIZED, true);
    }
  }

  @NotNull
  private JComponent createHeader(boolean includeSearchButton, @NotNull List<AnAction> additionalActions) {
    JPanel header = new JPanel(new BorderLayout());
    header.add(createTitlePanel(myDefinition.getTitle(), includeSearchButton, mySearchField), BorderLayout.CENTER);
    header.add(createActionPanel(includeSearchButton, additionalActions), BorderLayout.EAST);
    header.setBorder(new SideBorder(JBColor.border(), SideBorder.BOTTOM));
    return header;
  }

  @NotNull
  private static JPanel createTitlePanel(@NotNull String title, boolean includeSearchField, @NotNull SearchTextField searchField) {
    CardLayout layout = new CardLayout();
    JPanel titlePanel = new JPanel(layout);
    JLabel titleLabel = new JBLabel(title) {
      @Override
      public void updateUI() {
        super.updateUI();
        setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
      }
    };
    titleLabel.setBorder(JBUI.Borders.empty(2, 5, 2, 10));
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
    if (myContent == null || !myContent.supportsFiltering()) {
      return;
    }
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

  @Override
  public void startFiltering(@NotNull String initialSearchString) {
    if (myContent == null || !myContent.supportsFiltering()) {
      return;
    }
    mySearchField.setText(initialSearchString);
    showSearchField(true);
  }

  @Override
  public void stopFiltering() {
    if (myContent == null || !myContent.supportsFiltering()) {
      return;
    }
    mySearchField.setText("");
    showSearchField(false);
  }

  @NotNull
  private JComponent createActionPanel(boolean includeSearchButton, @NotNull List<AnAction> additionalActions) {
    Dimension buttonSize = myDefinition.getButtonSize();
    int border = buttonSize.equals(NAVBAR_MINIMUM_BUTTON_SIZE) ? 4 : 2;
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

  @VisibleForTesting
  public void updateActions() {
    myActionButtons.forEach(UpdatableActionButton::update);
  }

  private void showGearPopup(@NotNull Component component, int x, int y) {
    DefaultActionGroup group = new DefaultActionGroup();
    addGearPopupActions(group);

    ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.TOOLWINDOW_POPUP, group);
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
    DefaultActionGroup attachedSide = DefaultActionGroup.createPopupGroup(() -> "Attached Side");
    attachedSide.add(new TogglePropertyTypeDumbAction(PropertyType.LEFT, "Left"));
    attachedSide.add(new ToggleOppositePropertyTypeDumbAction(PropertyType.LEFT, "Right"));
    attachedSide.add(new SwapAction());
    if (myDefinition.isFloatingAllowed()) {
      attachedSide.add(new TogglePropertyTypeDumbAction(PropertyType.DETACHED, "None"));
    }
    group.add(attachedSide);
    ActionManager manager = ActionManager.getInstance();
    if (myDefinition.isAutoHideAllowed()) {
      group.add(
        new ToggleOppositePropertyTypeDumbAction(PropertyType.AUTO_HIDE, manager.getAction(InternalDecorator.TOGGLE_DOCK_MODE_ACTION_ID)));
    }
    if (myDefinition.isFloatingAllowed()) {
      group.add(new TogglePropertyTypeDumbAction(PropertyType.FLOATING, manager.getAction(InternalDecorator.TOGGLE_FLOATING_MODE_ACTION_ID)));
    }
    if (myDefinition.isSplitModeChangesAllowed()) {
      group.add(new TogglePropertyTypeDumbAction(PropertyType.SPLIT, manager.getAction(InternalDecorator.TOGGLE_SIDE_MODE_ACTION_ID)));
    }
  }

  static class DragEvent {
    private final MouseEvent myMouseEvent;
    private final Component myDragImage;
    private final Point myDragPoint;

    DragEvent(@NotNull MouseEvent mouseEvent, @NotNull Component dragImage, @NotNull Point dragPoint) {
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

  private static final class MinimizedButton extends AnchoredButton {
    private final AttachedToolWindow<?> myToolWindow;
    private JLabel myDragImage;
    private Point myStartDragPosition;

    private MinimizedButton(@NotNull String title, @NotNull Icon icon, @NotNull AttachedToolWindow<?> toolWindow) {
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
      BufferedImage image = ImageUtil.createImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
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
  }

  private class SearchAction extends DumbAwareAction {
    private SearchAction() {
      super("Search");
      Presentation presentation = getTemplatePresentation();
      presentation.setIcon(AllIcons.Actions.Find);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
      showSearchField(true);
    }
  }

  private class GearAction extends DumbAwareAction {
    private GearAction() {
      super("More Options", null, AllIcons.General.GearPlain);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
      int x = 0;
      int y = 0;
      InputEvent inputEvent = event.getInputEvent();
      if (inputEvent instanceof MouseEvent) {
        x = ((MouseEvent)inputEvent).getX();
        y = ((MouseEvent)inputEvent).getY();
      }

      showGearPopup(inputEvent.getComponent(), x, y);
    }
  }

  private class HideAction extends DumbAwareAction {
   private HideAction() {
      super(UIBundle.messagePointer("tool.window.hide.action.name"), AllIcons.General.HideToolWindow);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
      setPropertyAndUpdate(PropertyType.MINIMIZED, true);
    }
  }

  private class TogglePropertyTypeDumbAction extends DumbAwareToggleAction {
    private final PropertyType myProperty;

    private TogglePropertyTypeDumbAction(@NotNull PropertyType property, @NotNull String text) {
      super(text);
      myProperty = property;
    }

    private TogglePropertyTypeDumbAction(@NotNull PropertyType property, @NotNull AnAction action) {
      myProperty = property;
      copyFrom(action);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent event) {
      return getProperty(myProperty);
    }

    @Override
    public void setSelected(@NotNull AnActionEvent event, boolean state) {
      setPropertyAndUpdate(myProperty, state);
    }
  }

  private class ToggleOppositePropertyTypeDumbAction extends TogglePropertyTypeDumbAction {
    private ToggleOppositePropertyTypeDumbAction(@NotNull PropertyType property, @NotNull String text) {
      super(property, text);
    }

    private ToggleOppositePropertyTypeDumbAction(@NotNull PropertyType property, @NotNull AnAction action) {
      super(property, action);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent event) {
      return !super.isSelected(event);
    }

    @Override
    public void setSelected(@NotNull AnActionEvent event, boolean state) {
      super.setSelected(event, !state);
    }
  }

  private class SwapAction extends DumbAwareAction {
    private SwapAction() {
      super("Swap");
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
      myModel.swap();
      updateActions();
    }
  }

  private class MySearchField extends SearchTextField implements KeyListener {
    private Component myOldFocusComponent;

    private MySearchField(@NotNull String propertyName) {
      super(propertyName);
      addKeyboardListener(this);
      addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(@NotNull DocumentEvent e) {
          if (myContent != null) {
            myContent.setFilter(getText().trim());
          }
        }
      });
      getTextEditor().addFocusListener(new FocusAdapter() {
        @Override
        public void focusGained(@NotNull FocusEvent event) {
          myOldFocusComponent = event.getOppositeComponent();
        }
      });
    }

    @Override
    protected void onFocusLost() {
      Component focusedDescendant = IdeFocusManager.getGlobalInstance().getFocusedDescendantFor(this);
      if (focusedDescendant == null && getText().trim().isEmpty()) {
        showSearchField(false);
      }
      myOldFocusComponent = null;
      super.onFocusLost();
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
        if (myOldFocusComponent != null) {
          myOldFocusComponent.requestFocus();
        }
        else if (myContent != null) {
          myContent.getFocusedComponent().requestFocus();
        }
        else {
          mySearchActionButton.requestFocus();
        }
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

  private final class FindAction extends DumbAwareAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
      showSearchField(true);
    }
  }
}
