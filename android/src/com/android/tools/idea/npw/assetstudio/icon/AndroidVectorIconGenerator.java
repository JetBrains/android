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
package com.android.tools.idea.npw.assetstudio.icon;

import com.android.resources.Density;
import com.android.tools.idea.npw.assetstudio.GraphicGenerator;
import com.android.tools.idea.npw.assetstudio.VectorIconGenerator;
import com.android.tools.idea.npw.assetstudio.assets.BaseAsset;
import com.android.tools.idea.npw.assetstudio.assets.VectorAsset;
import org.jetbrains.annotations.NotNull;

/**
 * Settings when generating a preview for a {@link VectorAsset}
 */
public final class AndroidVectorIconGenerator extends AndroidIconGenerator {

  public AndroidVectorIconGenerator(int minSdkVersion) {
    super(minSdkVersion, new VectorIconGenerator());
  }

  @Override
  @NotNull
  public GraphicGenerator.Options createOptions(boolean forPreview) {
    VectorIconGenerator.VectorIconOptions options = new VectorIconGenerator.VectorIconOptions();
    options.minSdk = getMinSdkVersion();
    BaseAsset asset = sourceAsset().getValueOrNull();
    if (asset != null) {
      options.sourceImageFuture = asset.toImage();
      options.isTrimmed = asset.trimmed().get();
      options.paddingPercent = asset.paddingPercent().get();
    }

    options.density = Density.ANYDPI;
    return options;
  }
}
