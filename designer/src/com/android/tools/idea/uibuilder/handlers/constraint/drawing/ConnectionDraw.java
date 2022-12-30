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

import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.Stroke;

/**
 * Utility drawing class
 * <p/>
 * Contains functions dealing with drawing connection between ConstraintAnchors
 */
public final class ConnectionDraw {
    static Font sFont = new Font("Helvetica", Font.PLAIN, 12);

    public static final Stroke
            sDashedStroke = new BasicStroke(1, BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_BEVEL, 0, new float[] { 2 }, 0);

    public static final int ARROW_SIDE = 5;

    public static int CONNECTION_ANCHOR_SIZE = 6;
    public static final int CONNECTION_ARROW_SIZE = 3;
    static final int CONNECTION_RESIZE_SIZE = 4;
}
