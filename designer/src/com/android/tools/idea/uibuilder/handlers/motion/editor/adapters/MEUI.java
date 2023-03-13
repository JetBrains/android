/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.editor.adapters;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBUI;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Image;
import java.awt.Insets;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.border.Border;
import org.jetbrains.annotations.NotNull;

/**
 * The access to platform independent UI features allow us to run using the JB components as well as the stand alone.
 */
public class MEUI {

  public static final Color ourMySelectedTextColor = new JBColor(0xEAEAEA, 0xCCCCCC);

  public static int scale(int i) {
    return JBUI.scale(i);
  }

  public static Dimension size(int width, int height) {
    return JBUI.size(width, height);
  }

  public static Insets insets(int top, int left, int bottom, int right) {
    return JBUI.insets(top, left, bottom, right);
  }

  public static Insets dialogTitleInsets() { return MEUI.insets(8, 12, 0, 12); }
  public static Insets dialogSeparatorInsets() { return MEUI.insets(8, 0, 0, 0); }
  public static Insets dialogLabelInsets() { return MEUI.insets(8, 12, 0, 12); }
  public static Insets dialogControlInsets() { return MEUI.insets(4, 14, 0, 12); }
  public static Insets dialogBottomButtonInsets() { return MEUI.insets(12, 12, 12, 12); }

  public static MEComboBox<String> makeComboBox(String[] a) {
    return new MEComboBox<String>(a);
  }

  public static void invokeLater(Runnable runnable) {
    ApplicationManager.getApplication().invokeLater(runnable);
  }

  public static Color getBorderColor() {
    return JBColor.border();
  }

  public static Border getPanelBottomBorder() {
    return JBUI.Borders.customLine(MEUI.getBorderColor(), 0, 0,1,0);
  }

  private static Color makeColor(String name, int rgb, int darkRGB) {
    return JBColor.namedColor(name, new JBColor(rgb, darkRGB));
  }

  public static final int ourLeftColumnWidth = JBUI.scale(150);
  public static final int ourHeaderHeight = JBUI.scale(30);

  public static final Color ourErrorColor = makeColor("UIDesigner.motion.Error.foreground", 0x8f831b, 0xffa31b);
  public static final Color ourBannerColor = makeColor("UIDesigner.motion.Notification.background", 0xfff8d1, 0x1d3857);
  public static final Color myTimeCursorColor = makeColor("UIDesigner.motion.TimeCursor.selectedForeground", 0xFF4A81FF, 0xFFB4D7FF);
  public static final Color myGridColor = makeColor("UIDesigner.motion.timeLine.disabledBorderColor", 0xDDDDDD, 0x555555);
  public static final Color ourMySelectedKeyColor = makeColor("UIDesigner.motion.Key.selectedForeground", 0xff3da1f1, 0xff3dd1f1);
  public static final Color ourPrimaryPanelBackground = makeColor("UIDesigner.motion.PrimaryPanel.background", 0xf5f5f5, 0x2D2F31);
  public static final Color ourSecondaryPanelBackground = makeColor("UIDesigner.motion.SecondaryPanel.background", 0xfcfcfc, 0x313435);
  public static final Color ourAvgBackground = makeColor("UIDesigner.motion.ourAvg.background", 0xf8f8f8, 0x2f3133);
  public static final Color ourBorder = makeColor("UIDesigner.motion.borderColor", 0xc9c9c9, 0x242627);
  public static final Color ourTextColor = makeColor("UIDesigner.motion.Component.foreground", 0x2C2C2C, 0x9E9E9E);
  public static final Color ourSecondaryPanelHeaderTitleColor = makeColor("UIDesigner.motion.SecondaryPanel.header.foreground", 0x000000, 0xbababa);
  public static final Color ourSecondaryHeaderBackgroundColor = makeColor("UIDesigner.motion.SecondaryPanel.header.background", 0xf2f2f2, 0x3c3f40);

  public static final Color ourMySelectedLineColor = new Color(0x3879d9);

  public static BufferedImage createImage(int w, int h, int type) {
    return ImageUtil.createImage(w, h, type);
  }

  private static final double alpha = 0.7;
  /** List of colors for graphs. */
  public static JBColor[] graphColors = {
    (JBColor) ColorUtil.withAlpha(new JBColor(0xa6bcc9, 0x8da9ba), alpha),
    (JBColor) ColorUtil.withAlpha(new JBColor(0xaee3fe, 0x86d5fe), alpha),
    (JBColor) ColorUtil.withAlpha(new JBColor(0xf8a981, 0xf68f5b), alpha),
    (JBColor) ColorUtil.withAlpha(new JBColor(0x89e69a, 0x67df7d), alpha),
    (JBColor) ColorUtil.withAlpha(new JBColor(0xb39bde, 0x9c7cd4), alpha),
    (JBColor) ColorUtil.withAlpha(new JBColor(0xea85aa, 0xe46391), alpha),
    (JBColor) ColorUtil.withAlpha(new JBColor(0x6de9d6, 0x49e4cd), alpha),
    (JBColor) ColorUtil.withAlpha(new JBColor(0xe3d2ab, 0xd9c28c), alpha),
    (JBColor) ColorUtil.withAlpha(new JBColor(0x0ab4ff, 0x0095d6), alpha),
    (JBColor) ColorUtil.withAlpha(new JBColor(0x1bb6a2, 0x138173), alpha),
    (JBColor) ColorUtil.withAlpha(new JBColor(0x9363e3, 0x7b40dd), alpha),
    (JBColor) ColorUtil.withAlpha(new JBColor(0xe26b27, 0xc1571a), alpha),
    (JBColor) ColorUtil.withAlpha(new JBColor(0x4070bf, 0x335a99), alpha),
    (JBColor) ColorUtil.withAlpha(new JBColor(0xc6c54e, 0xadac38), alpha),
    (JBColor) ColorUtil.withAlpha(new JBColor(0xcb53a3, 0xb8388e), alpha),
    (JBColor) ColorUtil.withAlpha(new JBColor(0x3d8eff, 0x1477ff), alpha)};

  /**
   * TODO: support intellij copy paste
   *
   * @param copyListener
   * @param pasteListener
   * @param panel
   */
  public static void addCopyPaste(ActionListener copyListener, ActionListener pasteListener, JComponent panel) {
    // TODO ideally support paste and copy with control or command
    KeyStroke copy = KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_MASK, false);
    KeyStroke copy2 = KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.META_MASK, false);
    KeyStroke paste = KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_MASK, false);
    KeyStroke paste2 = KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.META_MASK, false);
    panel.registerKeyboardAction(copyListener, "Copy", copy, JComponent.WHEN_FOCUSED);
    panel.registerKeyboardAction(copyListener, "Copy", copy2, JComponent.WHEN_FOCUSED);
    panel.registerKeyboardAction(pasteListener, "Paste", paste, JComponent.WHEN_FOCUSED);
    panel.registerKeyboardAction(pasteListener, "Paste", paste2, JComponent.WHEN_FOCUSED);
  }

  //0c283e
  public static class CSPanel {
    public static final Color our_SelectedFocusBackground =
      makeColor("UIDesigner.motion.CSPanel.SelectedFocusBackground", 0x3973d6, 0x2E65CA);
    public static final Color our_SelectedBackground =
      makeColor("UIDesigner.motion.CSPanel.SelectedBackground", 0xD3D3D3, 0x0C283E);
  }

  public static class Overview {
    public static final Color ourCS_Background = makeColor("UIDesigner.motion.ourCS.background", 0xFFFFFF, 0x515658);
    public static final Color ourCS_SelectedBackground =
      makeColor("UIDesigner.motion.ourCS_SelectedBackground.selectionInactiveBackground", 0xD3D3D3, 0x797B7C);
    public static final Color ourCS_SelectedFocusBackground =
      makeColor("UIDesigner.motion.ourCS_SelectedFocusBackground.selectionForeground", 0xD1E7FD, 0x7691AB);


    public static final Color ourCS_Border = makeColor("UIDesigner.motion.ourCS_Border.borderColor", 0xBEBEBE, 0x6D6D6E);
    public static final Color ourCS_HoverBorder = makeColor("UIDesigner.motion.hoverBorderColor", 0x7A7A7A, 0xA1A1A1);
    public static final Color ourCS_SelectedBorder =
      makeColor("UIDesigner.motion.ourCS_SelectedBorder.pressedBorderColor", 0x7A7A7A, 0xA1A1A1);
    public static final Color ourCS_SelectedFocusBorder =
      makeColor("UIDesigner.motion.ourCS_SelectedFocusBorder.focusedBorderColor", 0x1886F7, 0x9CCDFF);

    public static final Color ourCS_TextColor = makeColor("UIDesigner.motion.ourCS_TextColor.foreground", 0x686868, 0xc7c7c7);
    public static final Color ourCS_FocusTextColor = makeColor("UIDesigner.motion.cs_FocusText.infoForeground", 0x888888 , 0xC7C7C7);
    public static final Color ourML_BarColor = makeColor("UIDesigner.motion.ourML_BarColor.separatorColor", 0xd8d8d8, 0x808385);
    public static final Color ourPositionColor = makeColor("UIDesigner.motion.PositionMarkColor", 0XF0A732, 0XF0A732);
  }

  public static class Graph {
    public static final Color ourG_Background = makeColor("UIDesigner.motion.motionGraph.background", 0xfcfcfc, 0x313334);
    public static final Color ourG_line = makeColor("UIDesigner.motion.graphLine.lineSeparatorColor", 0xE66F9A, 0xA04E6C);
    public static final Color ourCursorTextColor = makeColor("UIDesigner.motion.CursorTextColor.foreground", 0xFFFFFF, 0x000000);
  }

  public static final Color ourDashedLineColor = new JBColor(0xA0A0A0, 0xBBBBBB);

  public static final int DIR_LEFT = 0;
  public static final int DIR_RIGHT = 1;
  public static final int DIR_TOP = 2;
  public static final int DIR_BOTTOM = 3;

  public static Font getToolBarButtonSmallFont() {
    return JBUI.Fonts.smallFont();
  }

  public static JButton createToolBarButton(Icon icon, String tooltip) {
    return createToolBarButton(icon, null, tooltip);
  }

  public static JButton createToolBarButton(Icon icon, Icon disable_icon, String tooltip) {
    return new MEActionButton(icon, disable_icon, tooltip);
  }

  public interface Popup {
    void dismiss();

    void hide();

    void show();
  }

  public static Popup createPopup(JComponent component, JComponent local) {
    return new Popup() {
      private final JComponent myComponent = component;
      private final JComponent myLocal = local;
      private Balloon myBalloon = create();

      @Override
      public void dismiss() {
        hide();
      }

      @Override
      public void hide() {
        if (myBalloon != null) {
          myBalloon.hide();
          myBalloon = null;
        }
      }

      @Override
      public void show() {
        if (myBalloon == null) {
          myBalloon = create();
        } else {
          myBalloon.show(RelativePoint.getSouthOf(myLocal), Balloon.Position.below);
        }
      }

      private Balloon create() {
        Balloon balloon = JBPopupFactory.getInstance().createBalloonBuilder(myComponent)
          .setFillColor(ourSecondaryPanelBackground)
          .setBorderColor(JBColor.border())
          .setBorderInsets(JBUI.insets(1))
          .setAnimationCycle(Registry.intValue("ide.tooltip.animationCycle"))
          .setShowCallout(true)
          .setPositionChangeYShift(2)
          .setHideOnKeyOutside(false)
          .setHideOnAction(false)
          .setBlockClicksThroughBalloon(true)
          .setRequestFocus(true)
          .setDialogMode(false)
          .createBalloon();
        balloon.addListener(new JBPopupListener() {
          @Override
          public void onClosed(@NotNull LightweightWindowEvent event) {
            MEActionButton button = (myLocal instanceof MEActionButton ? (MEActionButton)myLocal : null);
            if (button != null) {
              button.getRootPane().requestFocusInWindow();
              button.setPopupIsShowing(false);
            }
          }
        });
        balloon.show(RelativePoint.getSouthOf(myLocal), Balloon.Position.below);
        MEActionButton button = (myLocal instanceof MEActionButton ? (MEActionButton)myLocal : null);
        if (button != null) {
          button.setPopupIsShowing(true);
        }
        return balloon;
      }
    };
  }

  public static void copy(MTag tag) {
    CopyPasteManager.getInstance().setContents(new StringSelection(MTag.serializeTag(tag)));
  }

  public static void cut(MTag tag) {
    CopyPasteManager.getInstance().setContents(new StringSelection(MTag.serializeTag(tag)));
    tag.getTagWriter().deleteTag().commit("cut");
  }

  public static Icon generateImageIcon(Image image) {
    return IconUtil.createImageIcon(image);
  }
}