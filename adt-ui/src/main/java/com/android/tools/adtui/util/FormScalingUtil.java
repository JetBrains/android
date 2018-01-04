/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.adtui.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.AbstractLayout;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.util.containers.Stack;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * Helper class used to scale UI elements defined in <code>.form</code> files according to the current
 * HiDPI scaling factor. This class includes logic to skip over elements that have previously been
 * scaled.
 *
 * <p>
 * Note that the {@link #skipComponent} method should handle components that already scale correctly,
 * it may need to be updated as this class is more widely used.
 *
 * <p>
 * <em>Implementation note:</em>
 * Change the <code>DEBUG</code> value to <code>true</code> to enable logging of activity, i.e. logging
 * the name and context of the various properties that are scaled.
 */
public class FormScalingUtil {
  private static final boolean DEBUG = false;
  private static final Logger LOG = Logger.getInstance(FormScalingUtil.class);

  /**
   * The key used to mark a component (and its subtree) as scaled. Used to avoid
   * double scaling if a component appears in more than one call to
   * <code>scaleComponentTree</code>.
   */
  private static final String SCALE_FACTOR_KEY = "hidpi.scale.factor";

  /**
   * The stack of component/property names as we traverse the tree. Used for logging if <code>DEBUG</code> is <code>true</code>
   */
  private final Stack<String> myStack = new Stack<String>();
  /**
   * The class associated to the .form file, used for logging only.
   */
  @SuppressWarnings("FieldCanBeLocal")  // non-local if DEBUG is changed to true
  private final Class myClazz;
  /**
   * The scaling factor to apply to the components.
   */
  private final float myScaleFactor;
  /**
   * The number of values scaled, used for logging only.
   */
  private int myScaledValueCount = 0;

  /**
   * Constructs a <code>FormScalingUtil</code> for the given <code>.form</code> class
   * using the default <code>JBUI</code> scaling factor.
   *
   * @param clazz the class associated to the <code>.form</code> file
   */
  public FormScalingUtil(Class clazz) {
    this(clazz, JBUI.scale(1.0f));
  }

  /**
   * Constructs a <code>FormScalingUtil</code> for the given <code>.form</code> class
   * using a explicit scaling factor.
   *
   * @param clazz the class associated to the <code>.form</code> file
   * @param scaleFactor the scaling factor to apply
   */
  public FormScalingUtil(Class clazz, float scaleFactor) {
    this.myClazz = clazz;
    this.myScaleFactor = scaleFactor;
  }

  /**
   * Convenience method to construct a new instance of <code>FormScalingUtil</code> and apply
   * the default scaling factor to the given component tree.
   *
   * @param clazz the class associated to the <code>.form</code> file
   * @param root the root component of the component tree to scale
   */
  public static void scaleComponentTree(Class clazz, JComponent root) {
    new FormScalingUtil(clazz).scaleComponentTree(root);
  }

  /**
   * Apply the scaling factor to the component tree starting at <code>root</code>
   *
   * @param root the root component of the component tree to scale
   */
  public void scaleComponentTree(JComponent root) {
    if (myScaleFactor == 1.0f)
      return;

    if (DEBUG) LOG.info("Scaling components from \"" + myClazz.getSimpleName() + "\"");
    scaleComponentTreeWorker(root);
    if (DEBUG) LOG.info("Done scaling components from \"" + myClazz.getSimpleName() + "\": " + myScaledValueCount + " values scaled");

    // Mark the component (implicitly the whole tree) as scaled so that it won't be scaled
    // again if re-appearing in a subtree in a later call.
    root.putClientProperty(SCALE_FACTOR_KEY, new Float(myScaleFactor));
  }

  /**
   * Some components already scale with the scaling factor. This method returns <code>true</code> for
   * such components.
   *
   * @param c the candidate component
   * @return <code>true</code> if the component must be skipped, false otherwise.
   */
  protected boolean skipComponent(Component c) {
    if (c instanceof JComponent) {
      JComponent jComponent = (JComponent)c;
      if (jComponent.getClientProperty(SCALE_FACTOR_KEY) != null)
        return true;
    }

    // Special case: ComponentWithBrowseButton already automatically scales according
    // to the default font size if setTextFieldPreferredWidth has been called.
    if (c instanceof ComponentWithBrowseButton) {
      if (c.isPreferredSizeSet()) {
        return true;
      }
    }

    return false;
  }

  private void scaleComponentTreeWorker(Component c) {
    if (DEBUG) myStack.push(getComponentName(c));
    try {
      if (skipComponent(c))
        return;

      scaleComponent(c);
      if (c instanceof Container) {
        Container container = (Container)c;
        for (Component child : container.getComponents()) {
          scaleComponentTreeWorker(child);
        }
      }
    } finally {
      if (DEBUG) myStack.pop();
    }
  }

  private void scaleComponent(Component c) {
    if (c instanceof Container) {
      Container container = (Container)c;
      scaleLayoutManager(container.getLayout());
    }
    if (c instanceof JTable) {
      JTable table = (JTable)c;
      Dimension size = table.getPreferredScrollableViewportSize();
      if (size != null) {
        table.setPreferredScrollableViewportSize(scale(size, "preferredScrollableViewportSize"));
      }
    }
    if (c instanceof JSlider) {
      JSlider slider = (JSlider)c;
      // Sliders have default width/height that need to be scaled. By calling getPreferredSize, we
      // force the default size to be computed. It will then be scaled in this method below.
      if (!slider.isPreferredSizeSet()) {
        slider.setPreferredSize(slider.getPreferredSize());
      }
    }
    if (c instanceof JBLabel) {
      JBLabel label = (JBLabel)c;
      label.setIconTextGap(scale(label.getIconTextGap(), "IconTextGap"));
    }
    if (c instanceof JComponent) {
      JComponent component = (JComponent)c;
      Border scaledBorder = getScaledBorder(component, component.getBorder());
      if (scaledBorder != null) {
        component.setBorder(scaledBorder);
      }
    }
    if (c.isFontSet()) {
      // Special case: If we have a font smaller than the threshold for the given
      // scale factor, scale the font size.
      // This heuristics only handle a subset of font sizing, where the intent was
      // do set the font size to "small" in the .form file.
      float fontSize = c.getFont().getSize2D();
      float minFontSize = 9f * myScaleFactor; // We assume a size < 9 would be too small at any dpi setting.
      if (fontSize <= minFontSize) {
        c.setFont(c.getFont().deriveFont(scale(fontSize, "FontSize")));
      }
    }

    scaleMinimumSize(c);
    scaleMaximumSize(c);
    scalePreferredSize(c);

    if (c.getParent() != null &&
        c.getParent().getLayout() != null &&
        c.getParent().getLayout() instanceof AbstractLayout) {
      AbstractLayout abstractLayout = (AbstractLayout)c.getParent().getLayout();

      GridConstraints constraint = abstractLayout.getConstraintsForComponent(c);

      constraint.myPreferredSize.width = scale(constraint.myPreferredSize.width, "constraint.myPreferredSize.width");
      constraint.myPreferredSize.height = scale(constraint.myPreferredSize.height, "constraint.myPreferredSize.height");

      constraint.myMinimumSize.width = scale(constraint.myMinimumSize.width, "constraint.myMinimumSize.width");
      constraint.myMinimumSize.height = scale(constraint.myMinimumSize.height, "constraint.myMinimumSize.height");

      constraint.myMaximumSize.width = scale(constraint.myMaximumSize.width, "constraint.myMaximumSize.width");
      constraint.myMaximumSize.height = scale(constraint.myMaximumSize.height, "constraint.myMaximumSize.height");
    }
  }

  private void scaleMinimumSize(Component c) {
    if (c.isMinimumSizeSet()) {
      c.setMinimumSize(scale(c.getMinimumSize(), "MinimumSize"));
    }
  }

  private void scaleMaximumSize(Component c) {
    if (c.isMaximumSizeSet()) {
      c.setMaximumSize(scale(c.getMaximumSize(), "MaximumSize"));
    }
  }

  private void scalePreferredSize(Component c) {
    if (c.isPreferredSizeSet()) {
      c.setPreferredSize(scale(c.getPreferredSize(), "PreferredSize"));
    }
  }

  private void scaleLayoutManager(LayoutManager layout) {
    if (layout instanceof AbstractLayout) {
      AbstractLayout abstractLayout = (AbstractLayout)layout;

      abstractLayout.setVGap(scale(abstractLayout.getVGap(), "VGap"));
      abstractLayout.setHGap(scale(abstractLayout.getHGap(), "VGap"));
      abstractLayout.setMargin(scale(abstractLayout.getMargin(), "Margin"));
    }
  }

  private Border getScaledBorder(Component c, Border border) {
    if (border == null)
      return null;

    if (border.getClass() == EmptyBorder.class) {
      return new EmptyBorder(scale(border.getBorderInsets(c), "EmptyBorder"));
    }

    if (border instanceof TitledBorder) {
      TitledBorder titledBorder = (TitledBorder)border;
      if (DEBUG) myStack.push("TitleBorder");
      try {
        Border innerBorder = getScaledBorder(c, titledBorder.getBorder());
        if (innerBorder != null) {
          titledBorder.setBorder(innerBorder);
        }
      } finally {
        if (DEBUG) myStack.pop();
      }
    }

    return border;
  }

  private Dimension scale(Dimension dimension, String propertyName) {
    if (DEBUG) myStack.push(propertyName);
    try {
      return new Dimension(scale(dimension.width, "width"), scale(dimension.height, "height"));
    }
    finally {
      if (DEBUG) myStack.pop();
    }
  }

  private Insets scale(Insets insets, String propertyName){
    if (DEBUG) myStack.push(propertyName);
    try {
      return new Insets(scale(insets.top, "top"), scale(insets.left, "left"), scale(insets.bottom, "bottom"), scale(insets.right, "right"));
    }
    finally {
      if (DEBUG) myStack.pop();
    }
  }

  private int scale(int value, String propertyName) {
    if (DEBUG) myStack.push(propertyName);
    try {
      return scale(value);
    }
    finally {
      if (DEBUG) myStack.pop();
    }
  }

  private float scale(float value, String propertyName) {
    if (DEBUG) myStack.push(propertyName);
    try {
      return scale(value);
    }
    finally {
      if (DEBUG)  myStack.pop();
    }
  }

  private float scale(float value) {
    return scale((int)value);
  }

  private int scale(int value) {
    if (value <= 0)
      return value;
    long result = (long)(myScaleFactor * value);
    if (result >= Integer.MAX_VALUE)
      return Integer.MAX_VALUE;
    logScale(value, (int)result);
    myScaledValueCount++;
    return (int)result;
  }

  private void logScale(int value, int scaledValue) {
    if (DEBUG) LOG.info(String.format("%1$s = %2$d (from %3$d)", buildStackString(), scaledValue, value));
  }

  private static String getComponentName(Component c) {
    String name = c.getName();
    if (name == null)
      name = c.getClass().getSimpleName();
    return name;
  }

  private String buildStackString() {
    StringBuilder sb = new StringBuilder();
    for (String text : myStack) {
      if (sb.length() > 0)
        sb.append(" > ");
      sb.append(text);
    }
    return sb.toString();
  }
}
