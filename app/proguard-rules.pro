# Keep Room generated code
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep DataStore
-keep class androidx.datastore.** { *; }

# Kotlin metadata for coroutines
-dontwarn kotlinx.coroutines.**
