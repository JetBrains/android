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
package com.android.tools.idea.monitor;

import com.android.annotations.VisibleForTesting;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;

/**
 * A component to display a TimelineData object. It locks the timeline object to prevent modifications to it while it's begin
 * rendered, but objects of this class should not be accessed from different threads.
 */
@SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized", "UseJBColor"})
public class TimelineComponent extends JComponent implements ActionListener, HierarchyListener {

  private static final Color TEXT_COLOR = Gray._128;
  private static final Font TIMELINE_FONT = new Font("Sans", Font.PLAIN, 10);

  private static final int LEFT_MARGIN = 120;
  private static final int RIGHT_MARGIN = 200;
  private static final int TOP_MARGIN = 10;
  private static final int BOTTOM_MARGIN = 30;
  private static final int FPS = 40;

  /**
   * The number of pixels a second in the timeline takes on the screen.
   */
  private static final float X_SCALE = 20;

  private final float myBufferTime;
  @NotNull private final TimelineData myData;
  private final float myInitialMax;
  private final float myInitialMarkerSeparation;
  private final Timer myTimer;
  private String[] myStreamNames;
  private Color[] myStreamColors;
  private boolean myFirstFrame;
  private long myLastRenderTime;
  private Path2D.Float[] myPaths;
  private boolean myDrawDebugInfo;
  /**
   * The current maximum range in y-axis units.
   */
  private float myCurrentMax;
  /**
   * Marker separation in y-axis units.
   */
  private float myMarkerSeparation;
  /**
   * The current alpha of markers at even positions. When there are not enough/too many markers, the markers
   * at even positions are faded in/out respectively. This tracks the animated alpha of such markers.
   */
  private float myEvenMarkersAlpha;
  /**
   * The current value in pixels where the x-axis is drawn.
   */
  private int myBottom;
  /**
   * The current value in pixels where the right hand side y-axis is drawn.
   */
  private int myRight;
  /**
   * The length of the last frame in seconds.
   */
  private float myFrameLength;
  /**
   * The current scale from y-axis values to pixels.
   */
  private float myYScale;
  /**
   * The current time value at the right edge of the timeline in seconds.
   */
  private float myEndTime;
  /**
   * The current time value at the left edge of the timeline in seconds.
   */
  private float myBeginTime;
  /**
   * The current state for all in-progress markers.
   */
  private float myEventProgress;
  /**
   * Which sample types should be rendered as events.
   */
  private TIntObjectHashMap<Event> myEvents;
  /**
   * The units of the y-axis values.
   */
  private String myUnits;

  /**
   * Creates a timeline component that renders the given timeline data. It will animate the timeline data by showing the
   * value at the current time on the right y-axis of the graph.
   *
   * @param data                    the data to be displayed.
   * @param bufferTime              the time, in seconds, to lag behind the given {@code data}.
   * @param initialMax              the initial maximum value for the y-axis.
   * @param initialMarkerSeparation the initial separations for the markers on the y-axis.
   */
  public TimelineComponent(@NotNull TimelineData data, float bufferTime, float initialMax, float initialMarkerSeparation) {
    myData = data;
    myBufferTime = bufferTime;
    myInitialMax = initialMax;
    myInitialMarkerSeparation = initialMarkerSeparation;
    int streams = myData.getStreamCount();
    myTimer = new Timer(1000 / FPS, this);
    addHierarchyListener(this);
    myPaths = new Path2D.Float[streams];
    myStreamNames = new String[streams];
    myStreamColors = new Color[streams];
    for (int i = 0; i < streams; i++) {
      myPaths[i] = new Path2D.Float();
      myStreamNames[i] = "Stream " + i;
      myStreamColors[i] = Color.BLACK;
    }
    myUnits = "";
    myEvents = new TIntObjectHashMap<Event>();
    setOpaque(true);
    reset();
  }

  public void configureStream(int stream, String name, JBColor color) {
    myStreamNames[stream] = name;
    myStreamColors[stream] = color;
  }

  public void configureEvent(int typeFrom, int typeTo, int stream, Icon icon, Color color, Color progress) {
    myEvents.put(typeFrom, new Event(typeFrom, typeTo, stream, icon, color, progress));
  }

  public void configureUnits(String units) {
    myUnits = units;
  }

  /**
   * A linear interpolation that accumulates over time. This gives an exponential effect where the value {@code from} moves
   * towards the value {@code to} at a rate of {@code fraction} per second. The actual interpolated amount depends
   * on the current frame length.
   *
   * @param from     the value to interpolate from.
   * @param to       the target value.
   * @param fraction the interpolation fraction.
   * @return the interpolated value.
   */
  private float lerp(float from, float to, float fraction) {
    float q = (float)Math.pow(1.0f - fraction, myFrameLength);
    return from * q + to * (1.0f - q);
  }

  public void reset() {
    myCurrentMax = myInitialMax;
    myMarkerSeparation = myInitialMarkerSeparation;
    myEvenMarkersAlpha = 1.0f;
    myFirstFrame = true;
  }

  public boolean isDrawDebugInfo() {
    return myDrawDebugInfo;
  }

  public void setDrawDebugInfo(boolean drawDebugInfo) {
    myDrawDebugInfo = drawDebugInfo;
  }

  @Override
  public void paintComponent(Graphics g) {
    Graphics2D g2d = (Graphics2D)g.create();
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    g2d.setFont(TIMELINE_FONT);

    Dimension dim = getSize();

    g2d.setClip(0, 0, dim.width, dim.height);
    g2d.setColor(getBackground());
    g2d.fillRect(0, 0, dim.width, dim.height);

    myBottom = dim.height - BOTTOM_MARGIN;
    myRight = dim.width - RIGHT_MARGIN;

    // Update frame length.
    long now = System.nanoTime();
    myFrameLength = (now - myLastRenderTime) / 1000000000.0f;
    myLastRenderTime = now;

    synchronized (myData) {
      // Calculate begin and end times in seconds.
      myEndTime = myData.getEndTime() - myBufferTime;
      myBeginTime = myEndTime - (myRight - LEFT_MARGIN) / X_SCALE;

      // Animate the current maximum towards the real one.
      if (myData.getMaxTotal() > myCurrentMax) {
        myCurrentMax = lerp(myCurrentMax, myData.getMaxTotal(), myFirstFrame ? 1.f : .95f);
      }
      myYScale = (myBottom - TOP_MARGIN) / myCurrentMax;

      g2d.setClip(LEFT_MARGIN, TOP_MARGIN, myRight - LEFT_MARGIN, myBottom - TOP_MARGIN);

      drawTimelineData(g2d);
      drawEvents(g2d);

      g2d.setClip(0, 0, dim.width, dim.height);

      drawLabels(g2d);
      drawTimeMarkers(g2d);
      drawMarkers(g2d);
      drawGuides(g2d);
      if (myDrawDebugInfo) {
        drawDebugInfo(g2d);
      }

      g2d.dispose();
    }

    myFirstFrame = false;
  }

  private void drawDebugInfo(Graphics2D g2d) {
    int size = myData.size();
    int drawn = 0;
    g2d.setFont(TIMELINE_FONT.deriveFont(5.0f));
    for (int i = 0; i < size; ++i) {
      TimelineData.Sample sample = myData.get(i);
      if (sample.time > myBeginTime && sample.time < myEndTime) {
        float v = 0.0f;
        for (float f : sample.values) {
          v += f;
          int x = (int)timeToX(sample.time);
          int y = (int)valueToY(v);
          Color c = new Color((17 * sample.type) % 255, (121 * sample.type) % 255, (71 * sample.type) % 255);
          g2d.setColor(c);
          g2d.drawLine(x, y - 2, x, y + 2);
          g2d.drawLine(x - 2, y, x + 2, y);
          g2d.setColor(TEXT_COLOR);
          if (sample.id > 0) {
            g2d.drawString(String.format("[%d]", sample.id), x - 3, y - 5);
          }
        }
        drawn++;
      }
    }

    g2d.setFont(TIMELINE_FONT);
    g2d.drawString(String.format("FPS: %.2f", (1.0f / myFrameLength)), myRight + 20, myBottom - 40);
    g2d.drawString(String.format("Total samples: %d", size), myRight + 20, myBottom - 30);
    g2d.drawString(String.format("Drawn samples: %d", drawn), myRight + 20, myBottom - 20);
    g2d.drawString(String.format("Render time: %.2fms", (System.nanoTime() - myLastRenderTime) / 1000000.f), myRight + 20, myBottom - 10);
  }

  @Override
  public void actionPerformed(ActionEvent actionEvent) {
    repaint();
  }

  @Override
  public void hierarchyChanged(HierarchyEvent hierarchyEvent) {
    if (myTimer.isRunning() && !isShowing()) {
      myTimer.stop();
    }
    else if (!myTimer.isRunning() && isShowing()) {
      myTimer.start();
    }
  }

  private void drawTimelineData(Graphics2D g2d) {
    if (myData.size() > 1) {
      setPaths(0, myData.size());
      for (int i = myPaths.length - 1; i >= 0; i--) {
        g2d.setColor(myStreamColors[i]);
        g2d.fill(myPaths[i]);
      }
    }
  }

  private void drawEvents(Graphics2D g2d) {
    int size = myData.size();
    AffineTransform tx = g2d.getTransform();
    Stroke stroke = g2d.getStroke();
    Event currentEvent = null;
    int start = 0;
    float startX = 0;
    float startY = 0;

    for (int i = 0; i < size + 1; ++i) {
      TimelineData.Sample sample = i < size ? myData.get(i) : null;
      Event event = sample == null ? null : myEvents.get(sample.type);
      // A new event or the end of the current one.
      if (sample == null || event != null || (currentEvent != null && currentEvent.typeTo == sample.type)) {
        // If there was an event in progress, end it.
        if (currentEvent != null) {
          setPaths(start, i < size ? i + 1 : size);
          g2d.setColor(currentEvent.color);
          g2d.fill(myPaths[0]);

          AffineTransform dt = new AffineTransform(tx);
          dt.translate(startX, startY);
          g2d.setTransform(dt);
          Icon icon = currentEvent.icon;
          icon.paintIcon(this, g2d, 0, -icon.getIconHeight());

          g2d.setColor(currentEvent.progress);

          g2d.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          g2d.drawLine(0, 0, 0, (int)(myBottom - startY));
          g2d.drawLine(0, 0, icon.getIconWidth(), 0);
          if (sample == null) {
            drawInProgressMarker(g2d, icon.getIconWidth() + 6, -icon.getIconHeight(), 6);
          }
          g2d.setTransform(tx);
        }

        if (sample != null) {
          float x = timeToX(sample.time);
          float y = valueToY(sample.values[0]);

          currentEvent = event;
          if (currentEvent != null) {
            start = i;
            startX = x;
            startY = y;
          }
        }
      }
    }
    g2d.setStroke(stroke);
  }

  private void drawInProgressMarker(Graphics2D g2d, int x, int y, int size) {
    float dir = myEventProgress < 0.f ? -1.f : 1.f;

    float startAngle = (System.currentTimeMillis() / 8) % 360;
    float endAngle = 360 * myEventProgress * dir;

    // Invert the animation if we move in the opposite direction.
    if (dir < 0.0f) {
      startAngle += endAngle;
      endAngle = 360 - endAngle;
    }

    g2d.drawArc(x - size / 2, y - size / 2, size, size, (int)startAngle, (int)endAngle);
    myEventProgress = myEventProgress * dir > 0.95f ? 0.01f * -dir : lerp(myEventProgress, dir, .9f);

  }

  private float valueToY(float val) {
    return myBottom - val * myYScale;
  }

  private float timeToX(float time) {
    return LEFT_MARGIN + (time - myBeginTime) * X_SCALE;
  }

  private void drawLabels(Graphics2D g2d) {
    if (!myData.isEmpty()) {
      TimelineData.Sample value = myData.get(myData.size() - 1);
      g2d.setFont(TIMELINE_FONT);
      FontMetrics metrics = g2d.getFontMetrics();
      for (int i = 0; i < myData.getStreamCount(); i++) {
        g2d.setColor(myStreamColors[i]);
        int y = TOP_MARGIN + 15 + (myData.getStreamCount() - i - 1) * 20;
        g2d.fillRect(myRight + 20, y, 15, 15);
        g2d.setColor(TEXT_COLOR);
        g2d.drawString(String.format("%s [%.2f %s]", myStreamNames[i], value.values[i], myUnits), myRight + 40,
                       y + 7 + metrics.getAscent() * .5f);
      }
    }
  }

  private void drawTimeMarkers(Graphics2D g2d) {
    g2d.setFont(TIMELINE_FONT);
    g2d.setColor(TEXT_COLOR);
    FontMetrics metrics = g2d.getFontMetrics();
    float offset = metrics.stringWidth("000") * 0.5f;
    Path2D.Float lines = new Path2D.Float();
    for (int sec = Math.max((int)Math.ceil(myBeginTime), 0); sec < myEndTime; sec++) {
      float x = timeToX(sec);
      boolean big = sec % 5 == 0;
      if (big) {
        String text = formatTime(sec);
        g2d.drawString(text, x - metrics.stringWidth(text) + offset, myBottom + metrics.getAscent() + 5);
      }
      lines.moveTo(x, myBottom);
      lines.lineTo(x, myBottom + (big ? 5 : 2));
    }
    g2d.draw(lines);
  }

  @VisibleForTesting
  static String formatTime(int seconds) {
    int[] factors = {60, seconds};
    String[] suffix = {"m", "h"};
    String ret = seconds % 60 + "s";
    int t = seconds / 60;
    for (int i = 0; i < suffix.length && t > 0; i++) {
      ret = t % factors[i] + suffix[i] + " " + ret;
      t /= factors[i];
    }
    return ret;
  }

  private void drawMarkers(Graphics2D g2d) {
    if (myYScale <= 0) {
      return;
    }

    // Animate the fade in/out of markers.
    g2d.setFont(TIMELINE_FONT);
    FontMetrics metrics = g2d.getFontMetrics();
    int ascent = metrics.getAscent();
    float distance = myMarkerSeparation * myYScale;
    float evenMarkersTarget = 1.0f;
    if (distance < ascent * 2) { // Too many markers
      if (myEvenMarkersAlpha < 0.1f) {
        myMarkerSeparation *= 2;
        myEvenMarkersAlpha = 1.0f;
      }
      else {
        evenMarkersTarget = 0.0f;
      }
    }
    else if (distance > ascent * 5) { // Not enough
      if (myEvenMarkersAlpha > 0.9f) {
        myMarkerSeparation /= 2;
        myEvenMarkersAlpha = 0.0f;
      }
    }
    myEvenMarkersAlpha = lerp(myEvenMarkersAlpha, evenMarkersTarget, 0.999f);

    int markers = (int)(myCurrentMax / myMarkerSeparation);
    float markerPosition = LEFT_MARGIN - 10;
    for (int i = 0; i < markers + 1; i++) {
      float markerValue = (i + 1) * myMarkerSeparation;
      int y = (int)valueToY(markerValue);
      // Too close to the top
      if (myCurrentMax - markerValue < myMarkerSeparation * 0.5f) {
        markerValue = myCurrentMax;
        //noinspection AssignmentToForLoopParameter
        i = markers;
        y = TOP_MARGIN;
      }
      if (i < markers && i % 2 == 0 && myEvenMarkersAlpha < 1.0f) {
        g2d.setColor(new Color(TEXT_COLOR.getColorSpace(), TEXT_COLOR.getColorComponents(null), myEvenMarkersAlpha));
      }
      else {
        g2d.setColor(TEXT_COLOR);
      }
      g2d.drawLine(LEFT_MARGIN - 2, y, LEFT_MARGIN, y);

      String marker = String.format("%.2f %s", markerValue, myUnits);
      g2d.drawString(marker, markerPosition - metrics.stringWidth(marker), y + ascent * 0.5f);
    }
  }

  private void drawGuides(Graphics2D g2d) {
    g2d.setColor(TEXT_COLOR);
    g2d.drawLine(LEFT_MARGIN - 10, myBottom, myRight + 10, myBottom);
    if (myYScale > 0) {
      g2d.drawLine(LEFT_MARGIN, myBottom, LEFT_MARGIN, TOP_MARGIN);
      g2d.drawLine(myRight, myBottom, myRight, TOP_MARGIN);
    }
  }

  private void setPaths(int from, int to) {
    for (Path2D.Float path : myPaths) {
      path.reset();
    }
    if (to - from > 1) {
      // Optimize to not render too many samples even though they get clipped.
      while (from < to - 1 && myData.get(from + 1).time < myBeginTime) {
        from++;
      }
      TimelineData.Sample sample = myData.get(from);
      for (Path2D.Float path : myPaths) {
        path.moveTo(timeToX(sample.time), valueToY(0.0f));
      }
      for (int i = from; i < to; i++) {
        sample = myData.get(i);
        float val = 0.0f;
        for (int j = 0; j < sample.values.length; j++) {
          val += sample.values[j];
          myPaths[j].lineTo(timeToX(sample.time), valueToY(val));
        }
        // Stop rendering if we are over the end limit.
        if (sample.time > myEndTime) {
          break;
        }
      }
      for (Path2D.Float path : myPaths) {
        path.lineTo(timeToX(sample.time), valueToY(0.0f));
      }
    }
  }

  private static class Event {
    public final int typeFrom;
    public final int typeTo;
    public final int stream;
    public final Icon icon;
    public final Color color;
    public final Color progress;

    private Event(int typeFrom, int typeTo, int stream, Icon icon, Color color, Color progress) {
      this.typeFrom = typeFrom;
      this.typeTo = typeTo;
      this.stream = stream;
      this.icon = icon;
      this.color = color;
      this.progress = progress;
    }
  }
}
