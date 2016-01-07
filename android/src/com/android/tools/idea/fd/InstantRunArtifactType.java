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
package com.android.tools.idea.fd;

/**
 * {@link InstantRunArtifactType} lists the possible artifacts as specified by the type attribute of artifact tag in the build-info.xml
 * The names given here match the names expected in the XML file.
 */
public enum InstantRunArtifactType {
  /** Main APK (contains resources) */
  MAIN,

  /** APK split (that can be installed on API 23+) */
  SPLIT,

  /** Shard dex file that can be deployed on L devices */
  DEX,

  /** Hot swappable classes */
  RELOAD_DEX,

  /** Classes that can be used on Dalvik devices */
  RESTART_DEX,

  /** Resources and manifest data */
  RESOURCES
}
