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
package com.android.tools.idea.ui;

import com.android.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.*;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.LineMetrics;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;

/**
 * A gallery widget for displaying a collection of images.
 * <p/>
 * This widget obtains its model from {@link javax.swing.ListModel} and
 * relies on two functions to obtain image and lable for model object.
 * It does not support notions of "renderer" or "editor"
 */
public class ASGallery<E> extends JComponent implements Accessible, Scrollable {
  /**
   * Default insets around the cell contents.
   */
  private static final Insets DEFAULT_CELL_MARGIN = new Insets(1, 1, 1, 1);
  /**
   * Insets around cell content (image and title).
   */
  @NotNull private Insets myCellMargin = DEFAULT_CELL_MARGIN;
  /**
   * Timeout in ms when incremental search is reset
   */
  private static final int INCSEARCH_TIMEOUT_MS = 500; // ms
  /**
   * Listeners for events other then property event
   */
  private final EventListenerList myListeners = new EventListenerList();
  /**
   * Listens to changes in the model data
   */
  private final ListDataListener myListDataListener = new InternalListDataListener();
  /**
   * Size of the image. Currently all images will be scaled to this size, this
   * may change as we get more requirements.
   */
  @NotNull private Dimension myThumbnailSize = new Dimension(128, 128);
  /**
   * Index of the selected item or -1 if none
   */
  private int mySelectedIndex = -1;
  /**
   * Data shown in this component
   */
  private ListModel myModel;
  /**
   * Filter string for the incremental search
   */
  private String myFilterString = "";
  /**
   * Timestamp of the last keypress used for incremental search.
   */
  private long myPreviousKeypressTimestamp = 0;
  /**
   * Caches item images, is reset if different image provider is supplied.
   */
  @NotNull private LoadingCache<E, Optional<Image>> myImagesCache;
  /**
   * Obtains string label for the model object.
   */
  @NotNull private Function<? super E, String> myLabelProvider = Functions.toStringFunction();

  public ASGallery() {
    this(new DefaultListModel(), Functions.<Image>constant(null), Functions.toStringFunction(), new Dimension(0, 0));
  }

  public ASGallery(@NotNull ListModel model,
                   @NotNull Function<? super E, Image> imageProvider,
                   @NotNull Function<? super E, String> labelProvider,
                   @NotNull Dimension thumbnailSize) {
    Font listFont = UIUtil.getListFont();
    if (listFont != null) {
      setFont(listFont);
    }

    setThumbnailSize(thumbnailSize);
    setImageProvider(imageProvider);
    setLabelProvider(labelProvider);
    setModel(model);

    setOpaque(true);
    setFocusable(true);
    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        requestFocusInWindow();
        int cell = getCellAt(e.getPoint());
        if (cell >= 0) {
          setSelectedIndex(cell);
        }
      }
    });
    addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        reveal();
        updateFocusRectangle();
      }

      @Override
      public void focusLost(FocusEvent e) {
        updateFocusRectangle();
      }
    });
    addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped(KeyEvent e) {
        char keyChar = e.getKeyChar();
        if (keyChar != KeyEvent.CHAR_UNDEFINED) {
          incrementalSearch(keyChar);
        }
      }
    });
    setBackground(UIUtil.getListBackground());
    InputMap inputMap = getInputMap(JComponent.WHEN_FOCUSED);
    ActionMap actionMap = getActionMap();

    ImmutableMap<Integer, Action> keysToActions = ImmutableMap.<Integer, Action>builder().
      put(KeyEvent.VK_DOWN, new MoveSelectionAction(1, 0)).
      put(KeyEvent.VK_UP, new MoveSelectionAction(-1, 0)).
      put(KeyEvent.VK_LEFT, new MoveSelectionAction(0, -1)).
      put(KeyEvent.VK_RIGHT, new MoveSelectionAction(0, 1)).
      put(KeyEvent.VK_HOME, new JumpSelection() {
        @Override
        public int getIndex() {
          return 0;
        }
      }).
      put(KeyEvent.VK_END, new JumpSelection() {
        @Override
        public int getIndex() {
          return myModel.getSize() - 1;
        }
      }).
      build();
    for (Map.Entry<Integer, Action> entry : keysToActions.entrySet()) {
      String key = "selection_move_" + entry.getKey();
      inputMap.put(KeyStroke.getKeyStroke(entry.getKey(), 0), key);
      actionMap.put(key, entry.getValue());
    }
  }

  @Override
  public Dimension getMinimumSize() {
    Dimension size = new Dimension(computeCellSize());
    Insets insets = getInsets();
    size.setSize(size.getWidth() + insets.left + insets.right, size.getHeight() + insets.top + insets.bottom);
    return computeCellSize();
  }

  private static int intDivideRoundUp(int divident, int divisor) {
    return (divident + divisor - 1) / divisor;
  }

  private static int getElementIndex(@NotNull ListModel model, @Nullable Object element) {
    if (element == null) {
      return -1;
    }
    for (int i = 0; i < model.getSize(); i++) {
      Object modelElement = model.getElementAt(i);
      if (Objects.equal(element, modelElement)) {
        return i;
      }
    }
    return -1;
  }

  public void setLabelProvider(@NotNull Function<? super E, String> labelProvider) {
    myLabelProvider = labelProvider;
    repaint(getVisibleRect());
  }

  public void setThumbnailSize(@NotNull Dimension thumbnailSize) {
    if (!Objects.equal(thumbnailSize, myThumbnailSize)) {
      myThumbnailSize = thumbnailSize;
      invalidate();
      repaint(getVisibleRect());
    }
  }

  /**
   * Set the function that obtains the image for the item.
   * <p/>
   * Values are cached. We may need to provide a way to force value update if
   * it is needed at a later time.
   * (Implementation detail) Cache uses identity (==) comparison and does not
   * use {@link Object#equals(Object)}. Please do not rely on this behaviour
   * as it may change without prior notice.
   */
  public void setImageProvider(@NotNull Function<? super E, Image> imageProvider) {
    CacheLoader<? super E, Optional<Image>> cacheLoader = CacheLoader.from(ToOptionalFunction.wrap(imageProvider));
    myImagesCache = CacheBuilder.newBuilder().weakKeys().build(cacheLoader);
    repaint(getVisibleRect());
  }

  private void incrementalSearch(char keyChar) {
    final long timestamp = System.currentTimeMillis();
    if (timestamp - myPreviousKeypressTimestamp > INCSEARCH_TIMEOUT_MS) {
      myFilterString = String.valueOf(keyChar);
    }
    else {
      myFilterString += keyChar;
    }
    final int ind = findMatchingItem();
    myPreviousKeypressTimestamp = timestamp;
    if (ind < 0) {
      boolean resumedSearch = myFilterString.length() > 1;
      myFilterString = "";
      if (resumedSearch) {
        incrementalSearch(keyChar);
      }
    }
    else {
      setSelectedIndex(ind);
    }
  }

  private int findMatchingItem() {
    int itemCount = myModel.getSize();
    int startingIndex = Math.max(0, mySelectedIndex);
    // Ideal match starts with the search string. Otherwise we try to match
    // words (e.g. "maps" should match Google maps template)
    int secondBest = -1;
    final String normalizedFilterString = StringUtil.toLowerCase(myFilterString);

    for (int i = startingIndex; i < itemCount + startingIndex; i++) {
      // We only should to wrap search if there's no matches "under" the cursor
      final int index = i % itemCount;
      String title = getLabel(index);
      if (!StringUtil.isEmpty(title)) {
        String normalizedTitle = StringUtil.toLowerCase(title);
        if (normalizedTitle.startsWith(normalizedFilterString)) {
          return index;
        }
        else if (secondBest < 0 && normalizedTitle.contains(" " + normalizedFilterString)) {
          secondBest = index;
        }
      }
    }
    return secondBest;
  }

  @Nullable
  private String getLabel(int index) {
    Object element = myModel.getElementAt(index);
    if (element == null) {
      return null;
    }
    else {
      //noinspection unchecked
      return myLabelProvider.apply((E)element);
    }
  }

  @VisibleForTesting
  protected int getCellAt(@NotNull Point point) {
    Insets borderInsets = getInsets();
    int columnCount = getColumnCount();
    Dimension cellDimensions = computeCellSize();
    int galleryWidth = getClientWidth(borderInsets);

    int offsetX = point.x - borderInsets.left;
    int offsetY = point.y - borderInsets.top;
    if (offsetX >= galleryWidth || offsetX < 0) {
      return -1;
    }
    int column = 0;
    // We may have columns of (slightly) varied width due to rounding errors...
    while (getColumnOffset(column + 1, columnCount, galleryWidth) <= offsetX) {
      column++;
    }

    int row = offsetY / cellDimensions.height;
    if (row < 0 || row > myModel.getSize() / columnCount) {
      return -1;
    }
    int selection = column + row * columnCount;
    return selection >= 0 && selection < myModel.getSize() ? selection : -1;
  }

  private int getClientWidth(Insets borderInsets) {
    return getWidth() - borderInsets.left - borderInsets.right;
  }

  private int getNewSelectionIndex(int vdirection, int hdirection) {
    if (mySelectedIndex < 0) {
      return 0;
    }
    int columnCount = getColumnCount();
    int column = mySelectedIndex % columnCount + hdirection;
    int row = mySelectedIndex / columnCount + vdirection;
    if (column >= 0 && column < columnCount) {
      int newSelection = column + row * columnCount;
      if (newSelection < 0) {
        return 0;
      }
      else {
        int itemCount = myModel.getSize();
        if (newSelection >= itemCount) {
          return itemCount - 1;
        }
        else {
          return newSelection;
        }
      }
    }
    else {
      return mySelectedIndex;
    }
  }

  private void updateFocusRectangle() {
    if (mySelectedIndex >= 0) {
      repaint(getVisibleRect());
    }
  }

  @NotNull
  public Insets getCellMargin() {
    return myCellMargin;
  }

  /**
   * Set cell margin value.
   */
  public void setCellMargin(@Nullable Insets cellMargin) {
    cellMargin = cellMargin == null ? DEFAULT_CELL_MARGIN : cellMargin;
    if (!Objects.equal(cellMargin, myCellMargin)) {
      Insets oldInsets = myCellMargin;
      myCellMargin = cellMargin;
      firePropertyChange("cellMargin", oldInsets, cellMargin);
    }
  }

  @Nullable
  public E getSelectedElement() {
    if (mySelectedIndex < 0) {
      return null;
    }
    //noinspection unchecked
    return (E)myModel.getElementAt(mySelectedIndex);
  }

  public void setSelectedElement(@Nullable E element) {
    final int index;
    if (element == null) {
      index = -1;
    }
    else {
      index = getElementIndex(getModel(), element);
      if (index < 0) {
        throw new NoSuchElementException(element.toString());
      }
    }
    setSelectedIndex(index);
  }

  public int getSelectedIndex() {
    return mySelectedIndex;
  }

  public void setSelectedIndex(int selectedIndex) {
    setSelectedIndex(selectedIndex, true);
  }

  private void setSelectedIndex(int selectedIndex, boolean notifyListeners) {
    assert selectedIndex < myModel.getSize() && selectedIndex >= -1;
    if (selectedIndex != mySelectedIndex) {
      mySelectedIndex = selectedIndex;
      repaint(getVisibleRect());
      revealCell(mySelectedIndex);
      if (notifyListeners) {
        fireSelectionChanged(mySelectedIndex);
      }
    }
  }

  private void fireSelectionChanged(int newSelection) {
    boolean isSelectionListener = false;
    ListSelectionEvent event = new ListSelectionEvent(this, newSelection, newSelection, false);
    for (Object object : myListeners.getListenerList()) {
      if (isSelectionListener) {
        ((ListSelectionListener)object).valueChanged(event);
        isSelectionListener = false;
      }
      else {
        isSelectionListener = object == ListSelectionListener.class;
      }
    }
  }

  /**
   * @return data model
   */
  @NotNull
  public ListModel getModel() {
    return myModel;
  }

  public void setModel(@NotNull ListModel model) {
    if (!Objects.equal(myModel, model)) {
      final Object element;
      //noinspection ConstantConditions
      if (myModel != null) {
        myModel.removeListDataListener(myListDataListener);
        element = mySelectedIndex < 0 ? null : myModel.getElementAt(mySelectedIndex);
      }
      else {
        element = null;
      }
      myModel = model;
      myModel.addListDataListener(myListDataListener);
      invalidate();
      setSelectedIndex(getElementIndex(myModel, element));
    }
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension preferredSize = super.getPreferredSize();
    int itemCount = myModel == null ? 0 : myModel.getSize();
    if (isPreferredSizeSet() || itemCount == 0) {
      return preferredSize;
    }
    Insets insets = getInsets();
    int insetsWidth = insets.left + insets.right;
    int insetsHeight = insets.top + insets.bottom;
    Dimension cellSize = computeCellSize();
    final int width = getWidth();
    if (width == 0) {
      return new Dimension(cellSize.width + insetsWidth, cellSize.height + insetsHeight);
    }
    else { // Avoid horizontal scroll
      int rows = intDivideRoundUp(itemCount, getColumnCount());
      int height = rows * cellSize.height + insetsHeight;
      return new Dimension(Math.max(width, cellSize.width + insetsWidth), height);
    }
  }

  @VisibleForTesting
  protected int getColumnCount() {
    Dimension cellSize = computeCellSize();
    int width = getClientWidth(getInsets());
    int columnCount = Math.max(width / cellSize.width, 1);
    if (myModel != null) {
      int entries = myModel.getSize();
      // If one row, spread out the entries - but don't increase the entry width more then 2x, doesn't look right then
      if (columnCount > entries && columnCount < entries * 2) {
        return entries;
      }
    }
    return columnCount;
  }

  @VisibleForTesting
  protected Dimension computeCellSize() {
    Dimension imageSize = myThumbnailSize;
    int width = imageSize.width + myCellMargin.left + myCellMargin.right;
    int textHeight = getFont().getSize();
    int height = imageSize.height + myCellMargin.top + myCellMargin.bottom + 2 * textHeight;
    return new Dimension(width, height);
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    Rectangle clipRectangle = g.getClipBounds();
    if (isOpaque()) {
      g.setColor(getBackground());
      g.fillRect(clipRectangle.x, clipRectangle.y, clipRectangle.width, clipRectangle.height);
    }

    Dimension cellBounds = computeCellSize();
    int firstColumn = clipRectangle.x / cellBounds.width;
    int lastColumn = (clipRectangle.x + clipRectangle.width) / cellBounds.width;
    int firstRow = clipRectangle.y / cellBounds.height;
    int lastRow = intDivideRoundUp(clipRectangle.y + clipRectangle.height, cellBounds.height);
    Insets borderInsets = getInsets();
    int componentWidth = getClientWidth(borderInsets);
    int columns = getColumnCount();

    for (int row = firstRow; row <= lastRow; row++) {
      for (int column = firstColumn; column <= lastColumn; column++) {
        int cell = row * columns + column;
        if (cell >= myModel.getSize()) {
          break;
        }
        // Intermediate values like componentWidth/columns are not cached
        // as we are doing integer math here and rounding errors might
        // accumulate and cause "holes" in control we are painting.
        final int cellX = getColumnOffset(column, columns, componentWidth);
        final int width = getColumnOffset(column + 1, columns, componentWidth) - cellX;
        int cellY = row * cellBounds.height + borderInsets.top;
        int cellHeight = cellBounds.height - 1;
        Rectangle bounds = new Rectangle(cellX + borderInsets.left, cellY, width, cellHeight);
        paintCell(g, cell, bounds);
      }
    }
  }

  private void paintCell(Graphics g, int cell, Rectangle cellBounds) {
    String label = getLabel(cell);
    Image thumbnail = getImage(cell);
    drawSelection(g, cell, cellBounds, !StringUtil.isEmpty(label) && thumbnail != null);
    final int thumbnailHeight;
    if (thumbnail != null) {
      Dimension thumbnailSize = myThumbnailSize;
      int imageX = cellBounds.x + (cellBounds.width - thumbnailSize.width) / 2;
      int imageY = cellBounds.y + myCellMargin.top;
      g.drawImage(thumbnail, imageX, imageY, thumbnailSize.width, thumbnailSize.height, null);
      thumbnailHeight = thumbnailSize.height;
    }
    else {
      thumbnailHeight = 0;
    }
    paintLabel(g, cell, cellBounds, label, thumbnailHeight);
  }

  private void paintLabel(Graphics g, int cell, Rectangle cellBounds, @Nullable String label, int thumbnailHeight) {
    if (!StringUtil.isEmpty(label)) {
      final Color fg;
      if (hasFocus() && cell == mySelectedIndex && (getImage(cell) != null || UIUtil.isUnderDarcula())) {
        fg = UIUtil.getTreeSelectionForeground();
      }
      else {
        fg = UIUtil.getTreeForeground();
      }
      GraphicsUtil.setupAntialiasing(g);
      g.setColor(fg);
      FontMetrics fontMetrics = g.getFontMetrics();
      LineMetrics metrics = fontMetrics.getLineMetrics(label, g);
      int width = fontMetrics.stringWidth(label);

      int textBoxTop = myCellMargin.top + thumbnailHeight;
      int cellBottom = cellBounds.height - myCellMargin.bottom;

      int textY = cellBounds.y + (cellBottom + textBoxTop + (int)(metrics.getHeight() - metrics.getDescent())) / 2 ;
      int textX = (cellBounds.width - myCellMargin.left - myCellMargin.right - width) / 2 + cellBounds.x + myCellMargin.left;
      g.drawString(label, textX, textY);
    }
  }

  private void drawSelection(Graphics g, int cell, Rectangle cellBounds, boolean paintLabelBackground) {
    if (cell == mySelectedIndex) {
      Color currentColor = g.getColor();
      Color bg = UIUtil.getTreeSelectionBackground(hasFocus());
      g.setColor(bg);
      g.drawRect(cellBounds.x, cellBounds.y, cellBounds.width - 1, cellBounds.height - 1);
      if (paintLabelBackground) {
        int textBoxTop = myThumbnailSize.height + myCellMargin.top;
        g.fillRect(cellBounds.x, cellBounds.y + textBoxTop, cellBounds.width - 1, cellBounds.height - textBoxTop);
      }
      if (hasFocus()) {
        Border border = UIUtil.getTableFocusCellHighlightBorder();
        border.paintBorder(this, g, cellBounds.x, cellBounds.y, cellBounds.width, cellBounds.height);
      }
      g.setColor(currentColor);
    }
  }

  @Nullable
  private Image getImage(int cell) {
    Object elementAt = myModel.getElementAt(cell);
    if (elementAt == null) {
      return null;
    }
    else {
      try {
        @SuppressWarnings("unchecked") Optional<Image> image = myImagesCache.get((E)elementAt);
        return image.orNull();
      }
      catch (ExecutionException e) {
        Logger.getInstance(getClass()).error(e);
        return null;
      }
    }
  }

  @Override
  public Dimension getPreferredScrollableViewportSize() {
    return getPreferredSize();
  }

  @Override
  public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
    return 10;
  }

  @Override
  public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
    return computeCellSize().height;
  }

  @Override
  public boolean getScrollableTracksViewportWidth() {
    return true;
  }

  @Override
  public boolean getScrollableTracksViewportHeight() {
    return false;
  }

  public void addListSelectionListener(ListSelectionListener listener) {
    myListeners.add(ListSelectionListener.class, listener);
  }

  public void removeListSelectionListener(ListSelectionListener listener) {
    myListeners.remove(ListSelectionListener.class, listener);
  }

  private void reveal() {
    int selectedIndex = getSelectedIndex();
    if (selectedIndex > 0) {
      revealCell(selectedIndex);
    }
  }

  private void revealCell(int selectedIndex) {
    int columnCount = getColumnCount();
    Insets borderInsets = getInsets();

    int width = getClientWidth(borderInsets);
    int height = computeCellSize().height;

    int column = selectedIndex % columnCount;
    int x = getColumnOffset(column, columnCount, width) + borderInsets.left;
    int y = (selectedIndex / columnCount) * height + borderInsets.top;

    scrollRectToVisible(new Rectangle(x, y, width, height));
  }

  @VisibleForTesting
  protected int getColumnOffset(int column, int columnCount, int galleryWidth) {
    if (columnCount <= myModel.getSize()) {
      return column * galleryWidth / columnCount;
    }
    else {
      return column * computeCellSize().width;
    }
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleASGallery();
    }
    return accessibleContext;
  }

  /**
   * Guava containers do not like <code>null</code> values. This function
   * wraps such values into {@link com.google.common.base.Optional}.
   */
  private static final class ToOptionalFunction<P, R> implements Function<P, Optional<R>> {
    private final Function<P, R> myFunction;

    public ToOptionalFunction(Function<P, R> function) {
      myFunction = function;
    }

    public static <P, R> Function<P, Optional<R>> wrap(Function<P, R> function) {
      return new ToOptionalFunction<P, R>(function);
    }

    @Override
    public Optional<R> apply(P input) {
      R result = myFunction.apply(input);
      return Optional.fromNullable(result);
    }
  }

  private class MoveSelectionAction extends AbstractAction {
    private final int myVdirection;
    private final int myHdirection;

    public MoveSelectionAction(int vdirection, int hdirection) {
      myVdirection = vdirection;
      myHdirection = hdirection;
    }

    @Override
    public boolean isEnabled() {
      return getNewSelectionIndex(myVdirection, myHdirection) != mySelectedIndex;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      setSelectedIndex(getNewSelectionIndex(myVdirection, myHdirection));
    }
  }

  private abstract class JumpSelection extends AbstractAction {
    public abstract int getIndex();

    @Override
    public boolean isEnabled() {
      return getIndex() >= 0;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      setSelectedIndex(getIndex());
    }
  }

  private class InternalListDataListener implements ListDataListener {
    @Override
    public void intervalAdded(ListDataEvent e) {
      if (e.getIndex0() <= mySelectedIndex) {
        final int newSelection = mySelectedIndex + e.getIndex1() - e.getIndex0() - 1;
        setSelectedIndex(newSelection, false);
      }
      invalidate();
    }

    @Override
    public void intervalRemoved(ListDataEvent e) {
      int firstRemoved = e.getIndex0();
      if (firstRemoved <= mySelectedIndex) {
        final int lastRemoved = e.getIndex1();
        // Retain selection if this element was not deleted.
        // Move selection down if the element was deleted if there are elements after selected
        // Move selection up otherwise
        // Remove selection if the list is empty
        final int index = mySelectedIndex - (lastRemoved - firstRemoved + 1);
        final int newSelectionIndex = Math.min(Math.max(index, e.getIndex0()), myModel.getSize() - 1);
        // Notify if selected element was deleted
        setSelectedIndex(newSelectionIndex, mySelectedIndex <= lastRemoved);
      }
      invalidate();
    }

    @Override
    public void contentsChanged(ListDataEvent e) {
      invalidate();
    }
  }

  private final class AccessibleASGallery extends AccessibleJComponent implements PropertyChangeListener, ListSelectionListener {
    private Map<Integer, Accessible> children = Maps.newHashMap();

    public AccessibleASGallery() {
      setAccessibleName(ASGallery.this.getName());
      ASGallery.this.addPropertyChangeListener(this);
      ASGallery.this.addListSelectionListener(this);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
      if ("name".equals(evt.getPropertyName())) {
        setAccessibleName((String)evt.getNewValue());
      }
      else if ("model".equals(evt.getPropertyName())) {
        firePropertyChange(AccessibleContext.ACCESSIBLE_INVALIDATE_CHILDREN, null, ASGallery.this);
      }
    }

    @Override
    public int getAccessibleChildrenCount() {
      return getModel().getSize();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Accessible getAccessibleChild(int i) {
      if (!children.containsKey(i)) {
        children.put(i, new AccessibleCell(i));
      }
      return children.get(i);
    }

    @Override
    public AccessibleRole getAccessibleRole() {
      return AccessibleRole.LIST;
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
      firePropertyChange(AccessibleContext.ACCESSIBLE_ACTIVE_DESCENDANT_PROPERTY, false, true);
      firePropertyChange(AccessibleContext.ACCESSIBLE_SELECTION_PROPERTY, false, true);
    }
  }

  private final class AccessibleCell extends AccessibleJComponent implements Accessible, AccessibleComponent, AccessibleAction {
    private final int myIndex;

    public AccessibleCell(int index) {
      myIndex = index;
    }

    @Override
    public AccessibleRole getAccessibleRole() {
      return AccessibleRole.LABEL;
    }

    @Override
    public AccessibleStateSet getAccessibleStateSet() {
      final AccessibleState[] state = {AccessibleState.SELECTABLE, AccessibleState.SINGLE_LINE, AccessibleState.ACTIVE};
      return new AccessibleStateSet(state);
    }

    @Override
    public Accessible getAccessibleParent() {
      return ASGallery.this;
    }

    @Override
    public String getAccessibleName() {
      final String label = getLabel(myIndex);
      return StringUtil.isEmpty(label) ? "No Label" : label;
    }

    @Override
    public int getAccessibleIndexInParent() {
      return myIndex;
    }

    @Override
    public AccessibleContext getAccessibleContext() {
      return this;
    }

    @Override
    public int getAccessibleActionCount() {
      return 1;
    }

    @Override
    public String getAccessibleActionDescription(int i) {
      return AccessibleAction.CLICK;
    }

    @Override
    public boolean doAccessibleAction(int i) {
      setSelectedIndex(myIndex);
      return true;
    }
  }
}
