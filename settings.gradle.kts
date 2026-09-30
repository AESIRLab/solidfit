import java.util.Properties
import java.io.FileInputStream

val localProperties = Properties().apply {
    val file = rootDir.resolve("local.properties")
    if (file.exists()) {
        load(FileInputStream(file))
    }
}

val gprUser: String? = localProperties.getProperty("gpr.user")
val gprKey: String? = localProperties.getProperty("gpr.key")

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/aesirlab/annotations-repo")
            credentials {
                username = gprUser
                password = gprKey
            }
        }
    }
}

rootProject.name = "solidfit"
include(":app")
//include(":app:solid-auth")
//include(":app:solid-annotation")
//include(":app:solid-processor")
