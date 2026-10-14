package com.example.lab08

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TaskFilter {
    ALL, PENDING, COMPLETED
}

class TaskViewModel(private val dao: TaskDao) : ViewModel() {

    private val cloudSyncManager = CloudSyncManager(dao)

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks

    private val _filter = MutableStateFlow(TaskFilter.ALL)
    val filter: StateFlow<TaskFilter> = _filter

    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus

    val filteredTasks: StateFlow<List<Task>> = combine(_tasks, _filter) { taskList, currentFilter ->
        when (currentFilter) {
            TaskFilter.ALL -> taskList
            TaskFilter.PENDING -> taskList.filter { !it.isCompleted }
            TaskFilter.COMPLETED -> taskList.filter { it.isCompleted }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    init {
        loadTasks()
    }

    private fun loadTasks() {
        viewModelScope.launch {
            _tasks.value = dao.getAllTasks()
        }
    }

    fun setFilter(filter: TaskFilter) {
        _filter.value = filter
    }

    fun addTask(description: String) {
        if (description.isBlank()) return
        val newTask = Task(description = description.trim())
        viewModelScope.launch {
            dao.insertTask(newTask)
            _tasks.value = dao.getAllTasks()
        }
    }

    fun editTask(task: Task, newDescription: String) {
        if (newDescription.isBlank()) return
        viewModelScope.launch {
            val updatedTask = task.copy(
                description = newDescription.trim(),
                updatedAt = System.currentTimeMillis(),
            )
            dao.updateTask(updatedTask)
            _tasks.value = dao.getAllTasks()
        }
    }

    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            val updatedTask = task.copy(
                isCompleted = !task.isCompleted,
                updatedAt = System.currentTimeMillis(),
            )
            dao.updateTask(updatedTask)
            _tasks.value = dao.getAllTasks()
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            dao.deleteTask(task)
            _tasks.value = dao.getAllTasks()
        }
    }

    fun deleteAllTasks() {
        viewModelScope.launch {
            dao.deleteAllTasks()
            _tasks.value = emptyList()
        }
    }

    fun syncWithCloud() {
        viewModelScope.launch {
            _syncStatus.value = "Sincronizando con la nube..."
            when (val result = cloudSyncManager.syncWithCloud()) {
                is SyncResult.Success -> {
                    _tasks.value = dao.getAllTasks()
                    _syncStatus.value = "¡Sincronización en la nube exitosa! (${result.count} tareas)"
                }
                is SyncResult.Error -> {
                    _syncStatus.value = "Error: ${result.message}"
                }
            }
        }
    }

    fun sendNotification(context: Context) {
        val pendingCount = _tasks.value.count { !it.isCompleted }
        TaskReminderWorker.showTaskReminderNotification(context, pendingCount)

        val workRequest = OneTimeWorkRequestBuilder<TaskReminderWorker>().build()
        WorkManager.getInstance(context).enqueue(workRequest)
        _syncStatus.value = "Recordatorio de notificación programado"
    }

    fun clearSyncStatus() {
        _syncStatus.value = null
    }
}
