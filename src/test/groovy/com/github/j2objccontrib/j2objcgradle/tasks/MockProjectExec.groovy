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
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import groovy.util.logging.Slf4j
import org.gradle.api.Project
import org.gradle.api.file.CopySpec
import org.gradle.api.internal.file.copy.DefaultCopySpec
import org.gradle.api.tasks.WorkResult
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import org.gradle.process.internal.ExecHandleBuilder
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

    // Windows backslashes are converted to forward slashes for easier verification
    private String initWorkingDir = '/INIT_WORKING_DIR'
    private static final String j2objcHomeCanonicalized = '/J2OBJC_HOME'
    private static final String projectDirCanonicalized = '/PROJECT_DIR'

    private MockFor mockForProj = new MockFor(Project)
    private ExecSpec execSpec = new ExecHandleBuilder()
    private GroovyObject proxyInstance

    MockProjectExec(Project project, String j2objcHome) {
        this.project = project
        this.j2objcHome = j2objcHome

        initWorkingDir = TestingUtils.windowsNoFakeAbsolutePath(initWorkingDir)

        // This prevents the test error: "Cannot convert relative path . to an absolute file."
        execSpec.setWorkingDir(initWorkingDir)

        // TODO: find a more elegant way to do this that doesn't require intercepting all methods
        // http://stackoverflow.com/questions/31129003/mock-gradle-project-exec-using-metaprogramming
        // Would prefer to mock a single method, e.g. project.metaClass.exec {...}
        // However this doesn't work due to a Groovy bug: https://issues.apache.org/jira/browse/GROOVY-3493

        // This intercepts all methods, stubbing out exec and passing through all other invokes
        project.metaClass.invokeMethod = { String name, Object[] args ->
            // Call the proxy object so that it can track verifications
            if (name == 'copy') {
                return projectProxyInstance().copy((Closure) args.first())
            } else if (name == 'delete') {
                return projectProxyInstance().delete(args.first())
            } else if (name == 'exec') {
                return projectProxyInstance().exec((Closure) args.first())
            } else if (name == 'mkdir') {
                return projectProxyInstance().mkdir(args.first())
            } else {
                // This calls the delegate without causing infinite recursion
                // http://stackoverflow.com/a/10126006/1509221
                MetaMethod metaMethod = delegate.class.metaClass.getMetaMethod(name, args)
                debugLogInvokeMethod(name, args, metaMethod)

                // TODO: is there a way to do this automatically, e.g. coerceArgumentsToClasses?
                if (name == 'files') {
                    // Coerce the arguments to match the signature of Project.files(Object... paths)
                    if (args.size() == 0 ||  // files()
                        args.first() == null) {  // files(null)
                        return metaMethod?.invoke(delegate, [[] as Object[]] as Object[])
                    } else if (args.size() == 1) {
                        // files(ArrayList) possibly, so cast ArrayList to Object[]
                        return metaMethod?.invoke(delegate, [(Object[]) args.first()] as Object[])
                    } else {
                        return metaMethod?.invoke(delegate, [args] as Object[])
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
        // NOTE: String interpolation fails on Closure methods for some reason
        // Exception will be thrown on ", ${args.first()}"
        if (args.size() > 0) {
            if (args.first() == null) {
                call += ', null, ' + args + ', ' + args.first()
            } else {
                call += ', ' + args.first().class + ', ' + args + ', ' + args.first()
            }
        } else {
            call += ', ' + args
        }
        log.debug(call)
        log.debug("method: ${metaMethod.isValidExactMethod(args)}, " +
                  "${metaMethod.isValidMethod(args)}, " +
                  "$metaMethod")
    }

    private Project projectProxyInstance() {
        // proxyInstance is only created once and then reused forever
        // Requires new MockProjectExec for another demand / verify configuration
        if (proxyInstance == null) {
            proxyInstance = mockForProj.proxyInstance()
        }
        return (Project) proxyInstance
    }

    void verify() {
        mockForProj.verify((GroovyObject) projectProxyInstance())
    }

    void demandCopyAndReturn(String intoParam, String... fromParam) {
        assert proxyInstance == null, "Demand calls must be prior to calling projectProxyInstance"

        demandCopyAndReturn({
            into intoParam
            fromParam.each { String fromStr ->
                from fromStr
            }
        })
    }

    // CopySpec as parameter
    void demandCopyAndReturn(
            @ClosureParams(value = SimpleType.class, options = "org.gradle.api.file.CopySpec")
            @DelegatesTo(CopySpec)
                    Closure expectedClosure) {

        mockForProj.demand.copy { Closure closure ->

            DefaultCopySpec copySpec = new DefaultCopySpec(null, null)
            ConfigureUtil.configure(closure, copySpec)

            DefaultCopySpec expectedSpec = new DefaultCopySpec(null, null)
            ConfigureUtil.configure(expectedClosure, expectedSpec)

            // destDir - exceeds access rights
            assert ((String) expectedSpec.destDir).equals((String) copySpec.destDir)

            // includeEmptyDirs
            assert expectedSpec.getIncludeEmptyDirs() == copySpec.getIncludeEmptyDirs()

            // includes
            assert expectedSpec.getIncludes().equals(copySpec.getIncludes())

            // includes
            assert expectedSpec.getExcludes().equals(copySpec.getExcludes())

            // sourcepaths
            // equals between Set<Object> doesn't work, so compare List<String>
            List<String> expectedSourcepaths = expectedSpec.getSourcePaths().collect { Object obj ->
                return obj.toString()
            }
            List<String> sourcepaths = copySpec.getSourcePaths().collect { Object obj ->
                return obj.toString()
            }
            assert expectedSourcepaths == sourcepaths

            return (WorkResult) null
        }
    }

    static String getAbsolutePathFromObject(Object path) {
        if (path instanceof String) {
            return (String) path
        } else if (path instanceof File) {
            return ((File) path).getAbsolutePath()
        }
        assert false, "Not implemented for type: ${path.class}, ${path}"
        return null
    }

    void demandDeleteAndReturn(String... expectedPaths) {
        mockForProj.demand.delete { Object[] args ->

            // Convert args to list of path strings
            List<String> pathsList = new ArrayList<String>()
            args.each { Object obj ->
                pathsList.add(getAbsolutePathFromObject(obj))
            }

            List<Object> expectedPathsList = Arrays.asList(expectedPaths)
            assert expectedPathsList.equals(pathsList)

            // Assume that something was deleted
            return true
        }
    }

    void demandDeleteThenMkDirAndReturn(String expectedPath) {

        mockForProj.demand.delete { Object path ->
            assert expectedPath == getAbsolutePathFromObject(path)
        }
        mockForProj.demand.mkdir { Object path ->
            assert expectedPath == getAbsolutePathFromObject(path)
        }
    }

    void demandExecAndReturn(
            List<String> expectedCommandLine,
            List<String> expectedWindowsExecutableAndArgs) {
        demandExecAndReturn(
                null, expectedCommandLine, expectedWindowsExecutableAndArgs, null, null, null)
    }

    void demandExecAndReturn(
            List<String> expectedCommandLine) {
        demandExecAndReturn(
                null, expectedCommandLine, null, null, null, null)
    }

    void demandExecAndReturn(
            String expectWorkingDir,
            List<String> expectedCommandLine,
            String stdout, String stderr,
            Exception exceptionToThrow) {
        demandExecAndReturn(
                // Empty expectedWindowsExecutableAndArgs
                expectWorkingDir, expectedCommandLine, null, stdout, stderr, exceptionToThrow)
    }

    void demandExecAndReturn(
            String expectWorkingDir,
            List<String> expectedCommandLine,
            List<String> expectedWindowsExecutableAndArgs,
            String stdout, String stderr,
            Exception exceptionToThrow) {

        assert proxyInstance == null, "Demand calls must be prior to calling projectProxyInstance"

        mockForProj.demand.exec { Closure closure ->

            ExecSpec execSpec = new ExecHandleBuilder()
            // This prevents the test error: "Cannot convert relative path . to an absolute file."

            execSpec.setWorkingDir(initWorkingDir)

            ConfigureUtil.configure(closure, execSpec)

            // Substitute first entry in command line with Windows specific executable and args
            // Example for translateTask:
            //   Before: ['j2objc', expectedArgs...]
            //   After:  ['java', '-jar', '/J2OBJC_HOME/lib/j2objc.jar', expectedArgs...]
            if (Utils.isWindows() && expectedWindowsExecutableAndArgs != null) {
                expectedCommandLine.remove(0)
                expectedCommandLine.addAll(0, expectedWindowsExecutableAndArgs)
            }

            String expectedExecutable = expectedCommandLine.remove(0)
            assert TestingUtils.windowsToForwardSlash(expectedExecutable) ==
                   TestingUtils.windowsToForwardSlash(execSpec.getExecutable())
                           .replace(j2objcHome, j2objcHomeCanonicalized)

            // Actual Arguments => canonicalize to simplify writing unit tests
            List<String> actualArgsCanonicalized = execSpec.getArgs().collect { String arg ->
                return TestingUtils.windowsToForwardSlash(arg)
                        // Use '/J2OBJC_HOME' in unit tests
                        .replace(TestingUtils.windowsToForwardSlash(j2objcHome), j2objcHomeCanonicalized)
                        // Use '/PROJECT_DIR' in unit tests
                        .replace(TestingUtils.windowsToForwardSlash(project.projectDir.absolutePath), projectDirCanonicalized)
            }
            // Expected Arguments => Windows replacement
            // 1) pathSeparator replace, switching ':' for ';'
            // 2) separator replace, switching '\\' for '/'
            List<String> expectedArgsCanonicalized = expectedCommandLine.collect { String arg ->
                assert !arg.contains('\\')
                if (Utils.isWindows()) {
                    assert 'C:' == TestingUtils.windowsAbsolutePathPrefix
                    // Hacky way of preserving 'C:' prefix for absolute paths
                    arg = TestingUtils.windowsToForwardSlash(arg).replace(':', ';').replace('C;', 'C:')
                }
                return arg
            }
            assert expectedArgsCanonicalized == actualArgsCanonicalized

            // WorkingDir
            String actualWorkingDir =
                    TestingUtils.windowsToForwardSlash(execSpec.getWorkingDir().absolutePath)
            if (expectWorkingDir == null) {
                expectWorkingDir = initWorkingDir
            }
            assert TestingUtils.windowsToForwardSlash(expectWorkingDir) == actualWorkingDir

            if (stdout) {
                execSpec.getStandardOutput().write(stdout.getBytes('utf-8'))
                execSpec.getStandardOutput().flush()
                // warn is needed to output to stdout in unit tests
                log.warn(stdout)
            }
            if (stderr) {
                execSpec.getErrorOutput().write(stderr.getBytes('utf-8'))
                execSpec.getErrorOutput().flush()
                log.error(stderr)
            }

            if (exceptionToThrow != null) {
                throw exceptionToThrow
            }

            return (ExecResult) null
        }
    }

    void demandMkDirAndReturn(String expectedPath) {
        mockForProj.demand.mkdir { Object path ->
            assert expectedPath.equals(getAbsolutePathFromObject(path))
            return project.file(path)
        }
    }
}
