extension {
    name = "extensions/extension.mpe"
}

android {
    namespace = "app.template.extension"
}

dependencies {
    // Provided by AARE at runtime (it uses Shizuku for car audio): compile against the same version.
    compileOnly("dev.rikka.shizuku:api:13.1.5")
}

dependencies {
    // TunnelMathTest and other pure-Java tests, run in CI before every release.
    testImplementation("junit:junit:4.13.2")
}

// The patches' version, shown at the bottom of the patches' Settings and in "Copy diagnostics":
// "v2.0.0" for a release, or the test build's label from CI ("test 20261124-0610 · 947099b").
val patchVersionDir = layout.buildDirectory.dir("generated/patchversion")
val generatePatchVersion = tasks.register("generatePatchVersion") {
    val label = providers.environmentVariable("PATCH_BUILD_LABEL").orElse("v${project.version}")
    inputs.property("label", label)
    outputs.dir(patchVersionDir)
    doLast {
        val file = patchVersionDir.get().file("app/template/extension/aare/PatchVersion.java").asFile
        file.parentFile.mkdirs()
        val text = label.get().replace("\\", "").replace("\"", "")
        file.writeText(
            "package app.template.extension.aare;\n\n" +
                "/** Generated at build time (extensions/extension/build.gradle.kts). */\n" +
                "final class PatchVersion {\n" +
                "    static final String LABEL = \"$text\";\n\n" +
                "    private PatchVersion() {\n    }\n}\n"
        )
    }
}
android.sourceSets.getByName("main").java.srcDir(patchVersionDir.get().asFile)
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(generatePatchVersion) }
