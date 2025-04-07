plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "it.reply.cm"
version = "0.1.11"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}



tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    patchPluginXml {
        sinceBuild.set("241")
        untilBuild.set("243.*")
    }

    signPlugin {
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
        certificateChain.set(File(System.getenv("CERTIFICATE_CHAIN") ?: "C:\\Users\\e.palmisano\\Desktop\\chain.crt").readText(Charsets.UTF_8))
        certificateChain.set(File(System.getenv("PRIVATE_KEY") ?: "C:\\Users\\e.palmisano\\Desktop\\private.pem").readText(Charsets.UTF_8))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }



}

dependencies {

    intellijPlatform {
        intellijIdeaCommunity("2024.3.5")
        plugins(providers.gradleProperty("platformPlugins").map { it.split(',') })
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })
    }

}

dependencies {

    implementation("org.bsc.langgraph4j:langgraph4j-core:1.5.0")
    implementation("dev.langchain4j:langchain4j:1.0.0-beta2")
    implementation("ch.qos.logback:logback-classic:1.4.12")
    implementation("org.bsc.langgraph4j:langgraph4j-langchain4j:1.5.0")
    implementation("dev.langchain4j:langchain4j-open-ai:1.0.0-beta2")
    implementation("org.projectlombok:lombok:1.18.34")
}