// 默认使用官方仓库(google / mavenCentral / gradlePluginPortal),保证 F-Droid、CI 及海外贡献者的
// 构建环境干净、可复现。
//
// 中国大陆开发者可启用阿里云镜像加速:设置环境变量 VIBETERM_CN_MIRROR=true 再构建。
// 镜像内容与官方一致,仅调整解析顺序。
pluginManagement {
    repositories {
        if (System.getenv("VIBETERM_CN_MIRROR") == "true") {
            maven("https://maven.aliyun.com/repository/gradle-plugin")
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/central")
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (System.getenv("VIBETERM_CN_MIRROR") == "true") {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/central")
        }
        google()
        mavenCentral()
    }
}
rootProject.name = "VibeTerm"
include(":app", ":terminal-emulator", ":terminal-view")
