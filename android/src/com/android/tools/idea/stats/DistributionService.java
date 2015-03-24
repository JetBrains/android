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

import com.android.sdklib.repository.FullRevision;
import com.google.common.collect.ImmutableList;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ResourceUtil;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for getting information on Android versions, including usage percentages.
 */

public class DistributionService {
  private static final Logger LOG = Logger.getInstance(DistributionService.class);
  private List<Distribution> myDistributions = null;

  private static final DistributionService INSTANCE = new DistributionService();

  public static DistributionService getInstance() {
    return INSTANCE;
  }

  @Nullable
  public List<Distribution> getDistributions() {
    if (myDistributions == null) {
      return null;
    }
    return ImmutableList.copyOf(myDistributions);
  }

  public double getSupportedDistributionForApiLevel(int apiLevel) {
    double unsupportedSum = 0;
    for (Distribution d : myDistributions) {
      if (d.getApiLevel() >= apiLevel) {
        break;
      }
      unsupportedSum += d.getDistributionPercentage();
    }
    return 1 - unsupportedSum;
  }

  @Nullable
  public Distribution getDistributionForApiLevel(int apiLevel) {
    for (Distribution d : myDistributions) {
      if (d.getApiLevel() == apiLevel) {
        return d;
      }
    }
    return null;
  }

  private DistributionService() {
    loadFromFile();
  }

  private void loadFromFile() {
    // TODO: pull current distribution data on demand
    try {
      String jsonString = ResourceUtil.loadText(ResourceUtil.getResource(this.getClass(), "wizardData", "distributions.json"));
      myDistributions = loadDistributionsFromJson(jsonString);
    } catch (IOException e) {
      LOG.error("Error while trying to load distributions file", e);
    }
  }

  @Nullable
  private static List<Distribution> loadDistributionsFromJson(String jsonString) {
    Type fullRevisionType = new TypeToken<FullRevision>(){}.getType();
    GsonBuilder gsonBuilder = new GsonBuilder()
      .registerTypeAdapter(fullRevisionType, new JsonDeserializer<FullRevision>() {
        @Override
        public FullRevision deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
          return FullRevision.parseRevision(json.getAsString());
        }
      });
    Gson gson = gsonBuilder.create();
    Type listType = new TypeToken<ArrayList<Distribution>>() {}.getType();
    try {
      return gson.fromJson(jsonString, listType);
    } catch (JsonParseException e) {
      LOG.error("Parse exception while reading distributions.json", e);
    }
    return null;
  }
}
