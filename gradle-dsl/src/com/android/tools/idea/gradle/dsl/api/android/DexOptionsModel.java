package com.android.tools.idea.gradle.dsl.api.android;

import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.api.util.GradleBlockModel;
import org.jetbrains.annotations.NotNull;

public interface DexOptionsModel extends GradleBlockModel {
  @NotNull
  ResolvedPropertyModel additionalParameters();

  @NotNull
  ResolvedPropertyModel javaMaxHeapSize();

  @NotNull
  ResolvedPropertyModel jumboMode();

  @NotNull
  ResolvedPropertyModel keepRuntimeAnnotatedClasses();

  @NotNull
  ResolvedPropertyModel maxProcessCount();

  @NotNull
  ResolvedPropertyModel optimize();

  @NotNull
  ResolvedPropertyModel preDexLibraries();

  @NotNull
  ResolvedPropertyModel threadCount();
}
