package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.GradleVersion

interface ContentManager {
  fun showContent(recommended: GradleVersion? = null)
}

