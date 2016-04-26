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

import com.android.tools.sherpa.drawing.decorator.WidgetDecorator;
import com.android.tools.sherpa.interaction.WidgetInteractionTargets;
import com.google.tnt.solver.widgets.ConstraintWidget;

import java.util.ArrayList;

/**
 * The associated widget companion holder
 */
public class WidgetCompanion {
    ArrayList<WidgetDecorator> mWidgetDecorators = new ArrayList<>();
    Object mWidgetModel;
    Object mWidgetTag;
    WidgetInteractionTargets mWidgetInteractionTargets;

    public static WidgetCompanion create(ConstraintWidget widget) {
        WidgetCompanion companion = new WidgetCompanion();

        WidgetDecorator blueprintDecorator = new WidgetDecorator(widget);
        blueprintDecorator.setStyle(WidgetDecorator.BLUEPRINT_STYLE);
        WidgetDecorator androidDecorator = new WidgetDecorator(widget);
        androidDecorator.setStyle(WidgetDecorator.ANDROID_STYLE);

        companion.addDecorator(blueprintDecorator);
        companion.addDecorator(androidDecorator);

        companion.setWidgetInteractionTargets(new WidgetInteractionTargets(widget));
        return companion;
    }

    public WidgetInteractionTargets getWidgetInteractionTargets() {
        return mWidgetInteractionTargets;
    }

    public WidgetDecorator getWidgetDecorator(int style) {
        for (WidgetDecorator decorator : mWidgetDecorators) {
            if (decorator.getStyle() == style) {
                return decorator;
            }
        }
        return null;
    }

    public void addDecorator(WidgetDecorator decorator) {
        mWidgetDecorators.add(decorator);
    }

    public void setWidgetInteractionTargets(WidgetInteractionTargets widgetInteractionTargets) {
        mWidgetInteractionTargets = widgetInteractionTargets;
    }

    public Object getWidgetModel() {
        return mWidgetModel;
    }

    public void setWidgetModel(Object model) { mWidgetModel = model; }

    public void setWidgetTag(Object tag) { mWidgetTag = tag; }

    public Object getWidgetTag() { return mWidgetTag; }
}
