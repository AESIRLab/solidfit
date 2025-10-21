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
                onDelete = onDeleteWorkout,
                onEdit = onEditWorkout,
                onSelect = onSelectWorkout
            )
        }
    }
}