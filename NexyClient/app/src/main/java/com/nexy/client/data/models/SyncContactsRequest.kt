package com.nexy.client.data.models

import com.google.gson.annotations.SerializedName

data class SyncContactsRequest(
    @SerializedName("phone_numbers")
    val phoneNumbers: List<String>
)
