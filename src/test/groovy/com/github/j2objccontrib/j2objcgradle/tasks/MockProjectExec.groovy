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

package com.github.j2objccontrib.j2objcgradle.tasks

import groovy.mock.interceptor.MockFor
import org.gradle.api.Project
import org.gradle.util.ConfigureUtil

/**
 * Used for mocking on the project.exec {...} command used by many of the plugin tasks.
 *
 * It operates in the following manner:
 * 1) Allows dependency injection and interception of the project.exec {...} command
 * 2) Verifies the executable and args compared to expected values
 * 3) Write to stdout and stderr as specified
 * 4) Throws an error, e.g. for non-zero exits from the command
 */
public class MockProjectExec {

    private Project project
    private String j2objcHome
    private static final String j2objcHomeStd = '/J2OBJC_HOME'
    private static final String projectDirStd = '/PROJECT_DIR'

    private MockFor mockForProj = new MockFor(Project)
    private MockExec mockExec = new MockExec()
    private GroovyObject proxyInstance

    MockProjectExec(Project project, String j2objcHome) {
        this.project = project
        this.j2objcHome = j2objcHome
    }

    Project projectProxyInstance() {
        proxyInstance = mockForProj.proxyInstance()
        return ( Project ) proxyInstance
    }

    void demandExec(
            List<String> expectedCommandLine) {
        demandExec(expectedCommandLine, null, null, null)
    }

    void demandExec(
            List<String> expectedCommandLine,
            String errorOutputToWrite,
            String standardOutputToWrite,
            Exception exceptionToThrow) {

        mockForProj.demand.exec { Closure closure ->

            ConfigureUtil.configure(closure, mockExec)

            assert expectedCommandLine[0] == mockExec.executable.replace(j2objcHome, j2objcHomeStd)
            expectedCommandLine.remove(0)

            List<String> canonicalizedArgs = mockExec.args.collect { String arg ->
                return arg
                        .replace(j2objcHome, j2objcHomeStd)
                        .replace(project.projectDir.path, projectDirStd)
            }
            assert expectedCommandLine == canonicalizedArgs

            if (errorOutputToWrite) {
                mockExec.errorOutput.write(errorOutputToWrite.getBytes('utf-8'))
                mockExec.errorOutput.flush()
            }
            if (standardOutputToWrite) {
                mockExec.standardOutput.write(standardOutputToWrite.getBytes('utf-8'))
                mockExec.standardOutput.flush()
            }

            if (exceptionToThrow != null) {
                throw exceptionToThrow
            }
        }
    }

    void verify() {
        mockForProj.verify(proxyInstance)
    }

    // Basically mocks Gradle's AbstractExecTask
    private class MockExec {
        String executable
        List<String> args = new ArrayList<>()
        OutputStream errorOutput;
        OutputStream standardOutput;

        public void executable(Object executable) {
            this.executable = (String) executable
        }

        public void args(Object... args) {
            args.each { Object arg ->
                String newArgStr = (String) arg
                this.args.add(newArgStr)
            }
        }

        public void errorOutput(OutputStream errorOutput) {
            this.errorOutput = errorOutput
        }

        public void standardOutput(OutputStream standardOutput) {
            this.standardOutput = standardOutput
        }

        public String toString() {
            return executable + ' ' + args.join(' ')
        }
    }
}
