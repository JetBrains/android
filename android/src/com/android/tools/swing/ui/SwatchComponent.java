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
package com.android.tools.swing.ui;

import com.android.tools.idea.editors.theme.ThemeEditorConstants;
import com.android.tools.swing.util.GraphicsUtil;
import com.google.common.collect.Lists;
import com.intellij.openapi.command.undo.UndoConstants;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.RoundedLineBorder;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.util.ui.JBUI;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static com.intellij.util.ui.GraphicsUtil.setupAAPainting;

/**
 * Component that displays a list of icons and a label
 */
public class SwatchComponent extends Box {
  /**
   * Padding used vertically and horizontally
   */
  private static final int PADDING = JBUI.scale(3);
  /**
   * Additional padding from the top for the value label. The text padding from the top will be PADDING + TEXT_PADDING
   */
  private static final int TEXT_PADDING = JBUI.scale(8);
  /**
   * Separation between states
   */
  private static final int ARC_SIZE = ThemeEditorConstants.ROUNDED_BORDER_ARC_SIZE;
  private static final Color DEFAULT_BORDER_COLOR = Gray._170;
  private static final Color WARNING_BORDER_COLOR = JBColor.ORANGE;

  public static final SwatchIcon WARNING_ICON = new SwatchIcon() {
    @Override
    public void paintSwatch(@NotNull Component c, @NotNull Graphics g, int x, int y, int w, int h) {
      Icon QUESTION_ICON = AndroidIcons.GreyQuestionMark;
      int horizontalMargin = (w + JBUI.scale(1) - QUESTION_ICON.getIconWidth()) / 2;
      int verticalMargin = (h + JBUI.scale(3) - QUESTION_ICON.getIconHeight()) / 2;
      QUESTION_ICON.paintIcon(c, g, x + horizontalMargin, y + verticalMargin);
    }
  };

  private final TextFieldWithAutoCompletion<String> myTextField;
  private final ClickableLabel mySwatchButton;
  private final List<ActionListener> myTextListeners = Lists.newArrayList();

  private Color myBorderColor;

  /**
   * Constructs a SwatchComponent with a maximum number of icons. If the number of icons is greater than maxIcons
   * the component will display a text with the number of icons left to display. When the user clicks that icon the
   * icons will expand until the user leaves the control area.
   */
  public SwatchComponent(@NotNull Project project) {
    super(BoxLayout.LINE_AXIS);
    setBorder(null);

    myBorderColor = DEFAULT_BORDER_COLOR;
    mySwatchButton = new ClickableLabel();
    //noinspection unchecked (EMPTY_COMPLETION doesn't need the generic type as it is empty)
    myTextField = new TextFieldWithAutoCompletion<String>(project, TextFieldWithAutoCompletion.EMPTY_COMPLETION, true, null) {
      @Override
      protected boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          ActionEvent event = new ActionEvent(myTextField, ActionEvent.ACTION_PERFORMED, null);
          for (ActionListener listener : myTextListeners) {
            listener.actionPerformed(event);
          }
          return true;
        }
        return false;
      }

      @Override
      protected void updateBorder(@NotNull EditorEx editor) {
        // Necessary to not have any border when not in a JTable
        editor.setBorder(null);
      }

      @Override
      public void removeNotify() {
        // The editor needs to be removed manually because it normally is removed by invokeLater, which may happen to late
        // and cause a crash when closing the project.
        Editor editor = getEditor();
        if (editor != null && !editor.isDisposed()) {
          EditorFactory.getInstance().releaseEditor(editor);
        }
        super.removeNotify();
      }
    };
    myTextField.setOneLineMode(true);

    final JPanel textFieldWrapper = new JPanel(new BorderLayout()) {
      @Override
      protected void paintComponent(Graphics graphics) {
        setupAAPainting(graphics);
        Graphics2D g = (Graphics2D)graphics;

        Shape savedClip = g.getClip();
        RoundRectangle2D clipRectangle = new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), ARC_SIZE, ARC_SIZE);

        g.clip(clipRectangle);
        super.paintComponent(g);
        g.setClip(savedClip);
      }
    };
    textFieldWrapper.setBorder(new RoundedLineBorder(myBorderColor, ARC_SIZE, 1) {
      @Override
      public Insets getBorderInsets(Component c) {
        return new Insets(0, 0, 0, 0);
      }
    });
    textFieldWrapper.setBackground(JBColor.WHITE);

    textFieldWrapper.add(Box.createVerticalStrut(TEXT_PADDING), BorderLayout.PAGE_START);
    textFieldWrapper.add(Box.createHorizontalStrut(TEXT_PADDING), BorderLayout.LINE_START);
    textFieldWrapper.add(myTextField, BorderLayout.CENTER);
    textFieldWrapper.add(Box.createHorizontalStrut(TEXT_PADDING), BorderLayout.LINE_END);
    textFieldWrapper.add(Box.createVerticalStrut(TEXT_PADDING), BorderLayout.PAGE_END);

    add(mySwatchButton);
    add(Box.createHorizontalStrut(PADDING));
    add(textFieldWrapper);
  }

  public void setSwatchIcon(@NotNull SwatchIcon icon) {
    int iconSize = getMinimumSize().height;
    icon.setSize(iconSize);
    icon.setBackgroundColor(getBackground());
    mySwatchButton.setIcon(icon);
  }

  public void showStack(boolean show) {
    ((SwatchIcon)mySwatchButton.getIcon()).setIsStack(show);
  }

  public void setWarningBorder(boolean isWarning) {
    myBorderColor = isWarning ? WARNING_BORDER_COLOR : DEFAULT_BORDER_COLOR;
  }

  @Override
  public void setFont(Font font) {
    super.setFont(font);
    if (myTextField != null) {
      myTextField.setFont(font);
    }
  }

  @Override
  public Dimension getMinimumSize() {
    if (!isPreferredSizeSet()) {
      // Since the text may be bold or not, we make sure to use the bold version of the font to compute the size
      // That way we ensure all components will have the same size
      FontMetrics fm = getFontMetrics(getFont());
      return new Dimension(0, fm.getHeight() + 2 * TEXT_PADDING);
    }
    return super.getPreferredSize();
  }

  @Override
  public Dimension getPreferredSize() {
    if (!isPreferredSizeSet()) {
      return getMinimumSize();
    }
    return super.getPreferredSize();
  }

  public static class ColorIcon extends SwatchIcon {
    private final Color myColor;

    public ColorIcon(@NotNull Color color) {
      myColor = color;
    }

    @Override
    public void paintSwatch(@NotNull Component c, @NotNull Graphics g, int x, int y, int w, int h) {
      if (myColor.getAlpha() != 0xff) {
        GraphicsUtil.paintCheckeredBackground(g, new Rectangle(x, y, w, h));
      }

      g.setColor(myColor);
      g.fillRect(x, y, w, h);
    }
  }

  public static class SquareImageIcon extends SwatchIcon {
    private final ImageIcon myImageIcon;

    public SquareImageIcon(@NotNull BufferedImage image) {
      myImageIcon = new ImageIcon(image);
    }

    @Override
    public void paintSwatch(@NotNull Component c, @NotNull Graphics g, int x, int y, int w, int h) {
      Image image = myImageIcon.getImage();
      GraphicsUtil.paintCheckeredBackground(g, new Rectangle(x, y, w, h));
      g.drawImage(image, x, y, w, h, c);
    }
  }

  public static class TextIcon extends SwatchIcon {
    private final Font myFont;
    private final String myText;

    public TextIcon(String text, @NotNull Font font) {
      myText = text;
      myFont = font;
    }

    @Override
    protected void paintSwatch(@NotNull Component c, @NotNull Graphics graphics, int x, int y, int w, int h) {
      Graphics2D g = (Graphics2D)graphics;
      g.setColor(JBColor.LIGHT_GRAY);
      g.fillRect(x, y, w, h);
      g.setColor(JBColor.DARK_GRAY);
      g.setFont(myFont);
      GraphicsUtil.drawCenteredString(g, new Rectangle(x, y, w, h), myText);
    }
  }

  public void setText(@NotNull String text) {
    // No need to register this action for undoing, and it conflicts with other undo actions if we do
    myTextField.getDocument().putUserData(UndoConstants.DONT_RECORD_UNDO, true);
    myTextField.setText(text);
    myTextField.getDocument().putUserData(UndoConstants.DONT_RECORD_UNDO, false);
  }

  @NotNull
  public String getText() {
    return myTextField.getText();
  }

  public void addSwatchListener(@NotNull ActionListener listener) {
    mySwatchButton.addActionListener(listener);
  }

  public void addTextListener(@NotNull ActionListener listener) {
    myTextListeners.add(listener);
  }

  public void addTextDocumentListener(@NotNull final DocumentListener listener) {
    myTextField.addDocumentListener(listener);
  }

  public boolean hasWarningIcon() {
    return WARNING_ICON.equals(mySwatchButton.getIcon());
  }

  public void setCompletionStrings(@NotNull List<String> completionStrings) {
    myTextField.setVariants(completionStrings);
  }

  public abstract static class SwatchIcon implements Icon {
    private static final int SPACING = JBUI.scale(3);
    private static final int TRIANGLE_SIZE = JBUI.scale(13);

    private int mySize;
    private boolean myIsStack;
    private Color myBackgroundColor;

    @Override
    public void paintIcon(Component c, Graphics graphics, int x, int y) {
      setupAAPainting(graphics);
      Graphics2D g = (Graphics2D)graphics;
      if (myIsStack) {
        paintStack(c, g);
      }
      else {
        paintSingleIcon(c, g);
      }
    }

    protected abstract void paintSwatch(@NotNull Component c, @NotNull Graphics g, int x, int y, int w, int h);

    private void paintSingleIcon(@NotNull Component c, @NotNull Graphics2D g) {
      g.setColor(myBackgroundColor);
      g.fillRect(0, 0, mySize - 1, mySize - 1);

      Shape savedClip = g.getClip();
      RoundRectangle2D clipRectangle = new RoundRectangle2D.Double(0, 0, mySize - 1, mySize - 1, ARC_SIZE, ARC_SIZE);

      g.clip(clipRectangle);
      paintSwatch(c, g, 0, 0, mySize - 1, mySize - 1);
      g.setColor(JBColor.WHITE);

      g.fillPolygon(new int[]{mySize - TRIANGLE_SIZE, mySize - 1, mySize - 1}, new int[]{mySize - 1, mySize - 1, mySize - TRIANGLE_SIZE}, 3);
      g.setClip(savedClip);
      g.setColor(DEFAULT_BORDER_COLOR);
      g.drawRoundRect(0, 0, mySize - 1, mySize - 1, ARC_SIZE, ARC_SIZE);
      g.drawLine(mySize - 1 - TRIANGLE_SIZE, mySize - 1, mySize - 1, mySize - 1 - TRIANGLE_SIZE);
    }

    private void paintStack(@NotNull Component c, @NotNull Graphics2D g) {
      g.setColor(myBackgroundColor);
      g.fillRect(0, 0, mySize - 1, mySize - 1);
      Shape savedClip = g.getClip();

      int smallSize = mySize - 2 * SPACING - 1;

      g.setColor(DEFAULT_BORDER_COLOR);
      g.drawRoundRect(2 * SPACING, 0, smallSize, smallSize, ARC_SIZE, ARC_SIZE);
      g.drawRoundRect(SPACING, SPACING, smallSize, smallSize, ARC_SIZE, ARC_SIZE);
      g.setColor(myBackgroundColor);
      g.fillRoundRect(SPACING + 1, SPACING + 1, smallSize - 1, smallSize - 1, ARC_SIZE, ARC_SIZE);

      RoundRectangle2D clipRectangle = new RoundRectangle2D.Double(0, 2 * SPACING, smallSize + 1, smallSize + 1, ARC_SIZE, ARC_SIZE);
      g.clip(clipRectangle);
      paintSwatch(c, g, 0, 2 * SPACING, smallSize, smallSize);
      g.setColor(JBColor.WHITE);
      g.fillPolygon(new int[]{smallSize + 1 - TRIANGLE_SIZE + SPACING, smallSize, smallSize}, new int[]{mySize - 1, mySize - 1, mySize - TRIANGLE_SIZE + SPACING}, 3);
      g.setClip(savedClip);

      g.setColor(DEFAULT_BORDER_COLOR);
      g.drawRoundRect(0, 2 * SPACING, smallSize, smallSize, ARC_SIZE, ARC_SIZE);
      g.drawLine(smallSize - TRIANGLE_SIZE + SPACING, mySize - 1, smallSize, mySize - 1 - TRIANGLE_SIZE + SPACING);
    }

    @Override
    public int getIconWidth() {
      return mySize;
    }

    @Override
    public int getIconHeight() {
      return mySize;
    }

    public void setSize(int size) {
      mySize = size;
    }

    public void setIsStack(boolean isStack) {
      myIsStack = isStack;
    }

    public void setBackgroundColor(@Nullable Color backgroundColor) {
      myBackgroundColor = backgroundColor;
    }
  }
}
