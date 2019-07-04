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

import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEScenePicker;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEUI;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MEScenePicker.HitElementListener;
import com.android.tools.idea.uibuilder.handlers.motion.editor.utils.Debug;
import com.android.tools.idea.uibuilder.handlers.motion.editor.utils.Drawing;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;

/**
 * The overview panel displays graphically the layout and the transitions and allows selection for ether.
 */
class OverviewPanel extends JPanel {
  public static final boolean DEBUG = false;
  private static final int ROUND_SIZE = 5;
  MEScenePicker picker = new MEScenePicker();
  MTag mMotionScene;
  MTag[] mConstraintSet;
  MTag[] mTransitions;
  int[] mTransitionStart;
  int[] mTransitionEnd;
  String[] mConstraintSetNames;
  int mTransitionSelected = -1;
  int mConstraintSetSelected = -1;
  private MTag mLayout;
  Listener mListener;
  private Stroke mThickStroke = new BasicStroke(2);
  GeneralPath mPath = new GeneralPath();
  int mRectPathLen = 4;
  int[] mRectPathX = new int[mRectPathLen];
  int[] mRectPathY = new int[mRectPathLen];
  private boolean mComputedDerivedLines = false;
  private static int MIN_CS_WIDTH = MEUI.scale(50);
  private static int MAX_CS_WIDTH = MEUI.scale(60);

  private static final String MAIN_TOOL_TIP = null;

  private static int CS_GAP = MEUI.scale(20);
  private static int ARROW_HEIGHT = 10;
  private int arrow_spacing;
  private MTag mMouseOverObject;
  private static final Stroke ourFatStroke = new BasicStroke(5f);
  private static final Stroke ourSelectedStroke = new BasicStroke(2f);
  private MTag mMouseOverDerived;

  interface Listener {
    void select(MTag selected);
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

  public void setSelectionListener(Listener l) {
    mListener = l;
  }

  public OverviewPanel() {
    setBackground(MEUI.ourPrimaryPanelBackground);

    setToolTipText("OverViewPanel showing ConstraintSets and Transitions");
    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        updateFromMouse(e.getX(), e.getY(), false);
      }

      @Override
      public void mouseReleased(MouseEvent e) {
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
  }

  private void updateFromMouse(int x, int y, boolean select) {
    MTag[] objects = new MTag[1];
    DerivedSetLine[] line = new DerivedSetLine[1];
    picker.setSelectListener(new HitElementListener() {
      @Override
      public void over(Object over, double dist) {
        if (over instanceof MTag) {
          objects[0] = ((MTag) over);
        } else {
          line[0] = (DerivedSetLine) over;
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
    } else if (line[0] == null && mMouseOverDerived != null) {
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
      mListener.select(found);
    } else {
      if (mMouseOverObject != found) {
        mMouseOverObject = found;

        switch (found.getTagName()) {
          case "Transition":
            setToolTipText("Transition " + Utils.formatTransition(found));

            break;
          case "ConstraintSet":
            setToolTipText("ConstraintSet " + Utils.stripID(found.getAttributeValue("id")));
            break;
          default:
            setToolTipText("Original MotionLayout");
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
    } else if (cs_width < MIN_CS_WIDTH) {
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
    Graphics2D g2g = (Graphics2D) g;
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
    int csWidth = cs_width;
    Stroke stroke = g2g.getStroke();
    g2g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    int yGap = 24;
    int selectedStart = -1;
    int selectedEnd = -1;
    int constraintSetY = CS_GAP + arrow_spacing + mTransitions.length * arrow_spacing;
    int yoffset = CS_GAP + arrow_spacing - ARROW_HEIGHT / 2;
    // --------  Draw Transitions lines -------------

    for (int i = 0; i < mTransitions.length; i++) {
      int start = mTransitionStart[i];
      int end = mTransitionEnd[i];
      int x1 = getmConstraintSetX(start + 1) + csWidth / 2;
      int x2 = getmConstraintSetX(end + 1) + csWidth / 2;
      int y = yoffset + i * arrow_spacing;
      g.setColor((mTransitionSelected == i) ? MEUI.Overview.ourSelectedLineColor : MEUI.Overview.ourLineColor);
      if (mTransitionSelected == i) {
        selectedStart = start;
        selectedEnd = end;
        g.setColor(MEUI.Overview.ourSelectedLineColor);
      } else {
        g.setColor(MEUI.Overview.ourLineColor);
      }

      boolean hoverHighlight = mMouseOverObject != null & mTransitions[i] == mMouseOverObject;
      drawTransition((Graphics2D) g, hoverHighlight, x1, x2, y, constraintSetY, mTransitions[i]);
    }
    // --------  Draw Transitions lines -------------
    ((Graphics2D) g).setStroke(ourSelectedStroke);
    for (int i = 0; i < mTransitions.length; i++) {
      int start = mTransitionStart[i];
      int end = mTransitionEnd[i];
      int x1 = getmConstraintSetX(start + 1) + csWidth / 2;
      int x2 = getmConstraintSetX(end + 1) + csWidth / 2;
      int y = yoffset + i * arrow_spacing;
      g.setColor((mTransitionSelected == i) ? Color.BLUE : Color.BLACK);
      if (mTransitionSelected != i) {
        continue;
      }
      selectedStart = start;
      selectedEnd = end;
      g.setColor(MEUI.Overview.ourSelectedLineColor);

      boolean hoverHighlight = mMouseOverObject != null & mTransitions[i] == mMouseOverObject;
      drawTransition((Graphics2D) g, hoverHighlight, x1, x2, y, constraintSetY, mTransitions[i]);
    }
    // --------  Draw Transitions strings -------------

    for (int i = 0; i < mTransitions.length; i++) {
      int start = mTransitionStart[i];
      int end = mTransitionEnd[i];
      int x1 = getmConstraintSetX(start + 1) + csWidth / 2;
      int x2 = getmConstraintSetX(end + 1) + csWidth / 2;
      int y = yoffset + i * arrow_spacing;
      g.setColor((mTransitionSelected == i) ? Color.BLUE : Color.BLACK);
      if (mTransitionSelected == i) {
        selectedStart = start;
        selectedEnd = end;
        g.setColor(MEUI.Overview.ourSelectedLineColor);
      } else {
        g.setColor(MEUI.Overview.ourLineColor);
      }
      picker.addLine(mTransitions[i], 3, x1, y, x2, y, 2);
      String str = mTransitions[i].getAttributeValue("id");
      if (str != null) {
        str = Utils.stripID(str);
        FontMetrics fm = g.getFontMetrics();
        Rectangle2D bounds = fm.getStringBounds(str, g);
        int strX = (x1 > x2) ? x1 - (int) bounds.getWidth() - 20 : x1 + 4;
        Color tmp = g.getColor();
        g.setColor(getBackground());
        g.fillRect(strX, y - fm.getHeight(), (int) bounds.getWidth(), (int) bounds.getHeight());
        g.setColor(tmp);

        g.drawString(str, strX, y - 5);
      }
    }
    // --------  Draw ConstraintSets -------------
    int csHeight = cs_height;

    // calculate and set the font based on fitting the ConstraintSet ids
    g2g.setStroke(mThickStroke);

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
    while (csWidth < maxStringWidth + margin) {
      g.setFont(g.getFont().deriveFont(g.getFont().getSize() / 1.4f));
      fm = g.getFontMetrics();
      maxStringWidth = fm.stringWidth(maxString);
    }

    int line_x = getmConstraintSetX(1) - CS_GAP - 1;
    int line_y = constraintSetY;
    g.setColor(MEUI.Overview.ourLineColor);
    g.drawLine(line_x, line_y, line_x, line_y + cs_height);

    // ---------  Draw Rectangles -------------
    for (int i = 0; i <= mConstraintSet.length; i++) {
      int setIndex = i - 1;
      if (i == 0) setIndex = mConstraintSet.length;
      int x = getmConstraintSetX(i);
      int y = constraintSetY;
      boolean hover = false;
      if (mMouseOverObject != null) {
        if (i == 0) {
          if (mLayout == mMouseOverObject) {
            hover = true;
          }
        } else {
          if (mConstraintSet[setIndex] == mMouseOverObject) {
            hover = true;
          }
        }
      }
      if (i != 0) {
        picker.addRect(mConstraintSet[setIndex], 3, x, y, x + csWidth, y + csHeight);
      } else {
        picker.addRect(mLayout, 3, x, y, x + csWidth, y + csHeight);
      }
      boolean isLayout = (i == 0);
      String name = isLayout ? "Layout" : mConstraintSetNames[setIndex];

      if (mConstraintSetSelected == i) {
        g.setColor((hover) ? MEUI.Overview.ourHoverColor : MEUI.Overview.ourSelectedSetColor);
        g.fillRoundRect(x, y, csWidth, csHeight, space, space);
        g.setColor(MEUI.Overview.ourSelectedLineColor);
        g.drawRoundRect(x, y, csWidth, csHeight, space, space);
      } else {
        g.setColor((hover) ? MEUI.Overview.ourHoverColor : MEUI.Overview.ourConstraintSet);
        g.fillRoundRect(x, y, csWidth, csHeight, space, space);
        if (selectedEnd == setIndex || selectedStart == setIndex) {
          g.setColor(MEUI.Overview.ourSelectedLineColor);
        } else {
          g.setColor(MEUI.Overview.ourLineColor);
        }
        g.drawRoundRect(x, y, csWidth, csHeight, space, space);
        g.setColor(MEUI.Overview.ourLineColor);
      }
      if (i == 0) {
        g.setColor((hover) ? MEUI.Overview.ourHoverColor : MEUI.Overview.ourLayoutColor);
        g.fillRoundRect(x, y, csWidth, csHeight, space, space);
        g.setColor(MEUI.Overview.ourLayoutHeaderColor);
        g.fillRoundRect(x, y, csWidth, csHeight / 4 + 1, space, space);
        g.setColor((mConstraintSetSelected == i) ? MEUI.Overview.ourSelectedLineColor : MEUI.Overview.ourLineColor);
        g.drawRoundRect(x, y, csWidth, csHeight, space, space);
        g.fillRect(x, y + csHeight / 4, csWidth, 2);

      }
      g.setColor(MEUI.Overview.ourConstraintSetText);

      if (isLayout) {
        int stringWidth = fm.stringWidth("Motion");
        int mx = x + (csWidth - stringWidth) / 2;
        int my = y + csHeight / 2 - fm.getHeight() + fm.getAscent();

        g.drawString("Motion", mx, my);

        stringWidth = fm.stringWidth("Layout");
        int lx = x + (csWidth - stringWidth) / 2;
        int ly = y + csHeight / 2 + fm.getAscent();

        g.drawString("Layout", lx, ly);
      } else {
        int stringWidth = fm.stringWidth(name);
        x += (csWidth - stringWidth) / 2;
        y += (csHeight - fm.getHeight()) / 2 + fm.getAscent();

        g.drawString(name, x, y);
      }
    }

    // Draw Derived Constraint
    if (true) {
      return;
    }
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

      optimizeLines(mDerivedLines, mTotalDerivedLines);
      mComputedDerivedLines = true;
    } else {
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
    g2g.setStroke(stroke);
    g.setColor(Color.LIGHT_GRAY);

    for (int i = 0; i < mTotalDerivedLines; i++) {
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
      Drawing.drawRound(mPath, mRectPathX, mRectPathY, mRectPathLen, GAP);
      Drawing.drawPick(picker, mDerivedLines[i], mRectPathX, mRectPathY, mRectPathLen, GAP);
      mPath.lineTo(mRectPathX[3] - 5, mRectPathY[3]);
      mPath.lineTo(mRectPathX[3], mRectPathY[3] - 5);
      mPath.lineTo(mRectPathX[3] + 5, mRectPathY[3]);
      mPath.lineTo(mRectPathX[3], mRectPathY[3]);

      g2g.draw(mPath);
    }

  }

  private void drawTransition(Graphics2D g, boolean hoverHighlight, int x1, int x2, int y, int constraintSetY, Object tag) {
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
      Stroke originalStroke = ((Graphics2D) g).getStroke();
      Color originalColor = g.getColor();
      g.setStroke(ourFatStroke);
      g.setColor(MEUI.Overview.ourHoverColor);
      g.draw(mPath);
      g.setColor(originalColor);
      g.setStroke(originalStroke);
    }
    g.draw(mPath);
    //fill triangle
    g.drawLine(mRectPathX[0] - tri_delta_x, mRectPathY[0], mRectPathX[0] + tri_delta_x, mRectPathY[0]);

    mRectPathX[0] = mRectPathX[3] - tri_delta_x;
    mRectPathY[0] = mRectPathY[3];

    mRectPathX[1] = mRectPathX[3];
    mRectPathY[1] = mRectPathY[3] + tri_delta_y;

    mRectPathX[2] = mRectPathX[3] + tri_delta_x;
    mRectPathY[2] = mRectPathY[3];

    g.fillPolygon(mRectPathX, mRectPathY, 4);

  }

  private void drawTransition_orig(Graphics2D g, boolean hoverHighlight, int x1, int x2, int y, int constraintSetY, Object tag) {
    picker.addLine(tag, 3, x1, y, x2, y, 2);

    int delta = x2 > x1 ? -5 : 5;
    if (hoverHighlight) {
      Stroke originalStroke = ((Graphics2D) g).getStroke();
      Color originalColor = g.getColor();
      ((Graphics2D) g).setStroke(ourFatStroke);
      g.setColor(MEUI.Overview.ourHoverColor);
      g.drawLine(x1, y, x2, y);
      g.drawLine(x1, y - delta, x1, y + delta);
      g.drawLine(x2, y, x2 + delta, y + delta);
      g.drawLine(x2, y, x2 + delta, y - delta);
      g.setColor(originalColor);
      ((Graphics2D) g).setStroke(originalStroke);
    }
    g.drawLine(x1, y, x2, y);
    g.drawLine(x1, y - delta, x1, y + delta);
    g.drawLine(x2, y, x2 + delta, y + delta);
    g.drawLine(x2, y, x2 + delta, y - delta);
  }

  private void optimizeLines(DerivedSetLine[] lines, int lineCount) {
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

  private double lineCost(DerivedSetLine[] lines, int lineCount, int maxLevels) {
    double ret = 0;
    for (int i = 0; i < lineCount; i++) {
      ret += lines[i].mPathYOffset;
    }
    for (int i = 0; i < lineCount; i++) {
      DerivedSetLine l1 = lines[i];

      for (int j = i + 1; j < lineCount; j++) {
        DerivedSetLine l2 = lines[j];

        if (Math.max(Math.min(l1.mSrcX, l1.mDstX), Math.min(l2.mSrcX, l2.mDstX)) < Math
          .min(Math.max(l1.mSrcX, l1.mDstX), Math.max(l2.mSrcX, l2.mDstX))
          && l1.mPathYOffset == l2.mPathYOffset) {
          ret += 5;
        } else {
          boolean l1Inside = ((l1.mSrcX - l2.mSrcX) * (l1.mSrcX - l2.mDstX) <= 0) && (
            (l1.mDstX - l2.mSrcX) * (l1.mDstX - l2.mDstX) <= 0);
          if (l1Inside && l1.mPathYOffset > l2.mPathYOffset) {
            ret += 5;
          }
        }
      }
    }
    return ret;
  }

  int intPower(int a, int b) {
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
}
