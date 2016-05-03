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
package com.android.tools.adtui;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import gnu.trove.TIntObjectHashMap;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.List;

/**
 * A component to display a TimelineData object. It locks the timeline object to prevent
 * modifications to it while it's begin rendered, but objects of this class should not be accessed
 * from different threads.
 */
public final class TimelineComponent extends AnimatedComponent {

  private static final Color TEXT_COLOR = new Color(128, 128, 128);

  private static final int LEFT_MARGIN = 120;

  private static final int RIGHT_MARGIN = 200;

  private static final int TOP_MARGIN = 10;

  private static final int BOTTOM_MARGIN = 30;

  /**
   * The number of pixels a second in the timeline takes on the screen.
   */
  private static final float X_SCALE = 20;

  private final float mBufferTime;

  @NonNull
  private final TimelineData mData;

  @NonNull
  private final EventData mEvents;

  private final float mInitialMax;

  private final float mAbsoluteMax;

  private final float mInitialMarkerSeparation;

  private Map<Integer, Style> mStyles;

  private boolean mFirstFrame;

  /**
   * The boolean value whether to stack all streams together, by default it is true.
   */
  private boolean mStackStreams = true;

  /**
   * The current maximum range in y-axis units.
   */
  private float mCurrentMax;

  /**
   * The current minimum non-negative range in y-axis units.
   */
  private float mCurrentMin;

  /**
   * Marker separation in y-axis units.
   */
  private float mMarkerSeparation;

  /**
   * The current alpha of markers at even positions. When there are not enough/too many markers,
   * the markers at even positions are faded in/out respectively. This tracks the animated alpha
   * of such markers.
   */
  private float mEvenMarkersAlpha;

  /**
   * The current value in pixels where the x-axis is drawn.
   */
  private int mBottom;

  /**
   * The current value in pixels where the right hand side y-axis is drawn.
   */
  private int mRight;

  /**
   * The current scale from y-axis values to pixels.
   */
  private float mYScale;

  /**
   * The current time value at the right edge of the timeline in seconds.
   */
  private float mEndTime;

  /**
   * The current time value at the left edge of the timeline in seconds.
   */
  private float mBeginTime;

  /**
   * How to render each event type.
   */
  private TIntObjectHashMap<EventInfo> mEventsInfo;

  /**
   * The units of the y-axis values.
   */
  private String mUnits;

  /**
   * The number of available local samples.
   */
  private int mSize;

  /**
   * The times at which the samples occurred.
   */
  private float[] mTimes;

  /**
   * The times at which the samples occurred.
   */
  private int[] mTypes;

  /**
   * Each list member represents a different stream.
   */
  private final List<StreamComponent> mStreamComponents = new ArrayList<StreamComponent>();

  private final List<LabelRow> mLabelRows = new ArrayList<LabelRow>();

  /**
   * Listeners which are notified when streams are modified.
   */
  private final List<Listener> myListeners = new ArrayList<Listener>();

  /**
   * The number of events to render.
   */
  private int mEventsSize;

  /**
   * The start time of each event.
   */
  private float[] mEventStart;

  /**
   * The end time of each event, if NaN then the event did not end.
   */
  private float[] mEventEnd;

  /**
   * The type of each event.
   */
  private int[] mEventTypes;

  /**
   * The animated angle of an event in progress.
   */
  private float mEventProgressStart;

  /**
   * The direction of the event animation.
   */
  private float mEventProgressDir = 1.0f;

  /**
   * The current state for all in-progress events.
   */
  private float mEventProgress;

  /**
   * The reference values for which guiding horizontal lines are drawn. The colored lines provide guidance like the stream values
   * should be at most a reference value, or at least a reference value.
   */
  private List<Reference> mReferences = new ArrayList<Reference>();

  /**
   * Creates a timeline component that renders the given timeline data. It will animate the
   * timeline data by showing the value at the current time on the right y-axis of the graph.
   *
   * @param data                    the data to be displayed.
   * @param bufferTime              the time, in seconds, to lag behind the given {@code data}.
   * @param initialMax              the initial maximum value for the y-axis.
   * @param absoluteMax             the absolute maximum value for the y-axis.
   * @param initialMarkerSeparation the initial separations for the markers on the y-axis.
   */
  public TimelineComponent(
    @NonNull TimelineData data,
    @NonNull EventData events,
    float bufferTime,
    float initialMax,
    float absoluteMax,
    float initialMarkerSeparation) {
    mData = data;
    mEvents = events;
    mBufferTime = bufferTime;
    mInitialMax = initialMax;
    mAbsoluteMax = absoluteMax;
    mInitialMarkerSeparation = initialMarkerSeparation;
    mSize = 0;
    mStyles = new HashMap<Integer, Style>();
    mUnits = "";
    mEventsInfo = new TIntObjectHashMap<EventInfo>();
    setOpaque(true);
    // TODO: Improve the initial stream set up, as MonitorView needs configure stream name, color, etc before the first data update.
    for (int i = 0; i < mData.getStreamCount(); i++) {
      addStream(mData.getStream(i).getId());
    }
    reset();
  }

  public void setStackStreams(boolean stackStreams) {
    mStackStreams = stackStreams;
  }

  public void configureStream(int stream, String name, Color color) {
    configureStream(stream, name, color, false);
  }

  public void configureStream(int stream, String name, Color color, boolean isMirrored) {
    assert stream < mStreamComponents.size() : String
      .format("Attempting to configure out of bounds stream: Stream: %1$d, Size %2$d", stream, mStreamComponents.size());
    StreamComponent streamComponent = mStreamComponents.get(stream);
    streamComponent.name = name;
    streamComponent.color = color;
    streamComponent.isMirrored = isMirrored;
  }

  private void addStream(String id) {
    int newStreamIndex = mStreamComponents.size();
    int streamValuesSize = mTimes != null ? mTimes.length : 0;
    StreamComponent component = new StreamComponent(streamValuesSize, id, Color.BLACK, false);
    mStreamComponents.add(component);
    mLabelRows.add(new LabelRow(component, null));
    for (Listener listener : myListeners) {
      listener.onStreamAdded(newStreamIndex, id);
    }
  }

  /**
   * Returns true if two streams are present and linked to each other, false otherwise. If two stream are linked, their labels will be
   * combined into one.
   */
  public boolean linkStreams(@NonNull String streamId1, @NonNull String streamId2) {
    assert !streamId1.equals(streamId2) : String.format("Attempt to link a stream %1$s with itself", streamId1);
    LabelRow labelRow1 = null;
    LabelRow labelRow2 = null;
    StreamComponent stream1 = null;
    StreamComponent stream2 = null;
    for (LabelRow row : mLabelRows) {
      if (row.stream1.id.equals(streamId1)) {
        labelRow1 = row;
        stream1 = row.stream1;
      }
      else if (row.stream1.id.equals(streamId2)) {
        labelRow2 = row;
        stream2 = row.stream1;
      }
    }
    if (stream1 != null && stream2 != null) {
      labelRow1.stream2 = stream2;
      mLabelRows.remove(labelRow2);
      return true;
    }
    return false;
  }

  public void addListener(@NonNull Listener listener) {
    myListeners.add(listener);
  }

  public void configureEvent(int type, int stream, Icon icon, Color color,
                             Color progress, boolean range) {
    mEventsInfo.put(type, new EventInfo(type, stream, icon, color, progress, range));
  }

  public void configureType(int type, Style style) {
    mStyles.put(type, style);
  }

  public void configureUnits(String units) {
    mUnits = units;
  }

  public void reset() {
    mCurrentMax = mInitialMax;
    mCurrentMin = 0.0f;
    mMarkerSeparation = mInitialMarkerSeparation;
    mEvenMarkersAlpha = 1.0f;
    mFirstFrame = true;
  }

  @Override
  protected void draw(Graphics2D g2d) {
    Dimension dim = getSize();

    mBottom = Math.max(TOP_MARGIN, dim.height - BOTTOM_MARGIN);
    mRight = Math.max(LEFT_MARGIN, dim.width - RIGHT_MARGIN);

    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2d.setFont(DEFAULT_FONT);
    g2d.setClip(0, 0, dim.width, dim.height);
    g2d.setColor(getBackground());
    g2d.fillRect(0, 0, dim.width, dim.height);
    g2d.setClip(LEFT_MARGIN, TOP_MARGIN, mRight - LEFT_MARGIN, mBottom - TOP_MARGIN);
    drawTimelineData(g2d);
    drawEvents(g2d);
    g2d.setClip(0, 0, dim.width, dim.height);
    drawLabels(g2d);
    drawTimeMarkers(g2d);
    drawMarkers(g2d);
    drawReferenceLines(g2d);
    drawGuides(g2d);

    mFirstFrame = false;
  }

  @Override
  protected void debugDraw(Graphics2D g2d) {
    int drawn = 0;
    g2d.setFont(DEFAULT_FONT.deriveFont(5.0f));
    for (int i = 0; i < mSize; ++i) {
      if (mTimes[i] > mBeginTime && mTimes[i] < mEndTime) {
        for (int j = 0; j < mStreamComponents.size(); ++j) {
          int x = (int)timeToX(mTimes[i]);
          int y = (int)valueToY(mStreamComponents.get(j).values[i]);
          g2d.setColor(new Color((17 * mTypes[i]) % 255, (121 * mTypes[i]) % 255,
                                 (71 * mTypes[i]) % 255));
          g2d.drawLine(x, y - 2, x, y + 2);
          g2d.drawLine(x - 2, y, x + 2, y);
          g2d.setColor(TEXT_COLOR);
        }
        drawn++;
      }
    }

    addDebugInfo("Drawn samples: %d", drawn);
  }

  private void drawTimelineData(Graphics2D g2d) {
    mYScale = (mBottom - TOP_MARGIN) / (mCurrentMax - mCurrentMin);
    if (mSize > 1) {
      int from = 0;
      // Optimize to not render too many samples since they get clipped.
      while (from < mSize - 1 && mTimes[from + 1] < mBeginTime) {
        from++;
      }
      int to = from;
      while (to + 1 < mSize && mTimes[to] <= mEndTime) {
        to++;
      }
      if (from == to) {
        return;
      }
      int drawnSegments = 0;
      for (int j = mStreamComponents.size() - 1; j >= 0; j--) {
        Path2D.Float path = new Path2D.Float();
        path.moveTo(timeToX(mTimes[from]), valueToY(0.0f));
        float[] values = mStreamComponents.get(j).values;
        for (int i = from; i <= to; i++) {
          path.lineTo(timeToX(mTimes[i]), valueToY(Math.min(values[i], mAbsoluteMax)));
        }
        path.lineTo(timeToX(mTimes[to]), valueToY(0.0f));
        g2d.setColor(mStreamComponents.get(j).color);
        g2d.fill(path);

        if (!mStyles.isEmpty()) {
          path = new Path2D.Float();
          Stroke current = g2d.getStroke();
          float step = 3.0f;
          float x0 = timeToX(mTimes[from]);
          float y0 = valueToY(values[from]);
          g2d.setColor(mStreamComponents.get(j).color.darker());
          Stroke stroke = null;
          float strokeScale = Float.NaN;
          for (int i = from + 1; i <= to; i++) {
            float x1 = timeToX(mTimes[i]);
            float y1 = valueToY(values[i]);
            Style style = mStyles.get(mTypes[i]);
            if (style != null && style != Style.NONE) {
              BasicStroke str = new BasicStroke(1.0f);
              float scale = 0;
              if (style == Style.DASHED) {
                float distance = (float)Point2D.distance(x0, y0, x1, y1);
                float delta = mTimes[i] * X_SCALE;
                scale = distance / (x1 - x0);
                str = new BasicStroke(1.0f, BasicStroke.CAP_ROUND,
                                      BasicStroke.JOIN_ROUND, 0.0f, new float[]{step * scale},
                                      (delta * scale) % (step * scale * 2));
              }
              if (scale != strokeScale) {
                if (stroke != null) {
                  g2d.setStroke(stroke);
                  g2d.draw(path);
                  path.reset();
                  drawnSegments++;
                }
                strokeScale = scale;
                stroke = str;
                path.moveTo(x0, y0);
              }
              path.lineTo(x1, y1);
            }
            x0 = x1;
            y0 = y1;
          }
          if (stroke != null) {
            g2d.setStroke(stroke);
            g2d.draw(path);
            drawnSegments++;
          }
          g2d.setStroke(current);
        }
      }
      addDebugInfo("Drawn segments: %d", drawnSegments);
    }
    addDebugInfo("Total samples: %d", mSize);
  }

  private float interpolate(int stream, int sample, float time) {
    int prev = sample > 0 ? sample - 1 : 0;
    int next = sample < mSize ? sample : mSize - 1;
    float[] values = mStreamComponents.get(stream).values;
    float a = values[prev];
    float b = values[next];
    float delta = mTimes[next] - mTimes[prev];
    float ratio = delta != 0 ? (time - mTimes[prev]) / delta : 1.0f;
    return (b - a) * ratio + a;
  }

  private void drawEvents(Graphics2D g2d) {

    if (mSize > 0) {
      int drawnEvents = 0;
      AffineTransform tx = g2d.getTransform();
      Stroke stroke = g2d.getStroke();
      int s = 0;
      int e = 0;
      while (e < mEventsSize) {
        if (s < mSize && mTimes[s] < mEventStart[e]) {
          s++;
        }
        else if (Float.isNaN(mEventEnd[e])
                 || mEventEnd[e] > mBeginTime && mEventEnd[e] > mTimes[0]) {
          drawnEvents++;
          EventInfo info = mEventsInfo.get(mEventTypes[e]);
          float x = timeToX(mEventStart[e]);
          float y = valueToY(interpolate(info.stream, s, mEventStart[e]));
          AffineTransform dt = new AffineTransform(tx);
          dt.translate(x, y);
          g2d.setTransform(dt);
          info.icon.paintIcon(this, g2d, -info.icon.getIconWidth() / 2,
                              -info.icon.getIconHeight() - 5);
          g2d.setTransform(tx);

          g2d.setStroke(
            new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          Path2D.Float p = new Path2D.Float();
          boolean closed = !Float.isNaN(mEventEnd[e]);
          if (info.range) {
            p.moveTo(x, mBottom);
            p.lineTo(x, y);
            float endTime = Float.isNaN(mEventEnd[e]) ? mEndTime : mEventEnd[e];
            int i = s;
            float[] values = mStreamComponents.get(info.stream).values;
            for (; i < mSize && mTimes[i] < endTime; i++) {
              p.lineTo(timeToX(mTimes[i]), valueToY(values[i]));
            }
            p.lineTo(timeToX(endTime), valueToY(interpolate(info.stream, i, endTime)));
            p.lineTo(timeToX(closed ? mEventEnd[e] : endTime), valueToY(0));
            if (info.color != null) {
              g2d.setColor(info.color);
              g2d.fill(p);
            }
            g2d.setColor(info.progress);
            g2d.draw(p);
          }
          else {
            p.moveTo(x, y - 2.0f);
            p.lineTo(x, y + 2.0f);
            g2d.setColor(info.progress);
            g2d.draw(p);
          }
          if (!closed) {
            g2d.setColor(info.progress);
            // Draw in progress marker
            float end = 360 * mEventProgress;
            float start = mEventProgressStart;
            if (mEventProgressDir < 0.0f) {
              start += end;
              end = 360 - end;
            }
            g2d.draw(new Arc2D.Float(
              x + info.icon.getIconWidth() / 2 + 3,
              y - info.icon.getIconHeight() - 3,
              6, 6,
              start, end, Arc2D.OPEN));

          }
          e++;
        }
        else {
          e++;
        }
      }
      g2d.setStroke(stroke);
      addDebugInfo("Drawn events: %d", drawnEvents);
    }
  }

  private float valueToY(float val) {
    return mBottom - (val - mCurrentMin) * mYScale;
  }

  private float timeToX(float time) {
    return LEFT_MARGIN + (time - mBeginTime) * X_SCALE;
  }

  private void drawLabels(Graphics2D g2d) {
    g2d.setFont(DEFAULT_FONT);
    FontMetrics metrics = g2d.getFontMetrics();
    int y = TOP_MARGIN + 15;
    for (int i = mLabelRows.size() - 1; i >= 0 && mSize > 0; i--, y += 20) {
      LabelRow labelRow = mLabelRows.get(i);
      StreamComponent stream1 = labelRow.stream1;
      if (labelRow.stream2 == null) {
        g2d.setColor(stream1.color);
        g2d.fillRect(mRight + 20, y, 15, 15);
        g2d.setColor(TEXT_COLOR);
        g2d.drawString(String.format("%s [%.2f %s]", stream1.name, stream1.currentValue, mUnits), mRight + 40,
                       y + 7 + metrics.getAscent() * .5f);
      }
      else {
        StreamComponent stream2 = labelRow.stream2;
        fillTriangle(new Point(mRight + 20, y), new Point(mRight + 35, y), new Point(mRight + 20, y + 15), stream2.color, g2d);
        fillTriangle(new Point(mRight + 35, y), new Point(mRight + 35, y + 15), new Point(mRight + 20, y + 15), stream1.color, g2d);
        g2d.setColor(TEXT_COLOR);
        g2d.drawString(String
                         .format("%1$s, %2$s [%3$.2f %4$s, %5$.2f %6$s]", stream1.name, stream2.name, stream1.currentValue,
                                 mUnits, stream2.currentValue, mUnits), mRight + 40, y + 7 + metrics.getAscent() * .5f);
      }
    }
  }

  private void fillTriangle(Point p1, Point p2, Point p3, Color color, Graphics2D g2d) {
    g2d.setColor(color);
    Path2D path = new Path2D.Float();
    path.moveTo(p1.getX(), p1.getY());
    path.lineTo(p2.getX(), p2.getY());
    path.lineTo(p3.getX(), p3.getY());
    path.lineTo(p1.getX(), p1.getY());
    g2d.fill(path);
  }

  private void drawTimeMarkers(Graphics2D g2d) {
    g2d.setFont(DEFAULT_FONT);
    g2d.setColor(TEXT_COLOR);
    FontMetrics metrics = g2d.getFontMetrics();
    float offset = metrics.stringWidth("000") * 0.5f;
    Path2D.Float lines = new Path2D.Float();
    float zeroY = valueToY(0.0f);
    for (int sec = Math.max((int)Math.ceil(mBeginTime), 0); sec < mEndTime; sec++) {
      float x = timeToX(sec);
      boolean big = sec % 5 == 0;
      if (big) {
        String text = formatTime(sec);
        g2d.drawString(text, x - metrics.stringWidth(text) + offset, zeroY + metrics.getAscent() + 5);
      }
      lines.moveTo(x, zeroY);
      lines.lineTo(x, zeroY + (big ? 5 : 2));
    }
    g2d.draw(lines);
  }

  @VisibleForTesting
  static String formatTime(int seconds) {
    final char[] suffix = {'h', 'm', 's'};
    final int[] secsPer = {60 * 60, 60, 1};

    StringBuilder sb = new StringBuilder(12); // "999h 59m 59s"
    for (int i = 0; i < suffix.length; i++) {
      int value = seconds / secsPer[i];
      seconds = seconds % secsPer[i];
      if (value == 0 && sb.length() == 0 && i != (suffix.length - 1)) {
        continue;
      }

      if (sb.length() > 0) {
        sb.append(' ');
      }
      sb.append(value);
      sb.append(suffix[i]);
    }
    return sb.toString();
  }

  private void drawMarkers(Graphics2D g2d) {
    drawMarkers(g2d, 1.0f, mCurrentMax);
    drawMarkers(g2d, -1.0f, mCurrentMin);
    if (mCurrentMin < 0) {
      int zeroY = (int)valueToY(0);
      int minimumGap = getFontMetrics(DEFAULT_FONT).getAscent();
      // Draw the zero marker if it is not overlapped by other markers.
      if (mBottom - zeroY > minimumGap && zeroY - TOP_MARGIN > minimumGap) {
        drawValueMarker(0, zeroY, g2d);
      }
    }
  }

  private void drawMarkers(Graphics2D g2d, float direction, float max) {
    if (mYScale <= 0) {
      return;
    }

    boolean drawNegativeMarkersAsPositive = hasMirroredStream();
    int markers = (int)(max / mMarkerSeparation * direction);
    for (int i = 0; i < markers + 1; i++) {
      float markerValue = (i + 1) * mMarkerSeparation * direction;
      int y = (int)valueToY(markerValue);
      // Too close to the end
      if (direction * (max - markerValue) < mMarkerSeparation * 0.5f) {
        markerValue = max;
        //noinspection AssignmentToForLoopParameter
        i = markers;
        y = (int)valueToY(max);
      }
      if (i < markers && i % 2 == 0 && mEvenMarkersAlpha < 1.0f) {
        g2d.setColor(
          new Color(TEXT_COLOR.getColorSpace(), TEXT_COLOR.getColorComponents(null),
                    mEvenMarkersAlpha));
      }
      else {
        g2d.setColor(TEXT_COLOR);
      }
      g2d.drawLine(LEFT_MARGIN - 2, y, LEFT_MARGIN, y);
      drawValueMarker(drawNegativeMarkersAsPositive ? Math.abs(markerValue) : markerValue, y, g2d);
    }
  }

  private void drawValueMarker(float value, int y, Graphics2D g2d) {
    FontMetrics metrics = getFontMetrics(DEFAULT_FONT);
    String marker = String.format("%.2f %s", value, mUnits);
    g2d.drawString(marker, LEFT_MARGIN - 10 - metrics.stringWidth(marker), y + metrics.getAscent() * 0.5f);
  }

  private boolean hasMirroredStream() {
    for (int i = 0; i < mStreamComponents.size(); i++) {
      if (mStreamComponents.get(i).isMirrored) {
        return true;
      }
    }
    return false;
  }

  private void drawReferenceLines(Graphics2D g2d) {
    for (Reference reference : mReferences) {
      if (reference.value <= mCurrentMax && reference.value >= mCurrentMin) {
        g2d.setColor(reference.color);
        int y = (int)valueToY(reference.value);
        g2d.drawLine(LEFT_MARGIN, y, mRight, y);
      }
    }
  }

  public void addReference(float reference, @NonNull Color color) {
    mReferences.add(new Reference(reference, color));
  }

  private void drawGuides(Graphics2D g2d) {
    g2d.setColor(TEXT_COLOR);
    int zeroY = (int)valueToY(0.0f);
    g2d.drawLine(LEFT_MARGIN - 10, zeroY, mRight + 10, zeroY);
    if (mYScale > 0) {
      g2d.drawLine(LEFT_MARGIN, mBottom, LEFT_MARGIN, TOP_MARGIN);
      g2d.drawLine(mRight, mBottom, mRight, TOP_MARGIN);
    }
  }

  private void removeStreamFromLabelRow(StreamComponent streamComponent) {
    Iterator<LabelRow> iterator = mLabelRows.iterator();
    while (iterator.hasNext()) {
      LabelRow labelRow = iterator.next();
      if (streamComponent.equals(labelRow.stream1)) {
        if (labelRow.stream2 != null) {
          labelRow.stream1 = labelRow.stream2;
          labelRow.stream2 = null;
        }
        else {
          iterator.remove();
        }
        break;
      }
      else if (streamComponent.equals(labelRow.stream2)) {
        labelRow.stream2 = null;
        break;
      }
    }
  }

  private void updateStreams() {
    int streamCountFromData = mData.getStreamCount();
    int streamIndex = 0;
    Iterator<StreamComponent> iterator = mStreamComponents.iterator();
    while (iterator.hasNext()) {
      StreamComponent streamComponent = iterator.next();
      if (streamIndex < streamCountFromData && streamComponent.id.equals(mData.getStream(streamIndex).getId())) {
        streamIndex++;
      }
      else {
        iterator.remove();
        removeStreamFromLabelRow(streamComponent);
      }
    }
    for (int i = streamIndex; i < streamCountFromData; i++) {
      addStream(mData.getStream(i).getId());
    }
  }

  @Override
  protected void updateData() {
    long start;
    synchronized (mData) {
      updateStreams();

      start = mData.getStartTime();
      mSize = mData.size();
      float lastUpdatedTime = mTimes != null ? mTimes[mTimes.length - 1] : 0;
      if (mTimes == null || mTimes.length < mSize) {
        int alloc = Math.max(mSize, mTimes == null ? 64 : mTimes.length * 2);
        mTimes = new float[alloc];
        mTypes = new int[alloc];
        for (int j = 0; j < mData.getStreamCount(); ++j) {
          mStreamComponents.get(j).values = new float[alloc];
        }
      }

      for (int i = 0; i < mSize; ++i) {
        TimelineData.SampleInfo info = mData.getSampleInfo(i);
        mTimes[i] = info.time;
        mTypes[i] = info.type;
      }

      float cappedMax = 0;
      float cappedMin = 0;
      StreamComponent lastStreamForNonMirroredStack = null;
      StreamComponent lastStreamForMirroredStack = null;
      for (int streamIndex = 0; streamIndex < mStreamComponents.size(); streamIndex++) {
        StreamComponent streamComponent = mStreamComponents.get(streamIndex);
        TimelineData.Stream streamFromData = mData.getStream(streamIndex);
        for (int i = 0; i < mSize; ++i) {
          float value = streamFromData.get(i);
          if (streamComponent.isMirrored) {
            value = -Math.abs(value);
          }
          if (mStackStreams) {
            if (streamComponent.isMirrored && lastStreamForMirroredStack != null) {
              value += lastStreamForMirroredStack.values[i];
            }
            else if (!streamComponent.isMirrored && lastStreamForNonMirroredStack != null) {
              value += lastStreamForNonMirroredStack.values[i];
            }
          }
          streamComponent.values[i] = value;
          if (mTimes[i] > lastUpdatedTime) {
            cappedMax = Math.max(cappedMax, value);
            cappedMin = Math.min(cappedMin, value);
          }
        }
        if (mSize > 0) {
          streamComponent.currentValue = streamFromData.get(mSize - 1);
        }
        if (streamComponent.isMirrored) {
          lastStreamForMirroredStack = streamComponent;
        }
        else {
          lastStreamForNonMirroredStack = streamComponent;
        }
      }

      // Calculate begin and end times in seconds.
      mEndTime = mData.getEndTime() - mBufferTime;
      mBeginTime = mEndTime - (mRight - LEFT_MARGIN) / X_SCALE;
      // Animate the current maximum towards the real one.
      cappedMax = Math.min(mAbsoluteMax, Math.max(mCurrentMax, cappedMax));
      cappedMin = Math.max(-mAbsoluteMax, Math.min(mCurrentMin, cappedMin));
      if (cappedMax > mCurrentMax) {
        mCurrentMax = Choreographer.lerp(mCurrentMax, cappedMax,
                                         mFirstFrame ? 1.f : .95f, mFrameLength);
      }
      if (cappedMin == 0.0f || cappedMin < mCurrentMin) {
        mCurrentMin = Choreographer.lerp(mCurrentMin, cappedMin,
                                         mFirstFrame ? 1.f : .95f, mFrameLength);
      }

      // Animate the fade in/out of markers.
      FontMetrics metrics = getFontMetrics(DEFAULT_FONT);
      int ascent = metrics.getAscent();
      float distance = mMarkerSeparation * mYScale;
      float evenMarkersTarget = 1.0f;
      if (distance < ascent * 2) { // Too many markers
        if (mEvenMarkersAlpha < 0.1f) {
          mMarkerSeparation *= 2;
          mEvenMarkersAlpha = 1.0f;
        }
        else {
          evenMarkersTarget = 0.0f;
        }
      }
      else if (distance > ascent * 5) { // Not enough
        if (mEvenMarkersAlpha > 0.9f) {
          mMarkerSeparation /= 2;
          mEvenMarkersAlpha = 0.0f;
        }
      }
      mEvenMarkersAlpha = Choreographer.lerp(mEvenMarkersAlpha, evenMarkersTarget,
                                             0.999f, mFrameLength);
    }
    synchronized (mEvents) {
      mEventsSize = mEvents.size();
      if (mEventStart == null || mEventStart.length < mEventsSize) {
        int alloc = Math.max(mEventsSize, mEventStart == null ? 64 : mEventStart.length * 2);
        mEventStart = new float[alloc];
        mEventEnd = new float[alloc];
        mEventTypes = new int[alloc];

      }
      for (int i = 0; i < mEventsSize; i++) {
        EventData.Event event = mEvents.get(i);
        mEventStart[i] = (event.from - start) / 1000.0f;
        mEventEnd[i] = event.to == -1 ? Float.NaN : (event.to - start) / 1000.0f;
        mEventTypes[i] = event.type;
      }

      // Animate events in progress
      if (mEventProgress > 0.95f) {
        mEventProgressDir = -mEventProgressDir;
        mEventProgress = 0.0f;
      }
      mEventProgressStart = (mEventProgressStart + mFrameLength * 200.0f) % 360.0f;
      mEventProgress = Choreographer.lerp(mEventProgress, 1.0f, .99f, mFrameLength);
    }
  }

  public interface Listener {

    /**
     * An event fired whenever a new Stream is added into a TimelineComponent. It's often a good idea to call
     * {@link TimelineComponent#configureStream(int, String, Color, boolean)} at this point.
     */
    void onStreamAdded(int stream, @NonNull String id);
  }

  private static class StreamComponent {

    /**
     * The values array length should be the same as the length of mTimes.
     */
    public float[] values;

    /**
     * The stream's latest value.
     */
    public float currentValue;

    public final String id;

    public String name;

    public Color color;

    public boolean isMirrored;

    public StreamComponent(int valueSize, @NonNull String id, @NonNull Color color, boolean isMirrored) {
      this.values = new float[valueSize];
      this.currentValue = 0;
      this.id = id;
      this.name = id;
      this.color = color;
      this.isMirrored = isMirrored;
    }
  }

  private static class LabelRow {

    @NonNull public StreamComponent stream1;

    @Nullable public StreamComponent stream2;

    public LabelRow(@NonNull StreamComponent stream1, @Nullable StreamComponent stream2) {
      this.stream1 = stream1;
      this.stream2 = stream2;
    }
  }

  public enum Style {
    NONE,
    SOLID,
    DASHED
  }

  private static class Reference {

    public final float value;

    @NonNull
    public final Color color;

    private Reference(float value, @NonNull Color color) {
      this.value = value;
      this.color = color;
    }
  }

  private static class EventInfo {

    public final int type;

    public final int stream;

    public final Icon icon;

    public final Color color;

    public final Color progress;

    public final boolean range;

    private EventInfo(int type, int stream, Icon icon, Color color,
                      Color progress, boolean range) {
      this.type = type;
      this.stream = stream;
      this.icon = icon;
      this.color = color;
      this.progress = progress;
      this.range = range;
    }
  }
}
