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
package com.android.tools.idea.templates;

import com.android.ide.common.resources.ValueXmlHelper;
import freemarker.template.*;

import java.util.List;

/**
 * Method invoked by FreeMarker to escape a string such that it can be placed
 * as text in a string resource file.
 * This is similar to {@link FmEscapeXmlTextMethod}, but in addition to escaping
 * &lt; and &amp; it also escapes characters such as quotes necessary for Android
 *{@code <string>} elements.
 */
public class FmEscapeXmlStringMethod implements TemplateMethodModelEx {
    @Override
    public TemplateModel exec(List args) throws TemplateModelException {
        if (args.size() != 1) {
            throw new TemplateModelException("Wrong arguments");
        }
        String string = ((TemplateScalarModel)args.get(0)).getAsString();
        return new SimpleScalar(ValueXmlHelper.escapeResourceString(string));
    }
}