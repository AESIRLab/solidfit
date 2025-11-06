package com.example.solidfit

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.example.solidfit.data.WorkoutItemRepository
import com.example.solidfit.healthdata.HealthConnectManager
import com.example.solidfit.data.WorkoutItemDatabase
import java.io.File

//needed
//val Context.dataStore: DataStore<Preferences> by preferencesDataStore("userData")
class WorkoutItemSolidApplication: Application(), ImageLoaderFactory {
    val healthConnectManager by lazy {
        HealthConnectManager(this)
    }
    init {
        appInstance = this
    }

    companion object {
        lateinit var appInstance: WorkoutItemSolidApplication
        const val FILE_PATH = "WorkoutItemApplication"
        const val BASE_URI = "https://solidworkout.com"
        const val IMAGES_DIR = "AndroidApplication/Images/SolidFit/"

    }

//    private val database by lazy { WorkoutItemDatabase.getDatabase(appInstance, BASE_URI, FILE_PATH) }
    private val database by lazy { WorkoutItemDatabase.getDatabase(appInstance, BASE_URI) }
    val repository by lazy { WorkoutItemRepository(database.WorkoutItemDao()) }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient {
                getUnsafeOkHttpClient()
            }

            .respectCacheHeaders(false)

            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "solid_image_cache"))
                    .maxSizeBytes(512 * 1024 * 1024)
                    .build()
            }
            .build()
    }
}