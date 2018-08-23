package com.github.mauricio.async.db.util

import com.github.mauricio.async.db.Configuration
import com.github.mauricio.async.db.SSLConfiguration
import com.github.mauricio.async.db.exceptions.UnableToParseURLException
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.nio.charset.Charset

/**
 * Common parser assisting methods for PG and MySQL URI parsers.
 */
abstract class AbstractURIParser {

  protected val logger = LoggerFactory.getLogger(AbstractURIParser::class.java)

  /**
   * Parses out userInfo into a tuple of optional username and password
   *
   * @param userInfo the optional user info string
   * @return a tuple of optional username and password
   */
  protected fun parseUserInfo(userInfo: String?): Pair<String?, String?> {
    val split = userInfo.nullableMap { it.split(":") }
    return when {
      split != null && split.size >= 2 -> split[0] to split[1]
      split != null && split.size == 1 -> split[0] to null
      else -> null  to null
    }
  }

  /**
   * A Regex that will when the base name of the driver scheme, minus jdbc:.
   * Eg: postgres(?:ul)?
   */
  abstract protected val SCHEME: Regex

  /**
   * The funault for this particular URLParser, ie: appropriate and specific to PG or MySQL accordingly
   */
  abstract val DEFAULT: Configuration


  /**
   * Parses the provided url and returns a Configuration based upon it.  On an error,
   * @param url the URL to parse.
   * @param charset the charset to use.
   * @return a Configuration.
   */
  //@throws<UnableToParseURLException>("if the URL does not when the expected type, or cannot be parsed for any reason")
  fun parseOrDie(url: String,
                 charset: Charset = DEFAULT.charset): Configuration {
    return try {
      val properties = parse(URI(url).parseServerAuthority())

      assembleConfiguration(properties, charset)
    } catch (e: URISyntaxException) {
      throw UnableToParseURLException("Failed to parse URL: $url", e)
    }
  }


  /**
   * Parses the provided url and returns a Configuration based upon it.  On an error,
   * a funault configuration is returned.
   * @param url the URL to parse.
   * @param charset the charset to use.
   * @return a Configuration.
   */
  fun parse(url: String,
            charset: Charset = DEFAULT.charset
  ): Configuration {
    return try {
      parseOrDie(url, charset)
    } catch (e: Exception) {
      logger.warn("Connection url '$url' could not be parsed.", e)
      // Fallback to funault to maintain current behavior
      DEFAULT
    }
  }

  /**
   * Assembles a configuration out of the provided property map.  This is the generic form, subclasses may override to
   * handle additional properties.
   * @param properties the extracted properties from the URL.
   * @param charset the charset passed in to parse or parseOrDie.
   * @return
   */
  protected fun assembleConfiguration(properties: Map<String, String>, charset: Charset): Configuration {
    return DEFAULT.copy(
        username = properties.getOrElse(USERNAME, { DEFAULT.username }),
        password = properties.get(PASSWORD),
        database = properties.get(DBNAME),
        host = properties.getOrElse(HOST, { DEFAULT.host }),
        port = properties.get(PORT)?.let { it.toInt() } ?: DEFAULT.port,
        ssl = SSLConfiguration(properties),
        charset = charset
    )
  }


  protected fun parse(uri: URI): Map<String, String> {
    return when {
      uri.scheme.matches(SCHEME) -> {
        val userInfo = parseUserInfo(uri.userInfo)

        val port = uri.port.nullableFilter { it > 0 }
        val db = uri.path.nullableMap { it.removeSuffix("/") }.nullableFilter { !it.isEmpty() }
        val host = uri.host

        val map = mutableMapOf<String, String>()
        userInfo.first?.nullableMap { USERNAME to it }?.apply { map += this }
        userInfo.second?.nullableMap { PASSWORD to it }?.apply { map += this }
        port?.nullableMap { PORT to it.toString() }?.apply { map += this }
        db?.nullableMap { DBNAME to it }?.apply { map += this }
        host?.nullableMap { HOST to unwrapIpv6address(it) }?.apply { map += this }

        // Parse query string parameters and just append them, overriding anything previously set
        uri.query.split("&").forEach { keyValue ->
          val split = keyValue.split("=")
          if (split.size == 2 && split[0].isNotBlank() && split[1].isNotBlank()) {
            map += URLDecoder.decode(split[0], "UTF-8") to URLDecoder.decode(split[1], "UTF-8")
          }
        }
        map
      }
      uri.scheme == "jdbc" ->
        handleJDBC(uri)
      else ->
        throw UnableToParseURLException("Unrecognized URI scheme")
    }
  }

  /**
   * This method breaks out handling of the jdbc: prefixed uri's, allowing them to be handled differently
   * ,out reimplementing all of parse.
   */
  protected fun handleJDBC(uri: URI): Map<String, String> = parse(URI(uri.getSchemeSpecificPart()))


  protected fun unwrapIpv6address(server: String): String {
    return if (server.startsWith("<")) {
      server.substring(1, server.length - 1)
    } else server
  }

  companion object {
    // Constants and value names
    val PORT = "port"
    val DBNAME = "database"
    val HOST = "host"
    val USERNAME = "user"
    val PASSWORD = "password"
  }
}

