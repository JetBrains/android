package com.android.tools.idea.uibuilder.scout;

import com.android.tools.idea.common.model.NlComponent;

import java.util.ArrayList;
import java.util.Random;

public class RectangleGenerator {
  public static final int MIN_WIDTH = 100;
  public static final int MIN_HEIGHT = 40;
  public static final int MIN_GAP = 40;
  public static final int MAX_TRIES = 100;

  /**
   * ScoutWidget object not tied to an NlComponent for testing purposes,
   * used when generating random layouts to test the Scout Inference Engine.
   */
  public class FakeWidget extends ScoutWidget {

    public FakeWidget(FakeWidget parent) {
      this.mParent = parent;
    }

    @Override
    public void setX(int x) {
      mX = x;
    }

    @Override
    public void setY(int y) {
      mY = y;
    }

    public void setBaseline(int baseline) {
      mBaseLine = baseline;
    }

    public int baseline() {
      return mBaseLine;
    }

    @Override
    public void setWidth(int width) {
      mWidth = width;
    }

    @Override
    public void setHeight(int height) {
      mHeight = height;
    }
  }

  ArrayList<ScoutWidget> recs;

  /**
   * Create a collection of rectangles
   *
   * @param count     the number of rectangles to try and generate
   * @param sizeRatio 0 = all small ones, 100 = all big ones
   * @param width     the width of the bounding rectangle
   * @param height    the height of the bounding rectangle
   * @return
   */
  public ArrayList<ScoutWidget> random(int count, int sizeRatio, int width, int height) {
    recs = new ArrayList<ScoutWidget>();
    FakeWidget parent = new FakeWidget(null);
    parent.setX(0);
    parent.setY(0);
    parent.setWidth(width);
    parent.setHeight(height);

    int minWidth = MIN_WIDTH;
    int minHeight = MIN_HEIGHT;
    int minGap = MIN_GAP;
    int gapBy2 = MIN_GAP * 2;

    Random rand = new Random(System.currentTimeMillis());
    FakeWidget test = new FakeWidget(null);
    for (int i = 0; i < count; i++) {

      FakeWidget rn = new FakeWidget(null);
      boolean found = false;

      int attempt = 0;
      while (!found) {
        if (rand.nextInt(100) < sizeRatio) {
          rn.setX(rand.nextInt(width - minWidth - gapBy2) + minGap);
          rn.setY(rand.nextInt(height - minHeight - gapBy2) + minGap);
          rn.setWidth(minWidth + rand.nextInt(width - (int)rn.getX() - minWidth - minGap));
          rn.setHeight(minHeight + rand.nextInt(height - (int)rn.getY() - minHeight - minGap));
        }
        else {
          rn.setX(rand.nextInt(width - minWidth - gapBy2) + minGap);
          rn.setY(rand.nextInt(height - minHeight - gapBy2) + minGap);
          rn.setWidth(minWidth);
          rn.setHeight(minHeight);
        }
        rn.setBaseline((int)(rn.getY() / 2));
        test.setX((int)rn.getX() - minGap);
        test.setY((int)rn.getY() - minGap);
        test.setWidth((int)rn.getWidth() + gapBy2);
        test.setHeight((int)rn.getHeight() + gapBy2);

        found = true;
        int size = recs.size();
        for (int j = 0; j < size; j++) {
          if (recs.get(j).getRectangle().intersects(test.getRectangle())) {
            found = false;
            break;
          }
        }
        attempt++;
        if (attempt > MAX_TRIES) {
          break;
        }
      }
      if (found) {
        recs.add(rn);
      }
    }
    recs.add(0, parent);
    return recs;
  }

  /**
   * Returns a string in display list format to visualize the widgets in testing.
   */
  public String displayRecs(ScoutWidget[] recs) {
    String display = "";
    for (int i = 0; i < recs.length; i++) {
      ScoutWidget wrect = recs[i];
      if (i != 0) {
        display += String.format("DrawComponentBackground,%d,%d,%d,%d,1,false\n",
                                 (int)wrect.getX(),
                                 (int)wrect.getY(),
                                 (int)wrect.getWidth(),
                                 (int)wrect.getHeight());
        display += String.format("DrawTextRegion,%d,%d,%d,%d,0,0,false,false,5,5,28,1.0,\"\"\n",
                                 (int)wrect.getX(),
                                 (int)wrect.getY(),
                                 (int)wrect.getWidth(),
                                 (int)wrect.getHeight());
      }
      display += String.format("DrawNlComponentFrame,%d,%d,%d,%d,1,20,20\n",
                               (int)wrect.getX(),
                               (int)wrect.getY(),
                               (int)wrect.getWidth(),
                               (int)wrect.getHeight());
    }
    display += String.format("Clip,0,0,%d,%d\n", (int)recs[0].getWidth(), (int)recs[0].getHeight());
    return display;
  }
}
