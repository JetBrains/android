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
package com.android.tools.profilers.analytics.energy;

import com.android.tools.profiler.proto.Common;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Class with metadata related to an energy duration event.
 */
public final class EnergyEventMetadata {
  private final List<Common.Event> mySubevents = new ArrayList<>();

  public EnergyEventMetadata(Collection<Common.Event> subevents) {
    mySubevents.addAll(subevents);
  }

  public List<Common.Event> getSubevents() {
    return mySubevents;
  }
}
