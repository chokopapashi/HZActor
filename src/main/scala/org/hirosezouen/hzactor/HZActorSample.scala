/*
 * Copyright (c) 2013, Hidekatsu Hirose
 * Copyright (c) 2013, Hirose-Zouen
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE.txt', which is part of this source code package.
 */

package org.hirosezouen.hzactor

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor._
import akka.actor.OneForOneStrategy
import akka.actor.SupervisorStrategy.Escalate
import akka.actor.SupervisorStrategy.Stop

import com.typesafe.config.ConfigFactory

import org.hirosezouen.hzutil._
import HZActor._
import HZLog._

object HZActorSample {
    implicit val logger = getLogger(this.getClass.getName)

    class MainActor extends Actor {
        log_trace("MainActor")

        override val supervisorStrategy = OneForOneStrategy(maxNrOfRetries=1, withinTimeRange=1 minutes, loggingEnabled=true) {
            case _: Exception => Stop
            case t => super.supervisorStrategy.decider.applyOrElse(t, (_: Any) => Escalate)
        }

        private var inputActor: ActorRef = null

        val quit_r = "(?i)^q$".r
        override def preStart() {
            inputActor = InputActor.start(System.in) {
                case (quit_r(),_) => System.in.close
                case (s,_)        => log_info(s"input : $s")
            }
            context.watch(inputActor)
        }

        def receive = {
            case Terminated(actor) if(actor == inputActor) => {
                log_debug(s"MainActor:receive:Terminated($actor)")
                context.system.shutdown()
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

    def main(args: Array[String]) {
        val config = ConfigFactory.parseString("""
            akka {
                loglevel = "DEBUG"
                loggers = ["akka.event.slf4j.Slf4jLogger"]
                logging-filter = "akka.event.slf4j.Slf4jLoggingFilter"
            }

            akka.actor.debug {
                receive = on
                lifecycle = on
            }
        """)

        implicit val system = ActorSystem("HZActorSample", config)
        MainActor.start
        system.awaitTermination()
    }
}

