/*
 * Copyright (c) 2013, Hidekatsu Hirose
 * Copyright (c) 2013, Hirose-Zouen
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE.txt', which is part of this source code package.
 */

package org.hirosezouen.hzactor

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor._
import akka.actor.OneForOneStrategy
import akka.actor.SupervisorStrategy.Escalate
import akka.actor.SupervisorStrategy.Stop

import org.hirosezouen.hzutil._
import HZActor1._
import HZLog._

object HZActorSample1 {
    implicit val logger = getLogger(this.getClass.getName)

    class MainActor extends Actor {
        log_trace("MainActor")

        override val supervisorStrategy = OneForOneStrategy(maxNrOfRetries=1, withinTimeRange=1 minutes, loggingEnabled=true) {
            case _: Exception => Stop
            case t => super.supervisorStrategy.decider.applyOrElse(t, (_: Any) => Escalate)
        }

        private val actorStates = HZActorStates()

        val quit_r = "(?i)^q$".r
        override def preStart() {
            actorStates += InputActor.start(System.in) {
                case quit_r() => System.in.close
                case s        => log_info(s"input : $s")
            }
        }

        def receive = {
            case Terminated(actor) if(actorStates.contains(actor)) => {
                log_debug(s"MainActor:receive:Terminated($actor)")
                context.system.terminate()
            }
            case x => log_debug(s"x=$x")
        }
    }
    object MainActor {
        def start(implicit system: ActorRefFactory): ActorRef = {
            log_debug("MainActor:Start")
            system.actorOf(Props(new MainActor))
        }
    }

    def start() {
        implicit val system = ActorSystem("HZActorSample")
        MainActor.start
        Await.result(system.whenTerminated, Duration.Inf)
    }
}

