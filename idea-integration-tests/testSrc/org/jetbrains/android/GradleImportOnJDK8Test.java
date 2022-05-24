// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.android;

import com.android.testutils.TestUtils;
import com.android.tools.idea.gradle.project.sync.idea.AndroidGradleProjectResolver;
import com.intellij.util.containers.ContainerUtil;
import java.io.IOException;
import java.util.List;
import org.jetbrains.kotlin.android.synthetic.idea.AndroidExtensionsProjectResolverExtension;
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverExtension;
import org.junit.Test;

public class GradleImportOnJDK8Test extends GradleImportingTestCase {

  private static final Class<?>[] REQUIRED_RESOLVERS = {
    AndroidGradleProjectResolver.class,
    AndroidExtensionsProjectResolverExtension.class
    //GradleAndroidProjectResolverExtension.class <= registered dynamically, does not inject classes
  };

  @Override
  public void setUp() throws Exception {
    super.setUp();

    String jdk8 = TestUtils.getEmbeddedJdk8Path();
    getCurrentExternalProjectSettings().setGradleJvm(jdk8);

    List<GradleProjectResolverExtension> extensions = GradleProjectResolverExtension.EP_NAME.getExtensionList();

    for (Class<?> resolver : REQUIRED_RESOLVERS) {
      if (!ContainerUtil.exists(extensions, resolver::isInstance)) {
        throw new AssertionError("Required extension is not registered: " + resolver);
      }
    }
  }

  @Test
  public void testBaseJavaProject() throws Exception {
    createDefaultDirs();
    importProject(
      "apply plugin: 'java'"
    );
    // should not throw exceptions, especially about "Unsupported class file version"
  }

  private void createDefaultDirs() throws IOException {
    createProjectSubFile("src/main/java/A.java");
    createProjectSubFile("src/test/java/A.java");
    createProjectSubFile("src/main/resources/resource.properties");
    createProjectSubFile("src/test/resources/test_resource.properties");
  }
}
