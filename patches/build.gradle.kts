group = "app.template"

patches {
    about {
        name = "AARE Background Location Patches"
        description = "Lets AARE keep navigation apps' GPS with the phone locked after a background start"
        source = "git@github.com:Pirrotto/aare-patches.git"
        author = "Pirrotto"
        contact = "https://github.com/Pirrotto/aare-patches/issues"
        website = "https://github.com/Pirrotto/aare-patches"
        license = "GPLv3"
    }
}

// Separate configuration so gson is available at runtime for the
// generatePatchesList task but never bundled into the APK.
val patchListGeneratorClasspath = configurations.create("patchListGeneratorClasspath")

dependencies {
    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
    }

    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}
