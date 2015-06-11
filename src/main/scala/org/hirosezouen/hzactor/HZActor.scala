/*
 * Copyright (c) 2013, Hidekatsu Hirose
 * Copyright (c) 2013, Hirose-Zouen
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE.txt', which is part of this source code package.
 */

package org.hirosezouen.hzactor

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader

import scala.util.control.Exception._
import scala.collection.generic.GenericSetTemplate
import scala.collection.generic.SetFactory
import scala.collection.generic.ImmutableSetFactory
import scala.collection.SetLike
import scala.concurrent.duration._

import akka.actor.Actor
import akka.actor.ActorContext
import akka.actor.ActorRef
import akka.actor.ActorRefFactory
import akka.actor.ActorContext
import akka.actor.Props
import akka.actor.Terminated

import org.hirosezouen.hzutil._
import HZIO._

object HZActor {
    import HZLog._
    implicit val logger = getLogger(this.getClass.getName)

    trait HZActorCommand
    case class HZStop() extends HZActorCommand 
    case class HZStopWithReason(reason: AnyRef) extends HZActorCommand 

    trait HZActorInformation

    trait HZActorReason
    trait HZActorStoped extends HZActorReason
    case class HZErrorStoped(th: Throwable) extends HZActorStoped
    case class HZNormalStoped() extends HZActorStoped
    case class HZNormalStopedWithMessage(message: String) extends HZActorStoped
    case class HZCommandStoped() extends HZActorStoped
    case class HZCommandStopedWithReason(reason: AnyRef) extends HZActorStoped
    case class HZUnHandledException(reason: Any) extends HZActorReason
    case class HZUnknownReason(reason: Any) extends HZActorReason
    object HZNullReason extends HZActorReason

    trait HZActorStateBase {
        val actor: ActorRef
        val stopReason: HZActorReason
    }
    case class HZActorState(actor: ActorRef, stopReason: HZActorReason = HZNullReason) extends HZActorStateBase

    class HZActorStateSet[A <: HZActorStateBase] extends Set[A]
                                                 with SetLike[A, HZActorStateSet[A]]
                                                 with Serializable
     {
        def +(elem: A): HZActorStateSet[A] = {
            super
            this
        }
        def -(elem: A): HZActorStateSet[A] = this
        def contains(elem: A): Boolean = true
        def iterator: Iterator[A] = null
        override def empty = new HZActorStateSet
    }
    object HZActorStateSet {
        def empty = new HZActorStateSet
    }

    def exitNormaly(reason: HZActorReason, parent: ActorRef)(implicit myself: ActorRef, context: ActorContext) {
        parent ! reason
        context.stop(myself)
    }
    def exitNormaly(parent: ActorRef)(implicit myself: ActorRef, context: ActorContext) {
        exitNormaly(HZNormalStoped(), parent)(myself, context)
    }

    def exitWithError(reason: HZActorReason, th: Throwable, parent: ActorRef):Nothing = {
        parent ! reason
        throw th
    }
    def exitWithError(th: Throwable, parent: ActorRef):Nothing = {
        exitWithError(HZErrorStoped(th), th, parent)
    }

    def defaultInputFilter(s: String) = s 

    class InputActor(in: InputStream, filter: (String) => String,
                     input: PartialFunction[String,Unit]) extends Actor
    {
        private val reader = new BufferedReader(new InputStreamReader(in))
        private case class InputLoop()

        override def preStart() {
            log_trace(s"InputActor:$self")
            self ! InputLoop()
        }

        def receive = {
            case InputLoop() => {
                catching(classOf[Exception]) either {
                    reader.readLine
                } match {
                    case Right(line) => {
                        log_trace(s"InputActor:Rignt($line)")
                        (({
                            case null => {
                                log_trace("InputActor:Right(null)")
//                                parent ! HZNormalStoped()
                                context.stop(self)
                            }
                        }: PartialFunction[String,Unit]) orElse input orElse({
                            case x => log_error(s"InputActor:unknown message:$x")
                        }: PartialFunction[String,Unit]))(filter(line))

                        self ! InputLoop()
                    }
                    case Left(th) => {
                        log_error(s"InputActor:Left($th.toString)")
//                        parent ! HZErrorStoped(th)
                        context.stop(self)
                    }
                }
            }
        }
    }
    object InputActor {
        def start(in: InputStream, filter: (String) => String = defaultInputFilter)
                 (input: PartialFunction[String,Unit])
                 (implicit context: ActorContext): ActorRef
            = context.actorOf(Props(new InputActor(in,filter,input)), "InputActor")
    }
}

/* vim: set expandtab: */
