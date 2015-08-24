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

J2OBJC_VERSION=0.9.8.1
mkdir localJ2objcDist
mkdir common

# Fail if any command fails
set -e

pushd localJ2objcDist

# For developer local testing, don't keep redownloading the zip file.
if [ ! -e j2objcDist.zip ]; then
  curl -L https://github.com/google/j2objc/releases/download/$J2OBJC_VERSION/j2objc-$J2OBJC_VERSION.zip > j2objcDist.zip
  unzip j2objcDist.zip
  mv j2objc-$J2OBJC_VERSION j2objcDist
  echo j2objc.home=$PWD/j2objcDist > ../common/local.properties
fi

popd
