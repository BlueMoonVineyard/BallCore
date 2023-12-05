package BallCore.PrimeTime

import java.util.TimeZone
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.util.Base64
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import io.circe.Encoder
import io.circe.Decoder
import scala.util.Try

object TimeZoneCodec:
    private def serialize(tz: TimeZone): String =
        val it = ByteArrayOutputStream()
        val oo = ObjectOutputStream(it)
        oo.writeObject(tz)
        oo.flush()
        it.flush()
        Base64.getEncoder().encodeToString(it.toByteArray())

    private def deserialize(s: String): Try[TimeZone] =
        Try {
            val bytes = Base64.getDecoder().decode(s)
            val it = ByteArrayInputStream(bytes)
            val oi = ObjectInputStream(it)
            oi.readObject().asInstanceOf[TimeZone]
        }

    given encoder: Encoder[TimeZone] =
        summon[Encoder[String]].contramap(serialize)
    given decoder: Decoder[TimeZone] =
        summon[Decoder[String]].emapTry(deserialize)
