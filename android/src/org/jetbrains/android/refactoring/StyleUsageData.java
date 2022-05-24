package org.jetbrains.android.refactoring;

import com.intellij.psi.PsiFile;
import java.util.Map;
import org.jetbrains.android.dom.converters.AndroidResourceReferenceBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

interface StyleUsageData {
  @Nullable
  PsiFile getFile();

  void inline(@NotNull Map<AndroidAttributeInfo, String> attributeValues, @Nullable StyleRefData parentStyleRef);

  @NotNull
  AndroidResourceReferenceBase getReference();
}
