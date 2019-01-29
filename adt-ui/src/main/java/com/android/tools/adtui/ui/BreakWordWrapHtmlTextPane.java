/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.adtui.ui;

import javax.swing.*;
import javax.swing.text.Element;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.*;
import java.awt.*;

/**
 * A {@link JTextPane} of HTML type whose text can be selected and can be wrapped even in the middle of words.
 */
public class BreakWordWrapHtmlTextPane extends JTextPane {

  public BreakWordWrapHtmlTextPane() {
    super();
    setContentType("text/html");
    setBackground(null);
    setBorder(null);
    setEditable(false);
    HTMLEditorKit editorKit = new BreakWordWrapHTMLEditorKit();
    StyleSheet styleSheet = editorKit.getStyleSheet();
    Font labelFont = UIManager.getFont("Label.font");
    styleSheet.addRule("body { font-family: " + labelFont.getFamily() + "; font-size: 13pt; }");
    styleSheet.addRule("p { margin: 4 0 4 0; }");
    setEditorKit(editorKit);
  }


  /**
   * Customized HTML editor kit for {@link JEditorPane}, which make words content can be wrapped in the middle of word instead of space.
   *
   * See <a href="http://java-sl.com/tip_html_letter_wrap.html">this article</a> for more details.
   */
  public static final class BreakWordWrapHTMLEditorKit extends HTMLEditorKit {

    @Override
    public ViewFactory getViewFactory() {
      return new HTMLFactory() {

        @Override
        public View create(Element e) {
          View v = super.create(e);
          if (v instanceof InlineView) {
            return new BreakWordInlineView(e);
          }
          else if (v instanceof ParagraphView) {
            return new BreakWordParagraphView(e);
          }
          return v;
        }
      };
    }
  }

  /**
   * Customized {@link InlineView}. See <a href="http://java-sl.com/tip_html_letter_wrap.html">this article</a> for more details.
   */
  private static final class BreakWordInlineView extends InlineView {
    public BreakWordInlineView(Element e) {
      super(e);
    }

    @Override
    public int getBreakWeight(int axis, float pos, float len) {
      return GoodBreakWeight;
    }

    @Override
    public View breakView(int axis, int p0, float pos, float len) {
      if (axis == View.X_AXIS) {
        checkPainter();
        int p1 = getGlyphPainter().getBoundedPosition(this, p0, pos, len);
        if (p0 == getStartOffset() && p1 == getEndOffset()) {
          return this;
        }
        return createFragment(p0, p1);
      }
      return this;
    }
  }

  /**
   * Customized {@link ParagraphView}. See <a href="http://java-sl.com/tip_html_letter_wrap.html">this article</a> for more details.
   */
  private static final class BreakWordParagraphView extends ParagraphView {
    public BreakWordParagraphView(Element e) {
      super(e);
    }

    @Override
    protected SizeRequirements calculateMinorAxisRequirements(int axis, SizeRequirements r) {
      if (r == null) {
        r = new SizeRequirements();
      }
      float pref = layoutPool.getPreferredSpan(axis);
      float min = layoutPool.getMinimumSpan(axis);
      // Don't include insets, Box.getXXXSpan will include them.
      r.minimum = (int)min;
      r.preferred = Math.max(r.minimum, (int)pref);
      r.maximum = Integer.MAX_VALUE;
      r.alignment = 0.5f;
      return r;
    }
  }
}
