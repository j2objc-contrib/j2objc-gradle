# FAQ

### Eclipse raises error ``Obtaining Gradle model...``?

You have to create the gralde wrapper first. Go to your project folder and do ``gradle wrapper``. Refresh your eclipse project.


### How to include files from additional source dirs?

In order to include source files from sources different than ``src/main/java`` you have to define a sourceSet. Assume you want to include files from ``build/source/base`` add to your ``build.gradle``:

```
sourceSets {
  main {
    java {
      srcDir 'build/source/base'
    }
  }
}
```
