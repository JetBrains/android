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

import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.PluginUtil;
import com.intellij.openapi.extensions.PluginId;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StudioExceptionReport extends BaseStudioReport {

  public static final String KEY_EXCEPTION_INFO = "exception_info";

  @NotNull private final String exceptionInfo;
  private final boolean userReported;
  @Nullable private final String fullStack;
  @NotNull private final Map<String,String> logs;
  @Nullable private final PluginId pluginId;

  private StudioExceptionReport(@Nullable String version,
                                @NotNull String exceptionInfo,
                                boolean userReported,
                                @Nullable String fullStack,
                                @NotNull Map<String,String> logs,
                                @Nullable PluginId pluginId,
                                @Nullable Map<String, String> productData) {
    super(version, productData, "Exception");
    this.exceptionInfo = exceptionInfo;
    this.userReported = userReported;
    this.fullStack = fullStack;
    this.logs = logs;
    this.pluginId = pluginId;
  }

  @Override
  protected void serializeTo(@NotNull MultipartEntityBuilder builder) {
    super.serializeTo(builder);

    // Capture kotlin version for kotlin exceptions.
    if (isKotlinOnStack()) {
      builder.addTextBody("kotlinVersion", getKotlinPluginVersionDescription());
    }
    try {
      IdeaPluginDescriptor plugin = PluginManagerCore.getPlugin(pluginId);
      if (plugin != null) {
        final String name = plugin.getName();
        final String version = plugin.getVersion();
        builder.addTextBody("plugin.name", name);
        builder.addTextBody("plugin.version", version);
      }
    } catch (Throwable ignored) {
      builder.addTextBody("plugin.name", "exceptionWhileReadingNameVersion");
    }
    builder.addTextBody("userReported", userReported ? "1" : "0");

    builder.addTextBody(KEY_EXCEPTION_INFO, exceptionInfo);

    if (fullStack != null) {
      builder.addTextBody("fullStack", fullStack);
    }
    // TODO: logs as files
    logs.forEach((name, logContent) -> {
      String filename = "log_" + name;
      builder.addBinaryBody("log_" + name, logContent.getBytes(StandardCharsets.UTF_8), ContentType.TEXT_PLAIN, filename + ".log");
    });

  }

  private boolean isKotlinOnStack() {
    return exceptionInfo.contains("\tat org.jetbrains.kotlin");
  }

  @VisibleForTesting
  @NotNull
  protected String getKotlinPluginVersionDescription() {
    try {
      IdeaPluginDescriptor[] pluginDescriptors = PluginManagerCore.getPlugins();
      return Arrays.stream(pluginDescriptors)
                   .filter(d -> d.getPluginId() != null &&
                                d.getPluginId().getIdString().equals("org.jetbrains.kotlin") &&
                                d.isEnabled())
                   .findFirst()
                   .map(d -> d.getVersion())
                   .orElse("pluginNotLoaded");
    }
    catch (Exception ignored) {
      return "exceptionWhenReadingVersion";
    }
  }

  public static class Builder extends BaseBuilder<StudioExceptionReport, Builder> {
    private String exceptionInfo;
    private boolean userReported;
    private PluginId pluginId;
    private Map<String,String> logs;
    private String fullStack;

    @Override
    protected Builder getThis() {
      return this;
    }

    @NotNull
    public Builder setThrowable(@NotNull Throwable throwable, boolean userReported, boolean includeLogs) {
      Throwable cause = getRootCause(throwable);
      ExceptionDataCollection service = ExceptionDataCollection.getInstance();
      UploadFields uploadFields = service.getExceptionUploadFields(throwable, userReported, includeLogs);
      this.exceptionInfo = uploadFields.getDescription();
      this.userReported = userReported;
      this.logs = uploadFields.getLogs();
      this.pluginId = PluginUtil.getInstance().findPluginId(cause);
      return this;
    }

    @Override
    public StudioExceptionReport build() {
      return new StudioExceptionReport(getVersion(), exceptionInfo, userReported, fullStack, logs, pluginId, getProductData());
    }
  }

  // Similar to ExceptionUtil.getRootCause, but attempts to avoid infinite recursion
  @NotNull
  public static Throwable getRootCause(@NotNull Throwable t) {
    int depth = 0;
    while (depth++ < 20) {
      if (t.getCause() == null) return t;
      t = t.getCause();
    }
    return t;
  }

  @NotNull
  public static String getDescription(@NotNull Throwable t) {
    return ExceptionDataCollection.getInstance().getDescription(t, true, false);
  }
}
