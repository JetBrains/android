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
package com.android.tools.idea.uibuilder.handlers.relative;

import com.android.tools.idea.uibuilder.model.Segment;
import com.android.tools.idea.uibuilder.model.TextDirection;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

import static com.android.SdkConstants.*;

/**
 * A match is a potential pairing of two segments with a given {@link ConstraintType}.
 */
class Match {
  /**
   * the edge of the dragged node that is matched
   */
  public final Segment with;

  /**
   * the "other" edge that the dragged edge is matched with
   */
  public final Segment edge;

  /**
   * the signed distance between the matched edges
   */
  public final int delta;

  /**
   * the type of constraint this is a match for
   */
  public final ConstraintType type;

  /**
   * whether this {@link Match} results in a cycle
   */
  public boolean cycle;

  /**
   * Create a new match.
   *
   * @param edge    the "other" edge that the dragged edge is matched with
   * @param with    the edge of the dragged node that is matched
   * @param type    the type of constraint this is a match for
   * @param delta   the signed distance between the matched edges
   */
  public Match(Segment edge, Segment with, ConstraintType type, int delta) {
    this.edge = edge;
    this.with = with;
    this.type = type;
    this.delta = delta;
  }

  /**
   * Returns the XML constraint attribute value for this match
   *
   * @param generateId whether an id should be generated if one is missing
   * @return the XML constraint attribute value for this match
   */
  public String getConstraint(boolean generateId) {
    if (type.targetParent) {
      return type.name + '=' + VALUE_TRUE;
    }
    else {
      String id = edge.id;
      if (id == null || id.length() == -1) {
        if (!generateId) {
          // Placeholder to display for the user during dragging
          id = "<generated>";
        }
        else {
          // Must generate an id on the fly!
          // See if it's been set by a different constraint we've already applied
          // to this same node
          if (edge.component != null) {
            id = edge.component.ensureId();
          }
        }
      }
      return type.name + '=' + NEW_ID_PREFIX + id;
    }
  }

  @Override
  public String toString() {
    return "Match [type=" + type + ", delta=" + delta + ", edge=" + edge + "]";
  }

  /** Style to use when describing component names */
  @SuppressWarnings("UseJBColor") // The designer canvas is not using light/dark themes; colors match Android theme rendering
  private static final SimpleTextAttributes SNAP_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD,
                                                                                       new Color(60, 139, 186));

  /**
   * Describes this match in the given {@link SimpleColoredComponent}
   *
   * @param renderer        the feedback to write the description into
   * @param margin          the number of pixels to use as a margin
   * @param marginAttribute the name of the applicable margin attribute
   */
  public void describe(SimpleColoredComponent renderer, int margin, String marginAttribute) {
    // Display the constraint. Remove the @id/ and @+id/ prefixes to make the text
    // shorter and easier to read. This doesn't use stripPrefix() because the id is
    // usually not a prefix of the value (for example, 'layout_alignBottom=@+id/foo').
    String constraint = getConstraint(false /* generateId */);
    String description = constraint.replace(NEW_ID_PREFIX, "").replace(ID_PREFIX, "");
    description = StringUtil.trimStart(description, ATTR_LAYOUT_RESOURCE_PREFIX);
    // Instead of "alignParentLeft=true", just display "alignParentLeft"
    description = StringUtil.trimEnd(description, "=true");
    renderer.append(description, SNAP_ATTRIBUTES);
    if (margin > 0) {
      renderer.append(String.format(", margin=%1$d dp", margin));
    }
  }

  @Nullable
  public String getRtlConstraint(TextDirection textDirection, boolean generateId) {
    switch (type) {
      case ALIGN_LEFT:
        return replaceAttribute(getConstraint(generateId), ATTR_LAYOUT_ALIGN_LEFT, textDirection.getAttrLeft());
      case LAYOUT_LEFT_OF:
        return replaceAttribute(getConstraint(generateId), ATTR_LAYOUT_TO_LEFT_OF, textDirection.getAttrLeftOf());
      case ALIGN_RIGHT:
        return replaceAttribute(getConstraint(generateId), ATTR_LAYOUT_ALIGN_RIGHT, textDirection.getAttrRight());
      case LAYOUT_RIGHT_OF:
        return replaceAttribute(getConstraint(generateId), ATTR_LAYOUT_TO_RIGHT_OF, textDirection.getAttrRightOf());
      case ALIGN_PARENT_LEFT:
        return replaceAttribute(getConstraint(generateId), ATTR_LAYOUT_ALIGN_PARENT_LEFT, textDirection.getAttrAlignParentLeft());
      case ALIGN_PARENT_RIGHT:
        return replaceAttribute(getConstraint(generateId), ATTR_LAYOUT_ALIGN_PARENT_RIGHT, textDirection.getAttrAlignParentRight());
    }
    return null;
  }

  private static String replaceAttribute(String s, String oldName, String newName) {
    assert s.startsWith(oldName) : s;
    return newName + s.substring(oldName.length());
  }
}
