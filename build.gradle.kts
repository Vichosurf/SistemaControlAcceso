// Plugins del proyecto. Firebase se habilita en el módulo app cuando exista google-services.json.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}
