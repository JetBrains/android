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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.HighlightedRegion;
import com.intellij.ui.HyperlinkLabel;
import org.fest.reflect.core.Reflection;
import org.fest.reflect.reference.TypeRef;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;
import java.awt.FontMetrics;
import java.awt.Point;
import java.util.ArrayList;

public class HyperlinkLabelFixture extends JComponentFixture<HyperlinkLabelFixture, HyperlinkLabel> {
  public HyperlinkLabelFixture(@NotNull Robot robot, @NotNull HyperlinkLabel target) {
    super(HyperlinkLabelFixture.class, robot, target);
  }

  public String getText() {
    return Reflection.method("getText").withReturnType(String.class).in(target()).invoke();
  }

  public int getTextOffset() {
    return Reflection.method("getTextOffset").withReturnType(int.class).in(target()).invoke();
  }

  public HyperlinkLabelFixture clickLink(@NotNull String linkText) {
    TextAttributes myAnchorAttributes = Reflection.field("myAnchorAttributes").ofType(TextAttributes.class).in(target()).get();
    ArrayList<HighlightedRegion> myHighlightedRegions = Reflection.field("myHighlightedRegions").ofType(new TypeRef<ArrayList<HighlightedRegion>>() {}).in(target()).get();

    HyperlinkLabel target = target();
    String fullText = getText();
    FontMetrics defFontMetrics = target.getFontMetrics(target.getFont());
    int offset = getTextOffset();

    if (fullText.length() != 0 && myHighlightedRegions.size() != 0) {
      int endIndex = 0;
      for (HighlightedRegion hRegion : myHighlightedRegions) {
        offset += defFontMetrics.stringWidth(fullText.substring(endIndex, hRegion.startOffset));

        String text = fullText.substring(hRegion.startOffset, hRegion.endOffset);
        FontMetrics fontMetrics = target.getFontMetrics(defFontMetrics.getFont().deriveFont(hRegion.textAttributes.getFontType()));
        int width = fontMetrics.stringWidth(text);

        if (text.equals(linkText) && hRegion.textAttributes == myAnchorAttributes) {
          robot().click(target, new Point(offset + width / 2, target.getHeight() / 2));
          return this;
        }

        endIndex = hRegion.endOffset;
        offset += width;
      }
    }
    throw new IllegalArgumentException("link not found " + linkText);
  }
}
