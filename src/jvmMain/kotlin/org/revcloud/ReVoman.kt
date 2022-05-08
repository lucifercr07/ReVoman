@file:JvmName("ReVoman")

package org.revcloud

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.adapter
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import com.squareup.moshi.internal.Util
import dev.zacsweers.moshix.adapters.AdaptedBy
import dev.zacsweers.moshix.adapters.JsonString
import org.apache.http.client.config.CookieSpecs
import org.apache.http.client.config.RequestConfig
import org.apache.http.conn.ssl.NoopHostnameVerifier
import org.apache.http.impl.client.HttpClients
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.Source
import org.http4k.client.Apache4Client
import org.http4k.core.ContentType.Companion.APPLICATION_JSON
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.then
import org.http4k.core.with
import org.http4k.filter.ClientFilters
import org.http4k.filter.DebuggingFilters
import org.http4k.format.ConfigurableMoshi
import org.http4k.format.EventAdapter
import org.http4k.format.ListAdapter
import org.http4k.format.MapAdapter
import org.http4k.format.ThrowableAdapter
import org.http4k.format.asConfigurable
import org.http4k.format.withStandardMappings
import org.http4k.lens.Header.CONTENT_TYPE
import org.revcloud.DynamicEnvironmentKeys.BEARER_TOKEN
import org.revcloud.output.Pokemon
import org.revcloud.postman.PostmanAPI
import org.revcloud.postman.state.collection.Collection
import org.revcloud.postman.state.collection.Item
import org.revcloud.postman.state.environment.Environment
import java.io.File
import java.lang.reflect.Type
import java.util.Date

private val postManVariableRegex = "\\{\\{([^{}]*?)}}".toRegex()

@OptIn(ExperimentalStdlibApi::class)
@JvmOverloads
fun revUp(
  pmCollectionPath: String,
  pmEnvironmentPath: String,
  itemNameToOutputType: Map<String, Class<out Any>>? = emptyMap(),
  dynamicEnvironment: Map<String, String?> = emptyMap(),
  typesInResponseToIgnore: Set<Class<out Any>> = emptySet(),
  customAdaptersForResponse: List<Any> = emptyList(),
): Pokemon {
  // Load environment
  val envJsonAdapter = Moshi.Builder().build().adapter<Environment>()
  val environment: Environment? = envJsonAdapter.fromJson(readTextFromFile(pmEnvironmentPath))
  val pm = PostmanAPI()
  pm.environment.putAll(environment?.values?.filter { it.enabled }?.associate { it.key to it.value } ?: emptyMap())
  pm.environment.putAll(dynamicEnvironment)

  // Marshall postman collection
  val collectionJsonAdapter = Moshi.Builder()
    .add(AdaptedBy.Factory()).build()
    .adapter<Collection>()
  val pmCollection: Collection? = collectionJsonAdapter.fromJson(readTextFromFile(pmCollectionPath))

  // Post request
  val itemJsonAdapter = Moshi.Builder().add(RegexAdapterFactory(pm.environment)).build().adapter<Item>()
  val configurableMoshi = configurableMoshi(typesInResponseToIgnore, customAdaptersForResponse)
  val itemNameToResponseWithType = pmCollection?.item?.asSequence()?.map { itemData ->
    val item = itemJsonAdapter.fromJson(itemData.data)
    val itemRequest: org.revcloud.postman.state.collection.Request =
      item?.request ?: org.revcloud.postman.state.collection.Request()
    val httpClient = DebuggingFilters.PrintRequestAndResponse()
      .then(ClientFilters.BearerAuth(dynamicEnvironment[BEARER_TOKEN] ?: ""))
      .then(
        Apache4Client(
          client = HttpClients.custom()
            .setDefaultRequestConfig(
              RequestConfig.custom()
                .setRedirectsEnabled(false)
                .setCookieSpec(CookieSpecs.IGNORE_COOKIES)
                .build()
            ).setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
            .build()
        )
      )
    val httpRequest = Request(Method.valueOf(itemRequest.method), itemRequest.url.raw)
      .with(CONTENT_TYPE of APPLICATION_JSON)
      .body(itemRequest.body?.raw ?: "")
    val response: Response = httpClient(httpRequest)

    // Init pm environment
    val pmResponse = org.revcloud.postman.state.collection.Response(
      response.status.toString(),
      response.status.code.toString(),
      response.bodyString()
    )
    pm.request = itemRequest
    pm.response = pmResponse

    // Test script
    val itemName = item?.name ?: ""
    val testScript = item?.event?.find { it.listen == "test" }?.script?.exec?.joinToString("\n")
    if (!testScript.isNullOrBlank()) { // ! TODO gopala.akshintala 04/05/22: Catch and handle exceptions
      val testSource = Source.newBuilder("js", testScript, "pmItemTestScript.js").build()
      // ! TODO gopala.akshintala 03/05/22: support requirejs
      val context = buildJsContext(useCommonjsRequire = false)
      context.getBindings("js").putMember("pm", pm)
      context.getBindings("js").putMember("responseBody", response.bodyString())
      try {
        context.eval(testSource)
      } catch (polyglotException: PolyglotException) {
        println(itemName)
        throw polyglotException
      }
    }

    // Marshall res
    val clazz = itemNameToOutputType?.get(itemName)?.kotlin ?: Any::class
    itemName to (configurableMoshi.asA(response.bodyString(), clazz) to clazz.java)
  }?.toMap() ?: emptyMap()
  return Pokemon(itemNameToResponseWithType, pm.environment)
}

fun buildJsContext(useCommonjsRequire: Boolean = true): Context {
  val options = buildMap {
    if (useCommonjsRequire) {
      put("js.commonjs-require", "true")
      put("js.commonjs-require-cwd", ".")
      put("js.commonjs-core-modules-replacements", "buffer:buffer/, path:path-browserify")
    }
    put("js.esm-eval-returns-exports", "true")
    put("engine.WarnInterpreterOnly", "false")
  }
  return Context.newBuilder("js")
    .allowExperimentalOptions(true)
    .allowIO(true)
    .options(options)
    .allowHostAccess(HostAccess.ALL)
    .allowHostClassLookup { true }
    .build()
}

private fun readTextFromFile(filePath: String): String = File(filePath).readText()

private class RegexAdapterFactory(val envMap: Map<String, String?>) : JsonAdapter.Factory {
  override fun create(type: Type, annotations: Set<Annotation?>, moshi: Moshi): JsonAdapter<*>? {
    if (type != String::class.java) {
      return null
    }
    val stringAdapter = moshi.nextAdapter<String>(this, String::class.java, Util.NO_ANNOTATIONS)
    return object : JsonAdapter<String>() {
      override fun fromJson(reader: JsonReader): String? {
        val s = stringAdapter.fromJson(reader)
        return s?.let {
          postManVariableRegex.replace(s) { matchResult ->
            val variableKey = matchResult.groupValues[1]
            dynamicVariables(variableKey) ?: envMap[variableKey] ?: ""
          }
        }
      }

      override fun toJson(writer: JsonWriter, value: String?) {
        stringAdapter.toJson(writer, value)
      }
    }
  }
}

fun configurableMoshi(
  typesToIgnore: Set<Class<out Any>> = emptySet(),
  customAdaptersForResponse: List<Any> = emptyList()
): ConfigurableMoshi {
  val moshi = Moshi.Builder()
  customAdaptersForResponse.forEach { moshi.add(it) }
  return object : ConfigurableMoshi(
    moshi
      .add(JsonString.Factory())
      .add(AdaptedBy.Factory())
      .add(Date::class.java, Rfc3339DateJsonAdapter())
      .add(IgnoreUnknownFactory(typesToIgnore))
      .addLast(EventAdapter)
      .addLast(ThrowableAdapter)
      .addLast(ListAdapter)
      .addLast(MapAdapter)
      .asConfigurable()
      .withStandardMappings()
      .done()
  ) {}
}


internal class IgnoreUnknownFactory(private val typesToIgnore: Set<Class<out Any>>) : JsonAdapter.Factory {
  override fun create(
    type: Type, annotations: Set<Annotation?>, moshi: Moshi
  ): JsonAdapter<*> {
    val rawType = Types.getRawType(type)
    return if (typesToIgnore.contains(rawType)) {
      object : JsonAdapter<Type>() {
        override fun fromJson(reader: JsonReader): Type? {
          return null
        }

        override fun toJson(writer: JsonWriter, value: Type?) {
          // do nothing
        }
      }
    } else moshi.nextAdapter<Any>(this, type, annotations)
  }
}
