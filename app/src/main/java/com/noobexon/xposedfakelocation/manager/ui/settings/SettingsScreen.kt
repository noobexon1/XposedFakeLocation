//SettingsScreen.kt
package com.noobexon.xposedfakelocation.manager.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel = viewModel ()
) {
    val useAccuracy by settingsViewModel.useAccuracy.collectAsState()
    val accuracy by settingsViewModel.accuracy.collectAsState()
    var accuracyInput by remember { mutableStateOf(accuracy.toString()) }

    val useAltitude by settingsViewModel.useAltitude.collectAsState()
    val altitude by settingsViewModel.altitude.collectAsState()
    var altitudeInput by remember { mutableStateOf(altitude.toString()) }

    val useRandomize by settingsViewModel.useRandomize.collectAsState()
    val randomizeRadius by settingsViewModel.randomizeRadius.collectAsState()
    var randomizeRadiusInput by remember { mutableStateOf(randomizeRadius.toString()) }

    val focusManager = LocalFocusManager.current

    LaunchedEffect(accuracy) {
        if (accuracy.toString() != accuracyInput) {
            accuracyInput = accuracy.toString()
        }
    }

    LaunchedEffect(altitude) {
        if (altitude.toString() != altitudeInput) {
            altitudeInput = altitude.toString()
        }
    }

    LaunchedEffect(randomizeRadius) {
        if (randomizeRadius.toString() != randomizeRadiusInput) {
            randomizeRadiusInput = randomizeRadius.toString()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    focusManager.clearFocus()
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Use Custom Accuracy")
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = useAccuracy,
                        onCheckedChange = { settingsViewModel.setUseAccuracy(it) }
                    )
                }

                if (useAccuracy) {
                    OutlinedTextField(
                        value = accuracyInput,
                        onValueChange = {
                            accuracyInput = it
                            val value = it.toDoubleOrNull()
                            if (value != null) {
                                settingsViewModel.setAccuracy(value)
                            }
                        },
                        label = { Text("Accuracy (meters)") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                            }
                        ),
                        isError = accuracyInput.toDoubleOrNull() == null,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Use Custom Altitude")
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = useAltitude,
                        onCheckedChange = { settingsViewModel.setUseAltitude(it) }
                    )
                }

                if (useAltitude) {
                    OutlinedTextField(
                        value = altitudeInput,
                        onValueChange = {
                            altitudeInput = it
                            val value = it.toDoubleOrNull()
                            if (value != null) {
                                settingsViewModel.setAltitude(value)
                            }
                        },
                        label = { Text("Altitude (meters)") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                            }
                        ),
                        isError = altitudeInput.toDoubleOrNull() == null,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Randomize Nearby Location")
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = useRandomize,
                        onCheckedChange = { settingsViewModel.setRandomize(it) }
                    )
                }

                if (useRandomize) {
                    OutlinedTextField(
                        value = randomizeRadiusInput,
                        onValueChange = {
                            randomizeRadiusInput = it
                            val value = it.toDoubleOrNull()
                            if (value != null) {
                                settingsViewModel.setRandomizeRadius(value)
                            }
                        },
                        label = { Text("Randomization Radius (meters)") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                            }
                        ),
                        isError = randomizeRadiusInput.toDoubleOrNull() == null,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                    )
                }
            }
        }
    }
}