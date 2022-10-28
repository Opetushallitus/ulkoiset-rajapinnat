package ulkoiset_rajapinnat.testutils

import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

object ApiJsonUtil {
    fun asApiJson(data: Any) =
        GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create().toJson(data)

    fun asMap(json: String): List<Map<String, Any>> {
        val t: Type = object : TypeToken<List<Map<String, Any>>>() {}.type
        return Gson().fromJson(json, t)
    }

}