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
package com.android.tools.idea.uibuilder.layout3d;

import com.intellij.util.ui.UIUtil;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Vector;

/**
 * Display the 3D rendering of of the mTriData
 */
public class Display3D extends JPanel {
  private boolean myRetinaDisplay = false;
  BufferedImage myImage;
  float[] myZBuffer = new float[1];
  TriData mTriData = new Cube();
  TriData myTransFormCache;
  ViewMatrix myViewMatrix = new ViewMatrix();
  Matrix myInvMatrix;
  boolean isImageInvalid = true;
  static final boolean mPerspective = false;

  Vector<ActionListener> myViewChangeListener = new Vector<>();

  public String getOrientationString(Rectangle rect) {
    return myViewMatrix.getOrientationString(rect);
  }

  public Rectangle parseOrientationString(String pref) {
    Rectangle rec = myViewMatrix.parseOrientationString(pref);
    myInvMatrix = myViewMatrix.invers();
    isImageInvalid = true;
    repaint();
    return rec;
  }

  public void addViewChangeListener(ActionListener listener) {
    myViewChangeListener.add(listener);
  }
  private void fireViewChanged() {
    for (ActionListener listener : myViewChangeListener) {
      listener.actionPerformed(new ActionEvent(this,0,"view"));
    }
  }
  public Display3D() {
    setup();
    Object obj = Toolkit.getDefaultToolkit()
      .getDesktopProperty(
        "apple.awt.contentScaleFactor");
    if (obj instanceof Float) {
      Float scale = (Float)obj;
      if (scale.intValue() == 2) {
        myRetinaDisplay = true;
      }
    }

    setFocusable(true);
    addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped(KeyEvent e) {
        switch (e.getKeyChar()) {
          case 's':
            BufferedImage img = save(1920*4,1024*4);
            try {
              ImageIO.write(img, "png", new File("capture"+((System.currentTimeMillis()/1000)%10000)+"png"));
            }
            catch (IOException e1) {
              e1.printStackTrace();
            }
            break;
        }
      }
    });

    addMouseListener(new MouseAdapter() {

      @Override
      public void mouseReleased(MouseEvent e) {
        myViewMatrix.trackBallUP(e.getX(), e.getY());
        myInvMatrix = myViewMatrix.invers();
        fireViewChanged();
       }

      @Override
      public void mousePressed(MouseEvent e) {
        myViewMatrix.trackBallDown(e.getX(), e.getY());
        myInvMatrix = myViewMatrix.invers();
      }
    });
    addMouseWheelListener(e -> {
      double sw = myViewMatrix.getScreenWidth();
      myViewMatrix.setScreenWidth(sw * (e.getPreciseWheelRotation() + 10) / 10);
      myViewMatrix.calcMatrix();
      myInvMatrix = myViewMatrix.invers();
      isImageInvalid = true;
      repaint();
    });
    addMouseMotionListener(new MouseMotionAdapter() {

      @Override
      public void mouseDragged(MouseEvent e) {
        myViewMatrix.trackBallMove(e.getX(), e.getY());
        myInvMatrix = myViewMatrix.invers();
        isImageInvalid = true;
        repaint();
      }
    });
  }

  public BufferedImage save(int w, int h) {
    myImage = UIUtil.createImage(w, h, BufferedImage.TYPE_INT_RGB);
    myZBuffer = new float[w * h];
    isImageInvalid = true;
    myViewMatrix.setScreenDim(w, h);
    myViewMatrix.calcMatrix();
    myInvMatrix = myViewMatrix.invers();

    int[] rgbData = ((DataBufferInt)myImage.getRaster().getDataBuffer()).getData();
    if (isImageInvalid) {
      if (myTransFormCache == null) {
        myTransFormCache = new TriData(mTriData);
      }
      if (mPerspective) {
        mTriData.transformP(myInvMatrix, myTransFormCache, w / 2f, h / 2f, 800);
      } else {
        mTriData.transform (myInvMatrix, myTransFormCache);
      }

      Arrays.fill(myZBuffer, Float.MAX_VALUE);
      Arrays.fill(rgbData, 0xFF000000);

      if (mTriData.myTexture != null) {
        Rasterize.toZBuff(myZBuffer, rgbData, w, h, myTransFormCache,
                          mTriData.myTexture, mTriData.myTextureWidth, mTriData.myTextureHeight, myViewMatrix.m);
      }
      else { // run a simple render if no myTexture
        Rasterize.simple(myZBuffer, rgbData, w, h, myTransFormCache);
      }

      isImageInvalid = false;
    }
    return myImage;
  }

  public void setTriData(TriData data) {
    mTriData = data;
    setup();
    repaint();
  }

  public void setup() {
    myViewMatrix = new ViewMatrix();
    myTransFormCache = null;
    if (myRetinaDisplay) {
      myViewMatrix.look(ViewMatrix.UP_AT, mTriData, getWidth() * 2, getHeight() * 2);
    }
    else {
      myViewMatrix.look(ViewMatrix.UP_AT, mTriData, getWidth(), getHeight());
    }
    myViewMatrix.setScreenWidth(1920);
    myViewMatrix.calcMatrix();
    myInvMatrix = myViewMatrix.invers();
    isImageInvalid = true;
  }

  @Override
  public void paint(Graphics g) {
    int w = getWidth();
    int h = getHeight();
    if (myRetinaDisplay) {
      w *= 2;
      h *= 2;
    }
    if (myImage == null || myImage.getWidth() != w || myImage.getHeight() != h) {
      myImage = UIUtil.createImage(w, h, BufferedImage.TYPE_INT_RGB);
      myZBuffer = new float[w * h];
      isImageInvalid = true;
      myViewMatrix.setScreenDim(w, h);
      myViewMatrix.calcMatrix();
      myInvMatrix = myViewMatrix.invers();
    }
    int[] rgbData = ((DataBufferInt)myImage.getRaster().getDataBuffer()).getData();
    if (isImageInvalid) {
      if (myTransFormCache == null) {
        myTransFormCache = new TriData(mTriData);
      }
      if (mPerspective) {
        mTriData.transformP(myInvMatrix, myTransFormCache, w / 2f, h / 2f, 800);
      } else {
        mTriData.transform (myInvMatrix, myTransFormCache);
      }

      Arrays.fill(myZBuffer, Float.MAX_VALUE);
      Arrays.fill(rgbData, 0xFF000000);

      if (mTriData.myTexture != null) {
        Rasterize.toZBuff(myZBuffer, rgbData, w, h, myTransFormCache,
                          mTriData.myTexture, mTriData.myTextureWidth, mTriData.myTextureHeight, myViewMatrix.m);
      }
      else { // run a simple render if no myTexture
        Rasterize.simple(myZBuffer, rgbData, w, h, myTransFormCache);
      }

      isImageInvalid = false;
    }
    g.drawImage(myImage, 0, 0, getWidth(), getHeight(), null);
  }

}
