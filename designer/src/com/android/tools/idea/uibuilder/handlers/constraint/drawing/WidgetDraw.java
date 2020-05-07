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

package com.android.tools.idea.uibuilder.handlers.constraint.drawing;

import static com.android.tools.idea.uibuilder.handlers.constraint.drawing.ConnectionDraw.sFont;

import com.android.tools.idea.common.scene.draw.ColorSet;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Polygon;
import java.awt.Stroke;
import java.awt.geom.RoundRectangle2D;

/**
 * Utility drawing class
 * Contains functions dealing with drawing widgets
 */
public class WidgetDraw {

    // TODO: fix the loading image pattern
    public static Image sGuidelinePercent = null;
    public static Image sGuidelineArrowLeft = null;
    public static Image sGuidelineArrowRight = null;
    public static Image sGuidelineArrowUp = null;
    public static Image sGuidelineArrowDown = null;

    // Used for drawing the tooltips

    private static final Polygon sTooltipTriangleDown = new Polygon();
    private static final Polygon sTooltipTriangleUp = new Polygon();
    private static final int sArrowBase = 3;
    private static final int sArrowHeight = 3;

    static {
        sTooltipTriangleDown.addPoint(-sArrowBase, 0);
        sTooltipTriangleDown.addPoint(0, sArrowHeight);
        sTooltipTriangleDown.addPoint(sArrowBase, 0);
        sTooltipTriangleUp.addPoint(-sArrowBase, 0);
        sTooltipTriangleUp.addPoint(0, -sArrowHeight);
        sTooltipTriangleUp.addPoint(sArrowBase, 0);
    }
}
