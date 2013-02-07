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

import freemarker.template.SimpleScalar;
import freemarker.template.TemplateMethodModel;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;

import java.util.List;

/**
 * Method invoked by FreeMarker to extract letters from a string; this will remove
 * any whitespace, punctuation and digits.
 */
public class FmExtractLettersMethod implements TemplateMethodModel {
    @Override
    public TemplateModel exec(List args) throws TemplateModelException {
        if (args.size() != 1) {
            throw new TemplateModelException("Wrong arguments");
        }
        String string = args.get(0).toString();
        StringBuilder sb = new StringBuilder(string.length());
        for (int i = 0, n = string.length(); i < n; i++) {
            char c = string.charAt(i);
            if (Character.isLetter(c)) {
                sb.append(c);
            }
        }
        return new SimpleScalar(sb.toString());
    }
}