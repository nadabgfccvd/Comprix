import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    jacoco
}

/**
 * Gera automaticamente (uma unica vez) a keystore usada para assinar o build de
 * release. Assim `./gradlew assembleRelease` funciona sem nenhum passo manual,
 * conforme exigido na Secao 12 do briefing.
 */
fun garantirKeystoreDeRelease(): File {
    val arquivo = rootProject.file("keystore/comprix-release.jks")
    if (!arquivo.exists()) {
        arquivo.parentFile.mkdirs()
        val keytool = File(File(System.getProperty("java.home"), "bin"), "keytool").absolutePath
        val processo = ProcessBuilder(
            keytool, "-genkeypair", "-v",
            "-keystore", arquivo.absolutePath,
            "-storetype", "PKCS12",
            "-storepass", "comprix",
            "-keypass", "comprix",
            "-alias", "comprix",
            "-keyalg", "RSA",
            "-keysize", "2048",
            "-validity", "10000",
            "-dname", "CN=Comprix, OU=Mobile, O=Comprix, L=Diamantina, ST=MG, C=BR",
        ).redirectErrorStream(true).start()
        processo.inputStream.bufferedReader().readText()
        processo.waitFor()
    }
    return arquivo
}

android {
    namespace = "br.com.comprix"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.comprix"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "1.4.0"
        vectorDrawables.useSupportLibrary = true
        resourceConfigurations += listOf("pt-rBR")
    }

    signingConfigs {
        create("comprix") {
            val ks = garantirKeystoreDeRelease()
            storeFile = ks
            storePassword = "comprix"
            keyAlias = "comprix"
            keyPassword = "comprix"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            // Instrumenta os testes JVM para o relatorio de cobertura do dominio.
            enableUnitTestCoverage = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("comprix")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-Xjvm-default=all",
            // FlowRow segue @ExperimentalLayoutApi na foundation 1.7.x e o app a usa
            // em varias telas; opt-in global evita anotar composable por composable.
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "META-INF/*.version",
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    // --- Base Android / Kotlin ---
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // --- Compose / Material 3 ---
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // --- Persistencia local (Room / SQLite) ---
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // --- Camera (CameraX) ---
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-video:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    // --- ML Kit ON-DEVICE, variantes BUNDLED (modelo embarcado no APK:
    //     funciona offline ja na primeira execucao, sem baixar nada) ---
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // --- Testes (camada de dominio, 100% JVM) ---
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

/**
 * Relatorio de cobertura da camada de dominio (meta do briefing: > 80%).
 *
 * Uso: ./gradlew jacocoDominioReport
 * Saida: app/build/reports/jacoco/jacocoDominioReport/html/index.html
 */
tasks.register<JacocoReport>("jacocoDominioReport") {
    dependsOn("testDebugUnitTest")
    group = "verification"
    description = "Cobertura de testes das classes de dominio e util (logica pura)."

    reports {
        html.required.set(true)
        xml.required.set(true)
        csv.required.set(true)
    }

    val classesDominio = fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
        include("br/com/comprix/domain/**", "br/com/comprix/util/**")
        // Modelos/enums sao apenas dados - a meta vale para a logica de negocio.
        exclude("br/com/comprix/domain/modelo/**")
    }
    classDirectories.setFrom(classesDominio)
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(fileTree(layout.buildDirectory) { include("**/testDebugUnitTest.exec") })
}
