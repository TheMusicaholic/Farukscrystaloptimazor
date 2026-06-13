plugins {
    alias(libs.plugins.fabric.loom)
    `maven-publish`
}

version = "1.1.0"
group = "com.deathmotion.marlowcrystal"

base {
    // Use a filesystem-safe slug for the artifact name. project.name is the
    // human-readable "Marlow's Crystal Optimizer", whose spaces and apostrophe
    // produce a jar/Maven artifactId that publishing tools (mc-publish,
    // Modrinth/CurseForge) fail to resolve ("Unable to infer project type").
    archivesName.set("marlows-crystal-optimizer-${libs.versions.minecraft.get()}")
}

dependencies {
    minecraft(libs.minecraft)
    mappings(loom.officialMojangMappings())
    modImplementation(libs.fabric.loader)
    modImplementation(libs.fabric.api)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}

tasks {
    java {
        disableAutoTargetJvm()
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }

    jar {
        from("LICENSE") {
            rename { "${it}_${project.base.archivesName.get()}" }
        }
    }

    withType<JavaCompile> {
        options.encoding = Charsets.UTF_8.name()
        options.release = 21
    }

    processResources {
        inputs.property("version", project.version)
        inputs.property("loader_version", libs.versions.fabric.loader.get())
        filteringCharset = "UTF-8"

        filesMatching("fabric.mod.json") {
            expand(
                "version" to project.version,
                "loader_version" to libs.versions.fabric.loader.get()
            )
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.base.archivesName.get()
            from(components["java"])
        }
    }
}