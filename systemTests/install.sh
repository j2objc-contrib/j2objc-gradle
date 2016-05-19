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


# Fail if any command fails
set -euv

if [[ "$PWD" =~ systemTests ]]; then
   echo "Should be run from project root and not systemTests directory"
   exit 1
fi

pushd systemTests

# Suppress error if directories already exist
mkdir -p localJ2objcDist
mkdir -p common

pushd localJ2objcDist

# Specific version can be configured from command line:
# export J2OBJC_VERSION=X.Y.Z
# Default is version number listed on following line:
J2OBJC_VERSION=${J2OBJC_VERSION:=1.0.2}

# Specific version can be configured from command line:
# export ANDROID_HOME=DIR
# Default is for Travis default location, listed on following line:
ANDROID_HOME=${ANDROID_HOME:=/usr/local/android-sdk}

echo "J2OBJC Version: $J2OBJC_VERSION"

DIST_DIR=j2objc-$J2OBJC_VERSION
DIST_FILE=$DIST_DIR.zip

# For developer local testing, don't keep redownloading the zip file.
if [ ! -e $DIST_FILE ]; then
   curl -L https://github.com/google/j2objc/releases/download/$J2OBJC_VERSION/j2objc-$J2OBJC_VERSION.zip > $DIST_FILE
   unzip -q $DIST_FILE

   # systemTests/common/local.properties
   echo j2objc.home=$PWD/$DIST_DIR > ../common/local.properties
   if [ "${ANDROID_BUILD_DISABLED:=false}" != "true" ]; then
      echo sdk.dir=$ANDROID_HOME >> ../common/local.properties
   fi
   if [ "${J2OBJC_TRANSLATE_ONLY:=false}" == "true" ]; then
      echo j2objc.translateOnlyMode=true >> ../common/local.properties
   fi
   echo "systemTests/common/local.properties configured:"
   cat ../common/local.properties
fi

# pop localJ2objcDist
popd

# pop systemTests
popd
