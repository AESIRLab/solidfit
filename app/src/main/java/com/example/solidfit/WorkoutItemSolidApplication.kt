package com.example.solidfit

import android.app.Application
import com.example.solidfit.data.WorkoutItemRepository
import com.example.solidfit.healthdata.HealthConnectManager
import org.skCompiler.generatedModel.WorkoutItemDatabase

//needed
//val Context.dataStore: DataStore<Preferences> by preferencesDataStore("userData")
class WorkoutItemSolidApplication: Application() {
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

}