// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.gradle.dsl.api.java;

import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.dsl.api.util.GradleBlockModel;
import org.jetbrains.annotations.NotNull;

public interface JavaDeclarativeModel extends GradleBlockModel {

  @NotNull
  LanguageLevelPropertyModel javaVersion();

  @NotNull
  String mainClass();

  @NotNull
  DependenciesModel dependencies();

  @NotNull
  JavaTestDeclarativeModel testing();
}
