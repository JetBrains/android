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

import com.google.common.collect.Lists;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntObjectHashMap;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleStateSet;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;
import java.lang.IllegalArgumentException;
import java.util.*;
import java.util.List;

@SuppressWarnings({"NonPrivateFieldAccessedInSynchronizedContext", "FieldAccessedSynchronizedAndUnsynchronized", "UnusedDeclaration"})
public class WrapAwareColoredComponent extends JComponent implements Accessible, ColoredTextContainer {
  private static final boolean isOracleRetina = UIUtil.isRetina() && SystemInfo.isOracleJvm;

  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.SimpleColoredComponent");

  public static final Color SHADOW_COLOR                  = new JBColor(new Color(250, 250, 250, 140), Gray._0.withAlpha(50));
  public static final Color STYLE_SEARCH_MATCH_BACKGROUND = SHADOW_COLOR; //api compatibility
  public static final int   FRAGMENT_ICON                 = -2;


  @NotNull private final List<String>                     myFragments      = Lists.newArrayListWithCapacity(3);
  @NotNull private final List<SimpleTextAttributes>       myAttributes     = Lists.newArrayListWithCapacity(3);
  @NotNull private final TIntObjectHashMap<TIntArrayList> myBreakOffsets   = new TIntObjectHashMap<TIntArrayList>();
  @NotNull private final TIntIntHashMap                   myLineHeights    = new TIntIntHashMap();
  @NotNull private final Dimension                        myTextDimensions = new Dimension();
  @NotNull private final WrapsAwareTextHelper             myTextHelper     = new WrapsAwareTextHelper(this);

  @NotNull private final String myLineBreakMarker;

  /**
   * Internal padding
   */
  @NotNull private Insets myIpad = new Insets(1, 2, 1, 2);

  /**
   * This is the border around the text. For example, text can have a border
   * if the component represents a selected item in a focused JList.
   * Border can be <code>null</code>.
   */
  @Nullable private Border myBorder = new MyBorder();

  @Nullable private List<Object> myFragmentTags;

  /**
   * Component's icon. It can be <code>null</code>.
   */
  @Nullable private Icon myIcon;

  /**
   * Holds value of the last width limit used for {@link #computeTextDimension(Font, boolean, int) calculating text dimensions}.
   * <p/>
   * E.g. we can safely use {@link #myTextDimensions cached text dimensions} if value of this field is not null and equals
   * to the {@link #getWidth() current width}.
   */
  @Nullable private Integer myLastUsedWidthLimit;

  /**
   * Gap between icon and text. It is used only if icon is defined.
   */
  protected int myIconTextGap = 2;
  /**
   * Defines whether the focus border around the text is painted or not.
   * For example, text can have a border if the component represents a selected item
   * in focused JList.
   */
  private boolean myPaintFocusBorder;
  /**
   * Defines whether the focus border around the text extends to icon or not
   */
  private boolean myFocusBorderAroundIcon;

  private int myMainTextLastIndex = -1;

  private final TIntIntHashMap myFixedWidths = new TIntIntHashMap(10);

  @JdkConstants.HorizontalAlignment private int myTextAlign = SwingConstants.LEFT;

  private boolean myIconOpaque = false;

  private boolean myAutoInvalidate = !(this instanceof TreeCellRenderer);

  private final AccessibleContext myContext = new MyAccessibleContext();

  private boolean myIconOnTheRight = false;
  private boolean myTransparentIconBackground;
  private boolean myWrapText;

  public WrapAwareColoredComponent() {
    setOpaque(true);
    WrapsAwareTextHelper.appendLineBreak(myFragments);
    myLineBreakMarker = myFragments.get(0);
    myFragments.clear();
  }

  @NotNull
  public ColoredIterator iterator() {
    return new MyIterator();
  }

  public boolean isIconOnTheRight() {
    return myIconOnTheRight;
  }

  public void setIconOnTheRight(boolean iconOnTheRight) {
    myIconOnTheRight = iconOnTheRight;
  }

  @NotNull
  public WrapAwareColoredComponent appendLineBreak() {
    WrapsAwareTextHelper.appendLineBreak(myFragments);
    myAttributes.add(SimpleTextAttributes.REGULAR_ATTRIBUTES);
    myMainTextLastIndex = myFragments.size() - 1;
    resetTextLayoutCache();
    return this;
  }

  @NotNull
  public final WrapAwareColoredComponent append(@NotNull String fragment) {
    append(fragment, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    return this;
  }

  /**
   * Appends string fragments to existing ones. Appended string
   * will have specified <code>attributes</code>.
   * @param fragment text fragment
   * @param attributes text attributes
   */
  @Override
  public final void append(@NotNull final String fragment, @NotNull final SimpleTextAttributes attributes) {
    append(fragment, attributes, myMainTextLastIndex < 0);
  }

  /**
   * Appends string fragments to existing ones. Appended string
   * will have specified <code>attributes</code>.
   * @param fragment text fragment
   * @param attributes text attributes
   * @param isMainText main text of not
   */
  public void append(@NotNull final String fragment, @NotNull final SimpleTextAttributes attributes, boolean isMainText) {
    _append(fragment, attributes, isMainText);
    revalidateAndRepaint();
  }

  private synchronized void _append(@NotNull final String fragment, @NotNull final SimpleTextAttributes attributes, boolean isMainText) {
    myFragments.add(fragment);
    myAttributes.add(attributes);
    if (isMainText) {
      myMainTextLastIndex = myFragments.size() - 1;
    }
    resetTextLayoutCache();
  }

  private void revalidateAndRepaint() {
    if (myAutoInvalidate) {
      revalidate();
    }

    repaint();
  }

  @Override
  public void append(@NotNull final String fragment, @NotNull final SimpleTextAttributes attributes, @Nullable Object tag) {
    _append(fragment, attributes, tag);
    revalidateAndRepaint();
  }

  private synchronized void _append(@NotNull String fragment, @NotNull SimpleTextAttributes attributes, @Nullable Object tag) {
    append(fragment, attributes);
    if (myFragmentTags == null) {
      myFragmentTags = new ArrayList<Object>();
    }
    while (myFragmentTags.size() < myFragments.size() - 1) {
      myFragmentTags.add(null);
    }
    myFragmentTags.add(tag);
  }

  public synchronized void appendFixedTextFragmentWidth(int width) {
    final int alignIndex = myFragments.size()-1;
    myFixedWidths.put(alignIndex, width);
  }

  public void setTextAlign(@JdkConstants.HorizontalAlignment int align) {
    myTextAlign = align;
  }

  /**
   * Clear all special attributes of <code>SimpleColoredComponent</code>.
   * They are icon, text fragments and their attributes, "paint focus border".
   */
  public void clear() {
    _clear();
    revalidateAndRepaint();
  }

  private synchronized void _clear() {
    myIcon = null;
    myPaintFocusBorder = false;
    myFragments.clear();
    myAttributes.clear();
    myFragmentTags = null;
    myMainTextLastIndex = -1;
    myFixedWidths.clear();
    resetTextLayoutCache();
  }

  public void resetTextLayoutCache() {
    myLastUsedWidthLimit = null;
    myBreakOffsets.clear();
  }

  /**
   * @return component's icon. This method returns <code>null</code>
   *         if there is no icon.
   */
  @Nullable
  public final Icon getIcon() {
    return myIcon;
  }

  /**
   * Sets a new component icon
   * @param icon icon
   */
  @Override
  public final void setIcon(@Nullable final Icon icon) {
    myIcon = icon;
    revalidateAndRepaint();
  }

  /**
   * @return "leave" (internal) internal paddings of the component
   */
  @NotNull
  public Insets getIpad() {
    return myIpad;
  }

  /**
   * Sets specified internal paddings
   * @param ipad insets
   */
  public void setIpad(@NotNull Insets ipad) {
    myIpad = ipad;

    revalidateAndRepaint();
  }

  /**
   * @return gap between icon and text
   */
  public int getIconTextGap() {
    return myIconTextGap;
  }

  /**
   * Sets a new gap between icon and text
   *
   * @param iconTextGap the gap between text and icon
   * @throws IllegalArgumentException
   *          if the <code>iconTextGap</code>
   *          has a negative value
   */
  public void setIconTextGap(final int iconTextGap) {
    if (iconTextGap < 0) {
      throw new IllegalArgumentException("wrong iconTextGap: " + iconTextGap);
    }
    myIconTextGap = iconTextGap;

    revalidateAndRepaint();
  }

  @Nullable
  public Border getMyBorder() {
    return myBorder;
  }

  public void setMyBorder(@Nullable Border border) {
    myBorder = border;
  }

  /**
   * Sets whether focus border is painted or not
   * @param paintFocusBorder <code>true</code> or <code>false</code>
   */
  protected final void setPaintFocusBorder(final boolean paintFocusBorder) {
    myPaintFocusBorder = paintFocusBorder;

    repaint();
  }

  /**
   * Sets whether focus border extends to icon or not. If so then
   * component also extends the selection.
   * @param focusBorderAroundIcon <code>true</code> or <code>false</code>
   */
  protected final void setFocusBorderAroundIcon(final boolean focusBorderAroundIcon) {
    myFocusBorderAroundIcon = focusBorderAroundIcon;

    repaint();
  }

  public boolean isIconOpaque() {
    return myIconOpaque;
  }

  public void setIconOpaque(final boolean iconOpaque) {
    myIconOpaque = iconOpaque;

    repaint();
  }

  @Override
  @NotNull
  public Dimension getPreferredSize() {
    return computePreferredSize(false);

  }

  @Override
  @NotNull
  public Dimension getMinimumSize() {
    return computePreferredSize(false);
  }

  @Nullable
  public synchronized Object getFragmentTag(int index) {
    if (myFragmentTags != null && index < myFragmentTags.size()) {
      return myFragmentTags.get(index);
    }
    return null;
  }

  @NotNull
  public final synchronized Dimension computePreferredSize(final boolean mainTextOnly) {
    // Calculate width
    int width = myIpad.left;

    if (myIcon != null) {
      width += myIcon.getIconWidth() + myIconTextGap;
    }

    final Insets borderInsets = myBorder != null ? myBorder.getBorderInsets(this) : new Insets(0, 0, 0, 0);
    width += borderInsets.left;

    Font font = getFont();
    if (font == null) {
      font = UIUtil.getLabelFont();
    }

    LOG.assertTrue(font != null);

    int height = myIpad.top + myIpad.bottom;
    width += myIpad.right + borderInsets.right;
    // Take into account that the component itself can have a border
    final Insets insets = getInsets();
    if (insets != null) {
      width += insets.left + insets.right;
      height += insets.top + insets.bottom;
    }

    if (isOracleRetina) {
      width++; //todo[kb] remove when IDEA-108760 will be fixed
    }
    assert font != null;
    Dimension textDimension = computeTextDimension(font, mainTextOnly, myWrapText ? getWidth() - width : 0);
    width += textDimension.width;

    int textHeight = textDimension.height;
    textHeight += borderInsets.top + borderInsets.bottom;

    if (myIcon != null) {
      height += Math.max(myIcon.getIconHeight(), textHeight);
    }
    else {
      height += textHeight;
    }

    return new Dimension(width, height);
  }

  @NotNull
  private Dimension computeTextDimension(@NotNull Font font, final boolean mainTextOnly, int widthLimit) {
    if (myLastUsedWidthLimit != null && widthLimit == myLastUsedWidthLimit) {
      return myTextDimensions;
    }
    final List<String> fragmentsToUse;
    final List<SimpleTextAttributes> attributesToUse;
    if (mainTextOnly && myMainTextLastIndex >= 0 && myMainTextLastIndex < myFragments.size() - 1) {
      fragmentsToUse = myFragments.subList(0, myMainTextLastIndex);
      attributesToUse = myAttributes.subList(0, myMainTextLastIndex);
    }
    else {
      fragmentsToUse = myFragments;
      attributesToUse = myAttributes;
    }
    myBreakOffsets.clear();
    myLineHeights.clear();
    myTextHelper.wrap(fragmentsToUse, attributesToUse, font, myFixedWidths, widthLimit, myTextDimensions, myBreakOffsets, myLineHeights);
    myLastUsedWidthLimit = widthLimit;
    return myTextDimensions;
  }

  /**
   * Returns the index of text fragment at the specified X offset.
   *
   * @param x the offset
   * @return the index of the fragment, {@link #FRAGMENT_ICON} if the icon is at the offset, or -1 if nothing is there.
   */
  public int findFragmentAt(int x, int y) {
    // Make sure text wraps are properly calculated
    computePreferredSize(false);

    int curX = myIpad.left;
    if (myIcon != null) {
      final int iconStartX;
      if (myIconOnTheRight) {
        iconStartX = curX + myTextDimensions.width + myIconTextGap;
      }
      else {
        iconStartX = curX;
        curX += myIcon.getIconWidth() + myIconTextGap;
      }
      if (x >= iconStartX && x < iconStartX + myIcon.getIconWidth()) {
        return FRAGMENT_ICON;
      }
    }

    if (x - curX >= 0 && x - curX < myTextDimensions.width && y >= 0 && y <= myTextDimensions.height) {
      return myTextHelper.mapFragment(myFragments, myAttributes, myFixedWidths, myBreakOffsets, myLineHeights, getFont(), x - curX, y);
    }
    else {
      return -1;
    }
  }

  @Nullable
  public Object getFragmentTagAt(int x, int y) {
    int index = findFragmentAt(x, y);
    return index < 0 ? null : getFragmentTag(index);
  }

  @NotNull
  protected JLabel formatToLabel(@NotNull JLabel label) {
    label.setIcon(myIcon);

    if (!myFragments.isEmpty()) {
      final StringBuilder text = new StringBuilder();
      text.append("<html><body style=\"white-space:nowrap\">");

      for (int i = 0; i < myFragments.size(); i++) {
        final String fragment = myFragments.get(i);
        final SimpleTextAttributes attributes = myAttributes.get(i);
        final Object tag = getFragmentTag(i);
        if (tag instanceof BrowserLauncherTag) {
          formatLink(text, fragment, attributes, ((BrowserLauncherTag)tag).myUrl);
        }
        else {
          formatText(text, fragment, attributes);
        }
      }

      text.append("</body></html>");
      label.setText(text.toString());
    }

    return label;
  }

  static void formatText(@NotNull StringBuilder builder, @NotNull String fragment, @NotNull SimpleTextAttributes attributes) {
    if (!fragment.isEmpty()) {
      builder.append("<span");
      formatStyle(builder, attributes);
      builder.append('>').append(convertFragment(fragment)).append("</span>");
    }
  }

  static void formatLink(@NotNull StringBuilder builder, @NotNull String fragment, @NotNull SimpleTextAttributes attributes, @NotNull String url) {
    if (!fragment.isEmpty()) {
      builder.append("<a href=\"").append(StringUtil.replace(url, "\"", "%22")).append("\"");
      formatStyle(builder, attributes);
      builder.append('>').append(convertFragment(fragment)).append("</a>");
    }
  }

  @NotNull
  private static String convertFragment(@NotNull String fragment) {
    return StringUtil.escapeXml(fragment).replaceAll("\\\\n", "<br>");
  }

  private static void formatStyle(final StringBuilder builder, final SimpleTextAttributes attributes) {
    final Color fgColor = attributes.getFgColor();
    final Color bgColor = attributes.getBgColor();
    final int style = attributes.getStyle();

    final int pos = builder.length();
    if (fgColor != null) {
      builder.append("color:#").append(Integer.toString(fgColor.getRGB() & 0xFFFFFF, 16)).append(';');
    }
    if (bgColor != null) {
      builder.append("background-color:#").append(Integer.toString(bgColor.getRGB() & 0xFFFFFF, 16)).append(';');
    }
    if ((style & SimpleTextAttributes.STYLE_BOLD) != 0) {
      builder.append("font-weight:bold;");
    }
    if ((style & SimpleTextAttributes.STYLE_ITALIC) != 0) {
      builder.append("font-style:italic;");
    }
    if ((style & SimpleTextAttributes.STYLE_UNDERLINE) != 0) {
      builder.append("text-decoration:underline;");
    }
    else if ((style & SimpleTextAttributes.STYLE_STRIKEOUT) != 0) {
      builder.append("text-decoration:line-through;");
    }
    if (builder.length() > pos) {
      builder.insert(pos, " style=\"");
      builder.append('"');
    }
  }

  @Override
  protected void paintComponent(@NotNull final Graphics g) {
    try {
      _doPaint(g);
    }
    catch (RuntimeException e) {
      LOG.error(logSwingPath(), e);
      throw e;
    }
  }

  private synchronized void _doPaint(@NotNull final Graphics g) {
    checkCanPaint(g);
    doPaint((Graphics2D)g);
  }

  protected void doPaint(@NotNull final Graphics2D g) {
    int offset = 0;
    final Icon icon = myIcon; // guard against concurrent modification (IDEADEV-12635)
    if (icon != null && !myIconOnTheRight) {
      doPaintIcon(g, icon, 0);
      offset += myIpad.left + icon.getIconWidth() + myIconTextGap;
    }

    doPaintTextBackground(g, offset);
    offset = doPaintText(g, offset, myFocusBorderAroundIcon || icon == null);
    if (icon != null && myIconOnTheRight) {
      doPaintIcon(g, icon, offset);
    }
  }

  private void doPaintTextBackground(@NotNull Graphics2D g, int offset) {
    if (isOpaque() || shouldDrawBackground()) {
      paintBackground(g, offset, getWidth() - offset, getHeight());
    }
  }

  protected void paintBackground(@NotNull Graphics2D g, int x, int width, int height) {
    g.setColor(getBackground());
    g.fillRect(x, 0, width, height);
  }

  protected void doPaintIcon(@NotNull Graphics2D g, @NotNull Icon icon, int offset) {
    final Container parent = getParent();
    Color iconBackgroundColor = null;
    if ((isOpaque() || isIconOpaque()) && !isTransparentIconBackground()) {
      if (parent != null && !myFocusBorderAroundIcon && !UIUtil.isFullRowSelectionLAF()) {
        iconBackgroundColor = parent.getBackground();
      }
      else {
        iconBackgroundColor = getBackground();
      }
    }

    if (iconBackgroundColor != null) {
      g.setColor(iconBackgroundColor);
      g.fillRect(offset, 0, icon.getIconWidth() + myIpad.left + myIconTextGap, getHeight());
    }

    paintIcon(g, icon, offset + myIpad.left);
  }

  protected int doPaintText(@NotNull Graphics2D g, int offset, boolean focusAroundIcon) {
    // Force using right text dimensions.
    computePreferredSize(false);

    // If there is no icon, then we have to add left internal padding
    if (offset == 0) {
      offset = myIpad.left;
    }

    int textStart = offset;
    if (myBorder != null) {
      offset += myBorder.getBorderInsets(this).left;
    }

    final List<Object[]> searchMatches = new ArrayList<Object[]>();

    UIUtil.applyRenderingHints(g);
    applyAdditionalHints(g);
    final Font ownFont = getFont();
    if (ownFont != null) {
      offset += computeTextAlignShift(ownFont);
    }
    int baseSize = ownFont != null ? ownFont.getSize() : g.getFont().getSize();
    boolean wasSmaller = false;
    int x = offset;
    int y = 0;
    int line = 0;
    boolean beforePaintTextCalled = false;
    for (int i = 0; i < myFragments.size(); i++) {
      final SimpleTextAttributes attributes = myAttributes.get(i);

      Font font = g.getFont();
      boolean isSmaller = attributes.isSmaller();
      if (font.getStyle() != attributes.getFontStyle() || isSmaller != wasSmaller) { // derive font only if it is necessary
        font = font.deriveFont(attributes.getFontStyle(), isSmaller ? UIUtil.getFontSize(UIUtil.FontSize.SMALL) : baseSize);
      }
      wasSmaller = isSmaller;

      g.setFont(font);
      final FontMetrics metrics = g.getFontMetrics(font);
      int lineHeight = myLineHeights.get(line++);
      if (lineHeight <= 0) {
        lineHeight = metrics.getHeight();
      }

      final String wholeFragmentTextToDraw = myFragments.get(i);
      if (myLineBreakMarker.equals(wholeFragmentTextToDraw)) {
        y += lineHeight;
        x = offset;
        continue;
      }

      Color color = attributes.getFgColor();
      if (color == null) { // in case if color is not defined we have to get foreground color from Swing hierarchy
        color = getForeground();
      }
      if (!isEnabled()) {
        color = UIUtil.getInactiveTextColor();
      }
      g.setColor(color);

      for (TextRange range = nextFragmentLineRange(i, null); range != null; range = nextFragmentLineRange(i, range)) {
        if (range.getStartOffset() > 0) { // This is not the first fragment's part, i.e. it was long enough to be split into multiple lines.
          lineHeight = myLineHeights.get(++line);
          if (lineHeight <= 0) {
            lineHeight = metrics.getHeight();
          }
          x = offset;
          y += lineHeight;
        }
        String textToDraw = wholeFragmentTextToDraw.substring(range.getStartOffset(), range.getEndOffset());
        final int textWidth = isOracleRetina ? GraphicsUtil.stringWidth(textToDraw, font) : metrics.stringWidth(textToDraw);
        final int textBaseline = y + getTextBaseLine(metrics, lineHeight);
        if (!beforePaintTextCalled) {
          beforePaintText(g, x, textBaseline);
        }

        final Color bgColor = attributes.isSearchMatch() ? null : attributes.getBgColor();
        if ((attributes.isOpaque() || isOpaque()) && bgColor != null) {
          g.setColor(bgColor);
          g.fillRect(x, y, textWidth, lineHeight);
        }

        if (!attributes.isSearchMatch()) {
          if (shouldDrawMacShadow()) {
            g.setColor(SHADOW_COLOR);
            g.drawString(textToDraw, x, textBaseline + 1);
          }

          if (shouldDrawDimmed()) {
            color = ColorUtil.dimmer(color);
          }

          g.setColor(color);
          g.drawString(textToDraw, x, textBaseline);
        }

        // 1. Strikeout effect
        if (attributes.isStrikeout()) {
          final int strikeOutAt = textBaseline + (metrics.getDescent() - metrics.getAscent()) / 2;
          UIUtil.drawLine(g, x, strikeOutAt, x + textWidth, strikeOutAt);
        }
        // 2. Waved effect
        if (attributes.isWaved()) {
          if (attributes.getWaveColor() != null) {
            g.setColor(attributes.getWaveColor());
          }
          final int wavedAt = textBaseline + 1;
          for (int waveX = x; waveX <= x + textWidth; waveX += 4) {
            UIUtil.drawLine(g, waveX, wavedAt, waveX + 2, wavedAt + 2);
            UIUtil.drawLine(g, waveX + 3, wavedAt + 1, waveX + 4, wavedAt);
          }
        }
        // 3. Underline
        if (attributes.isUnderline()) {
          final int underlineAt = textBaseline + 1;
          UIUtil.drawLine(g, x, underlineAt, x + textWidth, underlineAt);
        }
        // 4. Bold Dotted Line
        if (attributes.isBoldDottedLine()) {
          final int dottedAt = SystemInfo.isMac ? textBaseline : textBaseline + 1;
          final Color lineColor = attributes.getWaveColor();
          UIUtil.drawBoldDottedLine(g, x, x + textWidth, dottedAt, bgColor, lineColor, isOpaque());
        }

        if (attributes.isSearchMatch()) {
          searchMatches.add(new Object[]{x, x + textWidth, textBaseline, textToDraw, g.getFont(), lineHeight});
        }

        final int fixedWidth = myFixedWidths.get(i);
        if (fixedWidth > 0 && textWidth < fixedWidth) {
          x += fixedWidth;
        }
        else {
          x += textWidth;
        }
      }
    }

    // Paint focus border around the text and icon (if necessary)
    if (myPaintFocusBorder && myBorder != null) {
      if (focusAroundIcon) {
        myBorder.paintBorder(this, g, 0, 0, getWidth(), getHeight());
      }
      else {
        myBorder.paintBorder(this, g, textStart, 0, getWidth() - textStart, getHeight());
      }
    }

    // draw search matches after all
    for (final Object[] info : searchMatches) {
      UIUtil.drawSearchMatch(g, (Integer)info[0], (Integer)info[1], (Integer)info[5]);
      g.setFont((Font)info[4]);

      if (shouldDrawMacShadow()) {
        g.setColor(SHADOW_COLOR);
        g.drawString((String)info[3], (Integer)info[0], (Integer)info[2] + 1);
      }

      g.setColor(new JBColor(Gray._50, Gray._0));
      g.drawString((String)info[3], (Integer)info[0], (Integer)info[2]);
    }
    return offset;
  }

  protected void beforePaintText(@NotNull Graphics g, int x, int textBaseLine) {
  }

  /**
   * There is a possible case that particular text fragment is displayed at more than one line. It's assumed that information about
   * such inner fragment line break offsets is stored at the {@link #myBreakOffsets} field.
   * <p/>
   * This helper method assumes to be used during iterative fragment parts processing, i.e. it receives a text range within the target
   * fragment (identified by it's index at the {@link #myFragments} collection) and returns text range for the next part of the fragment
   * to be drawn
   *
   * @param fragmentIndex              target fragment's index within the {@link #myFragments fragments collection}
   * @param previousFragmentLineRange  text range for the fragment's part used the last time (<code>null</code> value indicates that
   *                                   the fragment hasn't been used yet)
   * @return                           text fragment for the fragment's part to be shown at new line (if any); <code>null</code> as an
   *                                   indication that the target fragment has been completely processed
   */
  @Nullable
  private TextRange nextFragmentLineRange(int fragmentIndex, @Nullable TextRange previousFragmentLineRange) {
    TIntArrayList breakOffsets = myBreakOffsets.get(fragmentIndex);
    String fragmentText = myFragments.get(fragmentIndex);
    if (breakOffsets == null || breakOffsets.isEmpty()) {
      if (previousFragmentLineRange == null) {
        return TextRange.allOf(fragmentText);
      }
      else {
        return null;
      }
    }

    if (previousFragmentLineRange == null) {
      return TextRange.create(0, breakOffsets.get(0));
    }

    for (int i = 0; i < breakOffsets.size(); i++) {
      if (breakOffsets.get(i) == previousFragmentLineRange.getEndOffset()) {
        if (i < breakOffsets.size() - 1) {
          return TextRange.create(previousFragmentLineRange.getEndOffset(), breakOffsets.get(i + 1));
        }
        else {
          return TextRange.create(previousFragmentLineRange.getEndOffset(), fragmentText.length());
        }
      }
    }
    return null;
  }

  private int computeTextAlignShift(@NotNull Font font) {
    if (myTextAlign == SwingConstants.LEFT || myTextAlign == SwingConstants.LEADING) {
      return 0;
    }

    int componentWidth = getSize().width;
    int excessiveWidth = componentWidth - computePreferredSize(false).width;
    if (excessiveWidth <= 0) {
      return 0;
    }

    Dimension textDimension = computeTextDimension(font, false, myWrapText ? getWidth() : 0);
    if (myTextAlign == SwingConstants.CENTER) {
      return excessiveWidth / 2;
    }
    else if (myTextAlign == SwingConstants.RIGHT || myTextAlign == SwingConstants.TRAILING) {
      return excessiveWidth;
    }
    return 0;
  }

  protected boolean shouldDrawMacShadow() {
    return false;
  }

  protected boolean shouldDrawDimmed() {
    return false;
  }

  protected boolean shouldDrawBackground() {
    return false;
  }

  protected void paintIcon(@NotNull Graphics g, @NotNull Icon icon, int offset) {
    final int y;
    if (myLineHeights.size() <= 1) {
      // Draw icon center-aligned in case one ore less text lines.
      y = (getHeight() - icon.getIconHeight()) / 2;
    }
    else {
      // Draw icon at the first text line instead.
      if (icon.getIconHeight() > myLineHeights.get(0)) {
        y = myIpad.top;
      }
      else {
        y = myIpad.top + (myLineHeights.get(0) - icon.getIconHeight()) / 2;
      }
    }
    icon.paintIcon(this, g, offset, y);
  }

  protected void applyAdditionalHints(@NotNull Graphics g) {
  }

  @Override
  public int getBaseline(int width, int height) {
    super.getBaseline(width, height);
    return getTextBaseLine(getFontMetrics(getFont()), height);
  }

  public boolean isTransparentIconBackground() {
    return myTransparentIconBackground;
  }

  public void setTransparentIconBackground(boolean transparentIconBackground) {
    myTransparentIconBackground = transparentIconBackground;
  }

  /**
   * Instructs current component to display {@link #append(String) encapsulated text} in a way to avoid it to go beyond the horizontal
   * visible area.
   * <p/>
   * Example:
   * <pre>
   *   Say, we have a situation like below:
   *
   *       |       |
   *       |       |&lt;-- visible area
   *       |       |
   *       |1234567|89
   *       |       |
   *
   *   Wrapped text is shown as follows then:
   *
   *       |       |
   *       |1234567|
   *       |89     |
   *       |       |
   * </pre>
   *
   * @param wrapText  a flag which indicates if target text shown by the current control should be wrapped
   */
  public void setWrapText(boolean wrapText) {
    if (myWrapText != wrapText) {
      resetTextLayoutCache();
    }
    myWrapText = wrapText;
  }

  public static int getTextBaseLine(@NotNull FontMetrics metrics, final int height) {
    return (height - metrics.getHeight()) / 2 + metrics.getAscent();
  }

  private static void checkCanPaint(@NotNull Graphics g) {
    if (UIUtil.isPrinting(g)) return;

    /* wtf??
    if (!isDisplayable()) {
      LOG.assertTrue(false, logSwingPath());
    }
    */
    final Application application = ApplicationManager.getApplication();
    if (application != null) {
      application.assertIsDispatchThread();
    }
    else if (!SwingUtilities.isEventDispatchThread()) {
      throw new RuntimeException(Thread.currentThread().toString());
    }
  }

  @NotNull
  private String logSwingPath() {
    //noinspection HardCodedStringLiteral
    final StringBuilder buffer = new StringBuilder("Components hierarchy:\n");
    for (Container c = this; c != null; c = c.getParent()) {
      buffer.append('\n');
      buffer.append(c);
    }
    return buffer.toString();
  }

  protected void setBorderInsets(@NotNull Insets insets) {
    if (myBorder instanceof MyBorder) {
      ((MyBorder)myBorder).setInsets(insets);
    }

    revalidateAndRepaint();
  }

  private static final class MyBorder implements Border {
    @NotNull private Insets myInsets;

    public MyBorder() {
      myInsets = new Insets(1, 1, 1, 1);
    }

    public void setInsets(@NotNull final Insets insets) {
      myInsets = insets;
    }

    @Override
    public void paintBorder(final Component c, final Graphics g, final int x, final int y, final int width, final int height) {
      g.setColor(JBColor.BLACK);
      UIUtil.drawDottedRectangle(g, x, y, x + width - 1, y + height - 1);
    }

    @Override
    public Insets getBorderInsets(@NotNull final Component c) {
      return (Insets)myInsets.clone();
    }

    @Override
    public boolean isBorderOpaque() {
      return true;
    }
  }

  @NotNull
  public CharSequence getCharSequence(boolean mainOnly) {
    List<String> fragments = mainOnly && myMainTextLastIndex > -1 && myMainTextLastIndex + 1 < myFragments.size()?
                                       myFragments.subList(0, myMainTextLastIndex + 1) : myFragments;
    return StringUtil.join(fragments, "");
  }

  @NotNull
  @Override
  public String toString() {
    return getCharSequence(false).toString();
  }

  public void change(@NotNull Runnable runnable, boolean autoInvalidate) {
    boolean old = myAutoInvalidate;
    myAutoInvalidate = autoInvalidate;
    try {
      runnable.run();
    } finally {
      myAutoInvalidate = old;
    }
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    return myContext;
  }

  private static class MyAccessibleContext extends AccessibleContext {
    @Override
    public AccessibleRole getAccessibleRole() {
      return AccessibleRole.AWT_COMPONENT;
    }

    @Override
    public AccessibleStateSet getAccessibleStateSet() {
      return new AccessibleStateSet();
    }

    @Override
    public int getAccessibleIndexInParent() {
      return 0;
    }

    @Override
    public int getAccessibleChildrenCount() {
      return 0;
    }

    @Nullable
    @Override
    public Accessible getAccessibleChild(int i) {
      return null;
    }

    @Override
    public Locale getLocale() throws IllegalComponentStateException {
      return Locale.getDefault();
    }
  }

  public static class BrowserLauncherTag implements Runnable {
    private final String myUrl;

    public BrowserLauncherTag(@NotNull String url) {
      myUrl = url;
    }

    @Override
    public void run() {
      BrowserUtil.browse(myUrl);
    }
  }

  public interface ColoredIterator extends Iterator<String> {
    int getOffset();
    int getEndOffset();
    @NotNull
    String getFragment();
    @NotNull
    SimpleTextAttributes getTextAttributes();

    int split(int offset, @NotNull SimpleTextAttributes attributes);
  }

  private class MyIterator implements ColoredIterator {
    int myIndex = -1;
    int myOffset;
    int myEndOffset;

    @Override
    public int getOffset() {
      return myOffset;
    }

    @Override
    public int getEndOffset() {
      return myEndOffset;
    }

    @NotNull
    @Override
    public String getFragment() {
      return myFragments.get(myIndex);
    }

    @NotNull
    @Override
    public SimpleTextAttributes getTextAttributes() {
      return myAttributes.get(myIndex);
    }

    @Override
    public int split(int offset, @NotNull SimpleTextAttributes attributes) {
      if (offset < 0 || offset > myEndOffset - myOffset) {
        throw new IllegalArgumentException(offset + " is not within [0, " + (myEndOffset - myOffset) + "]");
      }
      if (offset == myEndOffset - myOffset) {   // replace
        myAttributes.set(myIndex, attributes);
      }
      else if (offset > 0) {   // split
        String text = getFragment();
        myFragments.set(myIndex, text.substring(0, offset));
        myAttributes.add(myIndex, attributes);
        myFragments.add(myIndex + 1, text.substring(offset));
        if (myFragmentTags != null && myFragmentTags.size() > myIndex) {
          myFragmentTags.add(myIndex, myFragments.get(myIndex));
        }
        myIndex ++;
      }
      myOffset += offset;
      return myOffset;
    }

    @Override
    public boolean hasNext() {
      return myIndex + 1 < myFragments.size();
    }

    @Override
    public String next() {
      myIndex ++;
      myOffset = myEndOffset;
      String text = getFragment();
      myEndOffset += text.length();
      return text;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
}
