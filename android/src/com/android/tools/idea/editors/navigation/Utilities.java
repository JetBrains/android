package com.android.tools.idea.editors.navigation;

import java.awt.*;

public class Utilities {
  public static Point add(Point p1, Point p2) {
    return new Point(p1.x + p2.x, p1.y + p2.y);
  }

  public static Point diff(Point p1, Point p2) {
    return new Point(p1.x - p2.x, p1.y - p2.y);
  }

  public static Point scale(Point p, float k) {
    return new Point((int)(k * p.x), (int)(k * p.y));
  }

  public static Point project(Rectangle r, Point p) {
    Point centre = centre(r);
    Point diff = diff(p, centre);
    boolean horizontal = Math.abs((float)diff.y / diff.x) < Math.abs((float)r.height / r.width);
    float scale = horizontal ? (float)r.width / 2 / diff.x : (float)r.height / 2 / diff.y;
    return add(centre, scale(diff, Math.abs(scale)));
  }

  public static Point centre(Rectangle r) {
    return new Point(r.x + r.width / 2, r.y + r.height / 2);
  }

  public static Point centre(Component c) {
    return centre(c.getBounds());
  }
}
