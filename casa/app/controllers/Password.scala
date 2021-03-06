package controllers

import play.api.Play._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import play.api.Logger
import services.PasswordService
import play.api.data.Forms._
import play.api.data._
import views.html
import models.PasswordData
import play.api.data.validation._
import play.api.data.validation.ValidationError
import java.net.URLEncoder

class Password extends Controller with I18nSupport {
  override def messagesApi: MessagesApi = current.injector.instanceOf[MessagesApi]
  val passwordForm = Form(
    mapping (
      "userId" -> text.verifying("Staff ID should be numerals only and 8 characters long.",validateUserId _),
      "password1" -> text.verifying(validPassword),
      "password2" -> text
    )(PasswordData.apply)(PasswordData.unapply)
      .verifying("Passwords do not match", checkPassword _)
    )

  private def validateUserId(userId:String) = {
    val restrictedStringPattern = """^[0-9]{8}$""".r
    restrictedStringPattern.pattern.matcher(userId).matches
  }

  private def checkPassword(form: PasswordData) = form.password1==form.password2

  private def validPassword: Constraint[String] = Constraint[String]("constraint.password") { password =>
    val passwordPattern = """^(?=.*\d)(?=.*[a-z])(?=.*[A-Z])(?!.*\s).{9,20}$""".r

    passwordPattern.pattern.matcher(password).matches match {
      case true => Valid
      case false => Invalid(ValidationError("Choose a password that's 9 to 20 characters long, using a mixture of upper and lower case letters and numbers."))
    }
  }

  /**
   * Display the create password page.
   */
  def display = Action { implicit request =>
    Ok(html.password(passwordForm)).withHeaders(CACHE_CONTROL -> "no-cache, no-store")
      .withHeaders("X-Frame-Options" -> "SAMEORIGIN")
  }

  /**
   * Handle password form submission.
   */
  def digestPassword = Action { implicit request =>
    passwordForm.bindFromRequest.fold(
      formWithErrors => BadRequest(html.password(formWithErrors)),
      passwordData => {
        /* binding success, we get the actual value. */
        val pass = digestPasswordForUser(passwordData.userId, passwordData.password1)
        Ok(html.displayDigestedPassword(URLEncoder.encode(pass, "UTF-8"), passwordData.userId)).withHeaders(CACHE_CONTROL -> "no-cache, no-store")
          .withHeaders("X-Frame-Options" -> "SAMEORIGIN")
      }
    )
  }

  /**
   * Encrypt a user's password
   *
   * @param userId - the staff id of the user
   * @param password - plain user password
   * @return JsObject - the digested password as a json obj
   */
  def digestPasswordForUser(userId: String, password: String) = {
    Logger.debug(s"Digesting (encrypting) password for $userId")
    PasswordService.encryptPassword(password)
  }
}
