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

import com.android.tools.sherpa.animation.AnimatedColor;
import com.android.tools.sherpa.animation.AnimatedConnection;
import com.android.tools.sherpa.animation.AnimatedHoverAnchor;
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
import com.android.tools.sherpa.structure.WidgetCompanion;
import com.android.tools.sherpa.structure.WidgetsScene;
import com.android.tools.sherpa.structure.Selection;
import android.constraint.solver.widgets.*;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Implements the drawing of WidgetsScene
 */
public class SceneDraw {

    public static final int GRID_SPACING = 8; // Material Design 8dp grid
    public static boolean DRAW_ONLY_ROOT_BACKGROUND = true;
    public static boolean DRAW_GRID = false;

    private ColorSet mColorSet;

    private boolean mDrawOutsideShade = false;
    private boolean mDrawResizeHandle = false;

    private int mViewWidth;
    private int mViewHeight;

    private final WidgetsScene mWidgetsScene;
    private final Selection mSelection;
    private final WidgetMotion mWidgetMotion;
    private final WidgetResize mWidgetResize;

    // Animations
    private Choreographer mChoreographer = new Choreographer();

    private AnimatedHoverAnchor mAnimationCurrentAnchor = null;
    private AnimationSet mAnimationCandidateAnchors = new AnimationSet();
    private AnimationSet mAnimationCreatedConstraints = new AnimationSet();

    private AnimatedColor mCurrentAnimation = null;

    private AnimatedColor mNormalToDark;

    private AnimatedColor mDarkToNormal;

    private ConstraintAnchor mCurrentUnderneathAnchor;
    private boolean mMoveOnlyMode = false;
    private boolean mApplyConstraints = true;

    private Repaintable mRepaintableSurface;

    private int mBackgroundFactorX;
    private int mBackgroundFactorY;
    private int mBackgroundFactorColor;
    private int mBackgroundFactorSize;

    public interface Repaintable {
        void repaint();
        void repaint(int x, int y, int w, int h);
    }

    /**
     * Base constructor
     *
     * @param list      the list of widgets
     * @param selection the current selection
     * @param motion    implement motion-related behaviours for the widgets
     *                  -- we use it simply to get the list of similar margins, etc.
     */
    public SceneDraw(ColorSet colorSet, WidgetsScene list, Selection selection,
            WidgetMotion motion, WidgetResize resize) {
        mWidgetsScene = list;
        mSelection = selection;
        mWidgetMotion = motion;
        mWidgetMotion.setSceneDraw(this);
        mWidgetResize = resize;
        mAnimationCandidateAnchors.setLoop(true);
        mAnimationCandidateAnchors.setDuration(1000);
        mAnimationCreatedConstraints.setDuration(600);
        setColorSet(colorSet);
    }

    /**
     * Set a repaintable object
     *
     * @param repaintableSurface
     */
    public void setRepaintableSurface(Repaintable repaintableSurface) {
        mRepaintableSurface = repaintableSurface;
    }

    /**
     * Call repaint() on the repaintable object
     */
    public void repaint() {
        if (mRepaintableSurface != null) {
            mRepaintableSurface.repaint();
        }
    }

    /**
     * Set a new ColorSet for this scene
     *
     * @param set a new colorset
     */
    public void setColorSet(ColorSet set) {
        if (mColorSet == set) {
            return;
        }
        mColorSet = set;
        mNormalToDark = new AnimatedColor(
                mColorSet.getBackground(),
                mColorSet.getSubduedBackground());
        mDarkToNormal = new AnimatedColor(
                mColorSet.getSubduedBackground(),
                mColorSet.getBackground());
    }

    /**
     * Setter to draw the outside area shaded or not
     *
     * @param drawOutsideShade true to shade the outside area
     */
    public void setDrawOutsideShade(boolean drawOutsideShade) {
        mDrawOutsideShade = drawOutsideShade;
    }

    /**
     * Setter to draw or not a resize handle on the root container
     *
     * @param drawResizeHandle if true, will draw a resize handle
     */
    public void setDrawResizeHandle(boolean drawResizeHandle) {
        mDrawResizeHandle = drawResizeHandle;
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
        if (mAnimationCurrentAnchor != null) {
            mAnimationCurrentAnchor.setShowTooltip(false);
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

        // Reset the widget looks on mouse release
        for (ConstraintWidget widget : mWidgetsScene.getWidgets()) {
            WidgetCompanion companion = (WidgetCompanion) widget.getCompanionWidget();
            WidgetDecorator decorator = companion.getWidgetDecorator(getCurrentStyle());
            if (decorator.getLook() == ColorTheme.Look.HIGHLIGHTED
                    || decorator.getLook() == ColorTheme.Look.SUBDUED) {
                decorator.setLook(ColorTheme.Look.NORMAL);
            }
        }
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
            boolean highlighted = false;
            for (ConstraintAnchor a : widget.getAnchors()) {
                if (selectedAnchor.isValidConnection(a)
                        && selectedAnchor.isConnectionAllowed(a.getOwner())) {
                    ConstraintHandle constraintHandle =
                            WidgetInteractionTargets.constraintHandle(a);
                    if (constraintHandle == null) {
                        continue;
                    }
                    mAnimationCandidateAnchors.add(new AnimatedLine(constraintHandle));
                    highlighted = true;
                }
            }
            WidgetCompanion companion = (WidgetCompanion) widget.getCompanionWidget();
            WidgetDecorator decorator = companion.getWidgetDecorator(getCurrentStyle());
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
            for (ConstraintAnchor a : widget.getAnchors()) {
                if (!a.isConnected()) {
                    continue;
                }
                if (a.getConnectionCreator() == type) {
                    mAnimationCreatedConstraints.add(new AnimatedConnection(mColorSet, a));
                }
            }
        }
        mChoreographer.addAnimation(mAnimationCreatedConstraints);
    }

    /**
     *  Draw a background for the view by creating an caching a texture scaled
     *  according to the transform
     *
     * @param transform used to scale the textrue
     * @param g the graphics context to draw with
     * @param w width of screen
     * @param h height of screen
     * @return
     */
    public boolean drawBackground(ViewTransform transform, Graphics2D g, int w, int h) {
        boolean needsRepaint = false;
        Color backgroundColor = mColorSet.getBackground();
        Paint backgroundPaint = mColorSet.getBackgroundPaint();
        // We want to draw a grid (on GRID_SPACING) in blueprint mode.
        if (mCurrentAnimation != null) {
            if (mCurrentAnimation.step()) {
                needsRepaint = true;
            }
            backgroundColor = mCurrentAnimation.getColor();
        }
        int backgroundFactorColor = backgroundColor.getRGB();
        WidgetContainer root = mWidgetsScene.getRoot();
        if (root == null) {
            return needsRepaint;
        }
        int xr = transform.getSwingX(root.getDrawX());
        int yr = transform.getSwingY(root.getDrawY());
        int tileSize = transform.getSwingDimension(8);

        if (!DRAW_GRID) {
            mColorSet.setBackgroundPaint(backgroundColor);
            backgroundPaint = backgroundColor;
        } else if (mBackgroundFactorColor != backgroundFactorColor
                || mBackgroundFactorX != xr
                || mBackgroundFactorY != yr
                || mBackgroundFactorSize != tileSize) { // if background inputs changed

            mBackgroundFactorColor = backgroundFactorColor;
            mBackgroundFactorX = xr;
            mBackgroundFactorY = yr;
            mBackgroundFactorSize = tileSize;

            if (tileSize > 0) {
                GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
                GraphicsDevice device = environment.getDefaultScreenDevice();
                GraphicsConfiguration config = device.getDefaultConfiguration();

                BufferedImage image = config.createCompatibleImage(tileSize, tileSize);
                Graphics2D graphics = image.createGraphics();

                graphics.setColor(backgroundColor);
                graphics.fillRect(0, 0, tileSize, tileSize);

                Color backgroundLines = ColorTheme.updateBrightness(backgroundColor, 1.06f);
                graphics.setColor(backgroundLines);
                graphics.drawLine(0, tileSize - 1, tileSize, tileSize - 1);
                graphics.drawLine(tileSize - 1, 0, tileSize - 1, tileSize);

                graphics.dispose();

                backgroundPaint = new TexturePaint(image, new Rectangle(xr + 1, yr + 1, tileSize, tileSize));
                mColorSet.setBackgroundPaint(backgroundPaint);
            }
        }

        if (DRAW_ONLY_ROOT_BACKGROUND) {
            Paint paint = g.getPaint();
            g.setPaint(backgroundPaint);
            g.fillRect(xr, yr, w, h);
            g.setPaint(paint);
        } else {
            g.fillRect((int)transform.getTranslateX(), (int)transform.getTranslateY(), w, h);

            g.setColor(backgroundColor);
            g.fillRect(0, 0, w, h);

            int wr = transform.getSwingDimension(root.getDrawWidth());
            int hr = transform.getSwingDimension(root.getDrawHeight());

            g.setPaint(backgroundPaint);
            g.fillRect((int)transform.getTranslateX(), (int)transform.getTranslateY(), wr, hr);
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
        WidgetCompanion companion = (WidgetCompanion) widget.getCompanionWidget();
        WidgetDecorator decorator = companion.getWidgetDecorator(getCurrentStyle());
        if (!decorator.isSelected()) {
            decorator.updateShowAnchorsPolicy(selectedWidget, selectedAnchor);
        } else {
            decorator.setShowResizeHandles(mWidgetMotion.needToShowDecorations());
            decorator.setShowSizeIndicator(selectedResizeHandle != null);
        }
        if (mMoveOnlyMode) {
            decorator.setShowResizeHandles(false);
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
        decorator.setRepaintableSurface(mRepaintableSurface);
        if (!decorator.isVisible()) {
            return needsRepaint;
        }

        needsRepaint |= decorator.onPaint(transform, g);
        if (container == mWidgetsScene.getRoot()) {
            int xr = transform.getSwingX(container.getDrawX());
            int yr = transform.getSwingY(container.getDrawY());
            int wr = transform.getSwingDimension(container.getDrawWidth());
            int hr = transform.getSwingDimension(container.getDrawHeight());
            if (mDrawOutsideShade && mColorSet.drawBackground()) {
                g.setColor(mColorSet.getSubduedBackground());
                g.fillRect((int) transform.getTranslateX(), (int) transform.getTranslateY(),
                        mViewWidth, yr);
                g.fillRect((int) transform.getTranslateX(), yr + hr, mViewWidth,
                        mViewHeight - yr - hr);
                g.fillRect((int) transform.getTranslateX(), yr, xr, hr);
                g.fillRect(wr + xr, yr, mViewWidth - xr - wr, hr);
                g.setStroke(SnapDraw.sLongDashedStroke);
                g.setColor(mColorSet.getHighlightedFrames());
                g.drawRect(xr, yr, wr, hr);
            }
            if (mDrawResizeHandle) {
                g.setColor(mColorSet.getHighlightedFrames());
                int resizeHandleSize = 10;
                int gap = 8;
                g.setStroke(new BasicStroke(3));
                g.drawLine(xr + wr - resizeHandleSize, yr + hr + gap, xr + wr + gap, yr + hr + gap);
                g.drawLine(xr + wr + gap, yr + hr - resizeHandleSize, xr + wr + gap, yr + hr + gap);
                g.setStroke(new BasicStroke(1));
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
                if (widgetDecorator.isVisible()) {
                    needsRepaint |= widgetDecorator.onPaint(transform, g);
                    widgetDecorator.setRepaintableSurface(mRepaintableSurface);
                }
            }
        }
        if (needsRepaint) {
            repaint();
        }
        return needsRepaint;
    }

    /**
     * Utility function returning the size of an anchor depending on the current scale factor
     *
     * @param scale the current scale factor
     * @return the size of the anchor, in Dp
     */
    public static float getAnchorSize(float scale) {
        float size = 7;
        if (scale < 2f) {
            size = 6;
            if (scale < 1.8f) {
                size = 5;
            }
            if (scale < 1.4f) {
                size = 4;
            }
        }
        return size;
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

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                           RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        WidgetContainer root = mWidgetsScene.getRoot();
        if (root == null) {
            return false;
        }
        mViewWidth = width;
        mViewHeight = height;
        root = root.getRootConstraintContainer();
        if (mApplyConstraints) {
            root.layout();
        }

        // Adapt the anchor size
        ConnectionDraw.CONNECTION_ANCHOR_SIZE = (int) getAnchorSize(transform.getScale());

        ConstraintAnchor selectedAnchor = mSelection.getSelectedAnchor();
        ResizeHandle selectedResizeHandle = mSelection.getSelectedResizeHandle();

        // Let's draw the widgets and their constraints.

        boolean needsRepaint = false;

        WidgetDecorator.setShowAllConstraints(showAllConstraints);

        // First, mark which widgets is selected.
        for (ConstraintWidget widget : mWidgetsScene.getWidgets()) {
            WidgetCompanion widgetCompanion = (WidgetCompanion) widget.getCompanionWidget();
            WidgetDecorator decorator = widgetCompanion.getWidgetDecorator(getCurrentStyle());
            WidgetInteractionTargets widgetInteraction =
                    widgetCompanion.getWidgetInteractionTargets();
            widgetInteraction.updatePosition(transform);
            decorator.setColorSet(mColorSet);
            if (mSelection.contains(widget)) {
                decorator.setIsSelected(true);
            } else {
                decorator.setIsSelected(false);
            }
        }

        // Then, mark highlighted widgets
        animateInCandidateAnchors(selectedAnchor);

        for (ConstraintWidget widget : mWidgetsScene.getWidgets()) {
            WidgetCompanion widgetCompanion = (WidgetCompanion) widget.getCompanionWidget();
            WidgetDecorator decorator = widgetCompanion.getWidgetDecorator(getCurrentStyle());
            decorator.applyLook();
        }

        // Draw the constraints
        for (ConstraintWidget widget : mWidgetsScene.getWidgets()) {
            WidgetCompanion widgetCompanion = (WidgetCompanion) widget.getCompanionWidget();
            WidgetDecorator decorator = widgetCompanion.getWidgetDecorator(getCurrentStyle());
            if (decorator.isVisible() && !decorator.isSelected() && decorator.getLook() !=
                    ColorTheme.Look.HIGHLIGHTED) {
                decorator.onPaintConstraints(transform, g);
            }
        }

        // Draw all the widgets
        ConstraintWidget selectedWidget = null;
        if (mSelection.hasSingleElement()) {
            selectedWidget = mSelection.getFirstElement().widget;
        }
        needsRepaint |= paintWidgets(transform, g, mWidgetsScene.getRoot(), selectedWidget,
                selectedAnchor, selectedResizeHandle);

        // Draw the selected constraints
        for (ConstraintWidget widget : mWidgetsScene.getWidgets()) {
            WidgetCompanion widgetCompanion = (WidgetCompanion) widget.getCompanionWidget();
            WidgetDecorator decorator = widgetCompanion.getWidgetDecorator(getCurrentStyle());
            if (decorator.isVisible() && (decorator.isSelected() || decorator.getLook() ==
                    ColorTheme.Look.HIGHLIGHTED)) {
                decorator.onPaintConstraints(transform, g);
                decorator.onPaintAnchors(transform, g);
                decorator.onPaintActions(transform, g);
            }
        }

        // Draw snap candidates
        g.setColor(mColorSet.getHighlightedSnapGuides());
        for (SnapCandidate candidate : mWidgetMotion.getSimilarMargins()) {
            SnapDraw.drawSnapIndicator(transform, g, candidate);
        }
        g.setColor(mColorSet.getSnapGuides());
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
            g.setColor(mColorSet.getHighlightedConstraints());

            if (!selectedHandle.getAnchor().isConnected()
                    || selectedHandle.getAnchor().getTarget() != anchor) {
                Point lastPoint = mouseInteraction.getLastPoint();
                if (lastPoint.x != 0 && lastPoint.y != 0) {
                    selectedHandle
                      .drawConnection(transform, g, mColorSet, true,
                                      mouseInteraction.getLastPoint());
                }
            }
        }

        if (selectedResizeHandle != null) {
            g.setColor(mColorSet.getSelectionColor());
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
                g.setColor(mColorSet.getSelectionColor());
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
                    ConnectionDraw.drawHorizontalMarginIndicator(g, String.valueOf(w), ax1, ax2, ay1 - 20);
                }
                if (h >= 8) {
                    ConnectionDraw.drawVerticalMarginIndicator(g, String.valueOf(h), ax1 - 20, ay1, ay2);
                }
            }
        }

        if (mSelection.getSelectionBounds() != null) {
            Selection.Element bounds = mSelection.getSelectionBounds();
            g.setColor(mColorSet.getSelectionColor());
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

        if (needsRepaint) {
            repaint();
        }
        return needsRepaint;
    }

    /**
     * Getter for the anchor currently underneath the cursor, if any
     *
     * @return the anchor currently underneath the cursor
     */
    public ConstraintAnchor getCurrentUnderneathAnchor() {
        return mCurrentUnderneathAnchor;
    }

    /**
     * Start an animation for the current anchor (under the mouse)
     *
     * @param underneathAnchor
     */
    public void setCurrentUnderneathAnchor(ConstraintAnchor underneathAnchor) {
        if (mCurrentUnderneathAnchor != underneathAnchor) {
            mCurrentUnderneathAnchor = underneathAnchor;
            mChoreographer.removeAnimation(mAnimationCurrentAnchor);
            if (mCurrentUnderneathAnchor != null) {
                ConstraintHandle constraintHandle =
                        WidgetInteractionTargets.constraintHandle(mCurrentUnderneathAnchor);
                mAnimationCurrentAnchor = new AnimatedHoverAnchor(mColorSet, constraintHandle);
                mChoreographer.addAnimation(mAnimationCurrentAnchor);
            } else {
                mAnimationCurrentAnchor = null;
            }
        }
    }

    public void setMoveOnlyMode(boolean moveOnlyMode) {
        mMoveOnlyMode = moveOnlyMode;
    }

    /**
     * Enable or disable the application of constraints during painting
     *
     * @param applyConstraints
     */
    public void setApplyConstraints(boolean applyConstraints) {
        this.mApplyConstraints = applyConstraints;
    }

    /**
     * Return the current style from the color set
     *
     * @return the current colorset style
     */
    public int getCurrentStyle() {
        if (mColorSet == null) {
            return WidgetDecorator.BLUEPRINT_STYLE;
        }
        return mColorSet.getStyle();
    }
}
