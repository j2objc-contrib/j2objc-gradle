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
import groovy.util.logging.Slf4j
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

@Slf4j
class MockProjectExec {

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

        // TODO: find a more elegant way to do this that doesn't require intercepting all methods
        // http://stackoverflow.com/questions/31129003/mock-gradle-project-exec-using-metaprogramming
        // Would prefer to mock a single method, e.g. project.metaClass.exec {...}
        // However this doesn't work due to a Groovy bug: https://issues.apache.org/jira/browse/GROOVY-3493

        // This intercepts all methods, stubbing out exec and passing through all other invokes
        project.metaClass.invokeMethod = { String name, Object[] args ->
            if (name == 'exec') {
                // Call the proxy object so that it can track verifications
                projectProxyInstance().exec((Closure) args.first())
            } else {
                // This calls the delegate without causing infinite recursion
                // http://stackoverflow.com/a/10126006/1509221
                MetaMethod metaMethod = delegate.class.metaClass.getMetaMethod(name, args)
                debugLogInvokeMethod(name, args, metaMethod)

                // TODO: is there a way to do this automatically, e.g. coerceArgumentsToClasses?
                if (name == 'files') {
                    // Coerce the arguments to match the signature of Project.files(Object... paths)
                    assert 0 == args.size() || 1 == args.size()
                    if (args.size() == 0 ||  // files()
                        args.first() == null) {  // files(null)
                        return metaMethod?.invoke(delegate, [[] as Object[]] as Object[])
                    } else {
                        // files(ArrayList) possibly, so cast ArrayList to Object[]
                        return metaMethod?.invoke(delegate, [(Object[]) args.first()] as Object[])
                    }
                } else {
                    return metaMethod?.invoke(delegate, args)
                }
            }
        }
    }

    // Debug logging for invokeMethod calls parameters
    private static void debugLogInvokeMethod(String name, Object[] args, MetaMethod metaMethod) {
        String call = "call: $name, ${args.class}"
        if (args.size() > 0) {
            if (args.first() == null) {
                call += ", null, ${args}, ${args.first()}"
            } else {
                call += ", ${args.first().class}, ${args}, ${args.first()}"
            }
        } else {
            call += ", ${args}"
        }
        log.debug(call)
        log.debug("method: ${metaMethod.isValidExactMethod(args)}, " +
                  "${metaMethod.isValidMethod(args)}, " +
                  "$metaMethod")
    }

    Project projectProxyInstance() {
        proxyInstance = mockForProj.proxyInstance()
        return ( Project ) proxyInstance
    }

    void demandExecAndReturn(
            List<String> expectedCommandLine) {
        demandExecAndReturn(expectedCommandLine, null, null, null)
    }

    void demandExecAndReturn(
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

        void executable(Object executable) {
            this.executable = (String) executable
        }

        void args(Object... args) {
            args.each { Object arg ->
                String newArgStr = (String) arg
                this.args.add(newArgStr)
            }
        }

        void errorOutput(OutputStream errorOutput) {
            this.errorOutput = errorOutput
        }

        void standardOutput(OutputStream standardOutput) {
            this.standardOutput = standardOutput
        }

        String toString() {
            return "$executable ${args.join(' ')}"
        }
    }
}
