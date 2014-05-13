package org.jetbrains.android.resourceManagers;

import com.android.resources.ResourceType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
abstract class ValueResourceInfoBase implements ValueResourceInfo {
  protected final String myName;
  protected final ResourceType myType;
  protected final VirtualFile myFile;

  protected ValueResourceInfoBase(@NotNull String name, @NotNull ResourceType type, @NotNull VirtualFile file) {
    myName = name;
    myType = type;
    myFile = file;
  }

  @NotNull
  @Override
  public VirtualFile getContainingFile() {
    return myFile;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public ResourceType getType() {
    return myType;
  }

  @Override
  public String toString() {
    return "ANDROID_RESOURCE: " + myType + ", " + myName + ", " + myFile.getPath() + "]";
  }


  @Override
  public int compareTo(@NotNull ValueResourceInfo other) {
    VirtualFile file1 = myFile;
    VirtualFile file2 = other.getContainingFile();
    int delta = AndroidResourceUtil.compareResourceFiles(file1, file2);
    if (delta != 0) {
      return delta;
    }

    // Ensure stable order between unrelated value resources that don't know about each other
    return getSortingRank() - ((ValueResourceInfoBase)other).getSortingRank();
  }

  /** Relative ordering between Id resources and value resources */
  protected abstract int getSortingRank();
}
