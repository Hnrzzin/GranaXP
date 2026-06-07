package com.hnrzzin.granaxp.model

data class UserModel(

    val id: String,
    val name: String? = null,
    val userLevel: Int = 1,
    val userXP: Int = 0,

    )
