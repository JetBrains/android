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
package com.android.tools.idea.uibuilder.handlers.motion.editor.ui;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEIcons;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEScenePicker;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEScenePicker.HitElementListener;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEUI;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.StringMTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.utils.Debug;
import com.android.tools.idea.uibuilder.handlers.motion.editor.utils.Drawing;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.LinearGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;

/**
 * The overview panel displays graphically the layout and the transitions and allows selection for ether.
 */
class OverviewPanel extends JPanel {
  public static final boolean DEBUG = false;
  private static final int ROUND_SIZE = 5;
  private static final int MAX_OPTIMIZE_LINES = 7;
  static Font ourBaseFont = new Font("SansSerif", Font.BOLD, 12);
  MEScenePicker picker = new MEScenePicker();
  MTag mMotionScene;
  MTag[] mConstraintSet;
  MTag[] mTransitions;
  int[] mTransitionStart;
  int[] mTransitionEnd;
  int[] mOnActionSize;
  String[] mConstraintSetNames;
  int mTransitionSelected = -1;
  int mConstraintSetSelected = -1;
  private MTag mLayout;
  MTagActionListener mListener;
  private Stroke mThickStroke = new BasicStroke(2);
  private Stroke mDashStroke = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1, new float[]{5, 5}, 0);
  GeneralPath mPath = new GeneralPath();
  int mRectPathLen = 4;
  int[] mRectPathX = new int[mRectPathLen];
  int[] mRectPathY = new int[mRectPathLen];
  private boolean mComputedDerivedLines = false;
  private static int MIN_CS_WIDTH = MEUI.scale(50);
  private static int MAX_CS_WIDTH = MEUI.scale(80);
  boolean mHighlightStart = false;
  boolean mHighlightEnd = false;
  private static final String MAIN_TOOL_TIP = null;

  private static int CS_GAP = MEUI.scale(20);
  private static int ARROW_HEIGHT = 10;
  private int arrow_spacing;
  private MTag mMouseOverObject;
  private static final Stroke ourFatStroke = new BasicStroke(5f);
  private static final Stroke ourSelectedStroke = new BasicStroke(2f);
  private MTag mMouseOverDerived;
  private float mTransitionProgress = Float.NaN;
  private boolean mControlDown = false;
  private final ActionGroup myActionGroup;
  private boolean mTransitionHovered = false;
  private JPopupMenu mPopupMenu = new JPopupMenu();
  private boolean mShowSaveGif = StudioFlags.NELE_MOTION_SAVE_GIF.get();

  /**
   * Defines the progress along the selected Transition
   *
   * @param pos
   */
  public void setTransitionProgress(float pos) {
    mTransitionProgress = pos;
    repaint();
  }

  static class DerivedSetLine {
    public MTag mConstraintSet;
    public String mDerivedFrom;
    int mSrcX;
    int mDstX;
    int mPathYOffset = 10;
  }

  DerivedSetLine[] mDerivedLines = new DerivedSetLine[10];

  {
    for (int i = 0; i < mDerivedLines.length; i++) {
      mDerivedLines[i] = new DerivedSetLine();
    }
  }

  int mTotalDerivedLines = 0;

  public void setActionListener(MTagActionListener l) {
    mListener = l;
  }

  public OverviewPanel() {
    setBackground(MEUI.ourPrimaryPanelBackground);
    myActionGroup = createPopupActionGroup();

    setToolTipText("OverViewPanel showing ConstraintSets and Transitions");
    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        if (SwingUtilities.isRightMouseButton(e) && e.getClickCount() == 1 && mTransitionHovered && mShowSaveGif){
          showPopupMenu(e);
          return;
        }
        requestFocusInWindow();
        updateFromMouse(e.getX(), e.getY(), false);
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        mControlDown = e.isControlDown();
        updateFromMouse(e.getX(), e.getY(), true);
      }
    });

    addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseDragged(MouseEvent e) {
        updateFromMouse(e.getX(), e.getY(), false);
      }

      @Override
      public void mouseMoved(MouseEvent e) {
        updateFromMouse(e.getX(), e.getY(), false);
      }
    });

    picker.setSelectListener(new HitElementListener() {
      @Override
      public void over(Object over, double dist) {
        if (DEBUG) {
          Debug.log("over " + over);
        }
      }
    });

    setFocusable(true);
    setRequestFocusEnabled(true);

    addKeyListener(new KeyAdapter() {

                     @Override
                     public void keyPressed(KeyEvent e) {
                       mControlDown = e.isControlDown();
                       int flag = mControlDown ? MTagActionListener.CONTROL_FLAG : 0;
                       if (mTransitionSelected >= 0) {
                         switch (e.getExtendedKeyCode()) {
                           case KeyEvent.VK_DELETE:
                           case KeyEvent.VK_BACK_SPACE:
                             if (mTransitions.length > 1) {
                               mListener.delete(new MTag[]{mTransitions[mTransitionSelected]}, 0);
                             }
                             else {
                               Notification notification = new Notification(
                                 "Motion Editor",
                                 "Can not remove last transition: at least one required", NotificationType.WARNING
                               );
                               Notifications.Bus.notify(notification);
                               AppExecutorUtil.getAppScheduledExecutorService().schedule(notification::expire, 2, TimeUnit.SECONDS);
                             }
                             return;
                           case KeyEvent.VK_UP:
                             setTransitionSetIndex((mTransitionSelected - 1 + mTransitions.length) % mTransitions.length);
                             break;
                           case KeyEvent.VK_DOWN:
                             setConstraintSetIndex(0);
                             if (mListener != null) {
                               mListener.select(mConstraintSet[0], flag);
                             }
                             break;
                           case KeyEvent.VK_LEFT:
                             setTransitionSetIndex((mTransitionSelected - 1 + mTransitions.length) % mTransitions.length);
                             break;
                           case KeyEvent.VK_RIGHT:
                             setTransitionSetIndex((mTransitionSelected + 1) % mTransitions.length);
                             break;
                         }
                         if (mTransitionSelected >= 0 && mListener != null) {
                           mListener.select(mTransitions[mTransitionSelected], flag);
                         }
                       }
                       else if (mConstraintSetSelected >= 0) {
                         switch (e.getKeyCode()) {
                           case KeyEvent.VK_C:
                             if (e.isControlDown() || e.isMetaDown()) {
                               MEUI.copy(mConstraintSet[mConstraintSetSelected - 1]);
                             }
                             break;
                           case KeyEvent.VK_V:
                             if (e.isControlDown() || e.isMetaDown()) {
                               paste();
                             }
                             break;

                           case KeyEvent.VK_DELETE:
                           case KeyEvent.VK_BACK_SPACE:
                             if (mConstraintSetSelected > 0 && (mConstraintSetSelected - 1) < mConstraintSet.length) {
                               mListener.delete(new MTag[]{mConstraintSet[mConstraintSetSelected - 1]}, 0);
                             }
                             return;
                           case KeyEvent.VK_UP:
                             if (mTransitions.length > 0) {
                               setTransitionSetIndex(0);
                               if (mListener != null) {
                                 mListener.select(mTransitions[0], flag);
                               }
                             }
                             break;
                           case KeyEvent.VK_DOWN:
                             setConstraintSetIndex((mConstraintSetSelected + 1) % (mConstraintSet.length + 1));
                             break;
                           case KeyEvent.VK_LEFT:
                             setConstraintSetIndex((mConstraintSetSelected - 1 + 1 + mConstraintSet.length) % (mConstraintSet.length + 1));
                             break;
                           case KeyEvent.VK_RIGHT:
                             setConstraintSetIndex((mConstraintSetSelected + 1) % (mConstraintSet.length + 1));
                             break;
                         }
                         if (mListener != null) {
                           if (mConstraintSetSelected == 0) {
                             mListener.select(mLayout, flag);
                           }
                           else if (mConstraintSetSelected > 0) {
                             if (mConstraintSet.length > mConstraintSetSelected - 1) {
                               mListener.select(mConstraintSet[mConstraintSetSelected - 1], flag);
                             }
                           }
                         }
                       }
                     }
                   }
    );
  }

  private void paste() {
    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

    try {
      String buff = (String)(clipboard.getContents(this).getTransferData(DataFlavor.stringFlavor));
      StringMTag pastedTag = StringMTag.parse(buff);
      HashMap<String, MTag.Attribute> attr = pastedTag.getAttrList();

      if ("ConstraintSet".equals(pastedTag.getTagName())) {
        MTag.TagWriter writer = mMotionScene.getChildTagWriter(MotionSceneAttrs.Tags.CONSTRAINTSET);
        if (writer == null) {
          return;
        }
        for (String s : attr.keySet()) {
          MTag.Attribute a = attr.get(s);
          if (a == null || a.mAttribute.equals("id")) { // add _c to the id
            String value = a.mValue;
            if (value.matches(".*_c[0123456789]+")) {
              int n = value.lastIndexOf("_c");
              int end = Integer.parseInt(value.substring(n + 2));
              String start = value.substring(0, n);
              value = start + "_c" + (end + 1);
            }
            else if (value.matches(".*_c")) {
              value = value + "1";
            }
            else {
              value = value + "_c";
            }
            writer.setAttribute(a.mNamespace, a.mAttribute, value);
            continue;
          }
          writer.setAttribute(a.mNamespace, a.mAttribute, a.mValue);
        }
        addRecursive(pastedTag.getChildTags(), writer);
        writer.commit("paste");
      }
    }
    catch (UnsupportedFlavorException e) {
      e.printStackTrace();
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void addRecursive(StringMTag[] children, MTag.TagWriter writer) {
    if (children == null || children.length == 0) {
      return;
    }
    for (StringMTag child : children) {
      MTag.TagWriter childWriter = writer.getChildTagWriter(child.getTagName());
      HashMap<String, MTag.Attribute> attr = child.getAttrList();
      for (String s : attr.keySet()) {
        MTag.Attribute a = attr.get(s);
        childWriter.setAttribute(a.mNamespace, a.mAttribute, a.mValue);
        addRecursive(child.getChildTags(), childWriter);
      }
    }
  }

  private void updateFromMouse(int x, int y, boolean select) {
    MTag[] objects = new MTag[1];
    DerivedSetLine[] line = new DerivedSetLine[1];
    picker.setSelectListener(new HitElementListener() { // todo memory wasteful
      @Override
      public void over(Object over, double dist) {
        if (over instanceof MTag) {
          objects[0] = ((MTag)over);
        }
        else {
          line[0] = (DerivedSetLine)over;
        }
      }
    });
    picker.find(x, y);
    if (line[0] != null && mMouseOverDerived != line[0].mConstraintSet) {
      mMouseOverDerived = line[0].mConstraintSet;
      String id = Utils.stripID(line[0].mConstraintSet.getAttributeValue("id"));
      setToolTipText(id + " derives from " + line[0].mDerivedFrom);
      mMouseOverObject = null;
      repaint();
      return;
    }
    else if (line[0] == null && mMouseOverDerived != null) {
      mMouseOverDerived = null;
      setToolTipText(MAIN_TOOL_TIP);
      repaint();
    }

    if (objects[0] == null) {
      if (mMouseOverObject != null) {
        mMouseOverObject = null;
        setToolTipText(MAIN_TOOL_TIP);
        repaint();
      }
      return;
    }

    MTag found = objects[0];
    if (select && mListener != null) {
      mListener.select(found, mControlDown ? MTagActionListener.CONTROL_FLAG : 0);
    }
    else {
      if (mMouseOverObject != found) {
        mMouseOverObject = found;

        switch (found.getTagName()) {
          case "Transition":
            setToolTipText("Transition " + Utils.formatTransition(found));
            mTransitionHovered = true;
            break;
          case "ConstraintSet":
            setToolTipText("ConstraintSet " + Utils.stripID(found.getAttributeValue("id")));
            mTransitionHovered = false;
            break;
          default:
            setToolTipText("Original MotionLayout");
            mTransitionHovered = false;
        }

        repaint();
      }
    }
  }

  int cs_width, cs_height;

  void debugSize(String str) {
    int w = getWidth();
    int h = getHeight();
    System.out.println(str + " act w,h = " + w + "," + h);
    Dimension d = getPreferredSize();
    System.out.println(str + " prf w,h = " + d.width + "," + d.height);
    d = getMinimumSize();
    System.out.println(str + " min w,h = " + d.width + "," + d.height);
    d = getMaximumSize();
    System.out.println(str + " max w,h = " + d.width + "," + d.height);
  }

  private void calcDimensions() {
    boolean has_strings = false;
    for (int i = 0; i < mTransitions.length; i++) {
      if (null != mTransitions[i].getAttributeValue("id")) {
        has_strings = true;
        break;
      }
    }
    int w = getWidth();
    int h = getHeight();

    ///////  calc constraintSet dimensions //////
    int spaces = CS_GAP * mConstraintSet.length + 3;
    int noc = mConstraintSet.length + 1;
    cs_width = (w - CS_GAP * 2 - 2) / noc - CS_GAP;
    if (cs_width > MAX_CS_WIDTH) {
      cs_width = MAX_CS_WIDTH;
    }
    else if (cs_width < MIN_CS_WIDTH) {
      cs_width = MIN_CS_WIDTH;
    }
    int p_width = (cs_width + CS_GAP) * noc + 2 + CS_GAP;
    cs_height = (cs_width * 3) / 2;
    ///////// calc Transitions height
    int str_space = 0;
    if (has_strings) {
      int font_height = getFontMetrics(getFont()).getHeight();
      str_space = font_height / 2;
    }
    arrow_spacing = str_space + CS_GAP + ARROW_HEIGHT;
    int transition_height = CS_GAP + arrow_spacing + mTransitions.length * arrow_spacing;
    int derive_height = 3 * 12 + 10;
    int p_height = derive_height + transition_height + cs_height + CS_GAP;
    setPreferredSize(new Dimension(p_width, p_height));
  }

  /**
   * Get the x position of n'th constraint set drawing
   *
   * @param number
   * @return
   */
  private int getmConstraintSetX(int number) {
    if (number > 0) {
      return (cs_width + CS_GAP) * number + CS_GAP * 2 + 2;
    }
    return (cs_width + CS_GAP) * number + CS_GAP;
  }

  @Override
  public void paint(Graphics g) {
    if (mMotionScene == null) {
      return;
    }
    Graphics2D g2g = (Graphics2D)g;
    picker.reset();
    int w = getWidth();
    int h = getHeight();
    calcDimensions();
    int noc = mConstraintSet.length + 1;
    int space = 10;
    if (space * (noc + 1) * 2 > w) {
      space = 1;
    }
    g.setColor(getBackground());
    g.fillRect(0, 0, w, h);
    if (noc == 0) {
      return;
    }
    boolean focus = hasFocus();

    Color colorNormalLine = MEUI.Overview.ourCS_Border;
    Color colorHoverLine = MEUI.Overview.ourCS_HoverBorder;
    Color colorSelectedLine = focus ? MEUI.Overview.ourCS_SelectedFocusBorder : MEUI.Overview.ourCS_SelectedBorder;


    int csWidth = cs_width;
    Stroke stroke = g2g.getStroke();
    g2g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    int yGap = 24;
    int selectedStart = -1;
    int selectedEnd = -1;
    int constraintSetY = CS_GAP + arrow_spacing + mTransitions.length * arrow_spacing;
    int yoffset = CS_GAP + arrow_spacing - ARROW_HEIGHT / 2;
    // --------  Draw Transitions lines -------------
    Arrays.fill(mOnActionSize, 0);
    for (int i = 0; i < mTransitions.length; i++) {
      int swipes = 0;
      int clicks = 0;
      for (MTag tag : mTransitions[i].getChildren()) {
        String tagName = tag.getTagName();
        if (MotionSceneAttrs.Tags.ON_SWIPE.equals(tagName)) {
          swipes++;
        }
        if (MotionSceneAttrs.Tags.ON_CLICK.equals(tagName)) {
          clicks++;
        }
      }
      int start = mTransitionStart[i];
      int end = mTransitionEnd[i];
      int x1 = getmConstraintSetX(start + 1) + csWidth / 2;
      int x2 = getmConstraintSetX(end + 1) + csWidth / 2;
      int y = yoffset + i * arrow_spacing;
      g.setColor((mTransitionSelected == i) ? colorSelectedLine : colorNormalLine);
      if (mTransitionSelected == i) {
        selectedStart = start;
        selectedEnd = end;
        g.setColor(colorSelectedLine);
      }
      else {
        g.setColor(colorNormalLine);
      }

      boolean hoverHighlight = mMouseOverObject != null && mTransitions[i] == mMouseOverObject;
      drawTransition((Graphics2D)g, hoverHighlight, x1, x2, y, constraintSetY, mTransitions[i], Float.NaN, 0);
      if ((clicks > 0) || (swipes > 0)) {
        mOnActionSize[i] = drawActions(g, swipes, clicks, x1, x2, y);
      }
    }
    // --------  Draw Transitions lines -------------

    ((Graphics2D)g).setStroke(ourSelectedStroke);
    for (int i = 0; i < mTransitions.length; i++) {
      int swipes = 0;
      int clicks = 0;
      for (MTag tag : mTransitions[i].getChildren()) {
        String tagName = tag.getTagName();
        if (MotionSceneAttrs.Tags.ON_SWIPE.equals(tagName)) {
          swipes++;
        }
        if (MotionSceneAttrs.Tags.ON_CLICK.equals(tagName)) {
          clicks++;
        }
      }

      int start = mTransitionStart[i];
      int end = mTransitionEnd[i];
      int x1 = getmConstraintSetX(start + 1) + csWidth / 2;
      int x2 = getmConstraintSetX(end + 1) + csWidth / 2;
      int y = yoffset + i * arrow_spacing;
      g.setColor((mTransitionSelected == i) ? colorSelectedLine : colorNormalLine);
      if (mTransitionSelected != i) {
        continue;
      }
      selectedStart = start;
      selectedEnd = end;
      g.setColor(colorSelectedLine);
      float stagger = 0;
      if (mTransitionSelected == i && !Float.isNaN(mTransitionProgress)) {
        String str = mTransitions[i].getAttributeValue("staggered");
        if (str != null) {
          stagger = Float.parseFloat(str);
        }
      }
      boolean hoverHighlight = mMouseOverObject != null && mTransitions[i] == mMouseOverObject;
      drawTransition((Graphics2D)g, hoverHighlight, x1, x2, y, constraintSetY, mTransitions[i], mTransitionProgress, stagger);
      if ((clicks > 0) || (swipes > 0)) {
        drawActions(g, swipes, clicks, x1, x2, y);
      }
    }

    // --------  Draw Transitions strings -------------

    for (int i = 0; i < mTransitions.length; i++) {
      int start = mTransitionStart[i];
      int end = mTransitionEnd[i];
      int x1 = getmConstraintSetX(start + 1) + csWidth / 2;
      int x2 = getmConstraintSetX(end + 1) + csWidth / 2;
      int y = yoffset + i * arrow_spacing;
      g.setColor((mTransitionSelected == i) ? colorSelectedLine : colorNormalLine);
      if (mTransitionSelected == i) {
        selectedStart = start;
        selectedEnd = end;
        g.setColor(colorSelectedLine);
      }
      else {
        g.setColor(colorNormalLine);
      }
      picker.addLine(mTransitions[i], 3, x1, y, x2, y, 2);
      String str = mTransitions[i].getAttributeValue("id");
      if (str != null) {
        g.setFont(ourBaseFont);
        str = Utils.stripID(str);
        FontMetrics fm = g.getFontMetrics();
        Rectangle2D bounds = fm.getStringBounds(str, g);
        int strX = (x1 > x2) ? x1 - (int)bounds.getWidth() - 20 : x1 + 4;
        strX += mOnActionSize[i];
        Color tmp = g.getColor();
        g.setColor(getBackground());
        g.fillRect(strX, y - fm.getHeight(), (int)bounds.getWidth(), (int)bounds.getHeight());
        g.setColor(tmp);

        g.drawString(str, strX, y - 5);
      }
    }
    // --------  Draw ConstraintSets -------------
    int csHeight = cs_height;

    // calculate and set the font based on fitting the ConstraintSet ids
    g2g.setStroke(mThickStroke);
    g.setFont(ourBaseFont);
    FontMetrics fm = g.getFontMetrics();
    String maxString = "Layout";
    int maxStringWidth = maxString.length();

    for (int i = 0; i < mConstraintSetNames.length; i++) {
      int width = mConstraintSetNames[i].length();
      if (width > maxStringWidth) {
        maxStringWidth = width;
        maxString = mConstraintSetNames[i];
      }
    }
    maxStringWidth = fm.stringWidth(maxString);
    int margin = MEUI.scale(1);
    g.setFont(ourBaseFont);
    while (csWidth < maxStringWidth + margin) {
      float f = g.getFont().getSize() / 1.4f;
      g.setFont(g.getFont().deriveFont(f));
      fm = g.getFontMetrics();
      maxStringWidth = fm.stringWidth(maxString);
    }

    int line_x = getmConstraintSetX(1) - CS_GAP - 1;
    int line_y = constraintSetY;
    g.setColor(colorNormalLine);
    g.drawLine(line_x, line_y, line_x, line_y + cs_height);

    //  ---------  Draw Empty State (no constraints or transitions) -------------
    if (mTransitions.length == 0 && mConstraintSet.length == 0) {
      Image constraintSetImage = MEIcons.getUnscaledIconImage(MEIcons.CREATE_CONSTRAINTSET);
      Image transitionImage = MEIcons.getUnscaledIconImage(MEIcons.CREATE_TRANSITION);
      ((Graphics2D)g).drawImage(constraintSetImage,
                            line_x + 72,
                            line_y - 30 + cs_height / 2,
                            constraintSetImage.getWidth(null),
                            constraintSetImage.getHeight(null),
                            null);
      ((Graphics2D)g).drawImage(transitionImage,
                            line_x + 62,
                            line_y + 4 + cs_height / 2,
                            transitionImage.getWidth(null),
                            transitionImage.getHeight(null),
                            null);
      ((Graphics2D)g).drawString("      Use       to create a new", line_x + 20, line_y - 16 + cs_height / 2);
      ((Graphics2D)g).drawString("     constraint set, and use to", line_x + 20, line_y + cs_height / 2);
      ((Graphics2D)g).drawString("create       a transition between", line_x + 20, line_y + 16 + cs_height / 2);
      ((Graphics2D)g).drawString("        two constraint sets", line_x + 20, line_y + 32 + cs_height / 2);
    }

    // ---------  Draw ConstraintSet Rectangles -------------
    for (int i = 0; i <= mConstraintSet.length; i++) {
      int setIndex = i - 1;
      if (i == 0) setIndex = mConstraintSet.length;
      int x = getmConstraintSetX(i);
      int y = constraintSetY;
      boolean hover = false;
      boolean drawLayout = i == 0;
      boolean selected = (mConstraintSetSelected == i);
      if (selectedEnd == setIndex || selectedStart == setIndex) {
        selected = true;
      }
      boolean transitionHighlightStart = mHighlightStart && (selectedStart == setIndex);
      boolean transitionHighlightEnd = mHighlightEnd && (selectedEnd == setIndex);

      if (mMouseOverObject != null) {
        hover = ((drawLayout) ? mLayout : mConstraintSet[setIndex]) == mMouseOverObject;
      }
      if (!drawLayout) {
        picker.addRect(mConstraintSet[setIndex], 3, x, y, x + csWidth, y + csHeight);
      }
      else {
        picker.addRect(mLayout, 3, x, y, x + csWidth, y + csHeight);
      }

      String name = drawLayout ? "Layout" : mConstraintSetNames[setIndex];
      Color colorBackground = MEUI.Overview.ourCS_Background;
      Color textColor = MEUI.Overview.ourCS_TextColor;
      if (mConstraintSetSelected == i) {
        if (focus) {
          colorBackground = MEUI.Overview.ourCS_SelectedFocusBackground;
          textColor = MEUI.Overview.ourCS_FocusTextColor;
        }
        else {
          colorBackground = MEUI.Overview.ourCS_SelectedBackground;
        }
      }

      g.setColor(colorBackground);
      g.fillRoundRect(x, y, csWidth, csHeight, space, space);

      Color color = colorNormalLine;

      if (hover) {
        color = colorHoverLine;
      }
      if (selected) {
        color = colorSelectedLine;
      }
      if (transitionHighlightStart || transitionHighlightEnd) { // selected transition end
        color = MEUI.Overview.ourPositionColor;
      }

      g.setColor(color);
      g.drawRoundRect(x, y, csWidth, csHeight, space, space);

      if (i == 0) {
        //  g.setColor((hover) ? MEUI.Overview.ourCS_Hover : MEUI.Overview.ourLayoutColor);
        g.setColor(colorBackground);

        g.fillRoundRect(x, y, csWidth, csHeight, space, space);
        g.setColor(MEUI.Overview.ourML_BarColor);
        g.fillRoundRect(x, y, csWidth, csHeight / 4 + 1, space, space);
        g.setColor(color);
        g.drawRoundRect(x, y, csWidth, csHeight, space, space);
        g.fillRect(x, y + csHeight / 4, csWidth, 2);
      }
      g.setColor(textColor);

      if (drawLayout) {
        int stringWidth = fm.stringWidth("Motion");
        int mx = x + (csWidth - stringWidth) / 2;
        int my = y + csHeight / 2 - fm.getHeight() + fm.getAscent();

        g.drawString("Motion", mx, my);

        stringWidth = fm.stringWidth("Layout");
        int lx = x + (csWidth - stringWidth) / 2;
        int ly = y + csHeight / 2 + fm.getAscent();

        g.drawString("Layout", lx, ly);
      }
      else {
        int stringWidth = fm.stringWidth(name);
        x += (csWidth - stringWidth) / 2;
        y += (csHeight - fm.getHeight()) / 2 + fm.getAscent();

        g.drawString(name, x, y);
      }
    }

    // Draw Derived Constraint

    int rectY = constraintSetY;
    int lineY = rectY + csHeight;
    if (!mComputedDerivedLines) {
      mTotalDerivedLines = 0;
      if (mDerivedLines.length < mConstraintSet.length) {
        mDerivedLines = new DerivedSetLine[mConstraintSet.length + 1];
        for (int i = 0; i < mDerivedLines.length; i++) {
          mDerivedLines[i] = new DerivedSetLine();
        }
      }
      for (int i = 0; i < mConstraintSet.length; i++) {
        String derived = Utils
          .stripID(mConstraintSet[i].getAttributeValue("deriveConstraintsFrom"));
        if (derived != null) {
          for (int j = 0; j < mConstraintSet.length; j++) {
            String id = Utils.stripID(mConstraintSet[j].getAttributeValue("id"));
            if (id.equals(derived)) {
              int fromRectX = getmConstraintSetX(i + 1);// * (csWidth + space) + space;
              int toRectX = getmConstraintSetX(j + 1);// * (csWidth + space) + space;
              int lineSrcX = fromRectX + csWidth / 2;
              int lineDstX = toRectX + csWidth / 2;
              mDerivedLines[mTotalDerivedLines].mDstX = lineDstX;
              mDerivedLines[mTotalDerivedLines].mSrcX = lineSrcX;
              mDerivedLines[mTotalDerivedLines].mPathYOffset =
                10 * (mDerivedLines.length - mTotalDerivedLines);
              mTotalDerivedLines++;
            }
          }
        }
      }
      if (mTotalDerivedLines > MAX_OPTIMIZE_LINES) {
        locallyOptimizeLines(mDerivedLines, mTotalDerivedLines, 4);
      }
      else {
        optimizeLines(mDerivedLines, mTotalDerivedLines);
      }
      mComputedDerivedLines = true;
    }
    else {
      mTotalDerivedLines = 0;
      if (mDerivedLines.length < mConstraintSet.length) {
        mDerivedLines = new DerivedSetLine[mConstraintSet.length + 1];
        for (int i = 0; i < mDerivedLines.length; i++) {
          mDerivedLines[i] = new DerivedSetLine();
        }
      }
      for (int i = 0; i < mConstraintSet.length; i++) {
        String derived = Utils
          .stripID(mConstraintSet[i].getAttributeValue("deriveConstraintsFrom"));
        if (derived != null) {
          for (int j = 0; j < mConstraintSet.length; j++) {
            String id = Utils.stripID(mConstraintSet[j].getAttributeValue("id"));
            if (id.equals(derived)) {
              int fromRectX = getmConstraintSetX(i + 1); //* (csWidth + space) + space;
              int toRectX = getmConstraintSetX(j + 1);// * (csWidth + space) + space;
              int lineSrcX = fromRectX + csWidth / 2;
              int lineDstX = toRectX + csWidth / 2;
              mDerivedLines[mTotalDerivedLines].mDstX = lineDstX;
              mDerivedLines[mTotalDerivedLines].mSrcX = lineSrcX;
              mDerivedLines[mTotalDerivedLines].mConstraintSet = mConstraintSet[i];
              mDerivedLines[mTotalDerivedLines].mDerivedFrom = derived;

              mTotalDerivedLines++;
            }
          }
        }
      }
    }

    // ======================= draw the lines
    int GAP = 10;


    for (int i = 0; i < mTotalDerivedLines; i++) {
      g2g.setStroke(mDashStroke);
      g.setColor(Color.LIGHT_GRAY);
      mPath.reset();
      mRectPathX[0] = mDerivedLines[i].mSrcX - 5;
      mRectPathY[0] = lineY;

      mRectPathX[1] = mRectPathX[0];
      mRectPathY[1] = lineY + mDerivedLines[i].mPathYOffset;

      mRectPathX[2] = mDerivedLines[i].mDstX + 5;
      mRectPathY[2] = mRectPathY[1];

      mRectPathX[3] = mRectPathX[2];
      mRectPathY[3] = lineY + 5;
      mPath.moveTo(mRectPathX[0], mRectPathY[0]);
      Drawing.drawRound(mPath, mRectPathX, mRectPathY, mRectPathLen, 20);
      Drawing.drawPick(picker, mDerivedLines[i], mRectPathX, mRectPathY, mRectPathLen, 20);
      g2g.draw(mPath);
      mPath.reset();
      mPath.moveTo(mRectPathX[3], mRectPathY[3]);
      mPath.lineTo(mRectPathX[3] - 5, mRectPathY[3]);
      mPath.lineTo(mRectPathX[3], mRectPathY[3] - 5);
      mPath.lineTo(mRectPathX[3] + 5, mRectPathY[3]);
      mPath.lineTo(mRectPathX[3], mRectPathY[3]);
      g2g.setStroke(stroke);
      g2g.draw(mPath);
    }
  }

  private int drawActions(Graphics g, int swipes, int clicks, int x1, int x2, int y) {
    int onSize = 4;
    boolean backward = (x2 < x1);
    int offset = MEUI.scale(6);
    int gap = MEUI.scale(2);
    int markx = x1 - MEUI.scale(2) - 1;
    int totalSpace = 0;
    int dir = (backward) ? -1 : 1;

    if (clicks > 0) {
      g.fillRoundRect(markx + (backward ? (-offset - onSize) : offset), y - onSize - 2, onSize, onSize, onSize, onSize);
      markx += totalSpace = dir * (onSize + gap);
    }
    if (swipes > 0) {
      int longpill = onSize * 3;
      g.fillRoundRect(markx + (backward ? (-offset - longpill) : offset), y - onSize - 2, longpill, onSize, onSize, onSize);
      totalSpace += dir * (longpill + gap);
    }
    return totalSpace;
  }

  //private void calcHighlight(float progress,
  //                           float stagger) {
  //  mHighlightEnd = false;
  //  mHighlightStart = false;
  //  if (!Float.isNaN(progress)) {
  //    if (progress > 1) {
  //      progress = 1;
  //    }
  //    if (progress < 0) {
  //      progress = 0;
  //    }
  //    stagger = Math.abs(stagger);
  //    float scale = 1 / (1 - stagger);
  //    float startProgress = progress * scale;
  //    float endProgress = (progress - stagger) * scale;
  //    float startx = mRectPathX[0] + startProgress * (mRectPathX[3] - mRectPathX[0]) - 4;
  //    float endx = mRectPathX[0] + endProgress * (mRectPathX[3] - mRectPathX[0]) + 4;
  //
  //    if (Math.abs(startx + 4 - mRectPathX[0]) < 2) {
  //      mHighlightStart = true;
  //    }
  //    if (Math.abs(endx - 4 - mRectPathX[3]) < 2) {
  //      mHighlightEnd = true;
  //    }
  //  }
  //}

  private void drawTransition(Graphics2D g, boolean hoverHighlight, int x1, int x2, int y, int constraintSetY, Object tag,
                              float progress,
                              float stagger) {
    int tri_delta_y = 5;
    int tri_delta_x = 5;
    mPath.reset();
    mRectPathX[0] = x1 - 5;
    mRectPathY[0] = constraintSetY - 4;

    mRectPathX[1] = mRectPathX[0];
    mRectPathY[1] = y;

    mRectPathX[2] = x2 + 5;
    mRectPathY[2] = mRectPathY[1];

    mRectPathX[3] = mRectPathX[2];
    mRectPathY[3] = constraintSetY - 8;
    mPath.moveTo(mRectPathX[0], mRectPathY[0]);
    Drawing.drawRound(mPath, mRectPathX, mRectPathY, mRectPathLen, ROUND_SIZE);
    Drawing.drawPick(picker, tag, mRectPathX, mRectPathY, mRectPathLen, ROUND_SIZE);

    mPath.lineTo(mRectPathX[3] - tri_delta_x, mRectPathY[3]);
    mPath.lineTo(mRectPathX[3], mRectPathY[3] + tri_delta_y);
    mPath.lineTo(mRectPathX[3] + tri_delta_x, mRectPathY[3]);
    mPath.lineTo(mRectPathX[3], mRectPathY[3]);

    if (hoverHighlight) {
      Stroke originalStroke = ((Graphics2D)g).getStroke();
      Color originalColor = g.getColor();
      g.setStroke(ourFatStroke);
      g.setColor(MEUI.Overview.ourCS_HoverBorder);
      g.draw(mPath);
      g.setColor(originalColor);
      g.setStroke(originalStroke);
    }
    else {
      g.draw(mPath);
    }
    mHighlightStart = false;
    mHighlightEnd = false;

    if (!Float.isNaN(progress)) {
      if (progress > 1) {
        progress = 1;
      }
      if (progress < 0) {
        progress = 0;
      }
      stagger = Math.abs(stagger);
      float scale = 1 / (1 - stagger);
      float startProgress = progress * scale;
      float endProgress = (progress - stagger) * scale;
      float startx = mRectPathX[0] + startProgress * (mRectPathX[3] - mRectPathX[0]) - 4;
      float endx = mRectPathX[0] + endProgress * (mRectPathX[3] - mRectPathX[0]) + 4;
      int clipy = (int)mRectPathY[3] + (progress < 0.3 ? 3 : 0);
      Rectangle rect = g.getClipBounds();
      g.clipRect(0, 0, getWidth(), clipy);
      Color originalColor = g.getColor();
      g.setPaint(new LinearGradientPaint(
        startx,
        0,
        endx,
        0,
        new float[]{0, 0.01f, 0.99f, 1},
        new Color[]{originalColor, MEUI.Overview.ourPositionColor, MEUI.Overview.ourPositionColor, originalColor}));
      g.draw(mPath);
      g.setClip(rect);
      g.setColor(originalColor);
      if (Math.abs(startx + 4 - mRectPathX[0]) < 2) {
        mHighlightStart = true;
      }
      if (Math.abs(endx - 4 - mRectPathX[3]) < 2) {
        mHighlightEnd = true;
      }
    }
    Color originalColor = g.getColor();

    if (mHighlightStart) {
      g.setColor(MEUI.Overview.ourPositionColor);
    }
    //fill triangle
    g.drawLine(mRectPathX[0] - tri_delta_x, mRectPathY[0], mRectPathX[0] + tri_delta_x, mRectPathY[0]);
    if (mHighlightEnd) {
      g.setColor(MEUI.Overview.ourPositionColor);
    }
    else {
      g.setColor(originalColor);
    }

    mRectPathX[0] = mRectPathX[3] - tri_delta_x;
    mRectPathY[0] = mRectPathY[3];

    mRectPathX[1] = mRectPathX[3];
    mRectPathY[1] = mRectPathY[3] + tri_delta_y;

    mRectPathX[2] = mRectPathX[3] + tri_delta_x;
    mRectPathY[2] = mRectPathY[3];

    g.fillPolygon(mRectPathX, mRectPathY, 4);
    g.drawPolygon(mRectPathX, mRectPathY, 4);
    g.setColor(originalColor);
  }

  private void drawTransition_orig(Graphics2D g, boolean hoverHighlight, int x1, int x2, int y, int constraintSetY, Object tag) {
    picker.addLine(tag, 3, x1, y, x2, y, 2);

    int delta = x2 > x1 ? -5 : 5;
    if (hoverHighlight) {
      Stroke originalStroke = ((Graphics2D)g).getStroke();
      Color originalColor = g.getColor();
      ((Graphics2D)g).setStroke(ourFatStroke);
      g.setColor(MEUI.Overview.ourCS_HoverBorder);
      g.drawLine(x1, y, x2, y);
      g.drawLine(x1, y - delta, x1, y + delta);
      g.drawLine(x2, y, x2 + delta, y + delta);
      g.drawLine(x2, y, x2 + delta, y - delta);
      g.setColor(originalColor);
      ((Graphics2D)g).setStroke(originalStroke);
    }
    g.drawLine(x1, y, x2, y);
    g.drawLine(x1, y - delta, x1, y + delta);
    g.drawLine(x2, y, x2 + delta, y + delta);
    g.drawLine(x2, y, x2 + delta, y - delta);
  }

  @VisibleForTesting
  static void optimizeLines(DerivedSetLine[] lines, int lineCount) {
    int maxLevels = 3;
    int total = intPower(maxLevels, lineCount);
    double minCost = Double.MAX_VALUE;
    int minPattern = -1;
    for (int i = 0; i < total; i++) {
      int level = i;
      for (int j = 0; j < lineCount; j++) {
        lines[j].mPathYOffset = level % maxLevels;
        level /= maxLevels;
      }
      double cost = lineCost(lines, lineCount, maxLevels);
      if (cost < minCost) {
        minCost = cost;
        minPattern = i;
      }
    }
    int level = minPattern;
    for (int j = 0; j < lineCount; j++) {
      int value = level % maxLevels;
      level /= maxLevels;
      lines[j].mPathYOffset = value * 12 + 20;
    }
  }

  private static class DeltaInfo {
    int myLineIndex;
    int myReducedCost;
    int myLevel;

    DeltaInfo(int lineIndex, int cost, int level) {
      myLineIndex = lineIndex;
      myReducedCost = cost;
      myLevel = level;
    }
  }

  // An alternative approach to optimize the cost for drawing the derived line.
  // Although this approach does not guarantee the minimum cost, it can still produce a relatively good result.
  @VisibleForTesting
  static void locallyOptimizeLines(DerivedSetLine[] lines, int lineCount, int maxLevels) {
    boolean canReduce = true;
    DeltaInfo delta = new DeltaInfo(-1, -1, -1);

    for (int i = 0; i < lineCount; i++) {
      lines[i].mPathYOffset = 0;
    }

    while (canReduce) {
      canReduce = reduceCostStepwise(lines, lineCount, maxLevels);
    }

    for (int i = 0; i < lineCount; i++) {
      lines[i].mPathYOffset = lines[i].mPathYOffset * 12 + 20;
    }
  }

  // Move one of the derived lines to a different level so that the cost can be reduced at the most.
  // Return true if the cost can be reduced; false if no improvement can be made.
  private static boolean reduceCostStepwise(DerivedSetLine[] lines, int lineCount, int maxLevels) {
    int maxReducedCost = 0;
    boolean canReduce = false;
    DeltaInfo delta = new DeltaInfo(-1, -1, -1);
    DeltaInfo tmpDelta = new DeltaInfo(-1, -1, -1);

    for (int lineIndex = 0; lineIndex < lineCount; lineIndex++) {
      int reducedCost = 0;
      DerivedSetLine l1 = lines[lineIndex];
      int curLevel = l1.mPathYOffset;
      int[] costs = getAllCosts(lines, lineCount);

      // Iterate through different levels to verify if the cost can be reduced by
      // switching l1 to a different level.
      for (int i = 0; i < maxLevels; i++) {
        if (curLevel == i) {
          continue;
        }

        l1.mPathYOffset = i;
        int cost = 0;
        for (int j = 0; j < lineCount; j++) {
          if (j == lineIndex) {
            continue;
          }

          DerivedSetLine l2 = lines[j];
          cost += calcualteCost(l1, l2);
        }

        if ((costs[lineIndex] - cost) > reducedCost) {
          reducedCost = costs[lineIndex] - cost;
          tmpDelta.myReducedCost = reducedCost;
          tmpDelta.myLineIndex = lineIndex;
          tmpDelta.myLevel = i;
        }
      }

      l1.mPathYOffset = curLevel;
      if (tmpDelta.myReducedCost > maxReducedCost) {
        delta = tmpDelta;
        canReduce = true;
      }
    }

    if (canReduce) {
      lines[delta.myLineIndex].mPathYOffset = delta.myLevel;
    }
    return canReduce;
  }

  // calculate and return the cost of each derived line.
  private static int[] getAllCosts(DerivedSetLine[] lines, int lineCount) {
    int[] costs = new int[lineCount];

    for (int i = 0; i < lineCount; i++) {
      DerivedSetLine l1 = lines[i];
      int cost = 0;
      for (int j = 0; j < lineCount; j++) {
        if (i == j) {
          continue;
        }

        DerivedSetLine l2 = lines[j];
        cost += calcualteCost(l1, l2);
      }
      costs[i] = cost;
    }

    return costs;
  }

  private static int calcualteCost(DerivedSetLine l1, DerivedSetLine l2) {
    int cost = 0;

    if (Math.max(Math.min(l1.mSrcX, l1.mDstX), Math.min(l2.mSrcX, l2.mDstX)) < Math
      .min(Math.max(l1.mSrcX, l1.mDstX), Math.max(l2.mSrcX, l2.mDstX))
        && l1.mPathYOffset == l2.mPathYOffset) {
      cost += 5;
    }
    else {
      boolean l1Inside = ((l1.mSrcX - l2.mSrcX) * (l1.mSrcX - l2.mDstX) <= 0) && (
        (l1.mDstX - l2.mSrcX) * (l1.mDstX - l2.mDstX) <= 0);
      if (l1Inside && l1.mPathYOffset > l2.mPathYOffset) {
        cost += 5;
      }
    }
    return cost;
  }

  private static double lineCost(DerivedSetLine[] lines, int lineCount, int maxLevels) {
    double ret = 0;
    for (int i = 0; i < lineCount; i++) {
      ret += lines[i].mPathYOffset;
    }
    for (int i = 0; i < lineCount; i++) {
      DerivedSetLine l1 = lines[i];

      for (int j = i + 1; j < lineCount; j++) {
        DerivedSetLine l2 = lines[j];
        ret += calcualteCost(l1, l2);
      }
    }
    return ret;
  }

 static int intPower(int a, int b) {
    int ret = a;
    for (int i = 1; i < b; i++) {
      ret *= a;
    }
    return ret;
  }

  void setConstraintSetIndex(int index) {
    mTransitionSelected = -1;
    mConstraintSetSelected = index;
    repaint();
  }

  void setTransitionSetIndex(int index) {
    mConstraintSetSelected = -1;
    mTransitionSelected = index;
    repaint();
  }

  void setSelected(MTag tag) {
    if (DEBUG) {
      Debug.log("setMTag");
    }
    mTransitionSelected = -1;
    mConstraintSetSelected = -1;
    for (int i = 0; i < mConstraintSet.length; i++) {
      if (tag == mConstraintSet[i]) {
        mConstraintSetSelected = i;
        return;
      }
    }
    for (int i = 0; i < mTransitions.length; i++) {
      if (tag == mTransitions[i]) {
        mTransitionSelected = i;
        return;
      }
    }
  }

  public void setMTag(MTag motionScene, MTag layout) {
    mComputedDerivedLines = false;
    mMotionScene = motionScene;
    mLayout = layout;
    mConstraintSet = motionScene.getChildTags("ConstraintSet");
    mTransitions = motionScene.getChildTags("Transition");
    mTransitionStart = new int[mTransitions.length];
    mTransitionEnd = new int[mTransitions.length];
    mOnActionSize = new int[mTransitions.length];
    mConstraintSetNames = new String[mConstraintSet.length];
    for (int j = 0; j < mConstraintSet.length; j++) {
      mConstraintSetNames[j] = Utils.stripID(mConstraintSet[j].getAttributeValue("id"));
    }
    for (int i = 0; i < mTransitions.length; i++) {
      String start = Utils.stripID(mTransitions[i].getAttributeValue("constraintSetStart"));
      String end = Utils.stripID(mTransitions[i].getAttributeValue("constraintSetEnd"));
      for (int j = 0; j < mConstraintSetNames.length; j++) {
        if (start.equals(mConstraintSetNames[j])) {
          mTransitionStart[i] = j;
        }
        if (end.equals(mConstraintSetNames[j])) {
          mTransitionEnd[i] = j;
        }
      }
    }
    calcDimensions();
    revalidate();
    repaint();
  }

  private void showPopupMenu(@NotNull MouseEvent e) {
    ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.TOOLWINDOW_POPUP, myActionGroup);
    popupMenu.getComponent().show(e.getComponent(),e.getX(), e.getY());
  }

  // create an action group for the popup menu of a transition
  @NotNull
  private ActionGroup createPopupActionGroup() {
    DefaultActionGroup group = new DefaultActionGroup();

    AnAction mySaveGifAction = new AnAction("Save Gif", "Save selected transition as Gif", MEIcons.SAVE) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent event) {
        mListener.performAction(MTagActionListener.SAVE_GIF);
      }
    };

    group.add(mySaveGifAction);
    return group;
  }
}
