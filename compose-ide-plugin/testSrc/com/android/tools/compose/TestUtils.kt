package com.android.tools.compose

import com.android.testutils.TestUtils
import com.intellij.openapi.application.ex.PathManagerEx
import java.io.File

fun getComposePluginTestDataPath():String {
  val adtPath = TestUtils.getWorkspaceFile("tools/adt/idea/compose-ide-plugin/testData").path
  return if (File(adtPath).exists()) adtPath else PathManagerEx.findFileUnderCommunityHome("plugins/android-compose-ide-plugin").path
}
