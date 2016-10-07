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
package org.jetbrains.android.dom.attrs;

import com.android.resources.ResourceType;

import java.util.EnumSet;
import java.util.Set;

/**
 * @author yole
 */
public enum AttributeFormat {
    Reference, String, Color, Dimension, Boolean, Integer, Float, Fraction, Enum, Flag;

    public static EnumSet<ResourceType> convertTypes(Set<AttributeFormat> formats) {
        EnumSet<ResourceType> types = EnumSet.noneOf(ResourceType.class);
        for (AttributeFormat format : formats) {
            switch (format) {
                case Boolean:
                    types.add(ResourceType.BOOL);
                    break;
                case Color:
                    types.add(ResourceType.COLOR);
                    types.add(ResourceType.DRAWABLE);
                    types.add(ResourceType.MIPMAP);
                    break;
                case Dimension:
                    types.add(ResourceType.DIMEN);
                    break;
                case Integer:
                    types.add(ResourceType.INTEGER);
                    break;
                case Fraction:
                    types.add(ResourceType.FRACTION);
                    break;
                case String:
                    types.add(ResourceType.STRING);
                    break;
                case Reference:
                    types.add(ResourceType.COLOR);
                    types.add(ResourceType.DRAWABLE);
                    types.add(ResourceType.MIPMAP);
                    types.add(ResourceType.STRING);
                    types.add(ResourceType.ID);
                    types.add(ResourceType.STYLE);
                    break;
                default:
                    break;
            }
        }

        return types;
    }
}
