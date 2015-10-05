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
set -ev

function runTest {
    TEST_DIR=$1
    echo Running test $TEST_DIR
    set -e
    pushd $TEST_DIR
    ./gradlew wrapper
    ./gradlew clean
    # If we fail, try again with lots of logging.
    ./gradlew build
    # Dump out listings of the files generated for manual debugging/verification.
    ls -R1c build/j2objcOutputs || echo No such outputs
    ls -R1c */build/j2objcOutputs || echo No such outputs
    popd
}

# TODO: Might want to infer the directories that have build.gradle files in them.

# Simplest possible set-up.  A single project with no dependencies.
runTest simple1

# Two gradle projects, `extended` depends on `base`.  They also both test
# dependency on built-in j2objc libraries, like Guava, and build-closure
# based translation of an external library, Gson.
runTest multiProject1

# TODO: Re-enable building Guava when we figure out how to deal with Java 8.
# https://github.com/j2objc-contrib/j2objc-gradle/issues/484
# Two gradle projects, `extended` depends on `base`. Both of them depend
# on project `third_party_gson`, which fully translates and compiles an
# external library (Google's Gson); and also `third_party_guava` which
# does the same for Guava. These libraries are used in both `extended` and `base`.
# We must rename the include directory while this test runs, otherwise the
# code builds against the translated Guava headers provided in the j2objc dist.
# mv localJ2objcDist/j2objc-$J2OBJC_VERSION/include/com/google/common localJ2objcDist/j2objc-$J2OBJC_VERSION/include/com/google/common-bak
# runTest externalLibrary1
# mv localJ2objcDist/j2objc-$J2OBJC_VERSION/include/com/google/common-bak localJ2objcDist/j2objc-$J2OBJC_VERSION/include/com/google/common
