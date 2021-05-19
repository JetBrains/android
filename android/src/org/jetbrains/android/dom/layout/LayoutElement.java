/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.dom.layout;

import com.intellij.util.xml.CustomChildren;
import com.intellij.util.xml.SubTagList;
import java.util.List;
import org.jetbrains.android.dom.AndroidDomElement;
import org.jetbrains.android.dom.AndroidDomExtender;
import org.jetbrains.android.dom.SubtagsProcessingUtil;
import org.jetbrains.android.facet.AndroidFacet;

/**
 * Base interface for tags that can own all view classes as sub-tags (e.g. {@code Button},
 * {@code TextView}, etc.) or support android:layout_XXX attributes.
 * <p>
 * See also {@link SubtagsProcessingUtil#processSubtags(AndroidFacet, AndroidDomElement, SubtagsProcessingUtil.SubtagProcessor)}
 * which is responsible for dynamically registering the sub-tags for all view classes, which will
 * get called by {@link AndroidDomExtender} in this case.
 */
public interface LayoutElement extends AndroidDomElement {
  @SubTagList("requestFocus")
  List<LayoutElement> getRequestFocuses();

  @CustomChildren
  List<LayoutViewElement> getSubView();
}
