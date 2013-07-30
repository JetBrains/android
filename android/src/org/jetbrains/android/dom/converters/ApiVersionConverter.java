package org.jetbrains.android.dom.converters;

import com.android.ide.common.sdk.SdkVersionInfo;
import com.android.tools.idea.templates.TemplateUtils;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.android.sdk.AndroidSdkUtils;
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
  private static String[] ourKnownVersions;

  @NotNull
  @Override
  public Collection<? extends String> getVariants(ConvertContext context) {
    final List<String> result = new ArrayList<String>(SdkVersionInfo.HIGHEST_KNOWN_API);

    for (int i = 1; i <= SdkVersionInfo.HIGHEST_KNOWN_API; i++) {
      result.add(Integer.toString(i));
    }
    return result;
  }

  @Nullable
  @Override
  public LookupElement createLookupElement(String s) {
    final int apiLevel = Integer.parseInt(s);
    final String version = getApiLevelLabel(apiLevel);

    if (version == null) {
      return null;
    }
    return PrioritizedLookupElement.withPriority(LookupElementBuilder.create(s).
      withTypeText(version), apiLevel);
  }

  @Nullable
  private static String getApiLevelLabel(int apiLevel) {
    if (ourKnownVersions == null) {
      if (!AndroidSdkUtils.isAndroidSdkAvailable()) {
        return null;
      }
      ourKnownVersions = TemplateUtils.getKnownVersions();
    }
    return ourKnownVersions[apiLevel - 1];
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
