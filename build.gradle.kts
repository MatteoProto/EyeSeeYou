// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath (libs.gradle) // Usa la versione appropriata
        classpath ("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22") // Usa la versione appropriata
        // NOTA: Il plugin 'com.google.gms.google-services' NON è strettamente
        // necessario solo per play-services-wearable, ma spesso è presente per altri servizi Firebase/GMS.
        // classpath 'com.google.gms:google-services:4.4.1' // Aggiungi se usi altri servizi GMS/Firebase
    }
}