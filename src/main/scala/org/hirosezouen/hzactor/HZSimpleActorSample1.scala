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

import akka.actor.ActorDSL._
import akka.actor.ActorSystem

import org.hirosezouen.hzutil._
import HZActor1._
import HZLog._

object HZSimpleActorSample1 extends App {
    implicit val logger = getLogger(this.getClass.getName)
    implicit val system = ActorSystem("HZSimpleActorSample1")

    val quit_r = "(?i)^q$".r
    val inputActor = InputActor.start(System.in) {
        case quit_r() => System.in.close
        case s        => log_info(s"input : $s")
    }

    val ib = inbox()
    ib.watch(inputActor)
    log_info(s"ib.receive:${ib.receive(1 hours)}")

    Await.result(system.terminate, Duration.Inf)
}

