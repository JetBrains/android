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
package com.android.tools.idea.gradle.project.sync.compatibility;

import com.android.annotations.VisibleForTesting;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.util.io.HttpRequests;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Checks if there are component version metadata updates from a remote server. The check is perform on IDE startup, once, every day.
 */
public class CompatibilityChecksMetadataUpdater extends ApplicationComponent.Adapter {
  private static final Logger LOG = Logger.getInstance(CompatibilityChecksMetadataUpdater.class);

  private static final String LAST_CHECK_TIMESTAMP_PROPERTY_NAME = "android-component-compatibility-check";

  public CompatibilityChecksMetadataUpdater() {
    String checkIntervalProperty = System.getProperty("android.version.compatibility.check.interval");
    final CheckInterval checkInterval = CheckInterval.find(checkIntervalProperty);

    if (checkInterval != CheckInterval.NONE) {
      Application app = ApplicationManager.getApplication();
      app.getMessageBus().connect(app).subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener() {
        @Override
        public void appFrameCreated(String[] commandLineArgs, @NotNull Ref<Boolean> willOpenProject) {
          long lastUpdateCheck = PropertiesComponent.getInstance().getOrInitLong(LAST_CHECK_TIMESTAMP_PROPERTY_NAME, -1);
          if (checkInterval.needsUpdate(lastUpdateCheck)) {
            fetchVersionMetadataUpdate(false);
          }
        }
      });
    }
  }

  public void fetchVersionMetadataUpdate() {
    fetchVersionMetadataUpdate(true);
  }

  private static void fetchVersionMetadataUpdate(boolean startedByUser) {
    fetchMetadata().doWhenDone(() -> {
      long now = System.currentTimeMillis();
      PropertiesComponent.getInstance().setValue(LAST_CHECK_TIMESTAMP_PROPERTY_NAME, String.valueOf(now));
    });
  }

  @NotNull
  private static ActionCallback fetchMetadata() {
    ActionCallback callback = new ActionCallback();
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      String url = "https://dl.google.com/android/studio/metadata/android-component-compatibility.xml";
      try {
        Document metadata = HttpRequests.request(url).connect(request -> {
          try {
            return JDOMUtil.loadDocument(request.getInputStream());
          }
          catch (JDOMException e) {
            LOG.info("Failed to parse XML metadata", e);
            return null;
          }
          catch (Throwable e) {
            // Some other unexpected error related to JRE setup, e.g.
            // java.lang.NoClassDefFoundError: Could not initialize class javax.crypto.SunJCE_b
            //     at javax.crypto.KeyGenerator.a(DashoA13*..)
            //     ....
            // See http://b.android.com/149270 for more.
            LOG.info("Failed to parse XML metadata", e);
            return null;
          }
        });
        if (metadata != null) {
          VersionCompatibilityChecker.getInstance().updateMetadata(metadata);
          callback.setDone();
        }
      }
      catch (IOException e) {
        LOG.info(String.format("Failed to connect to '%1$s'", url), e);
      }
      callback.setRejected();
    });
    return callback;
  }

  @VisibleForTesting
  enum CheckInterval {
    NONE(Long.MAX_VALUE), DAILY(MILLISECONDS.convert(1, DAYS)), WEEKLY(MILLISECONDS.convert(7, DAYS)), TESTING(-1L);

    private final long myIntervalInMs;

    CheckInterval(long intervalInMs) {
      myIntervalInMs = intervalInMs;
    }

    boolean needsUpdate(long lastUpdateTimestampInMs) {
      return System.currentTimeMillis() - lastUpdateTimestampInMs >= myIntervalInMs;
    }

    @NotNull
    static CheckInterval find(@Nullable String value) {
      if (isNotEmpty(value)) {
        for (CheckInterval checkInterval : values()) {
          if (value.equalsIgnoreCase(checkInterval.name())) {
            return checkInterval;
          }
        }
      }
      return WEEKLY;
    }
  }
}
