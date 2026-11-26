buildscript {
    dependencies {
        // AGP 9.x trae Kotlin integrado 2.2.10 por defecto.
        // Firebase Auth 25.x requiere metadatos Kotlin 2.4.x.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

// Plugins del proyecto. Firebase se habilita en el módulo app cuando exista google-services.json.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}
