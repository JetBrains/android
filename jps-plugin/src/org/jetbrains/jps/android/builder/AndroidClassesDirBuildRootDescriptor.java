package org.jetbrains.jps.android.builder;

import com.intellij.openapi.util.io.FileFilters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.impl.BuildRootDescriptorImpl;

import java.io.File;
import java.io.FileFilter;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidClassesDirBuildRootDescriptor extends BuildRootDescriptorImpl {
  public AndroidClassesDirBuildRootDescriptor(@NotNull BuildTarget target, @NotNull File root) {
    super(target, root);
  }

  @NotNull
  @Override
  public FileFilter createFileFilter() {
    return FileFilters.withExtension("class");
  }
}
