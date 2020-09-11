/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.profilers;

import com.android.tools.adtui.trackgroup.DefaultTrackRenderer;
import com.android.tools.adtui.trackgroup.TrackRenderer;
import com.android.tools.adtui.trackgroup.TrackRendererFactory;
import com.android.tools.profilers.cpu.BufferQueueTrackRenderer;
import com.android.tools.profilers.cpu.CpuCoreTrackRenderer;
import com.android.tools.profilers.cpu.CpuFrequencyTrackRenderer;
import com.android.tools.profilers.cpu.CpuThreadTrackRenderer;
import com.android.tools.profilers.cpu.FramesTrackRenderer;
import com.android.tools.profilers.cpu.RssMemoryTrackRenderer;
import com.android.tools.profilers.cpu.SurfaceflingerTrackRenderer;
import com.android.tools.profilers.cpu.VsyncTrackRenderer;
import com.android.tools.profilers.customevent.CustomEventTrackRenderer;
import com.android.tools.profilers.event.LifecycleTrackRenderer;
import com.android.tools.profilers.event.UserEventTrackRenderer;
import org.jetbrains.annotations.NotNull;

/**
 * Implementation of {@link TrackRendererFactory} for creating track renderers used in profilers.
 */
public class ProfilerTrackRendererFactory implements TrackRendererFactory<ProfilerTrackRendererType> {
  @NotNull private final StudioProfilersView myProfilersView;

  public ProfilerTrackRendererFactory(@NotNull StudioProfilersView profilersView) {
    myProfilersView = profilersView;
  }

  @NotNull
  @Override
  public TrackRenderer<?, ProfilerTrackRendererType> createRenderer(@NotNull ProfilerTrackRendererType rendererType) {
    switch (rendererType) {
      case APP_LIFECYCLE:
        return new LifecycleTrackRenderer();
      case USER_INTERACTION:
        return new UserEventTrackRenderer();
      case FRAMES:
        return new FramesTrackRenderer();
      case SURFACEFLINGER:
        return new SurfaceflingerTrackRenderer();
      case VSYNC:
        return new VsyncTrackRenderer();
      case BUFFER_QUEUE:
        return new BufferQueueTrackRenderer();
      case CPU_THREAD:
        return new CpuThreadTrackRenderer(myProfilersView);
      case CPU_CORE:
        return new CpuCoreTrackRenderer();
      case CPU_FREQUENCY:
        return new CpuFrequencyTrackRenderer();
      case RSS_MEMORY:
        return new RssMemoryTrackRenderer();
      case CUSTOM_EVENTS:
        return new CustomEventTrackRenderer();
      default:
        return new DefaultTrackRenderer<>();
    }
  }
}
