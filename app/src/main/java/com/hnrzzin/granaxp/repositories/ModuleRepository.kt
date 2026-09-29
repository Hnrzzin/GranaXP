package com.hnrzzin.granaxp.repositories

import com.google.firebase.firestore.FirebaseFirestore
import com.hnrzzin.granaxp.model.ModuleModel
import kotlinx.coroutines.tasks.await

class ModuleRepository {
    private val modulesCollection = FirebaseFirestore.getInstance().collection("modules")

    suspend fun getModules(): List<ModuleModel> {
        return modulesCollection.orderBy("order").get().await()
            .map { document ->
                document.toObject(ModuleModel::class.java).copy(idModule = document.id)
            }
            .sortedWith(compareBy<ModuleModel> { it.order }.thenBy { it.idModule })
    }
}
