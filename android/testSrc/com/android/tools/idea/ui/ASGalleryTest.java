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
package com.android.tools.idea.ui;

import com.google.common.base.Function;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.AndroidTestCase;

import javax.accessibility.Accessible;
import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;

public final class ASGalleryTest extends AndroidTestCase {
  public static final Dimension THUMBNAIL_SIZE = new Dimension(128, 128);
  public static final int COLUMNS = 5;
  public static final Border BORDER = BorderFactory.createEmptyBorder(COLUMNS, 10, 20, 40);

  private ModelObject[] objects;
  private ASGallery<ModelObject> gallery;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    objects = new ModelObject[COLUMNS];
    for (int i = 0; i < COLUMNS; i++) {
      objects[i] = new ModelObject(i + 1);
      if (i > 0) {
        objects[i].myImage = UIUtil.createImage(500, 500, BufferedImage.TYPE_INT_ARGB);
      }
      objects[i].myLabel = "Model " + i;
    }

    ASGallery<ModelObject> asGallery =
      new ASGallery<ModelObject>(
        JBList.createDefaultListModel(objects),
        new Function<ModelObject, Image>() {
          @Override
          public Image apply(ModelObject input) {
            return input.myImage;
          }
        },
        new Function<ModelObject, String>() {
          @Override
          public String apply(ModelObject input) {
            return input.myLabel;
          }
        },
        THUMBNAIL_SIZE,
        null);
    asGallery.setBorder(BORDER);
    gallery = asGallery;
  }
  public void testSelection() {
    assertNull(gallery.getSelectedElement());
    for (int i = 0; i < COLUMNS; i++) {
      gallery.setSelectedIndex(i);
      assertEquals(objects[i], gallery.getSelectedElement());
      assertEquals(objects[i], gallery.getSelectedValue());
      assertEquals(i, gallery.getSelectedIndex());
    }

    gallery.setSelectedElement(objects[1]);
    assertEquals(objects[1], gallery.getSelectedElement());
    gallery.setSelectedElement(new ModelObject(10));
    assertEquals(objects[1], gallery.getSelectedElement());
    assertEquals(objects[1], gallery.getSelectedValue());
    assertEquals(1, gallery.getSelectedIndex());

    gallery.clearSelection();
    assertEquals(null, gallery.getSelectedElement());
    assertEquals(null, gallery.getSelectedValue());
    assertEquals(-1, gallery.getSelectedIndex());
  }

  public void testCellRenderer() {
    for (int i = 0; i < COLUMNS; i++) {
      ModelObject model = objects[i];
      Component renderer = gallery.getCellRenderer().getListCellRendererComponent(gallery, model, i, false, false);
      assertNotNull(renderer);
      if (model.myImage == null) {
        assertTrue(renderer instanceof JLabel);
        assertEquals(((JLabel)renderer).getText(), model.myLabel);
      } else {
        assertTrue(renderer instanceof JPanel);
        assertTrue(((JPanel)renderer).getComponent(0) instanceof JLabel);
        assertNotNull(((JLabel)((JPanel)renderer).getComponent(0)).getIcon());
        assertEquals(THUMBNAIL_SIZE.width, ((JLabel)((JPanel)renderer).getComponent(0)).getIcon().getIconHeight());
        assertEquals(THUMBNAIL_SIZE.height, ((JLabel)((JPanel)renderer).getComponent(0)).getIcon().getIconHeight());
        assertTrue(((JPanel)renderer).getComponent(1) instanceof JLabel);
        assertEquals(model.myLabel, ((JLabel)((JPanel)renderer).getComponent(1)).getText());
      }
    }
  }

  public void testCellRendererCache() {
    for (int i = 0; i < COLUMNS; i++) {
      ModelObject model = objects[i];
      Component renderer1 = gallery.getCellRenderer().getListCellRendererComponent(gallery, model, i, false, false);
      assertNotNull(renderer1);
      Component renderer2 = gallery.getCellRenderer().getListCellRendererComponent(gallery, model, i, true, false);
      assertEquals(renderer1, renderer2);
      Component renderer3 = gallery.getCellRenderer().getListCellRendererComponent(gallery, model, i, true, true);
      assertEquals(renderer1, renderer3);
    }
  }

  public void testNextElementAction() {
    ActionEvent event = new ActionEvent(gallery, ActionEvent.ACTION_FIRST, "test");
    gallery.setSelectedIndex(0);
    assertEquals(0, gallery.getSelectedIndex());
    for (int i = 1; i < COLUMNS; i++) {
      gallery.getActionMap().get("nextListElement").actionPerformed(event);
      assertEquals(i, gallery.getSelectedIndex());
    }
    gallery.getActionMap().get("nextListElement").actionPerformed(event);
    assertEquals(COLUMNS - 1, gallery.getSelectedIndex());
  }

  public void testPreviousElementAction() {
    ActionEvent event = new ActionEvent(gallery, ActionEvent.ACTION_FIRST, "test");
    gallery.setSelectedIndex(COLUMNS - 1);
    assertEquals(COLUMNS - 1, gallery.getSelectedIndex());
    for (int i = COLUMNS - 2; i >= 0; i--) {
      gallery.getActionMap().get("previousListElement").actionPerformed(event);
      assertEquals(i, gallery.getSelectedIndex());
    }
    gallery.getActionMap().get("previousListElement").actionPerformed(event);
    assertEquals(0, gallery.getSelectedIndex());
  }

  public void testAccessible() {
    for (int i = 0; i < COLUMNS; i++) {
      ModelObject model = objects[i];
      Accessible child = gallery.getAccessibleContext().getAccessibleChild(i);
      assertNotNull(child);
      assertEquals(model.myLabel, child.getAccessibleContext().getAccessibleName());
      assertEquals(null, child.getAccessibleContext().getAccessibleDescription());
      assertEquals(gallery, child.getAccessibleContext().getAccessibleParent());
      assertEquals(i, child.getAccessibleContext().getAccessibleIndexInParent());
    }
  }

  private static final class ModelObject {
    public final int myNumber;
    public String myLabel;
    public Image myImage;

    public ModelObject(int number) {
      myNumber = number;
    }

    @Override
    public String toString() {
      return String.valueOf(myNumber);
    }
  }
}
