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
//
//  IOS-APP-Bridging-Header.h
//  IOS-APP
//
//  Created by Bruno Bowden on 10/8/15.
//  Copyright © 2015 J2ObjC Contrib. All rights reserved.
//

// Needed for Swift initialization of Java objects
#import "JreEmulation.h"
#import "IOSClass.h" // appears to be nolonger needed

#import "com/example/Cube.h"
#import "com/example/ExtendedCube.h"
