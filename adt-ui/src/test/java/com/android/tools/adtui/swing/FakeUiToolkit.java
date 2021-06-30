/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.adtui.swing;

import java.awt.Dialog.ModalExclusionType;
import java.awt.Dialog.ModalityType;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.HeadlessException;
import java.awt.Image;
import java.awt.PrintJob;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.Clipboard;
import java.awt.font.TextAttribute;
import java.awt.im.InputMethodHighlight;
import java.awt.image.ColorModel;
import java.awt.image.DirectColorModel;
import java.awt.image.ImageObserver;
import java.awt.image.ImageProducer;
import java.awt.peer.WindowPeer;
import java.net.URL;
import java.util.Map;
import java.util.Properties;
import sun.awt.ComponentFactory;

class FakeUiToolkit extends Toolkit implements ComponentFactory {
  @Override
  public Dimension getScreenSize() throws HeadlessException {
    return new Dimension(1920, 1200);
  }

  @Override
  public int getScreenResolution() throws HeadlessException {
    return 0;
  }

  @Override
  public ColorModel getColorModel() throws HeadlessException {
    return new DirectColorModel(32, 0xFF0000, 0xFF00, 0xFF, 0xFF000000);
  }

  @Override
  public String[] getFontList() {
    return new String[0];
  }

  @Override
  public FontMetrics getFontMetrics(Font font) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void sync() {
  }

  @Override
  public Image getImage(String filename) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Image getImage(URL url) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Image createImage(String filename) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Image createImage(URL url) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean prepareImage(Image image, int width, int height, ImageObserver observer) {
    return false;
  }

  @Override
  public int checkImage(Image image, int width, int height, ImageObserver observer) {
    return 0;
  }

  @Override
  public Image createImage(ImageProducer producer) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Image createImage(byte[] imagedata, int imageoffset, int imagelength) {
    throw new UnsupportedOperationException();
  }

  @Override
  public PrintJob getPrintJob(Frame frame, String jobtitle, Properties props) {
    return null;
  }

  @Override
  public void beep() {
  }

  @Override
  public Clipboard getSystemClipboard() throws HeadlessException {
    throw new UnsupportedOperationException();
  }

  @Override
  protected EventQueue getSystemEventQueueImpl() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isModalityTypeSupported(ModalityType modalityType) {
    return false;
  }

  @Override
  public boolean isModalExclusionTypeSupported(ModalExclusionType modalExclusionType) {
    return false;
  }

  @Override
  public Map<TextAttribute, ?> mapInputMethodHighlight(InputMethodHighlight highlight) throws HeadlessException {
    return null;
  }

  @Override
  public WindowPeer createWindow(Window target) {
    return new FakeWindowPeer();
  }
}