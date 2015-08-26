package com.github.j2objccontrib.j2objcgradle

import com.github.j2objccontrib.j2objcgradle.tasks.TestingUtils
import com.github.j2objccontrib.j2objcgradle.tasks.Utils
import groovy.util.logging.Slf4j
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.nativeplatform.NativeBinarySpec
import org.gradle.nativeplatform.NativeLibraryBinary
import org.gradle.platform.base.BinarySpec
import org.junit.Before
import org.junit.Test

/**
 * Integration tests with multiple projects.
 */
@Slf4j
class MultiProjectTest {

    private Project rootProj, proj1, proj2
    private J2objcConfig j2objcConfig1, j2objcConfig2

    @Before
    void setUp() {
        Utils.setFakeOSMacOSX()
        // We can't use _ for unused slots because the other variables have already been declared above.
        Object unused

        // The root needs a local.properties, but nothing else.
        (rootProj, unused, unused) = TestingUtils.setupProject(new TestingUtils.ProjectConfig())

        // Project 1 will have its J2objcPlugin applied dynamically if needed.
        (proj1, unused, j2objcConfig1) = TestingUtils.setupProject(new TestingUtils.ProjectConfig(
                applyJavaPlugin: true,
                createReportsDir: true,
                rootProject: rootProj
        ))
        (proj2, unused, j2objcConfig2) = TestingUtils.setupProject(new TestingUtils.ProjectConfig(
                applyJ2objcPlugin: true,
                createReportsDir: true,
                rootProject: rootProj
        ))
    }

    @Test(expected = InvalidUserDataException)
    void twoProjectsWithDependsOnJ2objcLib_MissingPluginOnProject1() {
        j2objcConfig2.dependsOnJ2objcLib(proj1)
        j2objcConfig2.finalConfigure()
    }

    @Test
    void twoProjectsWithDependsOnJ2objcLib_Works() {
        // TODO: fix this to run on Windows
        // https://github.com/j2objc-contrib/j2objc-gradle/issues/374
        // org.gradle.api.UnknownTaskException: Task with path 'releaseTestJ2objcExecutable' not found in project ':testProject8'
        if (Utils.isWindowsNoFake()) {
            return
        }

        proj1.pluginManager.apply(J2objcPlugin)
        j2objcConfig1 = J2objcConfig.from(proj1)
        j2objcConfig1.finalConfigure()

        // Will force evaluation of proj1.
        j2objcConfig2.dependsOnJ2objcLib(proj1)
        j2objcConfig2.finalConfigure()

        boolean evaluated = false
        proj2.afterEvaluate { Project project ->
            evaluated = true
            // This forces the native plugin to convert the model rules to actual binaries.
            project.tasks.realize()
            project.bindAllModelRules()
        }

        // Should force evaluation of proj2.
        rootProj.evaluationDependsOnChildren()
        assert evaluated

        // proj2 should build after proj1
        assert TestingUtils.getTaskDependencies(proj2, 'j2objcPreBuild').contains(proj1.tasks.getByName('jar'))
        assert TestingUtils.getTaskDependencies(proj2, 'j2objcPreBuild').contains(proj1.tasks.getByName('j2objcBuild'))

        // proj2's Objective C libraries should link to proj1's Objective C libraries
        proj2.binaries.each { BinarySpec binary ->
            // Some of the binaries are Java classes, don't test those.
            if (binary instanceof NativeBinarySpec) {
                NativeBinarySpec nativeBinary = (NativeBinarySpec) binary
                // Some of the binaries may be the user's custom native binaries, don't test those.
                if (nativeBinary.name.contains('j2objc')) {
                    // There may be other dependencies, so we use 'any'.
                    assert nativeBinary.libs.any {
                        NativeLibraryBinary bin = it.binary
                        // The rest of the name indicates debug/release, architecture, etc.
                        return bin.displayName.startsWith("static library '${proj1.name}-j2objc:")
                    }
                }
            }
        }
    }

}
