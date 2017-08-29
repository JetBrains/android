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
import java.awt.image.BufferedImage;

/**
 * Generate icons for the vector drawable.
 * We need the image in original format and size, therefore, there is no extra
 * operation on the sourceImage.
 */
public class VectorIconGenerator extends GraphicGenerator {
    @NonNull
    @Override
    public BufferedImage generate(
            @NonNull GraphicGeneratorContext context, @NonNull Options options) {
        return options.sourceImage;
    }

    public static class VectorIconOptions extends GraphicGenerator.Options {
        public VectorIconOptions() {
            iconFolderKind = IconFolderKind.DRAWABLE_NO_DPI;
        }
    }
}
