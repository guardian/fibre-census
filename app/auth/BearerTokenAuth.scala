package auth

import java.time.Instant
import java.util.Date

import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.{JWK, JWKSet}
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}
import javax.inject.{Inject, Singleton}
import org.slf4j.LoggerFactory
import play.api.mvc.RequestHeader
import play.api.Configuration
import play.api.libs.typedmap.TypedKey
import scala.collection.JavaConverters._
import java.net.URL
import scala.io.Source
import scala.util.{Failure, Success, Try}

object BearerTokenAuth {
  final val ClaimsAttributeKey = TypedKey[JWTClaimsSet]("claims")
}

object ClaimsSetExtensions {
  private val logger = LoggerFactory.getLogger(getClass)
  implicit class ExtendedClaimsSet(val s:JWTClaimsSet) extends AnyVal {
    /**
     * A handy extension method for JWTClaimsSet that will get the "azp" field as an Optional string, since there isn't a method
     * for that already
     * @return the string content of the "azp" field or Non
     */
    def getAzp:Option[String] = Option(s.getClaim("azp")).map(_.asInstanceOf[String])

    def getIsMMAdmin:Boolean = Option(s.getClaim("multimedia_admin").asInstanceOf[String]).exists(value => value.toLowerCase == "true")

    def getIsMMAdminFromRole: Boolean = {
      (Option(s.getStringArrayClaim("roles")), Option(s.getStringClaim("multimedia_admin"))) match {
        case (Some(roles), _) =>
          logger.debug(s"Administrative rights check via roles claim")
          if (roles.contains("multimedia_admin")) {
            true
          } else {
            false
          }
        case (_, Some(_)) =>
          true
        case (_, None) =>
          false
      }
    }

    def getIsMMCreator:Boolean = Option(s.getClaim("multimedia_creator").asInstanceOf[String]).exists(value => value.toLowerCase == "true")
  }
}

/**
 * this class implements bearer token authentication. It's injectable because it needs to access app config.
 * You don't need to integrate it directly in your controller, it is required by the Security trait.
 *
 * A given bearer token must authenticate against the provided certificate to be allowed access, its expiry time
 * must not be in the past and it must have at least one of the `validAudiences` in either the `aud` or `azp` fields.
 * The token's subject field ("sub") is used as the username.
 * Admin access is only granted if the token's field given by auth.adminClaim is a string that equates to "true" or "yes".
 *
 * So, in order to use it:
 *
 * class MyController @Inject() (controllerComponents:ControllerComponents, override bearerTokenAuth:BearerTokenAuth) extends AbstractController(controllerComponets) with Security { }
 * @param config application configuration object. This is normally provided by the injector
 */
@Singleton
class BearerTokenAuth @Inject() (config:Configuration) {
  import ClaimsSetExtensions._
  private val logger = LoggerFactory.getLogger(getClass)

  //see https://stackoverflow.com/questions/475074/regex-to-parse-or-validate-base64-data
  //it is not the best option but is the simplest that will work here
  private val authXtractor = "^Bearer\\s+([a-zA-Z0-9+/._-]*={0,3})$".r
  private val maybeVerifiers = loadInKey() match {
    case Failure(err)=>
      if(!sys.env.contains("CI")) logger.warn(s"No token validation cert in config so bearer token auth will not work. Error was ${err.getMessage}")
      None
    case Success(jwk)=>
      Some(jwk)
  }

  protected def getVerifier(jwk:JWK) = new RSASSAVerifier(jwk.toRSAKey)

  /**
   * returns the configured name for the claim field that will give whether a user is an admin or not.
   * It's included here because the Security trait is a mixin and can't access the config directly.
   * @return
   */
  def isAdminClaimName():String = {
    config.get[String]("auth.adminClaim")
  }

  /**
   * extracts the authorization token from the provided header
   * @param fromString complete Authorization header text
   * @return None if the header text does not match the expected format. The raw bearer token if it does.
   */
  def extractAuthorization(fromString:String):Either[LoginResult,LoginResultOK[String]] =
    fromString match {
      case authXtractor(token)=>
        logger.debug("found valid base64 bearer")
        Right(LoginResultOK(token))
      case _=>
        logger.warn("no bearer token found or it failed to validate")
        Left(LoginResultInvalid("No token presented, this is probably a frontend bug"))
    }

  private def signingCertPath = config.get[String]("auth.tokenSigningCertPath")

  protected def loadRemoteJWKSet(remotePath:String) = JWKSet.load(new URL(remotePath))

  protected def loadLocalJWKSet(pemCertLocation:String) = {
    val s = Source.fromFile(pemCertLocation, "UTF-8")
    try {
      val pemCertData = s.getLines().reduce(_ + _)
      new JWKSet(JWK.parseFromPEMEncodedX509Cert(pemCertData))
    } finally {
      s.close()
    }
  }

  /**
    * Loads in the public certificate(s) used for validating the bearer tokens from configuration.
    * @return Either an initialised JWKSet or a Failure indicating why it would not load.
    */
  def loadInKey():Try[JWKSet] = Try {
    val isRemoteMatcher = "^https*:".r.unanchored

    signingCertPath match {
      case isRemoteMatcher(_*) => {
        logger.info(s"Loading JWKS from $signingCertPath")

        val set = loadRemoteJWKSet(signingCertPath)
        logger.info(s"Loaded in ${set.getKeys.toArray.length} keys from endpoint")
        set
      }
      case _ => {
        logger.info(s"Loading JWKS from local path $signingCertPath")
        loadLocalJWKSet(signingCertPath)
      }
    }
  }

  def getVerifier(maybeKeyId:Option[String]) = maybeVerifiers match {
    case Some(verifiers) =>
      maybeKeyId match {
        case Some(kid) =>
          logger.info(s"Provided JWT is signed with key ID $kid")
          val list = verifiers.getKeys
          if (list.size > 1) {
            Option(verifiers.getKeyByKeyId(kid))
              .map(jwk=>new RSASSAVerifier(jwk.toRSAKey))
          } else {
            if (list.isEmpty) None else Some(new RSASSAVerifier(list.get(0).toRSAKey))
          }
        case None =>
          logger.info(s"Provided JWT has no key ID, using first available cert")
          val list = verifiers.getKeys
          if (list.isEmpty) None else Some(new RSASSAVerifier(list.get(0).toRSAKey))
      }
    case None=>None
  }

  def checkAudience(claimsSet:JWTClaimsSet) = {
    val audiences = claimsSet.getAudience.asScala ++ claimsSet.getAzp
    logger.debug(s"JWT audiences: $audiences")
    config.getOptional[Seq[String]]("auth.validAudiences") match {
      case None=>
        logger.error(s"No valid audiences configured. Set auth.validAudiences. Token audiences were $audiences")
        Left(LoginResultMisconfigured("Server configuration problem"))
      case Some(audienceList)=>
        if(audiences.intersect(audienceList).nonEmpty) {
          logger.debug("Audience permitted")
          Right(LoginResultOK(claimsSet))
        } else {
          Left(LoginResultInvalid("The token was not from a supported app"))
        }
    }
  }

  def checkUserGroup(claimsSet: JWTClaimsSet) = {
    if(!claimsSet.getIsMMAdmin && !claimsSet.getIsMMCreator) {
      Left(LoginResultInvalid("You don't have access to this system.  Contact Multimediatech if you think this is an error."))
    } else {
      Right(LoginResultOK(claimsSet))
    }
  }

  def checkUserRoles(claimsSet: JWTClaimsSet): Either[LoginResultInvalid[String], LoginResultOK[JWTClaimsSet]] = {
    (Option(claimsSet.getStringArrayClaim("roles")), Option(claimsSet.getStringClaim(isAdminClaimName())), Option(claimsSet.getStringClaim("multimedia_creator"))) match {
      case (Some(roles), _, _) =>
        if (roles.contains(isAdminClaimName()) || roles.contains("multimedia_creator")) {
          Right(LoginResultOK(claimsSet))
        } else {
          Left(LoginResultInvalid("You do not have access to this system.  Contact Multimediatech if you think this is an error."))
        }
      case (_, Some(_), _) =>
        if(!claimsSet.getIsMMAdmin) {
          Left(LoginResultInvalid("You do not have access to this system.  Contact Multimediatech if you think this is an error."))
        } else {
          Right(LoginResultOK(claimsSet))
        }
      case (_, _, Some(_)) =>
        if(!claimsSet.getIsMMCreator) {
          Left(LoginResultInvalid("You do not have access to this system.  Contact Multimediatech if you think this is an error."))
        } else {
          Right(LoginResultOK(claimsSet))
        }
      case (_, None, None) =>
        Left(LoginResultInvalid("You do not have access to this system.  Contact Multimediatech if you think this is an error."))
    }
  }

  protected def parseTokenContent(content:String) = Try {
    SignedJWT.parse(content)
  }
  /**
   * try to validate the given token with the key provided
   * returns a JWTClaimsSet if successful
   * @param token JWT token to verify
   * @return a Try, containing a JWTClaimsSet or an error
   */
  def validateToken(token:LoginResultOK[String]):Either[LoginResult,LoginResultOK[JWTClaimsSet]] = {
    logger.debug(s"validating token $token")
    parseTokenContent(token.content) match {
      case Success(signedJWT) =>
        getVerifier(Option(signedJWT.getHeader.getKeyID)) match {
          case Some(verifier) =>
            if (signedJWT.verify(verifier)) {
              logger.debug("verified JWT")
              //logger.debug(s"${signedJWT.getJWTClaimsSet.toJSONObject(true).toJSONString}")

              val claimsSet = signedJWT.getJWTClaimsSet
              (checkAudience(claimsSet), checkUserRoles(claimsSet)) match {
                case (Left(audErr), Left(userErr))=>
                  logger.error(s"JWT is not valid: $audErr, $userErr")
                  Left(audErr)
                case (Left(audErr), _)=>
                  logger.error(s"JWT audience is not valid: $audErr")
                  Left(audErr)
                case (_, Left(userErr))=>
                  logger.error(s"User ${claimsSet.getSubject} is not allowed to login in: $userErr")
                  Left(userErr)
                case (valid@Right(claims), Right(_))=>
                  valid
              }
            } else {
              Left(LoginResultInvalid(token.content))
            }
          case None =>
            Left(LoginResultMisconfigured("No signing cert configured"))
        }
      case Failure(err) =>
        logger.error(s"Failed to validate token for ${token.content}: ${err.getMessage}")
        Left(LoginResultInvalid("Authentication not valid"))
    }
  }

  /**
   * check the given parsed claims set to see if the token has already expired
   * @param claims JWTClaimsSet representing the token under consideration
   * @return a Try, containing either the claims set or a failure indicating the reason authentication failed. This is
   *         to make composition easier.
   */
  def checkExpiry(claims:JWTClaimsSet):Either[LoginResult,LoginResultOK[JWTClaimsSet]] = {
    if(claims.getExpirationTime.before(Date.from(Instant.now()))) {
      logger.debug(s"JWT was valid but expired at ${claims.getExpirationTime.formatted("YYYY-MM-dd HH:mm:ss")}")
      Left(LoginResultExpired(claims.getSubject))
    } else {
      Right(LoginResultOK(claims))
    }
  }

  /**
   * performs the JWT authentication against a given header.
   * This should not be called directly, but is done in the Security trait as part of IsAuthenticated or IsAdmin.
   * @param rh request header
   * @return a LoginResult subclass, as a Left if something failed or a Right if it succeeded
   */
  def apply(rh: RequestHeader): Either[LoginResult,LoginResultOK[JWTClaimsSet]] = {
    rh.headers.get("Authorization") match {
      case Some(authValue)=>
        extractAuthorization(authValue)
          .flatMap(validateToken)
          .flatMap(result=>checkExpiry(result.content))
          .map(result=>{
            rh.addAttr(BearerTokenAuth.ClaimsAttributeKey, result.content)
            result
          })
      case None=>
        logger.error("Attempt to access without authorization")
        Left(LoginResultNotPresent)
    }
  }
}
