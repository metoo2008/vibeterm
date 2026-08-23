// 阿里云镜像置前是为了中国大陆网络可直接构建;内容与官方仓库一致,海外网络亦可用,介意可调整顺序。
// Aliyun mirrors are listed first so builds work smoothly from mainland China; they serve the same
// artifacts as the official repos and work fine elsewhere too. Reorder if you prefer.
pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        google()
        mavenCentral()
    }
}
rootProject.name = "VibeTerm"
include(":app", ":terminal-emulator", ":terminal-view")
