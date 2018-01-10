package com.redbubble.hawk.validate

import com.redbubble.hawk.params.{Nonce, PayloadContext, RequestContext, ValidatableRequestContext}
import com.redbubble.hawk.util.Time
import com.redbubble.hawk.validate.NormalisedRequest._
import com.redbubble.hawk.{HawkError, _}
import mouse.all._

object Maccer {
  /**
    * Computes the MAC of an outgoing (to a downstream server) request.
    */
  def computeRequestMac(credentials: Credentials,
      time: Time, nonce: Nonce, context: RequestContext, extendedData: Option[ExtendedData]): MAC = {
    val payloadMac: Option[MAC] = context.payload.map(p => normalisedPayloadMac(credentials, p))
    normalisedHeaderMac(credentials, time, nonce, context, extendedData, payloadMac)
  }

  /**
    * Validates an incoming request (i.e. has a Hawk authorisation header). If a payload is present, e.g. in a POST
    * request, `hawk.1.payload` validation  will be used, otherwise, `hawk.1.header` validation will be used.
    */
  def validateAndComputeRequestMac(credentials: Credentials, context: ValidatableRequestContext): Either[HawkError, MAC] =
    context.context.payload.fold(validateHeader(credentials, context))(_ => validatePayload(credentials, context))

  private def validateHeader(credentials: Credentials, context: ValidatableRequestContext): Either[HawkError, MAC] =
    Right(normalisedHeaderMac(credentials, context, None))

  private def validatePayload(credentials: Credentials, context: ValidatableRequestContext): Either[HawkError, MAC] = {
    context.context.payload.map { payload =>
      context.clientAuthHeader.payloadHash.flatMap { clientProvidedHash =>
        val macFromClientProvidedHash = normalisedHeaderMac(credentials, context, Some(MAC(clientProvidedHash.encoded)))
        (!MAC.isEqual(macFromClientProvidedHash, context.clientAuthHeader.mac)).option(errorE("MAC provided in request does not match the computed MAC (payload hash may be invalid)"))
      }.getOrElse(Right(computePayloadMac(credentials, context, payload)))
    }.getOrElse(errorE("No payload provided for payload validation"))
  }

  private def computePayloadMac(
      credentials: Credentials, context: ValidatableRequestContext, payloadContext: PayloadContext): MAC = {
    val computedPayloadMac = normalisedPayloadMac(credentials, payloadContext)
    normalisedHeaderMac(credentials, context, Some(computedPayloadMac))
  }
}
