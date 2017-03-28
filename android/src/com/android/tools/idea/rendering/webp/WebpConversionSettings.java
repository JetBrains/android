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
package com.android.tools.idea.rendering.webp;

// TODO: Preserve preferences across IDE sessions?
// Perhaps not very useful since most developers won't do this more than once.
// Also, some of these defaults are going to depend on the minSdkVersion,
// which varies by module.
public class WebpConversionSettings {
  public boolean lossless = false;
  public boolean allowLossless = true;
  public int quality = 75;
  public boolean previewConversion = true;
  public boolean skipNinePatches = true;
  public boolean skipTransparentImages = true;
  public boolean skipLargerImages = true;
  public boolean skipAnimated = true;
}