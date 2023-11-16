/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.insights

import com.android.tools.idea.insights.proto.BuildStamp
import com.android.tools.idea.insights.proto.RepositoryInfo
import com.android.tools.idea.insights.proto.VersionControlSystem
import com.android.tools.idea.protobuf.TextFormat
import com.intellij.openapi.diagnostic.Logger

private fun logger() = Logger.getInstance("AppVcsInfo")

const val PROJECT_ROOT_PREFIX = "\$PROJECT_DIR"
const val ABOVE_PROJECT_ROOT_PREFIX = "\$ABOVE_PROJECT_DIR"

/** Representation of the version control system used by an App. */
data class AppVcsInfo(val repoInfo: List<RepoInfo>) {
  companion object {
    val NONE = AppVcsInfo(emptyList())

    fun fromProto(textProto: String): AppVcsInfo {
      val proto = decode(textProto)
      return fromProto(proto)
    }

    private fun fromProto(proto: BuildStamp): AppVcsInfo {
      val repositories = proto.repositoriesList.mapNotNull { RepoInfo.fromProto(it) }
      return AppVcsInfo(repoInfo = repositories)
    }
  }
}

/** Representation of the repository used and the HEAD revision captured when the app is built. */
data class RepoInfo(val vcsKey: VCS_CATEGORY, val rootPath: String, val revision: String) {
  companion object {
    fun fromProto(proto: RepositoryInfo): RepoInfo? {
      val vcsKey = mapVcsCategoryFrom(proto.system) ?: return null

      return RepoInfo(vcsKey = vcsKey, rootPath = proto.localRootPath, revision = proto.revision)
    }
  }
}

/** Version control system */
enum class VCS_CATEGORY {
  GIT,
  TEST_VCS
}

fun mapVcsCategoryFrom(proto: VersionControlSystem): VCS_CATEGORY? {
  return when (proto) {
    VersionControlSystem.GIT -> VCS_CATEGORY.GIT
    else -> {
      logger().warn("$proto is not supported.")
      null
    }
  }
}

fun decode(textProto: String): BuildStamp {
  return try {
    BuildStamp.newBuilder().apply { TextFormat.getParser().merge(textProto, this) }.build()
  } catch (exception: Exception) {
    logger().warn("Error when decoding from text proto ($textProto): $exception")
    BuildStamp.getDefaultInstance()
  }
}
