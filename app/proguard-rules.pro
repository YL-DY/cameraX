# ProCam ProGuard / R8 Rules

# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep CameraX
-keep class androidx.camera.** { *; }

# Keep Timber
-keep class timber.log.Timber** { *; }

# Keep DataStore
-keep class androidx.datastore.** { *; }

# Keep our models (for serialization/deserialization)
-keep class com.professional.cam.domain.model.** { *; }
-keep class com.professional.cam.camera.capability.** { *; }
-keep class com.professional.cam.core.error.** { *; }

# Keep enum classes
-keepclassmembers enum * { *; }

# Keep Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
