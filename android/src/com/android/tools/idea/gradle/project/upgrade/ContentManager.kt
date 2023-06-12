package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.AgpVersion

interface ContentManager {
  fun showContent(recommended: AgpVersion? = null)
}

