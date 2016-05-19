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

import org.junit.Assert;
import org.junit.Test;

public class CubeTest {

    @Test
    public void testToString() {
        Assert.assertEquals("[Cube 7]", new Cube(7).toString());
    }

    @Test
    public void testExerciseGuava() {
        Assert.assertEquals(CubeTester.exerciseGuava("BASE"), new Cube(7).exerciseGuava());
    }
}
