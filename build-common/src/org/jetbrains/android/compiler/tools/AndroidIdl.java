// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.compiler.tools;

import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ArrayUtil;
import org.jetbrains.android.util.AndroidBuildCommonUtils;
import org.jetbrains.android.util.AndroidCompilerMessageKind;
import org.jetbrains.android.util.AndroidExecutionUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * IDL compiler.
 */
public final class AndroidIdl {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.compiler.tools.AndroidIdl");

  private AndroidIdl() {
  }

  @NotNull
  public static Map<AndroidCompilerMessageKind, List<String>> execute(@NotNull IAndroidTarget target,
                                                                      @NotNull String file,
                                                                      @NotNull String outFile,
                                                                      @NotNull String[] sourceRootPaths) throws IOException {
    BuildToolInfo buildToolInfo = target.getBuildToolInfo();
    if (buildToolInfo == null) {
      return Collections.singletonMap(AndroidCompilerMessageKind.ERROR, Collections.singletonList("No Build Tools in the Android SDK."));
    }

    final List<String> commands = new ArrayList<String>();
    final String frameworkAidlPath = target.getPath(IAndroidTarget.ANDROID_AIDL);

    commands.add(buildToolInfo.getPath(BuildToolInfo.PathId.AIDL));
    commands.add("-p" + frameworkAidlPath);

    for (String path : sourceRootPaths) {
      commands.add("-I" + path);
    }
    commands.add(file);
    commands.add(outFile);

    LOG.info(AndroidBuildCommonUtils.command2string(commands));
    return AndroidExecutionUtil.doExecute(ArrayUtil.toStringArray(commands));
  }

}
