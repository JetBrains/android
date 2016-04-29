package org.jetbrains.android.compiler;

import com.android.jarutils.SignedJarBuilder;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.intellij.compiler.server.BuildProcessParametersProvider;
import com.intellij.openapi.application.PathManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public class AndroidBuildProcessParametersProvider extends BuildProcessParametersProvider {
  @NotNull
  @Override
  public List<String> getClassPath() {
    return ImmutableList.of(PathManager.getJarPathForClass(Gson.class), PathManager.getJarPathForClass(SignedJarBuilder.class));
  }
}
