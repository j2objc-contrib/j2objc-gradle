#!/bin/bash
#
# Copyright (c) 2015 the authors of j2objc-gradle (see AUTHORS file)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Fail if anything fails.
set -e

function runTest {
    TEST_DIR=$1
    echo Running test $TEST_DIR
    set -e
    pushd $TEST_DIR
    ./gradlew wrapper
    ./gradlew clean
    ./gradlew build
    popd
}

# TODO: Might want to infer the directories that have build.gradle files in them.

# Simplest possible set-up.  A single project with no dependencies.
runTest simple1

# Two gradle projects, `extended` depends on `base`.  They also both test
# dependency on built-in j2objc libraries, like Guava.
runTest multiProject1
