/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.android.tools.idea.ui.resourcechooser;

import com.android.tools.idea.editors.theme.ColorUtils;
import com.android.tools.idea.editors.theme.MaterialColorUtils;
import com.android.tools.swing.ui.ClickableLabel;
import com.android.tools.swing.util.GraphicsUtil;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColorIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ImageObserver;
import java.awt.image.MemoryImageSource;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Color picker with support for Material suggestions and ARGB.
 * Unlike Intellij color picker, it uses a saturation and brightness matrix and a hue slider instead of a colour wheel
 */
public class ColorPicker extends JPanel implements ColorListener, DocumentListener {
  private static final String HSB_PROPERTY = "color.picker.is.hsb";

  private Color myColor;
  private ColorPreviewComponent myPreviewComponent;
  private JLabel myPreviewColorName;
  private ClickableLabel myColorSuggestionPreview;
  private Color myClosestColor;
  private final ColorSelectionPanel myColorSelectionPanel;
  private final JTextField myAlpha;
  private final JTextField myRed;
  private final JTextField myGreen;
  private final JTextField myBlue;
  private final JTextField myHex;
  private final Alarm myUpdateQueue;
  private final ColorPickerListener[] myExternalListeners;

  private RecommendedColorsComponent myRecommendedColorsComponent;
  private final ColorPipette myPicker;
  private final JLabel myA = new JLabel("A:");
  private final JLabel myR = new JLabel("R:");
  private final JLabel myG = new JLabel("G:");
  private final JLabel myB = new JLabel("B:");
  private final JLabel myR_after = new JLabel("");
  private final JLabel myG_after = new JLabel("");
  private final JLabel myB_after = new JLabel("");
  private final JLabel myHexLabel = new JLabel("#");

  private float[] myHSB;

  private final JComboBox myFormat = new JComboBox() {
    @Override
    public Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      UIManager.LookAndFeelInfo info = LafManager.getInstance().getCurrentLookAndFeel();
      if (info != null && info.getName().contains("Windows"))
        size.width += 10;
      return size;
    }
  };

  public ColorPicker(@NotNull Disposable parent, @Nullable Color color, boolean enableOpacity, ColorPickerListener... listeners) {
    this(parent, color, enableOpacity, listeners, false);
  }

  private ColorPicker(Disposable parent,
                      @Nullable Color color, boolean enableOpacity,
                      ColorPickerListener[] listeners, boolean opacityInPercent) {
    myUpdateQueue = new Alarm(Alarm.ThreadToUse.SWING_THREAD, parent);
    myAlpha = createColorField(false);
    myRed = createColorField(false);
    myGreen = createColorField(false);
    myBlue = createColorField(false);
    myHex = createColorField(true);
    myA.setLabelFor(myAlpha);
    myR.setLabelFor(myRed);
    myG.setLabelFor(myGreen);
    myB.setLabelFor(myBlue);
    myHexLabel.setLabelFor(myHex);
    setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
    setBorder(JBUI.Borders.empty(5, 5, 0, 5));

    DefaultComboBoxModel model = new DefaultComboBoxModel(new String[]{"RGB", "HSB"});
    if (enableOpacity) {
      model.addElement("ARGB");
    }
    myFormat.setModel(model);

    myColorSelectionPanel = new ColorSelectionPanel(this, enableOpacity, opacityInPercent);

    myExternalListeners = listeners;
    myFormat.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        PropertiesComponent.getInstance().setValue(HSB_PROPERTY, !isRGBMode());
        myColorSelectionPanel.myOpacityComponent.setVisible(isARGBMode());
        myA.setVisible(isARGBMode());
        myAlpha.setVisible(isARGBMode());
        myR.setText(isRGBMode() ? "R:" : "H:");
        myG.setText(isRGBMode() ? "G:" : "S:");
        myR_after.setText(isRGBMode() ? "" : "\u00B0");
        myR_after.setVisible(!isRGBMode());
        myG.setText(isRGBMode() ? "G:" : "S:");
        myG_after.setText(isRGBMode() ? "" : "%");
        myG_after.setVisible(!isRGBMode());
        myB_after.setText(isRGBMode() ? "" : "%");
        myB_after.setVisible(!isRGBMode());
        if (!isARGBMode() && myColor.getAlpha() != 255) {
          updatePreview(ColorUtil.toAlpha(myColor, 255), false);
        }
        applyColor(myColor);
        applyColorToHEX(myColor);
      }
    });

    myPicker = new ColorPipette(this, getColor());
    myPicker.setListener(new ColorListener() {
      @Override
      public void colorChanged(Color color, Object source) {
        setColor(color, source);
      }
    });
    try {
      myRecommendedColorsComponent = new RecommendedColorsComponent(new ColorListener() {
        @Override
        public void colorChanged(Color color, Object source) {
          setColor(color, source);
        }
      });

      JComponent topPanel = buildTopPanel(true);
      add(topPanel);
      add(myColorSelectionPanel);
      add(myRecommendedColorsComponent);
    }
    catch (ParseException ignore) {
    }

    setColor(color == null ? Color.WHITE : color, this);
    setSize(JBUI.size(300, 350));

    final boolean hsb = PropertiesComponent.getInstance().getBoolean(HSB_PROPERTY);
    if (hsb) {
      myFormat.setSelectedIndex(1);
    }
  }

  /** RGB or ARGB mode */
  private boolean isRGBMode() {
    return myFormat.getSelectedIndex() == 0 || isARGBMode();
  }

  private boolean isARGBMode() {
    return myFormat.getSelectedIndex() == 2;
  }

  /** Pick colors in RGB mode */
  public void pickRGB() {
    myFormat.setSelectedIndex(0);
  }

  /** Pick colors in HSB mode */
  public void pickHSB() {
    myFormat.setSelectedIndex(1);
  }

  /** Pick colors in ARGB mode. Only valid if the color picker was constructed with enableOpacity=true. */
  public void pickARGB() {
    myFormat.setSelectedIndex(2);
  }

  private int getLargestDigitWidth(boolean hex) {
    FontMetrics metrics = getFontMetrics(getFont());
    int largestWidth = metrics.charWidth('0');
    int maxDigit = hex ? 16 : 10;
    for (int i = 1; i < maxDigit; i++) {
      largestWidth = Math.max(largestWidth, metrics.charWidth(Character.toUpperCase(Character.forDigit(i, maxDigit))));
    }
    return largestWidth;
  }

  private JTextField createColorField(final boolean hex) {
    final NumberDocument doc = new NumberDocument(hex);
    final JTextField field = new JTextField(doc, "", hex ? 8 : 3) {
      @Override
      protected int getColumnWidth() {
        return getLargestDigitWidth(hex);
      }
    };
    doc.setSource(field);
    field.getDocument().addDocumentListener(this);
    field.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(final FocusEvent e) {
        field.selectAll();
      }
    });
    return field;
  }

  public JComponent getPreferredFocusedComponent() {
    return myHex;
  }

  public void setColor(@NotNull Color color) {
    setColor(color, null);
  }

  private void setColor(Color color, Object src) {
    colorChanged(color, src);
    myColorSelectionPanel.setColor(color, src);
  }

  public Color getColor() {
    return myColor;
  }

  public void setContrastParameters(@NotNull ImmutableMap<String, Color> contrastColorsWithDescription,
                                    boolean isBackground,
                                    boolean displayWarning) {
    myPreviewComponent.setContrastParameters(contrastColorsWithDescription, isBackground, displayWarning);
  }

  @Override
  public void insertUpdate(DocumentEvent e) {
    update(((NumberDocument)e.getDocument()).mySrc);
  }

  private void update(final JTextField src) {
    myUpdateQueue.cancelAllRequests();
    myUpdateQueue.addRequest(new Runnable() {
      @Override
      public void run() {
        validateAndUpdatePreview(src);
      }
    }, 300);
  }

  @Override
  public void removeUpdate(DocumentEvent e) {
    update(((NumberDocument)e.getDocument()).mySrc);
  }

  @Override
  public void changedUpdate(DocumentEvent e) {
    // ignore
  }

  private void validateAndUpdatePreview(JTextField src) {
    Color color;
    if (myHex.hasFocus()) {
      if (isARGBMode()) { // Read alpha from the text string itself
        // ColorUtil.fromHex only handles opaque colors
        String text = myHex.getText();
        int rgbIndex = Math.max(0, text.length() - 6);
        String rgb = text.substring(rgbIndex);
        String alphaText = text.substring(0, rgbIndex);
        int alpha = alphaText.isEmpty() ? 255 : Integer.parseInt(alphaText, 16);
        Color c = ColorUtil.fromHex(rgb, null);
        color = c != null ? ColorUtil.toAlpha(c, alpha) : null;
      } else {
        Color c = ColorUtil.fromHex(myHex.getText(), null);
        color = c != null ? ColorUtil.toAlpha(c, myColorSelectionPanel.mySaturationBrightnessComponent.myOpacity) : null;
      }
    } else {
      color = gatherRGB(myRed.hasFocus() || myGreen.hasFocus() || myBlue.hasFocus());
    }
    if (color != null) {
      myColorSelectionPanel.myOpacityComponent.setColor(color);
      if (myAlpha.hasFocus() || (isARGBMode() && myHex.hasFocus())) {
        myColorSelectionPanel.myOpacityComponent.setValue(color.getAlpha());
      }
      myColorSelectionPanel.myOpacityComponent.repaint();
      updatePreview(color, src == myHex);
    }
  }

  private void updatePreview(Color color, boolean fromHex) {
    if (color != null && !color.equals(myColor)) {
      myColor = color;
      myPreviewComponent.setColor(color);
      myColorSelectionPanel.setColor(color, fromHex ? myHex : null);

      setNameTag(color);

      if (fromHex) {
        applyColor(color);
      } else {
        applyColorToHEX(color);
      }

      fireColorChanged(color);
    }
  }

  @Override
  public void colorChanged(Color color, Object source) {
    if (color != null && !color.equals(myColor)) {
      myColor = color;

      applyColor(color);

      if (source != myHex) {
        applyColorToHEX(color);
      }
      myPreviewComponent.setColor(color);
      myColorSelectionPanel.setOpacityComponentColor(color);

      setNameTag(color);

      fireColorChanged(color);
    }
  }

  private void setNameTag(@NotNull Color color) {
    String name = MaterialColorUtils.getMaterialName(color);
    if (name == null) {
      name = "Custom color";
      myClosestColor = MaterialColorUtils.getClosestMaterialColor(color);
      myColorSuggestionPreview.setVisible(true);
      String toolTip = "<html>Change to <b>" + MaterialColorUtils.getMaterialName(myClosestColor);
      myColorSuggestionPreview.setToolTipText(toolTip);
      myColorSuggestionPreview.setIcon(new ColorIcon(JBUI.scale(12), myClosestColor));
    }
    else {
      myColorSuggestionPreview.setVisible(false);
    }
    myPreviewColorName.setText(name);
  }

  private void fireColorChanged(Color color) {
    if (myExternalListeners == null) return;
    for (ColorPickerListener listener : myExternalListeners) {
      listener.colorChanged(color);
    }
  }

  private void fireClosed(@Nullable Color color) {
    if (myExternalListeners == null) return;
    for (ColorPickerListener listener : myExternalListeners) {
      listener.closed(color);
    }
  }

  public void setRecommendedColors(@NotNull List<Color> colorList) {
    myRecommendedColorsComponent.setColors(colorList);
  }

  @SuppressWarnings("UseJBColor")
  @Nullable
  private Color gatherRGB(boolean fromTextFields) {
    try {
      final int r = Integer.parseInt(myRed.getText());
      final int g = Integer.parseInt(myGreen.getText());
      final int b = Integer.parseInt(myBlue.getText());
      final int a = Integer.parseInt(myAlpha.getText());
      if (isARGBMode()) {
        return new Color(r, g, b, a);
      }
      else if (isRGBMode()) {
        return new Color(r, g, b);
      }
      else if (fromTextFields) {
        return Color.getHSBColor(r / 360.0f, g / 100.0f, b / 100.0f);
      }
      else {
        return Color.getHSBColor(myHSB[0], myHSB[1], myHSB[2]);
      }
    }
    catch (Exception ignore) {
    }
    return null;
  }

  private void applyColorToHEX(final Color c) {
    if (isARGBMode()) {
      myHex.setText(String.format("%08X", c.getRGB()));
    } else {
      myHex.setText(String.format("%06X", (0xFFFFFF & c.getRGB())));
    }
  }

  private void applyColorToRGB(final Color color) {
    myAlpha.setText(String.valueOf(color.getAlpha()));
    myRed.setText(String.valueOf(color.getRed()));
    myGreen.setText(String.valueOf(color.getGreen()));
    myBlue.setText(String.valueOf(color.getBlue()));
  }

  private void applyColorToHSB(final Color c) {
    myAlpha.setText(String.valueOf(c.getAlpha()));
    myHSB = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
    myRed.setText(String.valueOf((Math.round(360f * myHSB[0]))));
    myGreen.setText(String.valueOf((Math.round(100f * myHSB[1]))));
    myBlue.setText(String.valueOf((Math.round(100f * myHSB[2]))));
  }

  private void applyColor(final Color color) {
    if (isRGBMode()) {
      applyColorToRGB(color);
    } else {
      applyColorToHSB(color);
    }
  }

  @Nullable
  public static Color showDialog(Component parent,
                                 String caption,
                                 @Nullable Color preselectedColor,
                                 boolean enableOpacity,
                                 @Nullable ColorPickerListener[] listeners,
                                 boolean opacityInPercent) {
    final ColorPickerDialog dialog = new ColorPickerDialog(parent, caption, preselectedColor, enableOpacity, listeners, opacityInPercent);
    dialog.show();
    if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      return dialog.getColor();
    }

    return null;
  }

  @NotNull
  private JComponent buildTopPanel(boolean enablePipette) throws ParseException {
    final JComponent result = new Box(BoxLayout.PAGE_AXIS);

    JComponent namePanel = new Box(BoxLayout.LINE_AXIS);
    myPreviewColorName = new JLabel("");
    Font f = myPreviewColorName.getFont();
    myPreviewColorName.setFont(f.deriveFont(f.getStyle() | Font.BOLD));

    myColorSuggestionPreview = new ClickableLabel("CLOSEST MATERIAL COLOR");
    myColorSuggestionPreview.setFont(f.deriveFont(JBUI.scale(8.0f)));
    myColorSuggestionPreview.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myColorSelectionPanel.setColor(myClosestColor, this);
      }
    });

    namePanel.add(myPreviewColorName);
    namePanel.add(Box.createRigidArea(new Dimension(JBUI.scale(5), 0)));
    namePanel.add(myColorSuggestionPreview);
    namePanel.add(Box.createHorizontalGlue());
    result.add(namePanel);

    final JComponent previewPanel = new Box(BoxLayout.LINE_AXIS);
    if (enablePipette && ColorPipette.isAvailable()) {
      final JButton pipette = new JButton();
      pipette.setUI(new BasicButtonUI());
      pipette.setRolloverEnabled(true);
      pipette.setIcon(AllIcons.Ide.Pipette);
      pipette.setBorder(IdeBorderFactory.createEmptyBorder());
      pipette.setRolloverIcon(AllIcons.Ide.Pipette_rollover);
      pipette.setFocusable(false);
      pipette.addActionListener(e -> {
        myPicker.myOldColor = getColor();
        myPicker.pick();
        //JBPopupFactory.getInstance().createBalloonBuilder(new JLabel("Press ESC button to close pipette"))
        //  .setAnimationCycle(2000)
        //  .setSmallVariant(true)
        //  .createBalloon().show(new RelativePoint(pipette, new Point(pipette.getWidth() / 2, 0)), Balloon.Position.above);
      });
      previewPanel.add(pipette);
    }

    myPreviewComponent = new ColorPreviewComponent();
    previewPanel.add(myPreviewComponent);

    result.add(previewPanel);

    final JComponent rgbPanel = new Box(BoxLayout.LINE_AXIS);
    if (!UIUtil.isUnderAquaLookAndFeel()) {
      myR_after.setPreferredSize(JBUI.size(14, -1));
      myG_after.setPreferredSize(JBUI.size(14, -1));
      myB_after.setPreferredSize(JBUI.size(14, -1));
    }
    rgbPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
    rgbPanel.add(myA);
    rgbPanel.add(myAlpha);
    myA.setVisible(isARGBMode());
    myAlpha.setVisible(isARGBMode());
    rgbPanel.add(Box.createHorizontalStrut(JBUI.scale(3)));
    rgbPanel.add(myR);
    rgbPanel.add(myRed);
    if (!UIUtil.isUnderAquaLookAndFeel()) rgbPanel.add(myR_after);
    myR_after.setVisible(false);
    rgbPanel.add(Box.createHorizontalStrut(JBUI.scale(3)));
    rgbPanel.add(myG);
    rgbPanel.add(myGreen);
    if (!UIUtil.isUnderAquaLookAndFeel()) rgbPanel.add(myG_after);
    myG_after.setVisible(false);
    rgbPanel.add(Box.createHorizontalStrut(JBUI.scale(3)));
    rgbPanel.add(myB);
    rgbPanel.add(myBlue);
    if (!UIUtil.isUnderAquaLookAndFeel()) rgbPanel.add(myB_after);
    myB_after.setVisible(false);
    rgbPanel.add(Box.createHorizontalStrut(JBUI.scale(3)));
    rgbPanel.add(myFormat);

    JComponent valuesPanel = new Box(BoxLayout.LINE_AXIS);

    rgbPanel.setMaximumSize(JBUI.size(-1, 35));
    valuesPanel.add(rgbPanel);

    final JComponent hexPanel = new Box(BoxLayout.X_AXIS);
    hexPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
    hexPanel.add(myHexLabel);
    hexPanel.add(myHex);
    myHex.setMaximumSize(JBUI.size(120, 25));

    valuesPanel.add(Box.createHorizontalGlue());
    valuesPanel.add(hexPanel);
    result.add(valuesPanel);

    return result;
  }

  private static class ColorSelectionPanel extends JPanel {
    private SaturationBrightnessComponent mySaturationBrightnessComponent;
    private HueSlideComponent myHueComponent;
    private SlideComponent myOpacityComponent = null;

    private ColorSelectionPanel(ColorListener listener, boolean enableOpacity, boolean opacityInPercent) {
      setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
      setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));

      mySaturationBrightnessComponent = new SaturationBrightnessComponent();
      add(mySaturationBrightnessComponent);

      mySaturationBrightnessComponent.addListener(listener);

      myHueComponent = new HueSlideComponent("Hue");
      myHueComponent.setToolTipText("Hue");
      myHueComponent.addListener(value -> {
        mySaturationBrightnessComponent.setHue(value.intValue() / 360.0f);
        myOpacityComponent.setHue(value.intValue() / 360.0f);
        mySaturationBrightnessComponent.repaint();
      });

      add(myHueComponent);

      if (enableOpacity) {
        myOpacityComponent = new SlideComponent("Opacity");
        myOpacityComponent.setUnits(opacityInPercent ? SlideComponent.Unit.PERCENT : SlideComponent.Unit.LEVEL);
        myOpacityComponent.setToolTipText("Opacity");
        myOpacityComponent.addListener(integer -> {
          mySaturationBrightnessComponent.setOpacity(integer.intValue());
          mySaturationBrightnessComponent.repaint();
        });

        add(myOpacityComponent);
      }
    }

    public void setOpacityComponentColor(Color color) {
      myOpacityComponent.setColor(color);
      myOpacityComponent.repaint();
    }

    public void setColor(Color color, Object source) {
      float[] hsb = new float[3];
      Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), hsb);

      myHueComponent.setHueValue(hsb[0]);
      myHueComponent.repaint();

      mySaturationBrightnessComponent.dropImage();
      if (myOpacityComponent != null) {
        setOpacityComponentColor(color);
        myOpacityComponent.setValue(color.getAlpha());
        myOpacityComponent.repaint();
      }
      mySaturationBrightnessComponent.setColor(color, source);
    }
  }

  static class SaturationBrightnessComponent extends JComponent {
    private static final int BORDER_SIZE = JBUI.scale(5);
    private float myBrightness = 1f;
    private float myHue = 1f;
    private float mySaturation = 0f;

    private Image myImage;
    private Rectangle myComponent;

    private Color myColor;

    private final List<ColorListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
    private int myOpacity;

    protected SaturationBrightnessComponent() {
      setOpaque(true);

      addMouseMotionListener(new MouseAdapter() {
        @Override
        public void mouseDragged(MouseEvent e) {
          final Dimension size = getSize();
          final int x = Math.max(Math.min(e.getX(), size.width - BORDER_SIZE), BORDER_SIZE) - BORDER_SIZE;
          final int y = Math.max(Math.min(e.getY(), size.height - BORDER_SIZE), BORDER_SIZE) - BORDER_SIZE;

          float saturation = ((float)x) / (size.width - 2 * BORDER_SIZE);
          float brightness = 1.0f - ((float)y) / (size.height - 2 * BORDER_SIZE);

          setHSBValue(myHue, saturation, brightness, myOpacity);
        }
      });

      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          final Dimension size = getSize();
          final int x = Math.max(Math.min(e.getX(), size.width - BORDER_SIZE), BORDER_SIZE) - BORDER_SIZE;
          final int y = Math.max(Math.min(e.getY(), size.height - BORDER_SIZE), BORDER_SIZE) - BORDER_SIZE;


          float saturation = ((float)x) / (size.width - 2 * BORDER_SIZE);
          float brightness = 1.0f - ((float)y) / (size.height - 2 * BORDER_SIZE);

          setHSBValue(myHue, saturation, brightness, myOpacity);
        }
      });
    }

    private void setHSBValue(float h, float s, float b, int opacity) {
      myHue = h;
      mySaturation = s;
      myBrightness = b;
      myOpacity = opacity;
      myColor = ColorUtil.toAlpha(Color.getHSBColor(h, s, b), opacity);

      fireColorChanged(this);

      repaint();
    }

    private void setColor(Color color, Object source) {
      float[] hsb = new float[3];
      Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), hsb);
      myColor = color;
      myHue = hsb[0];
      mySaturation = hsb[1];
      myBrightness = hsb[2];
      myOpacity = color.getAlpha();

      fireColorChanged(source);

      repaint();
    }

    public void addListener(ColorListener listener) {
      myListeners.add(listener);
    }

    private void fireColorChanged(Object source) {
      for (ColorListener listener : myListeners) {
        listener.colorChanged(myColor, source);
      }
    }

    public void setOpacity(int opacity) {
      if (opacity != myOpacity) {
        setHSBValue(myHue, mySaturation, myBrightness, opacity);
      }
    }

    public void setHue(float hue) {
      if (Math.abs(hue - myHue) > 0.01) {
        setHSBValue(hue, mySaturation, myBrightness, myOpacity);
      }
    }

    @Override
    public Dimension getPreferredSize() {
      return JBUI.size(250, 170);
    }

    @Override
    public Dimension getMinimumSize() {
      return JBUI.size(150, 170);
    }

    @Override
    protected void paintComponent(Graphics g) {
      final Dimension size = getSize();

      myComponent = new Rectangle(BORDER_SIZE, BORDER_SIZE, size.width, size.height);
      myImage = createImage(new SaturationBrightnessImageProducer(size.width - BORDER_SIZE * 2, size.height - BORDER_SIZE * 2, myHue));

      g.setColor(UIManager.getColor("Panel.background"));
      g.fillRect(0, 0, getWidth(), getHeight());

      g.drawImage(myImage, myComponent.x, myComponent.y, null);

      final int x = Math.round(mySaturation * (myComponent.width - 2 * BORDER_SIZE));
      final int y = Math.round((myComponent.height - 2 * BORDER_SIZE) * (1.0f - myBrightness));

      int knobX = BORDER_SIZE + x;
      int knobY = BORDER_SIZE + y;
      g.setColor(Color.WHITE);
      g.drawOval(knobX - JBUI.scale(4), knobY - JBUI.scale(4), JBUI.scale(8), JBUI.scale(8));
      g.drawOval(knobX - JBUI.scale(3), knobY - JBUI.scale(3), JBUI.scale(6), JBUI.scale(6));
    }

    public void dropImage() {
      myImage = null;
    }

    @VisibleForTesting
    protected Color getColor() {
      return myColor;
    }
  }

  public static class ColorPreviewComponent extends JComponent {
    private static final Icon WARNING_ICON = AllIcons.General.BalloonWarning;
    private static final String TEXT = "Text";
    private static final int PADDING = JBUI.scale(18);
    private static final float FONT_SIZE_RATIO = 1.5f;

    private Color myColor;
    private boolean myIsContrastPreview = false;
    private ImmutableSet<Color> myContrastColorSet;
    private boolean myIsBackgroundColor;
    private String myErrorString;
    private ImmutableMap<String, Color> myContrastColorsWithDescription;
    private boolean myDisplayWarning;

    private ColorPreviewComponent() {
      setBorder(JBUI.Borders.empty(0, 2));
    }

    @Override
    public Dimension getMaximumSize() {
      return new Dimension(Integer.MAX_VALUE, JBUI.scale(32));
    }

    /**
     * Adds text to the preview so the user can see the contrast of two colors
     *
     * @param contrastColorsWithDescription the colors we are testing the contrast against, and their associate description.
     *                                  If the user is editing a state list, this might be more than 1.
     * @param isBackgroundColor true if it's a background color, of false if it's a text color
     * @param displayWarning whether or not to display a warning for contrast issues
     */
    public void setContrastParameters(@NotNull ImmutableMap<String, Color> contrastColorsWithDescription,
                                      boolean isBackgroundColor,
                                      boolean displayWarning) {
      myIsContrastPreview = true;
      myContrastColorsWithDescription = contrastColorsWithDescription;
      myContrastColorSet = ImmutableSet.copyOf(contrastColorsWithDescription.values());
      myIsBackgroundColor = isBackgroundColor;
      myDisplayWarning = displayWarning;
      setErrorString(displayWarning ? ColorUtils.getContrastWarningMessage(contrastColorsWithDescription, myColor, isBackgroundColor) : "");
    }

    private void setErrorString(@NotNull String error) {
      myErrorString = error;
      setToolTipText(myErrorString);
    }

    @Override
    public Dimension getPreferredSize() {
      return JBUI.size(100, 32);
    }

    @Override
    public Dimension getMinimumSize() {
      return getPreferredSize();
    }

    public void setColor(Color c) {
      myColor = c;
      if (myIsContrastPreview && myDisplayWarning) {
        setErrorString(ColorUtils.getContrastWarningMessage(myContrastColorsWithDescription, c, myIsBackgroundColor));
      }
      repaint();
    }

    @Override
    @SuppressWarnings("UseJBColor")
    protected void paintComponent(Graphics graphics) {
      Graphics2D g = (Graphics2D)graphics;
      final Insets i = getInsets();
      final Rectangle r = getBounds();
      final int width = r.width - i.left - i.right;
      final int height = r.height - i.top - i.bottom;
      com.intellij.util.ui.GraphicsUtil.setupAntialiasing(g);

      Rectangle clipRectangle = new Rectangle(i.left, i.top, width, height);
      GraphicsUtil.paintCheckeredBackground(g, clipRectangle);

      if (!myIsContrastPreview) {
        g.setColor(myColor);
        g.fillRect(clipRectangle.x, clipRectangle.y, clipRectangle.width, clipRectangle.height);
        return;
      }

      // 1 added for rounding up the division, so that all the rectangles will fill the entire width of the component
      int colorCellWidth = width / myContrastColorSet.size() + 1;
      Rectangle drawingRectangle = new Rectangle(clipRectangle.x, clipRectangle.y, colorCellWidth, clipRectangle.height);
      Font defaultFont = UIUtil.getLabelFont();
      Font textFont = defaultFont.deriveFont(defaultFont.getSize() * FONT_SIZE_RATIO);

      for (Color color : myContrastColorSet) {
        Color textColor = myIsBackgroundColor ? color : myColor;
        Color backgroundColor = myIsBackgroundColor ? myColor : color;

        g.setColor(backgroundColor);
        g.fillRect(drawingRectangle.x, drawingRectangle.y, drawingRectangle.width, drawingRectangle.height);
        g.setColor(textColor);
        g.setFont(textFont);
        GraphicsUtil.drawCenteredString(g, drawingRectangle, TEXT);
        drawingRectangle.x += colorCellWidth;
      }

      if (!myErrorString.isEmpty()) {
        WARNING_ICON.paintIcon(this, g, width - PADDING, height - PADDING);
      }
    }
  }

  public class NumberDocument extends PlainDocument {

    private final boolean myHex;
    private JTextField mySrc;

    public NumberDocument(boolean hex) {
      myHex = hex;
    }

    void setSource(JTextField field) {
      mySrc = field;
    }

    @Override
    public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
      final boolean rgb = isRGBMode();
      char[] source = str.toCharArray();
      if (mySrc != null) {
        final int selected = mySrc.getSelectionEnd() - mySrc.getSelectionStart();
        int newLen = mySrc.getText().length() -  selected + str.length();
        if (newLen > (myHex ? (isARGBMode() ? 8 : 6) : 3)) {
          Toolkit.getDefaultToolkit().beep();
          return;
        }
      }
      char[] result = new char[source.length];
      int j = 0;
      for (int i = 0; i < result.length; i++) {
        if (myHex ? "0123456789abcdefABCDEF".indexOf(source[i]) >= 0 : Character.isDigit(source[i])) {
          result[j++] = source[i];
        }
        else {
          Toolkit.getDefaultToolkit().beep();
        }
      }
      final String toInsert = StringUtil.toUpperCase(new String(result, 0, j));
      final String res = new StringBuilder(mySrc.getText()).insert(offs, toInsert).toString();
      try {
        if (!myHex) {
          final int num = Integer.parseInt(res);
          if (rgb) {
            if (num > 255) {
              Toolkit.getDefaultToolkit().beep();
              return;
            }
          } else {
            if ((mySrc == myRed && num > 359) || ((mySrc == myGreen || mySrc == myBlue) && num > 100)) {
              Toolkit.getDefaultToolkit().beep();
              return;
            }
          }
        }
      }
      catch (NumberFormatException ignore) {
      }
      super.insertString(offs, toInsert, a);
    }
  }

  private static class RecommendedColorsComponent extends JComponent {
    private static final int SPACE = JBUI.scale(3);
    private static final int CELL_SIZE = JBUI.scale(40);
    private static final int COLUMN_COUNT = 10;

    private List<Color> myRecommendedColors = new ArrayList<>();

    private RecommendedColorsComponent(final ColorListener listener) {
      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          Color color = getColor(e);
          if (color != null) {
            listener.colorChanged(color, RecommendedColorsComponent.this);
          }
        }
      });
    }

    @Override
    public String getToolTipText(MouseEvent event) {
      Color color = getColor(event);
      if (color != null) {
        String name = MaterialColorUtils.getMaterialName(color);
        if (name != null) {
          return name;
        }
        return String.format("R: %d G: %d B: %d", color.getRed(), color.getGreen(), color.getBlue());
      }

      return super.getToolTipText(event);
    }

    @Nullable
    private Color getColor(MouseEvent event) {
      int ndx = pointToColorPosition(event.getPoint());
      if (ndx >= 0 && ndx < myRecommendedColors.size()) {
        return myRecommendedColors.get(ndx);
      }
      return null;
    }

    public void setColors(@NotNull List<Color> colorList) {
      myRecommendedColors.clear();
      myRecommendedColors.addAll(colorList);
    }

    private int pointToColorPosition(Point p) {
      int x = p.x;
      int y = p.y;

      int leftPadding = getLeftPadding();
      int topPadding = getTopPadding();

      int col = x - leftPadding >= 0 ? (x - leftPadding) / CELL_SIZE : -1;
      int row = y - topPadding >=0 ? (y - topPadding) / CELL_SIZE : -1;

      return row >= 0 && col >= 0 && row < getRowCount() && col < COLUMN_COUNT ?  col + row * COLUMN_COUNT: -1;
    }

    private int getLeftPadding() {
      return (getSize().width - (COLUMN_COUNT * CELL_SIZE - SPACE)) / 2;
    }

    private int getTopPadding() {
      return (getSize().height - (getRowCount() * CELL_SIZE - SPACE)) / 2;
    }

    private int getRowCount() {
      return myRecommendedColors.isEmpty() ? 0 : (int)Math.ceil(myRecommendedColors.size() / new Double(COLUMN_COUNT));
    }

    @Override
    public Dimension getPreferredSize() {
      return getMinimumSize();
    }

    @Override
    public Dimension getMinimumSize() {
      final Insets i = getInsets();
      // The dimension is not scaled since CELL_SIZE and PADDING are already scaled
      return new Dimension(COLUMN_COUNT * CELL_SIZE - SPACE + i.left + i.right, getRowCount() * CELL_SIZE - SPACE + i.top + i.bottom);
    }

    @Override
    public Dimension getMaximumSize() {
      return new Dimension(Integer.MAX_VALUE, -1);
    }

    @Override
    protected void paintComponent(Graphics g) {
      final int leftPadding = getLeftPadding();
      final int topPadding = getTopPadding();

      for (int r = 0; r < myRecommendedColors.size(); r++) {
        int row = r / COLUMN_COUNT;
        int col = r % COLUMN_COUNT;
        Color color = myRecommendedColors.get(r);
        g.setColor(color);
        g.fillRect(leftPadding + col * CELL_SIZE, topPadding + row * CELL_SIZE, CELL_SIZE - SPACE, CELL_SIZE - SPACE);
      }
    }
  }

  static class ColorPickerDialog extends DialogWrapper {

    private final Color myPreselectedColor;
    private final ColorPickerListener[] myListeners;
    private ColorPicker myColorPicker;
    private final boolean myEnableOpacity;
    private ColorPipette myPicker;
    private final boolean myOpacityInPercent;

    public ColorPickerDialog(Component parent,
                             String caption,
                             @Nullable Color preselectedColor,
                             boolean enableOpacity,
                             @Nullable ColorPickerListener[] listeners,
                             boolean opacityInPercent) {
      super(parent, true);
      myListeners = listeners;
      setTitle(caption);
      myPreselectedColor = preselectedColor;
      myEnableOpacity = enableOpacity;
      myOpacityInPercent = opacityInPercent;
      setResizable(false);
      setOKButtonText("Choose");
      init();
      addMouseListener((MouseMotionListener)new MouseAdapter() {
        @Override
        public void mouseEntered(MouseEvent e) {
          myPicker.cancelPipette();
        }

        @Override
        public void mouseExited(MouseEvent e) {
          myPicker.pick();
        }
      });

    }

    @Override
    protected JComponent createCenterPanel() {
      if (myColorPicker == null) {
        myColorPicker = new ColorPicker(myDisposable, myPreselectedColor, myEnableOpacity, myListeners, myOpacityInPercent);
        myColorPicker.pickARGB();
      }

      return myColorPicker;
    }

    public Color getColor() {
      return myColorPicker.getColor();
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      return myColorPicker.getPreferredFocusedComponent();
    }

    @Override
    public void show() {
      super.show();
      myColorPicker.fireClosed(getExitCode() == DialogWrapper.OK_EXIT_CODE ? getColor() : null);
    }
  }

  public static class SaturationBrightnessImageProducer extends MemoryImageSource {
    private int[] myPixels;
    private int myWidth;
    private int myHeight;

    private float[] mySat;
    private float[] myBrightness;

    private float myHue;

    public SaturationBrightnessImageProducer(int w, int h, float hue) {
      super(w, h, null, 0, w);
      myPixels = new int[w * h];
      myWidth = w;
      myHeight = h;
      myHue = hue;
      generateLookupTables();
      newPixels(myPixels, ColorModel.getRGBdefault(), 0, w);
      setAnimated(true);
      generateComponent();
    }

    public int getRadius() {
      return Math.min(myWidth, myHeight) / 2 - 2;
    }

    private void generateLookupTables() {
      mySat = new float[myWidth * myHeight];
      myBrightness = new float[myWidth * myHeight];
      for (int x = 0; x < myWidth; x++) {
        for (int y = 0; y < myHeight; y++) {
          int index = x + y * myWidth;
          mySat[index] = ((float)x) / myWidth;
          myBrightness[index] = 1.0f - ((float)y) / myHeight;
        }
      }
    }

    public void generateComponent() {
      for (int index = 0; index < myPixels.length; index++) {
        myPixels[index] = Color.HSBtoRGB(myHue, mySat[index], myBrightness[index]);
      }
      newPixels();
    }
  }

  private static class ColorPipette implements ImageObserver {
    private Dialog myPickerFrame;
    private final JComponent myParent;
    private Color myOldColor;
    private Timer myTimer;

    private Point myPoint = new Point();
    private Point myPickOffset;
    private Robot myRobot = null;
    private Color myPreviousColor;
    private Point myPreviousLocation;
    private Rectangle myCaptureRect;
    private Graphics2D myGraphics;
    private BufferedImage myImage;
    private Point myHotspot;
    private BufferedImage myMagnifierImage;
    private Color myTransparentColor = new Color(0, true);
    private Rectangle myZoomRect;
    private ColorListener myColorListener;
    private BufferedImage myMaskImage;
    private Alarm myColorListenersNotifier = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

    private ColorPipette(JComponent parent, Color oldColor) {
      myParent = parent;
      myOldColor = oldColor;

      try {
        myRobot = new Robot();
      }
      catch (AWTException e) {
        // should not happen
      }
    }

    public void setListener(ColorListener colorListener) {
      myColorListener = colorListener;
    }

    public void pick() {
      Dialog picker = getPicker();
      picker.setVisible(true);
      myTimer.start();
      // it seems like it's the lowest value for opacity for mouse events to be processed correctly
      WindowManager.getInstance().setAlphaModeRatio(picker, SystemInfo.isMac ? 0.95f : 0.99f);
    }

    @Override
    public boolean imageUpdate(Image img, int flags, int x, int y, int width, int height) {
      return false;
    }

    private Dialog getPicker() {
      if (myPickerFrame == null) {
        Window owner = SwingUtilities.getWindowAncestor(myParent);
        if (owner instanceof Dialog) {
          myPickerFrame = new JDialog((Dialog)owner);
        }
        else if (owner instanceof Frame) {
          myPickerFrame = new JDialog((Frame)owner);
        }
        else {
          myPickerFrame = new JDialog(new JFrame());
        }

        myPickerFrame.addMouseListener(new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent e) {
            e.consume();
            pickDone();
          }

          @Override
          public void mouseClicked(MouseEvent e) {
            e.consume();
          }

          @Override
          public void mouseExited(MouseEvent e) {
            updatePipette();
          }
        });

        myPickerFrame.addMouseMotionListener(new MouseAdapter() {
          @Override
          public void mouseMoved(MouseEvent e) {
            updatePipette();
          }
        });

        myPickerFrame.addFocusListener(new FocusAdapter() {
          @Override
          public void focusLost(FocusEvent e) {
            cancelPipette();
          }
        });

        myPickerFrame.setSize(50, 50);
        myPickerFrame.setUndecorated(true);
        myPickerFrame.setAlwaysOnTop(true);

        JRootPane rootPane = ((JDialog)myPickerFrame).getRootPane();
        rootPane.putClientProperty("Window.shadow", Boolean.FALSE);

        myPickOffset = new Point(0, 0);
        myCaptureRect = new Rectangle(-4, -4, 8, 8);
        myHotspot = new Point(14, 16);

        myZoomRect = new Rectangle(0, 0, 32, 32);

        myMaskImage = UIUtil.createImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D maskG = myMaskImage.createGraphics();
        maskG.setColor(Color.BLUE);
        maskG.fillRect(0, 0, 32, 32);

        maskG.setColor(Color.RED);
        maskG.setComposite(AlphaComposite.SrcOut);
        maskG.fillRect(0, 0, 32, 32);
        maskG.dispose();

        myMagnifierImage = UIUtil.createImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = myMagnifierImage.createGraphics();

        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        graphics.setColor(Color.BLACK);
        AllIcons.Ide.Pipette.paintIcon(null, graphics, 14, 0);

        graphics.dispose();

        myImage = myParent.getGraphicsConfiguration().createCompatibleImage(myMagnifierImage.getWidth(), myMagnifierImage.getHeight(),
                                                                            Transparency.TRANSLUCENT);

        myGraphics = (Graphics2D)myImage.getGraphics();
        myGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        myPickerFrame.addKeyListener(new KeyAdapter() {
          @Override
          public void keyPressed(KeyEvent e) {
            switch (e.getKeyCode()) {
              case KeyEvent.VK_ESCAPE:
                cancelPipette();
                break;
              case KeyEvent.VK_ENTER:
                pickDone();
                break;
            }
          }
        });

        myTimer = new Timer(5, new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            updatePipette();
          }
        });
      }

      return myPickerFrame;
    }

    private void cancelPipette() {
      myTimer.stop();
      myPickerFrame.setVisible(false);
      if (myColorListener != null && myOldColor != null) {
        myColorListener.colorChanged(myOldColor, this);
      }
    }

    public void pickDone() {
      PointerInfo pointerInfo = MouseInfo.getPointerInfo();
      Point location = pointerInfo.getLocation();
      Color pixelColor = myRobot.getPixelColor(location.x + myPickOffset.x, location.y + myPickOffset.y);
      cancelPipette();
      if (myColorListener != null) {
        myColorListener.colorChanged(pixelColor, this);
        myOldColor = pixelColor;
      }
    }

    private void updatePipette() {
      if (myPickerFrame != null && myPickerFrame.isShowing()) {
        PointerInfo pointerInfo = MouseInfo.getPointerInfo();
        Point mouseLoc = pointerInfo.getLocation();
        myPickerFrame.setLocation(mouseLoc.x - myPickerFrame.getWidth() / 2, mouseLoc.y - myPickerFrame.getHeight() / 2);

        myPoint.x = mouseLoc.x + myPickOffset.x;
        myPoint.y = mouseLoc.y + myPickOffset.y;

        final Color c = myRobot.getPixelColor(myPoint.x, myPoint.y);
        if (!c.equals(myPreviousColor) || !mouseLoc.equals(myPreviousLocation)) {
          myPreviousColor = c;
          myPreviousLocation = mouseLoc;
          myCaptureRect.setLocation(mouseLoc.x - 2/*+ myCaptureOffset.x*/, mouseLoc.y - 2/*+ myCaptureOffset.y*/);
          myCaptureRect.setBounds(mouseLoc.x -2, mouseLoc.y -2, 5, 5);

          BufferedImage capture = myRobot.createScreenCapture(myCaptureRect);

          // Clear the cursor graphics
          myGraphics.setComposite(AlphaComposite.Src);
          myGraphics.setColor(myTransparentColor);
          myGraphics.fillRect(0, 0, myImage.getWidth(), myImage.getHeight());

          myGraphics.drawImage(capture, myZoomRect.x, myZoomRect.y, myZoomRect.width, myZoomRect.height, this);

          // cropping round image
          myGraphics.setComposite(AlphaComposite.getInstance(AlphaComposite.DST_OUT));
          myGraphics.drawImage(myMaskImage, myZoomRect.x, myZoomRect.y, myZoomRect.width, myZoomRect.height, this);

          // paint magnifier
          myGraphics.setComposite(AlphaComposite.SrcOver);
          myGraphics.drawImage(myMagnifierImage, 0, 0, this);

          // We need to create a new subImage. This forces that
          // the color picker uses the new imagery.
          //BufferedImage subImage = myImage.getSubimage(0, 0, myImage.getWidth(), myImage.getHeight());
          myPickerFrame.setCursor(myParent.getToolkit().createCustomCursor(myImage, myHotspot, "ColorPicker"));
          if (myColorListener != null) {
            myColorListenersNotifier.cancelAllRequests();
            myColorListenersNotifier.addRequest(new Runnable() {
              @Override
              public void run() {
                myColorListener.colorChanged(c, ColorPipette.this);
              }
            }, 300);
          }
        }
      }
    }

    //public static void pickColor(ColorListener listener, JComponent c) {
    //  new ColorPipette(c, new ColorListener() {
    //    @Override
    //    public void colorChanged(Color color, Object source) {
    //      ColorPicker.this.setColor(color, my);
    //    }
    //  }).pick(listener);
    //}

    public static boolean isAvailable() {
      try {
        Robot robot = new Robot();
        robot.createScreenCapture(new Rectangle(0, 0, 1, 1));
        return WindowManager.getInstance().isAlphaModeSupported();
      }
      catch (AWTException e) {
        return false;
      }
    }
  }

  // SlideComponent uses a lot of plain colors because it's a color-manipulating component.
  // Thus, warning about using JBColor doesn't apply.
  @SuppressWarnings("UseJBColor")
  public static class SlideComponent extends JComponent {
    protected static final int MARGIN = JBUI.scale(5);
    private static final Color SHADOW_COLOR = new Color(0, 0, 0, 70);
    private static final Color HEAD_COLOR = new Color(153, 51, 0);
    protected int myPointerValue = 0;
    private int myValue = 0;
    private final String myTitle;

    private final List<Consumer<Integer>> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
    private LightweightHint myTooltipHint;
    private final JLabel myLabel = new JLabel();
    private Unit myUnit = Unit.LEVEL;

    private Color myColor;

    enum Unit {
      PERCENT,
      LEVEL;

      private static final float PERCENT_MAX_VALUE = 100f;
      private static final float LEVEL_MAX_VALUE = 255f;

      private static float getMaxValue(Unit unit) {
        return LEVEL.equals(unit) ? LEVEL_MAX_VALUE : PERCENT_MAX_VALUE;
      }

      private static String formatValue(int value, Unit unit) {
        return String.format("%d%s", (int) (getMaxValue(unit) / LEVEL_MAX_VALUE * value),
                             unit.equals(PERCENT) ? "%" : "");
      }
    }

    void setUnits(Unit unit) {
      myUnit = unit;
    }

    SlideComponent(String title) {
      myTitle = title;

      addMouseMotionListener(new MouseAdapter() {
        @Override
        public void mouseDragged(MouseEvent e) {
          processMouse(e);
        }
      });

      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          processMouse(e);
        }

        @Override
        public void mouseClicked(MouseEvent e) {
          updateBalloonText();
        }

        @Override
        public void mouseEntered(MouseEvent e) {
          updateBalloonText();
        }

        @Override
        public void mouseMoved(MouseEvent e) {
          updateBalloonText();
        }

        @Override
        public void mouseExited(MouseEvent e) {
          if (myTooltipHint != null) {
            myTooltipHint.hide();
            myTooltipHint = null;
          }
        }
      });

      addMouseWheelListener(new MouseWheelListener() {
        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
          final int amount = e.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL ? e.getUnitsToScroll() * e.getScrollAmount() :
                             e.getWheelRotation() < 0 ? -e.getScrollAmount() : e.getScrollAmount();
          int pointerValue = myPointerValue + amount;
          pointerValue = pointerValue < MARGIN ? MARGIN : pointerValue;
          int size = getWidth();
          pointerValue = pointerValue > (size - MARGIN) ? size - MARGIN : pointerValue;

          myPointerValue = pointerValue;
          myValue = pointerValueToValue(myPointerValue);

          repaint();
          fireValueChanged();
        }
      });

      addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          setValue(getValue());
          fireValueChanged();
          repaint();
        }
      });
    }

    public void setColor(Color color) {
      myColor = color;
    }

    public void setHue(float hue) {
      float[] hsv = Color.RGBtoHSB(myColor.getRed(), myColor.getGreen(), myColor.getBlue(), null);
      if (Math.abs(hue - hsv[0]) > 0.01) {
        setColor(Color.getHSBColor(hue, hsv[1], hsv[2]));
      }
    }


    private void updateBalloonText() {
      final Point point = new Point(myPointerValue, 0);
      myLabel.setText(myTitle + ": " + Unit.formatValue(myValue, myUnit));
      if (myTooltipHint == null) {
        myTooltipHint = new LightweightHint(myLabel);
        myTooltipHint.setCancelOnClickOutside(false);
        myTooltipHint.setCancelOnOtherWindowOpen(false);

        final HintHint hint = new HintHint(this, point)
          .setPreferredPosition(Balloon.Position.above)
          .setBorderColor(Color.BLACK)
          .setAwtTooltip(true)
          .setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD))
          .setTextBg(HintUtil.INFORMATION_COLOR)
          .setShowImmediately(true);

        final Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        myTooltipHint.show(this, point.x, point.y, owner instanceof JComponent ? (JComponent)owner : null, hint);
      }
      else {
        myTooltipHint.setLocation(new RelativePoint(this, point));
      }
    }

    @Override
    protected void processMouseMotionEvent(MouseEvent e) {
      super.processMouseMotionEvent(e);
      updateBalloonText();
    }

    private void processMouse(MouseEvent e) {
      int pointerValue = e.getX();
      pointerValue = pointerValue < MARGIN ? MARGIN : pointerValue;
      int size = getWidth();
      pointerValue = pointerValue > (size - MARGIN) ? size - MARGIN : pointerValue;

      setValue(pointerValueToValue(pointerValue));

      repaint();
      fireValueChanged();
    }

    public void addListener(Consumer<Integer> listener) {
      myListeners.add(listener);
    }

    private void fireValueChanged() {
      for (Consumer<Integer> listener : myListeners) {
        listener.consume(myValue);
      }
    }

    // 0 - 255
    public void setValue(int value) {
      myPointerValue = valueToPointerValue(value);
      myValue = value;
    }

    public int getValue() {
      return myValue;
    }

    protected int pointerValueToValue(int pointerValue) {
      pointerValue -= MARGIN;
      float proportion = (getWidth() - 2 * MARGIN) / 255f;
      return (int)(pointerValue / proportion);
    }

    protected int valueToPointerValue(int value) {
      float proportion = (getWidth() - 2 * MARGIN) / 255f;
      return MARGIN + (int)(value * proportion);
    }

    @Override
    public Dimension getPreferredSize() {
      return JBUI.size(100, 22);
    }

    @Override
    public Dimension getMinimumSize() {
      return JBUI.size(50, 22);
    }

    @Override
    public Dimension getMaximumSize() {
      return new Dimension(Integer.MAX_VALUE, JBUI.scale(getPreferredSize().height));
    }

    @Override
    public final void setToolTipText(String text) {
      // disable tooltips
    }

    @Override
    protected void paintComponent(Graphics g) {
      final Graphics2D g2d = (Graphics2D)g;
      Color color = new Color(myColor.getRGB());
      Color transparent = ColorUtil.toAlpha(Color.WHITE, 0);

      Rectangle clip = new Rectangle(MARGIN, JBUI.scale(7), getWidth() - 2 * MARGIN, JBUI.scale(12));
      GraphicsUtil.paintCheckeredBackground(g2d, clip);

      g2d.setPaint(UIUtil.getGradientPaint(0f, 0f, transparent, getWidth(), 0f, color));
      g.fillRect(clip.x, clip.y, clip.width, clip.height);

      drawKnob(g2d, myPointerValue, JBUI.scale(7));
    }

    protected static void drawKnob(Graphics2D g2d, int x, int y) {
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      x -= JBUI.scale(6);

      Polygon polygon = new Polygon();
      polygon.addPoint(x + JBUI.scale(1), y - JBUI.scale(5));
      polygon.addPoint(x + JBUI.scale(13), y - JBUI.scale(5));
      polygon.addPoint(x + JBUI.scale(7), y + JBUI.scale(7));

      g2d.setColor(SHADOW_COLOR);
      g2d.fill(polygon);

      polygon.reset();
      polygon.addPoint(x, y - JBUI.scale(6));
      polygon.addPoint(x + JBUI.scale(12), y - JBUI.scale(6));
      polygon.addPoint(x + JBUI.scale(6), y + JBUI.scale(6));

      g2d.setColor(HEAD_COLOR);
      g2d.fill(polygon);
    }
  }

  @SuppressWarnings("UseJBColor")
  public static class HueSlideComponent extends SlideComponent {
    private final Color[] myColors = {Color.RED, Color.ORANGE, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED};
    private final float[] myPoints = new float[myColors.length];
    private float myHue;

    HueSlideComponent(String title) {
      super(title);
      int i = 0;
      for (Color color : myColors) {
        if (color.equals(Color.RED) && i != 0) {
          myPoints[i++] = 1.0f;
        }
        else {
          myPoints[i++] = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null)[0];
        }
      }
    }

    @Override
    protected int pointerValueToValue(int pointerValue) {
      pointerValue -= MARGIN;
      float proportion = (getWidth() - 2 * MARGIN) / 360f;
      return (int)(pointerValue / proportion);
    }

    @Override
     protected int valueToPointerValue(int value) {
      float proportion = (getWidth() - 2 * MARGIN) / 360f;
      return MARGIN + (int)(value * proportion);
    }

    public void setHueValue(float hue) {
      if (Math.abs(hue - myHue) > 0.01) {
        myHue = hue;
        super.setValue(Math.round(360 * hue));
      }
    }

    @Override
    public void setValue(int value) {
      super.setValue(value);
      setHueValue(value / 360.0f);
    }


    @Override
    protected void paintComponent(Graphics g) {
      final Graphics2D g2d = (Graphics2D)g;

      g2d.setPaint(new LinearGradientPaint(new Point2D.Double(0, 0), new Point2D.Double(getWidth() - 2 * MARGIN, 0), myPoints, myColors));
      g.fillRect(MARGIN, JBUI.scale(7), getWidth() - 2 * MARGIN, JBUI.scale(12));
      drawKnob(g2d, valueToPointerValue(Math.round(myHue * 360)), JBUI.scale(7));
    }
  }
}

interface ColorListener {
  void colorChanged(Color color, Object source);
}

