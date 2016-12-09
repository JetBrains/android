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
package com.android.tools.idea.uibuilder.scene.draw;

import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.scene.decorator.*;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.GeneralPath;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;


/**
 * DisplayList implementation for Scene
 * Also contains some primitive display elements.
 */
public class DisplayList {
  private final static boolean DEBUG = false;
  private ArrayList<DrawCommand> myCommands = new ArrayList<DrawCommand>();

  public void clear() {
    myCommands.clear();
  }

  public ArrayList<DrawCommand> getCommands() {
    return myCommands;
  }

  /////////////////////////////////////////////////////////////////////////////
  // Drawing Elements
  /////////////////////////////////////////////////////////////////////////////

  static class Connection implements DrawCommand {
    int x1;
    int y1;
    int x2;
    int y2;
    int myDirection;
    private static final int DIR_LEFT = 0;
    private static final int DIR_TOP = 1;
    private static final int DIR_RIGHT = 2;
    private static final int DIR_BOTTOM = 3;
    private static final int DIR_BASELINE = 4;

    @Override
    public int getLevel() {
      return TOP_LEVEL;
    }

    @Override
    public int compareTo(@NotNull Object o) {
      return Integer.compare(getLevel(), ((DrawCommand)o).getLevel());
    }

    @Override
    public String serialize() {
      return "Connection," + x1 + "," + y1 + "," + x2 + "," + y2
             + myDirection;
    }

    public Connection(String s) {
      String[] sp = s.split(",");
      int c = 0;
      x1 = Integer.parseInt(sp[c++]);
      y1 = Integer.parseInt(sp[c++]);
      x2 = Integer.parseInt(sp[c++]);
      y2 = Integer.parseInt(sp[c++]);
      myDirection = Integer.parseInt(sp[c++]);
    }

    public Connection(int x1, int y1, int x2, int y2, int direction) {
      this.x1 = x1;
      this.y1 = y1;
      this.x2 = x2;
      this.y2 = y2;
      myDirection = direction;
    }

    @Override
    public void paint(Graphics2D g, SceneContext sceneContext) {
      g.setColor(sceneContext.getColorSet().getFrames());
      int start_dx = 0;
      int start_dy = 0;
      int end_dx = 0;
      int end_dy = 0;
      int scale = 20;
      int arrowDirection = 0;
      switch (myDirection) {
        case DIR_LEFT:
          start_dx = -scale;
          end_dx = (x2 > x1) ? -scale : scale;
          arrowDirection = (x2 > x1) ? DrawConnection.DIR_LEFT : DrawConnection.DIR_RIGHT;
          break;
        case DIR_TOP:
          start_dy = -10;
          end_dy = (y2 > y1) ? -scale : scale;
          arrowDirection = (y2 > y1) ? DrawConnection.DIR_TOP : DrawConnection.DIR_BOTTOM;

          break;
        case DIR_RIGHT:
          end_dx = (x2 > x1) ? -scale : scale;
          start_dx = scale;
          arrowDirection = (x2 > x1) ? DrawConnection.DIR_LEFT : DrawConnection.DIR_RIGHT;
          break;
        case DIR_BOTTOM:
          start_dy = scale;
          end_dy = (y2 > y1) ? -scale : scale;
          arrowDirection = (y2 > y1) ? DrawConnection.DIR_TOP : DrawConnection.DIR_BOTTOM;
          break;
        case DIR_BASELINE:
          start_dy = -scale;
          end_dy = scale;
          arrowDirection = DrawConnection.DIR_BOTTOM;
          break;
      }
      GeneralPath path = new GeneralPath();
      path.moveTo(x1, y1);
      path.curveTo(x1 + start_dx, y1 + start_dy, x2 + end_dx, y2 + end_dy, x2, y2);
      g.draw(path);
      int[] xPoints = new int[3];
      int[] yPoints = new int[3];
      DrawConnectionUtils.getArrow(arrowDirection, x2, y2, xPoints, yPoints);
      g.fillPolygon(xPoints, yPoints, 3);
    }
  }

  static class Rect extends Rectangle implements DrawCommand {
    Color color;

    @Override
    public String serialize() {
      return "Rect," + x + "," + y + "," + width + "," + height + "," + Integer.toHexString(color.getRGB());
    }

    public Rect(String s) {
      String[] sp = s.split(",");
      int c = 0;
      x = Integer.parseInt(sp[c++]);
      y = Integer.parseInt(sp[c++]);
      width = Integer.parseInt(sp[c++]);
      height = Integer.parseInt(sp[c++]);
      color = new Color((int)Long.parseLong(sp[c++], 16));
    }

    public Rect(int x, int y, int width, int height, Color c) {
      super(x, y, width, height);
      color = c;
    }

    @Override
    public int getLevel() {
      return COMPONENT_LEVEL;
    }

    @Override
    public int compareTo(@NotNull Object o) {
      return Integer.compare(getLevel(), ((DrawCommand)o).getLevel());
    }

    @Override
    public void paint(Graphics2D g, SceneContext sceneContext) {
      g.setColor(color);
      g.drawRect(x, y, width, height);
    }
  }

  static class Clip extends Rectangle implements DrawCommand {
    Shape myOriginal;

    @Override
    public String serialize() {
      return "Clip," + x + "," + y + "," + width + "," + height;
    }

    @Override
    public int getLevel() {
      return CLIP_LEVEL;
    }

    @Override
    public int compareTo(@NotNull Object o) {
      return Integer.compare(getLevel(), ((DrawCommand)o).getLevel());
    }

    public Clip(String s) {
      String[] sp = s.split(",");
      int c = 0;
      x = Integer.parseInt(sp[c++]);
      y = Integer.parseInt(sp[c++]);
      width = Integer.parseInt(sp[c++]);
      height = Integer.parseInt(sp[c++]);
    }

    public Clip(int x, int y, int width, int height) {
      super(x, y, width, height);
    }

    public Shape getOriginalShape() {
      return myOriginal;
    }

    @Override
    public void paint(Graphics2D g, SceneContext sceneContext) {
      myOriginal = g.getClip();
      g.clipRect(x, y, width, height);
    }
  }

  public static class UNClip implements DrawCommand {
    Clip lastClip;

    @Override
    public String serialize() {
      return "UNClip";
    }

    @Override
    public int getLevel() {
      return UNCLIP_LEVEL;
    }

    @Override
    public int compareTo(@NotNull Object o) {
      return Integer.compare(getLevel(), ((DrawCommand)o).getLevel());
    }

    public UNClip(String s) {
    }

    public UNClip(Clip s) {
      lastClip = s;
    }

    @Override
    public void paint(Graphics2D g, SceneContext sceneContext) {
      g.setClip(lastClip.getOriginalShape());
    }

    public void setClip(Clip clip) {
      lastClip = clip;
    }
  }

  static class Line implements DrawCommand {
    Color color;
    int x1;
    int y1;
    int x2;
    int y2;

    @Override
    public String serialize() {
      return "Line," + x1 + "," + y1 + "," + x2 + "," + y2 + "," + Integer.toHexString(color.getRGB());
    }

    @Override
    public int getLevel() {
      return TARGET_LEVEL;
    }

    @Override
    public int compareTo(@NotNull Object o) {
      return Integer.compare(getLevel(), ((DrawCommand)o).getLevel());
    }

    public Line(String s) {
      String[] sp = s.split(",");
      int c = 0;
      x1 = Integer.parseInt(sp[c++]);
      y1 = Integer.parseInt(sp[c++]);
      x2 = Integer.parseInt(sp[c++]);
      y2 = Integer.parseInt(sp[c++]);
      color = new Color((int)Long.parseLong(sp[c++], 16));
    }

    public Line(SceneContext transform, float x1, float y1, float x2, float y2, Color c) {
      this.x1 = transform.getSwingX(x1);
      this.y1 = transform.getSwingY(y1);
      this.x2 = transform.getSwingX(x2);
      this.y2 = transform.getSwingY(y2);
      this.color = c;
    }

    @Override
    public void paint(Graphics2D g, SceneContext sceneContext) {
      g.setColor(color);
      g.drawLine(x1, y1, x2, y2);
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // Public methods to add elements to the display list
  /////////////////////////////////////////////////////////////////////////////
  public void add(DrawCommand cmd) {
    myCommands.add(cmd);
  }

  public UNClip addClip(SceneContext transform, Rectangle r) {
    int l = transform.getSwingX(r.x);
    int t = transform.getSwingY(r.y);
    int w = transform.getSwingDimension(r.width);
    int h = transform.getSwingDimension(r.height);
    Clip c = new Clip(l, t, w, h);
    myCommands.add(c);
    return new UNClip(c);
  }

  public void addRect(SceneContext transform, Rectangle r, Color color) {
    int l = transform.getSwingX(r.x);
    int t = transform.getSwingY(r.y);
    int w = transform.getSwingDimension(r.width);
    int h = transform.getSwingDimension(r.height);
    myCommands.add(new Rect(l, t, w, h, color));
  }

  public void addRect(SceneContext transform, float left, float top, float right, float bottom, Color color) {
    int l = transform.getSwingX(left);
    int t = transform.getSwingY(top);
    int w = transform.getSwingDimension(right - left);
    int h = transform.getSwingDimension(bottom - top);
    add(new Rect(l, t, w, h, color));
  }

  public void addConnection(SceneContext transform, float x1, float y1, float x2, float y2, int direction) {
    int sx1 = transform.getSwingX(x1);
    int sy1 = transform.getSwingY(y1);
    int sx2 = transform.getSwingX(x2);
    int sy2 = transform.getSwingY(y2);
    add(new Connection(sx1, sy1, sx2, sy2, direction));
  }

  public void addLine(SceneContext transform, float x1, float y1, float x2, float y2, Color color) {
    add(new Line(transform, x1, y1, x2, y2, color));
  }

  /////////////////////////////////////////////////////////////////////////////
  // Painting
  /////////////////////////////////////////////////////////////////////////////
  static class CommandSet implements DrawCommand {
    private ArrayList<DrawCommand> myCommands = new ArrayList<DrawCommand>();

    public CommandSet(DrawCommand[] commands, int start, int end) {
      if (commands.length == 0) {
        return;
      }
      int first = findFirstClip(commands, start, end);
      int last = findLastUnClip(commands, start, end);
      if (first == start && last == end) {
        myCommands.add(commands[start]);
        for (int i = start + 1; i < end; i++) {
          DrawCommand cmd = commands[i];
          if (cmd instanceof Clip) {
            int n = findLastUnClip(commands, start, end - 1);
            cmd = new CommandSet(commands, i, n);
            i = Math.max(n, i);
          }
          myCommands.add(cmd);
        }
        myCommands.add(commands[end]);
      }
      else if (first != -1 && last != -1) {
        for (int i = start; i < first; i++) {
          myCommands.add(commands[i]);
        }
        myCommands.add(new CommandSet(commands, first, last));
        for (int i = last + 1; i <= end; i++) {
          myCommands.add(commands[i]);
        }
      }
      else {
        for (int i = start; i <= end; i++) {
          myCommands.add(commands[i]);
        }
      }
    }

    private int findFirstClip(DrawCommand[] commands, int start, int end) {
      for (int i = start; i < end; i++) {
        if (commands[i] instanceof Clip) {
          return i;
        }
      }
      return -1;
    }

    private int findLastUnClip(DrawCommand[] commands, int start, int end) {
      for (int i = end; i > start; i--) {
        if (commands[i] instanceof UNClip) {
          return i;
        }
      }
      return -1;
    }

    public void sort() {
      myCommands.sort((o1, o2) -> Integer.compare(o1.getLevel(), o2.getLevel()));
      myCommands.forEach(command -> {
        if (command instanceof CommandSet) ((CommandSet)command).sort();
      });
    }

    @Override
    public int getLevel() {
      return COMPONENT_LEVEL;
    }

    @Override
    public void paint(Graphics2D g2, SceneContext sceneContext) {
      myCommands.forEach(command -> command.paint(g2, sceneContext));
    }

    public void print(String s) {
      myCommands.forEach(command -> {
        if (command instanceof CommandSet) {
          ((CommandSet)command).print(s + ">");
        }
        else {
          System.out.println(s + command.serialize());
        }
      });
    }

    @Override
    public String serialize() {
      String str = "";
      for (DrawCommand command : myCommands) {
        str += command.serialize();
      }
      return str;
    }

    @Override
    public int compareTo(@NotNull Object o) {
      return Integer.compare(getLevel(), ((DrawCommand)o).getLevel());
    }
  }

  public void paint(Graphics2D g2, SceneContext sceneContext) {
    int count = myCommands.size();
    if (count == 0) {
      return;
    }
    if (DEBUG) {
      System.out.println(" -> ");
      for (int i = 0; i < myCommands.size(); i++) {
        System.out.println(i + " " + myCommands.get(i).serialize());
      }
      System.out.println("<");
    }
    Graphics2D g = (Graphics2D)g2.create();
    DrawCommand[] array = myCommands.toArray(new DrawCommand[myCommands.size()]);
    CommandSet set = new CommandSet(array, 0, array.length - 1);
    set.sort();
    if (DEBUG) {
      set.print(">");
      System.out.println("-end-");
    }
    set.paint(g, sceneContext);
    g.dispose();
  }

  /**
   * This serialized the current display list
   * it can be deserialize using the command getDisplayList(String)
   *
   * @return
   */
  public String serialize() {
    String str = "";
    int count = myCommands.size();
    for (int i = 0; i < count; i++) {
      DrawCommand command = myCommands.get(i);
      str += command.serialize() + "\n";
    }
    return str;
  }

  static HashMap<String, Constructor<? extends DrawCommand>> ourBuildMap = new HashMap<>();

  static {
    try {
      ourBuildMap.put("Connection", Connection.class.getConstructor(String.class));
      ourBuildMap.put("Rect", Rect.class.getConstructor(String.class));
      ourBuildMap.put("Clip", Clip.class.getConstructor(String.class));
      ourBuildMap.put("UNClip", UNClip.class.getConstructor(String.class));
      ourBuildMap.put("Line", Line.class.getConstructor(String.class));
      ourBuildMap.put("DrawConnection", DrawConnection.class.getConstructor(String.class));
      addListElementConstructor(DrawResize.class);
      addListElementConstructor(DrawAnchor.class);
      addListElementConstructor(DrawComponent.class);
      addListElementConstructor(ProgressBarDecorator.DrawProgressBar.class);
      addListElementConstructor(ButtonDecorator.DrawButton.class);
      addListElementConstructor(DrawTextRegion.class);
      addListElementConstructor(ImageViewDecorator.DrawImageView.class);
      addListElementConstructor(CheckBoxDecorator.DrawCheckbox.class);
      addListElementConstructor(SeekBarDecorator.DrawSeekBar.class);
      addListElementConstructor(SwitchDecorator.DrawSwitch.class);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  static public void addListElementConstructor(Class<? extends DrawCommand> c) {
    try {
      ourBuildMap.put(c.getSimpleName(), c.getConstructor(String.class));
    }
    catch (NoSuchMethodException e) {
      e.printStackTrace();
    }
  }

  static public void addListElementConstructor(String cmd, Constructor<? extends DrawCommand> constructor) {
    ourBuildMap.put(cmd, constructor);
  }

  static private DrawCommand get(String cmd, String args) {
    try {
      return ourBuildMap.get(cmd).newInstance(args);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  public static DisplayList getDisplayList(String str) {
    DisplayList list = new DisplayList();
    String[] sp = str.split("\n");
    DrawCommand drawCommand = null;
    Clip lastClip = null;
    for (int i = 0; i < sp.length; i++) {
      String s = sp[i];
      String cmd, args;
      if (s.indexOf(',') > 0) {
        cmd = s.substring(0, s.indexOf(","));
        args = s.substring(s.indexOf(",") + 1);
      }
      else {
        cmd = s;
        args = "";
      }
      list.add(drawCommand = get(cmd, args));
      if (drawCommand instanceof Clip) {
        lastClip = (Clip)drawCommand;
      }
      if (drawCommand instanceof UNClip) {
        UNClip unclip = (UNClip)drawCommand;
        unclip.setClip(lastClip);
      }
    }

    return list;
  }
}
