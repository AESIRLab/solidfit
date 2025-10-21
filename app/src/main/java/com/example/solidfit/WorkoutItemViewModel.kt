package com.example.solidfit

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.solidfit.data.WorkoutItemRepository
import com.example.solidfit.model.WorkoutItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.skCompiler.generatedModel.WorkoutItemRemoteDataSource

class WorkoutItemViewModel(
    private val repository: WorkoutItemRepository,
    private val remoteDataSource: WorkoutItemRemoteDataSource
): ViewModel() {

    private var _allItems: MutableStateFlow<List<WorkoutItem>> = MutableStateFlow(listOf())
    val allItems: StateFlow<List<WorkoutItem>> get() = _allItems

    private val _workoutItem = MutableStateFlow<WorkoutItem?>(null)
    val workoutItem: StateFlow<WorkoutItem?> = _workoutItem


    init {
        this.viewModelScope.launch {
            val newList = mutableListOf<WorkoutItem>()
            try{
                if (remoteDataSource.remoteAccessible()) {
                    newList += remoteDataSource.fetchRemoteItemList()
                }
                repository.allWorkoutItemsAsFlow.collect { list ->
                    newList += list
                }
                _allItems.value = newList.distinctBy { it.id }
            } catch (e: NullPointerException) {
                    Log.e("WorkoutViewModel", "Error loading RDF model: ${e.message}")
                    _allItems.value = emptyList()
            } catch (e: Exception) {
                    Log.e("WorkoutViewModel", "Unexpected error: ${e.message}")
                    _allItems.value = emptyList()
            }
        }
    }

    fun remoteIsAvailable(): Boolean {
        return remoteDataSource.remoteAccessible()
    }

    fun setRemoteRepositoryData(
        accessToken: String,
        signingJwk: String,
        webId: String,
        expirationTime: Long,
    ) {
        remoteDataSource.signingJwk = signingJwk
        remoteDataSource.webId = webId
        remoteDataSource.expirationTime = expirationTime
        remoteDataSource.accessToken = accessToken
    }

    fun updateWebId(webId: String) {
        viewModelScope.launch {
            // 1) Keep the new webId (or reset if bad)
            try {
                repository.insertWebId(webId)
            } catch (e: Exception) {
                repository.resetModel()
            }

            // 2) Fetch remote snapshot
            val remote = if (remoteDataSource.remoteAccessible())
                remoteDataSource.fetchRemoteItemList()
            else
                emptyList()

            // 3) Fetch one snapshot of local items
            val local = repository.allWorkoutItemsAsFlow.firstOrNull() ?: emptyList()

            // 4) Merge & de-duplicate
            val merged = (remote + local)
                .distinctBy { it.id }

            // 5) Overwrite local cache so you never “see” dupes again
            repository.overwriteModelWithList(merged)

            // 6) Update UI
            _allItems.value = merged

            // 7) Push merged back to remote
            remoteDataSource.updateRemoteItemList(merged)
        }
    }


    suspend fun fetchRemoteList() {
        // 1) Pull remote and local snapshots
        val remote = remoteDataSource.fetchRemoteItemList()
        val local = repository.allWorkoutItemsAsFlow.firstOrNull() ?: emptyList()

        // 2) Merge & de-duplicate
        val merged = (remote + local)
            .distinctBy { it.id }

        // 3) Overwrite local cache so future reads never re-introduce dupes
        repository.overwriteModelWithList(merged)

        // 4) Update UI
        _allItems.value = merged
    }


    suspend fun insert(item: WorkoutItem) {
        val tempList = mutableListOf<WorkoutItem>()
        viewModelScope.launch {
            repository.insert(item)
            // not sure if this is the right way to do it...
            repository.allWorkoutItemsAsFlow.collect { list ->
                tempList += list
            }
        }
        viewModelScope.launch {
            _allItems.value = tempList
            remoteDataSource.updateRemoteItemList(tempList)
        }
    }

    suspend fun insertMany(list: List<WorkoutItem>) {
        viewModelScope.launch {
            repository.insertMany(list)
            repository.allWorkoutItemsAsFlow.collect { list ->
                _allItems.value = list
            }
        }
    }

    fun delete(item: WorkoutItem) {
        viewModelScope.launch {
            // 1) Delete it from your local RDF store
            repository.deleteByUri(item.id)

            // 2) Pull one snapshot of your now-current local list
            val remaining: List<WorkoutItem> =
                repository.allWorkoutItemsAsFlow.firstOrNull() ?: emptyList()

            // 3) Update your in-memory UI state
            _allItems.value = remaining

            // 4) Sync that exact list back to your Pod
            remoteDataSource.updateRemoteItemList(remaining)
        }
    }


    suspend fun updateRemote() {
        viewModelScope.launch {
            repository.allWorkoutItemsAsFlow.collect { list ->
                _allItems.value = list
            }.also {
                remoteDataSource.updateRemoteItemList(_allItems.value)
            }
        }
    }

    suspend fun update(item: WorkoutItem) {
        withContext(Dispatchers.IO) {
            repository.update(item)
        }

        val list = repository.allWorkoutItemsAsFlow.first()

        withContext(Dispatchers.Main) {
            _allItems.value = list
        }

        withContext(Dispatchers.IO) {
            remoteDataSource.updateRemoteItemList(list)
        }
    }

    private fun merge(remote: List<WorkoutItem>, local: List<WorkoutItem>): List<WorkoutItem> =
        (remote + local).distinctBy { it.id }

    fun loadWorkoutById(id: String) {
        viewModelScope.launch {
            // Try local-first
            repository.getWorkoutItemLiveData(id).firstOrNull()?.let {
                _workoutItem.value = it
                return@launch
            }

            // Fallback to in-memory merged list
            val fromMerged = _allItems.value.find { it.id == id }
            if (fromMerged != null) {
                _workoutItem.value = fromMerged
            } else if (remoteDataSource.remoteAccessible()) {
                // as a last resort, pull fresh remote list into merged
                val remote = remoteDataSource.fetchRemoteItemList()
                val local = repository.allWorkoutItemsAsFlow.firstOrNull() ?: emptyList()
                val merged = merge(remote, local)
                _allItems.value = merged
                _workoutItem.value = merged.find { it.id == id }
            } else {
                _workoutItem.value = null
            }
        }
    }


    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as WorkoutItemSolidApplication)
                val itemRepository = application.repository
                val itemRemoteDataSource = WorkoutItemRemoteDataSource(externalScope = CoroutineScope(SupervisorJob() + Dispatchers.Default))
                WorkoutItemViewModel(itemRepository, itemRemoteDataSource)
            }
        }
    }
}