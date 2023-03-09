/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.crash;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.analytics.crash.CrashReport;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.intellij.ui.ExperimentalUI;
import java.util.Map;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginKindProviderKt;

/** Crash report that uses Android Studio product id. */
public abstract class BaseStudioReport extends CrashReport {
  public BaseStudioReport(@Nullable String version,
                          @Nullable Map<String, String> productData,
                          @NonNull String type) {
    super(StudioCrashReporter.PRODUCT_ANDROID_STUDIO, version, productData, type);
  }

  @Override
  protected void serializeTo(@NonNull MultipartEntityBuilder builder) {
    AndroidStudioEvent.IdeBrand ideBrand = UsageTracker.getIdeBrand();
    builder.addTextBody("ideBrand", ideBrand.getValueDescriptor().getName());

    String isNewUI = "Unknown";
    try {
      isNewUI = Boolean.toString(ExperimentalUI.isNewUI());
    } catch (Throwable ignore) {
    }
    builder.addTextBody("isNewUI", isNewUI);

    String isKotlinK2 = "Unknown";
    try {
      isKotlinK2 = Boolean.toString(KotlinPluginKindProviderKt.isK2Plugin());
    } catch (Throwable ignore) {
    }
    builder.addTextBody("isKotlinK2", isKotlinK2);
  }
}
