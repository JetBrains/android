/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.common.scene.draw;

import java.awt.*;
import java.awt.geom.FlatteningPathIterator;
import java.awt.geom.GeneralPath;
import java.awt.geom.PathIterator;

/**
 * This class can be used when you want to draw a stroke that is zig zag
 */
public class FancyStroke implements Stroke {
  private static final float FLATNESS = .1f;
  private final float mSize, mSpacing;
  private final static float CHAIN_RATIO = 0.6f;
  float[] mPoint = new float[6];
  Stroke myBasicStroke;

  public enum Type {
    SINE, SPRING, ROPE, CHAIN, HALF_CHAIN1, HALF_CHAIN2
  }

  Type myType;

  /**
   * This creates a Spring Stroke. It can be used in Graphics2D setStroke();
   *
   * @param size    The width of the zig zag.
   * @param spacing The length of each zig zag
   * @param width   the width of the line
   */
  public FancyStroke(Type type, float size, int spacing, float width) {
    myType = type;
    mSize = size;
    mSpacing = spacing;
    myBasicStroke = new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL);
  }

  /**
   * This creates a Spring Stroke. It can be used in Graphics2D setStroke();
   *
   * @param size    The width of the zig zag.
   * @param spacing The length of each zig zag
   * @param stroke  The Stroke to draw the zig zag with
   */
  public FancyStroke(float size, int spacing, Stroke stroke) {
    mSize = size;
    mSpacing = spacing;
    myBasicStroke = stroke;
  }

  @Override
  public Shape createStrokedShape(Shape shape) {
    switch (myType) {
      case SPRING:
        return createSpring(shape);
      case SINE:
        return createSine(shape);
      case ROPE:
        return createRope(shape);
      case CHAIN:
        return createChain(shape, 2);
      case HALF_CHAIN1:
        return createChain(shape, 0);
      case HALF_CHAIN2:
        return createChain(shape, 1);
    }
    return shape;
  }

  public Shape createSpring(Shape shape) {
    GeneralPath result = new GeneralPath();
    PathIterator it = new FlatteningPathIterator(shape.getPathIterator(null), FLATNESS);
    float dist = 0;
    float x1 = 0, x2 = 0, y1 = 0, y2 = 0;
    float drawX, drawY;
    int sign = 1;
    result.reset();
    while (!it.isDone()) {
      int type = it.currentSegment(mPoint);
      switch (type) {
        case PathIterator.SEG_MOVETO:

          result.moveTo(x1 = mPoint[0], y1 = mPoint[1]);
          dist = 0;
          break;
        case PathIterator.SEG_CLOSE:
        case PathIterator.SEG_LINETO:
          x2 = x1;
          y2 = y1;
          x1 = mPoint[0];
          y1 = mPoint[1];
          float dx = (x1 - x2);
          float dy = (y1 - y2);
          float dv = (float)Math.hypot(dx, dy);
          float rem = dist;
          dist += dv;
          dx /= dv;
          dy /= dv;
          float px = x2;
          float py = y2;

          while (dist > mSpacing) {
            px += dx * (mSpacing - rem);
            py += dy * (mSpacing - rem);
            rem = 0;
            sign *= -1;
            float cx = px - dy * mSize * sign;
            float cy = py + dx * mSize * sign;
            result.lineTo(cx, cy);
            dist -= mSpacing;
          }
          if (type == PathIterator.SEG_CLOSE) {
            px += dx * dist;
            py += dy * dist;
            result.closePath();
          }
          break;
        case PathIterator.SEG_QUADTO:
        case PathIterator.SEG_CUBICTO:
        default:
      }
      it.next();
    }
    result.lineTo(x1, y1);
    return myBasicStroke.createStrokedShape(result);
  }

  public Shape createSine(Shape shape) {
    GeneralPath result = new GeneralPath();
    PathIterator it = new FlatteningPathIterator(shape.getPathIterator(null), FLATNESS);
    float dist = 0;
    float x1 = 0, x2, y1 = 0, y2;
    int sign = 1;
    result.reset();

    while (!it.isDone()) {
      int type = it.currentSegment(mPoint);
      switch (type) {
        case PathIterator.SEG_MOVETO:
          result.moveTo(x1 = mPoint[0], y1 = mPoint[1]);
          dist = 0;

          break;
        case PathIterator.SEG_CLOSE:
        case PathIterator.SEG_LINETO:
          x2 = x1;
          y2 = y1;
          x1 = mPoint[0];
          y1 = mPoint[1];
          float dx = (x1 - x2);
          float dy = (y1 - y2);
          float dv = (float)Math.hypot(dx, dy);
          float rem = dist;
          dist += dv;
          dx /= dv;
          dy /= dv;
          float px = x2;
          float py = y2;

          while (dist > mSpacing) {
            float lastX = px - dx * rem;
            float lastY = py - dy * rem;
            px += dx * (mSpacing - rem);
            py += dy * (mSpacing - rem);
            rem = 0;
            sign *= -1;
            float cx = px - dy * mSize * sign;
            float cy = py + dx * mSize * sign;
            float cx1 = lastX + dx * mSpacing / 2 + dy * mSize * sign;
            float cy1 = lastY + dy * mSpacing / 2 - dx * mSize * sign;
            result.curveTo(cx1, cy1, cx - dx * mSpacing / 2, cy - dy * mSpacing / 2, cx, cy);
            dist -= mSpacing;
          }
          if (type == PathIterator.SEG_CLOSE) {
            result.closePath();
          }
          break;
        case PathIterator.SEG_QUADTO:
        case PathIterator.SEG_CUBICTO:
        default:
      }
      it.next();
    }
    return myBasicStroke.createStrokedShape(result);
  }

  public Shape createRope(Shape shape) {
    GeneralPath result = new GeneralPath();
    result.reset();
    for (int flip = -1; flip < 2; flip += 2) {
      PathIterator it = new FlatteningPathIterator(shape.getPathIterator(null), FLATNESS);
      float dist = 0;
      float x1 = 0, x2, y1 = 0, y2;
      int sign = 1;

      while (!it.isDone()) {
        int type = it.currentSegment(mPoint);
        switch (type) {
          case PathIterator.SEG_MOVETO:
            result.moveTo(x1 = mPoint[0], y1 = mPoint[1]);
            dist = 0;

            break;
          case PathIterator.SEG_CLOSE:
          case PathIterator.SEG_LINETO:
            x2 = x1;
            y2 = y1;
            x1 = mPoint[0];
            y1 = mPoint[1];
            float dx = (x1 - x2);
            float dy = (y1 - y2);
            float dv = (float)Math.hypot(dx, dy);
            float rem = dist;
            dist += dv;
            dx /= dv;
            dy /= dv;
            float px = x2;
            float py = y2;

            while (dist > mSpacing) {
              float lastX = px - dx * rem;
              float lastY = py - dy * rem;
              px += dx * (mSpacing - rem);
              py += dy * (mSpacing - rem);
              rem = 0;
              sign *= -1;
              float cx = px - dy * mSize * sign * flip;
              float cy = py + dx * mSize * sign * flip;
              float cx1 = lastX + dx * mSpacing / 2 + dy * mSize * sign * flip;
              float cy1 = lastY + dy * mSpacing / 2 - dx * mSize * sign * flip;
              result.curveTo(cx1, cy1, cx - dx * mSpacing / 2, cy - dy * mSpacing / 2, cx, cy);
              dist -= mSpacing;
            }
            if (type == PathIterator.SEG_CLOSE) {
              result.closePath();
            }
            break;
          case PathIterator.SEG_QUADTO:
          case PathIterator.SEG_CUBICTO:
          default:
        }
        it.next();
      }
    }
    return myBasicStroke.createStrokedShape(result);
  }

  /**
   * You can create a whole or half (left or right) chain
   * @param shape
   * @param sides 0 = left half, 1 = right half, 2 = both sides
   * @return
   */
  public Shape createChain(Shape shape, int sides) {
    GeneralPath result = new GeneralPath();
    result.reset();
    int start = (sides == 1) ? 1 : -1;
    int end = (sides == 0) ? 1 : 2;
    for (int flip = start; flip < end; flip += 2) {
      PathIterator it = new FlatteningPathIterator(shape.getPathIterator(null), FLATNESS);
      float dist = 0;
      float x1 = 0, x2 = 0, y1 = 0, y2 = 0;
      float dx = 0;
      float dy = 0;
      float px = 0;
      float py = 0;
      float rem = 0;
      boolean link = true;

      while (!it.isDone()) {
        int type = it.currentSegment(mPoint);
        switch (type) {
          case PathIterator.SEG_MOVETO:
            result.moveTo(x1 = mPoint[0], y1 = mPoint[1]);
            dist = 0;
            break;
          case PathIterator.SEG_CLOSE:
          case PathIterator.SEG_LINETO:
            x2 = x1;
            y2 = y1;
            x1 = mPoint[0];
            y1 = mPoint[1];
            dx = (x1 - x2);
            dy = (y1 - y2);
            float dv = (float)Math.hypot(dx, dy);
            rem = dist;
            dist += dv;
            dx /= dv;
            dy /= dv;
            px = x2;
            py = y2;

            while (dist > mSpacing) {
              float lastX = px - dx * rem;
              float lastY = py - dy * rem;
              float space = (link) ? mSpacing : mSpacing * CHAIN_RATIO;
              px += dx * (space - rem);
              py += dy * (space - rem);
              rem = 0;
              link = !link;
              float sign = (link) ? 0.2f : 1f;
              float cx = px - dy * mSize * sign * flip;
              float cy = py + dx * mSize * sign * flip;
              float cx1 = lastX + -dy * mSize * sign * flip;
              float cy1 = lastY + dx * mSize * sign * flip;
              result.curveTo(cx1, cy1, cx, cy, px, py);
              dist -= space;
            }
            if (type == PathIterator.SEG_CLOSE) {
              float lastX = px - dx * rem;
              float lastY = py - dy * rem;
              float space = dist;
              px += dx * (space - rem);
              py += dy * (space - rem);
              rem = 0;
              link = !link;
              float sign = (link) ? 0.2f : 1f;
              float cx = px - dy * mSize * sign * flip;
              float cy = py + dx * mSize * sign * flip;
              float cx1 = lastX + -dy * mSize * sign * flip;
              float cy1 = lastY + dx * mSize * sign * flip;
              result.curveTo(cx1, cy1, cx, cy, px, py);
              dist -= space;
              result.closePath();
            }
            break;
          case PathIterator.SEG_QUADTO:
          case PathIterator.SEG_CUBICTO:
          default:
        }
        it.next();
      }
      if (dist > .5) { // if you have more than half a pixel left
        float lastX = px - dx * rem;
        float lastY = py - dy * rem;
        float space = (link) ? mSpacing : mSpacing * CHAIN_RATIO;
        px += dx * (space - rem);
        py += dy * (space - rem);
        link = !link;
        float sign = (link) ? 0.2f : 1f;
        float cx = px - dy * mSize * sign * flip;
        float cy = py + dx * mSize * sign * flip;
        float cx1 = lastX + -dy * mSize * sign * flip;
        float cy1 = lastY + dx * mSize * sign * flip;

        result.curveTo(cx1, cy1, cx, cy, x1, y1);
      }
    }
    return myBasicStroke.createStrokedShape(result);
  }
}
