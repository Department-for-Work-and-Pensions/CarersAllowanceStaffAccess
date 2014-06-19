package services

import scala.concurrent.duration._
import org.joda.time.LocalDate
import play.api.libs.json._
import play.api.{Logger, Play}
import org.joda.time.format.DateTimeFormat
import play.api.http.Status
import scala.language.postfixOps
import scala.language.implicitConversions
import play.api.libs.json.JsObject
import play.api.libs.json.JsArray
import scala.Some
import utils.HttpUtils.HttpMethodWrapper

object ClaimServiceImpl extends ClaimsService {

  val url = getUrl
  val timeout = Play.configuration(Play.current).getInt("ws.timeout").getOrElse(30).seconds

  implicit val context = scala.concurrent.ExecutionContext.Implicits.global

  def getUrl = Play.configuration(Play.current).getString("claimsServiceUrl") match {
                case Some(s) if s.length > 0 => Logger.info(s"Getting claimServiceUrl value ($s)");s
                case _ => Logger.info("Getting default url value"); "http://localhost:9002"
  }

  implicit def stringGetWrapper(url: String) = new HttpMethodWrapper(url, timeout)

  override def claims(date: LocalDate): Option[JsArray] = {
    val dateString = DateTimeFormat.forPattern("ddMMyyyy").print(date)

    s"$url/claims/$dateString" get { response =>
      response.status match {
        case Status.OK => Some(response.json.as[JsArray])
        case Status.NOT_FOUND => None
      }
    }
  }

  override def circs(date: LocalDate): Option[JsArray] = {
    val dateString = DateTimeFormat.forPattern("ddMMyyyy").print(date)

    s"$url/circs/$dateString" get { response =>
      response.status match {
        case Status.OK => Some(response.json.as[JsArray])
        case Status.NOT_FOUND => None
      }
    }
  }

  def claimsFilteredBySurname(date: LocalDate, sortBy: String): Option[JsArray] = {

    val dateString = DateTimeFormat.forPattern("ddMMyyyy").print(date)

    s"$url/claims/surname/$dateString/$sortBy" get { response =>
      response.status match {
        case Status.OK => Some(response.json.as[JsArray])
        case Status.NOT_FOUND => None
      }
    }

  }

  override def claimsFiltered(date: LocalDate, status: String): Option[JsArray] = {

    val dateString = DateTimeFormat.forPattern("ddMMyyyy").print(date)

    s"$url/claims/$dateString/$status" get { response =>
      response.status match {
        case Status.OK => Some(response.json.as[JsArray])
        case Status.NOT_FOUND => None
      }
    }

  }

  override def claimNumbersFiltered(status: String*): JsObject =
    s"$url/counts/${status.mkString(",")}" get { response =>
      response.status match {
        case Status.OK => response.json.as[JsObject]
        case Status.BAD_REQUEST => Json.parse("{}").as[JsObject]
      }
    }

  override def updateClaim(transactionId: String, status: String): JsBoolean =
    s"$url/claim/$transactionId/$status" put { response =>
      response.status match {
        case Status.OK => new JsBoolean(true)
        case Status.BAD_REQUEST => new JsBoolean(false)
      }
    } exec()

  override def fullClaim(transactionId: String): Option[JsValue] =
    s"$url/claim/$transactionId/" get { response =>
      response.status match {
        case Status.OK => Some(response.json)
        case Status.BAD_REQUEST => None
      }
    }

  override def renderClaim(transactionId: String) = {
    s"$url/render/$transactionId" get { response =>
      response.status match {
        case Status.OK =>
          val html = response.body
          Some(
            html.replace("<title></title>",s"<title>Claim PDF $transactionId</title>")
                .replace("##CHECK##","""<img src="/assets/img/yes.png" style="height:20px;"/>""")
                .replace("##CROSS##","""<img src="/assets/img/no.png" style="height:20px;"/>""")
                .replace("</body>", footerForPrint)
                .replace("</body>","<script>window.onload = function(){window.opener.location.reload(false);};</script></body>")
                .replace("""<style type="text/css">""", styleSheetForHtmlOutput + """ <style type="text/css">""")
                .replace("""<body text="#000000" link="#000000" alink="#000000" vlink="#000000">""", headerForPrint)

          )
        case Status.NOT_FOUND => None
      }
    }
  }


  def export(): Option[JsArray] = {
    s"$url/export" get { response =>
      response.status match {
        case Status.OK => Some(response.json.as[JsArray])
        case Status.INTERNAL_SERVER_ERROR => None
      }
    }
  }

  def purge(): JsBoolean = {
    s"$url/purge" post { response =>
      response.status match {
        case Status.OK => JsBoolean(true)
        case Status.INTERNAL_SERVER_ERROR => JsBoolean(false)
      }
    } exec()
  }

  def styleSheetForHtmlOutput = {
    """<link rel="stylesheet" type="text/css" href="/assets/stylesheets/claimHtmlOutput.css"/>
       <link rel="stylesheet" type="text/css" href="/assets/stylesheets/claimPrintHtmlOutput.css" media="print">
      <script src="//code.jquery.com/jquery-1.9.1.min.js"></script>
      <script>$(document).ready(function() {
        $('#PrintPageHeaderId').append("<tr class='topHeaderBackGround'>")
          $('td.SummaryPageHeader span').each(function(){
             var header = $(this).html().trim();
             $('#PrintPageHeaderId tr').append("<th class='topHeader'>"+header+"</th>");
          });
        $('#PrintPageHeaderId').append("</tr>")
        $('#PrintPageHeaderId').append("<tr class='topHeaderBackGround'>")
              $('td.SummaryPageDateHeader span').each(function(){
                 var header = $(this).html();
                 $('#PrintPageHeaderId tr:nth-child(2)').append("<th class='topHeader' colspan='5'>"+header+"</th>");
        });
        $('#PrintPageHeaderId').append("</tr>")
        $('td.SummaryPageHeader').closest('tr').addClass('SummaryPageHeader');
        $('td.SummaryPageDateHeader').closest('tr').addClass('SummaryPageDateHeader');
        $('td.DetailPageHeader').closest('tr').addClass('DetailPageHeader');
        $('td.DetailPageFooter').closest('tr').addClass('DetailPageFooter');
        $('span').css('font-family','inherit').css('font-size','inherit').css('color', 'inherit').css('line-height', 'inherit');});
      </script>"""
  }

  def headerForPrint = {
    """
      <body text="#000000" link="#000000" alink="#000000" vlink="#000000">
        <table style="margin: 0 auto;">
          <thead id='PrintPageHeaderId'></thead>
          <tfoot><tr><td colspan='5'><span id="pageNumber"></span></td></tr></tfoot>
          <tbody>
           <tr><td colspan='5'>
    """
  }

  def footerForPrint = {
    """</td></tr>
        </tbody>
        </table>
      </body>"""
  }

}
