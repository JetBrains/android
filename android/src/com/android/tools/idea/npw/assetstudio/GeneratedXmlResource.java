/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.idea.npw.assetstudio;

import com.android.annotations.NonNull;

import java.nio.file.Path;

/** A {@link GeneratedIcon} that is defined by an XML document. */
public final class GeneratedXmlResource extends GeneratedIcon {
    @NonNull private final String name;
    @NonNull private final Path outputPath;
    @NonNull private final IconCategory category;
    @NonNull private final String xmlText;

    public GeneratedXmlResource(
            @NonNull String name,
            @NonNull Path outputPath,
            @NonNull IconCategory category,
            @NonNull String xmlText) {
        this.name = name;
        this.outputPath = outputPath;
        this.category = category;
        this.xmlText = xmlText;
    }

    @NonNull
    @Override
    public String getName() {
        return name;
    }

    @NonNull
    @Override
    public Path getOutputPath() {
        return outputPath;
    }

    @NonNull
    @Override
    public IconCategory getCategory() {
        return category;
    }

    @NonNull
    public String getXmlText() {
        return xmlText;
    }
}
