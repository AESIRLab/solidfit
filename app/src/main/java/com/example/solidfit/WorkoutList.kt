package com.example.solidfit

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.solidfit.model.WorkoutItem

@Composable
fun WorkoutList(
    workouts: List<WorkoutItem>,
    viewModel: WorkoutItemViewModel,
    onDeleteWorkout: (WorkoutItem) -> Unit,
    onEditWorkout: (WorkoutItem) -> Unit,
    onSelectWorkout: (WorkoutItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        items(workouts) { workout ->
            WorkoutItem(
                workout = workout,
                viewModel = viewModel,
                onDelete = onDeleteWorkout,
                onEdit = onEditWorkout,
                onSelect = onSelectWorkout
            )
        }
    }
}