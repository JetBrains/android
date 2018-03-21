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

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.uibuilder.scene.decorator.*;
import com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawAnchor; // TODO: remove
import com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawConnection; // TODO: remove
import com.android.tools.idea.uibuilder.handlers.constraint.draw.DrawConnectionUtils; // TODO: remove
import com.android.tools.idea.uibuilder.scene.draw.DrawResize;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.GeneralPath;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.function.Function;


/**
 * DisplayList implementation for Scene
 * Also contains some primitive display elements.
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class DisplayList {
  private final static boolean DEBUG = false;
  private ArrayList<DrawCommand> myCommands = new ArrayList<>();

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
    @SwingCoordinate int x1;
    @SwingCoordinate int y1;
    @SwingCoordinate int x2;
    @SwingCoordinate int y2;
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
      myDirection = Integer.parseInt(sp[c]);
    }

    public Connection(@SwingCoordinate int x1, @SwingCoordinate int y1, @SwingCoordinate int x2, @SwingCoordinate int y2, int direction) {
      this.x1 = x1;
      this.y1 = y1;
      this.x2 = x2;
      this.y2 = y2;
      myDirection = direction;
    }

    @Override
    public void paint(Graphics2D g, SceneContext sceneContext) {
      g.setColor(sceneContext.getColorSet().getAnchorConnectionCircle());
      int start_dx = 0;
      int start_dy = 0;
      int end_dx = 0;
      int end_dy = 0;
      int scale = 20;
      int arrowDirection = 0;
      int arrowX = x2;
      int arrowY = y2;
      int arrowGap = DrawConnectionUtils.CONNECTION_ARROW_SIZE;
      switch (myDirection) {
        case DIR_LEFT:
          start_dx = -scale;
          end_dx = (x2 > x1) ? -scale : scale;
          arrowDirection = (x2 > x1) ? DrawConnection.DIR_LEFT : DrawConnection.DIR_RIGHT;
          arrowX += (x2 > x1) ? -arrowGap : arrowGap;
          break;
        case DIR_TOP:
          start_dy = -10;
          end_dy = (y2 > y1) ? -scale : scale;
          arrowDirection = (y2 > y1) ? DrawConnection.DIR_TOP : DrawConnection.DIR_BOTTOM;
          arrowY += (y2 > y1) ? -arrowGap : arrowGap;
          break;
        case DIR_RIGHT:
          end_dx = (x2 > x1) ? -scale : scale;
          start_dx = scale;
          arrowDirection = (x2 > x1) ? DrawConnection.DIR_LEFT : DrawConnection.DIR_RIGHT;
          arrowX += (x2 > x1) ? -arrowGap : arrowGap;
          break;
        case DIR_BOTTOM:
          start_dy = scale;
          end_dy = (y2 > y1) ? -scale : scale;
          arrowDirection = (y2 > y1) ? DrawConnection.DIR_TOP : DrawConnection.DIR_BOTTOM;
          arrowY += (y2 > y1) ? -arrowGap : arrowGap;
          break;
        case DIR_BASELINE:
          start_dy = -scale;
          end_dy = (y2 > y1) ? -scale : scale;
          arrowDirection = (y2 > y1) ? DrawConnection.DIR_TOP : DrawConnection.DIR_BOTTOM;
          arrowY += (y2 > y1) ? -arrowGap : arrowGap;
          break;
      }
      GeneralPath path = new GeneralPath();
      path.moveTo(x1, y1);
      path.curveTo(x1 + start_dx, y1 + start_dy, x2 + end_dx, y2 + end_dy, arrowX, arrowY);
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
      //noinspection UseJBColor
      color = new Color((int)Long.parseLong(sp[c], 16));
    }

    public Rect(@SwingCoordinate int x, @SwingCoordinate int y, @SwingCoordinate int width, @SwingCoordinate int height, Color c) {
      super(x, y, width, height);
      color = c;
    }

    @Override
    public int getLevel() {
      return COMPONENT_LEVEL;
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

    public Clip(String s) {
      String[] sp = s.split(",");
      int c = 0;
      x = Integer.parseInt(sp[c++]);
      y = Integer.parseInt(sp[c++]);
      width = Integer.parseInt(sp[c++]);
      height = Integer.parseInt(sp[c]);
    }

    public Clip(@SwingCoordinate int x, @SwingCoordinate int y, @SwingCoordinate int width, @SwingCoordinate int height) {
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

    @SuppressWarnings("unused")
    public UNClip(String s) {
      // Used by addListElementConstructor
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

    public Line(String s) {
      String[] sp = s.split(",");
      int c = 0;
      x1 = Integer.parseInt(sp[c++]);
      y1 = Integer.parseInt(sp[c++]);
      x2 = Integer.parseInt(sp[c++]);
      y2 = Integer.parseInt(sp[c++]);
      //noinspection UseJBColor
      color = new Color((int)Long.parseLong(sp[c], 16));
    }

    public Line(SceneContext transform,
                @AndroidDpCoordinate float x1,
                @AndroidDpCoordinate float y1,
                @AndroidDpCoordinate float x2,
                @AndroidDpCoordinate float y2,
                Color c) {
      this.x1 = transform.getSwingXDip(x1);
      this.y1 = transform.getSwingYDip(y1);
      this.x2 = transform.getSwingXDip(x2);
      this.y2 = transform.getSwingYDip(y2);
      this.color = c;
    }

    @Override
    public void paint(Graphics2D g, SceneContext sceneContext) {
      g.setColor(color);
      g.drawLine(x1, y1, x2, y2);
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  //region Public methods to add elements to the display list
  /////////////////////////////////////////////////////////////////////////////

  public void add(DrawCommand cmd) {
    myCommands.add(cmd);
  }

  public UNClip addClip(SceneContext context, @AndroidDpCoordinate Rectangle r) {
    int l = context.getSwingXDip(r.x);
    int t = context.getSwingYDip(r.y);
    int w = context.getSwingDimensionDip(r.width);
    int h = context.getSwingDimensionDip(r.height);
    Clip c = new Clip(l, t, w, h);
    myCommands.add(c);
    return new UNClip(c);
  }

  public void addRect(SceneContext context, @AndroidDpCoordinate Rectangle r, Color color) {
    int l = context.getSwingXDip(r.x);
    int t = context.getSwingYDip(r.y);
    int w = context.getSwingDimensionDip(r.width);
    int h = context.getSwingDimensionDip(r.height);
    myCommands.add(new Rect(l, t, w, h, color));
  }

  public void addRect(SceneContext context,
                      @AndroidDpCoordinate float left,
                      @AndroidDpCoordinate float top,
                      @AndroidDpCoordinate float right,
                      @AndroidDpCoordinate float bottom,
                      Color color) {
    int l = context.getSwingXDip(left);
    int t = context.getSwingYDip(top);
    int w = context.getSwingDimensionDip(right - left);
    int h = context.getSwingDimensionDip(bottom - top);
    add(new Rect(l, t, w, h, color));
  }

  public void addConnection(SceneContext context,
                            @AndroidDpCoordinate float x1,
                            @AndroidDpCoordinate float y1,
                            @AndroidDpCoordinate float x2,
                            @AndroidDpCoordinate float y2,
                            int direction) {
    int sx1 = context.getSwingXDip(x1);
    int sy1 = context.getSwingYDip(y1);
    int sx2 = context.getSwingXDip(x2);
    int sy2 = context.getSwingYDip(y2);
    add(new Connection(sx1, sy1, sx2, sy2, direction));
  }

  public void addLine(SceneContext context,
                      @AndroidDpCoordinate float x1,
                      @AndroidDpCoordinate float y1,
                      @AndroidDpCoordinate float x2,
                      @AndroidDpCoordinate float y2,
                      Color color) {
    add(new Line(context, x1, y1, x2, y2, color));
  }

  //endregion
  /////////////////////////////////////////////////////////////////////////////
  //region Painting
  /////////////////////////////////////////////////////////////////////////////

  static class CommandSet implements DrawCommand {
    private ArrayList<DrawCommand> myCommands = new ArrayList<>();
    private int myLevel;

    public CommandSet(DrawCommand[] commands, int start, int end) {
      this(commands, start, end, COMPONENT_LEVEL);
    }

    @SuppressWarnings("ManualArrayToCollectionCopy")
    public CommandSet(DrawCommand[] commands, int start, int end, int level) {
      myLevel = level;
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
            int n = findNextUnClip(commands, i + 1, end - 1);
            cmd = new CommandSet(commands, i, n);
            //noinspection AssignmentToForLoopParameter
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

    private static int findFirstClip(DrawCommand[] commands, int start, int end) {
      for (int i = start; i < end; i++) {
        if (commands[i] instanceof Clip) {
          return i;
        }
      }
      return -1;
    }

    private static int findLastUnClip(DrawCommand[] commands, int start, int end) {
      for (int i = end; i > start; i--) {
        if (commands[i] instanceof UNClip) {
          return i;
        }
      }
      return -1;
    }

    private static int findNextUnClip(DrawCommand[] commands, int start, int end) {
      int count = 0;
      for (int i = start; i <= end; i++) {
        if (commands[i] instanceof Clip) {
          count++;
        }
        if (commands[i] instanceof UNClip) {
          if (count == 0) {
            return i;
          }
          else {
            count--;
          }
        }
      }
      return -1;
    }

    public void sort() {
      myCommands.sort(Comparator.comparingInt(DrawCommand::getLevel));
      myCommands.forEach(command -> {
        if (command instanceof CommandSet) ((CommandSet)command).sort();
      });
    }

    @Override
    public int getLevel() {
      return myLevel;
    }

    @SuppressWarnings("ForLoopReplaceableByForEach")
    @Override
    public void paint(Graphics2D g2, SceneContext sceneContext) {
      for (int i = 0; i < myCommands.size(); i++) {
        myCommands.get(i).paint(g2, sceneContext);
      }
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

    @SuppressWarnings("StringConcatenationInLoop")
    @Override
    public String serialize() {
      String str = "";
      for (DrawCommand command : myCommands) {
        str += command.serialize() + "\n";
      }
      return str;
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

  @VisibleForTesting
  public String generateSortedDisplayList(SceneContext sceneContext) {
    DrawCommand[] array = myCommands.toArray(new DrawCommand[myCommands.size()]);
    CommandSet set = new CommandSet(array, 0, array.length - 1);
    set.sort();
    return set.serialize();
  }

  /**
   * This serialized the current display list
   * it can be deserialize using the command getDisplayList(String)
   *
   * @return
   */
  @SuppressWarnings({"ForLoopReplaceableByForEach", "StringConcatenationInLoop"})
  public String serialize() {
    String str = "";
    int count = myCommands.size();
    for (int i = 0; i < count; i++) {
      DrawCommand command = myCommands.get(i);
      str += command.serialize() + "\n";
    }
    return str;
  }

  public DrawCommand getCommand(int level) {
    DrawCommand[] array = myCommands.toArray(new DrawCommand[myCommands.size()]);
    return new CommandSet(array, 0, array.length - 1, level);
  }

  static HashMap<String, Function<String, ? extends DrawCommand>> ourBuildMap = new HashMap<>();

  static {
    addListElementConstructor(Connection.class);
    addListElementConstructor(Rect.class);
    addListElementConstructor(Clip.class);
    addListElementConstructor(UNClip.class);
    addListElementConstructor(Line.class);
    addListElementConstructor(DrawConnection.class);
    addListElementConstructor(DrawResize.class);
    addListElementConstructor(DrawAnchor.class);
    addListElementConstructor(DrawComponentBackground.class);
    addListElementConstructor(DrawNlComponentFrame.class);
    addListElementConstructor(ProgressBarDecorator.DrawProgressBar.class);
    addListElementConstructor(LinearLayoutDecorator.DrawLinearLayout.class);

    addListElementConstructor(ImageViewDecorator.DrawImageView.class);
    addListElementConstructor(SeekBarDecorator.DrawSeekBar.class);

    addListElementProvider(DrawTextRegion.class, DrawTextRegion::createFromString);
    addListElementProvider(ButtonDecorator.DrawButton.class, ButtonDecorator.DrawButton::createFromString);
    addListElementProvider(SwitchDecorator.DrawSwitch.class, SwitchDecorator.DrawSwitch::createFromString);
    addListElementProvider(RadioButtonDecorator.DrawRadioButton.class, RadioButtonDecorator.DrawRadioButton::createFromString);
    addListElementProvider(CheckBoxDecorator.DrawCheckbox.class, CheckBoxDecorator.DrawCheckbox::createFromString);
  }

  static public void addListElementConstructor(Class<? extends DrawCommand> c) {
    ourBuildMap.put(c.getSimpleName(), s -> {
      try {
        return c.getConstructor(String.class).newInstance(s);
      }
      catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
        Logger.getInstance(DisplayList.class).error(e);
      }

      return null;
    });
  }

  static public void addListElementProvider(Class<? extends DrawCommand> c, Function<String, ? extends DrawCommand> provider) {
    ourBuildMap.put(c.getSimpleName(), provider);
  }

  @Nullable
  static private DrawCommand get(String cmd, String args) {
    return ourBuildMap.get(cmd).apply(args);
  }

  @SuppressWarnings("ForLoopReplaceableByForEach")
  public static DisplayList getDisplayList(String str) {
    DisplayList list = new DisplayList();
    String[] sp = str.split("\n");
    DrawCommand drawCommand;
    Clip lastClip = null;
    for (int i = 0; i < sp.length; i++) {
      String s = sp[i];
      String cmd, args;
      if (s.indexOf(',') > 0) {
        cmd = s.substring(0, s.indexOf(','));
        args = s.substring(s.indexOf(',') + 1);
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
