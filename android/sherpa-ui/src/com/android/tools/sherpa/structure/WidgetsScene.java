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

package com.android.tools.sherpa.structure;

import com.android.tools.sherpa.drawing.ConnectionDraw;
import com.android.tools.sherpa.drawing.decorator.WidgetDecorator;
import com.android.tools.sherpa.interaction.ResizeHandle;
import com.android.tools.sherpa.interaction.WidgetInteractionTargets;
import com.google.tnt.solver.widgets.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

/**
 * Represent a list of widgets and the associated operations
 */
public class WidgetsScene {

    private HashMap<Object, ConstraintWidget> mWidgets = new HashMap<Object, ConstraintWidget>();
    private ConstraintWidgetContainer mRoot;
    private Selection mSelection;

    /**
     * Clear the scene
     */
    public void clear() {
        mRoot = null;
        if (mSelection != null) {
            mSelection.clear();
        }
        mWidgets.clear();
    }

    /**
     * Accessor to the list of widgets
     * @return
     */
    public Collection<ConstraintWidget> getWidgets() {
        return mWidgets.values();
    }

    /**
     * Set the current list of widgets
     *
     * @param widgets
     */
    public void setWidgets(HashMap<Object, ConstraintWidget> widgets) {
        mWidgets = widgets;
        for (ConstraintWidget widget : mWidgets.values()) {
            if (widget.isRoot()) {
                mRoot = (ConstraintWidgetContainer) widget;
            }
        }
    }

    /**
     * Create and insert a new group from a given list of widgets
     *
     * @param widgets list of widgets to put in the group
     */
    public void createGroupFromWidgets(ArrayList<ConstraintWidget> widgets) {
        ConstraintWidgetContainer container = new ConstraintWidgetContainer();
        container.setCompanionWidget(new WidgetDecorator(container));
        createContainerFromWidgets(widgets, container, createContainerName("group"));
    }

    /**
     * Transform the selected table to a normal container
     */
    public void transformTableToContainer(ConstraintTableLayout table) {
        ConstraintWidgetContainer container = new ConstraintWidgetContainer();
        container.setDebugName(createContainerName("container"));
        transformContainerToContainer(table, container);
    }

    /**
     * Remove container and move its children to the same level
     *
     * @param container
     */
    public void removeContainer(ConstraintWidgetContainer container) {
        ConstraintWidgetContainer parent = (ConstraintWidgetContainer) container.getParent();
        if (parent == null) {
            return;
        }
        for (ConstraintWidget widget : mWidgets.values()) {
            widget.disconnectWidget(container);
        }
        ArrayList<ConstraintWidget> children = new ArrayList<ConstraintWidget>(container.getChildren());
        for (ConstraintWidget child : children) {
            parent.add(child);
            child.resetAnchors();
            child.setX(child.getX() + container.getX());
            child.setY(child.getY() + container.getY());
        }
        parent.remove(container);
        mWidgets.remove(container.getDebugName());
    }

    /**
     * Remove a widget from the tree, breaking any connections to it
     *
     * @param widget the widget we are removing
     */
    public void removeWidget(ConstraintWidget widget) {
        if (widget.isRoot()) {
            return;
        }
        if (widget instanceof ConstraintWidgetContainer) {
            ConstraintWidgetContainer container = (ConstraintWidgetContainer) widget;
            ArrayList<ConstraintWidget> children = new ArrayList<ConstraintWidget>(container.getChildren());
            for (ConstraintWidget w : children) {
                removeWidget(w);
            }
        }
        for (ConstraintWidget w : mWidgets.values()) {
            w.disconnectWidget(widget);
        }
        WidgetContainer parent = (WidgetContainer) widget.getParent();
        parent.remove(widget);
        mWidgets.remove(widget.getDebugName());
    }

    /**
     * Flatten the hierachy -- remove all existing containers children of the given container
     *
     * @param root the root container we start from
     */
    public void flattenHierarchy(ConstraintWidgetContainer root) {
        ArrayList<ConstraintWidgetContainer> containers =
                WidgetsScene.gatherContainers(root);
        while (containers.size() > 0) {
            for (ConstraintWidgetContainer container : containers) {
                removeContainer(container);
            }
            containers = WidgetsScene.gatherContainers(root);
        }
    }

    /**
     * Find which ResizeHandle is close to the (x, y) coordinates
     *
     * @param x x coordinate
     * @param y y coordinate
     * @return the ResizeHandle close to (x, y), or null if none are close enough
     */
    public ResizeHandle findResizeHandle(int x, int y) {
        for (ConstraintWidget widget : mWidgets.values()) {
            if (widget.isRoot()) {
                continue;
            }
            WidgetInteractionTargets widgetInteraction = (WidgetInteractionTargets) widget.getCompanionWidget();
            widgetInteraction.updatePosition();
            ResizeHandle handle = widgetInteraction.findResizeHandle(x, y);
            if (handle != null) {
                return handle;
            }
        }
        return null;
    }

    /**
     * Find which ConstraintAnchor is close to the (x, y) coordinates
     *
     * @param x x coordinate
     * @param y y coordinate
     * @param checkGuidelines if true, we will check for guidelines to connect to
     * @param mousePress pass true on mouse press
     * @return the ConstraintAnchor close to (x, y), or null if none are close enough
     */
    public ConstraintAnchor findAnchor(int x, int y, boolean checkGuidelines, boolean mousePress) {
        ConnectionCandidate candidate = new ConnectionCandidate();
        candidate.distance = ConnectionDraw.CONNECTION_ANCHOR_SIZE
                * ConnectionDraw.CONNECTION_ANCHOR_SIZE;
        // We first try to find an anchor in the current selection
        for (Selection.Element element : mSelection.getElements()) {
            ConstraintWidget widget = element.widget;
            if (!checkGuidelines && (widget instanceof Guideline)) {
                continue;
            }
            WidgetInteractionTargets widgetInteraction = (WidgetInteractionTargets) widget.getCompanionWidget();
            widgetInteraction.updatePosition();
            widgetInteraction.findClosestConnection(x, y, candidate, mousePress);
        }
        int slope = ConnectionDraw.CONNECTION_ANCHOR_SIZE * 2;
        if (candidate.anchorTarget != null
                && candidate.distance < slope) {
            // allow some slope if we picked an anchor from the selection
            candidate.distance = 0;
        }
        for (ConstraintWidget widget : mWidgets.values()) {
            if (!checkGuidelines && (widget instanceof Guideline)) {
                continue;
            }
            WidgetInteractionTargets widgetInteraction = (WidgetInteractionTargets) widget.getCompanionWidget();
            widgetInteraction.updatePosition();
            widgetInteraction.findClosestConnection(x, y, candidate, mousePress);
        }
        return candidate.anchorTarget;
    }

    /*-----------------------------------------------------------------------*/
    // Private functions
    /*-----------------------------------------------------------------------*/

    /**
     * Utility function returning a new unique name for a container
     *
     * @param name the prefix used
     * @return new container name
     */
    public String createContainerName(String name) {
        boolean valid = false;
        int counter = 1;
        while (!valid) {
            String candidate = name + counter;
            boolean exists = false;
            for (ConstraintWidget widget : mWidgets.values()) {
                if (widget.getDebugName().equalsIgnoreCase(candidate)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                valid = true;
                name = candidate;
            } else {
                counter++;
            }
        }
        return name;
    }

    /**
     * Gather a list of containers that are children of the given container
     *
     * @param container the container we start from
     * @return a list of containers
     */
    private static ArrayList<ConstraintWidgetContainer> gatherContainers(
            ConstraintWidgetContainer container) {
        ArrayList<ConstraintWidgetContainer> containers = new ArrayList<ConstraintWidgetContainer>();
        for (ConstraintWidget widget : container.getChildren()) {
            if (widget instanceof ConstraintWidgetContainer) {
                containers.add((ConstraintWidgetContainer) widget);
            }
        }
        return containers;
    }

    /**
     * Move the content of an old container to a new container
     *
     * @param oldContainer
     * @param newContainer
     */
    public void transformContainerToContainer(ConstraintWidgetContainer oldContainer,
            ConstraintWidgetContainer newContainer) {
        ConstraintWidgetContainer parent = (ConstraintWidgetContainer) oldContainer.getParent();
        if (newContainer.getCompanionWidget() == null) {
            newContainer.setCompanionWidget(new WidgetDecorator(newContainer));
        }
        newContainer.setOrigin(oldContainer.getX(), oldContainer.getY());
        newContainer.setDimension(oldContainer.getWidth(), oldContainer.getHeight());
        newContainer
                .setHorizontalDimensionBehaviour(oldContainer.getHorizontalDimensionBehaviour());
        newContainer.setVerticalDimensionBehaviour(oldContainer.getVerticalDimensionBehaviour());
        ArrayList<ConstraintWidget> children = new ArrayList<ConstraintWidget>(oldContainer.getChildren());
        for (ConstraintWidget child : children) {
            newContainer.add(child);
        }
        for (ConstraintAnchor anchor : oldContainer.getAnchors()) {
            if (anchor.isConnected()) {
                newContainer.getAnchor(anchor.getType())
                        .connect(anchor.getTarget(), anchor.getMargin(), anchor.getStrength());
            }
        }
        for (ConstraintWidget child : newContainer.getChildren()) {
            // make sure the child anchors are reset
            child.resetAnchors();
        }
        parent.remove(oldContainer);
        parent.add(newContainer);
        mWidgets.remove(oldContainer.getDebugName());
        mWidgets.put(newContainer.getDebugName(), newContainer);
        boolean previousAnimationState = Animator.isAnimationEnabled();
        Animator.setAnimationEnabled(false);
        mRoot.layout();
        Animator.setAnimationEnabled(previousAnimationState);
    }

    /**
     * Insert a new ConstraintWidgetContainer in place, from
     * a list of widgets. The widgets will be cleared of their current
     * constraints and put as children of the new container.
     *
     * @param widgets           widgets we want to group into the container
     * @param containerInstance the container that will be the parent of the widget
     */
    public void createContainerFromWidgets(ArrayList<ConstraintWidget> widgets,
            ConstraintWidgetContainer containerInstance, String name) {
        Collections.sort(widgets, new Comparator<ConstraintWidget>() {
            @Override
            public int compare(ConstraintWidget o1, ConstraintWidget o2) {
                if (o1.getY() + o1.getHeight() < o2.getY()) {
                    return -1;
                }
                if (o2.getY() + o2.getHeight() < o1.getY()) {
                    return 1;
                }
                // TODO when JDK 1.6 is no longer used, replace with: Integer.compare(o1.getX(), o2.getX());
                return o1.getX() - o2.getX();
            }
        });

        if (widgets.size() == 0) {
            return;
        }
        for (ConstraintWidget w : mWidgets.values()) {
            for (ConstraintWidget widget : widgets) {
                w.disconnectWidget(widget);
                widget.resetAnchors();
                widget.setHorizontalBiasPercent(0.5f);
                widget.setVerticalBiasPercent(0.5f);
            }
        }
        WidgetContainer parent = (WidgetContainer) widgets.get(0).getParent();
        if (parent == null) {
            parent = mRoot;
        }
        ConstraintWidgetContainer container =
                ConstraintWidgetContainer.createContainer(containerInstance, name, widgets, 8);
        if (container != null) {
            if (container.getCompanionWidget() == null) {
                container.setCompanionWidget(new WidgetDecorator(container));
            }
            parent.add(container);
            mWidgets.put(container.getDebugName(), container);
            boolean previousAnimationState = Animator.isAnimationEnabled();
            Animator.setAnimationEnabled(false);
            mRoot.layout();
            Animator.setAnimationEnabled(previousAnimationState);
        }
    }

    /**
     * Adapt the table's dimensions and columns or rows to its content
     *
     * @param table
     */
    public static void adaptTable(ConstraintTableLayout table) {
        // We do that by first setting the table to wrap_content...
        int width = table.getWidth();
        int height = table.getHeight();
        ConstraintWidget.DimensionBehaviour horizontalBehaviour = table.getHorizontalDimensionBehaviour();
        ConstraintWidget.DimensionBehaviour verticalBehaviour = table.getVerticalDimensionBehaviour();
        table.setHorizontalDimensionBehaviour(
                ConstraintWidget.DimensionBehaviour.WRAP_CONTENT);
        table.setVerticalDimensionBehaviour(
                ConstraintWidget.DimensionBehaviour.WRAP_CONTENT);
        table.layout();
        // FIXME, 2nd pass should not be necessary
        table.layout();
        // then getting the computed size, and use it as minimum size
        table.setMinWidth(table.getWidth());
        table.setMinHeight(table.getHeight());
        table.computeGuidelinesPercentPositions();
        // then put back the table to fixed size
        table.setHorizontalDimensionBehaviour(horizontalBehaviour);
        table.setVerticalDimensionBehaviour(verticalBehaviour);
        table.setWidth(width < table.getMinWidth() ? table.getMinWidth() : width);
        table.setHeight(height < table.getMinHeight() ? table.getMinHeight() : height);
        table.layout();
    }

    public ConstraintWidget getWidget(Object key) {
        return mWidgets.get(key);
    }

    public void setRoot(ConstraintWidgetContainer root) {
        mRoot = root;
    }

    public ConstraintWidgetContainer getRoot() {
        if (mRoot == null) {
            for (ConstraintWidget widget : mWidgets.values()) {
                if (widget instanceof ConstraintWidgetContainer && widget.isRoot()) {
                    ConstraintWidgetContainer lastRoot = (ConstraintWidgetContainer) widget;
                    WidgetContainer root = lastRoot;
                    while (root.getParent() != null) {
                        root = (WidgetContainer) root.getParent();
                        if (root instanceof ConstraintWidgetContainer) {
                            lastRoot = (ConstraintWidgetContainer) root;
                        }
                    }
                    mRoot = lastRoot;
                    break;
                }
            }
        }
        return mRoot;
    }

    public void setWidget(Object key, ConstraintWidget widget) {
        mWidgets.put(key, widget);
    }

    public void addWidget(ConstraintWidget widget) {
        if (widget instanceof ConstraintWidgetContainer && widget.getParent() == null) {
            mRoot = (ConstraintWidgetContainer) widget;
        }
        mWidgets.put(widget.getDebugName(), widget);
    }

    public void setSelection(Selection selection) {
        mSelection = selection;
    }

    public int size() { return mWidgets.size(); }

    /**
     * Utility function to return the closest horizontal anchor
     *
     * @param widget widget we start from
     * @param searchLeft if true, we are searching on our left side
     * @return the closest ConstraintAnchor
     */
    public ConstraintAnchor getClosestHorizontalWidgetAnchor(ConstraintWidget widget, boolean searchLeft) {
        ConstraintWidgetContainer parent = (ConstraintWidgetContainer) widget.getParent();
        ArrayList<ConstraintWidget> children = parent.getChildren();
        int pos = widget.getDrawX();
        if (!searchLeft) {
            pos = widget.getDrawRight();
        }
        int min = Integer.MAX_VALUE;
        ConstraintWidget found = null;
        for (int i = 0; i < children.size(); i++) {
            ConstraintWidget child = children.get(i);
            // check if it intersects
            int maxTop = Math.max(child.getDrawY(), widget.getDrawY());
            int minBottom = Math.min(child.getDrawBottom(), widget.getDrawBottom());
            if (maxTop > minBottom) {
                // we don't intersect
                continue;
            }
            int delta = pos - child.getDrawRight();
            if (!searchLeft) {
                delta = child.getDrawX() - pos;
            }
            if (delta >= 0 && delta < min) {
                found = child;
                min = delta;
            }
        }
        if (found == null) {
            if (searchLeft) {
                return parent.getAnchor(ConstraintAnchor.Type.LEFT);
            } else {
                return parent.getAnchor(ConstraintAnchor.Type.RIGHT);
            }
        }
        if (searchLeft) {
            return found.getAnchor(ConstraintAnchor.Type.RIGHT);
        } else {
            return found.getAnchor(ConstraintAnchor.Type.LEFT);
        }
    }

    /**
     * Utility function to return the closest vertical anchor
     *
     * @param widget widget we start from
     * @param searchTop if true, we are searching above us
     * @return the closest ConstraintAnchor
     */
    public ConstraintAnchor getClosestVerticalWidgetAnchor(ConstraintWidget widget, boolean searchTop) {
        ConstraintWidgetContainer parent = (ConstraintWidgetContainer) widget.getParent();
        ArrayList<ConstraintWidget> children = parent.getChildren();
        int pos = widget.getDrawY();
        if (!searchTop) {
            pos = widget.getDrawBottom();
        }
        int min = Integer.MAX_VALUE;
        ConstraintWidget found = null;
        for (int i = 0; i < children.size(); i++) {
            ConstraintWidget child = children.get(i);
            // check if it intersects
            int maxLeft = Math.max(child.getDrawX(), widget.getDrawX());
            int minRight = Math.min(child.getDrawRight(), widget.getDrawRight());
            if (maxLeft > minRight) {
                // we don't intersect
                continue;
            }
            int delta = pos - child.getDrawBottom();
            if (!searchTop) {
                delta = child.getDrawY() - pos;
            }
            if (delta >= 0 && delta < min) {
                found = child;
                min = delta;
            }
        }
        if (found == null) {
            if (searchTop) {
                return parent.getAnchor(ConstraintAnchor.Type.TOP);
            } else {
                return parent.getAnchor(ConstraintAnchor.Type.BOTTOM);
            }
        }
        if (searchTop) {
            return found.getAnchor(ConstraintAnchor.Type.BOTTOM);
        } else {
            return found.getAnchor(ConstraintAnchor.Type.TOP);
        }
    }

    /**
     * center the given widget horizontally
     *
     * @param widget the widget to center
     */
    public void centerHorizontally(ConstraintWidget widget) {
        ConstraintAnchor left = getClosestHorizontalWidgetAnchor(widget, true);
        ConstraintAnchor right = getClosestHorizontalWidgetAnchor(widget, false);
        widget.connect(widget.getAnchor(ConstraintAnchor.Type.LEFT), left, 0);
        widget.connect(widget.getAnchor(ConstraintAnchor.Type.RIGHT), right, 0);
    }

    /**
     * center the given widget vertically
     *
     * @param widget the widget to center
     */
    public void centerVertically(ConstraintWidget widget) {
        ConstraintAnchor top = getClosestVerticalWidgetAnchor(widget, true);
        ConstraintAnchor bottom = getClosestVerticalWidgetAnchor(widget, false);
        widget.connect(widget.getAnchor(ConstraintAnchor.Type.TOP), top, 0);
        widget.connect(widget.getAnchor(ConstraintAnchor.Type.BOTTOM), bottom, 0);
    }

    /**
     * Make sure the positions of the interaction targets are correctly updated
     */
    public void updatePositions() {
        for (ConstraintWidget widget : mWidgets.values()) {
            widget.updateDrawPosition();
            WidgetInteractionTargets widgetInteraction =
                    (WidgetInteractionTargets) widget.getCompanionWidget();
            widgetInteraction.updatePosition();
        }
    }
}
