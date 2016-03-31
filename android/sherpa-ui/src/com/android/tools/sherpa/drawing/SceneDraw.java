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

package com.android.tools.sherpa.drawing;

import com.android.tools.sherpa.animation.AnimatedCircle;
import com.android.tools.sherpa.animation.AnimatedColor;
import com.android.tools.sherpa.animation.AnimatedConnection;
import com.android.tools.sherpa.animation.AnimatedLine;
import com.android.tools.sherpa.animation.AnimationSet;
import com.android.tools.sherpa.animation.Choreographer;
import com.android.tools.sherpa.drawing.decorator.ColorTheme;
import com.android.tools.sherpa.drawing.decorator.WidgetDecorator;
import com.android.tools.sherpa.interaction.ConstraintHandle;
import com.android.tools.sherpa.interaction.MouseInteraction;
import com.android.tools.sherpa.interaction.ResizeHandle;
import com.android.tools.sherpa.interaction.SnapCandidate;
import com.android.tools.sherpa.interaction.WidgetInteractionTargets;
import com.android.tools.sherpa.interaction.WidgetMotion;
import com.android.tools.sherpa.interaction.WidgetResize;
import com.android.tools.sherpa.structure.WidgetsScene;
import com.android.tools.sherpa.structure.Selection;
import com.google.tnt.solver.widgets.*;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;

/**
 * Implements the drawing of WidgetsScene
 */
public class SceneDraw {

    public static final int GRID_SPACING = 8; // Material Design 8dp grid

    public static Color DarkBlueprintBackground = new Color(14, 45, 102);
    public static Color DarkBlueprintBackgroundLines = new Color(26, 60, 122);
    public static Color BlueprintBackground = new Color(24, 55, 112);
    public static Color BlueprintBackgroundLines = new Color(26, 60, 122);
    public static Color BlueprintConstraints = new Color(102, 129, 204);
    public static Color BlueprintFrames = new Color(100, 152, 199);
    public static Color BlueprintText = new Color(220, 220, 220);
    public static Color BlueprintHighlightFrames = new Color(160, 216, 237);
    public static Color BlueprintHighlightConstraints = new Color(165, 200, 221, 255);
    public static Color BlueprintSnapGuides = new Color(220, 220, 220);
    public static Color BlueprintSnapLightGuides = new Color(220, 220, 220, 128);
    public static Color DarkBlueprintFrames = ColorTheme.updateBrightness(BlueprintFrames, 0.4f);

    private boolean mDrawOutsideShade = false;
    private int mViewWidth;
    private int mViewHeight;

    private final WidgetsScene mWidgetsScene;
    private final Selection mSelection;
    private final WidgetMotion mWidgetMotion;
    private final WidgetResize mWidgetResize;

    // Animations
    private Choreographer mChoreographer = new Choreographer();

    private AnimationSet mAnimationCandidateAnchors = new AnimationSet();
    private AnimationSet mAnimationCreatedConstraints = new AnimationSet();

    private AnimatedColor mCurrentAnimation = null;

    private AnimatedColor mNormalToDark = new AnimatedColor(
            SceneDraw.BlueprintBackground,
            SceneDraw.DarkBlueprintBackground);

    private AnimatedColor mDarkToNormal = new AnimatedColor(
            SceneDraw.DarkBlueprintBackground,
            SceneDraw.BlueprintBackground);

    public static void generateColors() {
        BlueprintBackgroundLines = ColorTheme.updateBrightness(BlueprintBackground, 1.06f);
        DarkBlueprintBackground = ColorTheme.updateBrightness(BlueprintBackground, 0.8f);
        DarkBlueprintBackgroundLines = ColorTheme.updateBrightness(DarkBlueprintBackground, 1.06f);
        DarkBlueprintFrames = ColorTheme.updateBrightness(BlueprintFrames, 0.8f);
    }

    /**
     * Base constructor
     *
     * @param list      the list of widgets
     * @param selection the current selection
     * @param motion    implement motion-related behaviours for the widgets
     *                  -- we use it simply to get the list of similar margins, etc.
     */
    public SceneDraw(WidgetsScene list, Selection selection,
            WidgetMotion motion, WidgetResize resize) {
        mWidgetsScene = list;
        mSelection = selection;
        mWidgetMotion = motion;
        mWidgetResize = resize;
        mAnimationCandidateAnchors.setLoop(true);
        mAnimationCandidateAnchors.setDuration(1000);
        mAnimationCreatedConstraints.setDuration(600);
        generateColors();
    }

    /**
     * Setter to draw the outside area shaded or not
     * @param drawOutsideShade true to shade the outside area
     */
    public void setDrawOutsideShade(boolean drawOutsideShade) {
        mDrawOutsideShade = drawOutsideShade;
    }

    /**
     * Mouse handler on mouse press
     *
     * @param selectedAnchor
     */
    public void onMousePress(ConstraintAnchor selectedAnchor) {
        if (selectedAnchor != null) {
            mCurrentAnimation = mNormalToDark;
            mCurrentAnimation.start();
        } else {
            mCurrentAnimation = null;
        }
    }

    /**
     * Mouse handler on mouse release
     */
    public void mouseReleased() {
        if (mCurrentAnimation != null) {
            mCurrentAnimation = mDarkToNormal;
            mCurrentAnimation.start();
        }
        mAnimationCandidateAnchors.clear();
        mChoreographer.removeAnimation(mAnimationCandidateAnchors);
    }

    /**
     * Choreographer accessor
     */
    public Choreographer getChoreographer() {
        return mChoreographer;
    }

    /**
     * Utility function. Given the currently selected anchor, gather all the
     * potential anchors that we could connect to and start an AnimatedCircle animation
     * where they are, to indicate them to the user.
     */
    private void animateInCandidateAnchors(ConstraintAnchor selectedAnchor) {
        if (selectedAnchor == null) {
            return;
        }
        mAnimationCandidateAnchors.clear();
        for (ConstraintWidget widget : mWidgetsScene.getWidgets()) {
            WidgetInteractionTargets widgetInteraction =
                    (WidgetInteractionTargets) widget.getCompanionWidget();
            boolean highlighted = false;
            for (ConstraintAnchor a : widget.getAnchors()) {
                if (selectedAnchor.isValidConnection(a)
                        && selectedAnchor.isConnectionAllowed(a.getOwner())) {
                    ConstraintWidget owner = a.getOwner();
                    ConstraintHandle constraintHandle =
                            WidgetInteractionTargets.constraintHandle(a);
                    if (constraintHandle == null) {
                        continue;
                    }
                    if (owner instanceof Guideline || owner.isRoot()
                            || a.getType() == ConstraintAnchor.Type.BASELINE) {
                        mAnimationCandidateAnchors.add(new AnimatedLine(constraintHandle));
                        if (owner.isRoot() && a.getType() != ConstraintAnchor.Type.BASELINE) {
                            // also add the circle
                            mAnimationCandidateAnchors.add(new AnimatedCircle(constraintHandle));
                        }
                    } else {
                        mAnimationCandidateAnchors.add(new AnimatedCircle(constraintHandle));
                    }
                    highlighted = true;
                }
            }
            WidgetDecorator decorator = (WidgetDecorator) widget.getCompanionWidget();
            if (decorator.getLook() == ColorTheme.Look.NORMAL) {
                if (highlighted) {
                    decorator.setLook(ColorTheme.Look.HIGHLIGHTED);
                } else {
                    decorator.setLook(ColorTheme.Look.SUBDUED);
                }
            }
        }

        mChoreographer.addAnimation(mAnimationCandidateAnchors);
    }

    /**
     * Animate in constraints created by the given type
     */
    public void animateConstraints(int type) {
        mAnimationCreatedConstraints.clear();
        for (ConstraintWidget widget : mWidgetsScene.getWidgets()) {
            WidgetInteractionTargets widgetInteraction =
                    (WidgetInteractionTargets) widget.getCompanionWidget();
            for (ConstraintAnchor a : widget.getAnchors()) {
                if (!a.isConnected()) {
                    continue;
                }
                mAnimationCreatedConstraints.add(new AnimatedConnection(a));
            }
        }
        mChoreographer.addAnimation(mAnimationCreatedConstraints);
    }

    /**
     * Draw a background for the view.
     *
     * @param g              Graphics context
     * @param selectedAnchor
     */
    public boolean drawBackground(ViewTransform transform, Graphics2D g, int rootMargin, int w,
            int h,
            ConstraintAnchor selectedAnchor) {
        // We want to draw a grid (on GRID_SPACING) in blueprint mode.
        // TODO: use a tile bitmap instead
        Color backgroundColor = SceneDraw.BlueprintBackground;
        boolean needsRepaint = false;
        if (mCurrentAnimation != null) {
            if (mCurrentAnimation.step()) {
                needsRepaint = true;
            }
            backgroundColor = mCurrentAnimation.getColor();
        }
        g.setColor(backgroundColor);

        g.fillRect(transform.getTranslateX(), transform.getTranslateY(), w, h);

        WidgetContainer root = mWidgetsScene.getRoot();
        if (root == null) {
            return needsRepaint;
        }

        float step = SceneDraw.GRID_SPACING * transform.getScale();
        Color backgroundLines = ColorTheme.updateBrightness(backgroundColor, 1.06f);
        g.setColor(backgroundLines);

        int xr = transform.getSwingX(root.getDrawX());
        int yr = transform.getSwingY(root.getDrawY());
        int wr = transform.getSwingDimension(root.getDrawWidth());
        int hr = transform.getSwingDimension(root.getDrawHeight());
        for (float i = xr; i < xr + wr; i += step) {
            g.drawLine((int) i, (int) yr, (int) i, (int) (yr + hr));
        }
        for (float i = yr; i < yr + hr; i += step) {
            g.drawLine((int) xr, (int) i, (int) (xr + wr), (int) i);
        }

        return needsRepaint;
    }

    /**
     * Utility function to get the decorator of the widget and set the correct
     * anchor and resize indicators
     *
     * @param widget               the widget to draw
     * @param selectedWidget       the selected widget if any
     * @param selectedAnchor       the selected anchor if any
     * @param selectedResizeHandle the selected resize handle if any
     * @return the widget decorator
     */
    private WidgetDecorator getDecorator(ConstraintWidget widget, ConstraintWidget selectedWidget,
            ConstraintAnchor selectedAnchor, ResizeHandle selectedResizeHandle) {
        WidgetDecorator decorator = (WidgetDecorator) widget.getCompanionWidget();
        if (!decorator.isSelected()) {
            decorator.updateShowAnchorsPolicy(selectedWidget, selectedAnchor);
        } else {
            decorator.setShowResizeHandles(mWidgetMotion.needToShowDecorations());
            decorator.setShowSizeIndicator(selectedResizeHandle != null);
        }
        return decorator;
    }

    /**
     * Utility function to paint the given widget container and its children
     *
     * @param transform            the view transform
     * @param g                    the graphics context
     * @param container            the container to paint
     * @param selectedWidget       the selected widget if any
     * @param selectedAnchor       the selected anchor if any
     * @param selectedResizeHandle the selected resize handle if any
     * @return true if we need to be repainted, false otherwise
     */
    private boolean paintWidgets(ViewTransform transform, Graphics2D g,
            WidgetContainer container, ConstraintWidget selectedWidget,
            ConstraintAnchor selectedAnchor, ResizeHandle selectedResizeHandle) {
        if (container.getVisibility() == ConstraintWidget.GONE) {
            return false;
        }
        boolean needsRepaint = false;
        WidgetDecorator decorator =
                getDecorator(container, selectedWidget, selectedAnchor, selectedResizeHandle);
        needsRepaint |= decorator.onPaint(transform, g);
        if (container == mWidgetsScene.getRoot()) {
            if (mDrawOutsideShade) {
                int xr = transform.getSwingX(container.getDrawX());
                int yr = transform.getSwingY(container.getDrawY());
                int wr = transform.getSwingDimension(container.getDrawWidth());
                int hr = transform.getSwingDimension(container.getDrawHeight());
                g.setColor(DarkBlueprintBackground);
                g.fillRect(transform.getTranslateX(), transform.getTranslateY(), mViewWidth, yr);
                g.fillRect(transform.getTranslateX(), yr + hr, mViewWidth, mViewHeight - yr - hr);
                g.fillRect(transform.getTranslateX(), yr, xr, hr);
                g.fillRect(wr + xr, yr, mViewWidth - xr - wr, hr);
                g.setStroke(SnapDraw.sLongDashedStroke);
                g.setColor(BlueprintHighlightFrames);
                g.drawRect(xr, yr, wr, hr);
            }
        }
        for (ConstraintWidget widget : container.getChildren()) {
            if (widget.getVisibility() == ConstraintWidget.GONE) {
                continue;
            }
            if (widget instanceof WidgetContainer) {
                needsRepaint |= paintWidgets(transform, g, (WidgetContainer) widget,
                        selectedWidget, selectedAnchor, selectedResizeHandle);
            } else {
                WidgetDecorator widgetDecorator =
                        getDecorator(widget, selectedWidget, selectedAnchor, selectedResizeHandle);
                needsRepaint |= widgetDecorator.onPaint(transform, g);
            }
        }
        return needsRepaint;
    }

    /**
     * Main painting function
     *
     * @param width              width of the canvas we paint on
     * @param height             height of the canvas we paint on
     * @param transform
     * @param g
     * @param showAllConstraints
     * @param mouseInteraction
     * @return true if need to be called again (animation...)
     */
    public boolean paintWidgets(int width, int height,
            ViewTransform transform, Graphics2D g,
            boolean showAllConstraints,
            MouseInteraction mouseInteraction) {

        WidgetContainer root = mWidgetsScene.getRoot();
        if (root == null) {
            return false;
        }
        mViewWidth = width;
        mViewHeight = height;
        root = root.getRootConstraintContainer();
        root.layout();

        // Adapt the anchor size
        if (transform.getScale() < 2f) {
            ConnectionDraw.CONNECTION_ANCHOR_SIZE = 4;
            if (transform.getScale() < 1.8f) {
                ConnectionDraw.CONNECTION_ANCHOR_SIZE = 3;
            }
            if (transform.getScale() < 1.4f) {
                ConnectionDraw.CONNECTION_ANCHOR_SIZE = 2;
            }
        } else {
            ConnectionDraw.CONNECTION_ANCHOR_SIZE = 6;
        }

        WidgetInteractionTargets widgetInteraction =
                (WidgetInteractionTargets) root.getCompanionWidget();
        widgetInteraction.updatePosition(transform);

        ConstraintAnchor selectedAnchor = mSelection.getSelectedAnchor();
        ResizeHandle selectedResizeHandle = mSelection.getSelectedResizeHandle();

        // Let's draw the widgets and their constraints.

        boolean needsRepaint = false;

        WidgetDecorator.setShowAllConstraints(showAllConstraints);

        // First, mark which widgets is selected.
        for (ConstraintWidget widget : mWidgetsScene.getWidgets()) {
            WidgetDecorator decorator = (WidgetDecorator) widget.getCompanionWidget();
            if (mSelection.contains(widget)) {
                decorator.setIsSelected(true);
            } else {
                decorator.setIsSelected(false);
            }
        }

        // Then, mark highlighted widgets
        animateInCandidateAnchors(selectedAnchor);

        for (ConstraintWidget widget : mWidgetsScene.getWidgets()) {
            WidgetDecorator decorator = (WidgetDecorator) widget.getCompanionWidget();
            decorator.applyLook();
        }

        // Draw all the widgets
        ConstraintWidget selectedWidget = null;
        if (mSelection.hasSingleElement()) {
            selectedWidget = mSelection.getFirstElement().widget;
        }
        needsRepaint |= paintWidgets(transform, g, mWidgetsScene.getRoot(), selectedWidget,
                selectedAnchor, selectedResizeHandle);

        // Then draw the constraints
        for (ConstraintWidget widget : mWidgetsScene.getWidgets()) {
            WidgetDecorator decorator = (WidgetDecorator) widget.getCompanionWidget();
            decorator.onPaintConstraints(transform, g);
        }

        // Draw snap candidates
        g.setColor(BlueprintSnapLightGuides);
        for (SnapCandidate candidate : mWidgetMotion.getSimilarMargins()) {
            SnapDraw.drawSnapIndicator(transform, g, candidate);
        }
        g.setColor(BlueprintSnapGuides);
        for (SnapCandidate candidate : mWidgetMotion.getSnapCandidates()) {
            SnapDraw.drawSnapIndicator(transform, g, candidate);
        }
        for (SnapCandidate candidate : mWidgetResize.getSnapCandidates()) {
            SnapDraw.drawSnapIndicator(transform, g, candidate);
        }

        if (mSelection.hasSingleElement() && selectedAnchor != null) {
            ConstraintAnchor anchor = mSelection.getConnectionCandidateAnchor();
            ConstraintHandle selectedHandle =
                    WidgetInteractionTargets.constraintHandle(selectedAnchor);
            if (anchor != null
                    && anchor != selectedAnchor
                    && selectedAnchor.isValidConnection(anchor)
                    && selectedAnchor.isConnectionAllowed(anchor.getOwner())) {
                g.setColor(BlueprintSnapGuides);
                ConstraintHandle targetHandle = WidgetInteractionTargets.constraintHandle(anchor);
                ConnectionDraw
                        .drawConnection(transform, g, selectedHandle, targetHandle, true, false);
            } else {
                g.setColor(BlueprintSnapLightGuides);
                ConnectionDraw
                        .drawConnection(transform, g, selectedHandle,
                                mouseInteraction.getLastPoint());
            }
        }

        if (selectedResizeHandle != null) {
            g.setColor(Color.white);
            WidgetDraw.drawResizeHandleSelection(transform, g,
                    selectedResizeHandle);
        }

        if (mSelection.isEmpty() && mouseInteraction.isMouseDown()) {
            Point startPoint = mouseInteraction.getStartPoint();
            Point lastMousePosition = mouseInteraction.getLastPoint();
            // draw a selection rect
            int x1 = Math.min(startPoint.x, lastMousePosition.x);
            int x2 = Math.max(startPoint.x, lastMousePosition.x);
            int y1 = Math.min(startPoint.y, lastMousePosition.y);
            int y2 = Math.max(startPoint.y, lastMousePosition.y);
            int ax1 = transform.getSwingX(x1);
            int ax2 = transform.getSwingX(x2);
            int ay1 = transform.getSwingY(y1);
            int ay2 = transform.getSwingY(y2);
            int w = x2 - x1;
            int h = y2 - y1;
            if (w > 0 || h > 0) {
                g.setColor(Color.white);
                g.setStroke(SnapDraw.sDashedStroke);
                if (w >= 8 && h >= 8) {
                    g.drawRect(ax1, ay1, ax2 - ax1, ay2 - ay1);
                } else if (w >= 8 && h < 8) {
                    g.drawLine(ax1, ay1, ax2, ay1);
                } else {
                    g.drawLine(ax1, ay1, ax1, ay2);
                }
                g.setStroke(SnapDraw.sNormalStroke);
                if (w >= 8) {
                    ConnectionDraw.drawHorizontalMarginIndicator(g, "" + w, ax1, ax2, ay1 - 20);
                }
                if (h >= 8) {
                    ConnectionDraw.drawVerticalMarginIndicator(g, "" + h, ax1 - 20, ay1, ay2);
                }
            }
        }

        if (mSelection.getSelectionBounds() != null) {
            Selection.Element bounds = mSelection.getSelectionBounds();
            g.setColor(Color.white);
            g.setStroke(SnapDraw.sDashedStroke);
            int x = transform.getSwingX(bounds.widget.getDrawX());
            int y = transform.getSwingY(bounds.widget.getDrawY());
            int w = transform.getSwingDimension(bounds.widget.getDrawWidth());
            int h = transform.getSwingDimension(bounds.widget.getDrawHeight());
            g.drawRect(x, y, w, h);
        }

        needsRepaint |= mChoreographer.onPaint(transform, g);
        if (!needsRepaint && !mSelection.isEmpty()) {
            for (Selection.Element element : mSelection.getElements()) {
                needsRepaint |= element.widget.isAnimating();
            }
        }
        if (!needsRepaint) {
            for (ConstraintWidget widget : mWidgetsScene.getWidgets()) {
                needsRepaint |= widget.isAnimating();
            }
        }

        return needsRepaint;
    }

}
