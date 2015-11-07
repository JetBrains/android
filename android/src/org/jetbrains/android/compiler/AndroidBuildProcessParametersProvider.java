package org.jetbrains.android.compiler;

import com.google.gson.Gson;
import com.intellij.compiler.server.BuildProcessParametersProvider;
import com.intellij.openapi.application.PathManager;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class AndroidBuildProcessParametersProvider extends BuildProcessParametersProvider {
  @NotNull
  @Override
  public List<String> getClassPath() {
    return Collections.singletonList(PathManager.getJarPathForClass(Gson.class));
  }
}
