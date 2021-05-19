// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.inspections;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.InspectionEP;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.codeInspection.deadCode.UnusedDeclarationPresentation;
import com.intellij.codeInspection.ex.EntryPointsManagerBase;
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.codeInspection.reference.EntryPoint;
import com.intellij.openapi.extensions.DefaultPluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import java.io.IOException;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class AndroidInspectionsTest extends AndroidTestCase {
  @NonNls private static final String BASE_PATH = "/inspections/";
  @NonNls private static final String BASE_PATH_GLOBAL = BASE_PATH + "global/";

  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  private static GlobalInspectionToolWrapper getUnusedDeclarationWrapper() {
    InspectionEP ep = new InspectionEP(UnusedDeclarationInspection.class.getName(), new DefaultPluginDescriptor(PluginId.getId("AndroidInspectionsTest"), AndroidInspectionsTest.class.getClassLoader()));
    ep.presentation = UnusedDeclarationPresentation.class.getName();
    ep.shortName = UnusedDeclarationInspectionBase.SHORT_NAME;
    return new GlobalInspectionToolWrapper(new UnusedDeclarationInspection(true), ep);
  }

  public void testActivityInstantiated1() throws Throwable {
    final String dir = BASE_PATH_GLOBAL + "activityInstantiated";
    myFixture.copyFileToProject(dir + "/MyActivity.java", "src/p1/p2/MyActivity.java");
    doClassInstantiatedTest(dir + "/test1");
  }

  public void testActivityInstantiated2() throws Throwable {
    final String dir = BASE_PATH_GLOBAL + "activityInstantiated";
    myFixture.copyFileToProject(dir + "/MyActivity.java", "src/p1/p2/MyActivity.java");

    final AndroidComponentEntryPoint entryPoint = getAndroidEntryPoint();
    final boolean oldValue = entryPoint.ADD_ANDROID_COMPONENTS_TO_ENTRIES;

    try {
      entryPoint.ADD_ANDROID_COMPONENTS_TO_ENTRIES = false;
      doClassInstantiatedTest(dir + "/test2");
    }
    finally {
      entryPoint.ADD_ANDROID_COMPONENTS_TO_ENTRIES = oldValue;
    }
  }

  public void testServiceInstantiated1() throws Throwable {
    final String dir = BASE_PATH_GLOBAL + "serviceInstantiated";
    myFixture.copyFileToProject(dir + "/MyService.java", "src/p1/p2/MyService.java");
    doClassInstantiatedTest(dir + "/test1");
  }

  public void testServiceInstantiated2() throws Throwable {
    final String dir = BASE_PATH_GLOBAL + "serviceInstantiated";
    myFixture.copyFileToProject(dir + "/MyService.java", "src/p1/p2/MyService.java");

    final AndroidComponentEntryPoint entryPoint = getAndroidEntryPoint();
    final boolean oldValue = entryPoint.ADD_ANDROID_COMPONENTS_TO_ENTRIES;

    try {
      entryPoint.ADD_ANDROID_COMPONENTS_TO_ENTRIES = false;
      doClassInstantiatedTest(dir + "/test2");
    }
    finally {
      entryPoint.ADD_ANDROID_COMPONENTS_TO_ENTRIES = oldValue;
    }
  }

  private void doClassInstantiatedTest(String d) throws IOException {
    createManifest();
    doGlobalInspectionTest(getUnusedDeclarationWrapper(), d, new AnalysisScope(myModule));
  }

  @NotNull
  private static AndroidComponentEntryPoint getAndroidEntryPoint() {
    return EntryPointsManagerBase.DEAD_CODE_EP_NAME.findExtension(AndroidComponentEntryPoint.class);
  }
}
