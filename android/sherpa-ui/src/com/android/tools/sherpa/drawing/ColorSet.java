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

import java.awt.Color;

/**
 * Holds a set of colors for drawing a scene
 */
public class ColorSet {
    protected Color BlueprintBackground;
    protected Color BlueprintBackgroundLines;

    protected Color BlueprintFrames;
    protected Color BlueprintConstraints;
    protected Color BlueprintText;
    protected Color BlueprintHighlightFrames;

    protected Color BlueprintSnapGuides;
    protected Color BlueprintSnapLightGuides;

    protected Color DarkBlueprintBackground;
    protected Color DarkBlueprintBackgroundLines;
    protected Color DarkBlueprintFrames;

    protected Color InspectorBackgroundColor;
    protected Color InspectorFillColor;

    protected Color InspectorTrackBackgroundColor;
    protected Color InspectorTrackColor;
    protected Color InspectorHighlightsStrokeColor;


    public ColorSet() {
    }

    public Color getBlueprintBackground() { return BlueprintBackground; }

    public Color getBlueprintFrames() { return BlueprintFrames; }

    public Color getBlueprintConstraints() { return BlueprintConstraints; }

    public Color getBlueprintText() { return BlueprintText; }

    public Color getBlueprintHighlightFrames() { return BlueprintHighlightFrames; }

    public Color getBlueprintSnapGuides() { return BlueprintSnapGuides; }

    public Color getBlueprintSnapLightGuides() { return BlueprintSnapLightGuides; }

    public Color getInspectorStroke() { return BlueprintFrames; }

    public Color getDarkBlueprintBackground() {
        return DarkBlueprintBackground;
    }

    public Color getDarkBlueprintBackgroundLines() {
        return DarkBlueprintBackgroundLines;
    }

    public Color getDarkBlueprintFrames() {
        return DarkBlueprintFrames;
    }

    public Color getInspectorBackgroundColor() { return InspectorBackgroundColor; }

    public Color getInspectorFillColor() { return InspectorFillColor; }

    public Color getInspectorTrackBackgroundColor() { return InspectorTrackBackgroundColor; }

    public Color getInspectorTrackColor() { return InspectorTrackColor; }

    public Color getInspectorHighlightsStrokeColor() { return InspectorHighlightsStrokeColor; }
}
