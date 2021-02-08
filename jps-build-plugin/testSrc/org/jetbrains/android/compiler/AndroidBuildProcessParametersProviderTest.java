// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.compiler;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.Ref;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AndroidBuildProcessParametersProviderTest {

  public static final Class<Long> CLASS_FROM_JRE = Long.class;

  private AndroidBuildProcessParametersProvider inst;
  private Ref<List<Class<?>>> reportedMissingItems;

  @Before
  public void setup() {
    inst = new AndroidBuildProcessParametersProvider() {
      @Override
      void reportMissingClasses(@NotNull List<Class<?>> classesWithMissingJars) {
        reportedMissingItems.set(classesWithMissingJars);
      }
    };

    reportedMissingItems = new Ref<>();
  }

  @Test
  public void getJarsContainingClassesDoesNotThrowNPE() {
    assume().that(PathManager.getJarPathForClass(CLASS_FROM_JRE)).isNull(); // replace with assert after AndroidStudio moved to Java11
    List<String> jars = inst.getJarsContainingClasses(CLASS_FROM_JRE);
    assertThat(jars).isEmpty();
  }

  @Test
  public void classesWithNoJarAreReported() {
    assume().that(PathManager.getJarPathForClass(CLASS_FROM_JRE)).isNull(); // replace with assert after AndroidStudio moved to Java11
    inst.getJarsContainingClasses(CLASS_FROM_JRE);
    assertThat(reportedMissingItems.get()).containsExactly(CLASS_FROM_JRE);
  }
}