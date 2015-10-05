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

mkdir localJ2objcDist
mkdir common

# Fail if any command fails
set -ev

pushd localJ2objcDist

DIST_DIR=j2objc-$J2OBJC_VERSION
DIST_FILE=$DIST_DIR.zip

# For developer local testing, don't keep redownloading the zip file.
if [ ! -e $DIST_FILE ]; then
  curl -L https://github.com/google/j2objc/releases/download/$J2OBJC_VERSION/j2objc-$J2OBJC_VERSION.zip > $DIST_FILE
  unzip $DIST_FILE
  echo j2objc.home=$PWD/$DIST_DIR > ../common/local.properties
fi

popd

