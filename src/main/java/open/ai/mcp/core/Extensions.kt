package open.ai.mcp.core

import io.modelcontextprotocol.kotlin.sdk.ProgressNotification
import io.modelcontextprotocol.kotlin.sdk.PromptMessageContent
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.isSubclassOf

val EmptyJsonObject = buildJsonObject {  }

fun JsonObject.toMap(): Map<String, Any?> = this.mapValues { (_, v) -> v.toJsonValue() }

fun JsonArray.toList(): List<Any?> = this.map { it.toJsonValue() }

fun JsonElement.toJsonValue(): Any? = when (this) {
    is JsonPrimitive -> {
        if (isString) content
        else booleanOrNull ?: intOrNull ?: longOrNull ?: doubleOrNull ?: content
    }
    is JsonObject -> toMap()
    is JsonArray -> toList()
}

fun KParameter.isHiddenParameter() = this.type.classifier == Notifier::class
fun Any.createMultimodal():PromptMessageContent{
    return this as? PromptMessageContent ?: TextContent(this.toString())
}

fun  KType.mcpType():String{
    val classifier = this.classifier
    if (classifier is KClass<*>) {
        return when {
            classifier.isSubclassOf(String::class) -> "string"
            classifier.isSubclassOf(Boolean::class) -> "boolean"
            classifier.isSubclassOf(Int::class) -> "number"
            classifier.isSubclassOf(Long::class) -> "number"
            classifier.isSubclassOf(Float::class) -> "number"
            classifier.isSubclassOf(Double::class) -> "number"
            classifier.isSubclassOf(List::class) || classifier.isSubclassOf(Array::class) -> "array"
            else -> "object"
        }
    }
    return "object"
}



