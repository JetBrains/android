// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package androidx.compose.plugins.idea;

import androidx.compose.plugins.kotlin.ComposeCallResolutionInterceptorExtension;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.descriptors.FunctionDescriptor;
import org.jetbrains.kotlin.incremental.components.LookupLocation;
import org.jetbrains.kotlin.name.Name;
import org.jetbrains.kotlin.resolve.calls.CallResolver;
import org.jetbrains.kotlin.resolve.calls.context.BasicCallResolutionContext;
import org.jetbrains.kotlin.resolve.calls.tower.ImplicitScopeTower;
import org.jetbrains.kotlin.resolve.scopes.ResolutionScope;

public class IdeComposeCallResolutionInterceptorExtension extends ComposeCallResolutionInterceptorExtension {
  @NotNull
  @Override
  public Collection<FunctionDescriptor> interceptCandidates(@NotNull Collection<? extends FunctionDescriptor> candidates,
                                                            @NotNull ImplicitScopeTower scopeTower,
                                                            @NotNull BasicCallResolutionContext resolutionContext,
                                                            @NotNull ResolutionScope resolutionScope,
                                                            @Nullable CallResolver callResolver,
                                                            @NotNull Name name,
                                                            @NotNull LookupLocation location) {
    if (ComposePluginUtilsKt.isComposeEnabled(resolutionContext.call.getCallElement())) {
      return super.interceptCandidates(candidates, scopeTower, resolutionContext, resolutionScope, callResolver, name, location);
    }
    return (Collection)candidates;
  }
}
