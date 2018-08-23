
package com.github.mauricio.async.db.column

import org.joda.time.format.DateTimeFormat

object TimeWithTimezoneEncoderDecoder : TimeEncoderDecoder() {

  private val format = DateTimeFormat.forPattern("HH:mm:ss.SSSSSSZ")

  override fun formatter ()= format

}
