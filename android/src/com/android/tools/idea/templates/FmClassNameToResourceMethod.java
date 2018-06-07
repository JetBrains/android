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

import com.android.tools.idea.templates.AssetNameConverter.Type;
import freemarker.template.*;

import java.util.List;

/**
 * Similar to {@link FmCamelCaseToUnderscoreMethod}, but strips off common class
 * suffixes such as "Activity", "Fragment", etc.
 */
public class FmClassNameToResourceMethod implements TemplateMethodModelEx {
    @Override
    public TemplateModel exec(List args) throws TemplateModelException {
        if (args.size() != 1) {
            throw new TemplateModelException("Wrong arguments");
        }

        String name = ((TemplateScalarModel)args.get(0)).getAsString();

        if (name.isEmpty()) {
            return new SimpleScalar("");
        }

        return new SimpleScalar(new AssetNameConverter(Type.CLASS_NAME, name).getValue(Type.RESOURCE));
    }
}