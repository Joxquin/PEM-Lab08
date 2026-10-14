package com.example.lab08

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class CloudSyncManager(private val taskDao: TaskDao) {

    // Identificador compartido de espacio de trabajo Cloud para sincronización entre múltiples dispositivos
    private val cloudWorkspaceKey = "lab08_shared_tasks"

    // Simulación / API Cloud Endpoint para almacenamiento y sincronización en la nube
    suspend fun syncWithCloud(): SyncResult = withContext(Dispatchers.IO) {
        try {
            // 1. Obtener tareas locales actuales
            val localTasks = taskDao.getAllTasks()

            // 2. Simular/Ejecutar conexión a servidor Cloud (HTTP/REST payload)
            val jsonPayload = JSONArray()
            localTasks.forEach { task ->
                val obj = JSONObject().apply {
                    put("id", task.id)
                    put("description", task.description)
                    put("isCompleted", task.isCompleted)
                    put("updatedAt", task.updatedAt)
                }
                jsonPayload.put(obj)
            }

            // Simulación de respuesta Cloud en tiempo real
            delay(1200)

            // 3. Resolver de forma atómica y actualizar la base de datos local
            taskDao.insertAll(localTasks)

            SyncResult.Success(count = localTasks.size)
        } catch (e: Exception) {
            SyncResult.Error(message = e.localizedMessage ?: "Error al conectar con la nube")
        }
    }
}

sealed class SyncResult {
    data class Success(val count: Int) : SyncResult()
    data class Error(val message: String) : SyncResult()
}
