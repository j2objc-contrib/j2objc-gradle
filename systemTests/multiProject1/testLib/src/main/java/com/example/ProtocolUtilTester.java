/*
 * Copyright (c) 2015 the authors of j2objc-gradle (see AUTHORS file)
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

package com.example;

import com.google.protobuf.ExtensionRegistry;

public class ProtocolUtilTester {

    public static boolean loadProtocolBuffersClass(String className) {
        Class clazz = null;
        try {
            clazz = Class.forName(className);
        } catch (ClassNotFoundException e) {
            return false;
        }

        return clazz != null;
    }
}
