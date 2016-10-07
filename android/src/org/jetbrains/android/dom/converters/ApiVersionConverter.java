package org.jetbrains.android.dom.converters;

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.SdkVersionInfo;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class ApiVersionConverter extends ResolvingConverter<String> {
  @NotNull
  @Override
  public Collection<String> getVariants(ConvertContext context) {
    final List<String> result = new ArrayList<>(SdkVersionInfo.HIGHEST_KNOWN_API);

    for (int i = 1; i <= SdkVersionInfo.HIGHEST_KNOWN_API; i++) {
      if (i > SdkVersionInfo.HIGHEST_KNOWN_STABLE_API) {
        result.add(SdkVersionInfo.getBuildCode(i));
      } else {
        result.add(Integer.toString(i));
      }
    }
    return result;
  }

  @Nullable
  @Override
  public LookupElement createLookupElement(String s) {
    AndroidVersion version = SdkVersionInfo.getVersion(s, null);
    if (version == null) {
      return null;
    }
    return PrioritizedLookupElement.withPriority(LookupElementBuilder.create(s).
      withTypeText(version.getApiString()), version.getFeatureLevel());
  }

  @Nullable
  @Override
  public String fromString(@Nullable @NonNls String s, ConvertContext context) {
    return s;
  }

  @Nullable
  @Override
  public String toString(@Nullable String s, ConvertContext context) {
    return s;
  }
}
