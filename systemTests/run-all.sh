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
set -uev

# Add new tests to whichever set takes the least time to run on Travis.
# If any test set is nearing ~45 minutes to build, you must make a new
# test set and also update .travis.yml.

# Defaults to "12", which runs both tests
# Run specific test by script parameter, e.g. 'systemTests/run-all.sh 1'
TEST_SET=${1:-12}

J2OBJC_VERSION=${J2OBJC_VERSION:=1.0.2}

if [[ "$PWD" =~ systemTests ]]; then
   echo "Should be run from project root and not systemTests directory"
   exit 1
fi

if [[ $TEST_SET == *"1"* ]] ; then
   echo Running test set 1

   # Simplest possible set-up.  A single project with no dependencies.
   systemTests/run-test.sh systemTests/simple1

   # Two main gradle projects, `extended` depends on `base`.  They also both test
   # dependency on built-in j2objc libraries, like Guava, and build-closure
   # based translation of an external library, Gson.  They also both depend
   # depend on a third test-only gradle project, `testLib`.
   systemTests/run-test.sh systemTests/multiProject1

   # Platform specific app build: Android, iOS, OS X and some day watchOS
   systemTests/run-test.sh systemTests/allPlatforms
fi

if [[ $TEST_SET == *"2"* ]] ; then
   echo Running test set 2

   # Two gradle projects, `extended` depends on `base`. Both of them depend
   # on project `third_party_gson`, which fully translates and compiles an
   # external library (Google's Gson); and also `third_party_guava` which
   # does the same for Guava. These libraries are used in both `extended` and `base`.
   # We must rename the include directory while this test runs, otherwise the
   # code builds against the translated Guava headers provided in the j2objc dist.
   mv systemTests/localJ2objcDist/j2objc-$J2OBJC_VERSION/include/guava/com/google/common systemTests/localJ2objcDist/j2objc-$J2OBJC_VERSION/include/guava/com/google/common-bak
   systemTests/run-test.sh systemTests/externalLibrary1
   mv systemTests/localJ2objcDist/j2objc-$J2OBJC_VERSION/include/guava/com/google/common-bak systemTests/localJ2objcDist/j2objc-$J2OBJC_VERSION/include/guava/com/google/common
fi
