/*
 * Copyright (c) 2013, Hidekatsu Hirose
 * Copyright (c) 2013, Hirose-Zouen
 * This file is subject to the terms and conditions defined in
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE.txt', which is part of this source code package.
 */

package org.hirosezouen.hzutil

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader

import scala.actors._
import scala.actors.Actor._
import scala.util.control.Exception._

import ch.qos.logback.classic.Level

import org.slf4j.Logger
import org.slf4j.LoggerFactory

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

    def defaultInputFilter(s: String) = s 

    def startInputActor(in: InputStream, filter: (String) => String = defaultInputFilter)
                       (input: PartialFunction[String,Unit]): Actor
    = {

        val parent = self
        val reader = new BufferedReader(new InputStreamReader(in))

        actor {
            log_debug("InputActor:%s".format(self))
            link(parent)
            loop {
                catching(classOf[Exception]) either {
                    reader.readLine
                } match {
                    case Right(line) => {
                        log_debug("InputActor:Rignt(%s)".format(line))
                        (({
                            case null => {
                                exit(HZNormalStoped())
                            }
                        }: PartialFunction[String,Unit]) orElse input orElse({
                            case x => log_error("InputActor:unknown message:%s".format(x))
                        }: PartialFunction[String,Unit]))(filter(line))
                    }
                    case Left(th) => {
                        log_error("InputActor:Left(%s)".format(th.toString))
                        exit(HZErrorStoped(th))
                    }
                }
            }
        }
    }
}

/* vim: set expandtab: */
