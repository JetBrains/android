/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.adtui.chart;

import com.android.annotations.NonNull;
import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.Choreographer;
import com.android.tools.adtui.ValuedTreeNode;

import javax.swing.tree.TreeNode;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.List;

/**
 * Component which renders a
 * <a href="https://en.wikipedia.org/wiki/Pie_chart#Ring_chart_.2F_Sunburst_chart_.2F_Multilevel_pie_chart">
 * sunburst chart</a> that can be unrolled by setting its angle.
 */
public final class SunburstChart extends AnimatedComponent {

  private static final Color[] COLORS = {
    new Color(0x6baed6),
    new Color(0xc6dbef),
    new Color(0xfd8d3c),
    new Color(0xfdd0a2),
    new Color(0x74c476),
    new Color(0xc7e9c0),
    new Color(0x9e9ac8),
    new Color(0xdadaeb),
    new Color(0x969696),
    new Color(0xd9d9d9),
  };

  private static final Color[] HIGHLIGHTS = {
    new Color(0x3182bd),
    new Color(0x9ecae1),
    new Color(0xe6550d),
    new Color(0xfdae6b),
    new Color(0x31a354),
    new Color(0xa1d99b),
    new Color(0x756bb1),
    new Color(0xbcbddc),
    new Color(0x636363),
    new Color(0xbdbdbd),
  };

  private ValuedTreeNode mData;

  private Slice mSlice;

  private float mGap;

  private float mStart;

  private float mFixed;

  private float mAngle;

  private float mCurrentAngle;

  private float mSeparator;

  private boolean mAutoSize;

  private float mSliceWidth;

  private boolean myUseCount;

  private int mySelectionLevel;

  private int myZoomLevel;

  private Slice mySelection;

  private Slice myZoom;

  private boolean myLockSelection;

  private final List<SliceSelectionListener> mListeners;

  // Calculated values
  private float mX;

  private float mY;

  private float mMaxDepth;

  private float mMaxSide;

  private float mCenterX;

  private float mCenterY;

  private float mDelta;

  private Point2D.Float mDirection;

  private Map<Color, Path2D.Float> mPaths;

  public SunburstChart(@NonNull ValuedTreeNode data) {
    mData = data;
    mSlice = new Slice(0.0f);
    mSliceWidth = 50;
    mGap = 50;
    mX = -1;
    mY = -1;
    mCurrentAngle = mAngle = 360.0f;
    mDelta = 0.0f;
    mFixed = 60.0f;
    mStart = 180.0f;
    mSeparator = 1.0f;
    mySelectionLevel = -1;
    myLockSelection = false;
    mPaths = new HashMap<Color, Path2D.Float>();
    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
          myZoom = mySelection;
          myZoomLevel = mySelectionLevel;
          myLockSelection = false;
        }
        else {
          myLockSelection = !myLockSelection && mySelection != null;
        }
      }
    });
    mListeners = new LinkedList<SliceSelectionListener>();
  }

  @Override
  protected void draw(Graphics2D g) {
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    Dimension dim = getSize();

    g.setColor(getBackground());
    g.fillRect(0, 0, dim.width, dim.height);

    mPaths.clear();
    drawSlice(mSlice, 0.0f, 0.0f, 1.0f);
    for (Map.Entry<Color, Path2D.Float> entry : mPaths.entrySet()) {
      g.setColor(entry.getKey());
      g.fill(entry.getValue());
    }
  }

  @Override
  protected void updateData() {

    Dimension dim = getSize();
    mX = dim.width * 0.5f;
    mY = dim.height * 0.5f;

    updateArea();
    updateStructure(mSlice, mData, false);

    mCurrentAngle = Math.abs(mCurrentAngle - mAngle) < 0.1f ?
                    mAngle : Choreographer.lerp(mCurrentAngle, mAngle, 0.999f, mFrameLength);
    if (mAutoSize) {
      float full = Math.min(mMaxDepth, mMaxSide) - mGap - 20;
      float none = mMaxDepth * 2 - mGap - 20;
      float factor = mCurrentAngle / 360.0f;
      float depth = full * factor + none * (1 - factor);
      mFixed = Choreographer.lerp(mFixed, (float)((mMaxSide - 20) / Math.PI),
                                  0.999f, mFrameLength);
      float width = depth / getMaxDepth(mSlice);
      mSliceWidth = Choreographer.lerp(mSliceWidth, width, 0.999f, mFrameLength);
    }

    // mDelta is the extra radius needed to keep the same length at the fixes radius
    // (mDelta + mFixed * Rad(mCurrentAngle) == 2PI * mFixed ⇒
    // mDelta + mFixed == 2PI * mFixed / Rad(mCurrentAngle) ⇒
    mDelta = 360.0f * mFixed / mCurrentAngle - mFixed;
    mDirection = new Point2D.Float((float)Math.cos(mStart * Math.PI / 180.0f),
                                   -(float)Math.sin(mStart * Math.PI / 180.0f));

    mCenterX = mX + (mDelta + mMaxDepth * (360.0f - mCurrentAngle) / 360.0f) * mDirection.x;
    mCenterY = mY + (mDelta + mMaxDepth * (360.0f - mCurrentAngle) / 360.0f) * mDirection.y;

    updateSelection();
    updateSlice(mSlice, 0, myZoom == null);
  }

  private void updateSelection() {
    Point mouse = getMousePosition();
    if (!myLockSelection) {
      boolean selection = false;
      if (mouse != null) {
        float value;
        float depth;
        if (mCurrentAngle > 0) {
          float distance = (float)mouse.distance(mCenterX, mCenterY);
          depth = (distance - mGap - mDelta) / mSliceWidth;
          float angle = -(float)Math
            .toDegrees(Math.atan2(mouse.y - mCenterY, mouse.x - mCenterX));
          angle = (angle - mStart - 180.0f + mCurrentAngle * 0.5f) % 360.0f;
          angle = angle < 0 ? angle + 360.0f : angle;
          value = angle / mCurrentAngle;
        }
        else {
          float length = (float)(Math.PI * 2.0f * mFixed);
          // Do the dot product of the vector to the mouse with the unit vector to project to:
          depth = (mouse.x - mX) * mDirection.x + (mouse.y - mY) * mDirection.y - (mMaxDepth - mGap);
          value = (mouse.x - mX) * mDirection.y + (mouse.y - mY) * -mDirection.x;
          // Normalize:
          depth = -depth / mSliceWidth;
          value = -value / length + 0.5f;
        }
        selection = updateSelectedSlice(mSlice, depth, value, 0);
      }
      if (!selection) {
        mySelectionLevel = -1;
        if (mySelection != null) {
          mySelection = null;
          fireSliceSelected(new SliceSelectionEvent(null));
        }
      }
    }
    else {
      if (mySelection != null && mySelection.node == null) {
        myLockSelection = false;
        mySelectionLevel = -1;
        mySelection = null;
        fireSliceSelected(new SliceSelectionEvent(null));
      }
    }
    if (myZoom != null && myZoom.node == null) {
      resetZoom();
    }
  }

  public void resetZoom() {
    myZoom = null;
    myZoomLevel = -1;
  }

  private boolean updateSlice(Slice slice, int level, boolean zoom) {
    zoom = zoom || slice == myZoom;
    boolean children = false;
    for (int i = 0; i < slice.getChildrenCount(); i++) {
      Slice child = slice.getChild(i);
      children = updateSlice(child, level + 1, zoom) || children;
    }
    zoom = zoom || children;

    slice.selected = Choreographer.lerp(slice.selected, slice == mySelection ? 1.0f : 0.0f,
                                        0.99f, mFrameLength);
    slice.visible = Choreographer.lerp(slice.visible, zoom ? 1.0f : 0.0f, 0.99f, mFrameLength);
    slice.zoom = Choreographer.lerp(slice.zoom,
                                    level < myZoomLevel ? (level == myZoomLevel - 1) ? 0.5f : 0.0f : 1.0f,
                                    0.99f, mFrameLength);

    return zoom;
  }

  private boolean updateSelectedSlice(Slice slice, float depth, float value, int level) {
    if (depth < 0.0f || value < 0.0f || value > 1.0f) {
      return false;
    }
    else if (depth < slice.getDepth()) {
      mySelectionLevel = level;
      if (mySelection != slice) {
        mySelection = slice;
        fireSliceSelected(new SliceSelectionEvent(slice.node));
      }
      return true;
    }
    else {
      depth -= slice.getDepth();
      float total = 0.0f;
      for (Slice child : slice.getChildren()) {
        total += child.getValue();
      }
      float current = 0.0f;
      for (int i = 0; i < slice.getChildrenCount(); i++) {
        Slice child = slice.getChild(i);
        float val = child.getValue() / total;
        if (value < current + val || i == slice.getChildrenCount() - 1) {
          value = (value - current) / val;
          return updateSelectedSlice(child, depth, value, level + 1);
        }
        current += val;

      }
    }
    return false;
  }

  private void fireSliceSelected(SliceSelectionEvent event) {
    for (SliceSelectionListener listener : mListeners) {
      listener.valueChanged(event);
    }
  }

  @Override
  protected void debugDraw(Graphics2D g2d) {
    addDebugInfo("Total slices: %d", mData.getCount());
    addDebugInfo("Paths %d", mPaths.size());
    g2d.setColor(Color.GREEN);
    drawArrow(g2d, mX, mY, mDirection.x, mDirection.y, mMaxDepth, Color.MAGENTA);
    drawArrow(g2d, mX, mY, mDirection.y, -mDirection.x, mMaxSide, Color.MAGENTA);
    if (mCurrentAngle == 0) {
      Path2D.Float fixed = new Path2D.Float();
      float length = (float)(Math.PI * 2.0f * mFixed) * 0.5f;
      fixed.moveTo(
        mX + (mMaxDepth * (360.0f - mCurrentAngle) / 360.0f - mFixed) * mDirection.x
        - mDirection.y * length,
        mY + (mMaxDepth * (360.0f - mCurrentAngle) / 360.0f - mFixed) * mDirection.y
        + mDirection.x * length);
      fixed.lineTo(
        mX + (mMaxDepth * (360.0f - mCurrentAngle) / 360.0f - mFixed) * mDirection.x
        + mDirection.y * length,
        mY + (mMaxDepth * (360.0f - mCurrentAngle) / 360.0f - mFixed) * mDirection.y
        - mDirection.x * length);

      g2d.draw(fixed);
    }
    else {
      drawMarker(g2d, mCenterX, mCenterY, Color.BLUE);
      Arc2D.Float fixed = new Arc2D.Float();
      fixed.setArcByCenter(mCenterX, mCenterY, mDelta + mFixed,
                           mStart + (360.0f - mCurrentAngle) * 0.5f, mCurrentAngle, Arc2D.OPEN);
      g2d.draw(fixed);
    }
  }

  private void updateArea() {
    float angle = (float)Math.toRadians(mStart);
    float a = (float)Math.cos(angle) * mY;
    float b = (float)Math.sin(angle) * mX;
    mMaxDepth = mX * mY / (float)Math.sqrt((a * a) + (b * b));
    a = (float)Math.cos(angle + Math.PI * 0.5) * mY;
    b = (float)Math.sin(angle + Math.PI * 0.5) * mX;
    mMaxSide = mX * mY / (float)Math.sqrt((a * a) + (b * b));
  }

  private float getFraction(ValuedTreeNode node) {
    TreeNode parent = node.getParent();
    assert parent == null || parent instanceof ValuedTreeNode;
    if (myUseCount) {
      return parent == null ? 1.0f
                            : (float)node.getCount() / ((ValuedTreeNode)parent).getCount();
    }
    else {
      return parent == null ? 1.0f
                            : (float)node.getValue() / ((ValuedTreeNode)parent).getValue();
    }
  }

  private static ValuedTreeNode getChildAt(ValuedTreeNode node, int i) {
    TreeNode child = node.getChildAt(i);
    assert child instanceof ValuedTreeNode;
    return (ValuedTreeNode)child;
  }

  private static float getMaxDepth(Slice slice) {
    float depth = 0.0f;
    for (Slice child : slice.getChildren()) {
      depth = Math.max(depth, getMaxDepth(child));
    }
    return depth + slice.depth;
  }

  private boolean updateStructure(Slice slice, ValuedTreeNode node, boolean hasSiblings) {
    if (node == null) {
      slice.depth = Choreographer.lerp(slice.depth, hasSiblings ? slice.depth : 0.0f,
                                       0.99f, mFrameLength);
      slice.value = Choreographer.lerp(slice.value, hasSiblings ? 0.0f : slice.value,
                                       0.99f, mFrameLength);
    }
    else {
      slice.depth = Choreographer.lerp(slice.depth, node.getParent() == null ? 0.0f : 1.0f,
                                       0.99f, mFrameLength);
      slice.value = Choreographer.lerp(slice.value, getFraction(node),
                                       0.99f, mFrameLength);
    }
    slice.node = node;

    int last = -1;
    int slices = slice.getChildrenCount();
    int nodes = node == null ? 0 : node.getChildCount();
    for (int i = 0; i < slices; i++) {
      Slice childSlice = slice.getChild(i);
      ValuedTreeNode childNode = i < nodes ? getChildAt(node, i) : null;
      if (updateStructure(childSlice, childNode, nodes > 0)) {
        last = i;
      }
    }
    // Test neighbours with the same color:
    int c = slices > 0 ? slice.getChild(0).color
                       : ((slice.color + (int)(Math.random() * COLORS.length - 1) + 1) % COLORS.length);
    for (int i = slices; i < nodes; i++) {
      ValuedTreeNode childNode = getChildAt(node, i);
      Slice childSlice = new Slice(slices > 0 ? 0.0f : getFraction(childNode));
      childSlice.color = c;
      childSlice.depth = slices > 0 ? 1.0f : 0.0f;
      slice.addChild(childSlice);
      if (updateStructure(childSlice, childNode, nodes > 0)) {
        last = i;
      }
    }

    if (last + 1 < slice.getChildrenCount()) {
      slice.clearSublist(last + 1, slice.getChildrenCount());
    }

    return node != null || (slice.depth > 0.00001f && slice.value > 0.00001f) || last >= 0;
  }

  private Path2D.Float getPath(Color color) {
    Path2D.Float path = mPaths.get(color);
    if (path == null) {
      path = new Path2D.Float();
      mPaths.put(color, path);
    }
    return path;
  }

  private void drawSlice(Slice slice, float depth, float from, float to) {
    if (slice.getDepth() > 0.0f) { // Optimization for zero width slices
      Color c = COLORS[slice.color];
      float s = slice.selected;
      Color b = HIGHLIGHTS[slice.color];
      c = new Color((int)(b.getRed() * s + c.getRed() * (1 - s)),
                    (int)(b.getGreen() * s + c.getGreen() * (1 - s)),
                    (int)(b.getBlue() * s + c.getBlue() * (1 - s)));
      Path2D.Float path = getPath(c);
      if (mCurrentAngle == 0) {
        float length = (float)(Math.PI * 2.0f * mFixed);
        float delta = mGap + depth * mSliceWidth - mMaxDepth + mSeparator * 0.5f
                      + slice.getBorder() * mSliceWidth;
        float up = length * (0.5f - from) - mSeparator * 0.5f;
        float down = length * (0.5f - to) + mSeparator * 0.5f;
        float size = mSliceWidth * slice.getDepth() - mSeparator
                     - slice.getBorder() * mSliceWidth * 2.0f;

        float deltaX = mDirection.x * delta;
        float deltaY = mDirection.y * delta;
        float upX = mDirection.y * up;
        float upY = -mDirection.x * up;
        float downX = mDirection.y * down;
        float downY = -mDirection.x * down;
        float sizeX = mDirection.x * size;
        float sizeY = mDirection.y * size;

        if (up > down) {
          path.moveTo(mX - deltaX + upX, mY - deltaY + upY);
          path.lineTo(mX - deltaX + upX - sizeX, mY - deltaY + upY - sizeY);
          path.lineTo(mX - deltaX + downX - sizeX, mY - deltaY + downY - sizeY);
          path.lineTo(mX - deltaX + downX, mY - deltaY + downY);
          path.closePath();
        }
      }
      else {
        float angle = (360.0f - mCurrentAngle) * 0.5f + mCurrentAngle * from + mStart;
        float arc = mCurrentAngle * (to - from);

        float radius = mSliceWidth * depth + mGap + mDelta;

        float outerLen = (radius + mSliceWidth * slice.getDepth()) * (float)Math.toRadians(
          arc);
        if (outerLen < 1) {
          path = getPath(Color.RED);
        }
        float outerRadius = radius + mSliceWidth * slice.getDepth() - mSeparator * 0.5f
                            - slice.getBorder() * mSliceWidth;
        float innerRadius = radius + mSeparator * 0.5f + slice.getBorder() * mSliceWidth;
        float outerAngle = (float)Math.toDegrees(Math.asin(mSeparator / outerRadius));
        if (outerAngle < arc && outerRadius > innerRadius) {
          Arc2D.Float outer = new Arc2D.Float();
          outer.setArcByCenter(mCenterX, mCenterY, outerRadius,
                               angle + outerAngle * 0.5f, arc - outerAngle, Arc2D.OPEN);
          path.append(outer, false);

          float innerAngle = (float)Math.toDegrees(Math.asin(mSeparator / innerRadius));
          if (innerAngle < arc) {
            Arc2D.Float inner = new Arc2D.Float();
            inner.setArcByCenter(mCenterX, mCenterY, innerRadius,
                                 angle + innerAngle * 0.5f + arc - innerAngle, -(arc - innerAngle),
                                 Arc2D.OPEN);
            path.append(inner, true);
          }
          else {
            float r = (float)(mSeparator * 0.5f / Math
              .sin(Math.toRadians(arc * 0.5f)));
            float dx = (float)(Math.cos(Math.toRadians(angle + arc * 0.5f)) * r);
            float dy = (float)(Math.sin(Math.toRadians(angle + arc * 0.5f)) * r);
            path.lineTo(mCenterX + dx, mCenterY - dy);
          }
          path.lineTo(outer.getStartPoint().getX(), outer.getStartPoint().getY());
        }
      }
    }

    float total = 0.0f;
    for (Slice child : slice.getChildren()) {
      total += child.getValue();
    }
    float value = 0.0f;
    for (Slice child : slice.getChildren()) {
      float childFrom = from + (value / total) * (to - from);
      float childTo = from + ((value + child.getValue()) / total) * (to - from);

      drawSlice(child, depth + slice.getDepth(), childFrom, childTo);

      value += child.getValue();
    }
  }

  public void setGap(float gap) {
    mGap = gap;
  }

  public void setSliceWidth(float sliceWidth) {
    mSliceWidth = sliceWidth;
  }

  public void setAngle(float angle) {
    mAngle = angle;
  }

  public void setStart(float start) {
    mStart = start;
    updateArea();
  }

  public void setFixed(int fixed) {
    mFixed = fixed;
  }

  public float getGap() {
    return mGap;
  }

  public float getSliceWidth() {
    return mSliceWidth;
  }

  public float getStart() {
    return mStart;
  }

  public float getFixed() {
    return mFixed;
  }

  public float getAngle() {
    return mAngle;
  }

  public float getSeparator() {
    return mSeparator;
  }

  public void setSeparator(float separator) {
    mSeparator = separator;
  }

  public void setData(ValuedTreeNode data) {
    mData = data;
  }

  public ValuedTreeNode getData() {
    return mData;
  }

  public void setAutoSize(boolean autoSize) {
    mAutoSize = autoSize;
  }

  public void setUseCount(boolean useCount) {
    myUseCount = useCount;
  }

  public void addSelectionListener(SliceSelectionListener listener) {
    mListeners.add(listener);
  }

  public void removeSelectionListener(SliceSelectionListener listener) {
    mListeners.remove(listener);
  }

  static class Slice {

    private ArrayList<Slice> children = new ArrayList<Slice>();

    Slice parent;

    float value;

    float depth;

    int color;

    float hover;

    float zoom;

    float selected;

    float visible;

    ValuedTreeNode node;

    public float getValue() {
      return value * visible;
    }

    public float getBorder() {
      return selected * depth * 0.05f;
    }

    public float getDepth() {
      return zoom * depth + getBorder() * 2.0f;
    }

    public Slice(float value) {
      this.value = value;
      this.depth = 1.0f;
      this.hover = 0.0f;
      this.selected = 0.0f;
      this.zoom = 1.0f;
      this.visible = 1.0f;
    }

    public int getChildrenCount() {
      return children.size();
    }

    public void addChild(Slice child) {
      children.add(child);
      child.parent = this;
    }

    public Slice getChild(int i) {
      return children.get(i);
    }

    public void clearSublist(int from, int to) {
      for (int i = from; i < to; i++) {
        children.get(i).parent = null;
      }
      children.subList(from, to).clear();
    }

    public ArrayList<Slice> getChildren() {
      return children;
    }
  }

  public static class SliceSelectionEvent {

    private final ValuedTreeNode mNode;

    public SliceSelectionEvent(ValuedTreeNode node) {
      mNode = node;
    }

    public ValuedTreeNode getNode() {
      return mNode;
    }
  }

  public interface SliceSelectionListener {
    void valueChanged(SliceSelectionEvent e);
  }
}
