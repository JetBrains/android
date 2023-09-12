/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.profilers.memory;

import com.android.tools.profilers.sessions.SessionArtifactView;
import icons.StudioIcons;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link SessionArtifactView} that represents a native memory capture via heapprofd.
 */
public final class HeapProfdArtifactView extends SessionArtifactView<HeapProfdSessionArtifact> {

  public HeapProfdArtifactView(@NotNull ArtifactDrawInfo artifactDrawInfo, @NotNull HeapProfdSessionArtifact artifact) {
    super(artifactDrawInfo, artifact);
  }

  @Override
  @NotNull
  protected JComponent buildComponent() {
    return buildCaptureArtifactView(getArtifact().getName(), getArtifact().getSubtitle(), StudioIcons.Profiler.Sessions.HEAP,
                                    getArtifact().isOngoing());
  }
}
