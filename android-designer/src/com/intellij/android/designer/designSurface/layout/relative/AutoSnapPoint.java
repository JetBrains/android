/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.android.designer.designSurface.layout.relative;

import com.android.SdkConstants;
import com.intellij.android.designer.AndroidDesignerUtils;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.designSurface.EditableArea;
import com.intellij.designer.designSurface.feedbacks.TextFeedback;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.xml.XmlTag;

import java.awt.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class AutoSnapPoint extends SnapPoint {
  private int myMargin; // in model dp
  private final EditableArea myArea;

  public AutoSnapPoint(EditableArea area, RadViewComponent container, boolean horizontal) {
    super(container, horizontal);
    myArea = area;
  }

  private String getAttribute() {
    return myHorizontal ? "alignParentLeft" : "alignParentTop";
  }

  @Override
  public void addTextInfo(TextFeedback feedback) {
    feedback.append(getAttribute(), SnapPointFeedbackHost.SNAP_ATTRIBUTES);
    if (myMargin > 0) {
      feedback.append(", margin" + (myHorizontal ? "Left " : "Top ") + Integer.toString(myMargin));
      feedback.dimension("dp");
    }
  }

  @Override
  public boolean processBounds(List<RadComponent> components, Rectangle bounds, SnapPointFeedbackHost feedback) {
    super.processBounds(components, bounds, feedback);

    double dpi = AndroidDesignerUtils.getDpi(myArea);
    RadViewComponent first = (RadViewComponent)components.get(0);
    Rectangle modelDpi = first.toModelDp(dpi, feedback, bounds);

    if (myHorizontal) {
      int viewMargin = bounds.x - myBounds.x;
      myMargin = first.toModelDp(dpi, feedback, new Dimension(viewMargin, 0)).width;
      feedback.addVerticalLine(myBounds.x, myBounds.y, myBounds.height);
      feedback.addHorizontalArrow(myBounds.x, bounds.y + bounds.height / 2, viewMargin);
    }
    else {
      int viewMargin = bounds.y - myBounds.y;
      myMargin = first.toModelDp(dpi, feedback, new Dimension(0, viewMargin)).height;
      feedback.addHorizontalLine(myBounds.x, myBounds.y, myBounds.width);
      feedback.addVerticalArrow(bounds.x + bounds.width / 2, myBounds.y, viewMargin);
    }

    return true;
  }

  @Override
  public void execute(final List<RadComponent> components) throws Exception {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        String attribute = "layout_" + getAttribute();
        String marginAttribute = null;
        String marginValue = null;

        if (myMargin > 0) {
          marginAttribute = myHorizontal ? "layout_marginLeft" : "layout_marginTop";
          marginValue = Integer.toString(myMargin) + "dp";
        }

        for (RadComponent component : components) {
          XmlTag tag = ((RadViewComponent)component).getTag();
          tag.setAttribute(attribute, SdkConstants.NS_RESOURCES, "true");
          if (marginValue != null) {
            tag.setAttribute(marginAttribute, SdkConstants.NS_RESOURCES, marginValue);
          }
        }
      }
    });
  }
}