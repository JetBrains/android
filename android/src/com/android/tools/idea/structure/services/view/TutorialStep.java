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
package com.android.tools.idea.structure.services.view;

import com.android.tools.idea.structure.services.DeveloperService;
import com.android.tools.idea.structure.services.datamodel.StepData;
import com.android.tools.idea.structure.services.datamodel.StepElementData;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Renders a single step inside of a tutorial.
 *
 * TODO: Move render properties to a form.
 */
public class TutorialStep extends JPanel {
  private static final Logger logger = Logger.getLogger(TutorialStep.class.getName());

  private final int myIndex;
  private final StepData myStep;
  private final JPanel myContents;

  TutorialStep(StepData step, int index, ActionListener listener, DeveloperService service) {
    myIndex = index;
    myStep = step;
    myContents = new JPanel();
    setOpaque(false);
    setLayout(new GridBagLayout());

    // TODO: Consider the setup being in the ctors of customer inner classes.
    initStepNumber();
    initLabel();
    initStepContentsContainer();

    for (StepElementData element : step.getStepElements()) {
      // element is a wrapping node to preserve order in a heterogeneous list,
      // hence switching over type.
      switch (element.getType()) {
        case SECTION:
          // TODO: Make a custom inner class to handle this.
          JTextPane section = new JTextPane();
          section.setOpaque(false);
          section.setBorder(BorderFactory.createEmptyBorder());
          UIUtils.setHtml(section, element.getSection());
          myContents.add(section);
          break;
        case ACTION:
          myContents.add(new StatefulButton(element.getAction(), listener, service));
          break;
        case CODE:
          CodePane code = new CodePane();
          code.setCode(element.getCode());
          NaturalHeightScrollPane codeScroller = new NaturalHeightScrollPane(code);
          myContents.add(codeScroller);
          break;
        default:
          logger.log(Level.SEVERE, "Found a StepElement of unknown type. " + element.toString());
      }
      // Add 10px spacing between elements.
      myContents.add(Box.createRigidArea(new Dimension(0, 10)));
    }
  }

  /**
   * Create and add the step label.
   */
  private void initLabel() {
    JLabel label = new JLabel(myStep.getLabel());
    Font font = label.getFont();
    Font boldFont = new Font(font.getFontName(), Font.BOLD, font.getSize());
    label.setFont(boldFont);

    GridBagConstraints c = new GridBagConstraints();
    c.gridx = 1;
    c.gridy = 0;
    c.weightx = 1;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.anchor = GridBagConstraints.NORTHWEST;
    c.insets = new Insets(7, 0, 10, 5);

    add(label, c);
  }

  /**
   * Configure and add the container holding the set of step elements.
   */
  private void initStepContentsContainer() {
    myContents.setLayout(new BoxLayout(myContents, BoxLayout.Y_AXIS));
    myContents.setOpaque(false);
    GridBagConstraints c = new GridBagConstraints();
    c.gridx = 1;
    c.gridy = 1;
    c.weightx = 1;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.anchor = GridBagConstraints.NORTHWEST;
    c.insets = new Insets(0, 0, 0, 5);

    add(myContents, c);
  }

  /**
   * Create and add the step number indicator. Note that this is a custom
   * display that surrounds the number with a circle thus has some tricky
   * display characteristics. It's unclear if a form can be leveraged for this.
   */
  private void initStepNumber() {
    // Get standard label font.
    Font font = new JLabel().getFont();
    JTextPane stepNumber = new JTextPane();
    stepNumber.setEditable(false);
    stepNumber.setText(myIndex + "");
    Font boldFont = new Font(font.getFontName(), Font.BOLD, 11);
    stepNumber.setFont(boldFont);
    stepNumber.setOpaque(false);
    stepNumber.setForeground(UIUtils.getLinkColor());
    stepNumber.setBorder(new NumberBorder());
    Dimension size = new Dimension(20, 20);
    stepNumber.setSize(size);
    stepNumber.setPreferredSize(size);
    stepNumber.setMinimumSize(size);
    stepNumber.setMaximumSize(size);

    StyledDocument doc = stepNumber.getStyledDocument();
    SimpleAttributeSet center = new SimpleAttributeSet();
    StyleConstants.setAlignment(center, StyleConstants.ALIGN_CENTER);
    doc.setParagraphAttributes(0, doc.getLength(), center, false);

    GridBagConstraints c = new GridBagConstraints();
    c.gridx = 0;
    c.gridy = 0;
    c.weightx = 0;
    c.fill = GridBagConstraints.NONE;
    c.anchor = GridBagConstraints.CENTER;
    c.insets = new Insets(5, 5, 5, 5);

    add(stepNumber, c);
  }

  /**
   * A custom border used to create a circle around a specifically sized step number.
   * TODO: Adjust values further to match specs.
   */
  class NumberBorder extends AbstractBorder {
    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
      Graphics2D g2 = (Graphics2D)g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      int r = height - 1;
      RoundRectangle2D round = new RoundRectangle2D.Float(x, y, width - 1, height - 1, r, r);
      Container parent = c.getParent();
      if (parent != null) {
        g2.setColor(UIUtils.getBackgroundColor());
        Area corner = new Area(new Rectangle2D.Float(x, y, width, height));
        corner.subtract(new Area(round));
        g2.fill(corner);
      }
      g2.setColor(UIUtils.getLinkColor());
      g2.draw(round);
      g2.dispose();
    }

    @Override
    public Insets getBorderInsets(Component c) {
      return new Insets(2, 2, 2, 2);
    }

    @Override
    public Insets getBorderInsets(Component c, Insets insets) {
      insets.left = insets.right = 2;
      insets.top = insets.bottom = 2;
      return insets;
    }
  }

  /**
   * A text pane designed to display code samples, this should live inside a
   * {@code NaturalHeightScrollPane} to render properly.
   *
   * TODO: While this generally avoids wrapping, it still does in some
   * scenario. Either fix via code properties or render the contents as html.
   */
  private class CodePane extends JTextPane {

    public CodePane() {
      super();
      setEditable(false);
      setOpaque(false);
      setMargin(new Insets(5, 5, 5, 5));
      setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
      setContentType("text/html");
    }

    // Always scroll horizontally, avoids text wrapping.
    @Override
    public boolean getScrollableTracksViewportWidth() {
      return false;
    }

    /**
     * Alternative to {@code setText} that formats appropriately as markup. Prior implementations as plain text did not honor wrapping
     * rules. Note that this is not overriding {@code setText} as {@code UIUtils.setHtml} calls that internally which would cause a loop.
     */
    public void setCode(String text) {
      // {@code escapeXml} is sufficient as we merely want to prevent the contents as being interpreted as html, not deal with the myriad of
      // html entities.
      UIUtils.setHtml(this, "<pre>" + StringUtil.escapeXml(text) + "</pre>", "pre {padding: 0 5px 0 5px;");
    }
  }

  /**
   * A scrollpane that uses natural height of the contents up to a max height.
   * This addresses issues where it reports the correct preferred size but
   * does not grow unless you set a minimum height.
   *
   * TODO: If reused, ensure all ctors init, max height is settable, etc.
   */
  private class NaturalHeightScrollPane extends JBScrollPane {
    private final static int MAX_HEIGHT = 500;

    public NaturalHeightScrollPane(Component view) {
      super(view);
      init();
    }

    private void init() {
      // TODO: If reused elsewhere, the border may not be appropriate.
      setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, UIUtils.getSeparatorColor()));
      setViewportBorder(BorderFactory.createEmptyBorder());
      setOpaque(false);
      getViewport().setOpaque(false);

      // TODO: Find a cleaner way to manage this display such that we don't
      // game the min height and preferred size.

      // Due to the scroll pane default rendering with 0 height regardless
      // of the preferred height of the child, override the minimum to be a
      // calculated value that is the natural height inside the bounds of a
      // maximum and minimum height.
      Dimension preferred = getViewport().getView().getPreferredSize();
      // Code typically overflows width and we need to account for the
      // scrollbar height.
      int trackHeight = getHorizontalScrollBar().getPreferredSize().height;
      int height = Math.min(MAX_HEIGHT, preferred.height + trackHeight);
      setMinimumSize(new Dimension(1, height));
      // Due to the encapsulating scroller using the sum of the preferred
      // heights of it's children to size the scroll size, reset the
      // preferred size to the actual size so that it doesn't allocate extra space.
      // Note that this changes the behavior in some cases as some
      setPreferredSize(new Dimension(preferred.width, height));
    }
  }
}
