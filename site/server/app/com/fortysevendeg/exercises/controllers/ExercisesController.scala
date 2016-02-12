package com.fortysevendeg.exercises.controllers

import cats.data.Xor
import com.fortysevendeg.exercises.app._
import com.fortysevendeg.exercises.controllers.AuthenticationModule._
import com.fortysevendeg.exercises.persistence.domain.SaveUserProgress
import com.fortysevendeg.exercises.services.free.{ UserOps, UserProgressOps }
import com.fortysevendeg.exercises.services.interpreters.ProdInterpreters._
import com.fortysevendeg.exercises.utils.StringUtils.ExerciseType
import com.fortysevendeg.shared.free.ExerciseOps
import doobie.imports._
import play.api.libs.json.{ JsError, JsSuccess, JsValue }
import play.api.mvc.{ Action, BodyParsers, Controller }
import shared.ExerciseEvaluation

import scalaz.concurrent.Task

class ExercisesController(
    implicit
    exerciseOps:     ExerciseOps[ExercisesApp],
    userOps:         UserOps[ExercisesApp],
    userProgressOps: UserProgressOps[ExercisesApp],
    T:               Transactor[Task]
) extends Controller with JsonFormats {

  def evaluate(libraryName: String, sectionName: String): Action[JsValue] =
    AuthenticationAction(BodyParsers.parse.json) { request ⇒
      request.body.validate[ExerciseEvaluation] match {
        case JsSuccess(evaluation, _) ⇒

          userOps.getUserByLogin(request.userId).runTask match {
            case Xor.Right(Some(user)) ⇒
              val eval = for {
                exerciseEvaluation ← exerciseOps.evaluate(evaluation = evaluation)
                _ ← userProgressOps.saveUserProgress(
                  mkSaveProgressRequest(user.id, evaluation, exerciseEvaluation.isRight)
                )
              } yield exerciseEvaluation

              eval.runTask match {
                case Xor.Right(result) ⇒ Ok("Evaluation succeeded : " + result)
                case Xor.Left(error)   ⇒ BadRequest("Evaluation failed : " + error)
              }
            case _ ⇒ BadRequest("User login not found")
          }
        case JsError(errors) ⇒
          BadRequest(JsError.toJson(errors))
      }
    }

  private[this] def mkSaveProgressRequest(userId: Long, evaluation: ExerciseEvaluation, success: Boolean) =
    new SaveUserProgress.Request(
      userId = userId,
      libraryName = evaluation.libraryName,
      sectionName = evaluation.sectionName,
      method = evaluation.method,
      version = evaluation.version,
      exerciseType = ExerciseType fromString evaluation.exerciseType,
      args = evaluation.args.headOption map (_ ⇒ evaluation.args.mkString("[", ",", "]")),
      succeeded = success
    )
}