package com.android.tools.idea.model;

import com.android.builder.model.JavaLibrary;
import com.android.builder.model.Library;
import com.android.ide.common.repository.GradleVersion;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Creates a deep copy of {@link JavaLibrary}.
 *
 * @see IdeAndroidProject
 */
public class IdeJavaLibrary extends IdeLibrary implements JavaLibrary, Serializable {
  @NotNull private final File myJarFile;
  @NotNull private final List<IdeJavaLibrary> myDependencies;

  public IdeJavaLibrary(@NotNull JavaLibrary library, @NotNull Map<Library, Library> seen, @NotNull GradleVersion gradleVersion) {
    super(library, gradleVersion);

    myJarFile = library.getJarFile();

    myDependencies = new ArrayList<>();
    for (JavaLibrary dependency : library.getDependencies()) {
      if (!seen.containsKey(dependency)) {
        seen.put(dependency, new IdeJavaLibrary(dependency, seen, gradleVersion));
      }
      myDependencies.add((IdeJavaLibrary)seen.get(dependency));
    }
  }

  @Override
  @NotNull
  public File getJarFile() {
    return myJarFile;
  }

  @Override
  @NotNull
  public List<? extends JavaLibrary> getDependencies() {
    return myDependencies;
  }
}
