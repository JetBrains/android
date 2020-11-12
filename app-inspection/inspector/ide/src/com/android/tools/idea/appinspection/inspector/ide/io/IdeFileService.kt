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
package com.android.tools.idea.appinspection.inspector.ide.io

import com.android.tools.idea.appinspection.inspector.api.io.DiskFileService
import com.intellij.openapi.application.PathManager
import java.nio.file.Path
import java.nio.file.Paths

/**
 * A service that provides common file operations with locations standardized across the IDE.
 *
 * @param subdir An (optional) additional subdirectory to create all cache / temp files under to
 *   reduce the chance of filename collisions between different areas.
 */
// TODO(b/170769654): Move this class to a more widely accessible location?
class IdeFileService(subdir: String = "") : DiskFileService() {
  override val cacheRoot: Path = Paths.get(PathManager.getSystemPath()).resolve(subdir)
  override val tmpRoot: Path = Paths.get(PathManager.getTempPath()).resolve(subdir)
}
