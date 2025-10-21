package com.example.solidfit.data

import org.skCompiler.generatedModel.WorkoutItemDao
import androidx.`annotation`.WorkerThread
import com.example.solidfit.model.WorkoutItem
import kotlin.String
import kotlin.collections.List
import kotlinx.coroutines.flow.Flow

public class WorkoutItemRepository(
    private val workoutItemDao: WorkoutItemDao,
) {
    public val allWorkoutItemsAsFlow: Flow<List<WorkoutItem>> =
        workoutItemDao.getAllWorkoutItemsAsFlow()

    public fun getWorkoutItemLiveData(uri: String): Flow<WorkoutItem> =
        workoutItemDao.getWorkoutItemByIdAsFlow(uri)

    public fun allWorkoutItems(): List<WorkoutItem> = workoutItemDao.getAllWorkoutItems()

    @WorkerThread
    public suspend fun insertMany(itemList: List<WorkoutItem>) {
        itemList.forEach {
            workoutItemDao.insert(it)
        }
    }

    @WorkerThread
    public suspend fun update(item: WorkoutItem) {
        workoutItemDao.update(item)
    }

    @WorkerThread
    public suspend fun insert(item: WorkoutItem) {
        workoutItemDao.insert(item)
    }

    @WorkerThread
    public suspend fun deleteByUri(uri: String) {
        workoutItemDao.delete(uri)
    }

    public fun insertWebId(webId: String) {
        workoutItemDao.updateWebId(webId)
    }

    @WorkerThread
    public suspend fun deleteAll() {
        workoutItemDao.deleteAll()
    }

    public fun resetModel() {
        workoutItemDao.resetModel()
    }

    @WorkerThread
    public suspend fun overwriteModelWithList(items: List<WorkoutItem>) {
        workoutItemDao.overwriteModelWithList(items)
    }
}
