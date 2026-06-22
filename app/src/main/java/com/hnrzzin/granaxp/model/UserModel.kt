package com.hnrzzin.granaxp.model

import com.google.firebase.firestore.DocumentId


data class UserModel(
    @DocumentId
    var id: String = "",
    var name: String = "",
    var email: String = "",
    var level: Int = 1,
    var xp: Int = 0,
    var nextLevelXp: Int = 100
)