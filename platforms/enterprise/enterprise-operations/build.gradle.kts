plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Build operations consumed by the Gradle Enterprise plugin"

dependencies {
    api(project(":build-operations"))
    api(project(":base-annotations"))

    api(libs.jsr305)
}

dependencyAnalysis {
    issues {
        onAny {
            severity("fail")
        }
    }
}
