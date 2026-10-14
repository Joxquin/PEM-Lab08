package com.example.lab08

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.room.Room
import com.example.lab08.ui.theme.Lab08Theme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = Room.databaseBuilder(
            applicationContext,
            TaskDatabase::class.java,
            "task_db",
        ).fallbackToDestructiveMigration(dropAllTables = true).build()

        val taskDao = db.taskDao()
        val viewModel = TaskViewModel(taskDao)

        setContent {
            Lab08Theme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFFF7F8FA),
                ) { innerPadding ->
                    TaskScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen(viewModel: TaskViewModel, modifier: Modifier = Modifier) {
    val tasks by viewModel.filteredTasks.collectAsState()
    val activeFilter by viewModel.filter.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()

    val context = LocalContext.current
    var newTaskDescription by remember { mutableStateOf("") }
    var taskToEdit by remember { mutableStateOf<Task?>(null) }

    // Manejo de permisos para Notificaciones en Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            viewModel.sendNotification(context)
        } else {
            Toast.makeText(context, "Permiso de notificaciones denegado", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(syncStatus) {
        syncStatus?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearSyncStatus()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        // Cabecera superior: Título, fecha y acciones rápidas
        HeaderSection(
            onSyncClick = { viewModel.syncWithCloud() },
            onNotificationClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        viewModel.sendNotification(context)
                    } else {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    viewModel.sendNotification(context)
                }
            },
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Pestañas de Filtro (Pills)
        FilterPillTabs(
            selectedFilter = activeFilter,
            onFilterSelected = { viewModel.setFilter(it) },
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Lista de Tareas
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(tasks, key = { it.id }) { task ->
                TaskCardItem(
                    task = task,
                    onToggleComplete = { viewModel.toggleTaskCompletion(task) },
                    onEditClick = { taskToEdit = task },
                    onDeleteClick = { viewModel.deleteTask(task) },
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Campo inferior para agregar nuevas tareas
        AddTaskBottomCard(
            textValue = newTaskDescription,
            onValueChange = { newTaskDescription = it },
            onAddClick = {
                if (newTaskDescription.isNotBlank()) {
                    viewModel.addTask(newTaskDescription)
                    newTaskDescription = ""
                }
            },
        )
    }

    // Diálogo modal para Editar Tarea
    taskToEdit?.let { task ->
        EditTaskDialog(
            currentDescription = task.description,
            onDismiss = { taskToEdit = null },
            onConfirm = { newDesc ->
                viewModel.editTask(task, newDesc)
                taskToEdit = null
            },
        )
    }
}

@Composable
fun HeaderSection(
    onSyncClick: () -> Unit,
    onNotificationClick: () -> Unit,
) {
    val currentDateFormatted = remember {
        val formatter = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)
        formatter.format(Date())
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "My Tasks",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E212A),
            )
            Text(
                text = currentDateFormatted,
                fontSize = 14.sp,
                color = Color(0xFF8C919E),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = onSyncClick,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFFEAEAEE)),
            ) {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = "Sincronizar Cloud",
                    tint = Color(0xFF3B4868),
                )
            }

            IconButton(
                onClick = onNotificationClick,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFFEAEAEE)),
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = "Recordatorio Notificación",
                    tint = Color(0xFF3B4868),
                )
            }
        }
    }
}

@Composable
fun FilterPillTabs(
    selectedFilter: TaskFilter,
    onFilterSelected: (TaskFilter) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFFEDEDF2),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            FilterTabPill(
                label = "All",
                isSelected = selectedFilter == TaskFilter.ALL,
                onClick = { onFilterSelected(TaskFilter.ALL) },
                modifier = Modifier.weight(1f),
            )
            FilterTabPill(
                label = "To Do",
                isSelected = selectedFilter == TaskFilter.PENDING,
                onClick = { onFilterSelected(TaskFilter.PENDING) },
                modifier = Modifier.weight(1f),
            )
            FilterTabPill(
                label = "Done",
                isSelected = selectedFilter == TaskFilter.COMPLETED,
                onClick = { onFilterSelected(TaskFilter.COMPLETED) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun FilterTabPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor by animateColorAsState(
        if (isSelected) Color.White else Color.Transparent,
        label = "tabBgColor",
    )
    val textColor by animateColorAsState(
        if (isSelected) Color(0xFF1E212A) else Color(0xFF7E8494),
        label = "tabTextColor",
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor,
        )
    }
}

@Composable
fun TaskCardItem(
    task: Task,
    onToggleComplete: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onToggleComplete,
                modifier = Modifier.size(28.dp),
            ) {
                if (task.isCompleted) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Completada",
                        tint = Color(0xFF4CAF50),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Circle,
                        contentDescription = "Pendiente",
                        tint = Color(0xFFB0B5C0),
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = task.description,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (task.isCompleted) Color(0xFF9EA3B0) else Color(0xFF1E212A),
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                modifier = Modifier.weight(1f),
            )

            IconButton(
                onClick = onEditClick,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Editar",
                    tint = Color(0xFF7E8494),
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Eliminar",
                    tint = Color(0xFFE53935),
                )
            }
        }
    }
}

@Composable
fun AddTaskBottomCard(
    textValue: String,
    onValueChange: (String) -> Unit,
    onAddClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFF0F1F5),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onAddClick,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Agregar Tarea",
                    tint = Color(0xFF5C6578),
                )
            }

            TextField(
                value = textValue,
                onValueChange = onValueChange,
                placeholder = { Text("Add a new task...", color = Color(0xFF9EA3B0)) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )

            if (textValue.isNotBlank()) {
                Button(
                    onClick = onAddClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B4868)),
                ) {
                    Text("Agregar", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun EditTaskDialog(
    currentDescription: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var textState by remember { mutableStateOf(currentDescription) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Tarea", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = textState,
                onValueChange = { textState = it },
                label = { Text("Descripción de la tarea") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (textState.isNotBlank()) {
                        onConfirm(textState)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B4868)),
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color.Gray)
            }
        },
    )
}
