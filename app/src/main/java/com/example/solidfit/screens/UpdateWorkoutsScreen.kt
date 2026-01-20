package com.example.solidfit.screens

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.RequiresApi
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.solidfit.R
import com.example.solidfit.BottomNavItem
import com.example.solidfit.SolidAuthFlowScreen
import com.example.solidfit.WorkoutItemViewModel
import com.example.solidfit.WorkoutList
import com.example.solidfit.healthdata.HealthConnectManager
import com.example.solidfit.healthdata.InputReadingsViewModel
import com.example.solidfit.healthdata.InputReadingsViewModelFactory
import com.example.solidfit.healthdata.showExceptionSnackbar
import com.example.solidfit.model.WorkoutItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.solidfit.data.AuthTokenStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.navigation.NavHostController

@RequiresApi(Build.VERSION_CODES.S)
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun UpdateWorkouts(
    healthConnectManager: HealthConnectManager,
    authNavController: NavHostController
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val store = AuthTokenStore(LocalContext.current.applicationContext)
    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val viewModel: WorkoutItemViewModel = viewModel(
        factory = WorkoutItemViewModel.Factory
    )
    // Used for bottom bar navigation
    val bottomBarScreens = listOf(
        SolidAuthFlowScreen.WorkoutList.name,
        SolidAuthFlowScreen.AddEditWorkoutScreen.name,
        SolidAuthFlowScreen.HeartRateMonitor.name,
        SolidAuthFlowScreen.WeightMonitor.name,
        SolidAuthFlowScreen.WorkoutCardScreen.name,
        SolidAuthFlowScreen.SettingsScreen.name
    )
    val currentRoute = currentDestination?.route
    val showBottomBar = bottomBarScreens.any {screenRoute ->
        currentRoute?.startsWith(screenRoute) == true
    }
    val navBarItems = listOf(
        BottomNavItem.WorkoutList,
        BottomNavItem.HeartMonitor,
        BottomNavItem.WeightMonitor,
        BottomNavItem.Settings,
    )

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                val webId = store.getWebId().first()
                val accessToken = store.getAccessToken().first()
                val signingJwk = store.getSigner().first()
                val expirationTime = store.getTokenExpiresAt().first()
                viewModel.setRemoteRepositoryData(
                    accessToken = accessToken,
                    signingJwk = signingJwk,
                    webId = webId,
                    expirationTime = expirationTime
                )
                viewModel.updateWebId(webId)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.hsl(
                        224f,
                        1f,
                        0.73f
                    ),
//                    containerColor = MaterialTheme.colorScheme.primary,
                ),
                title = {
                    Row (modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 30.dp),
                        horizontalArrangement = Arrangement.SpaceBetween)
                    {
                        Text(
                            "SolidFit",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Image(
                            painter = painterResource(id = R.drawable.exercise_white_34dp),
                            contentDescription = "App logo"
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    navBarItems.forEach { screen ->
                        val isSelected = currentRoute?.startsWith(screen.route) == true ||
                                (currentRoute?.startsWith(SolidAuthFlowScreen.WorkoutCardScreen.name) == true &&
                                        screen.route == SolidAuthFlowScreen.WorkoutList.name)

                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = isSelected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            Crossfade(
                targetState = currentRoute,
                animationSpec = tween(durationMillis = 600), label = ""
            ) { route ->
                when {
                    // This will only show the FAB on the WorkoutList screen
                    route == SolidAuthFlowScreen.WorkoutList.name -> {
//                    currentDestination?.route == SolidAuthFlowScreen.WorkoutList.name -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 32.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            FloatingActionButton(
                                containerColor = Color.hsl(224f, 1f, 0.73f, 0.75f),
                                onClick = { navController.navigate(route = SolidAuthFlowScreen.AddEditWorkoutScreen.name) },
                                shape = CircleShape,
                                elevation = FloatingActionButtonDefaults.elevation(
                                    defaultElevation = 0.dp,
                                    hoveredElevation = 0.dp
                                ),
                            ) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = "Add workout",
                                    modifier = Modifier
                                        .size(28.dp)
                                )
                            }
                        }
                    }
                    // Shows FAB "Back Button" on Workout Card Screen
                    route?.startsWith(SolidAuthFlowScreen.WorkoutCardScreen.name) == true -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 32.dp),
                            horizontalArrangement = Arrangement.Absolute.Left,
                        ) {
                            FloatingActionButton(
                                containerColor = Color.hsl(224f, 1f, 0.73f, 0.75f),
                                onClick = {
                                    navController.popBackStack()
                                },
                                shape = CircleShape,
                                elevation = FloatingActionButtonDefaults.elevation(
                                    defaultElevation = 0.dp,
                                    hoveredElevation = 0.dp
                                )
                            ) {
                                Icon(
                                    Icons.Filled.ArrowBack,
                                    contentDescription = "Return to list",
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = SolidAuthFlowScreen.WorkoutList.name,
//            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding()
                ),
        ) {
            // SCREEN: displays the list of workouts
            composable(route = SolidAuthFlowScreen.WorkoutList.name) {
                // Fetches all workout items from repo
                val workouts by viewModel.allItems.collectAsState(initial = emptyList())
                val isReady by viewModel.isReady.collectAsState()

                if (isReady) {
                    WorkoutList(
                        workouts = workouts,
                        viewModel = viewModel,
                        onDeleteWorkout = { workout ->
                            coroutineScope.launch {
                                viewModel.delete(workout)
                            }
                        },
                        onEditWorkout = { workout ->
                            Log.d("Debug", workout.id)
                            navController.navigate(route = "${SolidAuthFlowScreen.AddEditWorkoutScreen.name}/${workout.id}")
                        },
                        onSelectWorkout = { workout ->
                            navController.navigate(route = "${SolidAuthFlowScreen.WorkoutCardScreen.name}/${workout.id}")
                        }
                    )
                }
                else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            // SCREEN: Workout Card
            composable(
                route = "${SolidAuthFlowScreen.WorkoutCardScreen.name}/{workoutId}",
                arguments = listOf(navArgument("workoutId") { type = NavType.StringType })
            ) { backStackEntry ->
                val workoutId = backStackEntry.arguments!!.getString("workoutId")!!
                LaunchedEffect(workoutId) {
                    viewModel.loadWorkoutById(workoutId)
                }
                val workoutState by viewModel.workoutItem.collectAsState()
                workoutState?.let { workout ->
                    WorkoutCard(
                        workout,
                        viewModel = viewModel
                    )
                }
            }

            // SCREEN: Edit workout
            composable(
                route = "${SolidAuthFlowScreen.AddEditWorkoutScreen.name}/{workoutId}",
                arguments = listOf(navArgument("workoutId") {
                    type = NavType.StringType
                })
            ) { backStackEntry ->
                val workoutId =
                    backStackEntry.arguments?.getString("workoutId") ?: return@composable
                LaunchedEffect(workoutId) {
                    viewModel.loadWorkoutById(workoutId)
                }
                val workoutState by viewModel.workoutItem.collectAsState()
                val editWorkoutCoroutineScope = rememberCoroutineScope()

                workoutState?.let { workout ->
                    key(workout.id) {
                        AddEditWorkoutScreen(
                            workout = workoutState,
                            viewModel = viewModel,
                            onSaveWorkout = { _, name, quantity, duration, workoutType, datePerformed, notes, mediaUri ->

                                val updated = workout.copy(
                                    name = name,
                                    dateModified = System.currentTimeMillis(),
                                    workoutType = workoutType,
                                    quantity = quantity,
                                    duration = duration,
                                    datePerformed = datePerformed,
                                    notes = notes,
                                    mediaUri = mediaUri
                                )

                                viewModel.update(updated)

                                navController.navigate(SolidAuthFlowScreen.WorkoutList.name) {
                                    launchSingleTop = true
                                    popUpTo(SolidAuthFlowScreen.WorkoutList.name) { inclusive = false }
                                }

                                saveWorkoutLog(context)

                            },
                            onCancel = {
                                navController.navigate(SolidAuthFlowScreen.WorkoutList.name)
                            }
                        )
                    }
                }
            }

            // SCREEN: Add workout
            composable(route = SolidAuthFlowScreen.AddEditWorkoutScreen.name) {
                val addWorkoutCoroutineScope = rememberCoroutineScope()
                AddEditWorkoutScreen(
                    viewModel = viewModel,
                    onSaveWorkout = { _, name, quantity, duration, workoutType, datePerformed, notes, mediaUri ->
                        addWorkoutCoroutineScope.launch {
                            viewModel.insert(
                                WorkoutItem(
                                    id = "",
                                    name = name,
                                    dateModified = System.currentTimeMillis(),
                                    quantity = quantity,
                                    duration = duration,
                                    heartRate = 0,
                                    workoutType = workoutType,
                                    datePerformed = datePerformed,
                                    notes = notes,
                                    mediaUri = mediaUri
                                )
                            )
                            saveWorkoutLog(context)
                            navController.navigate(SolidAuthFlowScreen.WorkoutList.name)
                        }
                    },
                    onCancel = {
                        navController.navigate(SolidAuthFlowScreen.WorkoutList.name)
                    }
                )
            }

            // SCREEN: Weight Monitor
            composable(route = SolidAuthFlowScreen.WeightMonitor.name) {
                val weightViewModel: InputReadingsViewModel = viewModel(
                    factory = InputReadingsViewModelFactory(
                        healthConnectManager = healthConnectManager,
                        appContext = context
                    )
                )
                val permissionsGranted by weightViewModel.permissionsGranted
                val readingsList by weightViewModel.weightReadingsList
                val permissions = weightViewModel.permissions
                val weeklyAvg by weightViewModel.weightWeeklyAvg
                val onPermissionsResult = { weightViewModel.initialLoad() }
                val permissionsLauncher =
                    rememberLauncherForActivityResult(weightViewModel.permissionsLauncher) {
                        onPermissionsResult()
                    }

                // Trigger `initialLoad` if the UI state is Uninitialized
                LaunchedEffect(weightViewModel.uiState) {
                    if (weightViewModel.uiState is InputReadingsViewModel.UiState.Uninitialized) {
                        weightViewModel.initialLoad()
                    }
                }

                WeightMonitor(
                    healthConnectManager = healthConnectManager,
                    permissionsGranted = permissionsGranted,
                    permissions = permissions,

                    uiState = weightViewModel.uiState,
                    onInsertClick = { weightInput ->
                        weightViewModel.inputReadings(weightInput)
                    },
                    weeklyAvg = weeklyAvg,
                    readingsList = readingsList,
                    onError = { exception ->
                        showExceptionSnackbar(snackbarHostState, coroutineScope, exception)
                    },
                    onPermissionsResult = {
                        weightViewModel.initialLoad()
                    },
                    onPermissionsLaunch = { values ->
                        permissionsLauncher.launch(values)
                    }
                )
            }

            // SCREEN: Heart Rate Monitor
            composable(route = SolidAuthFlowScreen.HeartRateMonitor.name) {
                val heartViewModel: InputReadingsViewModel = viewModel(
                    factory = InputReadingsViewModelFactory(
                        healthConnectManager = healthConnectManager,
                        appContext = context
                    )
                )
                val permissionsGranted by heartViewModel.permissionsGranted
                val permissions = heartViewModel.permissions
                val onPermissionsResult = { heartViewModel.initialLoad() }
                val permissionsLauncher =
                    rememberLauncherForActivityResult(heartViewModel.permissionsLauncher) {
                        onPermissionsResult()
                    }

                // Trigger `initialLoad` if the UI state is Uninitialized
                LaunchedEffect(heartViewModel.uiState) {
                    if (heartViewModel.uiState is InputReadingsViewModel.UiState.Uninitialized) {
                        heartViewModel.initialLoad()
                    }
                }
                HeartRateMonitor(
//                    permissionsGranted = permissionsGranted,
                    healthConnectManager = healthConnectManager,
                    permissions = permissions,

                    uiState = heartViewModel.uiState,
                    onInsertClick = { bpm ->
                        heartViewModel.inputHeartRate(bpm)
                    },
                    onError = { exception ->
                        showExceptionSnackbar(snackbarHostState, coroutineScope, exception)
                    },
                    onPermissionsResult = {
                        heartViewModel.initialLoad()
                    },
                    onPermissionsLaunch = { values ->
                        permissionsLauncher.launch(values)
                    },
                )
            }

            composable(route = SolidAuthFlowScreen.SettingsScreen.name) {
                SettingsScreen(navController = authNavController)
            }
        }
    }
}

// Saves current date - used to see if user has already logged workout for the day -> wont notify again
fun saveWorkoutLog(context: Context) {
    val sharedPreferences = context.getSharedPreferences("workoutPrefs", Context.MODE_PRIVATE)
    val editor = sharedPreferences.edit()

    val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    editor.putString("lastWorkoutDate", todayDate)
    editor.apply()
}