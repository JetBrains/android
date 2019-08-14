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
package com.android.tools.idea.templates

import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.GradleCoordinate.parseCoordinateString
import freemarker.template.SimpleScalar
import freemarker.template.TemplateMethodModelEx
import freemarker.template.TemplateModel
import freemarker.template.TemplateModelException
import java.security.InvalidParameterException

/**
 * Method invoked by FreeMarker to compute the right dependency string.
 * Arguments:
 *  1. The dependency
 *  2. Optional - Min revision for the dependency
 *
 * Example usage: `resolveDependency("com.android:lib:+", "1.1.0")`, which will return "com.android:lib:1.1.0" or a higher dependency.
 */
class FmResolveDependencyMethod : TemplateMethodModelEx {
  override fun exec(args: List<*>): TemplateModel {
    if (args.size !in 1..2) {
      throw TemplateModelException("Wrong arguments")
    }

    val dependency = args[0].toString()
    val minVersion = args.getOrNull(1)?.toString()

    return SimpleScalar(convertConfiguration(RepositoryUrlManager.get(), dependency, minVersion))
  }

  companion object {
    fun convertConfiguration(repo: RepositoryUrlManager, dependency: String, minRev: String?): String {
      // If we can't parse the dependency, just return it back
      val coordinate = parseCoordinateString(dependency) ?: throw InvalidParameterException("Invalid dependency: $dependency")

      val minCoordinate = if (minRev == null) coordinate else GradleCoordinate(coordinate.groupId, coordinate.artifactId, minRev)

      // If we cannot resolve the dependency on the repo, return the at least the min requested
      val resolved = repo.resolveDynamicCoordinate(coordinate, null) ?: return minCoordinate.toString()

      return maxOf(resolved, minCoordinate, GradleCoordinate.COMPARE_PLUS_LOWER).toString()
    }
  }
}