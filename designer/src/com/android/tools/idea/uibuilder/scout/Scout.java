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
package com.android.tools.idea.uibuilder.scout;

import com.android.tools.idea.common.command.NlWriteCommandAction;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static com.android.SdkConstants.*;

/**
 * Main entry for the Scout Inference engine.
 * All external access should be through this class
 * TODO support Stash / merge constraints ourConverts etc.
 */
public class Scout {
  private static final String[] ourAttrsToDelete = {
    ATTR_PADDING,
    ATTR_PADDING_LEFT,
    ATTR_PADDING_RIGHT,
    ATTR_PADDING_START,
    ATTR_PADDING_END,
    ATTR_PADDING_TOP,
    ATTR_PADDING_BOTTOM,
    ATTR_ORIENTATION
  };

  public enum Arrange {
    AlignVerticallyTop, AlignVerticallyMiddle, AlignVerticallyBottom, AlignHorizontallyLeft,
    AlignHorizontallyCenter, AlignHorizontallyRight, DistributeVertically,
    DistributeHorizontally, VerticalPack, HorizontalPack, ExpandVertically, AlignBaseline,
    ExpandHorizontally, CenterHorizontallyInParent, CenterVerticallyInParent, CenterVertically,
    CenterHorizontally, CreateHorizontalChain, CreateVerticalChain, ConnectTop, ConnectBottom, ConnectStart, ConnectEnd,
    ChainVerticalRemove, ChainHorizontalRemove, ChainVerticalMoveUp, ChainVerticalMoveDown, ChainHorizontalMoveLeft, ChainHorizontalMoveRight,
    ChainInsertHorizontal, ChainInsertVertical
  }

  private static int sMargin = 8;

  public static int getMargin() {
    return sMargin;
  }

  public static void setMargin(int margin) {
    sMargin = margin;
  }

  public static void arrangeWidgets(Arrange type, List<NlComponent> widgets,
                                    boolean applyConstraint) {
    switch (type) {
      case ChainVerticalRemove:
      case ChainHorizontalRemove:
      case ChainVerticalMoveUp:
      case ChainVerticalMoveDown:
      case ChainHorizontalMoveLeft:
      case ChainHorizontalMoveRight:
      case ChainInsertHorizontal:
      case ChainInsertVertical:
        ScoutChainsArrange.change(type, widgets);
        return;
      default:
        ScoutArrange.align(type, widgets, applyConstraint);
    }
  }

  /**
   * Provide catagories of test
   */
  public enum ChainTest {
    InVerticalChain,
    InHorizontalChain,
    IsTopOfChain,
    IsBottomOfChain,
    IsNearVerticalChain,
    IsNearHorizontalChain,
  }

  /**
   * This function can check a view for many critria (in enum)
   *
   * @param widgets
   * @param test
   * @return true if the widget meets the critria
   */
  public static boolean chainCheck(List<NlComponent> widgets, ChainTest test) {
    return ScoutChainsArrange.chainCheck(widgets, test);
  }

  /**
   * Detect if any component under the tree overlap.
   * inference does not work if views overlap.
   *
   * @param root parent of views to be tested
   * @return true if objects overlap
   */
  public static boolean containsOverlap(NlComponent root) {
    if (root == null) {
      return false;
    }
    if (root.getChildCount() == 0) {
      return false;
    }

    List<NlComponent> list = root.getChildren();
    int count = 0;
    Rectangle[] rec = new Rectangle[list.size()];
    for (NlComponent component : list) {
      rec[count] = new Rectangle();
      rec[count].x = ConstraintComponentUtilities.getDpX(component);
      rec[count].y = ConstraintComponentUtilities.getDpY(component);
      rec[count].width = ConstraintComponentUtilities.getDpWidth(component);
      rec[count].height = ConstraintComponentUtilities.getDpHeight(component);
      count++;
    }
    for (int i = 0; i < rec.length; i++) {
      Rectangle rectangle1 = rec[i];
      for (int j = i + 1; j < rec.length; j++) {
        Rectangle rectangle2 = rec[j];
        if (rectangle1.intersects(rectangle2)) {
          Rectangle r = rectangle1.intersection(rectangle2);
          if (r.width > 2 && r.height > 2) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public static void inferConstraints(List<NlComponent> components) {
    for (NlComponent component : components) {
      if (component.getParent() == null) {
        inferConstraints(component);
        return;
      }
    }
  }

  /**
   * Infer constraints will only set the attributes via a transaction; a separate
   * commit need to be done to save them.
   *
   * @param root the root element to infer from
   */
  public static void inferConstraintsFromConvert(NlComponent root) {
    inferConstraints(root, true, true);
  }

  /**
   * Infer constraints will only set the attributes via a transaction; a separate
   * commit need to be done to save them.
   *
   * @param root the root element to infer from
   */
  public static void inferConstraints(NlComponent root) {
    inferConstraints(root, true, false);
  }

  /**
   * Infer constraints will only set the attributes via a transaction; a separate
   * commit need to be done to save them.
   *
   * @param root
   * @param rejectOverlaps if true will not infer if views overlap
   */
  private static void inferConstraints(NlComponent root, boolean rejectOverlaps, boolean fromConvert) {
    if (root == null) {
      return;
    }
    for (NlComponent child : root.getChildren()) {
      child.ensureId();
    }
    if (!ConstraintComponentUtilities.isConstraintLayout(root)) {
      return;
    }
    if (rejectOverlaps && containsOverlap(root)) {
      System.err.println("containsOverlap!");
      return;
    }
    for (NlComponent constraintWidget : root.getChildren()) {
      if (ConstraintComponentUtilities.isConstraintLayout(constraintWidget)) {
        if (!constraintWidget.getChildren().isEmpty()) {
          inferConstraints(constraintWidget);
        }
      }
    }

    ArrayList<NlComponent> list = new ArrayList<>(root.getChildren());
    list.add(0, root);
    if (list.size() == 1) {
      return;
    }

    NlComponent[] widgets = list.toArray(new NlComponent[list.size()]);
    ScoutWidget[] scoutWidgets = ScoutWidget.create(widgets, fromConvert);
    ScoutWidget.computeConstraints(scoutWidgets);
    if (fromConvert) {
      postInferCleanupFromConvert(scoutWidgets);
    }
  }

  private static void postInferCleanupFromConvert(ScoutWidget[] widgets) {
    for (int i = 0; i < ourAttrsToDelete.length; i++) {
      widgets[0].mNlComponent.setAttribute(ANDROID_URI, ourAttrsToDelete[i], null);
    }

    for (int i = 1; i < widgets.length; i++) {
      ScoutWidget widget = widgets[i];
      if (widget.isCandidateResizable(0)) { // vertical

        if (!(widget.isConnected(Direction.TOP) && widget.isConnected(Direction.BOTTOM))) {
          widget.setVerticalDimensionBehaviour(ScoutWidget.DimensionBehaviour.FIXED);
        }
      }
      if (widget.isCandidateResizable(1)) { // horizontal

        if (!(widget.isConnected(Direction.LEFT) && widget.isConnected(Direction.RIGHT))) {
          widget.setHorizontalDimensionBehaviour(ScoutWidget.DimensionBehaviour.FIXED);
        }
      }
    }
  }

  public static void inferConstraintsAndCommit(List<NlComponent> components) {
    for (NlComponent component : components) {
      if (component.getParent() == null) {
        inferConstraintsAndCommit(component);
        return;
      }
    }
  }

  /**
   * Infer constraints and do a write commit of the attributes
   *
   * @param component the root element to infer from
   */
  public static void inferConstraintsAndCommit(NlComponent component) {
    inferConstraints(component, false, false);
    ArrayList<NlComponent> list = new ArrayList<>(component.getChildren());
    list.add(0, component);
    commit(list, "Infering constraints");
    evalResult(component);
  }

  /**
   * Evaluates the current constraint set
   *
   * @param component the root element to evaluate from
   */
  public static void evalResult(NlComponent component) {
    ArrayList<NlComponent> list = new ArrayList<>(component.getChildren());
    list.add(0, component);
    NlComponent[] widgets = list.toArray(new NlComponent[list.size()]);
    ScoutWidget[] scoutWidgets = ScoutWidget.create(widgets, false);
    ConstraintSet constraintSet = new ConstraintSet(scoutWidgets);
    if (constraintSet.validate()) {
      constraintSet.calculateError();
      System.out.println("Error in set (v1): " + Double.toString(constraintSet.error()));
    }
  }

  /**
   * Do a prioritized search in the constraint set space and commit
   * the set with minimum error that was found
   *
   * @param component the root element to infer from
   */
  public static void findConstraintSetAndCommit(NlComponent component) {
    ArrayList<NlComponent> list = new ArrayList<>(component.getChildren());
    list.add(0, component);
    if (list.size() == 1) {
      return;
    }
    //TODO: Handle nested constraint layouts
    NlComponent[] widgets = list.toArray(new NlComponent[list.size()]);
    ScoutWidget[] scoutWidgets = ScoutWidget.create(widgets, false);
    ConstraintSetGenerator generator = new ConstraintSetGenerator(scoutWidgets);
    ConstraintSet set = generator.findConstraintSet();
    set.applySet();
    System.out.println("Error in set (v2): " + Double.toString(set.error()));
    commit(list, "Infering constraints");
  }

  private static void commit(@NotNull List<NlComponent> list, String label) {
    if (list.isEmpty()) {
      return;
    }

    NlWriteCommandAction.run(list, label, () -> list.forEach(component -> component.startAttributeTransaction().commit()));
  }
}