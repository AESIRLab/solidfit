package com.example.solidfit.data

import com.example.solidfit.model.WorkoutItem
import kotlin.String
import kotlin.collections.List
import kotlinx.coroutines.flow.Flow

public interface WorkoutItemDao {
  public fun getWorkoutItemByIdAsFlow(id: String): Flow<WorkoutItem>

  public suspend fun delete(uri: String)

  public suspend fun insert(item: WorkoutItem)

  public suspend fun update(item: WorkoutItem)

  public fun getAllWorkoutItems(): List<WorkoutItem>

  public fun getAllWorkoutItemsAsFlow(): Flow<List<WorkoutItem>>

  public fun updateWebId(webId: String)

  public suspend fun deleteAll()

  public fun resetModel()

  public suspend fun overwriteModelWithList(items: List<WorkoutItem>)
}
