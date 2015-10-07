/*
 * Copyright (c) 2013, Hidekatsu Hirose
 * Copyright (c) 2013, Hirose-Zouen
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE.txt', which is part of this source code package.
 */

package org.hirosezouen.hzactor

import java.util.concurrent.TimeoutException

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.control.Exception._

import akka.actor.ActorDSL._
import akka.actor.ActorSystem
import akka.actor.Terminated

import org.hirosezouen.hzutil._
import HZActor2._
import HZLog._

object HZSimpleActorSample2 extends App {
    implicit val logger = getLogger(this.getClass.getName)
    implicit val system = ActorSystem("HZSimpleActorSample")

    val quit_r = "(?i)^q$".r
    val inputActor = actor("InputActor")(new InputActor(System.in) {
        override val input: PFInput = {
            case quit_r() => System.in.close
            case s        => {
                context.actorSelection("/system/dsl/inbox-1") ! s
                log_info(s"input : $s")
            }
        }
    })

    val ib = inbox()
    log_debug("***:" + ib.getRef.toString)
    ib.watch(inputActor)

    var loopFlag = true
    while(loopFlag) {
        catching(classOf[TimeoutException]) either {
            ib.receive(10 seconds) match {
                case Terminated(actor) => {
                    log_info(s"ib.receive:Terminated($actor)")
                    loopFlag = false
                }
                case s => log_info(s"ib.receive:($s)")
            }
        } match {
            case Right(_) => /* Continue loop. */
            case Left(th: Throwable) => log_error(th.getMessage)
        }
    }

    system.shutdown
}

