/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X;
import static com.android.SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y;
import static com.android.SdkConstants.ATTR_LAYOUT_END_TO_END_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_END_TO_START_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_START_TO_END_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_START_TO_START_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF;
import static com.android.SdkConstants.ATTR_PARENT;
import static com.android.SdkConstants.NEW_ID_PREFIX;
import static com.android.SdkConstants.SHERPA_URI;
import static com.android.SdkConstants.TOOLS_URI;

import com.android.tools.idea.common.command.NlWriteCommandActionUtil;
import com.android.tools.idea.common.model.AttributesTransaction;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

/**
 * Class used to implement manipulation of chains
 * Its primary interface is through a static method "change()"
 * The class itself is created to perform more complex operations on the chain
 */
public class ScoutChainsArrange {
  private List<NlComponent> myChain = new ArrayList<>();
  private boolean myVertical = false;
  private String attr_a_to_a = myVertical ? ATTR_LAYOUT_TOP_TO_TOP_OF : ATTR_LAYOUT_START_TO_START_OF;
  private String attr_a_to_b = myVertical ? ATTR_LAYOUT_TOP_TO_BOTTOM_OF : ATTR_LAYOUT_START_TO_END_OF;
  private String attr_b_to_a = myVertical ? ATTR_LAYOUT_BOTTOM_TO_TOP_OF : ATTR_LAYOUT_END_TO_START_OF;
  private String attr_b_to_b = myVertical ? ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF : ATTR_LAYOUT_END_TO_END_OF;
  private String myHeadType; // a_to_a or a_to_b
  private String myTailType; // b_to_a or b_to_b

  private String myHeadID; // where the head (top/left) of the myChain connects to
  private String myTailID; // where the tail (bottom/right) of the myChain connects to

  private void setVertical(boolean vertical) {
    myVertical = vertical;
    attr_a_to_a = myVertical ? ATTR_LAYOUT_TOP_TO_TOP_OF : ATTR_LAYOUT_START_TO_START_OF;
    attr_a_to_b = myVertical ? ATTR_LAYOUT_TOP_TO_BOTTOM_OF : ATTR_LAYOUT_START_TO_END_OF;
    attr_b_to_a = myVertical ? ATTR_LAYOUT_BOTTOM_TO_TOP_OF : ATTR_LAYOUT_END_TO_START_OF;
    attr_b_to_b = myVertical ? ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF : ATTR_LAYOUT_END_TO_END_OF;
  }

  private ScoutChainsArrange(NlComponent component, boolean vertical) {
    setVertical(vertical);
    setup(component);
  }

  /**
   * Build up a the myChain list which is an ordered top to bottom or left to right.
   * This is then used in inserts etc.
   *
   * @param component
   */
  private void setup(NlComponent component) {
    NlComponent c = getTopInChain(component);
    myChain.add(c);
    while (c != null) {
      String belowString = getAttr(c, attr_b_to_a);
      if (belowString == null) {
        myTailType = attr_b_to_b; // if attr_b_to_a is null must be b_to_b which means end of myChain
        myTailID = getAttr(c, attr_b_to_b);
        break;
      }
      NlComponent next_c = get(c, attr_b_to_a);
      if (c != get(next_c, attr_a_to_b)) {
        myTailType = attr_b_to_a; // if next does not point to me end of myChain
        myTailID = belowString;
        break;
      }
      c = next_c;
      myChain.add(c);
    }
  }

  /**
   * Walk up/left till the you reach the first element in the chain and return it.
   *
   * @param component
   * @return
   */
  private NlComponent getTopInChain(NlComponent component) {
    NlComponent c = component;
    while (true) {
      String aboveString = getAttr(c, attr_a_to_b);
      if (aboveString == null) {
        myHeadID = getAttr(c, attr_a_to_a);
        myHeadType = attr_a_to_a;
        return c;
      }
      NlComponent above = getComponent(c, aboveString);
      if (c != get(above, attr_b_to_a)) {
        myHeadType = attr_a_to_b;
        myHeadID = aboveString;
        return c;
      }
      c = above;
    }
  }

  /**
   * move a component up/left the Chain
   *
   * @param component
   * @return
   */
  private HashSet<AttributesTransaction> moveUp(NlComponent component) {
    HashSet<AttributesTransaction> transactions;
    int k = myChain.indexOf(component);
    if (k == 0) {
      return null;
    }
    transactions = removeComponent(component);
    transactions.addAll(insert(component, k - 1));
    return transactions;
  }

  /**
   * move a component down/right the Chain
   *
   * @param component
   * @return
   */
  private HashSet<AttributesTransaction> moveDown(NlComponent component) {
    HashSet<AttributesTransaction> transactions;
    int k = myChain.indexOf(component);
    if (k == myChain.size() - 1) {
      return null;
    }
    transactions = removeComponent(component);
    transactions.addAll(insert(component, k + 1));
    return transactions;
  }

  /**
   * Insert a component in a chain at a postion
   * There are 3 diffrent types of inserts start of chain, middle of chain, and end of chain
   *
   * @param component
   * @param i         where to insert
   * @return
   */
  private HashSet<AttributesTransaction> insert(NlComponent component, int i) {
    HashSet<AttributesTransaction> transactions = new HashSet<>();
    if (i == 0) { // first
      NlComponent after = myChain.get(i);
      String previousConnectString;
      String previousIdString = getAttr(after, previousConnectString = attr_a_to_b);
      if (previousIdString == null) {
        previousIdString = getAttr(after, previousConnectString = attr_a_to_a);
      }
      transactions.add(connect(component, previousConnectString, previousIdString));
      transactions.add(connect(component, attr_b_to_a, after));
      transactions.add(connect(after, attr_a_to_b, component));
      if (!attr_a_to_b.equals(previousConnectString)) {
        transactions.add(disconnect(after, previousConnectString));
      }
    }
    else if (i == myChain.size()) { // end
      NlComponent before = myChain.get(i - 1);
      String nextConnectString;
      String nextIdString = getAttr(before, nextConnectString = attr_b_to_a);
      if (nextIdString == null) {
        nextIdString = getAttr(before, nextConnectString = attr_b_to_b);
      }
      transactions.add(connect(component, nextConnectString, nextIdString));
      transactions.add(connect(before, attr_b_to_a, component));
      transactions.add(connect(component, attr_a_to_b, before));
      if (!attr_b_to_a.equals(nextConnectString)) {
        transactions.add(disconnect(before, nextConnectString));
      }
    }
    else { // middle

      NlComponent before = myChain.get(i - 1);
      String nextConnectString;
      String nextIdString = getAttr(before, nextConnectString = attr_b_to_a);
      if (nextIdString == null) {
        nextIdString = getAttr(before, nextConnectString = attr_b_to_b);
      }
      transactions.add(connect(component, nextConnectString, nextIdString));
      transactions.add(connect(before, attr_b_to_a, component));

      NlComponent after = myChain.get(i);
      String previousConnectString;
      String previousIdString = getAttr(after, previousConnectString = attr_a_to_b);
      if (previousIdString == null) {
        previousIdString = getAttr(after, previousConnectString = attr_a_to_a);
      }
      transactions.add(connect(component, previousConnectString, previousIdString));
      transactions.add(connect(after, attr_a_to_b, component));
    }
    return transactions;
  }

  /**
   * Remove a component form a chain (reforming the cain around it
   *
   * @param component
   * @return
   */
  private HashSet<AttributesTransaction> removeComponent(NlComponent component) {
    HashSet<AttributesTransaction> transactions = new HashSet<>();
    int k = myChain.indexOf(component);
    String previousConnectString;
    String previousIdString = getAttr(component, previousConnectString = attr_a_to_b);
    if (previousIdString == null) {
      previousIdString = getAttr(component, previousConnectString = attr_a_to_a);
    }
    String nextConnectString;
    String nextIdString = getAttr(component, nextConnectString = attr_b_to_a);
    if (nextIdString == null) {
      nextIdString = getAttr(component, nextConnectString = attr_b_to_b);
    }
    if (k > 0) {
      NlComponent before = myChain.get(k - 1);
      transactions.add(connect(before, nextConnectString, nextIdString));
      transactions.add(disconnect(before, myVertical, attr_b_to_b.equals(nextConnectString) ? attr_b_to_a : attr_b_to_b));
    }
    if (k + 1 < myChain.size()) {
      NlComponent after = myChain.get(k + 1);
      transactions.add(connect(after, previousConnectString, previousIdString));
      transactions.add(disconnect(after, myVertical, attr_a_to_a.equals(previousConnectString) ? attr_a_to_b : attr_a_to_a));
    }
    transactions.add(disconnect(component, myVertical, attr_a_to_a,
                                attr_a_to_b,
                                attr_b_to_a,
                                attr_b_to_b));
    myChain.remove(component);
    return transactions;
  }

  /**
   * The main entry point for the chain code
   * It performs the minuplation based on the selected NLcomponent
   *
   * @param type
   * @param widgets the selected NLcomponents
   */
  public static void change(Scout.Arrange type, List<NlComponent> widgets) {
    if (widgets.isEmpty()) {
      return;
    }
    switch (type) {
      case ChainVerticalRemove:
        chainRemove(widgets, true);
        break;
      case ChainHorizontalRemove:
        chainRemove(widgets, false);
        break;
      case ChainVerticalMoveUp:
        chainMoveUp(widgets, true);
        break;
      case ChainVerticalMoveDown:
        chainMoveDown(widgets, true);
        break;
      case ChainHorizontalMoveLeft:
        chainMoveUp(widgets, false);
        break;
      case ChainHorizontalMoveRight:
        chainMoveDown(widgets, false);
        break;
      case ChainInsertHorizontal:
        insertInChain(widgets.get(0), false);
        break;
      case ChainInsertVertical:
        insertInChain(widgets.get(0), true);
        break;
      default:
    }
  }


  private static void insertInChain(NlComponent component, boolean vertical) {
    NlComponent parent = component.getParent();
    if (parent == null) {
      return;
    }
    List<NlComponent> children = parent.getChildren();
    int distSqr = Integer.MAX_VALUE;
    if (isInChain(component, vertical)) {
      return;
    }
    NlComponent minChild = null;
    int centerX = anchorPosX(component, Direction.TOP);
    int centerY = anchorPosY(component, Direction.LEFT);
    for (NlComponent child : children) {
      if (isInChain(child, vertical)) {
        int d = distance(child, centerX, centerY, vertical);
        if (distSqr > d) {
          distSqr = d;
          minChild = child;
        }
        distSqr = Math.min(distSqr, d);
      }
    }
    boolean after = false;
    if (vertical) {
      if (centerY > anchorPosY(minChild, Direction.LEFT)) { // count on left anchor being at mid hight
        after = true;
      }
    }
    else {
      if (centerX > anchorPosX(minChild, Direction.LEFT)) { // count on left anchor being at mid hight
        after = true;
      }
    }

    if (minChild != null) {
      final HashSet<AttributesTransaction> transactions = new HashSet<>();
      ScoutChainsArrange a = new ScoutChainsArrange(minChild, vertical);
      int pos = 0;
      for (int i = 0; i < a.myChain.size(); i++) {
        if (a.myChain.get(i) == minChild) {
          pos = after ? i + 1 : i;
        }
      }
      transactions.addAll(a.insert(component, pos));
      commitAll(transactions, "Chain Move");
    }
  }

  /**
   * detect if any item in list is in a vertical or horizontal chain
   *
   * @param list
   * @param vertical
   * @return
   */
  public static boolean isInChain(List<NlComponent> list, boolean vertical) {
    if (vertical) {
      for (NlComponent component : list) {

        if (ConstraintComponentUtilities
              .isInChain(ConstraintComponentUtilities.ourBottomAttributes,
                         ConstraintComponentUtilities.ourTopAttributes, component) ||
            ConstraintComponentUtilities.isInChain(ConstraintComponentUtilities.ourTopAttributes,
                                                   ConstraintComponentUtilities.ourBottomAttributes, component)) {
          return true;
        }
      }
    }
    else {
      for (NlComponent component : list) {
        if (ConstraintComponentUtilities
              .isInChain(ConstraintComponentUtilities.ourStartAttributes, ConstraintComponentUtilities.ourEndAttributes, component)
            || ConstraintComponentUtilities
              .isInChain(ConstraintComponentUtilities.ourEndAttributes, ConstraintComponentUtilities.ourStartAttributes, component)) {
          return true;
        }
      }
    }
    return false;
  }


  /**
   * Detect if a single component is in a chain
   *
   * @param component
   * @param vertical
   * @return
   */
  public static boolean isInChain(NlComponent component, boolean vertical) {
    if (vertical) {
      return ConstraintComponentUtilities
               .isInChain(ConstraintComponentUtilities.ourBottomAttributes,
                          ConstraintComponentUtilities.ourTopAttributes, component) ||
             ConstraintComponentUtilities.isInChain(ConstraintComponentUtilities.ourTopAttributes,
                                                    ConstraintComponentUtilities.ourBottomAttributes, component);
    }
    return ConstraintComponentUtilities
             .isInChain(ConstraintComponentUtilities.ourStartAttributes, ConstraintComponentUtilities.ourEndAttributes, component)
           || ConstraintComponentUtilities
             .isInChain(ConstraintComponentUtilities.ourEndAttributes, ConstraintComponentUtilities.ourStartAttributes, component);
  }

  /**
   * Detecte if element is the element at the top of chain
   *
   * @param component
   * @param vertical
   * @return
   */
  private static boolean isTopInChain(NlComponent component, boolean vertical) {
    String attr_a_to_b = vertical ? ATTR_LAYOUT_TOP_TO_BOTTOM_OF : ATTR_LAYOUT_START_TO_END_OF;
    String attr_b_to_a = vertical ? ATTR_LAYOUT_BOTTOM_TO_TOP_OF : ATTR_LAYOUT_END_TO_START_OF;

    String aboveString = getAttr(component, attr_a_to_b);
    if (aboveString == null) {
      return true;
    }
    NlComponent above = getComponent(component, aboveString);
    if (component != get(above, attr_b_to_a)) {
      return true;
    }
    return false;
  }

  /**
   * Detect if element is at the bottom of a chain
   *
   * @param component
   * @param vertical
   * @return
   */
  private static boolean isBottomInChain(NlComponent component, boolean vertical) {
    String attr_a_to_b = vertical ? ATTR_LAYOUT_TOP_TO_BOTTOM_OF : ATTR_LAYOUT_START_TO_END_OF;
    String attr_b_to_a = vertical ? ATTR_LAYOUT_BOTTOM_TO_TOP_OF : ATTR_LAYOUT_END_TO_START_OF;

    String belowString = getAttr(component, attr_b_to_a);
    if (belowString == null) {
      return true;
    }
    NlComponent above = getComponent(component, belowString);
    if (component != get(above, attr_a_to_b)) {
      return true;
    }
    return false;
  }

  /**
   * performs check use to decide which capabilities can be enabled
   * The must match the actions to some extent (e.g. insertInChain depends on IsNearVerticalChain)
   *
   * @param widgets
   * @param test
   * @return
   */
  public static boolean chainCheck(List<NlComponent> widgets, Scout.ChainTest test) {
    NlComponent component = widgets.get(0);
    switch (test) {

      case InVerticalChain:
        return isInChain(widgets, true);
      case InHorizontalChain:
        return isInChain(widgets, false);
      case IsTopOfChain:
        if (isInChain(component, true)) {
          return isTopInChain(component, true);
        }
        if (isInChain(component, false)) {
          return isTopInChain(component, false);
        }
        return false;
      case IsBottomOfChain:
        if (isInChain(component, true)) {
          return isBottomInChain(component, true);
        }
        if (isInChain(component, false)) {
          return isBottomInChain(component, false);
        }
        return false;
      case IsNearVerticalChain:
        return (distanceToChain(widgets.get(0), true) < 2000);
      case IsNearHorizontalChain:
        return (distanceToChain(widgets.get(0), false) < 2000);
    }
    return true;
  }

  /**
   * Get the x position of a component's anchor
   *
   * @param component
   * @param dir
   * @return
   */
  private static int anchorPosX(NlComponent component, Direction dir) {
    switch (dir) {

      case TOP:
      case BOTTOM:
        return ConstraintComponentUtilities.getDpX(component) + ConstraintComponentUtilities.getDpWidth(component) / 2;

      case LEFT:
        return ConstraintComponentUtilities.getDpX(component);

      case RIGHT:
        return ConstraintComponentUtilities.getDpX(component) + ConstraintComponentUtilities.getDpWidth(component);

      case BASELINE:
    }
    return 0;
  }

  /**
   * Get the y position of a component's anchor
   *
   * @param component
   * @param dir
   * @return
   */
  private static int anchorPosY(NlComponent component, Direction dir) {
    switch (dir) {

      case TOP:
        return ConstraintComponentUtilities.getDpY(component);
      case BOTTOM:
        return ConstraintComponentUtilities.getDpY(component) + ConstraintComponentUtilities.getDpHeight(component);
      case LEFT:
      case RIGHT:
        return ConstraintComponentUtilities.getDpY(component) + ConstraintComponentUtilities.getDpHeight(component) / 2;
      case BASELINE:
    }
    return 0;
  }

  /**
   * Calculate the distanc from an xy coordnate to the anchors of a component
   *
   * @param c
   * @param x
   * @param y
   * @param vertical
   * @return
   */
  public static int distance(NlComponent c, int x, int y, boolean vertical) {
    int x1, y1;
    int dist;
    if (vertical) {
      x1 = anchorPosX(c, Direction.TOP);
      y1 = anchorPosY(c, Direction.TOP);
      dist = (x1 - x) * (x1 - x) + (y1 - y) * (y1 - y);
      x1 = anchorPosX(c, Direction.BOTTOM);
      y1 = anchorPosY(c, Direction.BOTTOM);
      dist = Math.min(dist, (x1 - x) * (x1 - x) + (y1 - y) * (y1 - y));
    }
    else {
      x1 = anchorPosX(c, Direction.LEFT);
      y1 = anchorPosY(c, Direction.LEFT);
      dist = (x1 - x) * (x1 - x) + (y1 - y) * (y1 - y);
      x1 = anchorPosX(c, Direction.RIGHT);
      y1 = anchorPosY(c, Direction.RIGHT);
      dist = Math.min(dist, (x1 - x) * (x1 - x) + (y1 - y) * (y1 - y));
    }
    return dist;
  }

  /**
   * calculate the distance to the nearest chain
   *
   * @param component
   * @param vertical
   * @return
   */
  private static int distanceToChain(NlComponent component, boolean vertical) {
    NlComponent parent = component.getParent();
    if (parent == null) {
      return Integer.MAX_VALUE;
    }
    List<NlComponent> children = parent.getChildren();
    int distSqr = Integer.MAX_VALUE;
    if (isInChain(component, vertical)) {
      return distSqr; // if we are already in return false
    }
    int centerX = anchorPosX(component, Direction.TOP);
    int centerY = anchorPosY(component, Direction.LEFT);
    for (NlComponent child : children) {
      if (isInChain(child, vertical)) {
        int d = distance(child, centerX, centerY, vertical);
        distSqr = Math.min(distSqr, d);
      }
    }
    return distSqr;
  }

  /**
   * Move an element down the chain. (use with more than one compoent my have unexpected effects.)
   *
   * @param components
   * @param vertical
   */
  private static void chainMoveDown(List<NlComponent> components, boolean vertical) {
    final HashSet<AttributesTransaction> transactions = new HashSet<>();
    for (NlComponent component : components) {
      ScoutChainsArrange a = new ScoutChainsArrange(component, vertical);
      transactions.addAll(a.moveDown(component));
    }
    if (transactions.isEmpty()) {
      return;
    }

    commitAll(transactions, "Chain Move");
  }

  /**
   * Move an element up the chain. (use with more than one compoent my have unexpected effects.)
   *
   * @param components
   * @param vertical
   */
  private static void chainMoveUp(List<NlComponent> components, boolean vertical) {
    final HashSet<AttributesTransaction> transactions = new HashSet<>();
    for (NlComponent component : components) {
      ScoutChainsArrange a = new ScoutChainsArrange(component, vertical);
      transactions.addAll(a.moveUp(component));
    }
    if (transactions.isEmpty()) {
      return;
    }

    commitAll(transactions, "Chain Move");
  }

  /**
   * Remove component from chain
   *
   * @param components
   * @param vertical
   */
  private static void chainRemove(List<NlComponent> components, boolean vertical) {
    final HashSet<AttributesTransaction> transactions = new HashSet<>();
    for (NlComponent component : components) {
      ScoutChainsArrange a = new ScoutChainsArrange(component, vertical);
      transactions.addAll(a.removeComponent(component));
    }

    commitAll(transactions, "Chain Move");
  }

  private static void commitAll(@NotNull Collection<AttributesTransaction> transactions, @NotNull String name) {
    List<NlComponent> components = transactions.stream()
      .map(AttributesTransaction::getComponent)
      .collect(Collectors.toList());

    NlWriteCommandActionUtil.run(components, name, () -> transactions.forEach(AttributesTransaction::commit));
  }

  /**
   * connect one component to the id of another
   * For some manipulations it is more efficent to do it with strings
   *
   * @param component
   * @param dir
   * @param to
   * @return
   */
  private static AttributesTransaction connect(NlComponent component, String dir, String to) {
    AttributesTransaction trans = component.startAttributeTransaction();
    trans.setAttribute(SHERPA_URI, dir, to);
    trans.apply();
    return trans;
  }

  /**
   * simple disconnect from single attribute
   *
   * @param component
   * @param dir
   * @return the transaction to be commited
   */
  private static AttributesTransaction disconnect(NlComponent component, String dir) {
    AttributesTransaction trans = component.startAttributeTransaction();
    trans.removeAttribute(SHERPA_URI, dir);
    trans.apply();
    return trans;
  }

  /**
   * connect one component to another
   *
   * @param component
   * @param dir
   * @param to
   * @return the transaction to be commited
   */
  private static AttributesTransaction connect(NlComponent component, String dir, NlComponent to) {
    AttributesTransaction trans = component.startAttributeTransaction();
    trans.setAttribute(SHERPA_URI, dir, NEW_ID_PREFIX + to.getId());
    trans.apply();
    return trans;
  }

  /**
   * disconnect from constraints filling in the absolute position
   *
   * @param component
   * @param vertical
   * @param dir
   * @return the transaction to be commited
   */
  private static AttributesTransaction disconnect(NlComponent component, boolean vertical, String... dir) {
    AttributesTransaction transaction = component.startAttributeTransaction();
    for (String aDir : dir) {
      transaction.removeAttribute(SHERPA_URI, aDir);
    }

    if (vertical) {
      int offsetY = Coordinates.pxToDp(component.getModel(), NlComponentHelperKt.getY(component) -
                                                             (component.isRoot() ? 0 : NlComponentHelperKt.getY(component.getParent())));
      ConstraintComponentUtilities.setDpAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, transaction, offsetY);
    }
    else {
      int offsetX = Coordinates.pxToDp(component.getModel(), NlComponentHelperKt.getX(component) -
                                                             (component.isRoot() ? 0 : NlComponentHelperKt.getX(component.getParent())));
      ConstraintComponentUtilities.setDpAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_X, transaction, offsetX);
    }
    transaction.apply();
    return transaction;
  }

  private static String getAttr(NlComponent component, String dir) {
    return component.getLiveAttribute(SHERPA_URI, dir);
  }

  /**
   * Get component given its ide.
   *
   * @param component
   * @param idString
   * @return
   */
  private static NlComponent getComponent(NlComponent component, String idString) {
    if (idString == null) {
      return null;
    }
    if (idString.equalsIgnoreCase(ATTR_PARENT)) {
      return component.getParent();
    }
    String id = NlComponent.extractId(idString);
    if (id == null) {
      System.out.println("id == null for " + idString);
    }
    NlComponent parent = component.getParent();
    if (parent == null) {
      return null;
    }

    if (id.equals(parent.getId())) {
      return parent;
    }
    List<NlComponent> list = parent.getChildren();
    return ConstraintComponentUtilities.getComponent(list, id);
  }

  /**
   * Get compoent pointed to by attribute
   *
   * @param component
   * @param attr
   * @return
   */
  private static NlComponent get(NlComponent component, String attr) {
    String attribute = component.getLiveAttribute(SHERPA_URI, attr);
    if (attribute == null) {
      return null;
    }
    if (attribute.equalsIgnoreCase(ATTR_PARENT)) {
      return component.getParent();
    }
    String id = NlComponent.extractId(attribute);

    NlComponent parent = component.getParent();
    if (parent == null) {
      return null;
    }
    if (id.equals(parent.getId())) {
      return parent;
    }
    List<NlComponent> list = parent.getChildren();
    return ConstraintComponentUtilities.getComponent(list, id);
  }
}
