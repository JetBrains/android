// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.adtui.workbench;

import static com.intellij.openapi.actionSystem.IdeActions.ACTION_FIND;

import com.android.tools.adtui.common.ColoredIconGenerator;
import com.android.tools.adtui.util.ActionToolbarUtil;
import com.android.tools.idea.flags.StudioFlags;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupFactory.ActionSelectionAid;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.impl.AnchoredButton;
import com.intellij.openapi.wm.impl.InternalDecorator;
import com.intellij.toolWindow.StripeButtonUi;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.NewUI;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.SideBorder;
import com.intellij.ui.UIBundle;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBImageIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonModel;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.LayoutFocusTraversalPolicy;
import javax.swing.event.DocumentEvent;
import javax.swing.event.MouseInputAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

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
  private final MinimizedButton<T> myMinimizedButton;
  private final MinimizedButtonModel myMinimizedButtonModel;
  private MySearchField mySearchField;
  private ButtonDragListener<T> myDragListener;
  private ActionToolbar myActionToolbar;
  private ActionButton mySearchActionButton;
  private boolean myShowSearchField;

  @Nullable
  private ToolContent<T> myContent;
  private boolean myAutoHideOpen;
  private int myToolOrder;

  AttachedToolWindow(@NotNull ToolWindowDefinition<T> definition,
                     @NotNull ButtonDragListener<T> dragListener,
                     @NotNull WorkBench<T> workBench,
                     @NotNull SideModel<T> model,
                     boolean minimizedByDefault) {
    Disposer.register(workBench, this);
    myWorkBench = workBench;
    myDefinition = definition;
    myDragListener = dragListener;
    myPropertiesComponent = PropertiesComponent.getInstance();
    myModel = model;
    myPanel = new AttachedToolWindowPanel(this);
    myMinimizedButtonModel = new MinimizedButtonModel();
    myMinimizedButton = new MinimizedButton<>(definition.getTitle(), definition.getIcon(), this, myMinimizedButtonModel);
    setDefaultProperty(PropertyType.LEFT, definition.getSide().isLeft());
    setDefaultProperty(PropertyType.SPLIT, definition.getSplit().isBottom());
    setDefaultProperty(PropertyType.AUTO_HIDE, definition.getAutoHide().isAutoHide());
    setDefaultProperty(PropertyType.MINIMIZED, minimizedByDefault);
    updateContent();
    if (myDefinition.overrideSide()) {
      setProperty(PropertyType.LEFT, definition.getSide().isLeft());
    }
    if (myDefinition.overrideSplit()) {
      setProperty(PropertyType.SPLIT, definition.getSplit().isBottom());
    }
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
    return getProperty(PropertyType.FLOATING) && StudioFlags.DETACHABLE_ATTACHED_TOOLWINDOWS.get();
  }

  public void setFloating(boolean value) {
    setProperty(PropertyType.FLOATING, value);
  }

  public boolean isDetached() {
    return getProperty(PropertyType.DETACHED) && StudioFlags.DETACHABLE_ATTACHED_TOOLWINDOWS.get();
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
      myMinimizedButtonModel.setMinimized(isMinimized());
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
    myModel.update(this, PropertyType.DETACHED);
  }

  public void setContext(T context) {
    if (myContent != null) {
      myContent.setToolContext(context);
    }
  }

  @VisibleForTesting
  @Nullable
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
      myActionToolbar = null;
      mySearchActionButton = null;
    }
    else if (!isDetached() && myContent == null) {
      myContent = myDefinition.getFactory().apply(this);
      assert myContent != null;
      myContent.setToolContext(myModel.getContext());
      myContent.registerCallbacks(this);
      myPanel.add(createHeader(myContent), BorderLayout.NORTH);
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
  private JComponent createHeader(@NotNull ToolContent<T> content) {
    myActionToolbar = createToolbar(content);
    mySearchActionButton = findSearchActionButton(content, myActionToolbar);

    JPanel header = new JPanel(new BorderLayout());
    header.add(createTitlePanel(myDefinition.getTitle(), content.supportsFiltering()), BorderLayout.CENTER);
    header.add(myActionToolbar.getComponent(), BorderLayout.EAST);
    header.setBorder(new SideBorder(JBColor.border(), SideBorder.BOTTOM));
    return header;
  }

  @NotNull
  private JPanel createTitlePanel(@NotNull String title, boolean includeSearchField) {
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
      mySearchField = new MySearchField(TOOL_WINDOW_PROPERTY_PREFIX + myWorkBench.getName() + ".TEXT_SEARCH_HISTORY");

      if (!NewUI.isEnabled() && (myDefinition.showGearAction() || myDefinition.showHideAction())) {
        // Override the preferred height of the search field in order to align with the toolbar in the center panel:
        mySearchField.setPreferredSize(new Dimension(mySearchField.getPreferredSize().width, titlePanel.getPreferredSize().height));
      }
      titlePanel.add(mySearchField, SEARCH_HEADER);
    }
    layout.show(titlePanel, LABEL_HEADER);
    return titlePanel;
  }

  private void showSearchField(boolean show) {
    if (myContent == null || mySearchField == null) {
      return;
    }
    Container parent = mySearchField.getParent();
    CardLayout layout = (CardLayout)parent.getLayout();
    myShowSearchField = show;
    if (show) {
      layout.show(parent, SEARCH_HEADER);
      mySearchField.requestFocus();
    }
    else {
      layout.show(parent, LABEL_HEADER);
    }
    updateActions();
  }

  @Override
  public void startFiltering(@NotNull String initialSearchString) {
    if (myContent == null || mySearchField == null) {
      return;
    }
    mySearchField.setText(initialSearchString);
    showSearchField(true);
  }

  @Override
  public void stopFiltering() {
    if (myContent == null || mySearchField == null) {
      return;
    }
    mySearchField.setText("");
    showSearchField(false);
  }

  @NotNull
  private ActionToolbar createToolbar(@NotNull ToolContent<T> content) {
    DefaultActionGroup rightGroup = new DefaultActionGroup();
    if (content.supportsFiltering()) {
      rightGroup.add(new SearchAction());
    }
    if (!content.getAdditionalActions().isEmpty()) {
      rightGroup.addAll(content.getAdditionalActions());
      rightGroup.add(Separator.getInstance());
    }
    if (myDefinition.showGearAction()) {
      rightGroup.add(new GearAction());
    }
    if (myDefinition.showHideAction()) {
      rightGroup.add(new HideAction());
    }

    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("AttachedToolWindow", rightGroup, true);
    ActionToolbarUtil.makeToolbarNavigable(actionToolbar);
    actionToolbar.setMinimumButtonSize(myDefinition.getButtonSize());
    actionToolbar.setTargetComponent(myPanel);
    actionToolbar.setLayoutStrategy(ToolbarLayoutStrategy.NOWRAP_STRATEGY);
    actionToolbar.setReservePlaceAutoPopupIcon(false);
    return actionToolbar;
  }

  @Nullable
  private ActionButton findSearchActionButton(@NotNull ToolContent<T> content, @NotNull ActionToolbar actionToolbar) {
    if (!content.supportsFiltering()) {
      return null;
    }
    return ActionToolbarUtil.findActionButton(actionToolbar, actionToolbar.getActions().get(0));
  }

  @Override
  public void updateActions() {
    if (myActionToolbar != null) {
      myActionToolbar.updateActionsAsync();
    }
  }

  private void showGearPopup(@NotNull Component component, int x, int y) {
    DefaultActionGroup group = new DefaultActionGroup();
    addGearPopupActions(group);

    DataContext context = DataManager.getInstance().getDataContext(component);
    ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(
      null, group, context, ActionSelectionAid.SPEEDSEARCH, true, null, -1, null, ActionPlaces.POPUP);
    popup.show(new RelativePoint(component, new Point(x, y)));
  }

  private void addGearPopupActions(@NotNull DefaultActionGroup group) {
    if (myContent != null) {
      List<AnAction> myExtraGearActions = myContent.getGearActions();
      if (!myExtraGearActions.isEmpty()) {
        group.addAll(myExtraGearActions);
        group.addSeparator();
      }
    }
    DefaultActionGroup attachedLocation = DefaultActionGroup.createPopupGroup(() -> "Attached to");
    if (myDefinition.isSplitModeChangesAllowed()) {
      attachedLocation.add(new AttachedLocationAction(AttachedLocation.LeftTop));
      attachedLocation.add(new AttachedLocationAction(AttachedLocation.LeftBottom));
      attachedLocation.add(new AttachedLocationAction(AttachedLocation.RightTop));
      attachedLocation.add(new AttachedLocationAction(AttachedLocation.RightBottom));
    }
    else {
      attachedLocation.add(new TogglePropertyTypeAction(PropertyType.LEFT, "Left"));
      attachedLocation.add(new ToggleOppositePropertyTypeAction(PropertyType.LEFT, "Right"));
    }

    attachedLocation.add(new SwapAction());
    if (myDefinition.isFloatingAllowed() && StudioFlags.DETACHABLE_ATTACHED_TOOLWINDOWS.get()) {
      attachedLocation.add(new TogglePropertyTypeAction(PropertyType.DETACHED, "None"));
    }
    group.add(attachedLocation);
    ActionManager manager = ActionManager.getInstance();
    if (myDefinition.isAutoHideAllowed()) {
      group.add(
        new ToggleOppositePropertyTypeAction(PropertyType.AUTO_HIDE, manager.getAction(InternalDecorator.TOGGLE_DOCK_MODE_ACTION_ID)));
    }
    if (myDefinition.isFloatingAllowed() && StudioFlags.DETACHABLE_ATTACHED_TOOLWINDOWS.get()) {
      group.add(new TogglePropertyTypeAction(PropertyType.FLOATING, manager.getAction(InternalDecorator.TOGGLE_FLOATING_MODE_ACTION_ID)));
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

  /**
   * Set the active state of this tool window.<br/>
   * Note: this method is only called if the new UI is active.
   */
  public void setActive(boolean isActive) {
    myMinimizedButtonModel.setActive(isActive);
    myMinimizedButton.setActive(isActive);
  }

  private static class MinimizedButton<T> extends AnchoredButton {
    private final AttachedToolWindow<T> myToolWindow;
    private final Icon myIcon;
    private JLabel myDragImage;
    private Point myStartDragPosition;

    private MinimizedButton(
      @NotNull String title,
      @NotNull Icon icon,
      @NotNull AttachedToolWindow<T> toolWindow,
      @NotNull ButtonModel model
    ) {
      super(title, icon);
      setModel(model);
      myToolWindow = toolWindow;
      myIcon = icon;
      setBorder(BorderFactory.createEmptyBorder(5, 5, 0, 5));
      setFocusable(false);
      setRolloverEnabled(true);

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
          if ((event.getModifiersEx() & InputEvent.META_DOWN_MASK) == 0) {
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
      setSelected(!toolWindow.isMinimized());
      setIcon(myIcon);
    }

    public void setActive(boolean isActive) {
      setIcon(isActive ? ColoredIconGenerator.generateWhiteIcon(myIcon) : myIcon);
    }

    @Override
    public void updateUI() {
      setUI(new StripeButtonUi());
      setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
    }

    /**
     * The {@link StripeButtonUi} is drawing the button slightly to the left for buttons on
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

  /**
   * A ButtonModel for a MinimizedButton.
   */
  private static class MinimizedButtonModel extends JToggleButton.ToggleButtonModel {
    private boolean myIsActive;
    private boolean myIsMinimized;

    public void setActive(boolean isActive) {
      if (isActive != myIsActive) {
        myIsActive = isActive;
        fireStateChanged();
      }
    }

    public void setMinimized(boolean isMinimized) {
      if (isMinimized != myIsMinimized) {
        myIsMinimized = isMinimized;
        fireStateChanged();
      }
    }

    /**
     * When setSelected is called we want to change the minimized state.
     */
    public void setSelected(boolean isSelected) {
      setMinimized(!isSelected);
    }

    /**
     * The selected property will cause:
     * <ul>
     *   <li>the NewUI to paint the background bright blue</li>
     *   <li>the old UI to paint the background a darker color</li>
     * </ul>
     */
    @Override
    public boolean isSelected() {
      return NewUI.isEnabled() ? myIsActive : !myIsMinimized;
    }

    /**
     * Override the isRollover property for the NewUI to show a selected state when not minimized.
     */
    @Override
    public boolean isRollover() {
      return NewUI.isEnabled() ? !myIsMinimized : super.isRollover();
    }
  }

  private class SearchAction extends DumbAwareAction {

    private SearchAction() {
      super("Search", null, AllIcons.Actions.Find);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
      Presentation presentation = event.getPresentation();
      presentation.setVisible(!myShowSearchField);
      presentation.setEnabled(myContent != null && myContent.isFilteringActive());
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
      super(UIBundle.message("tool.window.hide.action.name"), null, AllIcons.General.HideToolWindow);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
      setPropertyAndUpdate(PropertyType.MINIMIZED, true);
    }
  }

  private class TogglePropertyTypeAction extends DumbAwareToggleAction {
    private final PropertyType myProperty;

    private TogglePropertyTypeAction(@NotNull PropertyType property, @NotNull String text) {
      super(text);
      myProperty = property;
    }

    private TogglePropertyTypeAction(@NotNull PropertyType property, @NotNull AnAction action) {
      myProperty = property;
      copyFrom(action);
    }

    @NotNull
    @Override
    public ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
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

  private class AttachedLocationAction extends AnAction {
    private final AttachedLocation myLocation;

    private AttachedLocationAction(@NotNull AttachedLocation location) {
      super(location.getTitle(), null, location.getIcon());
      myLocation = location;
    }

    @NotNull
    @Override
    public ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
      boolean isCurrentLocation = myLocation.isLeft() == getProperty(PropertyType.LEFT) &&
                                  myLocation.isBottom() == getProperty(PropertyType.SPLIT);
      event.getPresentation().setEnabled(!isCurrentLocation);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
      setProperty(PropertyType.LEFT, myLocation.isLeft());
      setPropertyAndUpdate(PropertyType.SPLIT, myLocation.isBottom());
    }
  }

  private class ToggleOppositePropertyTypeAction extends TogglePropertyTypeAction {
    private ToggleOppositePropertyTypeAction(@NotNull PropertyType property, @NotNull String text) {
      super(property, text);
    }

    private ToggleOppositePropertyTypeAction(@NotNull PropertyType property, @NotNull AnAction action) {
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

    /**
     * Workaround for bug: IDEA-242498
     *
     * SearchTextField.addNotify() will register an action to clear the text on ESC. The action is wrapped (copied into a new instance)
     * before being added to the list stored as the client property AnAction.ACTION_KEYS. That means that our memory leak checks will
     * detect a leak when addNotify is called multiple times. Since no other actions are added this way, it is safe to just remove all
     * the registered actions on removeNotify(). See b/156924012.
     */
    @Override
    public void removeNotify() {
      super.removeNotify();
      putClientProperty(AnAction.ACTIONS_KEY, null);
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
        else if (mySearchActionButton != null) {
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

  /**
   * The main panel used in an AttachedToolWindow.<br/>
   * This class is here to help find the currently focused tool window.
   * @see WorkBenchManager#findActiveToolWindow
   */
  final static class AttachedToolWindowPanel extends JPanel {
    private final AttachedToolWindow<?> myToolWindow;

    @VisibleForTesting
    AttachedToolWindowPanel(@NotNull AttachedToolWindow<?> toolWindow) {
      super(new BorderLayout());
      setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());
      myToolWindow = toolWindow;
    }

    @TestOnly
    public AttachedToolWindow<?> getToolWindow() {
      return myToolWindow;
    }

    /**
     * Sets the current active state (focus) of the tool window.
     */
    public void setActive(boolean isActive) {
      myToolWindow.setActive(isActive);
    }
  }
}
