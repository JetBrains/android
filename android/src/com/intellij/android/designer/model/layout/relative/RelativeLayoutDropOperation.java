/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.intellij.android.designer.model.layout.relative;

import com.intellij.android.designer.AndroidDesignerUtils;
import com.intellij.android.designer.designSurface.AbstractEditOperation;
import com.intellij.android.designer.designSurface.feedbacks.TextFeedback;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.designSurface.FeedbackLayer;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.android.SdkConstants.*;

public class RelativeLayoutDropOperation extends AbstractEditOperation {
  private GuidelinePainter myFeedback;
  private MoveHandler myMoveHandler;
  private RadViewComponent myPrimary;
  private MultiLineTooltipManager myTooltip;

  public RelativeLayoutDropOperation(RadComponent container, OperationContext context) {
    super(container, context);
  }

  @Override
  public void showFeedback() {
    FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
    List<RadViewComponent> viewComponents = RadViewComponent.getViewComponents(myComponents);

    if (myFeedback == null) {
      myMoveHandler = new MoveHandler((RadViewComponent)myContainer, viewComponents, myContext);

      myFeedback = new GuidelinePainter(myMoveHandler);
      layer.add(myFeedback);
      myFeedback.setBounds(0, 0, layer.getWidth(), layer.getHeight());
      myTooltip = new MultiLineTooltipManager(layer, 4);

      // If multiple components are selected, designate the one under the mouse as the primary
      myPrimary = viewComponents.get(0);
      Point location = myContext.getLocation();
      RadComponent pointedTo = myContext.getArea().findTarget(location.x, location.y, null);
      if (pointedTo instanceof RadViewComponent && myComponents.contains(pointedTo)) {
        myPrimary = (RadViewComponent)pointedTo;
      }
    }

    Point moveDelta = myContext.getMoveDelta();
    Rectangle newBounds = myPrimary.getBounds(layer);

    if (newBounds.width == 0 || newBounds.height == 0) {
      // Pasting, etc
      Dimension size = AndroidDesignerUtils.computePreferredSize(myContext.getArea(), myPrimary, myContainer);
      newBounds = new Rectangle(0, 0, size.width, size.height);
    }
    if (moveDelta != null) {
      newBounds.x += moveDelta.x;
      newBounds.y += moveDelta.y;
    } else {
      // Dropping from palette etc
      Point location = myContext.getLocation();
      newBounds.x = location.x - newBounds.width / 2;
      newBounds.y = location.y - newBounds.height / 2;
    }

    myMoveHandler.updateMove(myPrimary, newBounds, myContext.getModifiers());
    myFeedback.repaint();

    // Update the text
    describeMatch(myMoveHandler.getCurrentLeftMatch(), 0, myMoveHandler.getLeftMarginDp(), ATTR_LAYOUT_MARGIN_LEFT);
    describeMatch(myMoveHandler.getCurrentRightMatch(), 1, myMoveHandler.getRightMarginDp(), ATTR_LAYOUT_MARGIN_RIGHT);
    describeMatch(myMoveHandler.getCurrentTopMatch(), 2, myMoveHandler.getTopMarginDp(), ATTR_LAYOUT_MARGIN_TOP);
    describeMatch(myMoveHandler.getCurrentBottomMatch(), 3, myMoveHandler.getBottomMarginDp(), ATTR_LAYOUT_MARGIN_BOTTOM);

    // Position the tooltip
    Point location = myContext.getLocation();
    myTooltip.update((RadViewComponent) myContainer, location);
  }

  private void describeMatch(Match m, int line, int margin, String marginAttribute) {
    if (m == null) {
      myTooltip.setVisible(line, false);
      return;
    }

    myTooltip.setVisible(line, true);
    TextFeedback feedback = myTooltip.getFeedback(line);
    m.describe(feedback, margin, marginAttribute);
  }

  @Override
  public void eraseFeedback() {
    if (myFeedback != null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
      layer.remove(myFeedback);
      myTooltip.dispose();
      myFeedback = null;
      myTooltip = null;
      layer.repaint();
    }
  }

  @Override
  public void execute() throws Exception {
    if (myContext.isCreate() || myContext.isPaste() || myContext.isAdd()) {
      super.execute();
      // TODO: Return here?
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        myMoveHandler.removeCycles();

        RadViewComponent previous = null;
        for (RadViewComponent component : RadViewComponent.getViewComponents(myComponents)) {
          if (previous == null) {
            myMoveHandler.applyConstraints(component);
          } else {
            // Arrange the nodes next to each other, depending on which
            // edge we are attaching to. For example, if attaching to the
            // top edge, arrange the subsequent nodes in a column below it.
            //
            // TODO: Try to do something smarter here where we detect
            // constraints between the dragged edges, and we preserve these.
            // We have to do this carefully though because if the
            // constraints go through some other nodes not part of the
            // selection, this doesn't work right, and you might be
            // dragging several connected components, which we'd then
            // need to stitch together such that they are all visible.

            myMoveHandler.attachPrevious(previous, component);
          }
          previous = component;
        }
      }
    });
  }

  /**
   * Returns a list of valid constraint targets for the given source component. This
   * checks for cycles and ensures that only components that will not introduce a
   * cycle are selected (which is used to determine whether we are looking for
   * a horizontal cycle or a vertical cycle or in the case of center, both)
   *
   * @param attributeName the relative layout attribute
   * @param layout        the relative layout
   * @param sources       the sources component
   * @return a list of components you can attach to
   */
  @NotNull
  public static List<RadComponent> getValidTargets(String attributeName, RadViewComponent layout, List<RadViewComponent> sources) {
    List<RadComponent> result = new ArrayList<RadComponent>();
    ConstraintType constraintType = ConstraintType.fromAttribute(attributeName);
    assert constraintType != null : attributeName;
    DependencyGraph graph = DependencyGraph.get(layout);
    Set<RadViewComponent> horizontalDependencies = constraintType.horizontalEdge ? graph.dependsOn(sources, false /* verticalEdge */)
                                                                                 : Collections.<RadViewComponent>emptySet();
    Set<RadViewComponent> verticalDependencies = constraintType.verticalEdge ? graph.dependsOn(sources, true /* verticalEdge */)
                                                                               : Collections.<RadViewComponent>emptySet();

    for (RadViewComponent component : RadViewComponent.getViewComponents(layout.getChildren())) {
      if (sources.contains(component)) {
        continue;
      }
      if (horizontalDependencies.contains(component) || verticalDependencies.contains(component)) {
        continue;
      }
      result.add(component);
    }

    return result;
  }
}
