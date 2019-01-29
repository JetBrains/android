package com.android.tools.idea.tests.gui.framework.bazel

import com.android.tools.idea.tests.gui.framework.fixture.ActionLinkFixture
import com.android.tools.idea.tests.gui.framework.fixture.WelcomeFrameFixture

fun WelcomeFrameFixture.importBazelProject(): WelcomeFrameFixture {
  // The actionId is from //tools/vendor/google3/blaze/third_party/intellij/bazel/plugin/java/src/META-INF/java-contents.xml
  ActionLinkFixture.findByActionId("Blaze.ImportProject2", robot(), target()).click()
  return this
}
