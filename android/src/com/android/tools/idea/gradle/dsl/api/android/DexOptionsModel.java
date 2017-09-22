package com.android.tools.idea.gradle.dsl.api.android;

import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.api.values.GradleNullableValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface DexOptionsModel {
  @Nullable
  List<GradleNotNullValue<String>> additionalParameters();

  @NotNull
  DexOptionsModel addAdditionalParameter(@NotNull String additionalParameter);

  @NotNull
  DexOptionsModel removeAdditionalParameter(@NotNull String additionalParameter);

  @NotNull
  DexOptionsModel removeAllAdditionalParameters();

  @NotNull
  DexOptionsModel replaceAdditionalParameter(@NotNull String oldAdditionalParameter, @NotNull String newAdditionalParameter);

  @NotNull
  GradleNullableValue<String> javaMaxHeapSize();

  @NotNull
  DexOptionsModel setJavaMaxHeapSize(@NotNull String javaMaxHeapSize);

  @NotNull
  DexOptionsModel removeJavaMaxHeapSize();

  @NotNull
  GradleNullableValue<Boolean> jumboMode();

  @NotNull
  DexOptionsModel setJumboMode(boolean jumboMode);

  @NotNull
  DexOptionsModel removeJumboMode();

  @NotNull
  GradleNullableValue<Boolean> keepRuntimeAnnotatedClasses();

  @NotNull
  DexOptionsModel setKeepRuntimeAnnotatedClasses(boolean keepRuntimeAnnotatedClasses);

  @NotNull
  DexOptionsModel removeKeepRuntimeAnnotatedClasses();

  @NotNull
  GradleNullableValue<Integer> maxProcessCount();

  @NotNull
  DexOptionsModel setMaxProcessCount(int maxProcessCount);

  @NotNull
  DexOptionsModel removeMaxProcessCount();

  @NotNull
  GradleNullableValue<Boolean> optimize();

  @NotNull
  DexOptionsModel setOptimize(boolean optimize);

  @NotNull
  DexOptionsModel removeOptimize();

  @NotNull
  GradleNullableValue<Boolean> preDexLibraries();

  @NotNull
  DexOptionsModel setPreDexLibraries(boolean preDexLibraries);

  @NotNull
  DexOptionsModel removePreDexLibraries();

  @NotNull
  GradleNullableValue<Integer> threadCount();

  @NotNull
  DexOptionsModel setThreadCount(int threadCount);

  @NotNull
  DexOptionsModel removeThreadCount();
}
