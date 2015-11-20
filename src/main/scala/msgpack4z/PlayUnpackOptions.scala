package msgpack4z

import msgpack4z.PlayUnpackOptions.NonStringKeyHandler
import play.api.libs.json._
import scalaz.{\/-, -\/}

final case class PlayUnpackOptions(
  extension: Unpacker[JsValue],
  binary: Unpacker[JsValue],
  positiveInf: UnpackResult[JsValue],
  negativeInf: UnpackResult[JsValue],
  nan: UnpackResult[JsValue],
  nonStringKey: NonStringKeyHandler
)

object PlayUnpackOptions {
  private[this] def bytes2NumberArray(bytes: Array[Byte]): JsValue =
    JsArray(bytes.map(JsNumber(_)))

  val binaryToNumberArray: Binary => JsValue = { bytes =>
    bytes2NumberArray(bytes.value)
  }

  val binaryToNumberArrayUnpacker: Unpacker[JsValue] = { unpacker =>
    CodecInstances.binary.binaryCodec.unpack(unpacker).map(binaryToNumberArray)
  }

  type NonStringKeyHandler = (MsgType, MsgUnpacker) => Option[String]

  val extUnpacker: Unpacker[JsValue] = { unpacker =>
    val header = unpacker.unpackExtTypeHeader
    val data = unpacker.readPayload(header.getLength)
    val result = Json.obj(
      ("type", JsNumber(header.getType)),
      ("data", bytes2NumberArray(data))
    )
    \/-(result)
  }

  val default: PlayUnpackOptions = PlayUnpackOptions(
    extUnpacker,
    binaryToNumberArrayUnpacker,
    \/-(JsNull),
    \/-(JsNull),
    \/-(JsNull),
    {case (tpe, unpacker) =>
      PartialFunction.condOpt(tpe){
        case MsgType.NIL =>
          "null"
        case MsgType.BOOLEAN =>
          unpacker.unpackBoolean().toString
        case MsgType.INTEGER =>
          unpacker.unpackBigInteger().toString
        case MsgType.FLOAT =>
          unpacker.unpackDouble().toString
        case MsgType.STRING =>
          unpacker.unpackString()
      }
    }
  )

}
