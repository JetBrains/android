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
package com.android.tools.idea.memory;

import com.intellij.ui.Gray;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Path2D;

/**
 * A component to display a TimelineData object. It locks the timeline object to prevent modifications to it while it's begin
 * rendered, but objects of this class should not be accessed from different threads.
 */
@SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
class TimelineComponent extends JComponent implements ActionListener {

  private static final Color TEXT_COLOR = Gray._128;
  private static final Font TIMELINE_FONT = new Font("Sans", Font.PLAIN, 10);
  private static final Font TITLE_FONT = TIMELINE_FONT.deriveFont(Font.BOLD, 12);

  private static final int LEFT_MARGIN = 120;
  private static final int RIGHT_MARGIN = 200;
  private static final int TOP_MARGIN = 40;
  private static final int BOTTOM_MARGIN = 30;
  private static final int FPS = 40;

  /**
   * The number of pixels a second in the timeline takes on the screen.
   */
  private static final float X_SCALE = 20;

  private final float myBufferTime;
  @NotNull
  private final TimelineData myData;
  private final float myInitialMax;
  private final float myInitialMarkerSeparation;
  private final Timer myTimer;
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
    myPaths = new Path2D.Float[myData.getStreamCount()];
    myTimer = new Timer(0, this);
    myTimer.setRepeats(false);
    for (int i = 0; i < myPaths.length; i++) {
      myPaths[i] = new Path2D.Float();
    }
    reset();
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

  public void setDrawDebugInfo(boolean drawDebugInfo) {
    myDrawDebugInfo = drawDebugInfo;
  }

  public boolean isDrawDebugInfo() {
    return myDrawDebugInfo;
  }

  @Override
  public void paintComponent(Graphics g) {
    Graphics2D g2d = (Graphics2D)g;
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    g2d.setFont(TIMELINE_FONT);

    Dimension dim = getSize();
    myBottom = dim.height - BOTTOM_MARGIN;
    myRight = dim.width - RIGHT_MARGIN;

    // Update frame length.
    long now = System.nanoTime();
    myFrameLength = (now - myLastRenderTime) / 1000000000.0f;
    myLastRenderTime = now;

    synchronized (myData) {
      // Calculate begin and end times in seconds.
      myEndTime = (now / 1000000 - myData.getStartTime()) / 1000.0f - myBufferTime;
      myBeginTime = myEndTime - (myRight - LEFT_MARGIN) / X_SCALE;

      // Animate the current maximum towards the real one.
      if (myData.getMaxTotal() > myCurrentMax) {
        myCurrentMax = lerp(myCurrentMax, myData.getMaxTotal(), myFirstFrame ? 1.f : .95f);
      }
      myYScale = (myBottom - TOP_MARGIN) / myCurrentMax;

      drawTimelineData(g2d);
      drawTimeMarkers(g2d);
      drawMarkers(g2d);
      drawGuides(g2d);
      drawTitle(g2d);
      if (myDrawDebugInfo) {
        drawDebugInfo(g2d);
      }
    }

    myFirstFrame = false;

    int delay = Math.max((int)((1000 / FPS) - (System.nanoTime() - now) / 1000000), 0);
    myTimer.setInitialDelay(delay);
    myTimer.restart();
  }

  private void drawDebugInfo(Graphics2D g2d) {
    g2d.setFont(TIMELINE_FONT);

    int size = myData.size();
    int drawn = 0;
    for (int i = 0; i < size; ++i) {
      TimelineData.Sample sample = myData.get(i);
      if (sample.time > myBeginTime && sample.time < myEndTime) {
        float v = 0.0f;
        for (float f : sample.values) {
          v += f;
          int x = (int)timeToX(sample.time);
          int y = (int)valueToY(v);
          g2d.drawLine(x, y - 2, x, y + 2);
          g2d.drawLine(x - 2, y, x + 2, y);
        }
        drawn++;
      }
    }

    g2d.drawString(String.format("FPS: %.2f",(1.0f / myFrameLength)), myRight + 20, myBottom - 40);
    g2d.drawString(String.format("Total samples: %d", size), myRight + 20, myBottom - 30);
    g2d.drawString(String.format("Drawn samples: %d", drawn), myRight + 20, myBottom - 20);
    g2d.drawString(String.format("Render time: %.2fms", (System.nanoTime() - myLastRenderTime) / 1000000.f), myRight + 20, myBottom - 10);
  }

  @Override
  public void actionPerformed(ActionEvent actionEvent) {
    repaint();
  }

  private void drawTimelineData(Graphics2D g2d) {
    int size = myData.size();
    if (size > 1) {
      for (Path2D.Float myPath : myPaths) {
        myPath.reset();
      }

      TimelineData.Sample prev = myData.get(0);
      boolean done = false;
      boolean hasData = false;
      for (int i = 0; !done && i < size; i++) {
        TimelineData.Sample sample = myData.get(i);
        if (sample.time > myEndTime) {
          prev = prev.interpolate(sample, myEndTime);
          done = true;
        }
        else {
          prev = sample;
        }
        if (prev.time < myBeginTime && i < size - 1) {
          TimelineData.Sample next = myData.get(i + 1);
          if (next.time >= myBeginTime) {
            prev = prev.interpolate(next, myBeginTime);
          }
        }
        if (prev.time >= myBeginTime) {
          if (!hasData) {
            for (Path2D.Float shape : myPaths) {
              shape.moveTo(timeToX(prev.time), valueToY(0.0f));
            }
            hasData = true;
          }
          float val = 0.0f;
          for (int j = 0; j < prev.values.length; j++) {
            val += prev.values[j];
            myPaths[j].lineTo(timeToX(prev.time), valueToY(val));
          }
        }
      }
      if (hasData) {
        for (int j = myPaths.length - 1; j >= 0; j--) {
          myPaths[j].lineTo(timeToX(prev.time), valueToY(0.0f));
          g2d.setColor(myData.getStream(j).color);
          g2d.fill(myPaths[j]);
        }
        drawLabels(g2d, prev);
      }
    }
  }

  private float valueToY(float val) {
    return myBottom - val * myYScale;
  }

  private float timeToX(float time) {
    return LEFT_MARGIN + (time - myBeginTime) * X_SCALE;
  }

  private void drawLabels(Graphics2D g2d, TimelineData.Sample value) {
    g2d.setFont(TIMELINE_FONT);
    FontMetrics metrics = g2d.getFontMetrics();
    for (int i = 0; i < myData.getStreamCount(); i++) {
      TimelineData.Stream stream = myData.getStream(i);
      g2d.setColor(stream.color);
      int y = TOP_MARGIN + 15 + (myData.getStreamCount() - i - 1) * 20;
      g2d.fillRect(myRight + 20, y, 15, 15);
      g2d.setColor(TEXT_COLOR);
      g2d.drawString(String.format("%s [%.2f %s]", stream.name, value.values[i], myData.getUnit()), myRight + 40,
                     y + 7 + metrics.getAscent() * .5f);
    }
  }

  private void drawTimeMarkers(Graphics2D g2d) {
    g2d.setFont(TIMELINE_FONT);
    g2d.setColor(TEXT_COLOR);
    FontMetrics metrics = g2d.getFontMetrics();
    float offset = metrics.charWidth('0') * 0.5f;
    Path2D.Float lines = new Path2D.Float();
    for (int sec = Math.max((int)Math.ceil(myBeginTime), 0); sec < myEndTime; sec++) {
      float x = timeToX(sec);
      boolean big = sec % 5 == 0;
      if (big) {
        g2d.drawString(sec + "s", x - offset, myBottom + metrics.getAscent() + 5);
      }
      lines.moveTo(x, myBottom);
      lines.lineTo(x, myBottom + (big ? 5 : 2));
    }
    g2d.draw(lines);
  }

  private void drawMarkers(Graphics2D g2d) {
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
        //noinspection UseJBColor
        g2d.setColor(new Color(TEXT_COLOR.getColorSpace(), TEXT_COLOR.getColorComponents(null), myEvenMarkersAlpha));
      }
      else {
        g2d.setColor(TEXT_COLOR);
      }
      g2d.drawLine(LEFT_MARGIN - 2, y, LEFT_MARGIN, y);

      String marker = String.format("%.2f %s", markerValue, myData.getUnit());
      g2d.drawString(marker, markerPosition - metrics.stringWidth(marker), y + ascent * 0.5f);
    }
  }

  private void drawGuides(Graphics2D g2d) {
    g2d.setColor(TEXT_COLOR);
    g2d.drawLine(LEFT_MARGIN - 10, myBottom, myRight + 10, myBottom);
    g2d.drawLine(LEFT_MARGIN, myBottom, LEFT_MARGIN, TOP_MARGIN);
    g2d.drawLine(myRight, myBottom, myRight, TOP_MARGIN);
  }

  private void drawTitle(Graphics2D g2d) {
    if (myData.getTitle() != null) {
      g2d.setFont(TITLE_FONT);
      g2d.drawString(myData.getTitle(), LEFT_MARGIN, TOP_MARGIN - g2d.getFontMetrics().getAscent());
    }
  }
}
