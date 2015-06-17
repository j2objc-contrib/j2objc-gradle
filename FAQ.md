# User FAQ
(for contributors, see [CONTRIBUTING.md](CONTRIBUTING.md))

### What version of Gradle do I need?

You need at least [Gradle version 2.4](https://discuss.gradle.org/t/gradle-2-4-released/9471), due to support for native compilation features.

### How do I solve the Eclipse error message ``Obtaining Gradle model...``?

You have to create the [Gradle wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html) first. Go to your project folder and do ``gradle wrapper``. Refresh your Eclipse project.

### How do I include Java files from additional source directories?

In order to include source files from sources different than ``src/main/java`` you have to [modify the Java plugin's sourceSet(s)](https://docs.gradle.org/current/userguide/java_plugin.html#N11FD1). For example, if you want to include files from ``src-gen/base`` both into your JAR and (translated) into your Objective C libraries, then add to your ``build.gradle``:

```
sourceSets {
  main {
    java {
      srcDir 'src-gen/base'
    }
  }
}
```
