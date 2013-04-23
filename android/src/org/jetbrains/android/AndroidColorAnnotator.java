/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.android;

import com.android.annotations.VisibleForTesting;
import com.android.resources.ResourceType;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.ColorChooser;
import com.intellij.util.ui.ColorIcon;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Locale;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 26, 2009
 * Time: 3:24:05 PM
 * To change this template use File | Settings | File Templates.
 */
public class AndroidColorAnnotator implements Annotator {
  private static final int ICON_SIZE = 8;

  private static final int BLACK = 0xFF000000;
  private static final int DKGRAY = 0xFF444444;
  private static final int GRAY = 0xFF888888;
  private static final int LTGRAY = 0xFFCCCCCC;
  private static final int WHITE = 0xFFFFFFFF;
  private static final int RED = 0xFFFF0000;
  private static final int GREEN = 0xFF00FF00;
  private static final int BLUE = 0xFF0000FF;
  private static final int YELLOW = 0xFFFFFF00;
  private static final int CYAN = 0xFF00FFFF;
  private static final int MAGENTA = 0xFFFF00FF;

  private static HashMap<String, Integer> myColors;

  private static void initializeColors() {
    myColors = new HashMap<String, Integer>();
    myColors.put("black", Integer.valueOf(BLACK));
    myColors.put("darkgray", Integer.valueOf(DKGRAY));
    myColors.put("gray", Integer.valueOf(GRAY));
    myColors.put("lightgray", Integer.valueOf(LTGRAY));
    myColors.put("white", Integer.valueOf(WHITE));
    myColors.put("red", Integer.valueOf(RED));
    myColors.put("green", Integer.valueOf(GREEN));
    myColors.put("blue", Integer.valueOf(BLUE));
    myColors.put("yellow", Integer.valueOf(YELLOW));
    myColors.put("cyan", Integer.valueOf(CYAN));
    myColors.put("magenta", Integer.valueOf(MAGENTA));
  }

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (element instanceof XmlTag) {
      XmlTag tag = (XmlTag)element;
      if ((ResourceType.COLOR.getName().equals(tag.getName()) || ResourceType.DRAWABLE.getName().equals(tag.getName()))) {
        DomElement domElement = DomManager.getDomManager(element.getProject()).getDomElement(tag);
        if (domElement instanceof ResourceElement) {
          Annotation annotation = holder.createInfoAnnotation(element, null);
          annotation.setGutterIconRenderer(new MyRenderer((XmlTag)element));
        }
      }
    }
  }

  /** Converts the supported color formats (#rgb, #argb, #rrggbb, #aarrggbb, or color name to Java color. */
  @Nullable
  @VisibleForTesting
  static Color parseColor(String s) {
    if (StringUtil.isEmpty(s)) {
      return null;
    }

    if (s.charAt(0) == '#') {
      long longColor;
      try {
        longColor = Long.parseLong(s.substring(1), 16);
      }
      catch (NumberFormatException e) {
        return null;
      }

      if (s.length() == 4 || s.length() == 5) {
        long a = s.length() == 4 ? 0xff : extend((longColor & 0xf000) >> 12);
        long r = extend((longColor & 0xf00) >> 8);
        long g = extend((longColor & 0x0f0) >> 4);
        long b = extend((longColor & 0x00f));
        longColor = (a << 24) | (r << 16) | (g << 8) | b;
        return new Color((int)longColor, true);
      }

      if (s.length() == 7) {
        longColor |= 0x00000000ff000000;
      }
      else if (s.length() != 9) {
        return null;
      }
      return new Color((int)longColor, true);
    }
    else {
      if (myColors == null) {
        initializeColors();
      }
      Integer intColor = myColors.get(s.toLowerCase(Locale.US));
      return intColor != null ? new Color(intColor) : null;
    }
  }

  private static long extend(long nibble) {
    return nibble | nibble << 4;
  }

  private static class MyRenderer extends GutterIconRenderer {
    private final XmlTag myElement;

    private MyRenderer(XmlTag element) {
      myElement = element;
    }

    @NotNull
    @Override
    public Icon getIcon() {
      final Color color = getCurrentColor();
      return color == null ? EmptyIcon.create(ICON_SIZE) : new ColorIcon(ICON_SIZE, color);
    }

    // see android.graphics.Color#parseColor in android.jar library
    @Nullable
    private Color getCurrentColor() {
      return parseColor(myElement.getValue().getText());
    }

    // see see android.graphics.Color in android.jar library
    private static String colorToString(Color color) {
      int intColor = (color.getAlpha() << 24) | (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();
      return '#' + Integer.toHexString(intColor);
    }

    @Override
    public AnAction getClickAction() {
      return new AnAction() {
        @Override
        public void actionPerformed(AnActionEvent e) {
          final Editor editor = PlatformDataKeys.EDITOR.getData(e.getDataContext());
          if (editor != null) {
            final Color color =
              ColorChooser.chooseColor(editor.getComponent(), AndroidBundle.message("android.choose.color"), getCurrentColor());
            if (color != null) {
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                @Override
                public void run() {
                  myElement.getValue().setText(colorToString(color));
                }
              });
            }
          }
        }
      };
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      MyRenderer that = (MyRenderer)o;

      if (myElement != null ? !myElement.equals(that.myElement) : that.myElement != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myElement != null ? myElement.hashCode() : 0;
    }
  }
}
