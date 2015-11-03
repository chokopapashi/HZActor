package org.hirosezouen.hzactor

import org.hirosezouen.hzutil._
import HZLog._

object HZSampleRunner extends App {
    implicit val logger = getLogger(this.getClass.getName)

    def usage =
        """|usage : HZSampleRunner <param>
           |param : 1 | HZActorSample
           |        2 | HZActorSample1
           |        3 | HZSimpleActorSample
           |        4 | HZSimpleActorSample1""".stripMargin
    def printErrorAndUsage(msg: String) = {log_error(msg) ; log_info(usage)}

    if(args.nonEmpty) {
        args(0) match {
            case "1" | "HZActorSample"        => HZActorSample.start
            case "2" | "HZActorSample1"       => HZActorSample1.start
            case "3" | "HZSimpleActorSample"  => HZSimpleActorSample.start
            case "4" | "HZSimpleActorSample1" => HZSimpleActorSample1.start
            case _ => printErrorAndUsage("error : wrong argument.")
        }
    } else
        printErrorAndUsage("error : argument required.")
}

