/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.rendering.multi;

import com.android.annotations.Nullable;
import com.intellij.util.ui.UIUtil;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * This class implements 2D bin packing: packing rectangles into a given area as
 * tightly as "possible" (bin packing in general is NP hard, so this class uses
 * heuristics).
 * <p/>
 * The algorithm implemented is to keep a set of (possibly overlapping)
 * available areas for placement. For each newly inserted rectangle, we first
 * pick which available space to occupy, and we then subdivide the
 * current rectangle into all the possible remaining unoccupied sub-rectangles.
 * We also remove any other space rectangles which are no longer eligible if
 * they are intersecting the newly placed rectangle.
 * <p/>
 * This algorithm is not very fast, so should not be used for a large number of
 * rectangles.
 */
@SuppressWarnings("AssignmentToForLoopParameter")
class BinPacker {
  /**
   * When enabled, the successive passes are dumped as PNG images showing the
   * various available and occupied rectangles)
   */
  static final boolean DEBUG = false;

  /** Whether new rectangles should be placed on the left side of available rectangles */
  private static final boolean PLACE_LEFT = true;

  /** Whether new rectangles should be placed on the top rather than the bottom */
  private static final boolean PLACE_TOP = true;

  private final List<Rectangle> mySpace = new ArrayList<Rectangle>();
  private final int myMinHeight;
  private final int myMinWidth;

  /**
   * Creates a new {@linkplain BinPacker}. To use it, first add one or more
   * initial available space rectangles with {@link #addSpace(Rectangle)}, and then
   * place the rectangles with {@link #occupy(int, int)}. The returned
   * {@link Rectangle} from {@link #occupy(int, int)} gives the coordinates of the
   * positioned rectangle.
   *
   * @param minWidth  the smallest width of any rectangle placed into this bin
   * @param minHeight the smallest height of any rectangle placed into this bin
   */
  BinPacker(int minWidth, int minHeight) {
    myMinWidth = minWidth;
    myMinHeight = minHeight;

    if (DEBUG) {
      myAllocated = new ArrayList<Rectangle>();
    }
  }

  /**
   * Adds more available space
   */
  void addSpace(Rectangle rect) {
    if (rect.width >= myMinWidth && rect.height >= myMinHeight) {
      mySpace.add(rect);
    }
  }

  /**
   * Attempts to place a rectangle of the given dimensions, if possible
   */
  @Nullable
  Rectangle occupy(int width, int height) {
    int index = findBest(width, height);
    if (index == -1) {
      return null;
    }

    return split(index, width, height);
  }

  /**
   * Finds the best available space rectangle to position a new rectangle of
   * the given size in.
   */
  private int findBest(int width, int height) {
    if (mySpace.isEmpty()) {
      return -1;
    }

    // Try to pack as far up as possible first
    int bestIndex = -1;
    boolean multipleAtSameY = false;
    int minY = Integer.MAX_VALUE;
    for (int i = 0, n = mySpace.size(); i < n; i++) {
      Rectangle rect = mySpace.get(i);
      if (rect.y <= minY) {
        if (rect.width >= width && rect.height >= height) {
          if (rect.y < minY) {
            minY = rect.y;
            multipleAtSameY = false;
            bestIndex = i;
          }
          else if (minY == rect.y) {
            multipleAtSameY = true;
          }
        }
      }
    }

    if (!multipleAtSameY) {
      return bestIndex;
    }

    bestIndex = -1;

    // Pick a rectangle. This currently tries to find the rectangle whose shortest
    // side most closely matches the placed rectangle's size.
    // Attempt to find the best short side fit
    int bestShortDistance = Integer.MAX_VALUE;
    int bestLongDistance = Integer.MAX_VALUE;

    for (int i = 0, n = mySpace.size(); i < n; i++) {
      Rectangle rect = mySpace.get(i);
      if (rect.y != minY) { // Only comparing elements at same y
        continue;
      }
      if (rect.width >= width && rect.height >= height) {
        if (width < height) {
          int distance = rect.width - width;
          if (distance < bestShortDistance || distance == bestShortDistance && (rect.height - height) < bestLongDistance) {
            bestShortDistance = distance;
            bestLongDistance = rect.height - height;
            bestIndex = i;
          }
        }
        else {
          int distance = rect.width - width;
          if (distance < bestShortDistance || distance == bestShortDistance && (rect.height - height) < bestLongDistance) {
            bestShortDistance = distance;
            bestLongDistance = rect.height - height;
            bestIndex = i;
          }
        }
      }
    }

    return bestIndex;
  }

  /**
   * Removes the rectangle at the given index. Since the rectangles are in an
   * {@link ArrayList}, removing a rectangle in the normal way is slow (it
   * would involve shifting all elements), but since we don't care about
   * order, this always swaps the to-be-deleted element to the last position
   * in the array first, <b>then</b> it deletes it (which should be
   * immediate).
   *
   * @param index the index in the {@link #mySpace} list to remove a rectangle
   *              from
   */
  private void removeRect(int index) {
    assert !mySpace.isEmpty();
    int lastIndex = mySpace.size() - 1;
    if (index != lastIndex) {
      // Swap before remove to make deletion faster since we don't
      // care about order
      Rectangle temp = mySpace.get(index);
      mySpace.set(index, mySpace.get(lastIndex));
      mySpace.set(lastIndex, temp);
    }

    mySpace.remove(lastIndex);
  }

  /**
   * Splits the rectangle at the given rectangle index such that it can contain
   * a rectangle of the given width and height.
   */
  private Rectangle split(int index, int width, int height) {
    Rectangle rect = mySpace.get(index);
    assert rect.width >= width && rect.height >= height : rect;

    Rectangle r = new Rectangle();
    if (PLACE_LEFT) {
      r.x = rect.x;
    } else {
      r.x = rect.x + rect.width - width;
    }
    if (PLACE_TOP) {
      r.y = rect.y;
    } else {
      r.y = rect.y + rect.height - height;
    }
    r.width = width;
    r.height = height;

    // Remove all rectangles that intersect my rectangle
    for (int i = 0; i < mySpace.size(); i++) {
      Rectangle other = mySpace.get(i);
      if (other.intersects(r)) {
        removeRect(i);
        i--;
      }
    }


    int remainingHeight = rect.height - height;
    int remainingWidth = rect.width - width;
    if (PLACE_LEFT) {
      if (remainingHeight >= myMinHeight) {
        if (PLACE_TOP) {
          // Split along vertical line x = rect.x + width:
          // (rect.x,rect.y)
          //     +-------------+-------------------------+
          //     |             |                         |
          //     |             |                         |
          //     |             | height                  |
          //     |             |                         |
          //     |             |                         |
          //     +-------------+           B             | rect.h
          //     |   width                               |
          //     |             |                         |
          //     |      A                                |
          //     |             |                         |
          //     |                                       |
          //     +---------------------------------------+
          //                    rect.w
          mySpace.add(new Rectangle(rect.x, rect.y + height, width, remainingHeight)); // Area A
        } else {
          assert PLACE_LEFT;
          assert !PLACE_TOP;
          // Split along vertical line x = rect.x + width:
          // (rect.x,rect.y)
          //     +-------------+-------------------------+
          //     |                                       |
          //     |             |                         |
          //     |      A                                |
          //     |             |                         |
          //     |    width                              |
          //     +-------------+           B             | rect.h
          //     |             |                         |
          //     |             |                         |
          //     |             | height                  |
          //     |             |                         |
          //     |             |                         |
          //     +-------------+-------------------------+
          //                    rect.w
          mySpace.add(new Rectangle(rect.x, rect.y, width, remainingHeight)); // Area A
        }
      }
      if (remainingWidth >= myMinWidth) { // Area B
        mySpace.add(new Rectangle(rect.x + width, rect.y, remainingWidth, rect.height));
      }
    }
    else {
      assert !PLACE_LEFT;
      // Split along vertical line x = rect.x + rect.w - width
      // (rect.x,rect.y)
      //     +---------------------------------------+
      //     |                                       |
      //     |                        |              |
      //     |                               A       |
      //     |                        |              |
      //     |                            width      |
      //     |          B             +--------------| rect.h
      //     |                        |              |
      //     |                        |              |
      //     |                 height |              |
      //     |                        |              |
      //     |                        |              |
      //     +------------------------+--------------+
      //                    rect.w
      if (remainingWidth >= myMinWidth) { // Area B
        mySpace.add(new Rectangle(rect.x, rect.y, remainingWidth, rect.height));
      }
      if (remainingHeight >= myMinHeight) { // Area A
        if (PLACE_TOP) {
          mySpace.add(new Rectangle(rect.x + rect.width - width, rect.y + height, width, remainingHeight));
        } else {
          mySpace.add(new Rectangle(rect.x + rect.width - width, rect.y, width, remainingHeight));
        }
      }
    }

    if (PLACE_LEFT) {
      if (PLACE_TOP) {
        // Split along horizontal line y = rect.y + height:
        //     +-------------+-------------------------+
        //     |             |                         |
        //     |             | height                  |
        //     |             |          A              |
        //     |             |                         |
        //     |             |                         | rect.h
        //     +-------------+ - - - - - - - - - - - - |
        //     |  width                                |
        //     |                                       |
        //     |                B                      |
        //     |                                       |
        //     |                                       |
        //     +---------------------------------------+
        //                   rect.w
        if (remainingHeight >= myMinHeight) { // Area B
          mySpace.add(new Rectangle(rect.x, rect.y + height, rect.width, remainingHeight));
        }
        if (remainingWidth >= myMinWidth) { // Area A
          mySpace.add(new Rectangle(rect.x + width, rect.y, remainingWidth, height));
        }
      } else {
        assert PLACE_LEFT;
        assert !PLACE_TOP;
        //     +---------------------------------------+
        //     |                                       |
        //     |                                       |
        //     |                   B                   |
        //     |                                       |
        //     |                                       | rect.h
        //     +-------------+ - - - - - - - - - - - - |
        //     |  width      |                         |
        //     |             |                         |
        //     |             | height    A             |
        //     |             |                         |
        //     |             |                         |
        //     +-------------+-------------------------+
        //                   rect.w
        if (remainingHeight >= myMinHeight) { // Area B
          mySpace.add(new Rectangle(rect.x, rect.y, rect.width, remainingHeight));
        }
        if (remainingWidth >= myMinWidth) { // Area A
          mySpace.add(new Rectangle(rect.x + width, rect.y + rect.height - height, remainingWidth, height));
        }
      }
    }
    else {
      assert !PLACE_LEFT;
      if (PLACE_TOP) {
        // Split along horizontal line y = rect.y + rect.h - height:
        //     +-------------------------+-------------+
        //     |                         |             |
        //     |                         |             |
        //     |          A       height |             |
        //     |                         |             |
        //     |                         |             | rect.h
        //     | - - - - - - - - - - - - +-------------|
        //     |                              width    |
        //     |                                       |
        //     |                    B                  |
        //     |                                       |
        //     |                                       |
        //     +---------------------------------------+
        //                   rect.w

        if (remainingHeight >= myMinHeight) { // Area B
          mySpace.add(new Rectangle(rect.x, rect.y + height, rect.width, remainingHeight));
        }
        if (remainingWidth >= myMinWidth) { // Area A
          mySpace.add(new Rectangle(rect.x, rect.y, remainingWidth, height));
        }
      } else {
        assert !PLACE_LEFT;
        assert !PLACE_TOP;
        // Split along horizontal line y = rect.y + rect.h - height:
        //     +---------------------------------------+
        //     |                                       |
        //     |                                       |
        //     |                 B                     |
        //     |                                       |
        //     |                              width    | rect.h
        //     | - - - - - - - - - - - - +-------------|
        //     |                         |             |
        //     |                         |             |
        //     |          A       height |             |
        //     |                         |             |
        //     |                         |             |
        //     +-------------------------+-------------+
        //                   rect.w

        if (remainingHeight >= myMinHeight) { // Area B
          mySpace.add(new Rectangle(rect.x, rect.y, rect.width, remainingHeight));
        }
        if (remainingWidth >= myMinWidth) { // Area A
          mySpace.add(new Rectangle(rect.x, rect.y + rect.height - height, remainingWidth, height));
        }
      }
    }

    if (DEBUG) {
      myAllocated.add(r);
    }

    if (DEBUG) {
      dumpDiagnostics();
    }


    // Remove redundant rectangles. This is not very efficient.
    for (int i = 0; i < mySpace.size() - 1; i++) {
      for (int j = i + 1; j < mySpace.size(); j++) {
        Rectangle iRect = mySpace.get(i);
        Rectangle jRect = mySpace.get(j);
        if (jRect.contains(iRect)) {
          removeRect(i);
          i--;
          break;
        }
        if (iRect.contains(jRect)) {
          removeRect(j);
          j--;
        }
      }
    }

    if (DEBUG) {
      dumpDiagnostics();
    }

    return r;
  }

  // DEBUGGING CODE: Enable with DEBUG

  private List<Rectangle> myAllocated;
  private int myIteration;

  @SuppressWarnings("EmptyCatchBlock")
  void dumpDiagnostics() {
    if (DEBUG) {
      myIteration++;
      // Determine size
      Rectangle r = new Rectangle(0, 0, 100, 100);
      for (Rectangle rect : mySpace) {
        r.add(rect);
      }

      BufferedImage image = UIUtil.createImage(r.width, r.height, BufferedImage.TYPE_INT_ARGB);
      Graphics g = image.getGraphics();
      // Fill background
      g.setColor(Color.BLACK);
      g.fillRect(0, 0, r.width, r.height);

      paintDiagnostics((Graphics2D)g);
      g.dispose();

      try {
        File file = new File("/tmp/layout-iteration-" + myIteration + ".png");
        ImageIO.write(image, "PNG", file);
      } catch (Exception e) {
      }
    }
  }

  @SuppressWarnings({"AssignmentToStaticFieldFromInstanceMethod", "CallToPrintStackTrace", "UseOfSystemOutOrSystemErr"})
  void paintDiagnostics(Graphics2D g) {
    if (DEBUG) {
      Composite prevComposite = g.getComposite();
      g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));

      Color[] colors =
        new Color[]{Color.blue, Color.cyan, Color.green, Color.magenta, Color.orange, Color.pink, Color.red, Color.white, Color.yellow,
          Color.darkGray, Color.lightGray, Color.gray,};

      if (myAllocated != null && !myAllocated.isEmpty()) {
        char allocated = 'A';
        for (Rectangle rect : myAllocated) {
          Color color = new Color(0x9FFFFFFF, true);
          g.setColor(color);
          g.setBackground(color);
          g.fillRect(rect.x, rect.y, rect.width, rect.height);
          g.setColor(Color.WHITE);
          g.drawRect(rect.x, rect.y, rect.width, rect.height);
          g.drawString(String.valueOf(allocated++), rect.x + rect.width / 2, rect.y + rect.height / 2);
        }
      }

      int colorIndex = 0;
      for (Rectangle rect : mySpace) {
        Color color = colors[colorIndex];
        colorIndex = (colorIndex + 1) % colors.length;

        color = new Color(color.getRed(), color.getGreen(), color.getBlue(), 128);
        g.setColor(color);

        g.fillRect(rect.x, rect.y, rect.width, rect.height);
        g.setColor(Color.WHITE);
        g.drawString(Integer.toString(colorIndex), rect.x + rect.width / 2, rect.y + rect.height / 2);
      }

      g.setComposite(prevComposite);
    }
  }
}