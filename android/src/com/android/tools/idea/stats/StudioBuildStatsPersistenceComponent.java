/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.stats;

import com.android.annotations.VisibleForTesting;
import com.intellij.internal.statistic.connect.StatisticsResult;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Stores "Android Build Statistics" records.
 * This is a quick throw-away implementation.
 *
 * A build record is composed of:
 * - an UTC epoch long timestamp (System.currentTimeMillis), when the even occurred.
 * - a list of key/value pairs (string => string).
 *
 * This component collects the build records. It merely acts as a synchronized
 * persistent storage for the records with a maximum upper bound.
 *
 * The {@link AndroidStatisticsService} is responsible for actually sending the
 * records to the statistic collection server based on the current user settings
 * (i.e. whether stats are enabled and at which frequency they are sent.. this is
 * not controlled here.)
 */
@State(
  name = "StudioBuildStatistic",
  roamingType = RoamingType.DISABLED,
  storages = {
    @Storage(
      file = StoragePathMacros.APP_CONFIG + "/studio.build.statistics.xml"
    )})
public class StudioBuildStatsPersistenceComponent
  implements ApplicationComponent, PersistentStateComponent<Element> {

  private static final int MAX_RECORDS = 1000;
  private static final String TAG_RECORD = "record";
  private static final String TAG_VALUE = "value";
  private static final String ATTR_UTC_MS = "utc_ms";
  private static final String ATTR_KEY = "key";
  private static final String ATTR_VALUE = "value";

  private final LinkedList<BuildRecord> myRecords = new LinkedList<BuildRecord>();

  private final SequentialTaskExecutor myTaskExecutor = new SequentialTaskExecutor(PooledThreadExecutor.INSTANCE);

  /**
   * Retrieves an instance of the component or null if not available or configured.
   *
   * @return an instance of the component or null.
   */
  @Nullable
  public static StudioBuildStatsPersistenceComponent getInstance() {
    Application app = ApplicationManager.getApplication();
    return app == null ? null : app.getComponent(StudioBuildStatsPersistenceComponent.class);
  }

  /**
   * Adds one build record.
   * <p/>
   * This checks the {@link AndroidStatisticsService}, if available:
   * this does nothing if stats have not been authorized to be collected.
   *
   * @param newRecord to be added to the internal queue.
   */
  public void addBuildRecord(@NotNull final BuildRecord newRecord) {
    myTaskExecutor.execute(new Runnable() {
      @Override
      public void run() {
        addBuildRecordImmediately(newRecord);
      }
    });
  }

  @VisibleForTesting
  void addBuildRecordImmediately(@NotNull BuildRecord newRecord) {
    // Skip if there is no Application, allowing this to run using non-idea unit tests.
    Application app = ApplicationManager.getApplication();
    if (app != null && !app.isUnitTestMode()) {
      StatisticsResult code = AndroidStatisticsService.areStatisticsAuthorized();
      if (code.getCode() != StatisticsResult.ResultCode.SEND) {
        // Don't even collect the stats.
        return;
      }
    }

    synchronized (myRecords) {
      myRecords.add(newRecord);

      // Limit the size of the queue to something reasonable.
      while (myRecords.size() > MAX_RECORDS) {
        myRecords.removeFirst();
      }
    }
  }

  public StudioBuildStatsPersistenceComponent() {
    // nop
  }

  // Visibility set to package-default for testing. Clients should use getFirstRecord().
  @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
  @NotNull
  LinkedList<BuildRecord> getRecords() {
    return myRecords;
  }

  /**
   * Returns the first record or null if there are no records.
   */
  @Nullable
  BuildRecord getFirstRecord() {
    synchronized (myRecords) {
      if (!myRecords.isEmpty()) {
        return myRecords.removeFirst();
      }
      return null;
    }
  }

  /**
   * Returns true if the record list is not empty.
   * When returning true, the next #getFirstRecord() should not return null.
   */
  boolean hasRecords() {
    synchronized (myRecords) {
      return !myRecords.isEmpty();
    }
  }

  // --- ApplicationComponent implementation

  @Override
  public void initComponent() {
    // nop
  }

  @Override
  public void disposeComponent() {
    // nop
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "StudioBuildStatsPersistenceComponent";
  }

  // --- PersistentStateComponent implementation

  @Override
  public void loadState(@NotNull Element state) {
    synchronized (myRecords) {
      for (Object record : state.getChildren(TAG_RECORD)) {
        Element recordElement = (Element) record;

        long timestampMs = Long.valueOf(recordElement.getAttributeValue(ATTR_UTC_MS));

        List<KeyString> data = new ArrayList<KeyString>();

        for (Object kv : recordElement.getChildren(TAG_VALUE)) {
          Element valueElement = (Element) kv;
          data.add(new KeyString(valueElement.getAttributeValue(ATTR_KEY),
                                 valueElement.getAttributeValue(ATTR_VALUE)));
        }

        myRecords.add(new BuildRecord(timestampMs, data));
      }
    }
  }

  @Nullable
  @Override
  public Element getState() {
    Element element = new Element("state");

    synchronized (myRecords) {
      for (BuildRecord record : myRecords) {
        KeyString[] data = record.getData();
        if (data.length == 0) {
          continue;
        }

        Element recordElement = new Element(TAG_RECORD);
        recordElement.setAttribute(ATTR_UTC_MS, Long.toString(record.getUtcTimestampMs()));
        for (KeyString kv : data) {
          Element valueElement = new Element(TAG_VALUE);
          valueElement.setAttribute(ATTR_KEY, kv.getKey());
          valueElement.setAttribute(ATTR_VALUE, kv.getValue());
          recordElement.addContent(valueElement);
        }

        element.addContent(recordElement);
      }
    }

    return element;
  }
}
