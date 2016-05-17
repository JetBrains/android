/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.tools.idea.rendering.ImageUtils;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Maps;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.TreeUIHelper;
import com.intellij.ui.components.JBList;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * A gallery widget for displaying a collection of images.
 * <p/>
 * This widget obtains its model from {@link ListModel} and
 * relies on two functions to obtain image and label for model object.
 */
public class ASGallery<E> extends JBList {
  /**
   * Default insets around the cell contents.
   */
  private static final Insets DEFAULT_CELL_MARGIN = new Insets(1, 1, 1, 1);
  /**
   * Insets around cell content (image and title).
   */
  @NotNull private Insets myCellMargin = DEFAULT_CELL_MARGIN;
  /**
   * Size of the image. Currently all images will be scaled to this size, this
   * may change as we get more requirements.
   */
  @NotNull private JBDimension myThumbnailSize;
  /**
   * Obtains string label for the model object.
   */
  @NotNull private Function<? super E, String> myLabelProvider;
  /**
   * Caches item images, is reset if different image provider is supplied.
   */
  @Nullable private LoadingCache<E, Optional<Image>> myImagesCache;
  /**
   * Caches item images, is reset if different image provider is supplied.
   */
  @NotNull private Map<E, CellRenderer> myCellRenderers = Maps.newHashMap();

  @Nullable private Action myDefaultAction;

  public ASGallery() {
    this(new DefaultListModel(), Functions.<Image>constant(null), Functions.toStringFunction(), new Dimension(0, 0), null);
  }
  public ASGallery(@NotNull ListModel model,
                   @NotNull Function<? super E, Image> imageProvider,
                   @NotNull Function<? super E, String> labelProvider,
                   @NotNull Dimension thumbnailSize,
                   @Nullable Action defaultAction) {
    myThumbnailSize = JBDimension.create(thumbnailSize);
    myLabelProvider = labelProvider;
    myDefaultAction = defaultAction;

    Font listFont = UIUtil.getListFont();
    if (listFont != null) {
      setFont(listFont);
    }

    setImageProvider(imageProvider);
    setLabelProvider(labelProvider);
    setModel(model);
    setThumbnailSize(thumbnailSize);

    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    setLayoutOrientation(JList.HORIZONTAL_WRAP);
    setVisibleRowCount(-1);
    setOpaque(true);
    setFocusable(true);
    setCellRenderer(new GalleryCellRenderer());
    setBackground(UIUtil.getListBackground());

    installListeners();
    installKeyboardActions();
    TreeUIHelper.getInstance().installListSpeedSearch(this, new Convertor<Object, String>() {
      @Override
      public String convert(Object o) {
        return myLabelProvider.apply((E)o);
      }
    });
  }

  private void installListeners() {
    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        // When the list is resized, we re-compute the width of the elements to avoid a gap between
        // the last column on the right and the list border.
        Dimension cellSize = computeCellSize();
        setFixedCellWidth(cellSize.width);
      }
    });
    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        int index = getSelectedIndex();
        if (index < 0) return;
        ensureIndexIsVisible(index);
      }

      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() > 1 && myDefaultAction != null) {
          int index = getSelectedIndex();
          if (index < 0) return;
          Rectangle bounds = getCellBounds(index, index);
          if (bounds.contains(e.getPoint())) {
            myDefaultAction.actionPerformed(new ActionEvent(e.getSource(), 0, null));
          }
        }
      }
    });
  }

  private void installKeyboardActions() {
    getActionMap().put("nextListElement", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        int index = getSelectedIndex();
        if (index < 0)
          return;
        index++;
        if (index >= getModel().getSize())
          return;

        setSelectedIndex(index);
        ensureIndexIsVisible(index);
      }
    });

    getActionMap().put("previousListElement", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        int index = getSelectedIndex();
        if (index <= 0)
          return;
        index--;

        setSelectedIndex(index);
        ensureIndexIsVisible(index);
      }
    });

    // BasicListUI does not handle wrapping to next/previous line by default, so we
    // customize arrow key actions to implement the wrapping behavior.
    final String nextListElementKey;
    final String previousListElementKey;
    if (getComponentOrientation().isLeftToRight()) {
      nextListElementKey = "RIGHT";
      previousListElementKey = "LEFT";
    } else {
      nextListElementKey = "LEFT";
      previousListElementKey = "RIGHT";
    }

    getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(nextListElementKey), "nextListElement");
    getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("KP_" + nextListElementKey), "nextListElement");
    getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(previousListElementKey), "previousListElement");
    getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("KP_" + previousListElementKey), "previousListElement");
  }

  /**
   * We override setModel to try to keep the selection across model changes, as we get multiple setModel calls.
   */
  @Override
  public void setModel(ListModel model) {
    final int oldSelectIndex  = getSelectedIndex();
    super.setModel(model);
    if (myImagesCache != null)
      myImagesCache.invalidateAll();
    myCellRenderers.clear();
    if (oldSelectIndex >= 0) {
      setSelectedIndex(oldSelectIndex);
      ensureIndexIsVisible(oldSelectIndex);
    }
  }

  /**
   * Update the size of thumbnails and redraw/relayout the list if necessary.
   */
  public void setThumbnailSize(Dimension thumbnailSize) {
    // JBDimension supports HiDPI
    myThumbnailSize = JBDimension.create(thumbnailSize);
    recomputeCellSize();
  }

  public void setDefaultAction(@NotNull Action action) {
    myDefaultAction = action;
  }

  private void recomputeCellSize() {
    Dimension cellSize = computeCellSize();
    setFixedCellWidth(cellSize.width);
    setFixedCellHeight(cellSize.height);
    invalidate();
    repaint();
  }

  /**
   * Compute the fixed size of each cell given the thumbnail size and the font size.
   */
  protected Dimension computeCellSize() {
    int preferredWidth = myThumbnailSize.width + myCellMargin.left + myCellMargin.right;
    int listWidth = getSize().width - getInsets().left - getInsets().right;
    int columnCount = listWidth / preferredWidth;
    int width = (columnCount == 0 ? preferredWidth : (listWidth / columnCount) - 1);
    int textHeight = getFont().getSize();
    int height = myThumbnailSize.height + myCellMargin.top + myCellMargin.bottom + 2 * textHeight;
    return new Dimension(width, height);
  }

  /**
   * Set cell margin value.
   */
  public void setCellMargin(@Nullable Insets cellMargin) {
    cellMargin = (cellMargin == null ? DEFAULT_CELL_MARGIN : cellMargin);
    if (!Objects.equal(cellMargin, myCellMargin)) {
      Insets oldCellMargin = myCellMargin;
      myCellMargin = cellMargin;
      recomputeCellSize();
      firePropertyChange("cellMargin", oldCellMargin, cellMargin);
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
  public void setImageProvider(@NotNull final Function<? super E, Image> imageProvider) {
    Function<? super E, Image> scaledImageProvider = new Function<E, Image> () {
      @Nullable
      @Override
      public Image apply(@Nullable E input) {
        Image img = imageProvider.apply(input);
        if (img == null) {
          return null;
        }
        BufferedImage image = ImageUtil.toBufferedImage(img);
        if (image.getHeight() <= 0 || image.getWidth() <= 0) {
          return null;
        }
        double xScale = (double) myThumbnailSize.width / image.getWidth();
        double yScale = (double) myThumbnailSize.height / image.getHeight();
        return ImageUtils.scale(image, xScale, yScale);
      }
    };
    CacheLoader<? super E, Optional<Image>> cacheLoader = CacheLoader.from(ToOptionalFunction.wrap(scaledImageProvider));
    myImagesCache = CacheBuilder.newBuilder().weakKeys().build(cacheLoader);
    repaint(getVisibleRect());
  }

  /**
   * Guava containers do not like <code>null</code> values. This function
   * wraps such values into {@link Optional}.
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

  public void setLabelProvider(@NotNull Function<? super E, String> labelProvider) {
    myLabelProvider = labelProvider;
  }

  public void setSelectedElement(E selectedElement) {
    setSelectedValue(selectedElement, true);
  }

  @Nullable
  public E getSelectedElement() {
    return (E)getSelectedValue();
  }

  @Nullable
  private Image getCellImage(E element) {
    try {
      Optional<Image> image = myImagesCache.get(element);
      return image.orNull();
    }
    catch (ExecutionException e) {
      Logger.getInstance(getClass()).error(e);
      return null;
    }
  }

  @Nullable
  private String getCellLabel(E element) {
    return myLabelProvider.apply(element);
  }

  @Override
  public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
    // 10 pixels, so that mouse wheel/track pad scrolling is smoother.
    return JBUI.scale(10);
  }

  @Override
  public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
    return computeCellSize().height;
  }

  class GalleryCellRenderer implements ListCellRenderer
  {
    /**
     * From <a href="http://java.sun.com/javase/6/docs/api/javax/swing/ListCellRenderer.html">ListCellRenderer</a>
     *
     * Return a component that has been configured to display the specified value.
     * That component's paint method is then called to "render" the cell.
     * If it is necessary to compute the dimensions of a list because the list cells do not have a fixed size,
     * this method is called to generate a component on which getPreferredSize can be invoked.
     *
     * jlist - the jlist we're painting
     * value - the value returned by list.getModel().getElementAt(index).
     * cellIndex - the cell index
     * isSelected - true if the specified cell is currently selected
     * cellHasFocus - true if the cell has focus
     */
    @Override
    public Component getListCellRendererComponent(JList jlist, Object value, int cellIndex, boolean isSelected, boolean cellHasFocus) {
      final E element = (E)value;
      CellRenderer renderer = myCellRenderers.get(element);
      if (renderer == null) {
        renderer = createCellRendererComponent(element);
        myCellRenderers.put(element, renderer);
      }
      renderer.setAppearance(isSelected, cellHasFocus);
      return renderer.getComponent();
    }

    public CellRenderer createCellRendererComponent(E element) {
      final Image image = ASGallery.this.getCellImage(element);
      final String label = ASGallery.this.getCellLabel(element);

      if (image == null) {
        return new TextOnlyCellRenderer(label);
      }
      else {
        return new TextAndImageCellRenderer(getFont(), label, image);
      }
    }
  }

  private interface CellRenderer {
    void setAppearance(boolean isSelected, boolean cellHasFocus);
    Component getComponent();
  }

  private static abstract class AbstractCellRenderer implements CellRenderer {
    private boolean myIsInitialized;
    protected boolean myIsSelected;
    protected boolean myCellHasFocus;

    @Override
    public abstract Component getComponent();

    @Override
    public void setAppearance(boolean isSelected, boolean cellHasFocus){
      if (myIsInitialized && isSelected == myIsSelected && cellHasFocus == myCellHasFocus)
        return;
      myIsInitialized = true;
      myIsSelected = isSelected;
      myCellHasFocus = cellHasFocus;
      updateAppearance();
    }

    protected abstract void updateAppearance();

    protected void setSelectionBorder(JComponent component) {
      if (myIsSelected) {
        component.setBorder(new LineBorder(UIUtil.getTreeSelectionBackground(myCellHasFocus)));
      } else {
        component.setBorder(null);
      }
    }
  }

  private static class TextOnlyCellRenderer extends AbstractCellRenderer {
    private JLabel myLabel;

    public TextOnlyCellRenderer(String label) {
      // If no image, create a single JLabel for the whole cell.
      JLabel jlabel;
      jlabel = new JLabel(label);
      jlabel.setHorizontalAlignment(SwingConstants.CENTER);
      jlabel.setForeground(UIUtil.getTreeForeground());
      jlabel.setFocusable(true);
      myLabel = jlabel;
    }

    @Override
    public void updateAppearance() {
      setSelectionBorder(myLabel);
    }

    @Override
    public Component getComponent() {
      return myLabel;
    }
  }

  private static class TextAndImageCellRenderer extends AbstractCellRenderer {
    private JPanel myPanel;
    private JLabel myLabel;

    public TextAndImageCellRenderer(Font font, String label, Image image) {
      // If there is an image, create a panel with the image at the top and
      // the label at the bottom. Also take care of selection highlighting.
      // +-Panel-------+
      // |             |
      // | (image,     |
      // |   centered) |
      // |             |
      // | (label,     |
      // |   bottom)   |
      // +-------------+
      ImageIcon icon = new ImageIcon(image, label);
      JLabel imageLabel = new JLabel(icon);

      JLabel textLabel = new JLabel(label, SwingConstants.CENTER);
      textLabel.setHorizontalTextPosition(SwingConstants.CENTER);
      int hPadding = font.getSize() / 3;
      Border padding = BorderFactory.createEmptyBorder(hPadding, 0, hPadding, 0);
      textLabel.setBorder(padding);

      JPanel panel = new JPanel();
      panel.setFocusable(true);
      panel.setOpaque(false); // so that background is from parent window
      panel.setLayout(new BorderLayout());
      panel.add(imageLabel);
      panel.add(textLabel, BorderLayout.PAGE_END);
      panel.getAccessibleContext().setAccessibleName(textLabel.getAccessibleContext().getAccessibleName());
      panel.getAccessibleContext().setAccessibleDescription(textLabel.getAccessibleContext().getAccessibleDescription());

      myPanel = panel;
      myLabel = textLabel;
    }

    @Override
    public void updateAppearance() {
      setSelectionBorder(myPanel);
      setLabelBackground(myLabel);
      setLabelForeground(myLabel);
    }

    @Override
    public Component getComponent() {
      return myPanel;
    }

    public void setLabelBackground(JLabel label) {
      if (myIsSelected) {
        label.setBackground(UIUtil.getTreeSelectionBackground(myCellHasFocus));
        label.setOpaque(true);
      } else {
        label.setBackground(null);
        label.setOpaque(false);
      }
    }

    public void setLabelForeground(JLabel label) {
      final Color labelForeground;
      if (myIsSelected && myCellHasFocus) {
        labelForeground = UIUtil.getTreeSelectionForeground();
      }
      else {
        labelForeground = UIUtil.getTreeForeground();
      }
      label.setForeground(labelForeground);
    }
  }
}
