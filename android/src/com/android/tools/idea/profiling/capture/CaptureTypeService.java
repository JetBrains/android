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
package com.android.tools.idea.profiling.capture;

import com.google.common.collect.Maps;
import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

public class CaptureTypeService {

  @NotNull Map<Class<? extends CaptureType>, CaptureType> myCaptureTypes = Maps.newHashMap();

  @NotNull
  public static CaptureTypeService getInstance() {
    return ServiceManager.getService(CaptureTypeService.class);
  }

  public <T extends CaptureType> void register(Class<T> clazz, T type) {
    myCaptureTypes.put(clazz, type);
  }

  @NotNull
  public Collection<CaptureType> getCaptureTypes() {
    return myCaptureTypes.values();
  }

  @Nullable
  public <T extends CaptureType> T getType(Class<T> type) {
    return (T)myCaptureTypes.get(type);
  }
}
