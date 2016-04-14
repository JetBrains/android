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
package com.android.tools.idea.editors.gfxtrace.service.path;

public interface PathListener {
  void notifyPath(PathEvent event);

  class PathEvent {
    public final Path path;
    public final Object source;

    public PathEvent(Path path, Object source) {
      this.path = path;
      this.source = source;
    }

    /**
     * Returns a path of the requested type if this event's path or one of its ancestors is of the given type, or {@code null}.
     */
    public <T extends Path> T findPathOfType(Class<T> cls) {
      Path p = path;
      while (p != null) {
        if (cls.isInstance(p)) {
          return cls.cast(p);
        }
        p = p.getParent();
      }
      return null;
    }

    public DevicePath findDevicePath() {
      return findPathOfType(DevicePath.class);
    }

    public CapturePath findCapturePath() {
      return findPathOfType(CapturePath.class);
    }

    public AtomPath findAtomPath() {
      return findPathOfType(AtomPath.class);
    }

    public MemoryRangePath findMemoryPath() {
      return findPathOfType(MemoryRangePath.class);
    }

    public StatePath findStatePath() {
      return findPathOfType(StatePath.class);
    }
  }
}
